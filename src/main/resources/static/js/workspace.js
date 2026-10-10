'use strict';
(() => {
  let state = window.workspaceInitial;
  let tabs = [...state.tabs];
  let activeTab = null, activeView = 'home';
  const navigationTrail=[];
  let restoringNavigation=false;
  function rememberScreen(view,tab){if(restoringNavigation||(view===activeView&&tab===activeTab))return;navigationTrail.push({view:activeView,tab:activeTab});if(navigationTrail.length>50)navigationTrail.shift();}
  async function navigateBack(){
    const dialogs=[...document.querySelectorAll('dialog[open]')];if(dialogs.length){dialogs.at(-1).close();return;}
    const menu=document.querySelector('details[open]');if(menu){menu.open=false;return;}
    let previous;while(navigationTrail.length){const candidate=navigationTrail.pop();if(candidate.tab?tabs.some(tab=>tab.id===candidate.tab):candidate.view==='home'||pageTabs.includes(candidate.view)){previous=candidate;break;}}
    restoringNavigation=true;try{if(previous?.tab)await activateTab(previous.tab);else showView(previous?.view||'home');}finally{restoringNavigation=false;}
  }
  const appRegistry=window.WorkspaceApps;
  const pageTabKey='workspace-app-tabs-v1:'+encodeURIComponent(document.body.dataset.account || 'owner');
  let pageTabs=[];
  try {pageTabs=JSON.parse(localStorage.getItem(pageTabKey)||'[]').filter(id=>appRegistry.get(id)?.route).slice(0,20);} catch {}
  function savePageTabs(){try{localStorage.setItem(pageTabKey,JSON.stringify(pageTabs));}catch{toast('열린 앱 목록을 저장하지 못했습니다.');}}
  function appSwitcher(){renderTabs();$('#app-switcher').showModal();}
  const runtimes = new Map();
  const statuses = new Map();
  const $ = (selector, root = document) => root.querySelector(selector);
  const escape = value => String(value ?? '').replace(/[&<>"']/g, character => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[character]));
  const icons = {TERMINAL:'›_', FILES:'▤', REMOTE:'▰', APP:'◇', DOCKER:'DK', GPU:'GPU'};
  const labels = {TERMINAL:'터미널', FILES:'파일', REMOTE:'원격', APP:'앱', DOCKER:'Docker', GPU:'GPU'};
  const modes = {CLIENT:'현재 브라우저', SERVER:'서버 Chromium', REMOTE:'원격 브라우저 서버'};
  const empty = message => window.WorkspaceUI.emptyState(message);
  const openAttrs = (kind, target, path = '/') => `data-open="${kind}" data-target="${escape(target)}" data-path="${escape(path)}"`;
  const device = id => state.devices.find(item => item.id === id);
  const timestamp = value => new Intl.DateTimeFormat('ko-KR', {month:'short',day:'numeric',hour:'2-digit',minute:'2-digit'}).format(value);
  const bytes = size => size < 1024 ? `${size} B` : size < 1048576 ? `${(size / 1024).toFixed(1)} KB` : `${(size / 1048576).toFixed(1)} MB`;
  const updateClock=()=>{const clock=$('#os-clock');if(clock){clock.textContent=new Date().toLocaleTimeString('ko-KR',{hour:'2-digit',minute:'2-digit',hour12:false});clock.dateTime=new Date().toISOString();}};
  updateClock();setInterval(updateClock,30000);
  let toastTimer;
  function toast(message) {
    $('#toast').textContent = message; $('#toast').classList.add('show');
    clearTimeout(toastTimer); toastTimer = setTimeout(() => $('#toast').classList.remove('show'), 5500);
  }
  async function api(path, method = 'GET', body, options = {}) {
    const isBackgroundRequest = options.quiet || path.startsWith('/assistant/events?') || path.startsWith('/assistant/jobs/') || path.startsWith('/studio/jobs/') || /^\/devices\/[^/]+\/codex\/jobs\//.test(path);
    const finishTask = isBackgroundRequest ? () => {} : window.WorkspaceUI.beginTask(method === 'GET' ? '데이터를 불러오는 중…' : '변경사항을 저장하는 중…');
    try {
    const headers = {};
    if (method !== 'GET') headers[$('meta[name=csrf-header]').content] = $('meta[name=csrf-token]').content;
    if (body !== undefined && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
    const response = await fetch(`/api/v1${path}`, {method, headers, body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body)});
    if (response.status === 401) { location.assign('/login'); throw new Error('로그인이 만료되었습니다.'); }
    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      throw new Error(error.message || (response.status === 403 ? '요청 권한 또는 CSRF 토큰이 만료되었습니다. 페이지를 다시 열어 주세요.' : '요청을 완료하지 못했습니다.'));
    }
    return response.status === 204 || !response.headers.get('content-type')?.includes('json') ? null : await response.json();
    } finally { finishTask(); }
  }
  async function refresh(options = {}) {
    const nextState = await api('/workspace', 'GET', undefined, options);
    if (options.quiet && JSON.stringify(state) === JSON.stringify(nextState)) return;
    if (options.quiet && Object.keys({...state,...nextState}).every(key => ['activity','clips'].includes(key) || JSON.stringify(state[key]) === JSON.stringify(nextState[key]))) {
      const activityChanged = JSON.stringify(state.activity) !== JSON.stringify(nextState.activity);
      const clipsChanged = JSON.stringify(state.clips) !== JSON.stringify(nextState.clips);
      state = nextState;
      if (activityChanged) {
        paint($('#all-recent'), recent(state.activity));
        window.WorkspaceLauncher?.updateActivity(state);
      }
      if (clipsChanged) renderClips();
      return;
    }
    state = nextState;
    render();
  }
  function metric(value) { return value === null || value === undefined ? '—' : `${value.toFixed(0)}%`; }
  function metricTile(name, value, symbol) {
    const tone=value==null?'accent':value>=90?'danger':value>=75?'warning':'accent';
    return `<div class="metric-tile" data-tone="${tone}"><span class="metric-tile-head">${window.WorkspaceUI.icon(symbol)}<small>${name}</small><b>${metric(value)}</b></span>${window.WorkspaceUI.progress(value,name,tone)}</div>`;
  }
  function statusLine(id) {
    const status = statuses.get(id);
    return status ? `${metric(status.cpu)} CPU · ${metric(status.memory)} RAM` : '상태 확인 전';
  }
  function recent(items) {
    return items.map(item => `<button ${openAttrs(item.kind,item.targetId,item.path || '/')}><span class="type">${icons[item.kind] || '◇'}</span><span class="main-copy"><b>${escape(item.label)}</b><small>${escape(item.path || labels[item.kind])}</small></span><time>${timestamp(item.occurredAt)}</time></button>`).join('') || empty('아직 실행한 작업이 없습니다.');
  }
  const paint=(target,html)=>window.WorkspaceLiveDOM?window.WorkspaceLiveDOM.patch(target,html):target.innerHTML=html;
  function renderClips() {
    paint($('#clip-list'), state.clips.filter(item => item.expiresAt > Date.now()).map(item => `<div class="clip-item" data-expiring="${item.expiresAt-Date.now()<300000}"><button data-action="clip-view" data-id="${item.id}"><b>${escape(item.content)}</b><small>${Math.max(1,Math.ceil((item.expiresAt-Date.now())/60000))}분 후 만료</small></button><button data-action="clip-delete" data-id="${item.id}" aria-label="삭제">×</button></div>`).join('') || empty('텍스트를 저장해 다른 세션에서 이어 쓰세요.'));
  }
  function render() {
    document.documentElement.dataset.theme = state.preferences.theme;
    document.documentElement.dataset.compact = state.preferences.compact;
    $('meta[name=theme-color]').content=window.WorkspaceUI.token('bg-app');
    for (const runtime of runtimes.values()) if (runtime.terminal) {runtime.terminal.options.fontSize = state.preferences.terminalFont; runtime.terminal.options.theme=window.WorkspaceUI.terminalTheme();}
    window.WorkspaceLauncher?.sync(state,statuses);
    paint($('#all-recent'), recent(state.activity));
    renderClips();
    paint($('#device-grid'), state.devices.map(item => {
      const status = statuses.get(item.id);
      const stateTone=status?.state==='ONLINE'?'success':status?.state==='OFFLINE'?'danger':'warning';
      const stateLabel=status?.state==='ONLINE'?'온라인':status?.state==='OFFLINE'?'오프라인':'미확인';
      return `<article class="device-card" data-live-key="${escape(item.id)}"><header><div><span class="ui-status" data-state="${stateTone}">${stateLabel}</span><b>${escape(item.name)}</b></div><small>${escape(item.host)}${item.networkMode==='TAILSCALE'?' · Tailscale':''}</small></header><div class="metrics">${metricTile('CPU',status?.cpu,'cpu')}${metricTile('RAM',status?.memory,'memory')}${metricTile('Disk',status?.disk,'disk')}</div><details class="device-extra"><summary>상태 상세</summary><p>${escape(status?.details || '새로고침으로 상태를 확인하세요.')}</p></details><footer><button ${openAttrs('TERMINAL',item.id)}>${item.id === 'local' ? '셸' : 'SSH'}</button><button ${openAttrs('FILES',item.id)}>파일</button><button ${openAttrs('DOCKER',item.id)}>Docker</button><details class="ui-menu device-actions"><summary aria-label="${escape(item.name)} 추가 작업">${window.WorkspaceUI.icon('more')}</summary><div class="ui-menu-content"><button ${openAttrs('GPU',item.id)}>GPU</button>${item.id !== 'local' ? `<button data-action="remote-setup" data-id="${escape(item.id)}">원격 데스크톱</button>` : ''}<button data-action="device-logs" data-id="${escape(item.id)}">로그</button><button data-action="status" data-id="${item.id}">새로고침</button>${item.id !== 'local' ? `<button data-action="device-codex" data-id="${escape(item.id)}">Codex</button><button data-action="device-edit" data-id="${item.id}">설정</button><button data-action="wake" data-id="${item.id}">Wake</button><button data-action="device-delete" data-id="${item.id}" class="danger">삭제</button>` : ''}</div></details></footer></article>`;
    }).join(''));
    for (const [container,kind] of [['file-choices','FILES'],['terminal-choices','TERMINAL']]) {
      paint($(`#${container}`), state.devices.filter(item => kind !== 'REMOTE' || item.remoteProtocol !== 'NONE').map(item => `<button class="device-card choice" ${openAttrs(kind,item.id)}><span class="type">${window.WorkspaceUI.icon(({FILES:'files',TERMINAL:'terminal',REMOTE:'remote'})[kind]||'apps')}</span><b>${escape(item.name)}</b><small>${escape(item.host)}</small></button>`).join('') || empty('장비 설정에서 RDP 또는 VNC 접속을 추가해 주세요.'));
    }
    $('#browser-mode').textContent = `앱 실행 위치: ${modes[state.browserSettings.mode]}`;
    paint($('#remote-choices'), state.devices.filter(item=>item.id!=='local').map(item=>`<article class="remote-device-card" data-live-key="${escape(item.id)}"><header><span aria-hidden="true">${window.WorkspaceUI.icon('remote')}</span><h3>${escape(item.name)}</h3></header><p>${escape(item.host)}</p><p>${item.remoteProtocol==='NONE'?'처음 연결 · 환경 자동 확인':`${escape(item.remoteProtocol)} 연결 설정됨`}</p><button class="primary" data-action="remote-setup" data-id="${escape(item.id)}">${item.remoteProtocol==='NONE'?'화면 준비하기':'연결하기'}</button></article>`).join('') || empty('장비 추가로 사용할 PC나 서버를 등록하세요.'));
    paint($('#apps-list'), state.applications.map(item => `<div class="app-item"><button ${openAttrs('APP',item.id)}><span class="app-icon">${escape(item.name.slice(0,3))}</span><span><b>${escape(item.name)} ${item.pinned ? '★' : ''}</b><small>${escape(item.url)}</small></span><em>${modes[state.browserSettings.mode]}</em></button><button type="button" data-authentication-app="${escape(item.id)}" aria-label="${escape(item.name)} 인증 브라우저에서 열기">인증 브라우저</button><button data-action="app-edit" data-id="${item.id}" aria-label="앱 설정">⚙</button><button data-action="app-delete" data-id="${item.id}" aria-label="앱 삭제">×</button></div>`).join('') || empty('앱 추가로 자주 사용하는 웹사이트를 등록하세요.'));
    renderTabs();
  }
  async function checkStatus(id, options = {}) {
    const status = await api(`/devices/${id}/status`, 'GET', undefined, options);
    statuses.set(id, status);
    const card = $('#device-grid')?.children[state.devices.findIndex(item => item.id === id)];
    if (card) {
      const stateTone = status.state === 'ONLINE' ? 'success' : status.state === 'OFFLINE' ? 'danger' : 'warning';
      const stateLabel = status.state === 'ONLINE' ? '온라인' : status.state === 'OFFLINE' ? '오프라인' : '미확인';
      const label = $('.ui-status', card);
      label.textContent = stateLabel;
      label.dataset.state = stateTone;
      paint($('.metrics', card), metricTile('CPU', status.cpu, 'cpu') + metricTile('RAM', status.memory, 'memory') + metricTile('Disk', status.disk, 'disk'));
      $('.device-extra p', card).textContent = status.details || '새로고침으로 상태를 확인하세요.';
    }
    window.WorkspaceLauncher?.updateStatuses(statuses);
  }
  async function refreshStatuses(options = {}) { for (let offset = 0; offset < state.devices.length; offset += 3) await Promise.all(state.devices.slice(offset,offset+3).map(item => checkStatus(item.id, options).catch(error => toast(error.message)))); }
  function showView(id) {
    if (!document.getElementById(id)) return;
    window.WorkspaceDrawers?.close();
    rememberScreen(id,null);
    if(id!=='home' && !pageTabs.includes(id)){pageTabs.push(id);savePageTabs();}
    activeView=id;
    window.WorkspaceNotes?.open(id).catch(error=>toast(error.message));
    window.WorkspaceCloud?.open(id).catch(error=>toast(error.message));
    window.WorkspaceLogs?.open(id).catch(error=>toast(error.message));
    $('#palette-dialog').close();$('#app-switcher').close();
    window.WorkspacePlanner?.open(id).catch(error=>toast(error.message));
    window.WorkspaceCommunications?.open(id).catch(error=>toast(error.message));
    window.WorkspaceMilitary?.open(id).catch(error=>toast(error.message));
    window.WorkspaceStudio?.open(id).catch(error=>toast(error.message));
    window.WorkspaceDeviceCodex?.open(id).catch(error=>toast(error.message));
    window.WorkspaceGithub?.open(id)?.catch(error=>toast(error.message));
    window.WorkspaceTelemetry?.open(id);
    window.WorkspaceServices?.open(id);
    window.WorkspaceDatabases?.open(id);
    activeTab = null;
    $('#runtime-host').hidden = true;
    document.querySelectorAll('.view').forEach(view => view.classList.toggle('active',view.id === id));
    const content = $('.main>.content');
    if (content && activeTab === null) content.scrollTop = 0;
    const activePane = document.getElementById(id);
    activePane.classList.remove('view-entering');
    void activePane.offsetWidth;
    activePane.classList.add('view-entering');
    for (const runtime of runtimes.values()) runtime.element.hidden = true;
    renderTabs(); window.WorkspaceLauncher?.opened();
    window.dispatchEvent(new CustomEvent('workspace:view', {detail:{id}}));
  }
  window.addEventListener('assistant:navigate', event => {
    const route=event.detail?.route;
    if(appRegistry.get(route)?.route)showView(route);
    const applicationId=event.detail?.applicationId;
    if(applicationId&&state.applications.some(app=>app.id===applicationId))openResource('APP',applicationId).catch(error=>toast(error.message));
  });
  function renderTabs() {
    const icon=name=>window.WorkspaceUI.icon(name);
    const home='<article class="os-task '+(!activeTab&&activeView==='home'?'active':'')+'"><button class="os-task-open" data-view="home"><span class="os-task-icon">'+icon('home')+'</span><b>홈</b><small>앱과 위젯</small></button></article>';
    const pages=pageTabs.map(id=>{const app=appRegistry.get(id);if(!app)return '';return '<article class="os-task '+(!activeTab&&activeView===id?'active':'')+'"><button class="os-task-open" data-view="'+escape(id)+'" aria-current="'+(!activeTab&&activeView===id)+'"><span class="os-task-icon">'+icon(app.icon)+'</span><b>'+escape(app.name)+'</b><small>'+(!activeTab&&activeView===id?'현재 화면':'다시 열기')+'</small></button><button class="os-task-close" data-action="page-close" data-id="'+escape(id)+'" aria-label="'+escape(app.name)+' 닫기">×</button></article>';}).join('');
    const sessions=tabs.map(tab=>'<article class="os-task '+(activeTab===tab.id?'active':'')+'"><button class="os-task-open" data-tab="'+escape(tab.id)+'" aria-current="'+(activeTab===tab.id)+'"><span class="os-task-icon">'+icon({TERMINAL:'terminal',FILES:'files',REMOTE:'remote',APP:'browser'}[tab.kind]||'apps')+'</span><b>'+escape(tab.title)+'</b><small>'+ (runtimes.get(tab.id)?.connected?'연결됨':'저장된 세션')+'</small></button><button class="os-task-pin" data-action="tab-pin" data-id="'+escape(tab.id)+'" aria-label="세션 고정" aria-pressed="'+Boolean(tab.pinned)+'">'+(tab.pinned?'◆':'◇')+'</button><button class="os-task-close" data-action="tab-close" data-id="'+escape(tab.id)+'" aria-label="'+escape(tab.title)+' 닫기">×</button></article>').join('');
    paint($('#switcher-apps'),home+pages+sessions);
    $('#mobile-current-app').textContent=activeTab?tabs.find(tab=>tab.id===activeTab)?.title||'작업':appRegistry.get(activeView)?.name||'홈';
    document.body.dataset.activeView=activeTab?'runtime':activeView;
    document.body.dataset.runtimeFocus=String(Boolean(activeTab&&['TERMINAL','REMOTE','FILES'].includes(tabs.find(tab=>tab.id===activeTab)?.kind)));
    document.querySelectorAll('.activity-rail [data-view],.os-navigation [data-view]').forEach(button=>{if(button.dataset.view===activeView&&!activeTab)button.setAttribute('aria-current','page');else button.removeAttribute('aria-current');});
    const count=pageTabs.length+tabs.length;$('#os-app-count').textContent=count;$('#os-app-count').hidden=count===0;
  }

  let saveQueue = Promise.resolve();
  function saveTabs() { const snapshot = tabs.map(({id,kind,targetId,path,title,pinned}) => ({id,kind,targetId,path,title,pinned})); saveQueue = saveQueue.catch(()=>{}).then(()=>api('/tabs','PUT',{tabs:snapshot})).catch(error=>toast(error.message)); }
  async function openResource(kind,targetId,path = '/',forceNew = false,bypassSetup = false) {
    if(kind==='REMOTE'&&!bypassSetup){openRemoteSetup(targetId);return;}
    $('#palette-dialog').close();
    if (kind === 'APP' && state.browserSettings.mode === 'CLIENT') {
      const app = state.applications.find(item=>item.id === targetId);
      if (!app) throw new Error('앱을 찾을 수 없습니다.');
      // The explicit browser preference permits opening the destination on this client.
      const result = await api('/sessions','POST',{kind,targetId,width:1280,height:800});
      editor('현재 브라우저에서 열기',`<p>${escape(app.name)}</p><a class="external-link" href="${escape(result.url)}" target="_blank" rel="noopener noreferrer">${escape(app.url)} ↗</a>`,async()=>{},'닫기');
      await refresh(); return;
    }
    let tab = !forceNew && tabs.find(item => item.kind === kind && item.targetId === targetId && (kind !== 'FILES' || item.path === path));
    if (!tab) {
      if(tabs.length >= 20) throw new Error('탭은 최대 20개입니다. 사용하지 않는 탭을 닫아 주세요.');
      const target = kind === 'APP' ? state.applications.find(item=>item.id===targetId) : device(targetId);
      if (!target) throw new Error('삭제되었거나 존재하지 않는 대상입니다.');
      tab = {id:window.WorkspaceUI.uuid(),kind,targetId,path:path || '/',title:`${labels[kind]}: ${target.name}`,pinned:false}; tabs.push(tab); saveTabs();
    }
    await activateTab(tab.id);
  }
  async function activateTab(id) {
    window.WorkspaceDrawers?.close();
    window.WorkspaceLogs?.open('runtime').catch(error=>toast(error.message));
    const tab = tabs.find(item => item.id === id); if (!tab) return;
    rememberScreen(activeView,id);
    $('#runtime-host').hidden = false;
    activeTab = id; document.querySelectorAll('.view').forEach(view=>view.classList.remove('active'));
    $('#app-switcher').close(); document.body.dataset.home='false';
    for(const [runtimeId,runtime] of runtimes) runtime.element.hidden = runtimeId !== id;
    let runtime = runtimes.get(id);
    if(!runtime) {
      const element = document.createElement('section'); element.className = 'runtime-pane'; element.dataset.runtime = id;
      $('#runtime-host').append(element); runtime = {element}; runtimes.set(id,runtime);
      element.innerHTML = '<p class="loading" role="status">작업 공간을 준비하는 중…</p>';
      renderTabs();
      try {
        if(tab.kind === 'FILES') await loadFiles(tab);
        else if(['DOCKER','GPU'].includes(tab.kind)) await loadInspection(tab);
        else await connectRuntime(tab);
      } catch(error) {
        element.innerHTML = `<div class="error-state" role="alert">${escape(error.message)}</div>`;
        throw error;
      }
    }
    runtime.element.hidden = false; renderTabs();
    requestAnimationFrame(()=>{ runtime.fit?.fit(); runtime.scale?.(); });
  }
  async function closeTab(id) {
    const runtime = runtimes.get(id);
    if(runtime) {
      runtime.remoteControls?.dispose(); runtime.socket?.close(); runtime.guacamole?.disconnect(); runtime.resizeObserver?.disconnect(); runtime.terminal?.dispose();
      if(runtime.sessionId) await api(`/sessions/${runtime.sessionId}`,'DELETE').catch(()=>{});
      runtime.element.remove(); runtimes.delete(id);
    }
    tabs = tabs.filter(tab=>tab.id !== id); saveTabs(); if(activeTab === id) showView('home'); else renderTabs();
  }
  const fields = {
    input: (name,label,value='',type='text',attributes='') => `<label>${escape(label)}<input name="${name}" type="${type}" value="${escape(value)}" ${attributes}></label>`,
    check: (name,label,value) => `<label class="checkbox"><input type="checkbox" name="${name}" ${value ? 'checked' : ''}>${escape(label)}</label>`,
    select: (name,label,value,options) => `<label>${escape(label)}<select name="${name}">${options.map(([key,text])=>`<option value="${escape(key)}" ${key===value?'selected':''}>${escape(text)}</option>`).join('')}</select></label>`,
    jumpSelects: (name,label,values,options) => `<fieldset class="form-grid"><legend>${escape(label)}</legend>${Array.from({length:5},(_,index)=>`<label>${index+1}번째 경유 장비<select name="${name}"><option value="">사용 안 함</option>${options.map(([key,text])=>`<option value="${escape(key)}" ${values[index]===key?'selected':''}>${escape(text)}</option>`).join('')}</select></label>`).join('')}</fieldset>`
  };
  let submitEditor;
  function editor(title,html,submit,saveLabel = '저장') {
    $('#editor-title').textContent = title; $('#editor-fields').innerHTML = html; $('#editor-error').textContent = '';
    $('#editor-form button[type=submit]').textContent = saveLabel; submitEditor = submit;
    if(!$('#editor-dialog').open) $('#editor-dialog').showModal();
  }
  $('#editor-form').addEventListener('submit',async event=>{
    event.preventDefault(); const button = $('button[type=submit]',event.target); button.disabled = true;
    try { await submitEditor(new FormData(event.target)); $('#editor-dialog').close(); }
    catch(error) { $('#editor-error').textContent = error.message; }
    finally { button.disabled = false; }
  });
  function editDevice(id) {
    const item = device(id) || {name:'',host:'',sshPort:22,username:'',rootPath:'/home',remoteProtocol:'NONE',remotePort:3389,remoteUsername:'',fingerprint:'',mac:'',broadcast:'',pinned:true};
    const jumpOptions=state.devices.filter(candidate=>candidate.id!=='local'&&candidate.id!==id).map(candidate=>[candidate.id,`${candidate.name} · ${candidate.host}`]);
    editor(id ? '장비 설정' : '장비 추가',`<div class="form-grid">${fields.input('name','이름',item.name,'text','required maxlength="80"')}${fields.input('host','호스트 / IP',item.host,'text','required')}${fields.select('networkMode','연결 네트워크',item.networkMode||'DIRECT',[['DIRECT','기본 네트워크'],['TAILSCALE','Tailscale']])}${fields.input('sshPort','SSH 포트',item.sshPort,'number','min="1" max="65535" required')}${fields.input('username','SSH 사용자',item.username)}${fields.input('password',item.hasPassword?'SSH 비밀번호 (비우면 유지)':'SSH 비밀번호','','password','autocomplete="new-password"')}${fields.jumpSelects('jumpDeviceIds','점프 프록시 순서 (최대 5개)',item.jumpDeviceIds||[],jumpOptions)}${fields.input('rootPath','SFTP 루트 경로',item.rootPath,'text','required')}${fields.select('remoteProtocol','원격 화면',item.remoteProtocol,[['NONE','미사용'],['RDP','RDP'],['VNC','VNC']])}${fields.input('remotePort','원격 포트',item.remotePort,'number','min="1" max="65535" required')}${fields.input('remoteUsername','원격 사용자',item.remoteUsername)}${fields.input('remotePassword',item.hasRemotePassword?'원격 비밀번호 (비우면 유지)':'원격 비밀번호','','password','autocomplete="new-password"')}${fields.input('mac','Wake MAC 주소',item.mac)}${fields.input('broadcast','Wake 브로드캐스트 주소',item.broadcast)}${fields.check('pinned','빠른 연결에 고정',item.pinned)}</div><p class="section-hint">위에 선택한 순서대로 SSH 점프 장비를 거칩니다. 각 점프 장비는 먼저 직접 SSH로 등록되어 있어야 합니다.</p>`,async form=>{
      const body = Object.fromEntries(form); body.jumpDeviceIds=form.getAll('jumpDeviceIds').filter(Boolean); body.pinned = form.has('pinned'); for(const key of ['sshPort','remotePort']) body[key]=Number(body[key]);
      await api(id?`/devices/${id}`:'/devices',id?'PUT':'POST',body); await refresh(); toast('장비를 저장했습니다.');
    });
  }
  function connectDevice() {
    const jumpOptions=state.devices.filter(candidate=>candidate.id!=='local').map(candidate=>[candidate.id,`${candidate.name} · ${candidate.host}`]);
    editor('SSH로 장비 연결',fields.input('command','SSH 명령어','','text','required maxlength="512" placeholder="ssh user@192.168.0.10 -p 22" autocomplete="off"')+fields.select('networkMode','연결 네트워크','DIRECT',[['DIRECT','기본 네트워크'],['TAILSCALE','Tailscale']])+fields.input('password','SSH 비밀번호','','password','required maxlength="4096" autocomplete="new-password"')+fields.input('name','장비 이름 (선택)','','text','maxlength="80" placeholder="비우면 사용자@호스트"')+fields.jumpSelects('jumpDeviceIds','점프 프록시 순서 (최대 5개)',[],jumpOptions)+'<p class="section-hint">위에 선택한 순서대로 SSH 점프 장비를 거칩니다. 점프 장비는 먼저 직접 연결해 등록해야 합니다.</p>',async form=>{
      const body=Object.fromEntries(form); body.jumpDeviceIds=form.getAll('jumpDeviceIds').filter(Boolean);
      const saved=await api('/devices/ssh','POST',body);
      await refresh(); showView('devices'); toast(`${saved.name} 연결 및 저장 완료`);
    },'연결하고 저장');
  }
  function editApp(id) {
    const item=state.applications.find(app=>app.id===id) || {name:'',url:'https://',pinned:true};
    editor(id?'앱 설정':'앱 추가',fields.input('name','이름',item.name,'text','required maxlength="80"')+fields.input('url','웹사이트 URL',item.url,'url','required')+fields.check('pinned','빠른 접근에 고정',item.pinned),async form=>{
      await api(id?`/applications/${id}`:'/applications',id?'PUT':'POST',{name:form.get('name'),url:form.get('url'),pinned:form.has('pinned')}); await refresh(); toast('앱을 저장했습니다.');
    });
  }
  function settings() {
    const preferences=state.preferences;
    editor('화면 설정',fields.select('theme','테마',preferences.theme,[['dark','다크'],['light','라이트']])+fields.check('compact','조밀한 레이아웃',preferences.compact)+fields.input('terminalFont','터미널 글자 크기',preferences.terminalFont,'number','min="10" max="24" required')+fields.input('clipMinutes','클립보드 기본 만료 (분)',preferences.clipMinutes,'number','min="1" max="1440" required'),async form=>{
      await api('/preferences','PUT',{theme:form.get('theme'),compact:form.has('compact'),terminalFont:Number(form.get('terminalFont')),clipMinutes:Number(form.get('clipMinutes'))}); await refresh();
    });
  }
  function browserSettings() {
    const preferences=state.browserSettings;
    editor('브라우저 실행 설정',fields.select('mode','앱을 실행할 위치',preferences.mode,Object.entries(modes))+fields.select('deviceId','원격 브라우저 장비',preferences.deviceId,[['','장비 선택'],...state.devices.filter(item=>item.remoteProtocol==='VNC').map(item=>[item.id,item.name])])+fields.input('debugPort','원격 Chromium 디버깅 포트',preferences.debugPort,'number','min="1" max="65535" required')+'<p class="section-hint">서버 Chromium은 Docker 브라우저에서 실행됩니다. 원격 모드는 VNC와 Chromium 원격 디버깅을 제공하는 등록 장비를 사용합니다. 설정은 다음 앱 실행부터 적용됩니다.</p>',async form=>{
      await api('/browser-settings','PUT',{mode:form.get('mode'),deviceId:form.get('deviceId'),debugPort:Number(form.get('debugPort'))}); await refresh();
    });
  }
  function clipAdd() {
    editor('임시 클립보드',`<label>내용<textarea name="content" rows="7" maxlength="32000" required></textarea></label>${fields.input('minutes','만료 시간 (분)',state.preferences.clipMinutes,'number','min="1" max="1440" required')}`,async form=>{await api('/clips','POST',{content:form.get('content'),minutes:Number(form.get('minutes'))});await refresh();});
  }
  function confirmAction(title,message,action) { editor(title,`<p>${escape(message)}</p>`,action,'확인'); }
  async function loadFiles(tab) {
    const runtime=runtimes.get(tab.id); const root=runtime.element;
    root.innerHTML=window.WorkspaceUI.skeleton(3);
    try {
      const listing=await api(`/devices/${tab.targetId}/files?path=${encodeURIComponent(tab.path)}`);
      runtime.listing=listing;
      root.innerHTML=`<div class="tool-view"><div class="tool-bar">
        <button type="button" class="ui-drawer-trigger file-places-trigger" data-drawer-target=".file-layout aside" data-drawer-title="파일 탐색" aria-label="즐겨찾기와 최근 경로" aria-expanded="false" aria-haspopup="dialog">${window.WorkspaceUI.icon('menu')}</button>
        <select data-file-device aria-label="장비">${state.devices.map(item=>`<option value="${item.id}" ${item.id===tab.targetId?'selected':''}>${escape(item.name)}</option>`).join('')}</select>
        <button data-file="parent" aria-label="상위 폴더" title="상위 폴더">${window.WorkspaceUI.icon('up')}<span class="file-action-label">상위</span></button>
        <form class="path-form"><input name="path" class="path" value="${escape(tab.path)}" aria-label="경로"><button type="submit" aria-label="경로로 이동" title="경로로 이동">${window.WorkspaceUI.icon('arrowRight')}<span class="file-action-label">이동</span></button></form>
        <button data-file="reload" aria-label="새로고침" title="새로고침">${window.WorkspaceUI.icon('refresh')}<span class="file-action-label">새로고침</span></button>
        <button data-file="bookmark" aria-label="즐겨찾기 추가" title="즐겨찾기 추가">${window.WorkspaceUI.icon('star')}<span class="file-action-label">즐겨찾기</span></button>
        <button data-file="upload" aria-label="파일 업로드" title="파일 업로드">${window.WorkspaceUI.icon('upload')}<span class="file-action-label">업로드</span></button><input type="file" data-upload hidden>
        <button data-file="mkdir" aria-label="새 폴더" title="새 폴더">${window.WorkspaceUI.icon('folderPlus')}<span class="file-action-label">새 폴더</span></button>
      </div><div class="file-layout"><aside><b>즐겨찾기</b>${state.bookmarks.filter(item=>item.deviceId===tab.targetId).map(item=>`<div class="bookmark-item"><button data-file="navigate" data-path="${escape(item.path)}">${escape(item.path)}</button><button data-action="bookmark-delete" data-id="${item.id}" aria-label="즐겨찾기 삭제">×</button></div>`).join('') || '<small>아직 없습니다.</small>'}<b>최근</b>${state.activity.filter(item=>item.kind==='FILES' && item.targetId===tab.targetId).slice(0,6).map(item=>`<button data-file="navigate" data-path="${escape(item.path)}">${escape(item.path)}</button>`).join('')}</aside>
      <div class="file-table"><div class="file-row head"><span>이름</span><span>수정</span><span>크기</span><span>동작</span></div>${listing.entries.map(item=>`<div class="file-row">
        <button class="file-row-open" data-file="${item.directory?'navigate':'download'}" data-path="${escape(item.path)}"><span class="file-row-icon" data-file-kind="${window.WorkspaceUI.fileKind(item.name,item.directory)}" aria-hidden="true">${window.WorkspaceUI.icon(item.directory?'folder':'file')}</span><span class="file-row-copy"><b>${escape(item.name)}</b><small class="file-mobile-meta">${item.directory?'폴더':bytes(item.size)} · ${timestamp(item.modifiedAt)}</small></span></button>
        <span class="file-modified">${timestamp(item.modifiedAt)}</span><span class="file-size">${item.directory?'—':bytes(item.size)}</span>
        <span class="file-actions"><button data-file="rename" data-path="${escape(item.path)}" data-name="${escape(item.name)}">이름</button><button data-file="delete" data-path="${escape(item.path)}">삭제</button></span>
        <details class="ui-menu file-mobile-actions"><summary aria-label="${escape(item.name)} 작업" title="파일 작업">${window.WorkspaceUI.icon('more')}</summary><div class="ui-menu-content"><button data-file="rename" data-path="${escape(item.path)}" data-name="${escape(item.name)}">이름 변경</button><button data-file="delete" data-path="${escape(item.path)}">삭제</button></div></details>
      </div>`).join('') || empty('비어 있는 폴더입니다.')}</div></div></div>`;
      $('.path-form',root).addEventListener('submit',event=>{event.preventDefault();tab.path=new FormData(event.target).get('path');saveTabs();loadFiles(tab);});
      $('[data-file-device]',root).addEventListener('change',event=>openResource('FILES',event.target.value).catch(error=>toast(error.message)));
      $('[data-upload]',root).addEventListener('change',async event=>{
        const file=event.target.files[0]; if(!file)return;
        const form=new FormData();form.append('path',tab.path);form.append('file',file);
        toast('파일을 서버에 업로드하고 있습니다.');event.target.disabled=true;
        try {await api(`/devices/${tab.targetId}/files`,'POST',form);await loadFiles(tab);toast('업로드했습니다.');}
        catch(error){toast(error.message);event.target.disabled=false;}
      });
    } catch(error) { root.innerHTML=`<div class="runtime-error"><h2>파일을 열 수 없습니다.</h2><p>${escape(error.message)}</p><button data-file="reload">다시 시도</button><button data-file="root">루트로 이동</button></div>`; }
  }
  async function fileAction(button) {
    const tab=tabs.find(item=>item.id===button.closest('[data-runtime]').dataset.runtime); const action=button.dataset.file;
    const path=button.dataset.path;
    const menu=button.closest('.file-mobile-actions');if(menu)menu.open=false;
    if(button.closest('.ui-side-drawer'))window.WorkspaceDrawers?.close();
    if(action==='navigate'||action==='parent'||action==='root') {tab.path=action==='root'?'/':action==='parent'?tab.path.slice(0,tab.path.lastIndexOf('/'))||'/':path;saveTabs();await loadFiles(tab);}
    if(action==='reload') await loadFiles(tab);
    if(action==='upload') $('[data-upload]',runtimes.get(tab.id).element).click();
    if(action==='bookmark'){await api('/bookmarks','POST',{deviceId:tab.targetId,path:tab.path});await refresh();await loadFiles(tab);}
    if(action==='download') {const link=document.createElement('a');link.href=`/api/v1/devices/${tab.targetId}/files/content?path=${encodeURIComponent(path)}`;link.download='';link.click();}
    if(action==='mkdir'||action==='rename') editor(action==='mkdir'?'새 폴더':'이름 변경',fields.input('name','이름',button.dataset.name || '','text','required maxlength="255"'),async form=>{await api(`/devices/${tab.targetId}/files${action==='mkdir'?'/folders':''}`,action==='mkdir'?'POST':'PATCH',{path:action==='mkdir'?tab.path:path,name:form.get('name')});await loadFiles(tab);});
    if(action==='delete') confirmAction('파일 삭제',`${path} 항목을 삭제합니다. 폴더는 비어 있어야 합니다.`,async()=>{await api(`/devices/${tab.targetId}/files?path=${encodeURIComponent(path)}`,'DELETE');await loadFiles(tab);});
  }
  async function loadInspection(tab) {
    const root=runtimes.get(tab.id).element;
    root.innerHTML=`<div class="terminal"><div class="terminal-head"><b>${escape(tab.title)}</b><div class="runtime-actions"><button data-runtime-action="reload" aria-label="새로고침" title="새로고침">${window.WorkspaceUI.icon('refresh')}<span class="runtime-action-label">새로고침</span></button>${tab.kind==='DOCKER'?`<button data-runtime-action="docker" aria-label="컨테이너 제어" title="컨테이너 제어">${window.WorkspaceUI.icon('server')}<span class="runtime-action-label">컨테이너 제어</span></button>`:''}</div></div><pre class="inspection-output">조회 중...</pre></div>`;
    try {const result=await api(`/devices/${tab.targetId}/${tab.kind.toLowerCase()}`);$('.inspection-output',root).textContent=result.output || '출력이 없습니다.';}
    catch(error){$('.inspection-output',root).textContent=error.message;}
  }
  function openRemoteSetup(id) {
    window.WorkspaceRemoteSetup.open(id,{api,editor,refresh,device:device(id),edit:()=>editDevice(id),connect:async()=>{
      const existing=tabs.find(item=>item.kind==='REMOTE'&&item.targetId===id);
      if(existing){const previous=runtimes.get(existing.id);await activateTab(existing.id);if(previous)await runtimeAction(previous.element.querySelector('[data-runtime-action="reconnect"]'));}
      else await openResource('REMOTE',id,'/',false,true);
    }});
  }
  async function connectRuntime(tab) {
    const runtime=runtimes.get(tab.id); const root=runtime.element;
    root.innerHTML=`<div class="terminal live-runtime"><div class="terminal-head"><span><i class="dot amber"></i><b>${escape(tab.title)}</b><small class="connection-state">연결 중...</small></span><div class="actions runtime-actions"><button data-runtime-action="reconnect" aria-label="다시 연결" title="다시 연결">${window.WorkspaceUI.icon('refresh')}<span class="runtime-action-label">다시 연결</span></button><button data-runtime-action="new" aria-label="새 세션" title="새 세션">${window.WorkspaceUI.icon('plus')}<span class="runtime-action-label">새 세션</span></button>${tab.kind!=='TERMINAL'?`<button data-runtime-action="keyboard" aria-expanded="false">키보드</button><button data-runtime-action="fit" aria-pressed="true">화면 맞춤</button>${tab.kind==='REMOTE'?'<button data-runtime-action="remote-setup">연결 도우미</button><button data-runtime-action="secure-attention" title="원격 PC에 Ctrl+Alt+Del 전송">Ctrl+Alt+Del</button>':''}<button data-runtime-action="fullscreen" aria-label="전체 화면" title="전체 화면">${window.WorkspaceUI.icon('maximize')}<span class="runtime-action-label">전체 화면</span></button><button data-runtime-action="paste" aria-label="텍스트 전송" title="텍스트 전송">${window.WorkspaceUI.icon('clip')}<span class="runtime-action-label">텍스트 전송</span></button>`:''}</div></div><div class="stream-area" tabindex="0" aria-label="${tab.kind==='TERMINAL'?'서버 터미널':'원격 화면'}"></div></div>`;
    if(tab.kind==='REMOTE') $('.runtime-actions',root).innerHTML = `<button data-runtime-action="reconnect" title="다시 연결" aria-label="다시 연결">↻</button><button data-runtime-action="keyboard" aria-expanded="false">키보드</button><button data-runtime-action="fullscreen" title="전체 화면" aria-label="전체 화면">⛶</button><details class="ui-menu"><summary aria-label="원격 화면 메뉴">⋯</summary><div class="ui-menu-content"><button data-runtime-action="fit" aria-pressed="true">화면 맞춤</button><button data-runtime-action="paste">클립보드 전송</button><button data-runtime-action="secure-attention">Ctrl+Alt+Del</button><button data-runtime-action="remote-setup">연결 도우미</button></div></details>`;
    const status=message=>{runtime.connected=message==='연결됨';if($('.connection-state',root)){const label=$('.connection-state',root);label.textContent=message;label.classList.add('ui-status');label.dataset.state=runtime.connected?'success':'warning';}renderTabs();};
    try {
      const session=await api('/sessions','POST',{kind:tab.kind,targetId:tab.targetId,width:Math.max(320,Math.min(1920,Math.round(root.clientWidth))),height:Math.max(240,Math.min(1080,Math.round(root.clientHeight-42)))});
      runtime.sessionId=session.id;
      if(session.kind==='CLIENT'){root.innerHTML=`<div class="runtime-error"><a href="${escape(session.url)}" target="_blank" rel="noopener noreferrer">현재 브라우저에서 ${escape(session.label)} 열기 ↗</a></div>`;return;}
      const endpoint=`${location.protocol==='https:'?'wss':'ws'}://${location.host}/ws/runtime/${session.id}`;
      const area=$('.stream-area',root);
      if(tab.kind==='TERMINAL') {
        const terminal=new Terminal({fontSize:state.preferences.terminalFont,fontFamily:'Consolas, monospace',cursorBlink:true,theme:window.WorkspaceUI.terminalTheme(),scrollback:5000});
        const fit=new FitAddon.FitAddon();terminal.loadAddon(fit);terminal.open(area);runtime.terminal=terminal;runtime.fit=fit;
        const socket=new WebSocket(endpoint);runtime.socket=socket;
        let ready=false;
        terminal.onData(data=>{if(socket.readyState===1 && ready)socket.send(JSON.stringify({type:'input',data}));});
        terminal.onResize(size=>{if(socket.readyState===1 && ready)socket.send(JSON.stringify({type:'resize',columns:size.cols,rows:size.rows}));});
        socket.onopen=()=>status('셸 준비 중...');
        socket.onmessage=event=>{
          if(event.data==='{"type":"ready"}'){
            ready=true;fit.fit();socket.send(JSON.stringify({type:'resize',columns:terminal.cols,rows:terminal.rows}));status('연결됨');return;
          }
          terminal.write(event.data);
          if(!ready){ready=true;fit.fit();socket.send(JSON.stringify({type:'resize',columns:terminal.cols,rows:terminal.rows}));}
          status('연결됨');
        };
        socket.onclose=event=>status(event.reason || (ready?'연결 종료 · 다시 연결할 수 있습니다.':'WebSocket 연결이 끊겼습니다. 로그인과 프록시의 WebSocket 설정을 확인하세요.'));
        socket.onerror=()=>status('WebSocket 연결 실패');
        runtime.resizeObserver=new ResizeObserver(()=>{if(!root.hidden)fit.fit();});runtime.resizeObserver.observe(area);fit.fit();terminal.focus();
      } else {
        const tunnel=new Guacamole.WebSocketTunnel(endpoint);const client=new Guacamole.Client(tunnel);runtime.guacamole=client;
        window.WorkspaceRemoteDesktop.attach(runtime,client,area,status);
      }
      await refresh();
    } catch(error) {status(error.message);}
  }
  async function runtimeAction(button) {
    const tab=tabs.find(item=>item.id===button.closest('[data-runtime]').dataset.runtime);const runtime=runtimes.get(tab.id);const action=button.dataset.runtimeAction;
    if(action==='new') await openResource(tab.kind,tab.targetId,tab.path,true);
    if(action==='reload') await loadInspection(tab);
    if(action==='reconnect') {runtime.remoteControls?.dispose();runtime.socket?.close();runtime.guacamole?.disconnect();runtime.terminal?.dispose();runtime.resizeObserver?.disconnect();if(runtime.sessionId)await api(`/sessions/${runtime.sessionId}`,'DELETE').catch(()=>{});await connectRuntime(tab);}
    if(action==='keyboard')runtime.remoteControls?.keyboard(button);
    if(action==='fit')runtime.remoteControls?.fit(button);
    if(action==='secure-attention')runtime.remoteControls?.secureAttention();
    if(action==='remote-setup')openRemoteSetup(tab.targetId);
    if(action==='fullscreen'){if(document.fullscreenElement)await document.exitFullscreen();else if(runtime.element.requestFullscreen)await runtime.element.requestFullscreen();else toast('이 기기에서는 전체 화면을 지원하지 않습니다.');}
    if(action==='paste') {
      editor('원격 클립보드',`<label>원격 화면에 전송할 텍스트<textarea name="text" rows="7" maxlength="32000">${escape(runtime.remoteClipboard || '')}</textarea></label><label>전송 방식<select name="pasteMode"><option value="clipboard">클립보드에만 보내기</option><option value="paste">보내고 붙여넣기 (Ctrl+V)</option><option value="terminal">보내고 터미널에 붙여넣기 (Ctrl+Shift+V)</option></select></label><p class="section-hint">붙여넣기를 선택하면 원격에서 마지막으로 선택한 입력창에 입력됩니다. 터미널에서 줄바꿈이 있는 텍스트를 붙여넣으면 명령이 실행될 수 있습니다.</p>`,async form=>{
        await window.WorkspaceRemoteDesktop.sendClipboard(runtime,form.get('text'),form.get('pasteMode'),text=>api(`/sessions/${runtime.sessionId}/clipboard`,'POST',{text}));
        toast('클립보드를 전송했습니다.');
      },'전송');
      $('#editor-dialog').addEventListener('close',()=>runtime.remoteControls?.focus(),{once:true});
    }
    if(action==='docker') editor('컨테이너 제어',fields.input('container','컨테이너 이름 또는 ID','','text','required')+fields.select('action','동작','restart',[['start','시작'],['stop','중지'],['restart','재시작']]),async form=>{const result=await api(`/devices/${tab.targetId}/docker`,'POST',Object.fromEntries(form));await loadInspection(tab);toast(result.output || '요청을 실행했습니다.');},'실행');
  }
  let searchTimer, searchVersion=0;
  async function search() {
    const version=++searchVersion;
    const text=$('#paletteInput').value,query=text.trim().toLowerCase();
    const [server,services,databases]=await Promise.allSettled([api(`/search?query=${encodeURIComponent(text)}`),api('/services'),api('/databases')]);
    const serverResults=server.status==='fulfilled'?server.value:[],seen=new Set();
    const results=[...serverResults,...state.activity.filter(item=>`${item.label} ${item.path||''}`.toLowerCase().includes(query))]
      .filter(item=>{const key=[item.kind,item.targetId,item.path||'/'].join('|');if(seen.has(key))return false;seen.add(key);return true;}).slice(0,80);
    if(version!==searchVersion)return;
    appRegistry.sync(state);
    const appResults=appRegistry.search(text).map(app=>`<button ${appRegistry.attributes(app)}><span>${window.WorkspaceUI.icon(app.icon)}</span><b>${escape(app.name)}</b><small>앱</small></button>`).join('');
    const serviceResults=(services.status==='fulfilled'?services.value:[]).filter(item=>item.name.toLowerCase().includes(query)).slice(0,15)
      .map(item=>`<button data-service-open="${escape(item.id)}"><span>${window.WorkspaceUI.icon(item.icon)}</span><b>${escape(item.name)}</b><small>Service</small></button>`).join('');
    const databaseResults=(databases.status==='fulfilled'?databases.value:[]).filter(item=>item.name.toLowerCase().includes(query)).slice(0,15)
      .map(item=>`<button data-database-open="${escape(item.id)}"><span>${window.WorkspaceUI.icon('disk')}</span><b>${escape(item.name)}</b><small>Database</small></button>`).join('');
    const actionResults=state.devices.filter(item=>item.name.toLowerCase().includes(query)).slice(0,8)
      .map(item=>`<button data-open="TERMINAL" data-target="${escape(item.id)}"><span>${window.WorkspaceUI.icon('terminal')}</span><b>${escape(item.name)} 터미널 열기</b><small>Action</small></button>`).join('');
    $('#search-results').innerHTML=appResults+serviceResults+databaseResults+actionResults+results
      .map(item=>`<button ${openAttrs(item.kind,item.targetId,item.path)}><span>${icons[item.kind]}</span><b>${escape(item.label)}</b><small>${labels[item.kind]}</small></button>`).join('')||empty('검색 결과가 없습니다.');
  }
  function palette() {$('#palette-dialog').showModal();$('#paletteInput').focus();search().catch(error=>toast(error.message));}
  $('#paletteInput').addEventListener('input',()=>{clearTimeout(searchTimer);searchTimer=setTimeout(()=>search().catch(error=>toast(error.message)),180);});
  document.addEventListener('keydown',event=>{
    if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='k'){event.preventDefault();palette();}
    if(event.altKey&&event.key==='ArrowLeft'&&!['INPUT','TEXTAREA'].includes(document.activeElement?.tagName)){event.preventDefault();navigateBack().catch(error=>toast(error.message));}
    if($('#palette-dialog').open && event.key==='ArrowDown'){event.preventDefault();const buttons=[...$('#search-results').querySelectorAll('button')];buttons[(buttons.indexOf(document.activeElement)+1)%buttons.length]?.focus();}
    if($('#palette-dialog').open && event.key==='Enter' && document.activeElement===$('#paletteInput')){event.preventDefault();$('#search-results button')?.click();}
  });
  document.addEventListener('click',async event=>{
    const button=event.target.closest('button,a[data-action]');if(!button)return;
    try {
      if(button.dataset.view)showView(button.dataset.view);
      if(button.dataset.open)await openResource(button.dataset.open,button.dataset.target,button.dataset.path);
      if(button.dataset.tab)await activateTab(button.dataset.tab);
      if(button.dataset.file)await fileAction(button);
      if(button.dataset.runtimeAction)await runtimeAction(button);
      const id=button.dataset.id;
      switch(button.dataset.action){
        case 'dialog-close':button.closest('dialog').close();break;
        case 'palette':palette();break;
        case 'app-switcher':appSwitcher();break;
        case 'os-back':await navigateBack();break;
        case 'os-fullscreen':if(document.fullscreenElement)await document.exitFullscreen();else if(document.documentElement.requestFullscreen)await document.documentElement.requestFullscreen();else toast('이 브라우저에서는 전체 화면 전환을 지원하지 않습니다.');break;
        case 'page-close':pageTabs=pageTabs.filter(value=>value!==id);savePageTabs();if(activeTab===null&&activeView===id)showView('home');else renderTabs();break;
        case 'settings':settings();break;
        case 'tailscale-settings':window.WorkspaceTailscale.open();break;
        case 'browser-settings':browserSettings();break;
        case 'device-add':connectDevice();break;
        case 'device-manual':editDevice();break;
        case 'device-logs':window.WorkspaceLogs?.select(id);showView('logs');break;
        case 'device-codex':window.WorkspaceDeviceCodex?.select(id);showView('device-codex');break;
        case 'remote-setup':openRemoteSetup(id);break;
        case 'device-edit':editDevice(id);break;
        case 'app-add':editApp();break;
        case 'app-edit':editApp(id);break;
        case 'status':await checkStatus(id);break;
        case 'refresh-status':await refreshStatuses();break;
        case 'wake':await api(`/devices/${id}/wake`,'POST');toast('Wake 패킷을 전송했습니다. 장비 기동 여부는 새로고침으로 확인하세요.');break;
        case 'device-delete':confirmAction('장비 삭제','장비 프로필과 해당 즐겨찾기를 삭제합니다.',async()=>{for(const tab of [...tabs].filter(item=>item.targetId===id))await closeTab(tab.id);await api(`/devices/${id}`,'DELETE');await refresh();});break;
        case 'app-delete':confirmAction('앱 삭제','등록한 앱을 삭제합니다.',async()=>{for(const tab of [...tabs].filter(item=>item.targetId===id))await closeTab(tab.id);await api(`/applications/${id}`,'DELETE');await refresh();});break;
        case 'clip-add':clipAdd();break;
        case 'clip-delete':await api(`/clips/${id}`,'DELETE');await refresh();break;
        case 'clip-view':{const clip=state.clips.find(item=>item.id===id);if(!clip || clip.expiresAt<=Date.now())throw new Error('만료된 클립보드입니다.');editor('클립보드 내용',`<textarea rows="9" readonly aria-label="클립보드 내용">${escape(clip.content)}</textarea><p class="section-hint">텍스트를 선택해 복사할 수 있습니다.</p>`,async()=>{},'닫기');break;}
        case 'bookmark-delete':await api(`/bookmarks/${id}`,'DELETE');await refresh();if(activeTab && tabs.find(item=>item.id===activeTab)?.kind==='FILES')await loadFiles(tabs.find(item=>item.id===activeTab));break;
        case 'tab-close':await closeTab(id);break;
        case 'tab-pin':{const tab=tabs.find(item=>item.id===id);tab.pinned=!tab.pinned;tabs.sort((left,right)=>Number(right.pinned)-Number(left.pinned));saveTabs();renderTabs();break;}
      }
    }catch(error){toast(error.message);}
  });
  for(const dialog of document.querySelectorAll('dialog'))dialog.addEventListener('click',event=>{if(event.target===dialog){const box=dialog.getBoundingClientRect();if(event.clientX<box.left||event.clientX>box.right||event.clientY<box.top||event.clientY>box.bottom)dialog.close();}});
  window.addEventListener('beforeunload',()=>{for(const runtime of runtimes.values()){runtime.remoteControls?.dispose();runtime.socket?.close();runtime.guacamole?.disconnect();}});
  window.WorkspacePlanner?.init({api,editor,fields,escape,toast,confirmAction});
  window.WorkspaceCommunications?.init({api,editor,fields,escape,toast,confirmAction});
  window.WorkspaceMilitary?.init({api,editor,fields,escape,toast,confirmAction});
  window.WorkspaceLogs?.init({api,escape,toast});
  window.WorkspaceNotes?.init({api,escape,toast,editor,confirmAction});
  window.WorkspaceCloud?.init({api,escape,toast,editor,confirmAction});
  window.WorkspaceNas?.init({api,escape,editor,toast});
  window.WorkspaceTailscale?.init({api,confirmAction});
  window.addEventListener('DOMContentLoaded', () => window.WorkspaceGithub?.init({api}));
  window.addEventListener('DOMContentLoaded', () => window.WorkspaceStudio?.init({api,editor,escape,toast,confirmAction,openTerminal: id=>openResource('TERMINAL',id)}));
  window.addEventListener('DOMContentLoaded', () => window.WorkspaceDeviceCodex?.init({api,editor,escape,toast,confirm:message=>window.WorkspaceAssistantRuntime.confirm(message)}));
  window.WorkspaceLauncher.init({api,editor,toast,state:()=>state,showHome:()=>showView('home')});
  window.WorkspaceAssistantRuntime={
    api,editor,toast,
    confirm(message){return new Promise(resolve=>{let accepted=false;const dialog=$('#editor-dialog');dialog.addEventListener('close',()=>resolve(accepted),{once:true});editor('Codex 확인',`<p>${escape(message)}</p>`,async()=>{accepted=true;},'확인');});}
  };
  window.visualViewport?.addEventListener('resize',()=>{document.documentElement.style.setProperty('--viewport-height',window.visualViewport.height+'px');for(const runtime of runtimes.values()){runtime.fit?.fit();runtime.scale?.();}});
  // Coalesce bursts and serialize refreshes. Foreground operations finish before live updates.
  const liveTopics=new Set();let liveRunning=false,liveTimer;
  function queueLive(topics){
    topics.forEach(topic=>liveTopics.add(topic));
    if(!liveTopics.size||liveRunning||liveTimer)return;
    liveTimer=setTimeout(drainLive,180);
  }
  async function drainLive(){
    liveTimer=null;
    if(document.hidden)return;
    if(document.documentElement.dataset.loading==='true'){liveTimer=setTimeout(drainLive,250);return;}
    const topics=new Set(liveTopics);liveTopics.clear();liveRunning=true;
    const all=topics.has('all'), changed=name=>all||topics.has(name);
    try{
      if(changed('workspace')||changed('devices'))await refresh({quiet:true});
      if(!activeTab){
        const view=activeView;
        const updates=[];
        if(['home','devices'].includes(view)&&(all||topics.has('devices')))updates.push(refreshStatuses({quiet:true}));
        if(view==='home')updates.push(window.WorkspaceLauncher?.refreshWidgets({quiet:true}));
        if(['calendar','timetable'].includes(view)&&changed(view==='timetable'?'timetables':'calendar'))updates.push(window.WorkspacePlanner?.refresh(view));
        const modules={communications:'Communications',services:'Services',telemetry:'Telemetry',notes:'Notes',databases:'Databases',github:'Github',cloud:'Cloud',military:'Military'};
        if(modules[view]&&changed(view))updates.push(window['Workspace'+modules[view]]?.refresh?.());
        const results=await Promise.allSettled(updates);
        if(results.some(result=>result.status==='rejected'))markLiveStale();
      }
    }catch{markLiveStale();}
    finally{liveRunning=false;if(liveTopics.size)queueLive([]);}
  }
  function markLiveStale(){const label=$('#workspace-live');if(label){label.textContent='갱신 재시도 중';label.dataset.state='stale';}}
  window.addEventListener('workspace:invalidate',event=>queueLive(event.detail.topics));
  window.addEventListener('workspace:heartbeat',()=>queueLive(['all']));
  document.addEventListener('visibilitychange',()=>{if(!document.hidden)queueLive(['all']);});
  render();refreshStatuses({quiet:true});
  setInterval(()=>{if(!document.hidden&&!window.WorkspaceRealtime?.connected())queueLive(['all']);},60000);
  window.WorkspaceRealtime?.start();
})();
