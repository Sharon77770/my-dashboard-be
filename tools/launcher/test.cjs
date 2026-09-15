const {JSDOM}=require('jsdom');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..');
const dom=new JSDOM(fs.readFileSync(path.join(root,'src/main/resources/templates/home.html'),'utf8'),{runScripts:'outside-only',url:'http://localhost',pretendToBeVisual:true});
const w=dom.window,d=w.document;let narrow=false,mediaChange;
w.matchMedia=()=>({get matches(){return narrow;},addEventListener(type,handler){mediaChange=handler;}});
w.HTMLDialogElement.prototype.showModal=function(){this.open=true;};w.HTMLDialogElement.prototype.close=function(){this.open=false;this.dispatchEvent(new w.Event('close'));};
w.ResizeObserver=class{observe(){}disconnect(){}};w.setInterval=()=>0;
w.workspaceInitial={devices:[],applications:[{id:'external',name:'<img src=x> Tool',url:'https://example.test',pinned:false}],clips:[],bookmarks:[],activity:[],preferences:{theme:'dark',compact:true,terminalFont:13,clipMinutes:60},browserSettings:{mode:'CLIENT'},tabs:[]};
const calls=[];w.fetch=async(url,opt={})=>{const method=opt.method||'GET',body=opt.body?JSON.parse(opt.body):null;calls.push({url,method,body});let data=[];
if(url==='/api/v1/workspace')data=w.workspaceInitial;
if(url==='/api/v1/preferences'){Object.assign(w.workspaceInitial.preferences,body);data=body;}
return {ok:true,status:200,headers:{get:()=> 'application/json'},json:async()=>data};};
const tick=()=>new Promise(resolve=>setTimeout(resolve,25));
const click=selector=>{const node=d.querySelector(selector);assert.ok(node,selector);node.click();};
const load=()=>JSON.parse(w.localStorage.getItem(w.HomePersistence.key()));
(async()=>{
for(const file of ['ui.js','launcher/app-registry.js','launcher/grid-model.js','launcher/persistence.js','launcher/widget-registry.js','launcher/interactions.js','launcher/launcher.js','planner.js','workspace.js'])w.eval(fs.readFileSync(path.join(root,'src/main/resources/static/js',file),'utf8'));
await tick();assert.equal(d.querySelector('#sidebar'),null);assert.equal(d.querySelectorAll('.home-item').length,11);
assert.equal(w.WorkspaceApps.all().length,18);assert.equal(d.querySelectorAll('#home-grid img').length,0);
// Grid projects one model without collisions or changing canonical coordinates.
const grid=w.HomeGrid;const items=[{id:'a',page:0,x:0,y:0,w:4,h:2},{id:'b',page:0,x:4,y:0,w:4,h:2},{id:'c',page:1,x:0,y:0,w:1,h:1}];
const projected=grid.project(items,4);assert.equal(projected[1].y,2);assert.equal(items[1].x,4);assert.ok(!grid.overlap(projected[0],projected[1]));
const moved=grid.move(projected,'b',{x:0,y:0},4);assert.ok(!grid.overlap(moved[0],moved[1]));assert.throws(()=>grid.move(items,'a',{x:7,y:0},8),/격자/);assert.throws(()=>grid.move(items,'a',{page:NaN},8),/격자/);assert.throws(()=>grid.move(items,'a',{x:1.5},8),/격자/);
// App shortcuts retain their sessions in the OS recent-apps screen.
click('#home-grid [data-view="calendar"]');await tick();assert.equal(d.querySelector('#calendar').classList.contains('active'),true);assert.equal(d.querySelectorAll('#switcher-apps [data-view="calendar"]').length,1);assert.equal(d.body.dataset.home,'false');
click('[data-action="app-switcher"]');assert.equal(d.querySelector('#app-switcher').open,true);click('#switcher-apps [data-view="home"]');await tick();assert.equal(d.body.dataset.home,'true');
click('[data-action="os-back"]');await tick();assert.equal(d.querySelector('#calendar').classList.contains('active'),true);click('[data-action="os-back"]');await tick();assert.equal(d.body.dataset.home,'true');
click('#home-edit');assert.equal(d.querySelector('#home-edit-tools').hidden,false);click('[data-launcher="page-add"]');assert.equal(load().pages,2);
click('[data-page="0"]');click('[data-launcher="drawer"]');assert.equal(d.querySelector('#app-drawer').open,true);assert.equal(d.querySelectorAll('#drawer-apps img').length,0);
d.querySelector('#drawer-search').value='터미널';d.querySelector('#drawer-search').dispatchEvent(new w.Event('input'));assert.equal(d.querySelectorAll('[data-drawer-app]').length,1);
click('[data-launcher="add-app"][data-app="terminal"]');await tick();assert.equal(load().items.filter(item=>item.appId==='terminal').length,2);click('#app-drawer [data-launcher="close"]');
// Create folder, rename, extract its final app and verify automatic empty-folder cleanup.
click('[data-launcher="new-folder"]');d.querySelector('#editor-fields [name="apps"][value="files"]').checked=true;d.querySelector('#editor-fields [name="name"]').value='Work <img>';
d.querySelector('#editor-form').dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));await tick();let folder=load().items.find(item=>item.type==='folder');assert.equal(folder.name,'Work <img>');assert.equal(d.querySelector('#home-grid img'),null);
click(`[data-launcher="folder"][data-item="${folder.id}"]`);click('[data-launcher="folder-app-menu"]');click('[data-launcher="folder-extract"]');await tick();assert.equal(load().items.some(item=>item.type==='folder'),false);
// Widget picker and resize preserve supported sizes and nonoverlap.
click('[data-launcher="widgets"]');assert.equal(d.querySelectorAll('.widget-preview').length,7);click('[data-launcher="add-widget"][data-widget="codex"]');await tick();const widget=load().items.find(item=>item.widgetId==='codex');assert.ok(widget);
click(`[data-home-item="${widget.id}"] [data-launcher="context"]`);click('#home-context [data-launcher="place"]');d.querySelector('[name="size"]').value='4,2';d.querySelector('[name="page"]').value='1';d.querySelector('[name="x"]').value='1';d.querySelector('#editor-form').dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));await tick();assert.equal(load().items.find(item=>item.id===widget.id).page,1);assert.equal(load().items.find(item=>item.id===widget.id).w,4);
// Pointer drag merges apps into a folder; folder reorder shares the same interaction layer.
const source=d.querySelector('.home-item.app'),target=[...d.querySelectorAll('.home-item.app')].find(node=>node!==source);
d.querySelector('#home-grid').getBoundingClientRect=()=>({left:0,top:0,right:800,bottom:800,width:800,height:800});
d.elementFromPoint=()=>target;
const pointer=(type,node,x,y)=>{const event=new w.MouseEvent(type,{bubbles:true,cancelable:true,clientX:x,clientY:y,button:0});Object.defineProperties(event,{pointerId:{value:1},pointerType:{value:'mouse'}});node.dispatchEvent(event);};
pointer('pointerdown',source,10,10);pointer('pointermove',source,120,10);pointer('pointerup',d,120,10);await new Promise(resolve=>setTimeout(resolve,320));
const merged=load().items.find(item=>item.type==='folder');assert.ok(merged);assert.equal(merged.apps.length,2);
click('[data-launcher="folder"][data-item="'+merged.id+'"]');const folderApps=[...d.querySelectorAll('[data-folder-app]')];d.elementFromPoint=()=>folderApps[1];d.querySelector('#home-folder').getBoundingClientRect=()=>({left:0,top:0,right:800,bottom:800});
pointer('pointerdown',folderApps[0],10,10);pointer('pointermove',folderApps[0],120,10);pointer('pointerup',d,120,10);await new Promise(resolve=>setTimeout(resolve,320));assert.equal(load().items.find(item=>item.id===merged.id).apps[1],merged.apps[0]);click('#home-folder [data-launcher="close"]');
// Lock prevents mutation; mobile uses same data and four-column projection.
click('#home-lock');const count=load().items.length;click('[data-launcher="drawer"]');click('[data-launcher="add-app"][data-app="terminal"]');await tick();assert.equal(load().items.length,count);assert.match(d.querySelector('#toast').textContent,/잠금/);
narrow=true;mediaChange();assert.equal(d.querySelector('#home-grid').style.getPropertyValue('--home-columns'),'4');
// One command engine includes registry apps; no second search API.
click('#app-drawer [data-launcher="close"]');click('[data-action="palette"]');await tick();assert.ok(d.querySelector('#search-results [data-view="studio"]'));assert.ok(calls.some(call=>call.url.startsWith('/api/v1/search?')));
// Scoped persistence and untrusted layout validation.
const key=w.HomePersistence.key();d.body.dataset.account='another';assert.notEqual(w.HomePersistence.key(),key);d.body.dataset.account='';
const clean=grid.sanitize({version:1,pages:2,dock:['missing','terminal'],items:[{id:'x',type:'folder',apps:[],page:0,x:0,y:0},{id:'y',type:'app',appId:'missing'}]},w.WorkspaceApps,w.WorkspaceWidgets);assert.equal(clean.items.length,0);assert.equal(clean.dock.length,1);
dom.window.close();console.log('PASS launcher: grid collisions/projection, registry XSS, tabs/switcher, pages, drawer, folders/extraction, widgets/resize, lock, shared search, account persistence');
})().catch(error=>{console.error(error);dom.window.close();process.exitCode=1;});


