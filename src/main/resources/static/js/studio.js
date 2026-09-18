'use strict';
(() => {
  let codex, ui, project, currentPath = '.', activeFile, busy = false, jobId, codeEditor;
  const documents = new Map();
  const directories=new Map(),expanded=new Set(['.']);
  let eventTarget=null,authKnown=false,studioSummary={};
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
<aside class="studio-explorer"><div class="studio-toolbar"><b>탐색기</b><button class="icon-btn" data-studio="refresh" aria-label="탐색기 새로고침" data-tooltip="새로고침">↻</button><button class="icon-btn" data-studio="create" aria-label="새 파일" data-tooltip="새 파일">+</button><button class="icon-btn" data-studio="mkdir" aria-label="새 폴더" data-tooltip="새 폴더">▱</button></div><div class="studio-breadcrumb"><button class="ghost sm" data-studio="parent" aria-label="상위 폴더">↑</button><span id="studio-directory">/</span></div><div id="studio-tree" aria-label="프로젝트 파일"></div><button class="studio-terminal ghost" data-studio="terminal">›_ 터미널 열기</button></aside>
<div class="ui-splitter" data-resize="explorer" role="separator" tabindex="0" aria-label="탐색기 너비" aria-orientation="vertical" aria-valuemin="160" aria-valuemax="480" aria-valuenow="200"></div>
<section class="studio-editor"><div id="studio-file-tabs" role="tablist" aria-label="열린 파일"></div><div class="studio-toolbar"><span id="studio-file-label">파일을 선택하세요</span><button class="ghost sm" data-studio="reload-file" aria-label="서버에서 파일 다시 읽기" data-tooltip="서버에서 다시 읽기">↻</button><button class="primary sm" data-studio="save">저장 <kbd>Ctrl S</kbd></button></div><div class="studio-code-area"><div id="studio-code" aria-label="코드 편집기"></div><div id="studio-empty"><span>⌘</span><h2>파일을 열어 시작하세요</h2><p>Files에서 파일을 선택하거나 새 파일을 만드세요.</p><small>저장 Ctrl S · 검색 Ctrl F</small></div></div><div class="studio-editor-foot"><span id="studio-dirty">UTF-8</span><span>검색 Ctrl F · 실행 취소 Ctrl Z</span></div></section>
<div class="ui-splitter" data-resize="inspector" role="separator" tabindex="0" aria-label="보조 패널 너비" aria-orientation="vertical" aria-valuemin="160" aria-valuemax="480" aria-valuenow="320"></div>
<aside class="studio-inspector"><div class="studio-panel-tabs" role="tablist" aria-label="개발 도구"><button data-studio-panel="git" class="active" role="tab" aria-selected="true">⑂ Git</button><button data-studio-panel="codex" role="tab" aria-selected="false">✦ Codex</button></div>
<section id="studio-git"><div class="studio-toolbar"><b id="studio-branch">소스 관리</b><button class="icon-btn" data-studio="git-refresh" aria-label="Git 새로고침">↻</button><details class="ui-menu"><summary aria-label="저장소 설정">⋯</summary><div class="ui-menu-content"><button data-studio="git-init">저장소 초기화</button><button data-studio="git-clone">저장소 복제</button><button data-studio="git-identity">커밋 작성자</button><button data-studio="git-remote">원격 저장소</button><button data-studio="git-branch">새 브랜치</button><button data-studio="github-login">GitHub 로그인</button><button data-studio="github-status" id="studio-github-auth">GitHub 인증 확인</button></div></details></div><label class="studio-branch-select">브랜치<select id="studio-branches"><option>—</option></select></label><div class="studio-git-actions"><button data-studio="git-fetch">Fetch</button><button data-studio="git-pull">Pull</button><button data-studio="git-push">Push</button></div><form id="studio-commit"><textarea id="studio-commit-message" placeholder="변경 내용을 요약하세요" aria-label="커밋 메시지" maxlength="4000" required rows="2"></textarea><button type="submit" class="primary">커밋</button></form><div id="studio-changes"></div><details class="studio-history"><summary>최근 커밋</summary><pre id="studio-history"></pre></details></section>
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
    window.StudioPanels.attach(root);
    codeEditor = window.WorkspaceCodeEditor($('#studio-code'), content => {
      const doc = documents.get(activeFile);
      if (doc) { doc.content = content; doc.dirty = content !== doc.saved; renderTabs(); }
    });
    codeEditor.load('', '');
    codex=window.StudioCodex($('#studio-codex'),{escape,api:ui.api,toast:ui.toast,editor:ui.editor,project:()=>project,busy:()=>busy,setBusy,job:id=>{jobId=id;},dirty,confirm:confirmChange,auth:updateAuth,publish,context:kind=>{if(!activeFile)return null;const selection=codeEditor.selection?.();return kind==='selection'?(selection?.content?{kind,path:activeFile,name:activeFile+':'+selection.fromLine,...selection}:null):{kind:'file',path:activeFile,name:activeFile};}});
    root.addEventListener('click', onClick);
    root.addEventListener('submit', onSubmit);
    $('.studio-project-menu').addEventListener('keydown',event=>{if(event.key==='Escape'){$('.studio-project-menu').open=false;$('.studio-project-menu summary').focus();}});
    $('#studio-device').addEventListener('change', () => { $('.studio-project-menu').open=true; const option = $('#studio-device').selectedOptions[0]; $('#studio-root').value = option?.dataset.root || ''; refreshFolderOptions(); });
    $('#studio-root').addEventListener('change', refreshFolderOptions);
    $('#studio-branches').addEventListener('change', () => guard(async () => { if (dirty()) throw new Error('편집 내용을 먼저 저장해 주세요.'); await execute('git-switch', {branch:$('#studio-branches').value}); documents.clear(); activeFile = null; codeEditor.load('', ''); renderTabs(); await refreshFiles(); await refreshGit(); }));
    document.addEventListener('keydown', event => { if(root.classList.contains('active') && (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {event.preventDefault(); guard(saveFile);} });
    window.addEventListener('beforeunload', event => { if(dirty() || busy) {event.preventDefault(); event.returnValue='';} });
  }

  function dirty() { return [...documents.values()].some(doc => doc.dirty); }
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
    root.querySelectorAll('[data-studio="cancel"], [data-studio-panel], [data-pane]').forEach(element => {element.disabled=false;});
    root.querySelectorAll('[data-studio="cancel"]').forEach(button=>{button.hidden=!value;});
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
        await new Promise(resolve=>setTimeout(resolve,650));
        job=await ui.api(`/studio/jobs/${job.id}`);
        renderEvents(job.events);
      }
      renderEvents(job.events);
      if(job.state !== 'SUCCEEDED') { const error = new Error(job.error || '작업이 중지되었습니다.'); error.status=job.errorStatus; throw error; }
      $('#studio-job-status').textContent=`${labels[action] || action} · 완료`;
      if(responseCard){responseCard.querySelector('.assistant-status').textContent='완료';publish({codex:'완료'});}
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
      if(event.url && (/^https:\/\/auth\.openai\.com\//.test(event.url) || event.url==='https://github.com/login/device')) {const link=document.createElement('a');link.href=event.url;link.target='_blank';link.rel='noopener noreferrer';link.textContent='인증 페이지 열기';row.append(link);}
      const text=document.createElement('pre');text.textContent=event.code || event.text || event.state || '';row.append(text);container.append(row);
    }
    container.scrollTop=container.scrollHeight;if(eventTarget){const conversation=$('#studio-conversation');conversation.scrollTop=conversation.scrollHeight;}
  }
  async function connect() {
    if(dirty() && !await confirmChange('저장하지 않은 편집 내용을 닫고 다른 작업 폴더를 열까요?')) return;
    const context={deviceId:$('#studio-device').value,root:$('#studio-root').value};
    await execute('setup',{},context);
    const listing=await execute('list',{path:'.'},context);
    project={...context,root:listing.root};currentPath='.';documents.clear();directories.clear();expanded.clear();expanded.add('.');activeFile=null;authKnown=false;studioSummary={};publish({codex:'대기 중'});$('#studio-context').textContent=project.root;updateAuth(null);codex.reset();
    $('.studio-connection').textContent=project.deviceId==='local'?'서버 자체 · 로컬 실행':'SSH · 원격 실행';
    codeEditor.load('', '');renderTabs();
    $('.studio-workbench').hidden=false;$('#studio-start').hidden=true;$('.studio-project-menu').open=false;$('#studio-project-name').textContent=project.root.split('/').filter(Boolean).pop()||'/';$('#studio-project-name').title=project.root;renderFiles(listing);await refreshGit();
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
    while(job.state==='RUNNING'){await new Promise(resolve=>setTimeout(resolve,250));job=await ui.api('/studio/jobs/'+job.id);}
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
  function renderTree(){
    const branch=(path,depth)=> (directories.get(path)?.entries||[]).map(entry=>'<div class="studio-file-row '+(entry.path===activeFile?'selected':'')+'" style="--tree-depth:'+depth+'"><button data-studio="'+(entry.directory?'directory':'file')+'" data-path="'+escape(entry.path)+'" title="'+escape(entry.name)+'" '+(entry.directory?'aria-expanded="'+expanded.has(entry.path)+'"':'')+'><span>'+(entry.directory?(expanded.has(entry.path)?'⌄':'›'):'·')+'</span>'+escape(entry.name)+'</button><button data-studio="rename" data-path="'+escape(entry.path)+'" aria-label="이름 변경">✎</button><button data-studio="delete" data-path="'+escape(entry.path)+'" aria-label="삭제">×</button></div>'+(entry.directory&&expanded.has(entry.path)?branch(entry.path,depth+1):'')).join('');
    $('#studio-tree').innerHTML=branch('.',0)||'<p class="empty-state">빈 폴더입니다.</p>';
  }
  async function refreshFiles(){directories.clear();expanded.clear();expanded.add('.');renderFiles(await execute('list',{path:'.'}));}
  function renderTabs() {
    $('#studio-file-tabs').innerHTML=[...documents].map(([path,doc])=>`<div class="studio-document ${path===activeFile?'active':''}"><button role="tab" aria-selected="${path===activeFile}" data-studio="tab" data-path="${escape(path)}">${escape(path.split('/').pop())}${doc.dirty?' ●':''}</button><button data-studio="close" data-path="${escape(path)}" aria-label="파일 닫기">×</button></div>`).join('');
    $('#studio-file-label').textContent=activeFile || '파일을 선택하세요';
    $('#studio-empty').hidden=Boolean(activeFile);$('#studio-code').hidden=!activeFile;
    if(!busy){$('[data-studio="save"]').disabled=!activeFile;$('[data-studio="reload-file"]').disabled=!activeFile;}
    renderTree();
    $('#studio-dirty').textContent=documents.get(activeFile)?.dirty?'저장하지 않은 변경':'UTF-8 · 서버에 저장됨';
  }
  function activateFile(path) {$('.studio-workbench').dataset.mobilePane='editor';activeFile=path;codeEditor.load(path,documents.get(path).content);renderTabs();codeEditor.focus();}
  async function openFile(path, reload=false) {
    if(!reload && documents.has(path)) {activateFile(path);return;}
    if(documents.get(path)?.dirty && !await confirmChange('편집 내용을 버리고 서버 파일을 다시 읽을까요?'))return;
    const doc=await execute('read',{path});documents.set(path,{...doc,saved:doc.content,dirty:false});activateFile(path);
  }
  async function saveFile() {
    const doc=documents.get(activeFile);if(!doc || !doc.dirty)return;
    const path=activeFile, content=doc.content;
    const saved=await execute('save',{path,content,revision:doc.revision});
    doc.revision=saved.revision;doc.saved=content;doc.dirty=doc.content!==content;renderTabs();
  }
  function gitStatusLabel(change,staged){
    if(change.index==='U'||change.worktree==='U')return ['충돌','danger'];
    const code=staged?change.index:change.worktree;
    if(code==='?')return ['새 파일','success'];
    return ({A:['추가','success'],M:['수정','warning'],D:['삭제','danger'],R:['이름 변경','neutral'],C:['복사','neutral']})[code]||['변경','neutral'];
  }
  async function refreshGit() {
    try {
      const status=await execute('git-status');$('#studio-branch').textContent=`⑂ ${status.branch}`;
      $('#studio-branches').innerHTML=[...new Set([status.branch,...status.branches])].map(branch=>`<option value="${escape(branch)}" ${branch===status.branch?'selected':''}>${escape(branch)}</option>`).join('');
      const conflicts=status.changes.filter(change=>change.index==='U'||change.worktree==='U');
      const staged=status.changes.filter(change=>!conflicts.includes(change)&&change.index!==' '&&change.index!=='?');
      const working=status.changes.filter(change=>!conflicts.includes(change)&&(change.worktree!==' '||change.index==='?'));
      const changeRows=(changes,stagedGroup)=>changes.map(change=>{const [label,tone]=gitStatusLabel(change,stagedGroup),disabled=label==='충돌';return `<div class="studio-change"><button data-studio="git-diff" data-path="${escape(change.path)}" data-untracked="${change.index==='?'}" data-staged="${stagedGroup}" aria-label="${escape(change.path)} ${label} 변경 내용 보기"><code class="git-status-${tone}">${escape(stagedGroup?change.index:change.worktree)}</code><span>${escape(change.path)}</span><em>${label}</em></button><button data-studio="${stagedGroup?'git-unstage':'git-stage'}" data-path="${escape(change.path)}" aria-label="${stagedGroup?'스테이징 취소':'스테이징'}" ${disabled?'disabled':''}>${stagedGroup?'−':'+'}</button></div>`;}).join('');
      const group=(title,description,changes,stagedGroup,action)=>`<details open class="change-group ${changes.length?'':'is-empty'}"><summary><span>${title} <span class="badge">${changes.length}</span></span>${action?`<button type="button" class="ghost sm" data-studio="${action}">${stagedGroup?'전체 스테이징 해제':'전체 스테이징'}</button>`:''}</summary><p class="change-group-hint">${description}</p>${changeRows(changes,stagedGroup)||'<p class="empty-state">없음</p>'}</details>`;
      $('#studio-changes').innerHTML=group('해결이 필요한 충돌','충돌을 해결한 뒤 파일을 다시 스테이징하세요.',conflicts,false,null)+group('커밋에 포함될 변경','아래 파일만 다음 커밋에 들어갑니다.',staged,true,'git-unstage-all')+group('작업 폴더 변경','검토한 뒤 스테이징하거나 변경 내용을 확인하세요.',working,false,'git-stage-all')+(status.changes.length?'':'<p class="empty-state">작업 폴더가 깨끗합니다.</p>');
      publish({branch:status.branch,changes:status.changes.length});
      $('#studio-history').textContent=status.history;
    } catch(error) {$('#studio-branch').textContent='Git 저장소 확인 필요';$('#studio-changes').textContent=error.message;$('#studio-history').textContent='';$('#studio-branches').innerHTML='<option>—</option>';}
  }
  async function input(title, items, submit) {
    ui.editor(title,items.map(item=>`<label>${escape(item.label)}<input name="${item.name}" value="${escape(item.value || '')}" maxlength="${item.max || 1024}" required></label>`).join(''),async form=>submit(Object.fromEntries(form.entries())));
  }
  async function onSubmit(event) {
    event.preventDefault();
    await guard(async()=> {
      if(event.target.matches('.studio-connect'))await connect();
      if(event.target.id==='studio-commit') {await execute('git-commit',{message:$('#studio-commit-message').value});$('#studio-commit-message').value='';await refreshGit();}

    });
  }
  async function onClick(event) {
    const button=event.target.closest('button');if(!button)return;
    if(button.dataset.studioPanel) {activePanel=button.dataset.studioPanel;$('.studio-workbench').dataset.mobilePane='inspector';$('#studio-git').hidden=activePanel!=='git';$('#studio-codex').hidden=activePanel!=='codex';root.querySelectorAll('[data-studio-panel]').forEach(item=>{item.classList.toggle('active',item===button);item.setAttribute('aria-selected',String(item===button));});if(activePanel==='codex')codex.load();return;}
    const action=button.dataset.studio, path=button.dataset.path;if(!action)return;
    await guard(async()=>{
      if(action==='open-project'){$('.studio-project-menu').open=true;$('#studio-root').focus();await refreshFolderOptions();return;}
      if(action==='folder-options'){await refreshFolderOptions();return;}
      if(action==='cancel') {event.preventDefault();if(jobId)await ui.api(`/studio/jobs/${jobId}`,'DELETE');return;}
      if(action==='terminal') {await ui.openTerminal(project.deviceId);return;}
      if(action==='file' || action==='tab') {await openFile(path);return;}
      if(action==='close') {if(documents.get(path)?.dirty && !await confirmChange('저장하지 않은 파일을 닫을까요?'))return;documents.delete(path);if(activeFile===path) {activeFile=documents.keys().next().value;if(activeFile)activateFile(activeFile);else codeEditor.load('', '');}renderTabs();return;}
      if(action==='save') {await saveFile();return;}
      if(action==='reload-file') {if(activeFile)await openFile(activeFile,true);return;}
      if(action==='directory') {currentPath=path;if(expanded.has(path)){expanded.delete(path);renderTree();}else renderFiles(directories.get(path)||await execute('list',{path}));return;}
      if(action==='parent') {const parent=currentPath.split('/').slice(0,-1).join('/') || '.';renderFiles(await execute('list',{path:parent}));return;}
      if(action==='refresh') {await refreshFiles();return;}
      if(['create','mkdir','rename'].includes(action)) {
        if(action==='rename' && dirty())throw new Error('파일 이름을 변경하기 전에 편집 내용을 저장해 주세요.');
        await input(action==='rename'?'이름 변경':action==='mkdir'?'새 폴더':'새 파일',[{name:'name',label:'작업 폴더 기준 경로',value:action==='rename'?path:(currentPath==='.'?'':currentPath+'/')}],async values=>{await execute(action,action==='rename'?{path,target:values.name}:{path:values.name});if(action==='rename'){documents.clear();activeFile=null;codeEditor.load('', '');renderTabs();}await refreshFiles();});return;
      }
      if(action==='delete') {if(!await confirmChange(`${path}을 삭제할까요? 폴더는 비어 있을 때만 삭제됩니다.`))return;await execute(action,{path});documents.delete(path);if(activeFile===path){activeFile=null;codeEditor.load('', '');}renderTabs();await refreshFiles();return;}
      if(action==='git-refresh') {await refreshGit();return;}
      if(action==='github-login' || action==='github-status') {const result=await execute(action);$('#studio-github-auth').textContent=result.authenticated?'GitHub 인증됨':'GitHub 로그인 필요';return;}
      if(action==='git-diff') {const result=await execute(action,{path,staged:button.dataset.staged==='true',untracked:button.dataset.untracked==='true'});$('#studio-diff').textContent=result.diff || '변경 내용이 없습니다.';$('#studio-diff').hidden=false;$('.studio-output').open=true;return;}
      if(action==='git-stage-all'||action==='git-unstage-all') {const status=await execute('git-status');const stagedGroup=action==='git-unstage-all';const paths=status.changes.filter(change=>stagedGroup?(change.index!==' '&&change.index!=='?'):(change.worktree!==' '||change.index==='?')).filter(change=>!(change.index==='U'||change.worktree==='U')).map(change=>change.path);for(const item of paths)await execute(stagedGroup?'git-unstage':'git-stage',{path:item});await refreshGit();return;}
      if(action==='git-clone') {await input('Git 저장소 복제',[{name:'url',label:'저장소 주소',max:2048},{name:'target',label:'새 폴더 이름'}],async args=>{const result=await execute(action,args);$('#studio-root').value=result.path;await refreshFiles();ui.toast('복제되었습니다. 작업 폴더 열기로 저장소를 여세요.');});return;}
      if(action==='git-branch') {if(dirty())throw new Error('편집 내용을 먼저 저장해 주세요.');await input('새 브랜치로 전환',[{name:'branch',label:'브랜치 이름'}],async args=>{await execute(action,args);await refreshGit();});return;}
      if(action==='git-identity') {await input('이 저장소의 커밋 작성자',[{name:'name',label:'이름',max:200},{name:'email',label:'이메일',max:200}],args=>execute(action,args));return;}
      if(action==='git-remote') {await input('origin 원격 저장소 설정',[{name:'url',label:'HTTPS 또는 SSH 저장소 주소',max:2048}],args=>execute(action,args));return;}
      if(action.startsWith('git-')) {if(dirty() && action==='git-pull')throw new Error('편집 내용을 먼저 저장해 주세요.');if(action==='git-push' && !await confirmChange('현재 브랜치의 커밋을 원격 저장소로 Push할까요?'))return;await execute(action,path?{path}:{});await refreshGit();return;}
      if(action.startsWith('codex-')) {if(action==='codex-logout' && !await confirmChange('이 서버 계정의 Codex에서 로그아웃할까요?'))return;const result=await execute(action);updateAuth(result.authenticated);return;}
    });
  }
  function updateAuth(authenticated){authKnown=authenticated!==null;$('#studio-auth').textContent=authenticated===null?'인증 확인 전':authenticated?'CLI 인증됨':'로그인 필요';$('#studio-auth-cta').hidden=authenticated!==false;root.querySelectorAll('.ui-menu [data-studio="codex-login"]').forEach(button=>button.hidden=authenticated!==false);$('[data-studio="codex-logout"]').hidden=authenticated!==true;}
  window.WorkspaceStudio={
    init(helpers){ui=helpers;renderShell();},
    async open(id){if(id!=='studio')return;const state=await ui.api('/workspace');const selected=$('#studio-device').value;$('#studio-device').innerHTML=[...state.devices].sort((a,b)=>Number(b.id==='local')-Number(a.id==='local')).map(device=>`<option value="${escape(device.id)}" data-root="${escape(device.rootPath)}">${device.id === 'local' ? '서버 자체' : escape(device.name)+' · '+escape(device.host)+(device.networkMode==='TAILSCALE'?' · Tailscale':'')}</option>`).join('');if(selected)$('#studio-device').value=selected;if(!$('#studio-root').value){let saved;try{saved=JSON.parse(localStorage.getItem('workspace-studio-project-v1'));}catch{}if(saved && state.devices.some(device=>device.id===saved.deviceId)){$('#studio-device').value=saved.deviceId;$('#studio-root').value=saved.root;}else $('#studio-root').value=$('#studio-device').selectedOptions[0]?.dataset.root || '';}}
  };
})();
