const {JSDOM}=require('jsdom');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..');
const dom=new JSDOM(fs.readFileSync(path.join(root,'src/main/resources/templates/home.html'),'utf8'),{runScripts:'outside-only',url:'http://localhost',pretendToBeVisual:true});
const w=dom.window,d=w.document;let narrow=false,mediaChange;
w.matchMedia=()=>({get matches(){return narrow;},addEventListener(type,handler){mediaChange=handler;}});
w.HTMLDialogElement.prototype.showModal=function(){this.open=true;};w.HTMLDialogElement.prototype.close=function(){this.open=false;this.dispatchEvent(new w.Event('close'));};
w.ResizeObserver=class{observe(){}disconnect(){}};let workspaceRefresh;w.setInterval=(callback,interval)=>{if(interval===60000)workspaceRefresh=callback;return 0;};
w.workspaceInitial={devices:[],applications:[{id:'external',name:'<img src=x> Tool',url:'https://example.test',pinned:false}],clips:[],bookmarks:[],activity:[],preferences:{theme:'dark',compact:true,terminalFont:13,clipMinutes:60},browserSettings:{mode:'CLIENT'},tabs:[]};
const calls=[];w.fetch=async(url,opt={})=>{const method=opt.method||'GET',body=opt.body?JSON.parse(opt.body):null;calls.push({url,method,body});let data=[];
if(url==='/api/v1/workspace')data=w.workspaceInitial;
if(url==='/api/v1/preferences'){Object.assign(w.workspaceInitial.preferences,body);data=body;}
if(url.startsWith('/api/v1/devices/device-1/files?'))data={entries:[{name:'test.txt',path:'/test.txt',directory:false,size:1024,modifiedAt:Date.now()},{name:'folder',path:'/folder',directory:true,size:0,modifiedAt:Date.now()}]};
if(url==='/api/v1/devices/device-1/docker')data={output:'running'};
if(url==='/api/v1/devices/device-1/status')data={state:'ONLINE',cpu:24,memory:35,disk:40,details:'healthy'};
return {ok:true,status:200,headers:{get:()=> 'application/json'},json:async()=>data};};
const tick=()=>new Promise(resolve=>setTimeout(resolve,25));
const click=selector=>{const node=d.querySelector(selector);assert.ok(node,selector);node.click();};
const load=()=>JSON.parse(w.localStorage.getItem(w.HomePersistence.key()));
(async()=>{
for(const file of ['ui.js','live-dom.js','launcher/app-registry.js','launcher/grid-model.js','launcher/persistence.js','launcher/widget-registry.js','launcher/interactions.js','launcher/launcher.js','planner.js','workspace.js'])w.eval(fs.readFileSync(path.join(root,'src/main/resources/static/js',file),'utf8'));
await tick();assert.equal(d.querySelector('#sidebar'),null);assert.equal(d.querySelectorAll('.home-item').length,16);
// A deferred response shows activity while navigation remains available.
const originalFetch=w.fetch;let resolveResponse,resolveBody;
w.fetch=async url=>url==='/api/v1/loading-check'?new Promise(resolve=>{resolveResponse=resolve;}):originalFetch(url);
const foreground=w.WorkspaceAssistantRuntime.api('/loading-check');
assert.equal(d.querySelector('#workspace-activity').hidden,false);
click('#home-grid [data-view="calendar"]');assert.equal(d.body.dataset.home,'false');
click('[data-view="home"]');
resolveResponse({ok:true,status:200,headers:{get:()=> 'application/json'},json:()=>new Promise(resolve=>{resolveBody=resolve;})});
await tick();assert.equal(d.querySelector('#workspace-activity').hidden,false);
resolveBody({ok:true});await foreground;await tick();assert.equal(d.querySelector('#workspace-activity').hidden,true);
w.fetch=async()=>{throw Error('offline');};
await assert.rejects(w.WorkspaceAssistantRuntime.api('/loading-check'),/offline/);await tick();assert.equal(d.querySelector('#workspace-activity').hidden,true);
let finishPolling;w.fetch=async()=>new Promise(resolve=>{finishPolling=resolve;});
const polling=w.WorkspaceAssistantRuntime.api('/assistant/jobs/polling-check');assert.equal(d.querySelector('#workspace-activity').hidden,true);
finishPolling({ok:true,status:204});await polling;w.fetch=originalFetch;
const homeItemBeforeRefresh=d.querySelector('.home-item');
w.dispatchEvent(new w.CustomEvent('workspace:invalidate',{detail:{topics:['workspace']}}));await new Promise(resolve=>setTimeout(resolve,250));
assert.equal(d.querySelector('.home-item'),homeItemBeforeRefresh,'unchanged background refresh preserves Home DOM');
for(const selector of ['.mobile-search-button','.workspace-mark','.home-command>span:first-child','.os-navigation [data-view="home"]>span:first-child','.os-navigation [data-action="palette"]>span:first-child','.os-navigation [data-launcher="drawer"]>span:first-child','.os-navigation [data-action="app-switcher"]>span:first-child','#devices [data-action="refresh-status"]','#apps [data-action="app-add"]'])assert.ok(d.querySelector(selector+' .ui-icon'),`${selector} uses a shared SVG icon`);
assert.ok(d.querySelector('.os-navigation [data-action="app-switcher"] #os-app-count'),'recent app count survives icon hydration');
assert.equal(d.querySelector('#devices [data-action="device-add"]').getAttribute('aria-label'),'SSH로 장비 연결');
assert.equal(d.querySelectorAll('#home-grid .home-widget-disclosure').length,0,'work shortcuts stay separate from saved widgets');
assert.equal(d.querySelectorAll('#home-widgets .home-widget-disclosure').length,6);
assert.equal(d.querySelectorAll('#home-widgets .home-widget-disclosure[open]').length,0);
assert.ok(d.querySelector('.overview-continue .overview-empty'));
w.workspaceInitial={...w.workspaceInitial,activity:[{kind:'TERMINAL',targetId:'local',label:'최근 터미널',path:'/',occurredAt:Date.now()}]};
workspaceRefresh();await new Promise(resolve=>setTimeout(resolve,250));
assert.equal(d.querySelector('.home-item'),homeItemBeforeRefresh,'activity polling preserves Home shortcuts');
assert.match(d.querySelector('.overview-continue').textContent,/최근 터미널/);
assert.ok(d.querySelector('.home-command[data-action="palette"]'));
assert.equal(d.querySelectorAll('#launcher-dock-apps').length,1);
assert.equal(w.WorkspaceApps.all().length,25);assert.equal(d.querySelectorAll('#home-grid img').length,0);
assert.ok(d.querySelector('#home-grid [data-view="assistant"]'));
assert.equal(w.WorkspaceApps.get('assistant').name,'AI 비서');
assert.ok(d.querySelector('#launcher-dock-apps [data-view="assistant"]'));
// Grid projects one model without collisions or changing canonical coordinates.
assert.equal(w.WorkspaceApps.get('kakaotalk'),undefined);
const retiredLayout=w.HomeGrid.sanitize({version:1,pages:1,dock:['kakaotalk','files'],items:[{id:'removed',type:'app',appId:'kakaotalk',page:0,x:0,y:0},{id:'old-folder',type:'folder',apps:['kakaotalk'],name:'old',page:0,x:1,y:0}]},w.WorkspaceApps,w.WorkspaceWidgets);
assert.deepEqual(Array.from(retiredLayout.dock),['files']);assert.equal(retiredLayout.items.length,0);
const grid=w.HomeGrid;const items=[{id:'a',page:0,x:0,y:0,w:4,h:2},{id:'b',page:0,x:4,y:0,w:4,h:2},{id:'c',page:1,x:0,y:0,w:1,h:1}];
const projected=grid.project(items,4);assert.equal(projected[1].y,2);assert.equal(items[1].x,4);assert.ok(!grid.overlap(projected[0],projected[1]));
const moved=grid.move(projected,'b',{x:0,y:0},4);assert.ok(!grid.overlap(moved[0],moved[1]));assert.throws(()=>grid.move(items,'a',{x:7,y:0},8),/격자/);assert.throws(()=>grid.move(items,'a',{page:NaN},8),/격자/);assert.throws(()=>grid.move(items,'a',{x:1.5},8),/격자/);
// App shortcuts retain their sessions in the OS recent-apps screen.
click('#home-grid [data-view="calendar"]');await tick();assert.equal(d.querySelector('#calendar').classList.contains('active'),true);assert.equal(d.querySelectorAll('#switcher-apps [data-view="calendar"]').length,1);assert.equal(d.body.dataset.home,'false');
click('[data-action="app-switcher"]');assert.equal(d.querySelector('#app-switcher').open,true);click('#switcher-apps [data-view="home"]');await tick();assert.equal(d.body.dataset.home,'true');
click('[data-action="os-back"]');await tick();assert.equal(d.querySelector('#calendar').classList.contains('active'),true);click('[data-action="os-back"]');await tick();assert.equal(d.body.dataset.home,'true');
click('#launcher-dock-apps [data-view="assistant"]');await tick();assert.equal(d.querySelector('#assistant').classList.contains('active'),true);assert.equal(d.querySelectorAll('#switcher-apps [data-view="assistant"]').length,1);
click('[data-action="os-back"]');await tick();assert.equal(d.body.dataset.home,'true');
const savedWidget=d.querySelector('#home-widgets .home-widget-disclosure');savedWidget.open=true;
assert.ok(savedWidget.querySelector('.widget-body'),'saved widgets remain available on demand');
savedWidget.dispatchEvent(new w.Event('toggle'));
await w.WorkspaceLauncher.refreshWidgets();
await tick(); // Foreground loading stays locked until the rendered response has settled.
assert.ok(d.querySelector('#home-widgets .home-widget-disclosure[open]'),'refresh preserves an expanded widget');
// Swiping a drawer icon must leave the drawer open and must not mutate the home layout.
const touchPointer=(type,node,x,y)=>{const event=new w.MouseEvent(type,{bubbles:true,cancelable:true,clientX:x,clientY:y,button:0});Object.defineProperties(event,{pointerId:{value:2},pointerType:{value:'touch'}});node.dispatchEvent(event);return event;};
click('[data-launcher="drawer"]');assert.ok(d.querySelector('.app-library-group h3'));const beforeSwipe=w.localStorage.getItem(w.HomePersistence.key());
const drawerIcon=d.querySelector('[data-drawer-app] .launcher-shortcut');
for(const end of ['pointerup','pointercancel']){
  touchPointer('pointerdown',drawerIcon,40,200);
  assert.equal(touchPointer('pointermove',drawerIcon,42,180).defaultPrevented,false);
  assert.equal(touchPointer('pointermove',drawerIcon,44,80).defaultPrevented,false);
  assert.equal(d.querySelector('#app-drawer').open,true);
  assert.equal(d.querySelector('.launcher-drag-ghost'),null);
  touchPointer(end,d,44,80);
}
await new Promise(resolve=>setTimeout(resolve,580));
assert.equal(d.querySelector('#home-context').open,false);
assert.equal(w.localStorage.getItem(w.HomePersistence.key()),beforeSwipe);
// A stationary long press still opens actions; a normal tap still launches the app.
touchPointer('pointerdown',drawerIcon,40,200);await new Promise(resolve=>setTimeout(resolve,580));
assert.equal(d.querySelector('#home-context').open,true);touchPointer('pointerup',d,40,200);drawerIcon.click();
d.querySelector('#home-context').close();click('#app-drawer [data-launcher="close"]');
click('[data-launcher="drawer"]');click('#drawer-apps [data-view="calendar"]');await tick();
assert.equal(d.querySelector('#calendar').classList.contains('active'),true);click('.os-navigation [data-view="home"]');await tick();
click('#home-edit');assert.equal(d.querySelector('#home-edit-tools').hidden,false);assert.equal(d.querySelector('#home-saved-widgets').hidden,true);assert.equal(d.querySelectorAll('#home-grid .home-item.widget').length,6,'editing restores saved widget coordinates');assert.equal(d.querySelector('#home-grid .launcher-shortcut[data-app-icon="studio"]').hasAttribute('data-view'),false);click('[data-launcher="page-add"]');assert.equal(load().pages,2);
click('[data-page="0"]');click('[data-launcher="drawer"]');assert.equal(d.querySelector('#app-drawer').open,true);assert.equal(d.querySelectorAll('#drawer-apps img').length,0);
d.querySelector('#drawer-search').value='터미널';d.querySelector('#drawer-search').dispatchEvent(new w.Event('input'));assert.equal(d.querySelectorAll('[data-drawer-app]').length,1);
click('[data-launcher="add-app"][data-app="terminal"]');await tick();assert.equal(load().items.filter(item=>item.appId==='terminal').length,2);click('#app-drawer [data-launcher="close"]');
// Create folder, rename, extract its final app and verify automatic empty-folder cleanup.
click('[data-launcher="new-folder"]');d.querySelector('#editor-fields [name="apps"][value="files"]').checked=true;d.querySelector('#editor-fields [name="name"]').value='Work <img>';
d.querySelector('#editor-form').dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));await tick();let folder=load().items.find(item=>item.type==='folder');assert.equal(folder.name,'Work <img>');assert.equal(d.querySelector('#home-grid img'),null);
click(`[data-launcher="folder"][data-item="${folder.id}"]`);click('[data-launcher="folder-app-menu"]');click('[data-launcher="folder-extract"]');await tick();assert.equal(load().items.some(item=>item.type==='folder'),false);
// Widget picker and resize preserve supported sizes and nonoverlap.
click('[data-launcher="widgets"]');assert.equal(d.querySelectorAll('.widget-preview').length,11);click('[data-launcher="add-widget"][data-widget="codex"]');await tick();const widget=load().items.filter(item=>item.widgetId==='codex').at(-1);assert.ok(widget);
click(`[data-home-item="${widget.id}"] [data-launcher="context"]`);click('#home-context [data-launcher="place"]');d.querySelector('[name="size"]').value='4,2';d.querySelector('[name="page"]').value='1';d.querySelector('[name="x"]').value='1';d.querySelector('#editor-form').dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));await tick();assert.equal(load().items.find(item=>item.id===widget.id).page,1);assert.equal(load().items.find(item=>item.id===widget.id).w,4);
// Pointer drag merges apps into a folder; folder reorder shares the same interaction layer.
const source=d.querySelector('.home-item.app'),target=[...d.querySelectorAll('.home-item.app')].find(node=>node!==source);
d.querySelector('#home-grid').getBoundingClientRect=()=>({left:0,top:0,right:800,bottom:800,width:800,height:800});
d.elementFromPoint=()=>target;
const pointer=(type,node,x,y)=>{const event=new w.MouseEvent(type,{bubbles:true,cancelable:true,clientX:x,clientY:y,button:0});Object.defineProperties(event,{pointerId:{value:1},pointerType:{value:'mouse'}});node.dispatchEvent(event);};
pointer('pointerdown',source,10,10);pointer('pointermove',source,120,10);pointer('pointerup',d,120,10);await new Promise(resolve=>setTimeout(resolve,320));
const merged=load().items.find(item=>item.type==='folder');assert.ok(merged);assert.equal(merged.apps.length,2);
click('[data-launcher="folder"][data-item="'+merged.id+'"]');const folderApps=[...d.querySelectorAll('[data-folder-app]')];d.elementFromPoint=()=>folderApps[1];d.querySelector('#home-folder').getBoundingClientRect=()=>({left:0,top:0,right:800,bottom:800});
touchPointer('pointerdown',folderApps[0],10,10);touchPointer('pointermove',folderApps[0],120,10);touchPointer('pointerup',d,120,10);await new Promise(resolve=>setTimeout(resolve,320));assert.equal(load().items.find(item=>item.id===merged.id).apps[1],merged.apps[0]);click('#home-folder [data-launcher="close"]');
// Lock prevents mutation; mobile uses same data and four-column projection.
click('#home-lock');const count=load().items.length;click('[data-launcher="drawer"]');click('[data-launcher="add-app"][data-app="terminal"]');await tick();assert.equal(load().items.length,count);assert.match(d.querySelector('#toast').textContent,/잠금/);
narrow=true;mediaChange();assert.equal(d.querySelector('#home-grid').style.getPropertyValue('--home-columns'),'4');
// One command engine includes registry apps; no second search API.
click('#app-drawer [data-launcher="close"]');click('[data-action="palette"]');await tick();assert.ok(d.querySelector('#search-results [data-view="studio"]'));assert.ok(d.querySelector('#search-results [data-view="assistant"]'));assert.ok(calls.some(call=>call.url.startsWith('/api/v1/search?')));
// Scoped persistence and untrusted layout validation.
const key=w.HomePersistence.key();d.body.dataset.account='another';assert.notEqual(w.HomePersistence.key(),key);d.body.dataset.account='';
const clean=grid.sanitize({version:1,pages:2,dock:['missing','terminal'],items:[{id:'x',type:'folder',apps:[],page:0,x:0,y:0},{id:'y',type:'app',appId:'missing'}]},w.WorkspaceApps,w.WorkspaceWidgets);assert.equal(clean.items.length,0);assert.equal(clean.dock.length,1);
// Mobile runtime tools keep file navigation and actions reachable without overlapping columns.
w.workspaceInitial={...w.workspaceInitial,devices:[...w.workspaceInitial.devices,{id:'device-1',name:'Spark',host:'localhost',remoteProtocol:'NONE'}],bookmarks:[...w.workspaceInitial.bookmarks,{id:'bookmark-1',deviceId:'device-1',path:'/saved'}]};
workspaceRefresh();await new Promise(resolve=>setTimeout(resolve,250));
const deviceCard=d.querySelector('#device-grid .device-card');
deviceCard.querySelector('details').open=true;
deviceCard.querySelector('[data-action="status"]').click();await tick();
assert.equal(d.querySelector('#device-grid .device-card'),deviceCard,'status polling preserves the device card');
assert.equal(deviceCard.querySelector('details').open,true,'status polling preserves expanded details');
assert.match(deviceCard.textContent,/온라인/);
w.eval(fs.readFileSync(path.join(root,'src/main/resources/static/js/drawers.js'),'utf8'));
const launch=d.createElement('button');launch.dataset.open='FILES';launch.dataset.target='device-1';d.body.append(launch);launch.click();await tick();
let fileRuntime=d.querySelector('#runtime-host .runtime-pane');assert.ok(fileRuntime);
assert.equal(fileRuntime.querySelectorAll('.file-row:not(.head)').length,2);
assert.ok(fileRuntime.querySelector('.file-places-trigger[aria-label] .ui-icon'));
assert.ok(fileRuntime.querySelector('.tool-bar [data-file="upload"] .ui-icon'));
assert.ok(fileRuntime.querySelector('.file-row-open .file-mobile-meta'));
assert.ok(fileRuntime.querySelector('.file-mobile-actions [data-file="delete"]'));
let uploadClicks=0;fileRuntime.querySelector('[data-upload]').click=()=>uploadClicks++;
click('.tool-bar [data-file="upload"]');await tick();assert.equal(uploadClicks,1);
click('.file-places-trigger');await tick();assert.ok(d.querySelector('.ui-side-drawer[open] .bookmark-item'));
click('.ui-side-drawer [data-file="navigate"]');await tick();
fileRuntime=d.querySelector('#runtime-host .runtime-pane');assert.equal(fileRuntime.querySelector('.path').value,'/saved');
assert.equal(d.querySelector('.ui-side-drawer[open]'),null,'navigating a bookmark closes its drawer');
const dockerLaunch=d.createElement('button');dockerLaunch.dataset.open='DOCKER';dockerLaunch.dataset.target='device-1';d.body.append(dockerLaunch);dockerLaunch.click();await tick();
assert.ok(d.querySelector('#runtime-host .runtime-pane:not([hidden]) [data-runtime-action="docker"][aria-label] .ui-icon'));
const storedLayoutBeforeContext=w.localStorage.getItem(w.HomePersistence.key());
w.WorkspaceLauncher.rememberContext('repository','owner/<script>','Repository <script>');
w.WorkspaceLauncher.rememberContext('service','service-1','Service <script>');
w.WorkspaceLauncher.updateActivity(w.workspaceInitial);
assert.ok(d.querySelector('.overview-continue [data-continue-repository]'));
assert.ok(d.querySelector('.overview-continue [data-service-open="service-1"]'));
assert.equal(d.querySelector('.overview-continue script'),null,'Continue labels are escaped');
assert.equal(w.localStorage.getItem(w.HomePersistence.key()),storedLayoutBeforeContext,'session navigation must not alter saved HomeItem layout');
w.dispatchEvent(new w.Event('pagehide'));dom.window.close();console.log('PASS launcher: grid collisions/projection, registry XSS, tabs/switcher, pages, drawer, folders/extraction, widgets/resize, lock, shared search, account persistence, mobile files runtime');
})().catch(error=>{console.error(error);w.dispatchEvent(new w.Event('pagehide'));dom.window.close();process.exitCode=1;});
