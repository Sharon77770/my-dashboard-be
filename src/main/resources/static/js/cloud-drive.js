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
 const tell=text=>{$('[data-cloud-status]').textContent=text;};
 function buttons(){
  root.querySelectorAll('[data-cloud-needs-selection]').forEach(button=>button.disabled=busy||selected.size===0);
  $('[data-cloud-paste]').disabled=busy||trash||!clipboard;
  $('[data-cloud-count]').textContent=selected.size?selected.size+'개 선택':entries.length+'개 항목';
  root.querySelectorAll('[data-cloud-normal]').forEach(element=>element.hidden=trash);
  root.querySelectorAll('[data-cloud-trash-only]').forEach(element=>element.hidden=!trash);
  root.querySelectorAll('[data-cloud-write]').forEach(element=>element.disabled=busy);
 }
 function draw(){
  const query=$('[data-cloud-filter]').value.toLowerCase(),sort=$('[data-cloud-sort]').value;
  const visible=entries.filter(entry=>!trash||entry.path.toLowerCase().includes(query)).sort((a,b)=>{
   if(a.directory!==b.directory)return a.directory?-1:1;
   if(sort==='modified')return (b.modified||b.deletedAt)-(a.modified||a.deletedAt);
   if(sort==='size')return (b.size||0)-(a.size||0);
   return name(a.path).localeCompare(name(b.path),'ko',{numeric:true});
  });
  $('[data-cloud-location]').textContent=trash?'휴지통':path;
  const crumbs=[{path:'/',label:'내 드라이브'}];let cursor='';for(const part of path.split('/').filter(Boolean)){cursor+='/'+part;crumbs.push({path:cursor,label:part});}
  $('[data-cloud-crumbs]').innerHTML=trash?'휴지통':crumbs.map(part=>`<button data-cloud-path="${esc(part.path)}">${esc(part.label)}</button>`).join('<span aria-hidden="true">/</span>');
  page=Math.min(page,Math.max(0,Math.ceil(visible.length/100)-1));
  $('[data-cloud-page]').textContent=(page+1)+' / '+Math.max(1,Math.ceil(visible.length/100))+' 페이지';
  $('[data-cloud-action=previous]').disabled=page===0;
  $('[data-cloud-action=next]').disabled=(page+1)*100>=visible.length;
  $('[data-cloud-items]').innerHTML=visible.slice(page*100,(page+1)*100).map(entry=>{
   const id=trash?entry.id:entry.path;
   return `<article class="cloud-item ${selected.has(id)?'selected':''}" data-cloud-item="${esc(id)}"><input type="checkbox" data-cloud-select="${esc(id)}" aria-label="${esc(name(entry.path))} 선택" ${selected.has(id)?'checked':''}><button class="cloud-item-open" data-cloud-open="${esc(id)}"><span class="cloud-icon" aria-hidden="true">${entry.directory?'▰':'▤'}</span><span><b>${esc(name(entry.path))}</b><small>${trash?esc(entry.path):entry.directory?'폴더':esc(name(entry.path).split('.').pop().toUpperCase())+' 파일'}</small></span></button><span class="cloud-item-size">${entry.directory?'—':bytes(entry.size||0)}</span><time>${date(entry.modified||entry.deletedAt)}</time><button class="ghost" data-cloud-info="${esc(id)}" aria-label="${esc(name(entry.path))} 상세 정보">ⓘ</button></article>`;
  }).join('')||'<p class="empty-state">'+(trash?'휴지통이 비어 있습니다.':'폴더가 비어 있거나 검색 결과가 없습니다. 파일을 끌어 놓아 업로드하세요.')+'</p>';
  buttons();
 }
 async function refresh(){
  const version=++generation;page=0;selected.clear();tell('불러오는 중…');
  try{
   const result=await ui.api(trash?'/cloud/trash':`/cloud?path=${encodeURIComponent(path)}&query=${encodeURIComponent($('[data-cloud-filter]').value)}`);
   if(version!==generation)return;
   entries=trash?result:result.entries;
   if(!trash)$('[data-cloud-space]').textContent='서버 저장 공간: '+bytes(result.usableSpace)+' 여유 / '+bytes(result.totalSpace);
   draw();tell(result.truncated?'처리 한도에 도달했습니다. 더 작은 폴더에서 검색하세요.':'');
  }catch(error){if(version===generation)tell(error.message);}
 }
 async function navigate(target){if(busy){tell('진행 중인 작업이 끝난 뒤 이동하세요.');return;}trash=false;path=target;$('[data-cloud-filter]').value='';await refresh();}
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
 async function preview(value){
  const entry=entries.find(item=>item.path===value);if(!entry)return;
  if(entry.directory){await navigate(value);return;}
  if(/\.(png|jpe?g|gif|webp)$/i.test(value)&&entry.size<=20*1024*1024){
   const response=await fetch('/api/v1/cloud/content?path='+encodeURIComponent(value));if(!response.ok)throw new Error('이미지를 불러오지 못했습니다.');
   const url=URL.createObjectURL(await response.blob());
   ui.editor(name(value),`<img class="cloud-preview-image" src="${esc(url)}" alt="${esc(name(value))}">`,async()=>{},'닫기');
   document.getElementById('editor-dialog').addEventListener('close',()=>URL.revokeObjectURL(url),{once:true});return;
  }
  try{
   const text=await ui.api('/cloud/preview?path='+encodeURIComponent(value));
   ui.editor(name(value),`<label>UTF-8 텍스트<textarea class="cloud-text-editor" name="content" spellcheck="false">${esc(text.content)}</textarea></label><p class="section-hint">저장은 서버 파일을 수정합니다. 다른 작업의 변경이 있으면 덮어쓰지 않습니다.</p>`,async form=>{
    await ui.api('/cloud/text','PUT',{path:value,content:form.get('content'),revision:text.revision});await refresh();
   });
  }catch(error){ui.editor(name(value),`<p>${esc(error.message)}</p><a href="/api/v1/cloud/content?path=${encodeURIComponent(value)}" download>파일 다운로드</a>`,async()=>{},'닫기');}
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
  if(action==='cancel'){cancelUpload=true;uploadRequest?.abort();return;}
  if(busy){tell('현재 작업이 끝날 때까지 기다려 주세요.');return;}
  if(action==='previous'||action==='next'){page+=action==='next'?1:-1;draw();return;}
  if(action==='nas')return window.WorkspaceNas.open();
  if(action==='home')return navigate('/');
  if(action==='trash'){trash=true;$('[data-cloud-filter]').value='';return refresh();}
  if(action==='reload')return refresh();
  if(action==='folder'||action==='file')return create(action==='folder');
  if(action==='upload'||action==='upload-folder'){$(action==='upload'?'[data-cloud-files]':'[data-cloud-folders]').click();return;}
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
   root.innerHTML=`<aside class="cloud-sidebar"><h2>☁ 드라이브</h2><button data-cloud-action="home">내 드라이브</button><button data-cloud-action="trash">휴지통</button><button data-cloud-action="nas">NAS 연결</button><p data-cloud-space class="section-hint"></p></aside><section class="cloud-main"><div class="cloud-header"><h1 data-cloud-location>내 드라이브</h1><form data-cloud-search><input data-cloud-filter placeholder="이 폴더와 하위 폴더 검색" aria-label="파일 검색" maxlength="200"><button>검색</button></form><button data-cloud-action="reload" aria-label="새로고침">↻</button></div><nav data-cloud-crumbs aria-label="폴더 경로"></nav><div class="cloud-toolbar" data-cloud-normal><button class="primary" data-cloud-action="upload" data-cloud-write>파일 업로드</button><button data-cloud-action="upload-folder" data-cloud-write>폴더 업로드</button><button data-cloud-action="folder" data-cloud-write>새 폴더</button><button data-cloud-action="file" data-cloud-write>새 파일</button><label><input type="checkbox" data-cloud-overwrite>같은 이름 업로드 덮어쓰기</label><input type="file" multiple data-cloud-files hidden><input type="file" webkitdirectory multiple data-cloud-folders hidden></div><div class="cloud-toolbar cloud-selection"><button data-cloud-action="all">페이지 전체 선택</button><span data-cloud-count></span><span data-cloud-normal><button data-cloud-action="download" data-cloud-needs-selection>다운로드</button><button data-cloud-action="copy" data-cloud-needs-selection>복사</button><button data-cloud-action="move" data-cloud-needs-selection>이동</button><button data-cloud-action="rename" data-cloud-needs-selection>이름 변경</button><button data-cloud-action="delete" data-cloud-needs-selection>삭제</button><details class="ui-menu"><summary aria-label="클립보드 작업">⋯</summary><div class="ui-menu-content"><button data-cloud-action="clipboard" data-cloud-needs-selection>복사 대기</button><button data-cloud-action="cut" data-cloud-needs-selection>잘라내기</button></div></details><button data-cloud-action="paste" data-cloud-paste disabled>붙여넣기</button></span><span data-cloud-trash-only hidden><button data-cloud-action="restore" data-cloud-needs-selection>복원</button><button data-cloud-action="purge" data-cloud-needs-selection class="danger">영구 삭제</button><button data-cloud-action="empty" class="danger">휴지통 비우기</button></span></div><div class="cloud-toolbar"><label>정렬<select data-cloud-sort><option value="name">이름</option><option value="modified">최근 수정</option><option value="size">크기</option></select></label><label>보기<select data-cloud-view><option value="list">목록</option><option value="grid">격자</option></select></label><span class="section-hint">파일을 열면 미리보기 · 파일을 끌어 놓아 업로드</span></div><div data-cloud-upload-state hidden><progress data-cloud-progress max="100" value="0"></progress><button data-cloud-action="cancel">업로드 취소</button></div><p data-cloud-status role="status"></p><div data-cloud-items class="cloud-items" data-layout="list"></div><div class="cloud-toolbar"><button data-cloud-action="previous">이전</button><span data-cloud-page></span><button data-cloud-action="next">다음</button></div></section>`;
   root.addEventListener('click',event=>Promise.resolve(click(event)).catch(error=>tell(error.message)));
   root.addEventListener('change',event=>{const id=event.target.dataset.cloudSelect;if(id!==undefined){if(event.target.checked)selected.add(id);else selected.delete(id);event.target.closest('.cloud-item').classList.toggle('selected',event.target.checked);buttons();}});
   $('[data-cloud-search]').addEventListener('submit',event=>{event.preventDefault();if(!busy)refresh();});
   $('[data-cloud-sort]').onchange=draw;$('[data-cloud-view]').onchange=event=>{$('[data-cloud-items]').dataset.layout=event.target.value;};
   for(const selector of ['[data-cloud-files]','[data-cloud-folders]'])$(selector).onchange=event=>{upload([...event.target.files]);event.target.value='';};
   root.addEventListener('dragover',event=>{if(event.dataTransfer?.types.includes('Files')){event.preventDefault();root.classList.add('cloud-drag');}});
   root.addEventListener('dragleave',event=>{if(!root.contains(event.relatedTarget))root.classList.remove('cloud-drag');});
   root.addEventListener('drop',event=>{event.preventDefault();root.classList.remove('cloud-drag');const items=[...(event.dataTransfer?.items||[])];if(items.some(item=>item.webkitGetAsEntry?.()?.isDirectory)){tell('폴더는 폴더 업로드 버튼으로 선택하세요.');return;}upload([...(event.dataTransfer?.files||[])]);});
   document.addEventListener('keydown',event=>{
    if(!root.classList.contains('active')||document.querySelector('dialog[open]')||event.target.closest('input,textarea,select,[contenteditable="true"]'))return;
    let action=null;
    if(event.ctrlKey||event.metaKey)action=({a:'all',c:'clipboard',x:'cut',v:'paste'})[event.key.toLowerCase()];
    else if(event.key==='Delete')action=trash?'purge':'delete';else if(event.key==='F2'&&!trash)action='rename';
    if(action){const button=root.querySelector('[data-cloud-action="'+action+'"]');if(button&&!button.disabled){event.preventDefault();button.click();}}
   });
   window.addEventListener('beforeunload',event=>{if(busy){event.preventDefault();event.returnValue='';}});
  },
  async open(view){if(root&&view==='cloud'&&!busy)await refresh();}
 };
})();
