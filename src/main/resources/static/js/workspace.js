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
  const empty = message => `<p class="empty-state">${escape(message)}</p>`;
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
  async function api(path, method = 'GET', body) {
    const headers = {};
    if (method !== 'GET') headers[$('meta[name=csrf-header]').content] = $('meta[name=csrf-token]').content;
    if (body !== undefined && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
    const response = await fetch(`/api/v1${path}`, {method, headers, body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body)});
    if (response.status === 401) { location.assign('/login'); throw new Error('로그인이 만료되었습니다.'); }
    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      throw new Error(error.message || (response.status === 403 ? '요청 권한 또는 CSRF 토큰이 만료되었습니다. 페이지를 다시 열어 주세요.' : '요청을 완료하지 못했습니다.'));
    }
    return response.status === 204 || !response.headers.get('content-type')?.includes('json') ? null : response.json();
  }
  async function refresh() { state = await api('/workspace'); render(); }
  function metric(value) { return value === null || value === undefined ? '—' : `${value.toFixed(0)}%`; }
  function statusLine(id) {
    const status = statuses.get(id);
    return status ? `${metric(status.cpu)} CPU · ${metric(status.memory)} RAM` : '상태 확인 전';
  }
  function recent(items) {
    return items.map(item => `<button ${openAttrs(item.kind,item.targetId,item.path || '/')}><span class="type">${icons[item.kind] || '◇'}</span><span class="main-copy"><b>${escape(item.label)}</b><small>${escape(item.path || labels[item.kind])}</small></span><time>${timestamp(item.occurredAt)}</time></button>`).join('') || empty('아직 실행한 작업이 없습니다.');
  }
  function render() {
    document.documentElement.dataset.theme = state.preferences.theme;
    document.documentElement.dataset.compact = state.preferences.compact;
    $('meta[name=theme-color]').content=window.WorkspaceUI.token('bg-app');
    for (const runtime of runtimes.values()) if (runtime.terminal) {runtime.terminal.options.fontSize = state.preferences.terminalFont; runtime.terminal.options.theme=window.WorkspaceUI.terminalTheme();}
    window.WorkspaceLauncher?.sync(state,statuses);
    $('#all-recent').innerHTML = recent(state.activity);
    $('#clip-list').innerHTML = state.clips.filter(item => item.expiresAt > Date.now()).map(item => `<div class="clip-item"><button data-action="clip-view" data-id="${item.id}"><b>${escape(item.content)}</b><small>${Math.max(1,Math.ceil((item.expiresAt-Date.now())/60000))}분 후 만료</small></button><button data-action="clip-delete" data-id="${item.id}" aria-label="삭제">×</button></div>`).join('') || empty('텍스트를 저장해 다른 세션에서 이어 쓰세요.');
    $('#device-grid').innerHTML = state.devices.map(item => {
      const status = statuses.get(item.id);
      return `<article class="device-card"><header><div><i class="dot ${status?.state === 'ONLINE' ? 'green' : 'amber'}"></i><b>${escape(item.name)}</b></div><small>${escape(item.host)}${item.networkMode==='TAILSCALE'?' · Tailscale':''}</small></header><div class="metrics"><div><small>CPU</small><b>${metric(status?.cpu)}</b></div><div><small>RAM</small><b>${metric(status?.memory)}</b></div><div><small>DISK</small><b>${metric(status?.disk)}</b></div></div><p class="device-note">${escape(status?.details || '새로고침으로 상태를 확인하세요.')}</p><footer><button ${openAttrs('TERMINAL',item.id)}>${item.id === 'local' ? '셸' : 'SSH'}</button><button ${openAttrs('FILES',item.id)}>파일</button><button ${openAttrs('DOCKER',item.id)}>Docker</button><button ${openAttrs('GPU',item.id)}>GPU</button>${item.remoteProtocol !== 'NONE' ? `<button ${openAttrs('REMOTE',item.id)}>원격</button>` : ''}<button data-action="device-logs" data-id="${escape(item.id)}">로그</button><button data-action="status" data-id="${item.id}">새로고침</button>${item.id !== 'local' ? `<button data-action="remote-setup" data-id="${item.id}">원격 자동 연결</button><button data-action="device-edit" data-id="${item.id}">설정</button><button data-action="wake" data-id="${item.id}">Wake</button><button data-action="device-delete" data-id="${item.id}" class="danger">삭제</button>` : ''}</footer></article>`;
    }).join('');
    for (const [container,kind] of [['file-choices','FILES'],['terminal-choices','TERMINAL'],['remote-choices','REMOTE']]) {
      $(`#${container}`).innerHTML = state.devices.filter(item => kind !== 'REMOTE' || item.remoteProtocol !== 'NONE').map(item => `<button class="device-card choice" ${openAttrs(kind,item.id)}><span class="type">${icons[kind]}</span><b>${escape(item.name)}</b><small>${escape(item.host)}</small></button>`).join('') || empty('장비 설정에서 RDP 또는 VNC 접속을 추가해 주세요.');
    }
    $('#browser-mode').textContent = `앱 실행 위치: ${modes[state.browserSettings.mode]}`;
    $('#apps-list').innerHTML = state.applications.map(item => `<div class="app-item"><button ${openAttrs('APP',item.id)}><span class="app-icon">${escape(item.name.slice(0,3))}</span><span><b>${escape(item.name)} ${item.pinned ? '★' : ''}</b><small>${escape(item.url)}</small></span><em>${modes[state.browserSettings.mode]}</em></button><button data-action="app-edit" data-id="${item.id}" aria-label="앱 설정">⚙</button><button data-action="app-delete" data-id="${item.id}" aria-label="앱 삭제">×</button></div>`).join('') || empty('앱 추가로 자주 사용하는 웹사이트를 등록하세요.');
    renderTabs();
  }
  async function checkStatus(id) { statuses.set(id, await api(`/devices/${id}/status`)); render(); }
  async function refreshStatuses() { for (let offset = 0; offset < state.devices.length; offset += 3) await Promise.all(state.devices.slice(offset,offset+3).map(item => checkStatus(item.id).catch(error => toast(error.message)))); }
  function showView(id) {
    rememberScreen(id,null);
    if(id!=='home' && !pageTabs.includes(id)){pageTabs.push(id);savePageTabs();}
    activeView=id;
    window.WorkspaceNotes?.open(id).catch(error=>toast(error.message));
    window.WorkspaceCloud?.open(id).catch(error=>toast(error.message));
    window.WorkspaceLogs?.open(id).catch(error=>toast(error.message));
    $('#palette-dialog').close();$('#app-switcher').close();
    window.WorkspacePlanner?.open(id).catch(error=>toast(error.message));
    window.WorkspaceStudio?.open(id).catch(error=>toast(error.message));
    activeTab = null;
    $('#runtime-host').hidden = true;
    document.querySelectorAll('.view').forEach(view => view.classList.toggle('active',view.id === id));
    for (const runtime of runtimes.values()) runtime.element.hidden = true;
    renderTabs(); window.WorkspaceLauncher?.opened();
  }
  function renderTabs() {
    const icon=name=>window.WorkspaceUI.icon(name);
    const home='<article class="os-task '+(!activeTab&&activeView==='home'?'active':'')+'"><button class="os-task-open" data-view="home"><span class="os-task-icon">'+icon('home')+'</span><b>홈</b><small>앱과 위젯</small></button></article>';
    const pages=pageTabs.map(id=>{const app=appRegistry.get(id);if(!app)return '';return '<article class="os-task '+(!activeTab&&activeView===id?'active':'')+'"><button class="os-task-open" data-view="'+escape(id)+'" aria-current="'+(!activeTab&&activeView===id)+'"><span class="os-task-icon">'+icon(app.icon)+'</span><b>'+escape(app.name)+'</b><small>'+(!activeTab&&activeView===id?'현재 화면':'다시 열기')+'</small></button><button class="os-task-close" data-action="page-close" data-id="'+escape(id)+'" aria-label="'+escape(app.name)+' 닫기">×</button></article>';}).join('');
    const sessions=tabs.map(tab=>'<article class="os-task '+(activeTab===tab.id?'active':'')+'"><button class="os-task-open" data-tab="'+escape(tab.id)+'" aria-current="'+(activeTab===tab.id)+'"><span class="os-task-icon">'+icon({TERMINAL:'terminal',FILES:'files',REMOTE:'remote',APP:'browser'}[tab.kind]||'apps')+'</span><b>'+escape(tab.title)+'</b><small>'+ (runtimes.get(tab.id)?.connected?'연결됨':'저장된 세션')+'</small></button><button class="os-task-pin" data-action="tab-pin" data-id="'+escape(tab.id)+'" aria-label="세션 고정" aria-pressed="'+Boolean(tab.pinned)+'">'+(tab.pinned?'◆':'◇')+'</button><button class="os-task-close" data-action="tab-close" data-id="'+escape(tab.id)+'" aria-label="'+escape(tab.title)+' 닫기">×</button></article>').join('');
    $('#switcher-apps').innerHTML=home+pages+sessions;
    $('#mobile-current-app').textContent=activeTab?tabs.find(tab=>tab.id===activeTab)?.title||'작업':appRegistry.get(activeView)?.name||'홈';
    const count=pageTabs.length+tabs.length;$('#os-app-count').textContent=count;$('#os-app-count').hidden=count===0;
  }

  let saveQueue = Promise.resolve();
  function saveTabs() { const snapshot = tabs.map(({id,kind,targetId,path,title,pinned}) => ({id,kind,targetId,path,title,pinned})); saveQueue = saveQueue.catch(()=>{}).then(()=>api('/tabs','PUT',{tabs:snapshot})).catch(error=>toast(error.message)); }
  async function openResource(kind,targetId,path = '/',forceNew = false) {
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
      if(tab.kind === 'FILES') await loadFiles(tab);
      else if(['DOCKER','GPU'].includes(tab.kind)) await loadInspection(tab);
      else await connectRuntime(tab);
    }
    runtime.element.hidden = false; renderTabs();
    requestAnimationFrame(()=>{ runtime.fit?.fit(); runtime.scale?.(); });
  }
  async function closeTab(id) {
    const runtime = runtimes.get(id);
    if(runtime) {
      runtime.socket?.close(); runtime.guacamole?.disconnect(); runtime.resizeObserver?.disconnect(); runtime.terminal?.dispose();
      if(runtime.sessionId) await api(`/sessions/${runtime.sessionId}`,'DELETE').catch(()=>{});
      runtime.element.remove(); runtimes.delete(id);
    }
    tabs = tabs.filter(tab=>tab.id !== id); saveTabs(); if(activeTab === id) showView('home'); else renderTabs();
  }
  const fields = {
    input: (name,label,value='',type='text',attributes='') => `<label>${escape(label)}<input name="${name}" type="${type}" value="${escape(value)}" ${attributes}></label>`,
    check: (name,label,value) => `<label class="checkbox"><input type="checkbox" name="${name}" ${value ? 'checked' : ''}>${escape(label)}</label>`,
    select: (name,label,value,options) => `<label>${escape(label)}<select name="${name}">${options.map(([key,text])=>`<option value="${escape(key)}" ${key===value?'selected':''}>${escape(text)}</option>`).join('')}</select></label>`
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
    editor(id ? '장비 설정' : '장비 추가',`<div class="form-grid">${fields.input('name','이름',item.name,'text','required maxlength="80"')}${fields.input('host','호스트 / IP',item.host,'text','required')}${fields.select('networkMode','연결 네트워크',item.networkMode||'DIRECT',[['DIRECT','기본 네트워크'],['TAILSCALE','Tailscale']])}${fields.input('sshPort','SSH 포트',item.sshPort,'number','min="1" max="65535" required')}${fields.input('username','SSH 사용자',item.username)}${fields.input('password',item.hasPassword?'SSH 비밀번호 (비우면 유지)':'SSH 비밀번호','','password','autocomplete="new-password"')}${fields.input('rootPath','SFTP 루트 경로',item.rootPath,'text','required')}${fields.select('remoteProtocol','원격 화면',item.remoteProtocol,[['NONE','미사용'],['RDP','RDP'],['VNC','VNC']])}${fields.input('remotePort','원격 포트',item.remotePort,'number','min="1" max="65535" required')}${fields.input('remoteUsername','원격 사용자',item.remoteUsername)}${fields.input('remotePassword',item.hasRemotePassword?'원격 비밀번호 (비우면 유지)':'원격 비밀번호','','password','autocomplete="new-password"')}${fields.input('mac','Wake MAC 주소',item.mac)}${fields.input('broadcast','Wake 브로드캐스트 주소',item.broadcast)}${fields.check('pinned','빠른 연결에 고정',item.pinned)}</div><p class="section-hint">Tailscale 선택 시 호스트에 장비의 Tailscale IP 또는 MagicDNS 이름을 입력하세요. 서버의 기존 Tailscale 로그인이 필요합니다. SSH 호스트 키는 자동으로 관리됩니다. RDP 서버 인증서는 guacd가 신뢰하는 인증서여야 합니다.</p>`,async form=>{
      const body = Object.fromEntries(form); body.pinned = form.has('pinned'); for(const key of ['sshPort','remotePort']) body[key]=Number(body[key]);
      await api(id?`/devices/${id}`:'/devices',id?'PUT':'POST',body); await refresh(); toast('장비를 저장했습니다.');
    });
  }
  function connectDevice() {
    editor('SSH로 장비 연결',fields.input('command','SSH 명령어','','text','required maxlength="512" placeholder="ssh user@192.168.0.10 -p 22" autocomplete="off"')+fields.select('networkMode','연결 네트워크','DIRECT',[['DIRECT','기본 네트워크'],['TAILSCALE','Tailscale']])+fields.input('password','SSH 비밀번호','','password','required maxlength="4096" autocomplete="new-password"')+fields.input('name','장비 이름 (선택)','','text','maxlength="80" placeholder="비우면 사용자@호스트"')+'<p class="section-hint">Tailscale을 선택하면 명령어에 Tailscale IP 또는 MagicDNS 이름을 사용하세요. 연결에 성공하면 장비를 저장하고 파일 경로를 자동으로 설정합니다. 첫 연결의 호스트 키는 자동 저장하며 이후 변경되면 연결을 차단합니다.</p>',async form=>{
      const saved=await api('/devices/ssh','POST',Object.fromEntries(form));
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
    root.innerHTML='<p class="loading">파일을 불러오는 중...</p>';
    try {
      const listing=await api(`/devices/${tab.targetId}/files?path=${encodeURIComponent(tab.path)}`);
      runtime.listing=listing;
      root.innerHTML=`<div class="tool-view"><div class="tool-bar"><select data-file-device aria-label="장비">${state.devices.map(item=>`<option value="${item.id}" ${item.id===tab.targetId?'selected':''}>${escape(item.name)}</option>`).join('')}</select><button data-file="parent" aria-label="상위 폴더">↑</button><form class="path-form"><input name="path" class="path" value="${escape(tab.path)}" aria-label="경로"><button type="submit">이동</button></form><button data-file="reload" title="새로고침">↻</button><button data-file="bookmark" title="즐겨찾기">☆</button><label class="upload-button">업로드<input type="file" data-upload hidden></label><button data-file="mkdir">새 폴더</button></div><div class="file-layout"><aside><b>즐겨찾기</b>${state.bookmarks.filter(item=>item.deviceId===tab.targetId).map(item=>`<div class="bookmark-item"><button data-file="navigate" data-path="${escape(item.path)}">${escape(item.path)}</button><button data-action="bookmark-delete" data-id="${item.id}" aria-label="즐겨찾기 삭제">×</button></div>`).join('') || '<small>아직 없습니다.</small>'}<b>최근</b>${state.activity.filter(item=>item.kind==='FILES' && item.targetId===tab.targetId).slice(0,6).map(item=>`<button data-file="navigate" data-path="${escape(item.path)}">${escape(item.path)}</button>`).join('')}</aside><div class="file-table"><div class="file-row head"><span>이름</span><span>수정</span><span>크기</span><span>동작</span></div>${listing.entries.map(item=>`<div class="file-row"><button data-file="${item.directory?'navigate':'download'}" data-path="${escape(item.path)}"><span>${item.directory?'📁':'📄'}</span> ${escape(item.name)}</button><span>${timestamp(item.modifiedAt)}</span><span>${item.directory?'—':bytes(item.size)}</span><span class="file-actions"><button data-file="rename" data-path="${escape(item.path)}" data-name="${escape(item.name)}">이름</button><button data-file="delete" data-path="${escape(item.path)}">삭제</button></span></div>`).join('') || empty('비어 있는 폴더입니다.')}</div></div></div>`;
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
    if(action==='navigate'||action==='parent'||action==='root') {tab.path=action==='root'?'/':action==='parent'?tab.path.slice(0,tab.path.lastIndexOf('/'))||'/':path;saveTabs();await loadFiles(tab);}
    if(action==='reload') await loadFiles(tab);
    if(action==='bookmark'){await api('/bookmarks','POST',{deviceId:tab.targetId,path:tab.path});await refresh();await loadFiles(tab);}
    if(action==='download') {const link=document.createElement('a');link.href=`/api/v1/devices/${tab.targetId}/files/content?path=${encodeURIComponent(path)}`;link.download='';link.click();}
    if(action==='mkdir'||action==='rename') editor(action==='mkdir'?'새 폴더':'이름 변경',fields.input('name','이름',button.dataset.name || '','text','required maxlength="255"'),async form=>{await api(`/devices/${tab.targetId}/files${action==='mkdir'?'/folders':''}`,action==='mkdir'?'POST':'PATCH',{path:action==='mkdir'?tab.path:path,name:form.get('name')});await loadFiles(tab);});
    if(action==='delete') confirmAction('파일 삭제',`${path} 항목을 삭제합니다. 폴더는 비어 있어야 합니다.`,async()=>{await api(`/devices/${tab.targetId}/files?path=${encodeURIComponent(path)}`,'DELETE');await loadFiles(tab);});
  }
  async function loadInspection(tab) {
    const root=runtimes.get(tab.id).element;
    root.innerHTML=`<div class="terminal"><div class="terminal-head"><b>${escape(tab.title)}</b><div><button data-runtime-action="reload">새로고침</button>${tab.kind==='DOCKER'?'<button data-runtime-action="docker">컨테이너 제어</button>':''}</div></div><pre class="inspection-output">조회 중...</pre></div>`;
    try {const result=await api(`/devices/${tab.targetId}/${tab.kind.toLowerCase()}`);$('.inspection-output',root).textContent=result.output || '출력이 없습니다.';}
    catch(error){$('.inspection-output',root).textContent=error.message;}
  }
  async function connectRuntime(tab) {
    const runtime=runtimes.get(tab.id); const root=runtime.element;
    root.innerHTML=`<div class="terminal live-runtime"><div class="terminal-head"><span><i class="dot amber"></i><b>${escape(tab.title)}</b><small class="connection-state">연결 중...</small></span><div class="actions"><button data-runtime-action="reconnect">다시 연결</button><button data-runtime-action="new">새 세션</button>${tab.kind!=='TERMINAL'?'<button data-runtime-action="fullscreen">전체 화면</button><button data-runtime-action="paste">텍스트 전송</button>':''}</div></div><div class="stream-area" tabindex="0" aria-label="${tab.kind==='TERMINAL'?'서버 터미널':'원격 화면'}"></div></div>`;
    const status=message=>{runtime.connected=message==='연결됨';if($('.connection-state',root))$('.connection-state',root).textContent=message;renderTabs();};
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
        socket.onmessage=event=>{terminal.write(event.data);if(!ready){ready=true;fit.fit();socket.send(JSON.stringify({type:'resize',columns:terminal.cols,rows:terminal.rows}));}status('연결됨');};
        socket.onclose=event=>status(event.reason || '연결 종료 · 다시 연결할 수 있습니다.');socket.onerror=()=>status('연결 실패');
        runtime.resizeObserver=new ResizeObserver(()=>{if(!root.hidden)fit.fit();});runtime.resizeObserver.observe(area);fit.fit();terminal.focus();
      } else {
        const tunnel=new Guacamole.WebSocketTunnel(endpoint);const client=new Guacamole.Client(tunnel);runtime.guacamole=client;
        const display=client.getDisplay();area.append(display.getElement());
        runtime.scale=()=>{if(display.getWidth())display.scale(Math.min(area.clientWidth/display.getWidth(),area.clientHeight/display.getHeight(),1));};
        display.onresize=runtime.scale;
        const mouse=new Guacamole.Mouse(display.getElement());mouse.onmousedown=mouse.onmouseup=mouse.onmousemove=mouseState=>client.sendMouseState(mouseState,true);
        const touch=new Guacamole.Mouse.Touchpad(display.getElement());touch.onmousedown=touch.onmouseup=touch.onmousemove=mouseState=>client.sendMouseState(mouseState,true);
        const keyboard=new Guacamole.Keyboard(area);runtime.keyboard=keyboard;keyboard.onkeydown=keysym=>{client.sendKeyEvent(1,keysym);return false;};keyboard.onkeyup=keysym=>client.sendKeyEvent(0,keysym);
        area.addEventListener('pointerdown',()=>area.focus());area.addEventListener('blur',()=>keyboard.reset());
        client.onerror=error=>status(`원격 연결 실패 (${error.code}) · 설정을 확인하세요.`);
        client.onstatechange=value=>status(({1:'연결 중...',2:'응답 대기...',3:'연결됨',4:'연결 종료 중',5:'연결 종료'})[value] || '준비 중');
        client.onclipboard=(stream,mimetype)=>{if(mimetype==='text/plain'){const reader=new Guacamole.StringReader(stream);let text='';reader.ontext=chunk=>{if(text.length<32000)text+=chunk;};reader.onend=()=>{runtime.remoteClipboard=text.slice(0,32000);};}};
        runtime.resizeObserver=new ResizeObserver(runtime.scale);runtime.resizeObserver.observe(area);client.connect();area.focus();
      }
      await refresh();
    } catch(error) {status(error.message);}
  }
  async function runtimeAction(button) {
    const tab=tabs.find(item=>item.id===button.closest('[data-runtime]').dataset.runtime);const runtime=runtimes.get(tab.id);const action=button.dataset.runtimeAction;
    if(action==='new') await openResource(tab.kind,tab.targetId,tab.path,true);
    if(action==='reload') await loadInspection(tab);
    if(action==='reconnect') {runtime.socket?.close();runtime.guacamole?.disconnect();runtime.terminal?.dispose();runtime.resizeObserver?.disconnect();if(runtime.sessionId)await api(`/sessions/${runtime.sessionId}`,'DELETE').catch(()=>{});await connectRuntime(tab);}
    if(action==='fullscreen') await runtime.element.requestFullscreen();
    if(action==='paste') editor('원격 클립보드',`<label>원격 화면에 전송할 텍스트<textarea name="text" rows="7" maxlength="32000">${escape(runtime.remoteClipboard || '')}</textarea></label><p class="section-hint">전송 후 원격 앱에서 붙여넣기 하세요. 원격에서 복사한 텍스트도 이 창에서 확인할 수 있습니다.</p>`,async form=>{if(!runtime.guacamole)throw new Error('원격 연결이 없습니다.');const writer=new Guacamole.StringWriter(runtime.guacamole.createClipboardStream('text/plain'));writer.sendText(form.get('text'));writer.sendEnd();},'전송');
    if(action==='docker') editor('컨테이너 제어',fields.input('container','컨테이너 이름 또는 ID','','text','required')+fields.select('action','동작','restart',[['start','시작'],['stop','중지'],['restart','재시작']]),async form=>{const result=await api(`/devices/${tab.targetId}/docker`,'POST',Object.fromEntries(form));await loadInspection(tab);toast(result.output || '요청을 실행했습니다.');},'실행');
  }
  let searchTimer, searchVersion=0;
  async function search() {const version=++searchVersion;const serverResults=await api(`/search?query=${encodeURIComponent($('#paletteInput').value)}`);const query=$('#paletteInput').value.trim().toLowerCase(),seen=new Set();const results=[...serverResults,...state.activity.filter(item=>`${item.label} ${item.path||''}`.toLowerCase().includes(query))].filter(item=>{const key=[item.kind,item.targetId,item.path||'/'].join('|');if(seen.has(key))return false;seen.add(key);return true;}).slice(0,80);if(version!==searchVersion)return;appRegistry.sync(state);const appResults=appRegistry.search($('#paletteInput').value).map(app=>`<button ${appRegistry.attributes(app)}><span>${window.WorkspaceUI.icon(app.icon)}</span><b>${escape(app.name)}</b><small>앱</small></button>`).join('');$('#search-results').innerHTML=appResults+results.map(item=>`<button ${openAttrs(item.kind,item.targetId,item.path)}><span>${icons[item.kind]}</span><b>${escape(item.label)}</b><small>${labels[item.kind]}</small></button>`).join('')||empty('검색 결과가 없습니다.');}
  function palette() {$('#palette-dialog').showModal();$('#paletteInput').focus();search().catch(error=>toast(error.message));}
  $('#paletteInput').addEventListener('input',()=>{clearTimeout(searchTimer);searchTimer=setTimeout(()=>search().catch(error=>toast(error.message)),180);});
  document.addEventListener('keydown',event=>{
    if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='k'){event.preventDefault();palette();}
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
        case 'remote-setup':window.WorkspaceRemoteSetup.open(id,{api,editor,refresh,connect:()=>openResource('REMOTE',id,'/',true)});break;
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
  window.addEventListener('beforeunload',()=>{for(const runtime of runtimes.values()){runtime.socket?.close();runtime.guacamole?.disconnect();}});
  window.WorkspacePlanner?.init({api,editor,fields,escape,toast,confirmAction});
  window.WorkspaceLogs?.init({api,escape,toast});
  window.WorkspaceNotes?.init({api,escape,toast,editor,confirmAction});
  window.WorkspaceCloud?.init({api,escape,toast,editor,confirmAction});
  window.WorkspaceNas?.init({api,escape,editor});
  window.WorkspaceTailscale?.init({api,confirmAction});
  window.addEventListener('DOMContentLoaded', () => window.WorkspaceStudio?.init({api,editor,escape,toast,confirmAction,openTerminal: id=>openResource('TERMINAL',id)}));
  window.WorkspaceLauncher.init({api,editor,toast,state:()=>state,showHome:()=>showView('home')});
  window.visualViewport?.addEventListener('resize',()=>{document.documentElement.style.setProperty('--viewport-height',window.visualViewport.height+'px');for(const runtime of runtimes.values()){runtime.fit?.fit();runtime.scale?.();}});
  render();refreshStatuses();setInterval(()=>{if(!document.hidden)refresh().catch(error=>toast(error.message));},60000);
})();
