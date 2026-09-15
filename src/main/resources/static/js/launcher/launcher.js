'use strict';
/** Launcher presentation/state orchestration. Grid math, registries and storage have separate owners. */
window.WorkspaceLauncher = (() => {
  const apps=window.WorkspaceApps,grid=window.HomeGrid,widgets=window.WorkspaceWidgets;
  const {escape:e,icon}=window.WorkspaceUI;
  let helpers,state,statuses=new Map(),layout,page=0,editing=false,projection=[],folderId=null;
  const $=selector=>document.querySelector(selector);
  const mobile=()=>window.matchMedia('(max-width:700px)').matches;
  const columns=()=>mobile()?4:8;
  const id=()=>window.WorkspaceUI.uuid();
  function defaults(){const items=['devices','files','terminal','studio','calendar','timetable','kakaotalk','apps'].map((appId,x)=>({id:id(),type:'app',appId,page:0,x,y:0,w:1,h:1}));items.push({id:id(),type:'widget',appId:'search',widgetId:'search',page:0,x:0,y:1,w:4,h:1},{id:id(),type:'widget',appId:'devices',widgetId:'device-status',page:0,x:0,y:2,w:4,h:2},{id:id(),type:'widget',appId:'calendar',widgetId:'today',page:0,x:4,y:2,w:4,h:2});return {version:1,pages:1,locked:false,dock:['terminal','studio','files','kakaotalk'],items};}
  function save(){try{window.HomePersistence.save(layout);}catch{helpers.toast('홈 배치를 저장하지 못했습니다. 브라우저 저장 공간과 권한을 확인하세요. 현재 배치는 이 창에서 유지됩니다.');}render();}
  function transaction(change){try{const next=JSON.parse(JSON.stringify(layout));change(next);if(next.items.length>160)throw new Error('홈에는 최대 160개 항목을 배치할 수 있습니다.');if(next.items.some(item=>item.type==='folder'&&item.apps.length>60))throw new Error('폴더에는 최대 60개 앱을 배치할 수 있습니다.');layout=next;save();}catch(error){helpers.toast(error.message);}}
  function iconLabel(app,attrs=''){return `<button class="launcher-shortcut" ${attrs} aria-label="${e(app.name)}"><span class="launcher-icon">${icon(app.icon)}</span><span class="launcher-label">${e(app.name)}</span></button>`;}
  function render(){
    if(!layout)return;
    projection=grid.project(layout.items,columns());
    const visible=projection.filter(item=>item.page===page);
    const rows=Math.max(5,...visible.map(item=>item.y+item.h));
    const host=$('#home-grid');host.style.setProperty('--home-columns',columns());host.style.setProperty('--home-rows',rows);host.dataset.editing=editing;
    host.innerHTML=visible.map(item=>{
      let content;
      if(item.type==='app'){const app=apps.get(item.appId);content=app?iconLabel(app,editing?'':apps.attributes(app)):'';}
      if(item.type==='folder')content=`<button class="launcher-shortcut" data-launcher="folder" data-item="${e(item.id)}"><span class="launcher-icon folder-preview">${item.apps.slice(0,4).map(appId=>icon(apps.get(appId)?.icon)).join('')}</span><span class="launcher-label">${e(item.name)}</span></button>`;
      if(item.type==='widget'){const def=widgets.get(item.widgetId);content=`<section class="home-widget ${item.h===1?'compact':''}"><header><span>${icon(apps.get(def.appId)?.icon)} ${e(def.name)}</span><button class="ghost sm" ${apps.attributes(apps.get(def.appId))} aria-label="${e(def.name)} 앱 열기">↗</button></header><div class="widget-body">${widgets.render(item,state,statuses)}</div></section>`;}
      return `<div class="home-item ${item.type}" data-home-item="${e(item.id)}" style="grid-column:${item.x+1}/span ${item.w};grid-row:${item.y+1}/span ${item.h}" tabindex="${editing?'0':'-1'}" aria-label="${e(item.type==='folder'?item.name:item.type==='widget'?widgets.get(item.widgetId).name:apps.get(item.appId)?.name)}${editing?' · 방향키 이동, Enter 메뉴':''}">${content}${editing?`<button class="item-edit" data-launcher="context" data-item="${e(item.id)}" aria-label="항목 편집">${icon('more')}</button>${item.type==='widget'?`<button class="widget-resize" data-widget-resize data-launcher="place" data-item="${e(item.id)}" aria-label="위젯 크기 조절">↘</button>`:''}`:''}</div>`;
    }).join('');
    $('#home-pages').innerHTML=Array.from({length:layout.pages},(_,index)=>`<button data-page="${index}" class="page-dot ${index===page?'active':''}" aria-label="홈 ${index+1}페이지" aria-current="${index===page?'page':'false'}">${index+1}</button>`).join('');
    $('#home-page-label').textContent=`${page+1} / ${layout.pages}`;
    $('#home-edit-tools').hidden=!editing;
    $('#home-edit').textContent=editing?'편집 완료':'홈 편집';$('#home-edit').setAttribute('aria-pressed',String(editing));
    $('#home-lock').textContent=layout.locked?'잠금 해제':'레이아웃 잠금';$('#home-lock').setAttribute('aria-pressed',String(layout.locked));
    $('#launcher-dock-apps').innerHTML=layout.dock.filter(appId=>apps.get(appId)).map(appId=>iconLabel(apps.get(appId),`${apps.attributes(apps.get(appId))} data-dock-app="${e(appId)}"`)).join('');
    document.body.dataset.home=$('#home').classList.contains('active');document.body.dataset.homeEditing=String(editing);
  }
  function setPage(next){page=Math.max(0,Math.min(layout.pages-1,next));render();}
  function edit(){if(layout.locked){helpers.toast('레이아웃 잠금을 먼저 해제하세요.');return;}editing=!editing;render();}
  function addItem(item){transaction(next=>{if(next.items.length>=160)throw new Error('홈에는 최대 160개 항목을 배치할 수 있습니다.');const projected=grid.project(next.items,columns());next.items=[...projected,grid.findSpace(projected,{id:id(),page,x:0,y:0,w:1,h:1,...item},columns())];});}
  function drawer(){const dialog=$('#app-drawer');dialog.showModal();$('#drawer-search').value='';renderDrawer();$('#drawer-search').focus();}
  function renderDrawer(){const query=$('#drawer-search').value;$('#drawer-apps').innerHTML=apps.search(query).map(app=>`<div data-drawer-app="${e(app.id)}">${iconLabel(app,apps.attributes(app))}<button class="drawer-add ghost sm" data-launcher="add-app" data-app="${e(app.id)}" aria-label="${e(app.name)} 홈에 추가">+</button></div>`).join('')||'<p class="empty-state">검색 결과가 없습니다.</p>';}
  function closePopups(){for(const selector of ['#app-drawer','#home-context','#home-folder','#widget-picker']){const dialog=$(selector);if(dialog.open)dialog.close();}}
  function context(target){const item=layout.items.find(item=>item.id===(target?.dataset.homeItem||target?.dataset.item));const appId=target?.dataset.drawerApp||target?.dataset.dockApp;const folderApp=target?.dataset.folderApp;const dialog=$('#home-context');
    let html='';
    if(item){html=`<h2>${e(item.type==='folder'?item.name:item.type==='widget'?widgets.get(item.widgetId).name:apps.get(item.appId)?.name)}</h2>`;
      if(item.type==='app')html+=`<button ${apps.attributes(apps.get(item.appId))}>열기</button><button data-launcher="dock-add" data-app="${e(item.appId)}">Dock에 추가</button><button data-launcher="app-info" data-app="${e(item.appId)}">앱 정보</button>`;
      if(item.type==='folder')html+=`<button data-launcher="folder" data-item="${item.id}">폴더 열기</button><button data-launcher="rename-folder" data-item="${item.id}">폴더 이름 변경</button>`;
      html+=`<button data-launcher="place" data-item="${item.id}" ${layout.locked?'disabled':''}>위치 · 페이지${item.type==='widget'?' · 크기':''}</button><button class="danger" data-launcher="remove" data-item="${item.id}" ${layout.locked?'disabled':''}>Home에서 제거</button>`;
    }else if(folderApp){html=`<h2>${e(apps.get(folderApp)?.name)}</h2><button data-launcher="folder-extract" data-app="${e(folderApp)}">폴더 밖으로 이동</button><button data-launcher="folder-left" data-app="${e(folderApp)}">앞으로 이동</button><button data-launcher="folder-right" data-app="${e(folderApp)}">뒤로 이동</button>`;
    }else if(appId){const app=apps.get(appId);html=`<h2>${e(app.name)}</h2><button ${apps.attributes(app)}>열기</button><button data-launcher="add-app" data-app="${e(appId)}">Home에 추가</button><button data-launcher="dock-add" data-app="${e(appId)}">Dock에 추가</button><button data-launcher="dock-remove" data-app="${e(appId)}">Dock에서 제거</button><button data-launcher="app-info" data-app="${e(appId)}">앱 정보</button>`;
    }else html='<h2>홈 화면</h2><button data-launcher="edit">홈 편집</button><button data-launcher="drawer">앱 추가</button><button data-launcher="widgets">위젯 추가</button>';
    $('#context-content').innerHTML=html;dialog.showModal();
  }
  function folder(itemId){const item=layout.items.find(item=>item.id===itemId);if(!item)return;folderId=itemId;$('#folder-name').textContent=item.name;$('#folder-apps').innerHTML=item.apps.map(appId=>`<div data-folder-app="${e(appId)}">${iconLabel(apps.get(appId),apps.attributes(apps.get(appId)))}<button class="ghost sm" data-launcher="folder-app-menu" data-app="${e(appId)}" aria-label="${e(apps.get(appId).name)} 이동">${icon('more')}</button></div>`).join('');$('#folder-rename').dataset.item=itemId;$('#home-folder').showModal();}
  function textForm(title,fields,submit){closePopups();helpers.editor(title,fields,async data=>submit(data));}
  function placement(itemId){const item=projection.find(item=>item.id===itemId);if(!item)return;const def=widgets.get(item.widgetId);textForm('홈 배치',`<label>페이지<select name="page">${Array.from({length:layout.pages},(_,index)=>`<option value="${index}" ${item.page===index?'selected':''}>${index+1}</option>`).join('')}</select></label><div class="form-grid"><label>열 (1–${columns()})<input name="x" type="number" min="1" max="${columns()}" value="${item.x+1}" required></label><label>행 (1–64)<input name="y" type="number" min="1" max="64" value="${item.y+1}" required></label></div>${def?`<label>위젯 크기<select name="size">${def.sizes.filter(size=>size[0]<=columns()).map(size=>`<option value="${size.join(',')}" ${size[0]===item.w&&size[1]===item.h?'selected':''}>${size[0]} × ${size[1]}</option>`).join('')}</select></label>`:''}<p class="section-hint">겹치는 항목은 빈 공간으로 이동합니다. 같은 위치에 놓인 앱은 드래그로 폴더로 묶을 수 있습니다.</p>`,data=>{const [w,h]=data.has('size')?data.get('size').split(',').map(Number):[item.w,item.h];const next=grid.move(grid.project(layout.items,columns()),itemId,{page:Number(data.get('page')),x:Number(data.get('x'))-1,y:Number(data.get('y'))-1,w,h},columns());layout.items=next;save();});}
  function picker(){const dialog=$('#widget-picker');$('#widget-options').innerHTML=widgets.all().map(def=>`<article class="widget-preview"><header><h3>${e(apps.get(def.appId).name)} · ${e(def.name)}</h3><button data-launcher="add-widget" data-widget="${def.id}">추가</button></header><p>${e(def.description)}</p><div class="widget-preview-body">${widgets.render({widgetId:def.id,w:def.defaultSize[0],h:def.defaultSize[1]},state,statuses)}</div><small>${def.sizes.map(size=>size.join(' × ')).join(' / ')}</small></article>`).join('');dialog.showModal();}
  function resize(source,x,y){const item=projection.find(item=>item.id===source.dataset.homeItem);if(!item||item.type!=='widget'||layout.locked)return;const bounds=source.getBoundingClientRect(),host=$('#home-grid'),cellWidth=host.getBoundingClientRect().width/columns();const rowHeight=(parseFloat(getComputedStyle(host).gridAutoRows)||88)+(parseFloat(getComputedStyle(host).rowGap)||12);const width=Math.max(1,Math.round((x-bounds.left)/cellWidth)),height=Math.max(1,Math.round((y-bounds.top)/rowHeight));const sizes=widgets.get(item.widgetId).sizes.filter(size=>size[0]<=columns());sizes.sort((a,b)=>Math.abs(a[0]-width)+Math.abs(a[1]-height)-Math.abs(b[0]-width)-Math.abs(b[1]-height));const [w,h]=sizes[0];transaction(next=>{next.items=grid.move(projection,item.id,{w,h,x:Math.min(item.x,columns()-w)},columns());});}
  function drop(source,x,y){if(layout.locked)return;const target=document.elementFromPoint(x,y);const targetPage=target?.closest('[data-page]');const targetFolder=target?.closest('[data-folder-app]');const fromFolder=source.dataset.folderApp;const appId=source.dataset.drawerApp||fromFolder;const moving=layout.items.find(item=>item.id===source.dataset.homeItem);
    if(fromFolder&&targetFolder){transaction(next=>{const item=next.items.find(item=>item.id===folderId);const index=item.apps.indexOf(targetFolder.dataset.folderApp);item.apps=item.apps.filter(app=>app!==fromFolder);item.apps.splice(index,0,fromFolder);});folder(folderId);return;}
    const host=$('#home-grid'),rect=host.getBoundingClientRect();if(!targetPage&&(x<rect.left||x>rect.right||y<rect.top||y>rect.bottom))return;
    const cellX=targetPage?0:Math.max(0,Math.min(columns()-1,Math.floor((x-rect.left)/(rect.width/columns()))));const rowHeight=parseFloat(getComputedStyle(host).gridAutoRows)||96;const gap=parseFloat(getComputedStyle(host).rowGap)||12;const cellY=targetPage?0:Math.max(0,Math.floor((y-rect.top)/(rowHeight+gap)));
    const destination=target?.closest('[data-home-item]');const hit=layout.items.find(item=>item.id===destination?.dataset.homeItem);
    transaction(next=>{
      if(appId&&hit?.type==='folder'){const folder=next.items.find(item=>item.id===hit.id);if(!folder.apps.includes(appId))folder.apps.push(appId);if(fromFolder){const old=next.items.find(item=>item.id===folderId);if(old.id!==folder.id)old.apps=old.apps.filter(app=>app!==fromFolder);}next.items=next.items.filter(item=>item.type!=='folder'||item.apps.length);return;}
      if(hit&&hit.id!==moving?.id&&['app','folder'].includes(hit.type)&&(appId||moving?.type==='app')){const incoming=appId||moving.appId;const folder=next.items.find(item=>item.id===hit.id);if(folder.type==='app'){folder.type='folder';folder.name='새 폴더';folder.apps=[folder.appId];delete folder.appId;}if(!folder.apps.includes(incoming))folder.apps.push(incoming);next.items=next.items.filter(item=>item.id!==moving?.id);if(fromFolder){const old=next.items.find(item=>item.id===folderId);old.apps=old.apps.filter(app=>app!==fromFolder);next.items=next.items.filter(item=>item.type!=='folder'||item.apps.length);}return;}
      let items=grid.project(next.items,columns()),item=moving;
      if(appId){item={id:id(),type:'app',appId,page,x:0,y:0,w:1,h:1};items.push(item);if(fromFolder){const old=items.find(item=>item.id===folderId);old.apps=old.apps.filter(app=>app!==fromFolder);items=items.filter(item=>item.type!=='folder'||item.apps.length);}}
      if(!item)return;const projected=items.find(entry=>entry.id===item.id);next.items=grid.move(items,item.id,{page:targetPage?Number(targetPage.dataset.page):page,x:Math.min(cellX,columns()-projected.w),y:cellY},columns());
    });
  }
  async function click(event){const button=event.target.closest('button');if(!button)return;
    if(button.dataset.page!==undefined){setPage(Number(button.dataset.page));return;}
    if(button.closest('#app-drawer,#home-context,#home-folder,#widget-picker')&&(button.dataset.view||button.dataset.open||button.dataset.action))closePopups();
    const action=button.dataset.launcher;if(!action)return;
    const itemId=button.dataset.item,appId=button.dataset.app;
    try{
      if(action==='close'){button.closest('dialog').close();return;}
      if(action!=='folder-app-menu')$('#home-context').close();
      if(['add-app','add-widget','new-folder','remove','place','rename-folder','folder-extract','folder-left','folder-right','page-add','page-remove','dock-add','dock-remove'].includes(action)&&layout.locked)throw new Error('레이아웃 잠금을 먼저 해제하세요.');
      if(action==='edit')edit();
      if(action==='lock'){editing=false;transaction(next=>{next.locked=!next.locked;});}
      if(action==='drawer')drawer();
      if(action==='widgets')picker();
      if(action==='add-app'){addItem({type:'app',appId});helpers.toast('현재 홈 페이지에 추가했습니다.');}
      if(action==='add-widget'){const def=widgets.get(button.dataset.widget);const size=def.sizes.find(size=>size[0]<=columns()&&size[0]===def.defaultSize[0])||def.sizes[0];addItem({type:'widget',appId:def.appId,widgetId:def.id,w:size[0],h:size[1]});$('#widget-picker').close();}
      if(action==='context')context(button.closest('[data-home-item]'));
      if(action==='place')placement(itemId);
      if(action==='remove')transaction(next=>{next.items=next.items.filter(item=>item.id!==itemId);});
      if(action==='page-add')transaction(next=>{if(next.pages>=grid.maxPages)throw new Error('홈 페이지는 최대 12개입니다.');page=next.pages++;});
      if(action==='page-remove')transaction(next=>{if(next.pages===1)throw new Error('홈 페이지는 하나 이상 필요합니다.');if(next.items.some(item=>item.page===page))throw new Error('항목을 옮긴 뒤 빈 페이지를 삭제하세요.');next.items.forEach(item=>{if(item.page>page)item.page--;});next.pages--;page=Math.min(page,next.pages-1);});
      if(action==='dock-add')transaction(next=>{if(next.dock.includes(appId))return;if(next.dock.length>=6)throw new Error('Dock은 최대 6개 앱입니다. 기존 앱을 먼저 제거하세요.');next.dock.push(appId);});
      if(action==='dock-remove')transaction(next=>{next.dock=next.dock.filter(app=>app!==appId);});
      if(action==='app-info'){const app=apps.get(appId);textForm('앱 정보',`<h3>${e(app.name)}</h3><p>${e(app.kind?'연결 앱':app.route?'워크스페이스 앱':'도구 및 설정')}</p><p class="section-hint">Home이나 Dock에서 제거해도 앱과 저장 데이터는 유지됩니다.</p>`,()=>{});}
      if(action==='folder')folder(itemId);
      if(action==='rename-folder'){const item=layout.items.find(item=>item.id===itemId);textForm('폴더 이름',`<label>이름<input name="name" value="${e(item.name)}" maxlength="60" required></label>`,data=>{const name=data.get('name').trim();if(!name)throw new Error('폴더 이름을 입력하세요.');transaction(next=>{next.items.find(item=>item.id===itemId).name=name;});});}
      if(action==='new-folder')textForm('폴더 추가',`<label>이름<input name="name" value="새 폴더" maxlength="60" required></label><p>포함할 앱을 선택하세요.</p>${apps.all().map(app=>`<label class="checkbox"><input type="checkbox" name="apps" value="${e(app.id)}">${e(app.name)}</label>`).join('')}`,data=>{const selected=data.getAll('apps');if(!selected.length)throw new Error('앱을 하나 이상 선택하세요.');addItem({type:'folder',name:data.get('name').trim()||'새 폴더',apps:selected});});
      if(action==='folder-app-menu')context({dataset:{folderApp:appId}});
      if(action==='folder-extract'){transaction(next=>{const item=next.items.find(item=>item.id===folderId);item.apps=item.apps.filter(app=>app!==appId);next.items=next.items.filter(item=>item.type!=='folder'||item.apps.length);const projected=grid.project(next.items,columns());next.items=[...projected,grid.findSpace(projected,{id:id(),type:'app',appId,page,x:0,y:0,w:1,h:1},columns())];});$('#home-folder').close();}
      if(action==='folder-left'||action==='folder-right'){transaction(next=>{const item=next.items.find(item=>item.id===folderId);const index=item.apps.indexOf(appId),destination=Math.max(0,Math.min(item.apps.length-1,index+(action==='folder-left'?-1:1)));item.apps.splice(index,1);item.apps.splice(destination,0,appId);});folder(folderId);}
      if(action==='refresh-widgets')await refreshWidgets();
    }catch(error){helpers.toast(error.message);}
  }
  async function refreshWidgets(){await widgets.refresh(helpers.api);render();}
  return {
    init(shared){helpers=shared;state=helpers.state();apps.sync(state);try{const saved=window.HomePersistence.load();layout=saved?grid.sanitize(saved,apps,widgets):defaults();}catch{layout=defaults();helpers.toast('저장된 홈을 읽지 못해 기본 배치를 표시합니다. 편집 후 새 배치가 저장됩니다.');}
      document.body.addEventListener('click',click);$('#drawer-search').addEventListener('input',renderDrawer);
      document.body.addEventListener('contextmenu',event=>{const app=event.target.closest('[data-dock-app],[data-folder-app]');if(app){event.preventDefault();context(app);}});
      window.LauncherInteractions.attach(document.body,{editable:()=>editing&&!layout.locked,locked:()=>layout.locked,context,drop,resize,dragMove:(source,x,y)=>{if(source.dataset.folderApp&&$('#home-folder').open){const box=$('#home-folder').getBoundingClientRect();if(x<box.left||x>box.right||y<box.top||y>box.bottom)$('#home-folder').close();}},page:delta=>setPage(page+delta),dragStart:source=>{if(source.dataset.drawerApp){editing=true;$('#app-drawer').close();helpers.showHome();render();}}});
      $('#home-grid').addEventListener('keydown',event=>{if(!editing)return;const target=event.target.closest('[data-home-item]');if(!target)return;if(event.key==='Enter'){event.preventDefault();context(target);return;}const offsets={ArrowLeft:[-1,0],ArrowRight:[1,0],ArrowUp:[0,-1],ArrowDown:[0,1]};if(!offsets[event.key])return;event.preventDefault();const item=projection.find(item=>item.id===target.dataset.homeItem);transaction(next=>{next.items=grid.move(projection,item.id,{x:item.x+offsets[event.key][0],y:item.y+offsets[event.key][1]},columns());});$(`[data-home-item="${item.id}"]`)?.focus();});
      window.matchMedia('(max-width:700px)').addEventListener('change',render);
      window.addEventListener('storage',event=>{if(event.key!==window.HomePersistence.key()||!event.newValue)return;try{layout=grid.sanitize(JSON.parse(event.newValue),apps,widgets);page=Math.min(page,layout.pages-1);editing=false;render();}catch{helpers.toast('다른 창의 홈 배치를 읽지 못했습니다.');}});
      window.addEventListener('studio-state',event=>{widgets.updateStudio(event.detail);render();});render();refreshWidgets();
    },
    sync(nextState,nextStatuses){state=nextState;statuses=nextStatuses;apps.sync(state);if(layout){layout=grid.sanitize(layout,apps,widgets);render();}},
    opened(){document.body.dataset.home=$('#home').classList.contains('active');if(document.body.dataset.home==='true')refreshWidgets();},
    drawer
  };
})();
