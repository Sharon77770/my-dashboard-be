const {JSDOM}=require('jsdom');
const fs=require('node:fs'),assert=require('node:assert/strict');
const tick=()=>new Promise(resolve=>setTimeout(resolve,0));
(async()=>{
  const dom=new JSDOM('<button data-authentication-browser>Accounts</button><a id="auth" href="https://github.com/login/device">Auth</a><button data-authentication-app="app-one">App</button>',{url:'https://dashboard.test',runScripts:'outside-only'});
  const w=dom.window,d=w.document,requests=[],clients=[];
  w.HTMLDialogElement.prototype.showModal=function(){this.open=true;};
  w.HTMLDialogElement.prototype.close=function(){this.open=false;this.dispatchEvent(new w.Event('close'));};
  w.ResizeObserver=class{observe(){}disconnect(){}};
  let serial=0,gate;
  w.WorkspaceAssistantRuntime={api:async(path,method,body)=>{requests.push({path,method,body});if(method==='POST'){if(gate)return gate;return {id:'session-'+(++serial)};}}};
  class Mouse{}Mouse.Touchpad=class{};
  w.Guacamole={Mouse,WebSocketTunnel:class{},Keyboard:class{reset(){}},StringWriter:class{sendText(value){this.value=value;}sendEnd(){}},Client:class{
    constructor(){clients.push(this);this.display={getElement:()=>d.createElement('div'),getWidth:()=>1600,getHeight:()=>900,scale(){}};}
    getDisplay(){return this.display;}connect(){this.onstatechange(3);}disconnect(){this.closed=true;this.onstatechange?.(5);}sendKeyEvent(){}sendMouseState(){}createClipboardStream(){return {};}
  }};
  w.eval(fs.readFileSync('src/main/resources/static/js/remote-viewport.js','utf8'));
w.eval(fs.readFileSync('src/main/resources/static/js/authentication-browser.js','utf8'));
  const feature=w.WorkspaceAuthenticationBrowser,link=d.querySelector('#auth');let code='FIRST';
  feature.attach(link,()=>code);feature.attach(link,()=>code);
  assert.equal(d.querySelectorAll('.authentication-browser-open').length,1);
  code='LATEST';d.querySelector('.authentication-browser-open').click();await tick();
  assert.equal(requests[0].body.provider,'GITHUB');assert.equal(requests[0].body.url,link.href);
  assert.equal(requests[0].body.code,undefined,'one-time code must not enter session request');
  assert.match(d.querySelector('[data-auth-code]').textContent,/LATEST/);
  code='CHANGED';await new Promise(resolve=>setTimeout(resolve,550));assert.match(d.querySelector('[data-auth-code]').textContent,/CHANGED/);
  assert.match(d.querySelector('[data-auth-status]').textContent,/원본 앱/);
  assert.equal(d.querySelectorAll('.remote-viewport-controls').length,1,'authentication and messenger screens expose viewport controls');
  assert.equal(w.localStorage.length,0);assert.equal(w.sessionStorage.length,0);
  const dialog=d.querySelector('dialog');dialog.querySelector('input').value='private input';dialog.close();await tick();
  assert.equal(clients[0].closed,true);assert.equal(dialog.querySelector('input').value,'');assert.equal(d.querySelector('[data-auth-code]').textContent,'');
  assert.equal(d.querySelectorAll('.remote-viewport-controls').length,0,'closing removes viewport controls');
  assert.ok(requests.some(request=>request.method==='DELETE'&&request.path==='/sessions/session-1'));
  for(const bad of ['https://github.com.evil.test/','https://password@github.com/','http://github.com/','https://github.com:8443/','javascript:alert(1)'])assert.equal(feature.providerFor(bad),null);
  d.querySelector('[data-authentication-browser]').click();await tick();
  assert.equal(requests.at(-1).body.provider,'BROWSER');assert.equal(requests.at(-1).body.url,undefined);
  d.querySelector('[data-auth-provider=GOOGLE]').click();await tick();
  assert.equal(requests.at(-1).body.provider,'GOOGLE');
  d.querySelector('[data-authentication-app]').click();await tick();
  assert.equal(requests.at(-1).body.provider,'APP');assert.equal(requests.at(-1).body.applicationId,'app-one');
  let resolve;gate=new Promise(done=>resolve=done);const pending=feature.open({provider:'CODEX'});await tick();dialog.close();resolve({id:'late-session'});await pending;
  assert.ok(requests.some(request=>request.method==='DELETE'&&request.path==='/sessions/late-session'),'closing during creation must delete the late handle');
  assert.equal(dialog.open,false);
  dom.window.close();console.log('PASS authentication browser: provider routing, shared entrypoints, current code, cleanup, race and no credential persistence');
})().catch(error=>{console.error(error);process.exitCode=1;});
