'use strict';
/** Personal drive UI. All file operations are authenticated server requests. */
window.WorkspaceCloud=(()=>{
 let page=0;
 let ui,root,path='/',trash=false,entries=[],selected=new Set(),clipboard=null,generation=0,busy=false,uploadRequest=null,cancelUpload=false;
 const $=selector=>root.querySelector(selector),esc=value=>ui.escape(value);
 const join=(base,name)=>(base==='/'?'':base)+'/'+name;
 const name=value=>value.split('/').pop();
 const bytes=value=>value<1024?value+' B':value<1048576?(value/1024).toFixed(1)+' KB':value<1073741824?(value/1048576).toFixed(1)+' MB':(value/1073741824).toFixed(1)+' GB';
 const date=value=>new Date(value).toLocaleString('ko-KR');
 const shortDate=value=>new Date(value).toLocaleDateString('ko-KR',{year:'2-digit',month:'2-digit',day:'2-digit'});
 const tell=text=>{$('[data-cloud-status]').textContent=text;};
 function buttons(){
  root.querySelectorAll('[data-cloud-needs-selection]').forEach(button=>button.disabled=busy||selected.size===0);
  $('[data-cloud-paste]').disabled=busy||trash||!clipboard;
  const filter=$('[data-cloud-filter]').value.trim();
  $('[data-cloud-count]').textContent=(selected.size?selected.size+'개 선택':entries.length+'개 항목')+(filter?' · 검색: '+filter:'');
  $('[data-cloud-selected-count]').textContent=selected.size+'개 선택';
  root.classList.toggle('cloud-search-filtered',Boolean(filter));
  root.classList.toggle('cloud-has-selection',selected.size>0);
  root.classList.toggle('cloud-has-paste',Boolean(clipboard)&&!trash);
  $('[data-cloud-action="selection-toggle"]').setAttribute('aria-expanded',String(selected.size>0||Boolean(clipboard)&&!trash||root.classList.contains('cloud-selection-open')));
  root.querySelectorAll('[data-cloud-normal]').forEach(element=>element.hidden=trash);
  root.querySelectorAll('[data-cloud-trash-only]').forEach(element=>element.hidden=!trash);
  root.querySelectorAll('[data-cloud-write]').forEach(element=>element.disabled=busy);
 }
 function syncViewToggle(){
  const view=$('[data-cloud-view]').value,button=$('[data-cloud-action="view-toggle"]');
  button.innerHTML=window.WorkspaceUI.icon(view==='list'?'apps':'menu');
  button.setAttribute('aria-label',view==='list'?'격자 보기':'목록 보기');
  button.title=button.getAttribute('aria-label');
 }
 const paint=(target,html,options={})=>options.quiet&&window.WorkspaceLiveDOM?window.WorkspaceLiveDOM.patch(target,html):target.innerHTML=html;
 function draw(options={}){
  const query=$('[data-cloud-filter]').value.toLowerCase(),sort=$('[data-cloud-sort]').value;
  const visible=entries.filter(entry=>!trash||entry.path.toLowerCase().includes(query)).sort((a,b)=>{
   if(a.directory!==b.directory)return a.directory?-1:1;
   if(sort==='modified')return (b.modified||b.deletedAt)-(a.modified||a.deletedAt);
   if(sort==='size')return (b.size||0)-(a.size||0);
   return name(a.path).localeCompare(name(b.path),'ko',{numeric:true});
  });
  $('[data-cloud-location]').textContent=trash?'휴지통':path==='/'?'내 드라이브':name(path);
  root.classList.toggle('cloud-at-root',!trash&&path==='/');
  const crumbs=[{path:'/',label:'내 드라이브'}];let cursor='';for(const part of path.split('/').filter(Boolean)){cursor+='/'+part;crumbs.push({path:cursor,label:part});}
  $('[data-cloud-crumbs]').innerHTML=trash?'휴지통':crumbs.map(part=>`<button data-cloud-path="${esc(part.path)}">${esc(part.label)}</button>`).join('<span aria-hidden="true">/</span>');
  page=Math.min(page,Math.max(0,Math.ceil(visible.length/100)-1));
  $('[data-cloud-page]').textContent=(page+1)+' / '+Math.max(1,Math.ceil(visible.length/100))+' 페이지';
  $('.cloud-pagination').hidden=visible.length<=100;
  $('[data-cloud-action=previous]').disabled=page===0;
  $('[data-cloud-action=next]').disabled=(page+1)*100>=visible.length;
  paint($('[data-cloud-items]'),visible.slice(page*100,(page+1)*100).map(entry=>{
   const id=trash?entry.id:entry.path,fileName=name(entry.path);
   const type=trash?entry.path:entry.directory?'폴더':fileName.split('.').pop().toUpperCase()+' 파일';
   const modified=date(entry.modified||entry.deletedAt),mobileDate=shortDate(entry.modified||entry.deletedAt);
   return `<article class="cloud-item ${selected.has(id)?'selected':''}" data-live-key="${esc(id)}" data-cloud-item="${esc(id)}"><label class="cloud-select-hit"><input type="checkbox" data-cloud-select="${esc(id)}" aria-label="${esc(fileName)} 선택" ${selected.has(id)?'checked':''}></label><button class="cloud-item-open" data-cloud-open="${esc(id)}"><span class="cloud-icon ${entry.directory?'cloud-folder-icon':'cloud-file-icon'}" aria-hidden="true">${window.WorkspaceUI.icon(entry.directory?'folder':'file')}</span><span><b>${esc(fileName)}</b><small class="cloud-item-type">${esc(type)}</small><small class="cloud-mobile-meta">${esc(type)}${entry.directory?'':' · '+bytes(entry.size||0)} · ${mobileDate}</small></span></button><span class="cloud-item-size">${entry.directory?'—':bytes(entry.size||0)}</span><time>${modified}</time><button class="ghost cloud-item-info" data-cloud-info="${esc(id)}" aria-label="${esc(fileName)} 상세 정보">${window.WorkspaceUI.icon('info')}</button></article>`;
  }).join('')||window.WorkspaceUI.emptyState(trash?'휴지통이 비어 있습니다.':query?'검색 결과가 없습니다.':'폴더가 비어 있습니다.',trash?'':'파일을 끌어 놓아 업로드할 수 있습니다.','files'),options);
  buttons();
 }
 async function refresh(options={}){
  const version=++generation;if(!options.quiet){page=0;selected.clear();$('[data-cloud-items]').innerHTML=window.WorkspaceUI.skeleton(3);tell('');}
  try{
   const result=await ui.api(trash?'/cloud/trash':`/cloud?path=${encodeURIComponent(path)}&query=${encodeURIComponent($('[data-cloud-filter]').value)}`,'GET',undefined,options);
   if(version!==generation)return;
   entries=trash?result:result.entries;
   if(options.quiet){const ids=new Set(entries.map(entry=>trash?entry.id:entry.path));for(const id of selected)if(!ids.has(id))selected.delete(id);}
   if(!trash)$('[data-cloud-space]').textContent='서버 저장 공간: '+bytes(result.usableSpace)+' 여유 / '+bytes(result.totalSpace);
   draw(options);if(!options.quiet)tell(result.truncated?'처리 한도에 도달했습니다. 더 작은 폴더에서 검색하세요.':'');
  }catch(error){if(options.quiet)throw error;if(version===generation){$('[data-cloud-items]').innerHTML=window.WorkspaceUI.emptyState('파일을 불러오지 못했습니다.',error.message,'warning');tell(error.message);}}
 }
  async function navigate(target){if(busy){tell('진행 중인 작업이 끝난 뒤 이동하세요.');return;}trash=false;path=target;$('[data-cloud-filter]').value='';root.classList.remove('cloud-search-open','cloud-selection-open');$('[data-cloud-action="search-toggle"]').setAttribute('aria-expanded','false');await refresh();}
 async function batch(ids,operation){
  if(busy)return;busy=true;buttons();const failures=[];let completed=0;
  try{for(const id of ids){try{await operation(id);completed++;}catch(error){failures.push(name(id)+': '+error.message);}tell(`${completed} / ${ids.length} 처리 중…`);}}
  finally{busy=false;await refresh();tell(`${completed}개 완료${failures.length?' · '+failures.join(' / '):''}`);}
 }
 function download(paths){
  if(!paths.length)return;
  const link=document.createElement('a');
  link.href=paths.length===1?'/api/v1/cloud/content?path='+encodeURIComponent(paths[0]):'/api/v1/cloud/archive?'+paths.map(value=>'path='+encodeURIComponent(value)).join('&');
  if(link.href.length>7500||paths.length>100){tell('한 번에 최대 100개를 선택하거나 폴더 단위로 다운로드하세요.');return;}
  link.download='';link.click();
 }
 function create(directory){
  ui.editor(directory?'새 폴더':'새 파일','<label>이름<input name="name" required maxlength="255" autocomplete="off"></label>',async form=>{
   const value=String(form.get('name'));if(!value.trim()||/[\\/]/.test(value))throw new Error('이름에는 경로 구분자를 사용할 수 없습니다.');
   await ui.api('/cloud/entries','POST',{path:join(path,value),directory});await refresh();
  });
 }
 function rename(){
  if(selected.size!==1){tell('이름을 바꿀 항목 하나를 선택하세요.');return;}
  const source=[...selected][0];
  ui.editor('이름 변경',`<label>새 이름<input name="name" value="${esc(name(source))}" required maxlength="255"></label>`,async form=>{
   const value=String(form.get('name'));if(!value.trim()||/[\\/]/.test(value))throw new Error('올바른 이름을 입력하세요.');
   await ui.api('/cloud/transfers','POST',{source,target:join(source.slice(0,source.lastIndexOf('/'))||'/',value),copy:false});await refresh();
  });
 }
 function destination(copy){
  const paths=[...selected];let destination='/';
  const originalName=name(paths[0]||''),dot=originalName.lastIndexOf('.');
  const proposed=copy?(dot>0?originalName.slice(0,dot)+' - 사본'+originalName.slice(dot):originalName+' - 사본'):originalName;
  ui.editor(copy?'복사할 위치':'이동할 위치',(paths.length===1?'<label>대상 이름<input name="targetName" value="'+esc(proposed)+'" required maxlength="255"></label>':'')+'<div id="cloud-folder-picker">불러오는 중…</div>',async form=>{
   const targetName=paths.length===1?String(form.get('targetName')):'';
   if(paths.length===1&&(!targetName.trim()||/[\\/]/.test(targetName)))throw new Error('올바른 대상 이름을 입력하세요.');
   await batch(paths,source=>ui.api('/cloud/transfers','POST',{source,target:join(destination,paths.length===1?targetName:name(source)),copy}));
  },copy?'여기에 복사':'여기로 이동');
  const picker=document.getElementById('cloud-folder-picker');let revision=0;
  async function browse(target){
   const version=++revision;
   try{const listing=await ui.api('/cloud?path='+encodeURIComponent(target));if(version!==revision||!picker.isConnected)return;
    destination=target;picker.innerHTML=`<p>선택 위치: <b>${esc(target)}</b></p><button type="button" data-picker="${esc(target.slice(0,target.lastIndexOf('/'))||'/')}">↑ 상위 폴더</button><div class="cloud-picker">${listing.entries.filter(item=>item.directory).map(item=>`<button type="button" data-picker="${esc(item.path)}">▰ ${esc(item.name)}</button>`).join('')||'<p>하위 폴더 없음</p>'}</div>`;
   }catch(error){picker.textContent=error.message;}
  }
  picker.addEventListener('click',event=>{const button=event.target.closest('[data-picker]');if(button)browse(button.dataset.picker);});browse('/');
 }
 let fileEditor=null;
 const editorDirty=()=>fileEditor?.input&&fileEditor.input.value!==fileEditor.saved;
 /** Full drive editor keeps its buffer across app switches and owns preview URLs. */
 async function preview(value){
  const entry=entries.find(item=>item.path===value);if(!entry)return;
  if(entry.directory){await navigate(value);return;}
  if(fileEditor)return;
  const session={saved:'',saving:false};fileEditor=session;root.classList.add('cloud-editing');
  const screen=document.createElement('section');screen.className='cloud-editor';
  screen.innerHTML=`<header class="cloud-editor-header"><button data-file-back aria-label="드라이브로 돌아가기">${window.WorkspaceUI.icon('back')}<span class="cloud-action-label">드라이브로 돌아가기</span></button><div><h1>${esc(name(value))}</h1><p>${esc(value)}</p></div><button class="primary" data-file-save aria-label="파일 저장" hidden disabled>${window.WorkspaceUI.icon('save')}<span class="cloud-action-label">저장</span></button><a class="button" href="/api/v1/cloud/content?path=${encodeURIComponent(value)}" aria-label="파일 다운로드" download>${window.WorkspaceUI.icon('download')}<span class="cloud-action-label">다운로드</span></a></header><p data-file-status role="status">불러오는 중…</p><div class="cloud-editor-content"></div>`;
  root.append(screen);
  const content=screen.querySelector('.cloud-editor-content'),status=screen.querySelector('[data-file-status]'),save=screen.querySelector('[data-file-save]');
  screen.querySelector('[data-file-back]').onclick=async()=>{
   if(session.saving)return;
   if(editorDirty()&&!window.confirm('저장하지 않은 변경을 버리고 드라이브로 돌아갈까요?'))return;
   if(session.url)URL.revokeObjectURL(session.url);
   fileEditor=null;screen.remove();root.classList.remove('cloud-editing');await refresh();
  };
  screen.querySelector('[data-file-back]').focus();
  try{
  if(/\.(png|jpe?g|gif|webp)$/i.test(value)&&entry.size<=20*1024*1024){
   const finishTask=window.WorkspaceUI.beginTask?.('이미지를 불러오는 중…')||(()=>{});
   try{
   const response=await fetch('/api/v1/cloud/content?path='+encodeURIComponent(value));if(!response.ok)throw new Error('이미지를 불러오지 못했습니다.');
   const blob=await response.blob();if(fileEditor!==session)return;
   session.url=URL.createObjectURL(blob);
   const image=document.createElement('img');image.className='cloud-preview-image';image.src=session.url;image.alt=name(value);content.append(image);
   image.onerror=()=>{status.textContent='이미지를 표시하지 못했습니다. 다운로드로 확인하세요.';};
   status.textContent='이미지 열람';return;
   }finally{finishTask();}
  }
   const text=await ui.api('/cloud/preview?path='+encodeURIComponent(value));
   if(fileEditor!==session)return;
   session.saved=text.content;session.revision=text.revision;
   const input=document.createElement('textarea');input.className='cloud-text-editor';input.setAttribute('aria-label','UTF-8 텍스트 편집');input.spellcheck=false;input.value=text.content;session.input=input;content.append(input);
   save.hidden=false;status.textContent='UTF-8 텍스트 · 변경 후 저장하세요.';
   input.oninput=()=>{save.disabled=session.saving||!session.revision||!editorDirty();status.textContent=editorDirty()?'저장하지 않은 변경이 있습니다.':'변경 사항이 없습니다.';};
   save.onclick=async()=>{
    if(session.saving||!session.revision)return;
    session.saving=true;save.disabled=true;input.readOnly=true;status.textContent='저장 중…';
    const submitted=input.value;
    try{
     await ui.api('/cloud/text','PUT',{path:value,content:submitted,revision:session.revision});
     session.saved=submitted;session.revision=null;
     // Adopt the next revision only when it still belongs to the submitted content.
     const current=await ui.api('/cloud/preview?path='+encodeURIComponent(value));
     if(current.content!==submitted)throw new Error('저장 후 파일이 다시 변경되었습니다.');
     session.revision=current.revision;status.textContent='저장했습니다.';
    }catch(error){status.textContent=error.message+(session.revision?'':' 파일을 다시 열어 최신 내용을 확인하세요.');}
    finally{session.saving=false;input.readOnly=false;save.disabled=!session.revision||!editorDirty();}
   };
   input.focus();
  }catch(error){if(fileEditor===session)status.textContent=error.message+' 다운로드로 확인할 수 있습니다.';}
 }
 function uploadOne(file,target,overwrite){
  return new Promise((resolve,reject)=>{
   const xhr=new XMLHttpRequest();uploadRequest=xhr;xhr.open('POST','/api/v1/cloud/uploads');
   xhr.setRequestHeader(document.querySelector('meta[name=csrf-header]').content,document.querySelector('meta[name=csrf-token]').content);
   xhr.upload.onprogress=event=>{if(event.lengthComputable){$('[data-cloud-progress]').value=event.loaded/event.total*100;}};
   xhr.onload=()=>{uploadRequest=null;if(xhr.status>=200&&xhr.status<300)resolve();else{let message='업로드에 실패했습니다.';try{message=JSON.parse(xhr.responseText).message||message;}catch{}reject(new Error(message));}};
   xhr.onerror=()=>{uploadRequest=null;reject(new Error('네트워크 연결을 확인하세요.'));};xhr.onabort=()=>{uploadRequest=null;reject(new Error('업로드가 취소되었습니다.'));};
   const form=new FormData();form.append('file',file);form.append('path',target);form.append('overwrite',String(overwrite));xhr.send(form);
  });
 }
 async function upload(list){
  if(busy||trash||!list.length)return;
  busy=true;cancelUpload=false;buttons();$('[data-cloud-upload-state]').hidden=false;
  const destination=path,overwrite=$('[data-cloud-overwrite]').checked,failures=[];let done=0;
  try{for(const file of list){if(cancelUpload)break;
   tell(`업로드 ${done+1}/${list.length} · ${file.name}`);$('[data-cloud-progress]').value=0;
   try{await uploadOne(file,join(destination,file.webkitRelativePath||file.name),overwrite);done++;}catch(error){failures.push(file.name+': '+error.message);}
  }}finally{busy=false;$('[data-cloud-upload-state]').hidden=true;await refresh();tell(`${done}개 업로드 완료${cancelUpload?' · 나머지 취소':''}${failures.length?' · '+failures.join(' / '):''}`);}
 }
 async function click(event){
  const button=event.target.closest('button');if(!button)return;
  if(button.dataset.cloudPath!==undefined)return navigate(button.dataset.cloudPath);
  if(button.dataset.cloudOpen!==undefined){if(trash){selected=new Set([button.dataset.cloudOpen]);draw();return;}return preview(button.dataset.cloudOpen);}
  if(button.dataset.cloudInfo!==undefined){const item=entries.find(entry=>(trash?entry.id:entry.path)===button.dataset.cloudInfo);ui.editor('상세 정보',`<dl><dt>경로</dt><dd>${esc(item.path)}</dd><dt>종류</dt><dd>${item.directory?'폴더':'파일'}</dd><dt>크기</dt><dd>${item.directory?'폴더 다운로드는 ZIP으로 제공됩니다.':bytes(item.size||0)}</dd><dt>${trash?'삭제':'수정'} 시각</dt><dd>${date(item.modified||item.deletedAt)}</dd></dl>`,async()=>{},'닫기');return;}
  const action=button.dataset.cloudAction;if(!action)return;
  const actionMenu=button.closest('.cloud-action-menu');if(actionMenu)actionMenu.open=false;
  if(['home','trash','nas'].includes(action))button.closest('.ui-side-drawer')?.close();
  if(action==='create-toggle'){
   window.WorkspaceDrawers.open($('.cloud-create'),button,{title:'새 항목',side:'bottom'});
   return;
  }
  if(action==='search-toggle'){const open=root.classList.toggle('cloud-search-open');button.setAttribute('aria-expanded',String(open));if(open)$('[data-cloud-filter]').focus();return;}
  if(action==='selection-toggle'){const open=root.classList.toggle('cloud-selection-open');button.setAttribute('aria-expanded',String(open||selected.size>0));return;}
  if(action==='view-toggle'){
   const view=$('[data-cloud-view]');view.value=view.value==='list'?'grid':'list';
   $('[data-cloud-items]').dataset.layout=view.value;
   syncViewToggle();
   return;
  }
  if(action==='cancel'){cancelUpload=true;uploadRequest?.abort();return;}
  if(busy){tell('현재 작업이 끝날 때까지 기다려 주세요.');return;}
  if(action==='previous'||action==='next'){page+=action==='next'?1:-1;draw();return;}
  if(action==='nas')return window.WorkspaceNas.open();
  if(action==='home')return navigate('/');
  if(action==='trash'){trash=true;$('[data-cloud-filter]').value='';root.classList.remove('cloud-search-open','cloud-selection-open');$('[data-cloud-action="search-toggle"]').setAttribute('aria-expanded','false');return refresh();}
  if(action==='reload')return refresh();
  if(action==='folder'||action==='file'){
   window.WorkspaceDrawers?.close();
   return create(action==='folder');
  }
  if(action==='upload'||action==='upload-folder'){
   window.WorkspaceDrawers?.close();
   $(action==='upload'?'[data-cloud-files]':'[data-cloud-folders]').click();
   return;
  }
  if(action==='all'){const visible=[...root.querySelectorAll('[data-cloud-select]')].map(item=>item.dataset.cloudSelect);selected=visible.every(id=>selected.has(id))?new Set():new Set(visible);draw();return;}
  if(action==='rename')return rename();
  if(action==='download')return download([...selected]);
  if(action==='copy'||action==='move')return destination(action==='copy');
  if(action==='cut'||action==='clipboard'){clipboard={paths:[...selected],copy:action==='clipboard'};buttons();tell(clipboard.paths.length+'개 '+(clipboard.copy?'복사':'잘라내기')+' 대기 · 대상 폴더에서 붙여넣으세요.');return;}
  if(action==='paste'){const pending=clipboard;await batch(pending.paths,source=>ui.api('/cloud/transfers','POST',{source,target:join(path,name(source)),copy:pending.copy}));if(!pending.copy)clipboard=null;buttons();return;}
  if(action==='delete'){const ids=[...selected];ui.confirmAction('휴지통으로 이동',ids.length+'개 항목과 하위 파일을 휴지통으로 이동합니다.',()=>batch(ids,id=>ui.api('/cloud/entries?path='+encodeURIComponent(id),'DELETE')));return;}
  if(action==='restore')return batch([...selected],id=>ui.api('/cloud/trash/'+id+'/restoration','POST'));
  if(action==='purge'||action==='empty'){const ids=action==='empty'?entries.map(item=>item.id):[...selected];ui.confirmAction('영구 삭제',ids.length+'개 항목을 영구 삭제합니다. 복구할 수 없습니다.',()=>batch(ids,id=>ui.api('/cloud/trash/'+id,'DELETE')));}
 }
 return {
  init(helpers){
   ui=helpers;root=document.getElementById('cloud');if(!root)return;
   root.innerHTML=`<aside class="cloud-sidebar"><h2>☁ 드라이브</h2><button data-cloud-action="home">내 드라이브</button><button data-cloud-action="trash">휴지통</button><button data-cloud-action="nas">NAS 연결</button><p data-cloud-space class="section-hint"></p></aside><section class="cloud-main"><div class="cloud-header"><h1 data-cloud-location>내 드라이브</h1><form data-cloud-search><input data-cloud-filter placeholder="이 폴더와 하위 폴더 검색" aria-label="파일 검색" maxlength="200"><button>검색</button></form><button data-cloud-action="reload" aria-label="새로고침">↻</button></div><nav data-cloud-crumbs aria-label="폴더 경로"></nav><div class="cloud-toolbar cloud-create" data-cloud-normal><button class="primary" data-cloud-action="upload" data-cloud-write>파일 업로드</button><button data-cloud-action="upload-folder" data-cloud-write>폴더 업로드</button><button data-cloud-action="folder" data-cloud-write>새 폴더</button><button data-cloud-action="file" data-cloud-write>새 파일</button><label><input type="checkbox" data-cloud-overwrite>같은 이름 업로드 덮어쓰기</label><input type="file" multiple data-cloud-files hidden><input type="file" webkitdirectory multiple data-cloud-folders hidden></div><div class="cloud-toolbar cloud-selection"><button data-cloud-action="all">페이지 전체 선택</button><span data-cloud-count></span><span data-cloud-normal><button data-cloud-action="download" data-cloud-needs-selection>다운로드</button><button data-cloud-action="delete" data-cloud-needs-selection>삭제</button><details class="ui-menu cloud-action-menu"><summary aria-label="더 많은 파일 작업">더 보기</summary><div class="ui-menu-content"><button data-cloud-action="copy" data-cloud-needs-selection>복사</button><button data-cloud-action="move" data-cloud-needs-selection>이동</button><button data-cloud-action="rename" data-cloud-needs-selection>이름 변경</button><button data-cloud-action="clipboard" data-cloud-needs-selection>복사 대기</button><button data-cloud-action="cut" data-cloud-needs-selection>잘라내기</button><button data-cloud-action="paste" data-cloud-paste disabled>붙여넣기</button></div></details></span><span data-cloud-trash-only hidden><button data-cloud-action="restore" data-cloud-needs-selection>복원</button><details class="ui-menu cloud-action-menu"><summary aria-label="더 많은 휴지통 작업">더 보기</summary><div class="ui-menu-content"><button data-cloud-action="purge" data-cloud-needs-selection class="danger">영구 삭제</button><button data-cloud-action="empty" class="danger">휴지통 비우기</button></div></details></span></div><div class="cloud-toolbar cloud-view-options"><label>정렬<select data-cloud-sort><option value="name">이름</option><option value="modified">최근 수정</option><option value="size">크기</option></select></label><label>보기<select data-cloud-view><option value="list">목록</option><option value="grid">격자</option></select></label><span class="section-hint">파일을 열면 에디터로 이동 · 파일을 끌어 놓아 업로드</span></div><div data-cloud-upload-state hidden><progress data-cloud-progress max="100" value="0"></progress><button data-cloud-action="cancel">업로드 취소</button></div><p data-cloud-status role="status"></p><div data-cloud-items class="cloud-items" data-layout="list"></div><div class="cloud-toolbar cloud-pagination"><button data-cloud-action="previous">이전</button><span data-cloud-page></span><button data-cloud-action="next">다음</button></div></section>`;
   $('.cloud-sidebar h2').innerHTML=window.WorkspaceUI.icon('files')+'<span>드라이브</span>';
   const header=$('.cloud-header');
   header.insertAdjacentHTML('afterbegin',`<button type="button" class="ui-drawer-trigger" data-drawer-target=".cloud-sidebar" data-drawer-title="드라이브" aria-label="드라이브 열기" aria-expanded="false" aria-haspopup="dialog">${window.WorkspaceUI.icon('menu')}</button>`);
   header.insertAdjacentHTML('beforeend',`<button type="button" class="cloud-search-trigger" data-cloud-action="search-toggle" aria-label="파일 검색 열기" aria-expanded="false">${window.WorkspaceUI.icon('search')}</button><button type="button" class="cloud-select-trigger" data-cloud-action="selection-toggle" aria-label="파일 선택 작업 열기" aria-expanded="false">${window.WorkspaceUI.icon('select')}</button><button type="button" class="cloud-create-trigger" data-cloud-action="create-toggle" data-cloud-normal aria-label="새 항목 만들기" aria-haspopup="dialog">${window.WorkspaceUI.icon('plus')}</button>`);
   const selectedCount=document.createElement('span');
   selectedCount.dataset.cloudSelectedCount='';
   selectedCount.className='cloud-selected-count';
   $('.cloud-selection').insertBefore(selectedCount,$('.cloud-selection [data-cloud-normal]'));
   $('.cloud-view-options').prepend($('[data-cloud-count]'));
   $('[data-cloud-view]').closest('label').classList.add('cloud-view-select');
   $('.cloud-view-options').append($('[data-cloud-action="reload"]'));
   const viewToggle=document.createElement('button');
   viewToggle.type='button';viewToggle.className='cloud-view-trigger';viewToggle.dataset.cloudAction='view-toggle';
   viewToggle.setAttribute('aria-label','격자 보기');viewToggle.title='격자 보기';
   viewToggle.innerHTML=window.WorkspaceUI.icon('apps');
   $('.cloud-view-options').insertBefore(viewToggle,$('[data-cloud-action="reload"]'));
   $('.cloud-view-options').insertBefore($('[data-cloud-action="selection-toggle"]'),$('[data-cloud-action="reload"]'));
   $('.cloud-selection [data-cloud-normal] .ui-menu-content').append($('[data-cloud-action="delete"]'));
   for(const [action,label] of Object.entries({upload:'파일 업로드','upload-folder':'폴더 업로드',folder:'새 폴더',file:'새 파일',all:'페이지 전체 선택',download:'선택 파일 다운로드',delete:'선택 파일 삭제',restore:'선택 파일 복원'})){
    const control=$(`[data-cloud-action="${action}"]`);control.setAttribute('aria-label',label);control.title=label;
   }
   for(const [action,symbol] of Object.entries({reload:'refresh',upload:'upload','upload-folder':'uploadFolder',folder:'folderPlus',file:'filePlus',all:'select',download:'download',delete:'trash',restore:'restore'})){
    const control=$(`[data-cloud-action="${action}"]`);
    control.innerHTML=window.WorkspaceUI.icon(symbol)+`<span class="cloud-action-label">${action==='reload'?'새로고침':control.textContent}</span>`;
   }
   root.querySelectorAll('.cloud-action-menu>summary').forEach(control=>{
    control.innerHTML=window.WorkspaceUI.icon('more')+'<span class="cloud-action-label">더 보기</span>';
   });
   $('[data-cloud-overwrite]').setAttribute('aria-label','같은 이름 업로드 덮어쓰기');
   root.addEventListener('click',event=>Promise.resolve(click(event)).catch(error=>tell(error.message)));
   root.addEventListener('change',event=>{const id=event.target.dataset.cloudSelect;if(id!==undefined){if(event.target.checked)selected.add(id);else selected.delete(id);event.target.closest('.cloud-item').classList.toggle('selected',event.target.checked);buttons();}});
   $('[data-cloud-search]').addEventListener('submit',event=>{event.preventDefault();if(!busy)refresh();});
   $('[data-cloud-sort]').onchange=draw;$('[data-cloud-view]').onchange=event=>{$('[data-cloud-items]').dataset.layout=event.target.value;syncViewToggle();};
   for(const selector of ['[data-cloud-files]','[data-cloud-folders]'])$(selector).onchange=event=>{upload([...event.target.files]);event.target.value='';};
   root.addEventListener('dragover',event=>{if(event.dataTransfer?.types.includes('Files')){event.preventDefault();root.classList.add('cloud-drag');}});
   root.addEventListener('dragleave',event=>{if(!root.contains(event.relatedTarget))root.classList.remove('cloud-drag');});
   root.addEventListener('drop',event=>{event.preventDefault();root.classList.remove('cloud-drag');const items=[...(event.dataTransfer?.items||[])];if(items.some(item=>item.webkitGetAsEntry?.()?.isDirectory)){tell('폴더는 폴더 업로드 버튼으로 선택하세요.');return;}upload([...(event.dataTransfer?.files||[])]);});
   document.addEventListener('keydown',event=>{
    if(fileEditor||!root.classList.contains('active')||document.querySelector('dialog[open]')||event.target.closest('input,textarea,select,[contenteditable="true"]'))return;
    let action=null;
    if(event.ctrlKey||event.metaKey)action=({a:'all',c:'clipboard',x:'cut',v:'paste'})[event.key.toLowerCase()];
    else if(event.key==='Delete')action=trash?'purge':'delete';else if(event.key==='F2'&&!trash)action='rename';
    if(action){const button=root.querySelector('[data-cloud-action="'+action+'"]');if(button&&!button.disabled){event.preventDefault();button.click();}}
   });
   window.addEventListener('beforeunload',event=>{if(busy||fileEditor?.saving||editorDirty()){event.preventDefault();event.returnValue='';}});
  },
  refresh(){if(root&&!busy&&!fileEditor)return refresh({quiet:true});},
  async open(view){if(root&&view==='cloud'&&!busy&&!fileEditor)await refresh();}
 };
})();
