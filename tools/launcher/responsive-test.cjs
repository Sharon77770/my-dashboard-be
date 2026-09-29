const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {JSDOM}=require('jsdom');
const postcss=require('../ui/node_modules/postcss');
const root=path.resolve(__dirname,'../..'),base=path.join(root,'src/main/resources');
const bundledCss=fs.readFileSync(path.join(base,'static/vendor/workspace-ui.css'),'utf8');
const bundled=postcss.parse(bundledCss);
const homeTemplate=fs.readFileSync(path.join(base,'templates/home.html'),'utf8');
for(const feature of ['telemetry','assistant','github','services','databases']){
 assert.ok(!homeTemplate.includes(`/css/${feature}.css`),`${feature} must use the shared CSS layer`);
}
for(const selector of ['.telemetry-card','.assistant-window','.github-layout','.services-grid','.db-layout']){
 assert.ok(bundledCss.includes(selector),`${selector} must be present in the shared CSS bundle`);
}
const telemetryRules=[];
bundled.walkRules(rule=>{if(rule.selector.split(',').includes('.telemetry-grid>.telemetry-card'))telemetryRules.push(rule);});
assert.equal(telemetryRules.filter(rule=>rule.nodes.some(node=>node.prop==='border')).at(-1)?.nodes.find(node=>node.prop==='border')?.value,'0','Telemetry feature style keeps list rows flat');

