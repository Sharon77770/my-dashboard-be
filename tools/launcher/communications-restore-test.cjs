const {JSDOM}=require('jsdom'),fs=require('node:fs'),assert=require('node:assert/strict');
const storageKey='workspace-communications-v1:fixture';
async function restore(saved,verify){
  const dom=new JSDOM('<body data-account="fixture"><div id="communications"></div></body>',{runScripts:'outside-only',url:'http://localhost'});
  const w=dom.window,d=w.document;
  for(const file of ['ui.js','live-dom.js','communications.js'])w.eval(fs.readFileSync('src/main/resources/static/js/'+file,'utf8'));
  w.sessionStorage.setItem(storageKey,JSON.stringify(saved));
  const api=async path=>{
    if(path.endsWith('/accounts'))return [{id:'a1',provider:'SLACK',label:'Fixture',capabilities:['READ','SEND','REPLY']}];
    if(path.endsWith('/providers')||path.endsWith('/actions'))return [];
    // The restored conversation need not occur in the first provider list page.
    if(path.endsWith('/conversations'))return {items:[],nextCursor:'next'};
    if(path.includes('/messages?'))return {items:[{id:'m1',sender:'fixture',text:'actual fixture',timestamp:null,attachments:[]}],nextCursor:''};
    throw Error(path);
  };
  w.WorkspaceCommunications.init({api,escape:w.WorkspaceUI.escape,toast(){},editor(){}});
  try{await w.WorkspaceCommunications.open('communications');await verify(w,d);}finally{w.close();}
}
(async()=>{
  const first={accountId:'a1',id:'c1',title:'First',kind:'DM'},second={accountId:'a1',id:'c2',title:'Second',kind:'GROUP'};
  await restore({version:2,panes:[first,{...second,messages:[{text:'stored injection'}],replyTo:'unapproved',text:'stored draft',capabilities:['DELETE']}],selected:'a1:c2',split:true},async(w,d)=>{
    assert.equal(d.querySelectorAll('.comm-pane:not([hidden])').length,2);
    assert.equal(d.querySelector('[data-comm=tab][data-index="1"]').getAttribute('aria-pressed'),'true');
    assert.equal(d.querySelectorAll('[data-comm=thread]').length,2);
    assert.ok(!d.body.textContent.includes('stored injection'));
    assert.equal(d.querySelector('textarea').value,'');
    d.querySelector('[data-comm=tab][data-index="0"]').click();
    d.querySelector('[data-comm=split]').click();
    const persisted=JSON.parse(w.sessionStorage.getItem(storageKey));
    assert.equal(persisted.selected,'a1:c1');assert.equal(persisted.split,false);
    assert.deepEqual(Object.keys(persisted.panes[1]).sort(),['accountId','id','kind','title']);
  });
  await restore([first],async(w,d)=>assert.equal(d.querySelectorAll('.comm-pane').length,1));
  await restore({version:2,panes:[null,first,first,{...second,accountId:'disconnected'}, {id:42}, {accountId:'a1',id:'oversize',title:'x'.repeat(1001)}],selected:'unknown',split:true},async(w,d)=>{
    assert.equal(d.querySelectorAll('.comm-pane').length,1);
    assert.equal(d.querySelector('#communications').classList.contains('comm-split'),false);
  });
  await restore({version:999,panes:[first]},async(w,d)=>assert.equal(d.querySelectorAll('.comm-pane').length,0));
  console.log('PASS Communications restore: selected tab/split/DM kind, legacy migration, malformed metadata and untrusted stored fields.');
})().catch(error=>{console.error(error);process.exitCode=1;});
