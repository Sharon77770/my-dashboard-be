const {JSDOM}=require('jsdom');
const fs=require('node:fs'),assert=require('node:assert/strict');
const dom=new JSDOM('<dialog open></dialog>',{runScripts:'outside-only'}),w=dom.window,d=w.document;
w.eval(fs.readFileSync('src/main/resources/static/js/remote-setup.js','utf8'));
let next={state:'BLOCKED',stage:'BLOCKED',code:'ADMIN_REQUIRED',message:'<img src=x>권한 필요'},connected=0,posts=0,saved;
const dialog=d.querySelector('dialog');dialog.close=()=>{dialog.removeAttribute('open');dialog.dispatchEvent(new w.Event('close'));};
const ui={editor:(title,html)=>{dialog.setAttribute('open','');dialog.innerHTML=html;},api:async(path,method,body)=>{
 if(path.endsWith('/plan'))return {title:'테스트 장비',message:'설치 안내',actionLabel:'설치하고 연결',canStart:true,requiresPassword:true};
 if(path.endsWith('/connection')){saved=body;return;}
 if(method==='POST'){posts++;assert.equal(d.querySelector('[data-setup-password]').value,'');return next;}
 return {state:'IDLE',message:''};
},refresh:async()=>{},connect:async()=>connected++};
const tick=()=>new Promise(resolve=>setImmediate(resolve));
(async()=>{
 w.WorkspaceRemoteSetup.open('fixture',ui);await tick();
 assert.equal(posts,0,'inspection must never install');
 assert.equal(d.querySelector('[data-setup-start]').disabled,false);
 d.querySelector('[data-setup-password]').value='test-transient';
 await d.querySelector('[data-setup-start]').onclick();
 assert.equal(connected,0);assert.equal(d.querySelectorAll('img').length,0);
 assert.match(d.querySelector('[data-setup-status]').textContent,/권한 필요/);
 assert.equal(d.querySelector('[data-setup-start]').disabled,false);
 d.querySelector('[data-remote-protocol]').value='VNC';d.querySelector('[data-remote-protocol]').onchange();
 assert.equal(d.querySelector('[data-remote-port]').value,'5900');
 next={state:'READY',stage:'READY',message:'연결됨'};
 await d.querySelector('[data-setup-save]').onclick();
 assert.equal(saved.protocol,'VNC');assert.equal(saved.port,5900);
 assert.equal(connected,1);assert.equal(dialog.open,false);
 console.log('PASS remote setup UI: read-only inspection, transient credentials, safe failure, protocol defaults and automatic connection');
 dom.window.close();
})().catch(error=>{console.error(error);dom.window.close();process.exitCode=1;});
