const {JSDOM}=require('jsdom');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const base=path.resolve(__dirname,'../../src/main/resources/static/js');
const tick=(ms=150)=>new Promise(resolve=>setTimeout(resolve,ms));
(async()=>{
 const dom=new JSDOM('<main id="root"></main><span id="workspace-live"></span>',{url:'https://dashboard.test/',runScripts:'outside-only',pretendToBeVisual:true});
 const w=dom.window,d=w.document;w.matchMedia=()=>({matches:false});w.setInterval=()=>0;
 w.eval(fs.readFileSync(path.join(base,'live-dom.js'),'utf8'));
 const root=d.querySelector('#root'),patch=w.WorkspaceLiveDOM.patch;
 patch(root,'<article data-live-key="a"><b>1</b><details><summary>Menu</summary><input value="original"></details><form><textarea>draft</textarea></form></article><article data-live-key="b">B</article>');
 const a=root.children[0],b=root.children[1],input=a.querySelector('input'),form=a.querySelector('form'),details=a.querySelector('details');
 input.value='unsaved';input.focus();input.setSelectionRange(2,5);details.open=true;root.scrollTop=90;a.querySelector('textarea').value='local edit';
 patch(root,'<article data-live-key="b">B changed</article><article data-live-key="a"><b>2</b><details><summary>Menu</summary><input value="server"></details><form><textarea>server edit</textarea></form></article><article data-live-key="c">C</article>');
 assert.equal(root.children[0],b);assert.equal(root.children[1],a);assert.equal(a.querySelector('b').textContent,'2');assert.equal(input.value,'unsaved');assert.equal(d.activeElement,input);assert.equal(input.selectionStart,2);assert.equal(details.open,true);assert.equal(a.querySelector('form'),form);assert.equal(form.querySelector('textarea').value,'local edit');assert.equal(root.scrollTop,90);
 patch(root,'<article data-live-key="a"><b>3</b><details><summary>Menu</summary><input></details><form></form></article>');assert.equal(b.isConnected,false);assert.equal(root.firstChild,a);
 let hidden=false;Object.defineProperty(d,'hidden',{get:()=>hidden});
 const sockets=[];
 class FakeSocket {
  constructor(url){this.url=String(url);this.readyState=0;sockets.push(this);}
  frame(frame){this.readyState=1;this.onmessage({data:JSON.stringify({epoch:'epoch1',revision:1,topics:[],jobs:[],...frame})});}
  close(code=1006){this.readyState=3;this.onclose?.({code});}
 }
 w.WebSocket=FakeSocket;w.eval(fs.readFileSync(path.join(base,'realtime.js'),'utf8'));
 const changes=[];let heartbeats=0;w.addEventListener('workspace:invalidate',event=>changes.push([...event.detail.topics]));w.addEventListener('workspace:heartbeat',()=>heartbeats++);
 const live=w.WorkspaceRealtime;live.start();assert.equal(sockets.length,1);assert.equal(sockets[0].url,'wss://dashboard.test/ws/workspace');
 sockets[0].frame({type:'ready'});await tick();assert.deepEqual(changes.pop(),['all']);assert.equal(live.connected(),true);
 sockets[0].frame({type:'changed',revision:2,topics:['notes']});sockets[0].frame({type:'changed',revision:3,topics:['notes','calendar']});await tick();assert.deepEqual(changes.pop().sort(),['calendar','notes']);
 let done=false;const waiting=live.waitForJob('job').then(()=>done=true);await tick(10);assert.equal(done,false);sockets[0].frame({type:'changed',revision:4,jobs:['job']});await waiting;assert.equal(done,true);await tick();assert.equal(changes.length,0,'job-only frames must not refresh workspace widgets');
 sockets[0].frame({type:'changed',revision:6});await tick();assert.deepEqual(changes.pop(),['all'],'sequence gap resynchronizes');
 sockets[0].frame({type:'ready',epoch:'epoch2',revision:0});await tick();changes.length=0;sockets[0].frame({type:'changed',epoch:'epoch2',revision:2});await tick();assert.deepEqual(changes.pop(),['all'],'restart resets revision baseline');
 hidden=true;sockets[0].frame({type:'changed',epoch:'epoch2',revision:3,topics:['notes']});await tick();assert.equal(changes.length,0);hidden=false;d.dispatchEvent(new w.Event('visibilitychange'));await tick();assert.ok(changes.pop().includes('all'));
 sockets[0].frame({type:'heartbeat',epoch:'epoch2',revision:4});assert.equal(heartbeats,1);
 sockets[0].close();assert.equal(live.connected(),false);await tick(850);assert.equal(sockets.length,2,'disconnect reconnects automatically');
 sockets[1].frame({type:'ready',epoch:'epoch2',revision:5});await tick();assert.deepEqual(changes.pop(),['all']);
 w.dispatchEvent(new w.Event('pagehide'));await tick(850);assert.equal(sockets.length,2,'pagehide cancels reconnect');
 w.dispatchEvent(new w.PageTransitionEvent('pageshow',{persisted:true}));assert.equal(sockets.length,3);
 sockets[2].close(1008);await tick(850);assert.equal(sockets.length,3,'expired session stops reconnect');assert.equal(d.documentElement.dataset.realtime,'expired');
 dom.window.close();console.log('Live DOM and WebSocket lifecycle: passed');
})().catch(error=>{console.error(error);process.exit(1);});