// jsdom lacks cascade layers and real viewport layout. Expand applicable CSS for DOM contract checks.
function applies(query,width){
 if(query.includes('prefers-reduced-motion'))return false;
 const max=/(?:max-width:\s*|width\s*<=\s*)([\d.]+)(px|rem)/.exec(query);
 const min=/(?:min-width:\s*|width\s*>=\s*)([\d.]+)(px|rem)/.exec(query);
 const pixels=value=>Number(value[1])*(value[2]==='rem'?16:1);
 return (!max||width<=pixels(max))&&(!min||width>=pixels(min));
}
function flatten(node,width){
 return (node.nodes||[]).map(rule=>rule.type==='atrule'
  ?(rule.name==='layer'||rule.name==='media'&&applies(rule.params,width)?flatten(rule,width):'')
  :rule.type==='rule'&&!rule.nodes.some(child=>child.type==='rule'||child.type==='atrule')?rule.toString():'').join('\n');
}
for(const width of [1440,1280,1024,768,710,700,430,390,360]){
 const dom=new JSDOM(homeTemplate,{runScripts:'outside-only',url:'http://localhost'});
 const window=dom.window,document=window.document;
 const style=document.createElement('style');
 style.textContent=flatten(bundled,width);
 document.head.append(style);
 const css=element=>window.getComputedStyle(element);
 const shell=document.querySelector('.main'),rail=document.querySelector('.activity-rail'),mobileNav=document.querySelector('.os-navigation');
 const header=document.querySelector('#devices .page-head');
 document.querySelector('.launcher-grid').dataset.editing='false';
 const savedWidget=document.createElement('div');savedWidget.className='home-item widget';document.querySelector('#home-widgets').append(savedWidget);
 assert.ok(shell&&rail&&mobileNav&&document.querySelector('.workspace-bar'));
 assert.equal(document.querySelector('.os-tools'),null);
 assert.equal(document.querySelector('#sidebar'),null);
 assert.equal(document.querySelector('#home-grid')!==null,true);
 assert.equal(document.querySelector('.home-overview')!==null,true);
 assert.equal(document.querySelector('#launcher-dock-apps')!==null,true);
 const note=document.createElement('div');note.className='notes-document';document.querySelector('#notes').append(note);
 const editor=document.createElement('div');editor.dataset.notesEditor='';editor.innerHTML='<pre>SELECT * FROM records</pre><table><tr><td>result</td></tr></table>';note.append(editor);
 const databasePane=document.createElement('div');databasePane.className='db-mobile-tabs';document.querySelector('#databases').append(databasePane);
 const databaseSidebar=document.createElement('div');databaseSidebar.className='db-sidebar';document.querySelector('#databases').append(databaseSidebar);
 const databaseEditor=document.createElement('div');databaseEditor.className='db-editor';databaseEditor.innerHTML='<div class="db-toolbar"><span>Production Database</span><button data-db-action="run">Run</button><button data-db-action="cancel">Cancel</button></div><textarea aria-label="SQL"></textarea>';document.querySelector('#databases').append(databaseEditor);
 const services=document.querySelector('#services');services.classList.add('service-detail-open');
 const serviceDetail=document.createElement('div');serviceDetail.className='service-detail';serviceDetail.innerHTML='<button data-services="back">Back</button><header class="service-detail-head"><div><h2>PFM API</h2></div><button data-services="refresh">Refresh</button></header><div class="service-actions"></div><nav class="service-tabs"></nav><div class="service-tab-body"></div>';services.append(serviceDetail);
 const telemetry=document.querySelector('#telemetry');telemetry.classList.add('telemetry-detail-open');
 telemetry.querySelector('#telemetry-content').innerHTML='<div class="telemetry-grid"><article class="panel telemetry-card"><button class="telemetry-card-open"><header><b>PFM API</b></header></button><footer><button>Guide</button></footer></article></div><div class="telemetry-detail"><header class="telemetry-service-head"><button class="telemetry-back">Back</button><div class="telemetry-service-identity"><h2>PFM API</h2></div><details class="telemetry-menu"><summary>More</summary></details></header></div>';
 const github=document.querySelector('#github');
 github.querySelector('.github-layout').innerHTML='<section class="panel github-scope"><nav id="github-nav"></nav><div id="github-repositories"></div></section><section class="panel github-content"><header class="github-content-head"><button class="github-scope-trigger" data-drawer-target=".github-scope">Browse</button><h2>Repository</h2></header></section>';
 const workbench=document.createElement('div');workbench.className='studio-workbench';workbench.dataset.mobilePane='editor';workbench.innerHTML='<div class="studio-explorer"></div><div class="studio-editor"></div><div class="studio-inspector"></div>';document.querySelector('#studio').append(workbench);
 const studioBar=document.createElement('div');studioBar.className='studio-commandbar';document.querySelector('#studio').prepend(studioBar);
 const cloud=document.querySelector('#cloud');cloud.classList.add('active','cloud-at-root');cloud.innerHTML='<section class="cloud-main"><div class="cloud-header"><button class="ui-drawer-trigger"></button><h1>내 드라이브</h1><form data-cloud-search><input></form><button class="cloud-search-trigger">⌕</button><button class="cloud-create-trigger">+</button></div><nav data-cloud-crumbs>내 드라이브</nav><div class="cloud-toolbar cloud-create"><button>파일 업로드</button></div><div class="cloud-toolbar cloud-selection"><button data-cloud-action="all">전체 선택</button><span class="cloud-selected-count">1개 선택</span><span data-cloud-normal><button data-cloud-action="download">다운로드</button><details class="ui-menu cloud-action-menu"><summary>더 보기</summary></details></span></div><div class="cloud-toolbar cloud-view-options"><span data-cloud-count>12개 항목</span><label>정렬<select><option>이름</option></select></label><label class="cloud-view-select">보기<select><option>목록</option></select></label><button class="cloud-view-trigger" data-cloud-action="view-toggle">격자</button><button class="cloud-select-trigger" data-cloud-action="selection-toggle">선택</button><button data-cloud-action="reload">↻</button></div><div class="cloud-items"><article class="cloud-item"><label class="cloud-select-hit"><input type="checkbox"></label><button class="cloud-item-open"><span class="cloud-icon">▤</span><span><b>file</b><small class="cloud-mobile-meta">text · 1 KB</small></span></button><span class="cloud-item-size">1 KB</span><time>today</time><button class="cloud-item-info">ⓘ</button></article></div></section>';
 const cloudEditor=document.createElement('section');cloudEditor.className='cloud-editor';cloudEditor.innerHTML='<header class="cloud-editor-header"><button data-file-back>←</button><div><h1>file.txt</h1></div><button data-file-save>저장</button><a>다운로드</a></header><div class="cloud-editor-content"><textarea class="cloud-text-editor"></textarea></div>';cloud.append(cloudEditor);
 const runtimeHost=document.querySelector('#runtime-host');runtimeHost.innerHTML='<section class="runtime-pane"><div class="tool-view"><div class="tool-bar"><button class="ui-drawer-trigger file-places-trigger">Places</button><select data-file-device><option>Spark</option></select><button data-file="parent">Up</button><form class="path-form"><input class="path"><button>Go</button></form><button data-file="reload">Reload</button><button data-file="bookmark">Bookmark</button><button data-file="upload">Upload</button><button data-file="mkdir">Folder</button></div><div class="file-layout"><aside>Places</aside><div class="file-table"><div class="file-row"><button class="file-row-open">file.txt</button><span class="file-modified">Today</span><span class="file-size">1 KB</span><span class="file-actions"><button>Rename</button></span><details class="file-mobile-actions"><summary>More</summary><div class="ui-menu-content"><button>Rename</button></div></details></div></div></div></div><div class="terminal live-runtime"><div class="terminal-head"><span><b>Terminal</b><small class="connection-state">Connected</small></span><div class="actions runtime-actions"><button>Reconnect</button><button>New</button><button>Fullscreen</button><button>Paste</button></div></div><div class="stream-area"></div></div></section>';
 const calendar=document.querySelector('#calendar');calendar.innerHTML='<div class="planner-toolbar"><div class="actions"><button>Previous</button><h2>2026년 10월</h2><input type="month" data-month value="2026-10"><button>Next</button><button>Today</button></div></div>';
 if(width<=700){
  assert.equal(css(calendar.querySelector('.planner-toolbar .actions')).flexWrap,'nowrap','Calendar navigation remains in one mobile row');
  assert.equal(css(calendar.querySelector('[data-month]')).minWidth,'0','Month picker can shrink without forcing page overflow');
  assert.equal(css(calendar.querySelector('h2')).display,'none','Mobile month picker replaces the duplicated month title');
  const editDialog=document.querySelector('#editor-dialog');editDialog.setAttribute('open','');
  assert.equal(css(editDialog).display,'flex','Mobile forms use a sheet with separate scrolling content');
  assert.equal(css(document.querySelector('#editor-fields')).overflow,'auto','Long mobile forms scroll inside their field area');
  assert.equal(css(document.querySelector('#editor-form .dialog-actions')).flexShrink,'0','Save and cancel stay outside the scrolling form');
  assert.equal(editDialog.getAttribute('aria-labelledby'),'editor-title');
  assert.equal(css(header).display,'grid','mobile app headers use one shared layout');
  assert.equal(css(header.querySelector('.actions')).flexWrap,'nowrap','compact device actions stay on one mobile toolbar row');
  for(const actions of document.querySelectorAll('.page-head .actions'))assert.ok(!['auto','hidden','clip'].includes(css(actions).overflowX),'app header actions remain visible');
  assert.equal(css(studioBar).flexWrap,'wrap','Studio controls reflow on narrow screens');
  assert.equal(css(cloud.querySelector('.cloud-header')).display,'grid','Cloud uses one compact mobile header');
  assert.equal(css(cloud.querySelector('[data-cloud-crumbs]')).display,'none','Cloud root does not repeat its title');
  assert.equal(css(cloud.querySelector('.cloud-view-options')).display,'grid','Cloud options keep fixed icon slots');
  assert.equal(css(cloud.querySelector('.cloud-select-trigger')).display,'grid','Cloud selection stays with list options');
  assert.equal(css(cloud.querySelector('.cloud-view-select')).display,'none','Cloud uses the compact view toggle');
  assert.equal(css(cloud.querySelector('[data-cloud-search]')).display,'none','Cloud search opens on demand');
  assert.equal(css(cloud.querySelector('.cloud-main>.cloud-create')).display,'none','Cloud creation tools leave the file area until requested');
  assert.equal(css(cloud.querySelector('.cloud-create-trigger')).display,'grid','Cloud opens creation from the compact header');
  const createSheet=document.createElement('dialog');createSheet.className='ui-side-drawer';createSheet.dataset.side='bottom';
  cloud.querySelector('.cloud-main').append(createSheet);createSheet.append(cloud.querySelector('.cloud-create'));
  assert.equal(css(createSheet.querySelector('.cloud-create')).display,'flex','Cloud creation actions become readable rows in the bottom sheet');
  assert.equal(css(cloud.querySelector('.cloud-selection')).display,'none','Cloud selection tools stay out of the file area until needed');
  cloud.classList.add('cloud-has-selection');assert.equal(css(cloud.querySelector('.cloud-selection')).display,'flex');
  assert.equal(css(cloud.querySelector('.cloud-selection')).flexWrap,'nowrap','Cloud selected actions do not overlap on narrow screens');
  cloud.classList.remove('cloud-has-selection');
  assert.equal(css(cloud.querySelector('.cloud-mobile-meta')).display,'block');
  assert.equal(css(cloud.querySelector('.cloud-select-hit')).display,'none','Cloud hides selection controls until selection mode');
  cloud.classList.add('cloud-selection-open');
  assert.equal(css(cloud.querySelector('.cloud-select-hit')).display,'grid','Cloud reveals selection controls in selection mode');
  cloud.classList.remove('cloud-selection-open');
  assert.equal(css(cloud.querySelector('.cloud-items')).borderTopWidth,'0px','Cloud file list stays flat');
  assert.ok(Number.parseFloat(css(cloud.querySelector('.cloud-select-hit')).minHeight)>=44,'Cloud selection keeps a touch target');
  assert.equal(css(cloudEditor.querySelector('.cloud-editor-header')).display,'grid','Cloud editor has a single compact action bar');
  assert.equal(css(runtimeHost.querySelector('.tool-bar')).display,'grid','Files toolbar uses two fixed mobile rows');
  assert.equal(css(runtimeHost.querySelector('.file-row')).gridTemplateColumns,'minmax(0,1fr) 44px','Files row reserves its own action column');
  assert.equal(css(runtimeHost.querySelector('.file-actions')).display,'none','Desktop file actions leave the mobile row');
  assert.equal(css(runtimeHost.querySelector('.file-mobile-actions')).display,'block','Files mobile actions remain reachable');
  assert.equal(css(runtimeHost.querySelector('.terminal-head')).display,'grid','Terminal keeps status and actions on one compact row');
  assert.equal(css(runtimeHost.querySelector('.terminal-head .runtime-actions')).flexWrap,'nowrap','Terminal actions cannot wrap into the stream');
  assert.equal(css(rail).display,'none');
  assert.equal(css(mobileNav).display,'grid');
  assert.equal(css(document.querySelector('#mobile-current-app')).display,'block');
  assert.equal(css(databasePane).display,'grid');
  assert.equal(css(databaseSidebar).display,'none','Database mobile panes replace the desktop explorer');
  assert.equal(css(databaseEditor.querySelector('.db-toolbar')).flexWrap,'nowrap','Database mobile query actions stay on one row');
  assert.ok(Number.parseFloat(css(databaseEditor.querySelector('[data-db-action="run"]')).minHeight)>=40,'Database run action keeps a touch target');
  assert.ok(services.querySelector('#services.service-detail-open>.page-head'),'Service detail targets its list header');
  assert.ok(bundledCss.includes('#services.service-detail-open>.page-head{display:none}'),'Service detail hides the list header');
  assert.equal(css(serviceDetail).display,'grid','Service mobile detail joins back and status in one header');
  assert.equal(css(telemetry.querySelector('.telemetry-card')).display,'grid','Telemetry mobile list uses compact rows');
  assert.ok(css(telemetry.querySelector('.telemetry-service-head')).gridTemplateColumns.includes('40px'),'Telemetry detail keeps its actions in one compact header');
  assert.equal(css(github.querySelector('.github-scope')).display,'none','GitHub browse controls leave the mobile content pane');
  assert.equal(css(github.querySelector('.github-scope-trigger')).display,'grid','GitHub browse drawer remains reachable');
  const githubDrawer=document.createElement('dialog');githubDrawer.className='ui-side-drawer';github.querySelector('.github-layout').append(githubDrawer);githubDrawer.append(github.querySelector('.github-scope'));
  assert.equal(css(githubDrawer.querySelector('.github-scope')).display,'flex','GitHub browse controls are visible inside the drawer');
  assert.ok(Number.parseFloat(css(note).paddingLeft)<=12,'Notes side padding should preserve document width');
  assert.equal(css(editor.querySelector('pre')).overflowX,'auto');
  assert.equal(css(editor.querySelector('table')).overflowX,'auto');
  assert.equal(css(workbench.querySelector('.studio-editor')).display,'flex');
  assert.equal(css(workbench.querySelector('.studio-explorer')).display,'none');
  assert.ok(css(databaseEditor.querySelector('textarea')).minHeight,'SQL editor keeps a viewport-sized writing area');
  assert.equal(css(document.querySelector('.launcher-grid')).gridAutoRows,'auto');
  assert.ok(Number.parseFloat(css(savedWidget).minHeight)<=42,'saved widgets remain compact');
  document.body.dataset.activeView='notes';
  assert.equal(css(mobileNav).display,'none','Notes uses content focus mode');
 }else{
  assert.equal(css(rail).display,'flex');
  assert.equal(css(mobileNav).display,'none');
  assert.equal(css(databasePane).display,'none');
  assert.equal(css(databaseSidebar).display,'flex','Database explorer remains available above mobile breakpoint');
  assert.equal(css(github.querySelector('.github-scope')).display,'grid','GitHub explorer remains available above mobile breakpoint');
  assert.equal(css(workbench).display,'grid');
 }
 if(width<=800){
  assert.equal(css(runtimeHost.querySelector('.file-layout')).gridTemplateColumns,'minmax(0,1fr)','Files must fill the width when its sidebar moves into a drawer');
  assert.equal(css(runtimeHost.querySelector('.file-layout>aside')).display,'none');
 }
 dom.window.close();
 console.log(`PASS ${width}px: workstation shell, Home and content focus rules`);
}
const featureCss=fs.readFileSync(path.join(base,'static/css/notes.css'),'utf8')+fs.readFileSync(path.join(base,'static/css/databases.css'),'utf8')+fs.readFileSync(path.join(base,'static/css/shell.css'),'utf8');
for(const required of ['overflow-x:auto','safe-area-inset-bottom','--viewport-height','data-mobile-pane=result','data-mobile-pane=schema','data-active-view=studio','data-runtime-focus=true'])
 assert.ok(featureCss.includes(required),required);
assert.ok(fs.readFileSync(path.join(base,'static/js/workspace.js'),'utf8').includes("window.visualViewport?.addEventListener('resize'"));
console.log('CSS/DOM regression checks passed; visual viewport, touch and virtual keyboard still need a browser.');
