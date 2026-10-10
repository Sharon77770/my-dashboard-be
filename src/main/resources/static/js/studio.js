'use strict';
(() => {
  let codex, ui, project, currentPath = '.', activeFile, busy = false, jobId, codeEditor, workbench;
  const documents = new Map();
  const directories=new Map(),expanded=new Set(['.']);
  let eventTarget=null,authKnown=false,studioSummary={}, deviceRoots=new Map();
  function publish(detail){studioSummary={...studioSummary,...detail};window.dispatchEvent(new CustomEvent('studio-state',{detail:studioSummary}));}
  const root = document.querySelector('#studio');
  const $ = selector => root.querySelector(selector);
  let activePanel = 'git';
  const labels = {'setup':'원격 도구 준비','list':'폴더 읽기','read':'파일 열기','save':'파일 저장','git-status':'Git 변경 확인','codex-run':'Codex 작업','codex-login':'Codex 로그인'};
  const escape = value => ui.escape(value);

  function renderShell() {
    root.innerHTML = `<div class="studio-commandbar"><label class="studio-target"><span class="studio-sr-only">실행 대상</span><select id="studio-device" form="studio-connect-form" required aria-label="실행 대상"></select></label><details class="studio-project-menu"><summary title="작업 폴더 변경"><span aria-hidden="true">▱</span><span id="studio-project-name">폴더 열기</span><span aria-hidden="true">⌄</span></summary><form id="studio-connect-form" class="studio-connect"><label class="studio-root-label">작업 폴더<input id="studio-root" placeholder="/home/me/project" required maxlength="4096"></label><p class="section-hint">선택한 서버의 폴더를 엽니다. 처음 열면 필요한 도구를 준비합니다.</p><button type="submit" class="primary">폴더 열기</button></form></details><span class="studio-connection badge">연결 전</span><div class="studio-view-controls" aria-label="IDE 패널"><button data-pane="explorer" class="ghost" aria-pressed="false">Files</button><button data-pane="editor" class="ghost" aria-pressed="true">Editor</button><button data-pane="inspector" class="ghost" aria-pressed="false">Git / Codex</button></div></div>
<div id="studio-start" class="studio-start"><span aria-hidden="true">⌘</span><h1>작업 폴더를 열어 시작하세요</h1><p>위에서 서버를 선택하고 프로젝트 폴더를 여세요.</p><button type="button" class="primary" data-studio="open-project">폴더 열기</button></div>
<div class="studio-workbench" data-mobile-pane="editor" hidden>
<aside class="studio-explorer"><div class="studio-toolbar"><b>탐색기</b><button class="icon-btn" data-studio="refresh" aria-label="탐색기 새로고침" data-tooltip="새로고침">↻</button><button class="icon-btn" data-studio="create" aria-label="새 파일" data-tooltip="새 파일">+</button><button class="icon-btn" data-studio="mkdir" aria-label="새 폴더" data-tooltip="새 폴더">▱</button></div><div class="studio-breadcrumb"><button class="ghost sm" data-studio="parent" aria-label="상위 폴더">↑</button><span id="studio-directory">/</span></div><div id="studio-tree" aria-label="프로젝트 파일"></div><div class="studio-toolbar"><button data-studio="upload">업로드</button><input id="studio-upload" type="file" hidden><button data-studio="copy-path">경로 복사</button><button data-studio="download">다운로드</button></div><button class="studio-terminal ghost" data-studio="terminal">›_ 터미널 열기</button></aside>
<div class="ui-splitter" data-resize="explorer" role="separator" tabindex="0" aria-label="탐색기 너비" aria-orientation="vertical" aria-valuemin="160" aria-valuemax="480" aria-valuenow="200"></div>
<section class="studio-editor"><div id="studio-file-tabs" role="tablist" aria-label="열린 파일"></div><div class="studio-toolbar studio-editor-actions"><span id="studio-file-label"></span><button class="icon-btn" data-studio="find" aria-label="검색 (Ctrl F)" title="검색 (Ctrl F)"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><circle cx="10" cy="10" r="6"/><path d="m15 15 6 6"/></svg></button><button class="icon-btn" data-studio="save" aria-label="저장 (Ctrl S)" title="저장 (Ctrl S)"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="M4 4h14l2 2v14H4zM8 4v6h8V4M8 20v-7h8v7"/></svg></button><details class="ui-menu"><summary aria-label="편집 명령 더 보기" title="편집 명령">&#8943;</summary><div class="ui-menu-content"><button class="ghost sm" data-studio="reload-file" aria-label="서버에서 파일 다시 읽기" data-tooltip="서버에서 다시 읽기">다시 읽기</button><button class="ghost sm" data-studio="find">찾기</button><button class="ghost sm" data-studio="replace">바꾸기</button><button class="ghost sm" data-studio="goto">줄 이동</button><button class="ghost sm" data-studio="save-all">모두 저장</button></div></details></div><div class="studio-code-area"><div id="studio-code" aria-label="코드 편집기"></div><div id="studio-empty"><span>⌘</span><h2>파일을 열어 시작하세요</h2><p>Files에서 파일을 선택하거나 새 파일을 만드세요.</p><small>저장 Ctrl S · 검색 Ctrl F</small></div></div><div class="studio-editor-foot"><span id="studio-dirty">UTF-8</span><span id="studio-position">Ln 1, Col 1</span></div></section>
<div class="ui-splitter" data-resize="inspector" role="separator" tabindex="0" aria-label="보조 패널 너비" aria-orientation="vertical" aria-valuemin="160" aria-valuemax="480" aria-valuenow="320"></div>
<aside class="studio-inspector"><div class="studio-panel-tabs" role="tablist" aria-label="개발 도구"><button data-studio-panel="git" class="active" role="tab" aria-selected="true">⑂ Git</button><button data-studio-panel="codex" role="tab" aria-selected="false">✦ Codex</button></div>
<section id="studio-git"><div class="studio-toolbar"><b id="studio-branch">소스 관리</b><button class="icon-btn" data-studio="git-refresh" aria-label="Git 새로고침">↻</button><details class="ui-menu"><summary aria-label="저장소 설정">⋯</summary><div class="ui-menu-content"><button data-studio="git-init">저장소 초기화</button><button data-studio="git-clone">저장소 복제</button><button data-studio="git-identity">커밋 작성자</button><button data-studio="git-remote">원격 저장소</button><button data-studio="git-branch">새 브랜치</button></div></details></div><div class="studio-github-bar"><button data-studio="github-login">GitHub 연결</button><button data-studio="github-status" id="studio-github-auth">인증 확인</button></div><div id="studio-github-progress" hidden></div><p id="studio-git-sync"></p><label class="studio-branch-select">브랜치<select id="studio-branches"><option>—</option></select></label><div class="studio-git-actions"><button data-studio="git-fetch">Fetch</button><button data-studio="git-pull">Pull</button><button data-studio="git-push">Push</button></div><form id="studio-commit"><textarea id="studio-commit-message" placeholder="변경 내용을 요약하세요" aria-label="커밋 메시지" maxlength="4000" required rows="2"></textarea><button type="submit" class="primary">커밋</button></form><div id="studio-changes"></div><details id="studio-git-diff"><summary></summary><pre></pre></details><details class="studio-history"><summary>최근 커밋</summary><pre id="studio-history"></pre></details></section>
<section id="studio-codex" hidden></section></aside></div>
<details class="studio-output" aria-label="작업 결과"><summary class="studio-toolbar"><b id="studio-job-status" role="status">작업 폴더를 열어 주세요</b><button data-studio="cancel" class="danger sm" hidden>실행 중지</button></summary><div id="studio-events" aria-live="polite"></div><pre id="studio-diff" hidden></pre></details>`;
    const folderInput=$('#studio-root');
    folderInput.placeholder='목록에서 작업 폴더를 검색하세요';
    folderInput.autocomplete='off';
    folderInput.setAttribute('list','studio-folder-options');
    folderInput.setAttribute('aria-describedby','studio-folder-status');
    const folderOptions=document.createElement('datalist');folderOptions.id='studio-folder-options';folderInput.after(folderOptions);
    const folderStatus=document.createElement('p');folderStatus.id='studio-folder-status';folderStatus.className='section-hint';folderStatus.role='status';folderStatus.textContent='장비를 선택하면 ls 결과에서 폴더 목록을 불러옵니다.';folderInput.closest('label').after(folderStatus);
    const folderRefresh=document.createElement('button');folderRefresh.type='button';folderRefresh.className='ghost sm';folderRefresh.dataset.studio='folder-options';folderRefresh.textContent='폴더 목록 새로고침';folderStatus.after(folderRefresh);
    const folderCreate=document.createElement('button');folderCreate.type='button';folderCreate.className='ghost sm';folderCreate.dataset.studio='folder-create';folderCreate.textContent='＋ 새 폴더';folderRefresh.after(folderCreate);
    window.StudioPanels.attach(root);
    codeEditor = window.WorkspaceCodeEditor($('#studio-code'), content => {
      const doc = documents.get(activeFile);
      if (doc) { doc.content = content; doc.dirty = content !== doc.saved; renderTabs(); }
    });
    codeEditor.load('', '');
    codex=window.StudioCodex($('#studio-codex'),{escape,api:ui.api,toast:ui.toast,editor:ui.editor,project:()=>project,updateCli:()=>execute('setup',{refresh:true}),busy:()=>busy,setBusy,job:id=>{jobId=id;},dirty,confirm:confirmChange,auth:updateAuth,publish,toggleFocus:()=>$('.studio-workbench').classList.toggle('codex-focused'),runtimeContext:()=>workbench?.context(),context:kind=>{if(!activeFile)return null;const selection=codeEditor.selection?.();return kind==='selection'?(selection?.content?{kind,path:activeFile,name:activeFile+':'+selection.fromLine,...selection}:null):{kind:'file',path:activeFile,name:activeFile};}});
    root.addEventListener('studio-cursor',event=>{$('#studio-position').textContent=`Ln ${event.detail.lineNumber}, Col ${event.detail.column}`;});
    $('#studio-changes').after($('#studio-commit'));
    $('#studio-upload').addEventListener('change',()=>guard(uploadFile));
    root.addEventListener('click', onClick);
    root.addEventListener('submit', onSubmit);
    $('.studio-project-menu').addEventListener('keydown',event=>{if(event.key==='Escape'){$('.studio-project-menu').open=false;$('.studio-project-menu summary').focus();}});
    $('#studio-device').addEventListener('change', () => { $('.studio-project-menu').open=true; const option = $('#studio-device').selectedOptions[0]; $('#studio-root').value = option?.dataset.root || ''; refreshFolderOptions(); });
    $('#studio-root').addEventListener('change', refreshFolderOptions);
    $('#studio-branches').addEventListener('change', () => guard(async () => { if (dirty()) throw new Error('편집 내용을 먼저 저장해 주세요.'); await execute('git-switch', {branch:$('#studio-branches').value}); documents.clear();codeEditor.reset?.(); activeFile = null; codeEditor.load('', ''); renderTabs(); await refreshFiles(); gitSignature='';await refreshGit(); }));
    document.addEventListener('keydown', event => { if(root.classList.contains('active') && (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {event.preventDefault(); event.stopPropagation(); guard(event.shiftKey?saveAll:saveFile);} },true);
    window.addEventListener('beforeunload', event => { if(dirty() || busy) {event.preventDefault(); event.returnValue='';} });
    setInterval(()=>{if(gitVisible())refreshGit();},5000);
    window.addEventListener('focus',()=>{if(gitVisible())refreshGit();});
    document.addEventListener('visibilitychange',()=>{if(gitVisible())refreshGit();});
    workbench=window.StudioWorkbench?.(root,{api:ui.api,toast:ui.toast,escape,project:()=>project,confirm:confirmChange,input,openFile,editor:()=>codeEditor,job:toolJob});
  }

  function dirty() { return [...documents.values()].some(doc => doc.dirty); }
  async function toolJob(action,args={},context=project) {
    if(!context)throw Error('프로젝트를 먼저 여세요.');
    let job=await ui.api('/studio/jobs','POST',{...context,action,args},{quiet:true});
    while(job.state==='RUNNING'){await new Promise(resolve=>setTimeout(resolve,350));job=await ui.api('/studio/jobs/'+job.id,'GET',undefined,{quiet:true});}
    if(job.state!=='SUCCEEDED')throw Error(job.error||'도구 실행 실패');
    return job.result;
  }
  function confirmChange(message) {
    return new Promise(resolve => {
      let accepted=false;
      const dialog=document.querySelector('#editor-dialog');
      dialog.addEventListener('close',()=>resolve(accepted),{once:true});
      ui.editor('작업 확인',`<p>${escape(message)}</p>`,async()=>{accepted=true;},'확인');
    });
  }
  async function guard(action) { try { await action(); } catch(error) { ui.toast(error.message); $('#studio-job-status').textContent=error.message; $('#studio-job-status').classList.add('form-error'); } }
  function setBusy(value) {
    busy = value;
    root.querySelectorAll('button,select,input,textarea').forEach(element => { element.disabled = value; });
    root.querySelectorAll('[data-studio="cancel"], [data-studio-panel], [data-pane], [data-cx="focus"], [data-cx="settings"], [data-cx="settings-close"]').forEach(element => {element.disabled=false;});
    root.querySelectorAll('[data-studio="cancel"]').forEach(button=>{button.hidden=!value;});
    $('#studio-github-progress')?.querySelectorAll('button').forEach(button=>button.disabled=false);
    $('#studio-prompt-form').setAttribute('aria-busy',String(value));
    if(!value){$('[data-studio="save"]').disabled=!activeFile;$('[data-studio="reload-file"]').disabled=!activeFile;}
  }
  async function execute(action, args = {}, context = project) {
    if(busy) throw new Error('진행 중인 작업이 끝난 뒤 실행해 주세요.');
    if(!context) throw new Error('먼저 작업 폴더를 열어 주세요.');
    setBusy(true); $('#studio-diff').hidden=true; $('#studio-events').replaceChildren();$('#studio-job-status').classList.remove('form-error');
    let responseCard;
    if(action.startsWith('codex-')&&action!=='codex-status'){
      $('#studio-conversation .assistant-welcome')?.remove();
      responseCard=document.createElement('article');responseCard.className='assistant-result';
      const heading=document.createElement('p');heading.className='assistant-request';heading.textContent=action==='codex-run'?args.prompt:labels[action]||action;responseCard.append(heading);
      const status=document.createElement('small');status.className='assistant-status';status.textContent='실행 중';responseCard.append(status);
      eventTarget=document.createElement('div');eventTarget.className='assistant-events';eventTarget.setAttribute('aria-live','polite');responseCard.append(eventTarget);$('#studio-conversation').append(responseCard);
      while($('#studio-conversation').children.length>20)$('#studio-conversation').firstElementChild.remove();
      publish({codex:'실행 중'});
    }else eventTarget=null;
    $('#studio-job-status').textContent = `${labels[action] || action} · 실행 중`;
    try {
      let job=await ui.api('/studio/jobs','POST',{...context,action,args}); jobId=job.id;
      while(job.state === 'RUNNING') {
        await (window.WorkspaceRealtime?.waitForJob(job.id,650)||new Promise(resolve=>setTimeout(resolve,650)));
        job=await ui.api(`/studio/jobs/${job.id}`);
        renderEvents(job.events);
      }
      renderEvents(job.events);
      if(job.state !== 'SUCCEEDED') { const error = new Error(job.error || '작업이 중지되었습니다.'); error.status=job.errorStatus; throw error; }
      $('#studio-job-status').textContent=`${labels[action] || action} · 완료`;
      if(responseCard){responseCard.querySelector('.assistant-status').textContent='완료';publish({codex:'완료'});}
      if(action==='save'||(action.startsWith('git-')&&!['git-status','git-diff'].includes(action))){$('#studio-git-diff').open=false;$('#studio-git-diff pre').textContent='';}
      return job.result;
    } catch(error){if(responseCard){responseCard.querySelector('.assistant-status').textContent=error.message;responseCard.classList.add('failed');publish({codex:'중지 또는 실패'});}throw error;} finally { eventTarget=null;jobId=null; setBusy(false); }
  }
  function renderEvents(events) {
    const container=eventTarget||$('#studio-events');
    if(!eventTarget&&events.length)$('.studio-output').open=true;
    container.replaceChildren();
    for(const event of events) {
      const row=document.createElement('div');
      const label=document.createElement('b'); label.textContent=event.event; row.append(label);
      if(event.url && (/^https:\/\/auth\.openai\.com\//.test(event.url) || event.url==='https://github.com/login/device')) {const link=document.createElement('a');link.href=event.url;link.target='_blank';link.rel='noopener noreferrer';link.textContent='인증 페이지 열기';row.append(link);window.WorkspaceAuthenticationBrowser?.attach(link,()=>busy?(container.querySelector('[data-auth-code-value]')?.textContent||''):'');}
      const text=document.createElement('pre');if(event.code)text.dataset.authCodeValue='';text.textContent=event.code || event.text || event.state || '';row.append(text);container.append(row);
    }
    container.scrollTop=container.scrollHeight;if(eventTarget){const conversation=$('#studio-conversation');conversation.scrollTop=conversation.scrollHeight;}
  }
  async function connect() {
    if(dirty() && !await confirmChange('저장하지 않은 편집 내용을 닫고 다른 작업 폴더를 열까요?')) return;
    const context={deviceId:$('#studio-device').value,root:$('#studio-root').value};
    await execute('setup',{},context);
    const listing=await execute('list',{path:'.'},context);
    project={...context,root:listing.root};treeChanges=[];gitSignature='';$('#studio-git-diff').open=false;$('#studio-git-diff pre').textContent='';currentPath='.';documents.clear();codeEditor.reset?.();directories.clear();expanded.clear();expanded.add('.');activeFile=null;authKnown=false;studioSummary={};publish({codex:'대기 중'});$('#studio-context').textContent=project.root;updateAuth(null);codex.reset();
    $('.studio-connection').textContent=project.deviceId==='local'?'서버 자체 · 로컬 편집':'SSH · 원격 실행';
    workbench?.projectChanged();
    codeEditor.load('', '');renderTabs();
    $('.studio-workbench').hidden=false;$('#studio-start').hidden=true;$('.studio-project-menu').open=false;$('#studio-project-name').textContent=project.root.split('/').filter(Boolean).pop()||'/';$('#studio-project-name').title=project.root;renderFiles(listing);gitSignature='';await refreshGit();
    try {localStorage.setItem('workspace-studio-project-v1',JSON.stringify(project));}catch{}publish({root:project.root});if(activePanel==='codex')await codex.load();
  }
  function renderFiles(listing) {
    currentPath=listing.path;directories.set(listing.path,listing);expanded.add(listing.path);$('#studio-directory').textContent=currentPath==='.'?project.root:currentPath;renderTree();
  }
  const selectedDeviceRoot=()=>$('#studio-device').selectedOptions[0]?.dataset.root||'';
  const relativeFolderPath=(value,base)=>{
    if(!value||!base||value===base)return '.';
    const prefix=base.endsWith('/')?base:base+'/';
    return value.startsWith(prefix)?value.slice(prefix.length)||'.':value;
  };
  async function folderListing(){
    const base=selectedDeviceRoot(),value=$('#studio-root').value.trim()||base;
    if(!base)return null;
    let job=await ui.api('/studio/jobs','POST',{deviceId:$('#studio-device').value,root:base,action:'list',args:{path:relativeFolderPath(value,base)}});
    while(job.state==='RUNNING'){await (window.WorkspaceRealtime?.waitForJob(job.id,250)||new Promise(resolve=>setTimeout(resolve,250)));job=await ui.api('/studio/jobs/'+job.id);}
    if(job.state!=='SUCCEEDED')throw new Error(job.error||'폴더 목록을 불러오지 못했습니다.');
    return job.result;
  }
  async function refreshFolderOptions(){
    const status=$('#studio-folder-status'),list=$('#studio-folder-options');
    if(!status||!list)return;
    try{
      status.textContent='폴더 목록을 불러오는 중…';
      const listing=await folderListing(),base=selectedDeviceRoot(),rootPath=listing?.root||base;
      list.innerHTML=(listing?.entries||[]).filter(entry=>entry.directory).map(entry=>{const path=rootPath==='/'?'/'+entry.path:rootPath+'/'+entry.path;return `<option value="${escape(path)}" label="${escape(entry.name)}"></option>`;}).join('');
      status.textContent=`${list.options.length}개 폴더를 검색할 수 있습니다. 경로를 입력하면 해당 위치의 하위 폴더를 다시 읽습니다.`;
    }catch(error){status.textContent=error.message;list.replaceChildren();}
  }
  async function createProjectFolder(){
    const deviceId=$('#studio-device').value;
    const parent=$('#studio-root').value.trim()||selectedDeviceRoot();
    if(!deviceId||!parent)throw new Error('장비와 상위 폴더를 먼저 선택하세요.');
    await input('새 프로젝트 폴더',[{name:'name',label:`${parent} 안에 만들 폴더 이름`,max:255}],async values=>{
      const name=values.name.trim();
      if(!name||name==='.'||name==='..'||/[\\/\x00-\x1f\x7f]/.test(name))throw new Error('경로 구분자 없이 폴더 이름만 입력하세요.');
      await execute('mkdir',{path:name},{deviceId,root:parent});
      const path=parent.replace(/\/+$/,'')+'/'+name;
      if($('#studio-device').value===deviceId&&($('#studio-root').value.trim()||selectedDeviceRoot())===parent){
        $('#studio-root').value=path;
        await refreshFolderOptions();
        $('#studio-folder-status').textContent='폴더를 만들었습니다. ‘폴더 열기’를 누르면 시작합니다.';
      }
      ui.toast(`폴더를 만들었습니다: ${path}`);
    });
  }
  function renderTree(){
    const branch=(path,depth)=> (directories.get(path)?.entries||[]).map(entry=>'<div class="studio-file-row '+(entry.path===activeFile?'selected':'')+'" style="--tree-depth:'+depth+'"><button data-studio="'+(entry.directory?'directory':'file')+'" data-path="'+escape(entry.path)+'" title="'+escape(entry.name)+'" '+(entry.directory?'aria-expanded="'+expanded.has(entry.path)+'"':'')+'><span>'+(entry.directory?(expanded.has(entry.path)?'⌄':'›'):'·')+'</span>'+escape(entry.name)+'</button><button data-studio="rename" data-path="'+escape(entry.path)+'" aria-label="이름 변경">✎</button><button data-studio="delete" data-path="'+escape(entry.path)+'" aria-label="삭제">×</button></div>'+(entry.directory&&expanded.has(entry.path)?branch(entry.path,depth+1):'')).join('');
    $('#studio-tree').innerHTML=branch('.',0)||'<p class="empty-state">빈 폴더입니다.</p>';
    decorateTree();
  }
  function decorateTree(){
    for (const row of root.querySelectorAll('.studio-file-row')) {
      const button=row.querySelector('[data-path]');const path=button.dataset.path.replace(/^\.\//,'');
      const directory=button.dataset.studio==='directory';
      const changes=treeChanges.filter(change=>change.path===path||(directory&&change.path.startsWith(path+'/')));
      const ranked=changes.map(change=>gitStatusLabel(change,change.worktree===' ')).sort((a,b)=>['danger','warning','success','neutral'].indexOf(a[1])-['danger','warning','success','neutral'].indexOf(b[1]));
      if(ranked.length){const [label,tone]=ranked[0];row.dataset.change=tone;const badge=document.createElement('small');badge.className='studio-tree-change';badge.textContent=label;button.append(badge);button.title+=' - '+label;}
      else if(directory)row.dataset.fileKind='folder';
    }
  }
  async function refreshFiles(){directories.clear();expanded.clear();expanded.add('.');renderFiles(await execute('list',{path:'.'}));}
  function renderTabs() {
    $('#studio-file-tabs').innerHTML=[...documents].map(([path,doc])=>`<div class="studio-document ${path===activeFile?'active':''}"><button role="tab" aria-selected="${path===activeFile}" data-studio="tab" data-path="${escape(path)}">${escape(path.split('/').pop())}${doc.dirty?' ●':''}</button><button data-studio="close" data-path="${escape(path)}" aria-label="파일 닫기">×</button></div>`).join('');
    $('#studio-file-label').title=activeFile || '';
    $('#studio-file-label').textContent=activeFile || '파일을 선택하세요';
    $('#studio-empty').hidden=Boolean(activeFile);$('#studio-code').hidden=!activeFile;
    if(!busy){$('[data-studio="save"]').disabled=!activeFile;$('[data-studio="reload-file"]').disabled=!activeFile;}
    renderTree();
    $('#studio-dirty').classList.add('ui-status');$('#studio-dirty').dataset.state=documents.get(activeFile)?.dirty?'warning':'neutral';
    $('#studio-dirty').textContent=documents.get(activeFile)?.dirty?'저장하지 않은 변경':'UTF-8 · 서버에 저장됨';
  }
  function activateFile(path) {$('.studio-workbench').dataset.mobilePane='editor';activeFile=path;codeEditor.load(path,documents.get(path).content);renderTabs();codeEditor.focus();}
  async function openFile(path, reload=false) {
    if(!reload && documents.has(path)) {activateFile(path);return;}
    if(documents.get(path)?.dirty && !await confirmChange('편집 내용을 버리고 서버 파일을 다시 읽을까요?'))return;
    const doc=await execute('read',{path});documents.set(path,{...doc,saved:doc.content,dirty:false});activateFile(path);
  }
  async function saveFile(path=activeFile) {
    const doc=documents.get(path);if(!doc || !doc.dirty)return;
    const content=doc.content;
    const saved=await execute('save',{path,content,revision:doc.revision});
    doc.revision=saved.revision;doc.saved=content;doc.dirty=doc.content!==content;renderTabs();await refreshGit();
  }
  async function saveAll() {
    for(const [path,doc] of documents) if(doc.dirty) await saveFile(path);
  }
  // Existing Files API paths are relative to the device root, not the project root.
  function transferPath(path) {
    const base=(deviceRoots.get(project.deviceId)||'').replace(/\/$/,'');
    const projectRoot=project.root.replace(/\/$/,'');
    if(!base && deviceRoots.get(project.deviceId)!=='/')throw new Error('장비 루트를 확인해 주세요.');
    if(projectRoot!==base && !projectRoot.startsWith(base+'/'))throw new Error('프로젝트 경로를 확인해 주세요.');
    const suffix=path==='.'?'':'/'+path;
    return (projectRoot.slice(base.length)+suffix)||'/';
  }
  async function uploadFile() {
    const picker=$('#studio-upload'),file=picker.files[0];picker.value='';
    if(!file)return;
    const target=currentPath==='.'?file.name:currentPath+'/'+file.name;
    if(documents.get(target)?.dirty)throw new Error('업로드 전에 해당 파일의 편집 내용을 저장해 주세요.');
    if(file.name==='.git'||file.name.includes('/')||file.name.includes('\\'))throw new Error('파일 이름을 확인해 주세요.');
    const context={...project},directory=currentPath;
    setBusy(true);
    root.querySelectorAll('[data-studio="cancel"]').forEach(button=>{button.hidden=true;});
    try {
      const listing=await ui.api(`/devices/${encodeURIComponent(context.deviceId)}/files?path=${encodeURIComponent(transferPath(directory))}`);
      if(listing.entries.some(entry=>entry.name===file.name))throw new Error('같은 이름의 파일이 있습니다. 이름을 변경한 후 업로드하세요.');
      const form=new FormData();form.append('path',transferPath(directory));form.append('file',file);
      await ui.api(`/devices/${encodeURIComponent(context.deviceId)}/files`,'POST',form);
    } finally {setBusy(false);}
    await refreshFiles();
  }
  function gitStatusLabel(change,staged){
    if(change.index==='U'||change.worktree==='U'||['AA','DD'].includes(change.index+change.worktree))return ['충돌','danger'];
    const code=staged?change.index:change.worktree;
    if(code==='?')return ['새 파일','success'];
    return ({A:['추가','success'],M:['수정','warning'],D:['삭제','danger'],R:['이름 변경','neutral'],C:['복사','neutral']})[code]||['변경','neutral'];
  }
  // Read-only Git refresh and device authorization must not lock the editor UI.
  let treeChanges=[], gitRefreshing=false, gitSignature='', githubJob=null, githubPending=false;
  function gitVisible(){return project&&!document.hidden&&root.classList.contains('active');}
  async function githubAuth(action){
    if(!project)throw Error('프로젝트를 먼저 여세요.');
    if(githubPending)throw Error('GitHub 인증이 진행 중입니다. 인증을 마치거나 취소하세요.');
    const context=project, box=$('#studio-github-progress');
    githubPending=true; box.hidden=false;box.replaceChildren();
    const message=document.createElement('p');message.setAttribute('role','status');message.textContent='GitHub 연결 확인 중…';box.append(message);
    const cancel=document.createElement('button');cancel.type='button';cancel.textContent='로그인 취소';cancel.hidden=action!=='github-login';box.append(cancel);
    let cancelled=false;
    cancel.onclick=async()=>{cancelled=true;cancel.disabled=true;try{if(githubJob)await ui.api('/studio/jobs/'+githubJob,'DELETE');}catch(error){message.textContent=error.message;cancel.disabled=false;}};
    const seen=new Set();
    function events(items=[]){for(const event of items){
      if(event.code&&!seen.has(event.code)){seen.add(event.code);const code=document.createElement('code');code.textContent=event.code;box.append(code);message.textContent='인증 코드를 복사한 뒤 GitHub에서 승인을 완료하세요.';}
      if((event.code||event.url==='https://github.com/login/device')&&!box.querySelector('a')){const link=document.createElement('a');link.href='https://github.com/login/device';link.target='_blank';link.rel='noopener noreferrer';link.textContent='GitHub 인증 페이지 열기 ↗';box.append(link);window.WorkspaceAuthenticationBrowser?.attach(link,()=>githubJob?(box.querySelector('code')?.textContent||''):'');}
    }}
    try{
      let job=await ui.api('/studio/jobs','POST',{...context,action,args:{}},{quiet:true});githubJob=job.id;
      if(cancelled)await ui.api('/studio/jobs/'+job.id,'DELETE');
      events(job.events);
      while(job.state==='RUNNING'){
        await new Promise(resolve=>setTimeout(resolve,650));
        job=await ui.api('/studio/jobs/'+job.id,'GET',undefined,{quiet:true});events(job.events);
      }
      if(job.state!=='SUCCEEDED')throw Error(job.state==='CANCELLED'?'GitHub 로그인을 취소했습니다.':job.error||'GitHub 인증에 실패했습니다.');
      message.textContent=job.result.authenticated?'GitHub 연결됨':'GitHub 로그인이 필요합니다.';
      if(project===context)$('#studio-github-auth').textContent=job.result.authenticated?'GitHub 연결됨':'인증 확인';
    }catch(error){message.textContent=error.message;}finally{githubJob=null;githubPending=false;cancel.remove();box.querySelectorAll('code,a').forEach(node=>node.remove());}
  }
  async function refreshGit() {
    if(!project||gitRefreshing||busy)return;
    const context=project;gitRefreshing=true;
    try {
      const status=await toolJob('git-status',{},context);
      if(project!==context)return;
      const signature=JSON.stringify(status);
      if(signature===gitSignature)return;
      gitSignature=signature;treeChanges=status.changes||[];renderTree();
      if(status.repository===false){
        $('#studio-branch').textContent='Git';
        $('#studio-changes').innerHTML='<div class="studio-git-empty"><p>이 폴더에는 Git 저장소가 없습니다.</p><button data-studio="git-init">저장소 만들기</button><button data-studio="git-clone">저장소 복제</button>'+(status.repositories||[]).map(path=>`<button data-studio="git-open" data-path="${escape(path)}">${escape(path)} 열기</button>`).join('')+'</div>';
        $('#studio-git').dataset.repository='false';$('#studio-git-diff').open=false;$('#studio-git-diff pre').textContent='';$('#studio-git-diff summary').textContent='';$('#studio-history').textContent='';return;
      }
      $('#studio-git').dataset.repository='true';
      $('#studio-git-sync').textContent=status.upstream?`${status.upstream} · ↑${status.ahead||0} ↓${status.behind||0}`:'원격 추적 브랜치 없음';$('#studio-branch').textContent=`⑂ ${status.branch}`;
      $('#studio-branches').innerHTML=[...new Set([status.branch,...status.branches])].map(branch=>`<option value="${escape(branch)}" ${branch===status.branch?'selected':''}>${escape(branch)}</option>`).join('');
      const conflicts=status.changes.filter(change=>change.index==='U'||change.worktree==='U'||['AA','DD'].includes(change.index+change.worktree));
      const staged=status.changes.filter(change=>!conflicts.includes(change)&&change.index!==' '&&change.index!=='?');
      const working=status.changes.filter(change=>!conflicts.includes(change)&&(change.worktree!==' '||change.index==='?'));
      const changeRows=(changes,stagedGroup)=>changes.map(change=>{const [label,tone]=gitStatusLabel(change,stagedGroup);return `<div class="studio-change"><button data-studio="git-diff" data-path="${escape(change.path)}" data-untracked="${change.index==='?'}" data-staged="${stagedGroup}" aria-label="${escape(change.path)} ${label} 변경 내용 보기"><code class="git-status-${tone}">${escape(stagedGroup?change.index:change.worktree)}</code><span title="${escape(change.path)}">${escape(change.path)}</span><em>${label}</em></button><button data-studio="${stagedGroup?'git-unstage':'git-stage'}" data-path="${escape(change.path)}" aria-label="${stagedGroup?'스테이징 취소':'스테이징'}" >${stagedGroup?'−':'+'}</button></div>`;}).join('');
      const group=(title,description,changes,stagedGroup,action)=>`<details open class="change-group ${changes.length?'':'is-empty'}"><summary title="${escape(description)}"><span>${title} <span class="badge">${changes.length}</span></span>${action?`<button type="button" class="ghost sm" data-studio="${action}">${stagedGroup?'전체 스테이징 해제':'전체 스테이징'}</button>`:''}</summary>${changeRows(changes,stagedGroup)}</details>`;
      $('#studio-changes').innerHTML=(conflicts.length?group('해결이 필요한 충돌','충돌을 해결한 뒤 파일을 다시 스테이징하세요.',conflicts,false,null):'')+group('커밋에 포함될 변경','아래 파일만 다음 커밋에 들어갑니다.',staged,true,'git-unstage-all')+group('작업 폴더 변경','검토한 뒤 스테이징하거나 변경 내용을 확인하세요.',working,false,'git-stage-all')+(status.changes.length?'':'<p class="empty-state">작업 폴더가 깨끗합니다.</p>');
      publish({branch:status.branch,changes:status.changes.length});
      $('#studio-history').textContent=status.history;
    } catch(error) {if(project!==context)return;$('#studio-branch').textContent='Git 저장소 확인 필요';$('#studio-changes').textContent=error.message;$('#studio-history').textContent='';$('#studio-branches').innerHTML='<option>—</option>';gitSignature='';}finally{gitRefreshing=false;}
  }
  async function input(title, items, submit) {
    ui.editor(title,items.map(item=>`<label>${escape(item.label)}<input name="${item.name}" value="${escape(item.value || '')}" maxlength="${item.max || 1024}" required></label>`).join(''),async form=>submit(Object.fromEntries(form.entries())));
  }
  async function onSubmit(event) {
    event.preventDefault();
    await guard(async()=> {
      if(event.target.matches('.studio-connect'))await connect();
      if(event.target.id==='studio-commit') {await execute('git-commit',{message:$('#studio-commit-message').value});$('#studio-commit-message').value='';gitSignature='';await refreshGit();}

    });
  }
  async function onClick(event) {
    const button=event.target.closest('button');if(!button)return;
    if(button.dataset.studioPanel) {if(button.dataset.studioPanel!=='codex'){$('.studio-workbench').classList.remove('codex-focused');const focus=$('[data-cx=focus]');focus?.setAttribute('aria-pressed','false');if(focus)focus.textContent='대화 확대';}activePanel=button.dataset.studioPanel;$('.studio-workbench').dataset.mobilePane='inspector';$('#studio-git').hidden=activePanel!=='git';$('#studio-codex').hidden=activePanel!=='codex';root.querySelectorAll('[data-studio-panel]').forEach(item=>{item.classList.toggle('active',item===button);item.setAttribute('aria-selected',String(item===button));});if(activePanel==='codex')codex.load();else refreshGit();return;}
    const action=button.dataset.studio, path=button.dataset.path;if(!action)return;
    await guard(async()=>{
      if(action==='open-project'){$('.studio-project-menu').open=true;$('#studio-root').focus();await refreshFolderOptions();return;}
      if(action==='folder-options'){await refreshFolderOptions();return;}
      if(action==='folder-create'){await createProjectFolder();return;}
      if(action==='cancel') {event.preventDefault();if(jobId)await ui.api(`/studio/jobs/${jobId}`,'DELETE');return;}
      if(action==='terminal') {await workbench.newTerminal();return;}
      if(action==='file' || action==='tab') {await openFile(path);return;}
      if(action==='close') {if(documents.get(path)?.dirty && !await confirmChange('저장하지 않은 파일을 닫을까요?'))return;documents.delete(path);codeEditor.close?.(path);if(activeFile===path) {activeFile=documents.keys().next().value;if(activeFile)activateFile(activeFile);else codeEditor.load('', '');}renderTabs();return;}
      if(['find','replace','goto'].includes(action)){await codeEditor.command?.(action);return;}
      if(action==='save-all'){await saveAll();return;}
      if(action==='upload'){if(!project)throw new Error('프로젝트를 먼저 여세요.');$('#studio-upload').click();return;}
      if(action==='copy-path'){if(project)await navigator.clipboard.writeText((project.root.replace(/\/$/,'')+(activeFile?'/'+activeFile:currentPath==='.'?'':'/'+currentPath))||'/');return;}
      if(action==='download'){if(!activeFile)throw new Error('다운로드할 파일을 여세요.');const link=document.createElement('a');link.href=`/api/v1/devices/${encodeURIComponent(project.deviceId)}/files/content?path=${encodeURIComponent(transferPath(activeFile))}`;link.download='';link.click();return;}
      if(action==='save') {await saveFile();return;}
      if(action==='reload-file') {if(activeFile)await openFile(activeFile,true);return;}
      if(action==='directory') {currentPath=path;if(expanded.has(path)){expanded.delete(path);renderTree();}else renderFiles(directories.get(path)||await execute('list',{path}));return;}
      if(action==='parent') {const parent=currentPath.split('/').slice(0,-1).join('/') || '.';renderFiles(await execute('list',{path:parent}));return;}
      if(action==='refresh') {await refreshFiles();return;}
      if(['create','mkdir','rename'].includes(action)) {
        if(action==='rename' && dirty())throw new Error('파일 이름을 변경하기 전에 편집 내용을 저장해 주세요.');
        await input(action==='rename'?'이름 변경':action==='mkdir'?'새 폴더':'새 파일',[{name:'name',label:'작업 폴더 기준 경로',value:action==='rename'?path:(currentPath==='.'?'':currentPath+'/')}],async values=>{await execute(action,action==='rename'?{path,target:values.name}:{path:values.name});if(action==='rename'){documents.clear();codeEditor.reset?.();activeFile=null;codeEditor.load('', '');renderTabs();}await refreshFiles();});return;
      }
      if(action==='delete') {if(!await confirmChange(`${path}을 삭제할까요? 폴더는 비어 있을 때만 삭제됩니다.`))return;await execute(action,{path});documents.delete(path);codeEditor.close?.(path);if(activeFile===path){activeFile=null;codeEditor.load('', '');}renderTabs();await refreshFiles();return;}
      if(action==='git-refresh') {gitSignature='';await refreshGit();return;}
      if(action==='github-login' || action==='github-status') {await githubAuth(action);return;}
      if(action==='git-open'){$('#studio-root').value=path;await connect();return;}
      if(action==='git-diff') {const result=await execute(action,{path,staged:button.dataset.staged==='true',untracked:button.dataset.untracked==='true'});const diff=$('#studio-git-diff');diff.querySelector('summary').textContent=path;diff.querySelector('pre').textContent=result.diff || '변경 내용이 없습니다.';diff.open=true;diff.scrollIntoView({block:'nearest'});return;}
      if(action==='git-stage-all'||action==='git-unstage-all') {const status=await execute('git-status');const stagedGroup=action==='git-unstage-all';const paths=status.changes.filter(change=>stagedGroup?(change.index!==' '&&change.index!=='?'):(change.worktree!==' '||change.index==='?')).filter(change=>!(change.index==='U'||change.worktree==='U'||['AA','DD'].includes(change.index+change.worktree))).map(change=>change.path);for(const item of paths)await execute(stagedGroup?'git-unstage':'git-stage',{path:item});gitSignature='';await refreshGit();return;}
      if(action==='git-clone') {await input('Git 저장소 복제',[{name:'url',label:'저장소 주소',max:2048},{name:'target',label:'새 폴더 이름'}],async args=>{const result=await execute(action,args);$('#studio-root').value=result.path;await refreshFiles();ui.toast('복제되었습니다. 작업 폴더 열기로 저장소를 여세요.');});return;}
      if(action==='git-branch') {if(dirty())throw new Error('편집 내용을 먼저 저장해 주세요.');await input('새 브랜치로 전환',[{name:'branch',label:'브랜치 이름'}],async args=>{await execute(action,args);gitSignature='';await refreshGit();});return;}
      if(action==='git-identity') {await input('이 저장소의 커밋 작성자',[{name:'name',label:'이름',max:200},{name:'email',label:'이메일',max:200}],args=>execute(action,args));return;}
      if(action==='git-remote') {await input('origin 원격 저장소 설정',[{name:'url',label:'HTTPS 또는 SSH 저장소 주소',max:2048}],args=>execute(action,args));return;}
      if(action.startsWith('git-')) {if(dirty() && action==='git-pull')throw new Error('편집 내용을 먼저 저장해 주세요.');if(action==='git-push' && !await confirmChange('현재 브랜치의 커밋을 원격 저장소로 Push할까요?'))return;await execute(action,path?{path}:{});gitSignature='';await refreshGit();return;}
      if(action.startsWith('codex-')) {if(action==='codex-logout' && !await confirmChange('이 서버 계정의 Codex에서 로그아웃할까요?'))return;const result=await execute(action);updateAuth(result.authenticated);if(action==='codex-logout')codex.account({authenticated:false});else if(action==='codex-login')await codex.refreshAccount();return;}
    });
  }
  function updateAuth(authenticated){authKnown=authenticated!==null;$('#studio-auth').textContent=authenticated===null?'인증 확인 전':authenticated?'CLI 인증됨':'로그인 필요';$('#studio-auth-cta').hidden=authenticated!==false;root.querySelectorAll('.ui-menu [data-studio="codex-login"]').forEach(button=>button.hidden=authenticated!==false);$('[data-studio="codex-logout"]').hidden=authenticated!==true;}
  window.WorkspaceStudio={
    init(helpers){ui=helpers;renderShell();},
    async open(id){if(id!=='studio')return;const state=await ui.api('/workspace');deviceRoots=new Map(state.devices.map(device=>[device.id,device.rootPath]));const selected=$('#studio-device').value;$('#studio-device').innerHTML=[...state.devices].sort((a,b)=>Number(b.id==='local')-Number(a.id==='local')).map(device=>`<option value="${escape(device.id)}" data-root="${escape(device.rootPath)}">${device.id === 'local' ? '서버 자체' : escape(device.name)+' · '+escape(device.host)+(device.networkMode==='TAILSCALE'?' · Tailscale':'')}</option>`).join('');if(selected)$('#studio-device').value=selected;if(!$('#studio-root').value){let saved;try{saved=JSON.parse(localStorage.getItem('workspace-studio-project-v1'));}catch{}if(saved && state.devices.some(device=>device.id===saved.deviceId)){$('#studio-device').value=saved.deviceId;$('#studio-root').value=saved.root;}else $('#studio-root').value=$('#studio-device').selectedOptions[0]?.dataset.root || '';}}
  };
})();
