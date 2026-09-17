'use strict';
/** Notebook UI: navigation and drafts stay local; all persistence uses the authenticated notes API. */
window.WorkspaceNotes=(()=>{
 let ui,root,entries=[],current=null,editor=null,folder=null,timer=null,saving=null,version=0,savedVersion=0,loading=false;
 const expanded=new Set(),$=selector=>root.querySelector(selector),esc=value=>ui.escape(value);
 const dirty=()=>version!==savedVersion;
 const tell=(message,error=false)=>{const status=$('[data-notes-status]');status.textContent=message;status.classList.toggle('form-error',error);};
 const pathOf=entry=>{const names=[],seen=new Set();while(entry&&!seen.has(entry.id)){seen.add(entry.id);names.unshift(entry.title);entry=entries.find(item=>item.id===entry.parentId);}return names.join(' / ');};
 const setEntry=entry=>{entries=entries.map(item=>item.id===entry.id?entry:item);if(current?.id===entry.id)current=entry;drawTree();};
 function drawTree(){
  const query=$('[data-notes-search]').value.trim().toLowerCase();
  const branch=(parent,depth)=>entries.filter(item=>item.parentId===parent).map(item=>{
   const isFolder=item.kind==='FOLDER',open=expanded.has(item.id),active=current?.id===item.id||(!current&&folder===item.id);
   return `<div class="notes-tree-row ${active?'active':''}" style="--notes-depth:${depth}"><button class="notes-toggle" data-note-toggle="${item.id}" aria-label="${esc(item.title)} ${open?'접기':'펼치기'}" ${isFolder?'aria-expanded="'+open+'"':'disabled'}>${isFolder?(open?'▾':'▸'):''}</button><button class="notes-tree-open" data-note-open="${item.id}" ${active?'aria-current="page"':''}><span aria-hidden="true">${esc(item.icon||(isFolder?'📁':'📄'))}</span><span>${esc(item.title)}</span></button><button class="notes-tree-menu" data-note-menu="${item.id}" aria-label="${esc(item.title)} 설정">⋯</button></div>${isFolder&&open?branch(item.id,depth+1):''}`;
  }).join('');
  $('[data-notes-tree]').innerHTML=query?entries.filter(item=>pathOf(item).toLowerCase().includes(query)).map(item=>`<div class="notes-tree-row"><button class="notes-tree-open" data-note-open="${item.id}"><span>${esc(item.icon||'📄')}</span><span>${esc(pathOf(item))}</span></button><button class="notes-tree-menu" data-note-menu="${item.id}" aria-label="${esc(item.title)} 설정">⋯</button></div>`).join('')||'<p class="section-hint">검색 결과가 없습니다.</p>':branch(null,0)||'<p class="notes-tree-empty">조직 폴더를 만들고<br>프로젝트 문서를 모아 보세요.</p>';
 }
 async function refreshTree(){entries=await ui.api('/notes');drawTree();}
 function markDirty(){version++;tell('저장하지 않은 변경');clearTimeout(timer);timer=setTimeout(()=>save().catch(()=>{}),1000);}
 /** Serialize saves; edits made during a request stay dirty and are saved with the returned revision. */
 async function save(){
  clearTimeout(timer);
  if(saving){await saving;if(dirty())return save();return;}
  if(!current||!editor||!dirty())return;
  const snapshot=version,entry=current,title=$('[data-notes-title]').value.trim(),blocks=editor.blocks();
  if(!title){tell('문서 제목을 입력하세요.',true);throw new Error('문서 제목을 입력하세요.');}
  tell('저장 중…');
  saving=(async()=>{
   try{
    let latest=entry;
    if(title!==entry.title){latest=await ui.api('/notes/'+entry.id,'PUT',{parentId:entry.parentId,title,icon:entry.icon,revision:latest.revision});setEntry(latest);}
    latest=await ui.api('/notes/'+entry.id+'/content','PUT',{blocks,revision:latest.revision});setEntry(latest);savedVersion=snapshot;
    tell(dirty()?'변경 사항을 저장하는 중…':'모든 변경 사항 저장됨');
   }catch(error){tell(error.message+' · 편집 내용은 유지됩니다. Markdown으로 내려받아 보관할 수 있습니다.',true);throw error;}
   finally{saving=null;}
  })();
  await saving;
  if(dirty())timer=setTimeout(()=>save().catch(()=>{}),500);
 }
 function destroyEditor(){editor?.destroy();editor=null;current=null;version=0;savedVersion=0;clearTimeout(timer);}
 function showFolder(){
  const parent=entries.find(item=>item.id===folder);$('[data-notes-path]').textContent=parent?pathOf(parent):'내 메모';
  $('[data-notes-document-actions]').hidden=true;
  $('[data-notes-page]').innerHTML=`<div class="notes-welcome"><span class="notes-page-icon">${esc(parent?.icon||'📓')}</span><h1>${esc(parent?.title||'프로젝트의 모든 기록')}</h1><p>조직과 프로젝트별로 문서를 정리하고, 블록으로 자유롭게 작성하세요.</p><div class="actions"><button class="primary" data-notes-action="new-document">＋ 새 문서</button><button data-notes-action="new-folder">＋ 하위 폴더</button></div><div class="notes-folder-list">${entries.filter(item=>item.parentId===folder).map(item=>`<button data-note-open="${item.id}"><span>${esc(item.icon||'📄')}</span><b>${esc(item.title)}</b><small>${item.kind==='FOLDER'?'폴더':'문서'}</small></button>`).join('')||'<p class="section-hint">아직 항목이 없습니다. 새 문서에서 템플릿을 선택해 보세요.</p>'}</div></div>`;
  tell('');drawTree();
 }
 async function openEntry(id){
  if(loading)return;
  if(current?.id===id)return;
  loading=true;
  try{
   await save();const entry=entries.find(item=>item.id===id);if(!entry)return;
   if(entry.kind==='FOLDER'){destroyEditor();folder=id;expanded.add(id);showFolder();return;}
   const document=await ui.api('/notes/'+id);
   destroyEditor();current=document.entry;folder=current.parentId;
   let ancestor=folder;while(ancestor){expanded.add(ancestor);ancestor=entries.find(item=>item.id===ancestor)?.parentId;}
   $('[data-notes-path]').textContent=pathOf(current);$('[data-notes-document-actions]').hidden=false;
   $('[data-notes-page]').innerHTML=`<div class="notes-document"><span class="notes-page-icon">${esc(current.icon||'📄')}</span><input class="notes-title" data-notes-title aria-label="문서 제목" maxlength="200" placeholder="제목 없음"><p class="notes-editor-hint">/ 로 블록 추가 · 블록 옆 핸들로 이동 · 텍스트를 선택해 서식 변경</p><div data-notes-editor></div></div>`;
   $('[data-notes-title]').value=current.title;$('[data-notes-title]').oninput=markDirty;
   editor=window.NotesBlockEditor.mount($('[data-notes-editor]'),{blocks:document.blocks,onChange:markDirty,upload:file=>uploadImage(id,file),onError:error=>tell(error.message,true)});
   tell('모든 변경 사항 저장됨');drawTree();root.classList.remove('notes-sidebar-open');
  }finally{loading=false;}
 }
 async function uploadImage(id,file){
  if(file.size>10*1024*1024)throw new Error('이미지는 10 MiB 이하여야 합니다.');
  const form=new FormData();form.append('file',file);
  const headers={};headers[document.querySelector('meta[name=csrf-header]').content]=document.querySelector('meta[name=csrf-token]').content;
  const response=await fetch('/api/v1/notes/'+id+'/images',{method:'POST',body:form,headers});
  if(!response.ok){let message='이미지를 업로드하지 못했습니다.';try{message=(await response.json()).message||message;}catch{}throw new Error(message);}
  return (await response.json()).url;
 }
 function folderOptions(selected,excluded){
  const allowed=item=>{let cursor=item;const seen=new Set();while(cursor&&!seen.has(cursor.id)){if(cursor.id===excluded)return false;seen.add(cursor.id);cursor=entries.find(entry=>entry.id===cursor.parentId);}return true;};
  return '<option value="">내 메모 (최상위)</option>'+entries.filter(item=>item.kind==='FOLDER'&&allowed(item)).map(item=>`<option value="${item.id}" ${item.id===selected?'selected':''}>${esc(pathOf(item))}</option>`).join('');
 }
 async function create(folderOnly){
  await save();
  const templates=window.WorkspaceNoteTemplates.all();
  ui.editor(folderOnly?'새 폴더':'새 문서 · 템플릿 선택',`<label>이름<input name="title" required maxlength="200" placeholder="${folderOnly?'조직명 또는 프로젝트명':'문서 제목'}"></label><label>저장할 폴더<select name="parentId">${folderOptions(folder)}</select></label>${folderOnly?'':`<fieldset class="notes-template-grid"><legend>시작 템플릿</legend>${templates.map((template,index)=>`<label class="notes-template"><input type="radio" name="template" value="${template.id}" ${index===0?'checked':''}><span>${template.icon} <b>${esc(template.name)}</b><small>${esc(template.description)}</small></span></label>`).join('')}</fieldset>`}`,async form=>{
   const template=folderOnly?{icon:'📁',blocks:[]}:window.WorkspaceNoteTemplates.create(String(form.get('template')));
   const parentId=String(form.get('parentId'))||null;
   const document=await ui.api('/notes','POST',{kind:folderOnly?'FOLDER':'DOCUMENT',parentId,title:String(form.get('title')),icon:template.icon,blocks:template.blocks});
   if(parentId)expanded.add(parentId);await refreshTree();await openEntry(document.entry.id);
  },'만들기');
 }
 async function settings(id){
  await save();const entry=entries.find(item=>item.id===id);if(!entry)return;
  ui.editor(entry.kind==='FOLDER'?'폴더 설정':'문서 설정',`<label>이름<input name="title" required maxlength="200" value="${esc(entry.title)}"></label><label>아이콘 (이모지)<input name="icon" maxlength="16" value="${esc(entry.icon)}"></label><label>이동할 폴더<select name="parentId">${folderOptions(entry.parentId,id)}</select></label><button type="button" class="danger" id="notes-delete-entry">${entry.kind==='FOLDER'?'빈 폴더':'문서'} 삭제</button>`,async form=>{
   await save();const latest=entries.find(item=>item.id===id);
   const updated=await ui.api('/notes/'+id,'PUT',{title:String(form.get('title')),icon:String(form.get('icon')),parentId:String(form.get('parentId'))||null,revision:latest.revision});
   setEntry(updated);if(current?.id===id){$('[data-notes-title]').value=updated.title;folder=updated.parentId;$('[data-notes-path]').textContent=pathOf(updated);$('[data-notes-page] .notes-page-icon').textContent=updated.icon||'📄';}else if(!current)showFolder();
  });
  document.getElementById('notes-delete-entry').onclick=()=>ui.confirmAction('삭제 확인',entry.title+'을(를) 영구 삭제합니다. 문서의 이미지도 삭제되며 복구할 수 없습니다.',async()=>{
   await save();const latest=entries.find(item=>item.id===id);
   await ui.api('/notes/'+id+'?revision='+latest.revision,'DELETE');
   document.getElementById('editor-dialog').close();if(current?.id===id){destroyEditor();folder=latest.parentId;}if(folder===id)folder=latest.parentId;
   await refreshTree();if(!current)showFolder();
  });
 }
 async function exportMarkdown(){
  if(!editor)return;const text=await editor.markdown();const url=URL.createObjectURL(new Blob([text],{type:'text/markdown;charset=utf-8'}));
  const link=document.createElement('a');link.href=url;link.download=($('[data-notes-title]').value||'문서')+'.md';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
 }
 async function action(value){
  if(value==='new-folder'||value==='new-document')return create(value==='new-folder');
  if(value==='save')return save();
  if(value==='sidebar'){root.classList.toggle('notes-sidebar-open');return;}
  if(value==='root'){await save();destroyEditor();folder=null;showFolder();return;}
  if(value==='settings'&&current)return settings(current.id);
  if(value==='export')return exportMarkdown();
  if(value==='import'){$('[data-notes-import]').click();return;}
  if(value==='reload'){
   if(dirty()&&!window.confirm('저장하지 않은 내용을 버리고 서버의 최신 문서를 열까요? 먼저 Markdown으로 보관할 수 있습니다.'))return;
   if(saving)await saving;
   const id=current?.id;destroyEditor();await refreshTree();if(id&&entries.some(item=>item.id===id))await openEntry(id);else {folder=null;showFolder();}
  }
 }
 return {
  init(helpers){
   ui=helpers;root=document.getElementById('notes');if(!root)return;
   root.innerHTML=`<aside class="notes-sidebar"><button class="notes-brand" data-notes-action="root">📓 내 메모</button><input data-notes-search aria-label="문서와 폴더 검색" placeholder="문서와 폴더 검색"><div class="notes-create"><button data-notes-action="new-document">＋ 새 문서</button><button data-notes-action="new-folder" aria-label="새 폴더">＋ 폴더</button></div><nav data-notes-tree aria-label="폴더와 문서"></nav><p class="notes-sidebar-footer">조직 → 프로젝트 → 문서</p></aside><section class="notes-main"><header class="notes-toolbar"><button data-notes-action="sidebar" class="notes-sidebar-toggle" aria-label="문서 목록 열기">☰</button><span data-notes-path>내 메모</span><div data-notes-document-actions hidden><button data-notes-action="save">저장</button><button data-notes-action="import">MD 가져오기</button><button data-notes-action="export">MD 내보내기</button><button data-notes-action="settings" aria-label="문서 설정">⋯</button></div><button data-notes-action="reload" aria-label="최신 문서 다시 열기">↻</button></header><p data-notes-status role="status"></p><div data-notes-page></div><input type="file" data-notes-import accept=".md,.markdown,text/markdown,text/plain" hidden></section>`;
   root.addEventListener('click',event=>{const button=event.target.closest('button');if(!button)return;
    let result;if(button.dataset.noteToggle){const id=button.dataset.noteToggle;expanded.has(id)?expanded.delete(id):expanded.add(id);drawTree();return;}
    if(button.dataset.noteOpen)result=openEntry(button.dataset.noteOpen);else if(button.dataset.noteMenu)result=settings(button.dataset.noteMenu);else if(button.dataset.notesAction)result=action(button.dataset.notesAction);
    Promise.resolve(result).catch(error=>tell(error.message,true));
   });
   $('[data-notes-search]').oninput=drawTree;
   $('[data-notes-import]').onchange=async event=>{const file=event.target.files[0];event.target.value='';if(!file||!editor)return;const target=editor;
    try{if(file.size>2*1024*1024)throw new Error('Markdown 파일은 2 MiB 이하여야 합니다.');if(!window.confirm('현재 본문을 가져온 Markdown 내용으로 바꿀까요?'))return;const text=await file.text();if(editor!==target)return;await target.importMarkdown(text);}catch(error){tell(error.message,true);}
   };
   window.addEventListener('beforeunload',event=>{if(dirty()||saving){event.preventDefault();event.returnValue='';}});
   root.addEventListener('keydown',event=>{if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='s'){event.preventDefault();save().catch(()=>{});}});
  },
  async open(view){if(!root||view!=='notes')return;await refreshTree();if(!current)showFolder();},
 };
})();
