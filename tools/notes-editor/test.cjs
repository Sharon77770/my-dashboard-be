const {JSDOM}=require('jsdom'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const base=path.resolve(__dirname,'../..');
const dom=new JSDOM('<html data-theme="light"><body><div id="notes"><div id="editor"></div></div></body></html>',{runScripts:'outside-only',url:'http://localhost',pretendToBeVisual:true});
const w=dom.window;
w.matchMedia=()=>({matches:false,addEventListener(){},removeEventListener(){},addListener(){},removeListener(){}});
w.ResizeObserver=class{observe(){}unobserve(){}disconnect(){}};
w.HTMLElement.prototype.scrollIntoView=function(){};
w.Range.prototype.getBoundingClientRect=()=>({x:0,y:0,width:100,height:20,top:0,left:0,right:100,bottom:20});
w.Range.prototype.getClientRects=()=>[];
w.document.elementFromPoint=()=>w.document.body;
const tick=()=>new Promise(resolve=>setTimeout(resolve,80));
(async()=>{
 w.eval(fs.readFileSync(path.join(base,'src/main/resources/static/js/notes-templates.js'),'utf8'));
 w.eval(fs.readFileSync(path.join(base,'src/main/resources/static/vendor/notes-editor.js'),'utf8'));
 for(const template of w.WorkspaceNoteTemplates.all()){
  const initial=w.WorkspaceNoteTemplates.create(template.id);let changed=0;
  const editor=w.NotesBlockEditor.mount(w.document.querySelector('#editor'),{blocks:initial.blocks,onChange:()=>changed++,upload:async()=>'/api/v1/notes/images/fixture',onError:error=>{throw error;}});
  await tick();assert.ok(w.document.querySelector('[contenteditable="true"]'),template.id+' renders editable blocks');
  assert.ok(editor.blocks().length>=1);const markdown=await editor.markdown();assert.equal(typeof markdown,'string');
  if(template.id==='ledger'){assert.ok(editor.blocks().some(block=>block.type==='table'));assert.match(markdown,/수입/);}
  await editor.importMarkdown('# 작업 기록\n\n- [x] 완료\n\n**중요한 내용**\n\n<script>alert(1)</script>');await tick();
  assert.ok(editor.blocks().some(block=>block.type==='heading'));assert.ok(changed>0);assert.equal(w.document.querySelectorAll('#editor script').length,0);
  editor.destroy();await tick();assert.equal(w.document.querySelector('#editor').children.length,0);
 }
 const first=w.WorkspaceNoteTemplates.create('todo');first.blocks[0].content[0].text='modified';assert.notEqual(w.WorkspaceNoteTemplates.create('todo').blocks[0].content[0].text,'modified');
 assert.equal(w.WorkspaceNoteTemplates.create('blank').blocks.length,0);
 console.log('PASS real BlockNote: eight templates, editable rendering, table, Markdown import/export, change events, safe text, disposal');
 dom.window.close();
})().catch(error=>{console.error(error.stack?.slice(0,2500)||error);dom.window.close();process.exitCode=1;});
