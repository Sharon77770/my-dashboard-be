'use strict';
/** Native modal drawers retain panels inside their owning app, preserving events and form state. */
window.WorkspaceDrawers=(()=>{
 let active=null,disposed=false;
 const mobile=()=>window.matchMedia('(max-width:800px)').matches;
 function close(){if(active){const current=active;current.dialog.close();current.restore();}}
 function open(panel,trigger,{title='탐색',side='left'}={}){
  if(!panel||!mobile())return;
  close();
  const marker=document.createComment('drawer panel position');panel.before(marker);
  const dialog=document.createElement('dialog');dialog.className='ui-side-drawer';dialog.dataset.side=side;dialog.setAttribute('aria-label',title);
  const header=document.createElement('header');header.className='ui-drawer-head';
  const heading=document.createElement('strong');heading.textContent=title;
  const dismiss=document.createElement('button');dismiss.type='button';dismiss.textContent='×';dismiss.setAttribute('aria-label','사이드바 닫기');dismiss.onclick=()=>dialog.close();
  header.append(heading,dismiss);dialog.append(header);marker.parentNode.append(dialog);dialog.append(panel);
  const previousOverflow=document.body.style.overflow;
  active={dialog,panel,trigger};trigger?.setAttribute('aria-expanded','true');document.body.style.overflow='hidden';
  dialog.addEventListener('click',event=>{if(event.target!==dialog)return;const bounds=dialog.getBoundingClientRect();if(event.clientX<bounds.left||event.clientX>bounds.right||event.clientY<bounds.top||event.clientY>bounds.bottom)dialog.close();});
  let restored=false;
  const restore=()=>{
   if(restored)return;restored=true;
   marker.replaceWith(panel);dialog.remove();trigger?.setAttribute('aria-expanded','false');document.body.style.overflow=previousOverflow;
   if(active?.dialog===dialog)active=null;
   if(trigger?.isConnected)trigger.focus();
  };
  active.restore=restore;
  dialog.addEventListener('close',restore,{once:true});
  dialog.showModal();dismiss.focus();
 }
 function addTrigger(host,panelSelector,title){
  if(!host||host.querySelector('[data-drawer-target]'))return;
  const button=document.createElement('button');button.type='button';button.className='ui-drawer-trigger';button.textContent='☰';button.dataset.drawerTarget=panelSelector;button.dataset.drawerTitle=title;button.setAttribute('aria-label',title+' 열기');button.setAttribute('aria-expanded','false');button.setAttribute('aria-haspopup','dialog');host.prepend(button);
 }
 function scan(){
  if(disposed)return;
  addTrigger(document.querySelector('.cloud-header'),'.cloud-sidebar','드라이브');
  document.querySelectorAll('.file-layout').forEach(layout=>{if(layout.querySelector('aside'))addTrigger(layout,'.file-layout aside','파일 탐색');});
  document.querySelectorAll('[data-notes-action="sidebar"],[data-pane="explorer"],[data-pane="inspector"]').forEach(button=>{button.setAttribute('aria-haspopup','dialog');if(!button.hasAttribute('aria-expanded'))button.setAttribute('aria-expanded','false');});
 }
 document.addEventListener('click',event=>{
  if(!mobile())return;
  const trigger=event.target.closest('[data-notes-action="sidebar"],[data-pane],[data-drawer-target]');if(!trigger)return;
  const root=trigger.closest('.view,.runtime-pane')||document;
  let panel,title,side='left';
  if(trigger.matches('[data-notes-action="sidebar"]')){panel=root.querySelector('.notes-sidebar');title='문서와 폴더';}
  else if(trigger.dataset.pane){
   if(trigger.dataset.pane==='editor'){event.preventDefault();event.stopImmediatePropagation();close();return;}
   side=trigger.dataset.pane==='inspector'?'right':'left';panel=root.querySelector('.studio-'+trigger.dataset.pane);title=side==='right'?'Git · Codex':'프로젝트 파일';
  }else{panel=root.querySelector(trigger.dataset.drawerTarget);title=trigger.dataset.drawerTitle;}
  if(panel){event.preventDefault();event.stopImmediatePropagation();open(panel,trigger,{title,side});}
 },true);
 window.matchMedia('(max-width:800px)').addEventListener('change',close);
 let scheduled=false;
 const observer=new MutationObserver(()=>{if(scheduled)return;scheduled=true;queueMicrotask(()=>{scheduled=false;scan();});});
 window.addEventListener('pagehide',()=>{disposed=true;observer.disconnect();close();});
 window.addEventListener('pageshow',()=>{if(disposed){disposed=false;init();}});
 function init(){scan();observer.observe(document.body,{childList:true,subtree:true});}
 if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
 return {open,close};
})();
