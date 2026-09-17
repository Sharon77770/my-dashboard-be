const {JSDOM}=require('jsdom'),fs=require('fs'),assert=require('node:assert/strict');
const dom=new JSDOM('<meta name="csrf-header" content="X-CSRF-TOKEN"><meta name="csrf-token" content="fixture"><div id="notes" class="view active"></div><dialog id="editor-dialog"><form id="fields"></form></dialog>',{runScripts:'outside-only',url:'http://localhost'});
const w=dom.window,d=w.document,entries=[{id:'org',parentId:null,kind:'FOLDER',title:'조직',icon:'📁',revision:0},{id:'project',parentId:'org',kind:'FOLDER',title:'프로젝트',icon:'📁',revision:0},{id:'page',parentId:'project',kind:'DOCUMENT',title:'<img src=x>',icon:'📄',revision:0}],documents={page:[{type:'paragraph',content:'처음'}]},calls=[];
let submit,change,buffer,failSave=false,holdSave=null,saveStarted=null;
const escape=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const clone=value=>JSON.parse(JSON.stringify(value));
const api=async(path,method='GET',body)=>{
 calls.push({path,method,body:body&&clone(body)});
 if(path==='/notes'&&method==='GET')return clone(entries);
 if(path==='/notes'&&method==='POST'){const entry={...body,id:'new'+entries.length,revision:0};delete entry.blocks;entries.push(entry);documents[entry.id]=body.blocks;return {entry:clone(entry),blocks:clone(body.blocks)};}
 const id=path.split('/')[2],entry=entries.find(item=>item.id===id);
 if(method==='GET')return {entry:clone(entry),blocks:clone(documents[id])};
 if(method==='PUT'){
  if(failSave)throw new Error('revision conflict');
  if(path.endsWith('/content')&&holdSave){saveStarted?.();await new Promise(resolve=>{holdSave=resolve;});}
  assert.equal(body.revision,entry.revision);
  if(path.endsWith('/content'))documents[id]=clone(body.blocks);else Object.assign(entry,{title:body.title,icon:body.icon,parentId:body.parentId});
  entry.revision++;return clone(entry);
 }
 throw new Error('Unexpected request '+method+' '+path);
};
w.NotesBlockEditor={mount:(host,options)=>{buffer=clone(options.blocks);change=options.onChange;host.textContent='editor';return {blocks:()=>clone(buffer),markdown:()=>'',destroy:()=>{host.textContent='';},importMarkdown:()=>{change();}};}};
for(const file of ['notes-templates','notes'])w.eval(fs.readFileSync('src/main/resources/static/js/'+file+'.js','utf8'));
w.WorkspaceNotes.init({api,escape,toast:()=>{},editor:(title,html,callback)=>{d.querySelector('#fields').innerHTML=html;submit=callback;},confirmAction:(title,text,callback)=>callback()});
const tick=()=>new Promise(resolve=>setTimeout(resolve,35));
const click=selector=>{const target=d.querySelector(selector);assert.ok(target,selector);target.click();};
(async()=>{
 await w.WorkspaceNotes.open('notes');click('[data-note-toggle="org"]');click('[data-note-toggle="project"]');assert.equal(d.querySelectorAll('#notes img').length,0);
 click('[data-note-open="page"]');await tick();assert.equal(d.querySelector('[data-notes-title]').value,'<img src=x>');assert.match(d.querySelector('[data-notes-path]').textContent,/조직 \/ 프로젝트/);
 buffer=[{type:'checkListItem',props:{checked:true},content:'완료'}];change();click('[data-notes-action="save"]');await tick();assert.equal(documents.page[0].props.checked,true);
 const title=d.querySelector('[data-notes-title]');title.value='업무 기록';title.dispatchEvent(new w.Event('input'));click('[data-notes-action="save"]');await tick();assert.equal(entries.find(item=>item.id==='page').title,'업무 기록');
 failSave=true;buffer=[{type:'paragraph',content:'잃어버리면 안 되는 내용'}];change();click('[data-note-open="org"]');await tick();assert.ok(d.querySelector('[data-notes-title]'));assert.match(d.querySelector('[data-notes-status]').textContent,/revision conflict/);
 const unload=new w.Event('beforeunload',{cancelable:true});w.dispatchEvent(unload);assert.ok(unload.defaultPrevented);
 failSave=false;click('[data-notes-action="save"]');await tick();assert.equal(documents.page[0].content,'잃어버리면 안 되는 내용');
 // An edit while a request is in flight must not be acknowledged as saved.
 holdSave=true;buffer=[{type:'paragraph',content:'first'}];change();click('[data-notes-action="save"]');await tick();buffer=[{type:'paragraph',content:'second'}];change();const release=holdSave;holdSave=null;release();await tick();click('[data-notes-action="save"]');await tick();assert.equal(documents.page[0].content,'second');
 click('[data-notes-action="new-document"]');await tick();assert.equal(d.querySelectorAll('[name=template]').length,8);assert.ok(d.querySelector('[name=template][value=blank]').checked);
 const form=new w.FormData();form.set('title','9월 가계부');form.set('parentId','project');form.set('template','ledger');await submit(form);await tick();assert.equal(d.querySelector('[data-notes-title]').value,'9월 가계부');assert.ok(buffer.some(block=>block.type==='table'));
 assert.equal(calls.findLast(call=>call.method==='POST').body.parentId,'project');
 d.querySelector('[data-notes-search]').value='9월';d.querySelector('[data-notes-search]').dispatchEvent(new w.Event('input'));assert.match(d.querySelector('[data-notes-tree]').textContent,/9월 가계부/);
 console.log('PASS notebook UI: nested navigation, escaped names, template creation, revision chaining, failed-save protection, in-flight edits, search');dom.window.close();
})().catch(error=>{console.error(error);dom.window.close();process.exitCode=1;});
