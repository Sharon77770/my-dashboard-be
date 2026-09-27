'use strict';
(() => {
  const shell=document.querySelector('#assistant-float'),button=document.querySelector('#assistant-launcher'),windowPanel=document.querySelector('#assistant-window');
  const root=window.workspaceInitial?.devices?.find(device=>device.id==='local')?.rootPath;
  if(!shell||!button||!windowPanel||!root||!window.StudioCodex||!window.WorkspaceAssistantRuntime)return;
  let busy=false,ready=false,preparing=null,jobId=null,opened=false,drag=null;
  const host={
    escape:window.WorkspaceUI.escape,
    api:(path,method,body)=>window.WorkspaceAssistantRuntime.api(path.replace(/^\/studio\/jobs/, '/assistant/jobs'),method,body),
    project:()=>({deviceId:'local',root}),
    busy:()=>busy,
    setBusy:value=>{busy=value;button.toggleAttribute('aria-busy',value);},
    job:id=>{jobId=id;},
    dirty:()=>false,
    context:()=>null,
    editor:window.WorkspaceAssistantRuntime.editor,
    confirm:window.WorkspaceAssistantRuntime.confirm,
    toast:window.WorkspaceAssistantRuntime.toast,
    auth:value=>{document.querySelector('#assistant-account-status').textContent=value===null?'Codex 인증 상태 미확인':value?'서버 Codex 로그인됨':'Codex 로그인 필요';},
    publish:()=>{},
    async prepare(){
      if(ready)return;
      if(preparing)return preparing;
      preparing=(async()=>{
        document.querySelector('#assistant-account-status').textContent='서버 Codex 준비 중';
        const response=await host.api('/assistant/jobs','POST',{...host.project(),action:'setup',args:{}});jobId=response.id;
        let job=response;
        while(job.state==='RUNNING'){
          await new Promise(resolve=>setTimeout(resolve,500));
          job=await host.api('/studio/jobs/'+response.id);
        }
        if(job.state!=='SUCCEEDED')throw new Error(job.error||'서버 Codex를 준비하지 못했습니다.');
        ready=true;
      })().finally(()=>{preparing=null;});
      return preparing;
    }
  };
  const codex=window.StudioCodex(document.querySelector('#assistant-codex'),host);
  function placePanel(){
    if(windowPanel.hidden)return;
    const anchor=shell.getBoundingClientRect(),panel=windowPanel.getBoundingClientRect();
    const left=anchor.left>innerWidth/2?anchor.right-panel.width:anchor.left;
    const top=anchor.top>innerHeight/2?anchor.top-panel.height-10:anchor.bottom+10;
    windowPanel.style.left=Math.max(8,Math.min(innerWidth-panel.width-8,left))+'px';
    windowPanel.style.top=Math.max(36,Math.min(innerHeight-panel.height-8,top))+'px';
    windowPanel.style.right='auto';windowPanel.style.bottom='auto';
  }
  async function open(){
    if(opened){windowPanel.hidden=false;button.setAttribute('aria-expanded','true');placePanel();return;}
    opened=true;windowPanel.hidden=false;button.setAttribute('aria-expanded','true');placePanel();await codex.load();
  }
  function close(){windowPanel.hidden=true;button.setAttribute('aria-expanded','false');}
  button.addEventListener('click',()=>{if(drag?.moved)return; if(windowPanel.hidden)open().catch(error=>host.toast(error.message));else close();});
  document.querySelector('#assistant-close').addEventListener('click',close);
  window.addEventListener('resize',placePanel);
  document.addEventListener('keydown',event=>{if(event.key==='Escape'&&!windowPanel.hidden)close();});
  button.addEventListener('pointerdown',event=>{if(event.button!==0)return;drag={id:event.pointerId,x:event.clientX,y:event.clientY,left:shell.offsetLeft,top:shell.offsetTop,moved:false};button.setPointerCapture(event.pointerId);});
  button.addEventListener('pointermove',event=>{if(!drag||drag.id!==event.pointerId)return;const dx=event.clientX-drag.x,dy=event.clientY-drag.y;if(Math.abs(dx)+Math.abs(dy)>6)drag.moved=true;if(!drag.moved)return;const left=Math.max(8,Math.min(innerWidth-shell.offsetWidth-8,drag.left+dx)),top=Math.max(38,Math.min(innerHeight-shell.offsetHeight-52,drag.top+dy));shell.style.left=left+'px';shell.style.top=top+'px';shell.style.right='auto';shell.style.bottom='auto';try{localStorage.setItem('assistant-position-v1',JSON.stringify({left,top}));}catch{}});
  button.addEventListener('pointerup',()=>{if(drag)setTimeout(()=>{drag=null;},0);});
  try{const position=JSON.parse(localStorage.getItem('assistant-position-v1'));if(Number.isFinite(position?.left)&&Number.isFinite(position?.top)){shell.style.left=Math.max(8,Math.min(innerWidth-64,position.left))+'px';shell.style.top=Math.max(38,Math.min(innerHeight-64,position.top))+'px';shell.style.right='auto';shell.style.bottom='auto';}}catch{}
  let eventCursor=0;try{eventCursor=Number(sessionStorage.getItem('assistant-event-cursor')||0)||0;}catch{}
  async function pollEvents(){
    try{const events=await host.api('/assistant/events?after='+eventCursor);for(const item of events){eventCursor=Math.max(eventCursor,item.sequence);try{sessionStorage.setItem('assistant-event-cursor',String(eventCursor));}catch{}window.dispatchEvent(new CustomEvent('assistant:navigate',{detail:{route:item.route,applicationId:item.applicationId}}));}}catch{}
  }
  setInterval(()=>{if(!document.hidden)pollEvents();},1000);pollEvents();
})();
