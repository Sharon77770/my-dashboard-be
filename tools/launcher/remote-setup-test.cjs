const {JSDOM}=require('jsdom');
const fs=require('node:fs'),assert=require('node:assert/strict');
const dom=new JSDOM('<dialog open></dialog>',{runScripts:'outside-only'}),w=dom.window,d=w.document;
w.eval(fs.readFileSync('src/main/resources/static/js/remote-setup.js','utf8'));
let next={state:'BLOCKED',message:'<img src=x>권한 필요'},connected=0;
const dialog=d.querySelector('dialog');dialog.close=()=>dialog.removeAttribute('open');
const ui={editor:(title,html)=>{dialog.setAttribute('open','');dialog.innerHTML=html;},api:async(path,method)=>method?next:{state:'IDLE',message:''},refresh:async()=>{},connect:async()=>connected++};
(async()=>{
 w.WorkspaceRemoteSetup.open('fixture',ui);
 await d.querySelector('[data-setup-start]').onclick();
 assert.equal(connected,0);assert.equal(d.querySelectorAll('img').length,0);
 assert.match(d.querySelector('[data-setup-status]').textContent,/권한 필요/);
 assert.equal(d.querySelector('[data-setup-start]').disabled,false);
 next={state:'READY',message:'연결됨'};
 await d.querySelector('[data-setup-start]').onclick();
 assert.equal(connected,1);assert.equal(dialog.open,false);
 console.log('PASS remote setup UI: safe failure guidance, retry, automatic connection after READY');
 dom.window.close();
})().catch(error=>{console.error(error);dom.window.close();process.exitCode=1;});
