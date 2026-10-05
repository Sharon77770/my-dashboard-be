'use strict';
window.WorkspaceDeviceCodex = (() => {
  let ui, view, chat, devices=[], selected=null, pending=null, busy=false, prepared=false, job=null, opening=0;
  const $=selector=>view.querySelector(selector);
  const project=()=>selected?{deviceId:selected.id,root:selected.root}:null;
  const path=()=>'/devices/'+encodeURIComponent(selected.id)+'/codex/jobs';
  const status=text=>{$('[data-dc-status]').textContent=text;};
  function setBusy(value){
    busy=value;
    view.querySelectorAll('[data-dc-controls] input,[data-dc-controls] select,[data-dc-controls] button,[data-dc-prompt],.device-codex-panel button,.device-codex-panel select,.device-codex-panel textarea').forEach(element=>element.disabled=value||!selected);
    $('[data-dc-device]').disabled=value||!devices.length;
    $('[data-dc-cancel]').hidden=!value;
  }
  function clearAuth(){const box=$('[data-dc-auth]');box.replaceChildren();box.hidden=true;}
  function resetChat(){
    chat.reset();
    const mode=$('[data-cx-id="studio-codex-mode"]');mode.value='read-only';mode.dispatchEvent(new Event('change'));
  }
  function authEvent(event){
    if(event.event)status(event.event);
    if(!event.url&&!event.code)return;
    const box=$('[data-dc-auth]');box.hidden=false;
    if(event.url){try{const url=new URL(event.url);if(url.protocol==='https:'){let link=box.querySelector('a');if(!link){link=document.createElement('a');link.target='_blank';link.rel='noopener noreferrer';link.textContent='인증 페이지 열기';box.append(link);}link.href=url.href;}}catch{}}
    if(event.code){let code=box.querySelector('code');if(!code){code=document.createElement('code');const copy=document.createElement('button');copy.type='button';copy.textContent='코드 복사';copy.onclick=()=>guard(async()=>{if(navigator.clipboard&&window.isSecureContext)await navigator.clipboard.writeText(code.textContent);else{const field=document.createElement('textarea');field.value=code.textContent;box.append(field);field.select();const ok=document.execCommand('copy');field.remove();if(!ok)throw Error('인증 코드를 선택해 복사하세요.');}});box.append(code,copy);}code.textContent=event.code;}
  }
  async function task(action,args={}){
    if(busy||!selected)throw Error('장비를 선택하고 진행 중인 작업을 마쳐 주세요.');
    setBusy(true);clearAuth();const endpoint=path();const seen=new Set();
    try{
      let result=await ui.api(endpoint,'POST',{root:selected.root,action,args});job=result.id;
      for(;;){
        for(const event of result.events||[]){const key=JSON.stringify(event);if(!seen.has(key)){seen.add(key);authEvent(event);}}
        if(result.state!=='RUNNING')break;
        await (window.WorkspaceRealtime?.waitForJob(job,500)||new Promise(resolve=>setTimeout(resolve,500)));result=await ui.api(endpoint+'/'+job);
      }
      if(result.state!=='SUCCEEDED')throw Error(result.error||'작업이 취소되었습니다.');
      return result.result||{};
    }finally{job=null;setBusy(false);clearAuth();}
  }
  async function prepare(refresh=false){
    if(prepared&&!refresh)return;
    status('선택한 장비의 CLI를 준비하고 있습니다…');
    const result=await task('setup',{refresh});prepared=true;
    status((result.codex||'Codex 준비됨')+' · '+selected.host+' · '+selected.root);
  }
  function choose(id){
    if(busy)return;
    const device=devices.find(item=>item.id===id);
    selected=device?{...device,root:device.rootPath||'/'}:null;
    prepared=false;resetChat();
    $('[data-dc-device]').value=selected?.id||'';
    $('[data-dc-root]').value=selected?.root||'';
    status(selected?selected.name+' · '+selected.host+' · SSH 계정 권한으로 실행':'먼저 인프라에서 SSH 장비를 등록하세요.');
    clearAuth();setBusy(false);
  }
  async function connect(refresh=false){
    const root=$('[data-dc-root]').value.trim();
    if(!selected||!root.startsWith('/'))throw Error('SSH 장비와 절대 경로의 작업 폴더를 선택하세요.');
    if(selected.root!==root){selected={...selected,root};prepared=false;resetChat();}
    await prepare(refresh);await chat.load(true);
  }
  async function guard(work){try{await work();}catch(error){status(error.message);ui.toast(error.message);}}
  function init(helpers){
    ui=helpers;view=document.getElementById('device-codex');if(!view)return;
    view.innerHTML=`<div class="page-head"><div><span class="eyebrow">SSH DEVICE</span><h1>장비 Codex</h1></div><button data-dc-cancel hidden>작업 취소</button></div>
      <div class="device-codex-controls" data-dc-controls><label>장비<select data-dc-device aria-label="Codex 실행 장비"></select></label><label>작업 폴더<input data-dc-root aria-label="장비 작업 폴더" placeholder="/home/user/project" maxlength="4096"></label><button class="primary" data-dc-connect>연결 · CLI 준비</button><details class="ui-menu"><summary>장비 설정</summary><div class="ui-menu-content"><button data-dc-update>CLI 업데이트</button><button data-dc-github>GitHub 로그인</button><button data-dc-github-status>GitHub 인증 확인</button></div></details></div>
      <p class="section-hint">선택한 장비에서 상태 확인, 로그 수집, 배포, 파일 수정과 Git 작업을 수행합니다. 작업 폴더를 적용하려면 연결을 누르세요.</p><p data-dc-status role="status" aria-live="polite"></p><div data-dc-auth hidden></div>
      <div class="device-codex-suggestions"><button data-dc-prompt="현재 장비의 CPU, 메모리, 디스크와 실행 중인 서비스 상태를 확인하고 문제를 설명하라.">장비 상태</button><button data-dc-prompt="1557 포트의 서비스를 찾아 지난주부터 현재까지 실제 실행 로그와 HTTP 5xx 오류를 수집하고 원인을 설명하라.">서비스 오류 로그</button><button data-dc-prompt="작업 폴더의 배포 구성을 확인하고 현재 변경 사항을 배포한 뒤 서비스 상태를 검증하라.">배포</button><button data-dc-prompt="작업 폴더의 Git 상태, 변경 내용과 최근 커밋을 확인하라.">Git 확인</button></div><section class="device-codex-panel" data-cx-id="studio-codex" aria-label="장비 Codex 대화"></section>`;
    const panel=$('.device-codex-panel');
    chat=window.StudioCodex(panel,{
      ...ui,project,jobsPath:path,idPrefix:'device-',storagePrefix:'device-codex:',busy:()=>busy,setBusy,
      welcomeTitle:'장비에 어떤 작업이 필요한가요?',welcomeText:'실제 장비의 서비스 상태, 로그, 배포와 Git 작업을 요청하세요.',
      job:value=>{job=value;},prepare,dirty:()=>false,context:()=>null,publish:()=>{},confirm:ui.confirm,
      auth:value=>{panel.querySelector('[data-cx-id="studio-auth-cta"]').hidden=value!==false;panel.querySelectorAll('[data-studio="codex-logout"]').forEach(element=>element.hidden=value!==true);}
    });
    panel.querySelectorAll('[data-cx="file"],[data-cx="selection"]').forEach(element=>element.hidden=true);
    $('[data-dc-device]').addEventListener('change',event=>choose(event.target.value));
    view.addEventListener('click',event=>guard(async()=>{
      const button=event.target.closest('button');if(!button||button.disabled)return;
      if(button.hasAttribute('data-dc-connect'))await connect();
      if(button.hasAttribute('data-dc-update'))await connect(true);
      if(button.hasAttribute('data-dc-cancel')&&job){await ui.api(path()+'/'+job,'DELETE');clearAuth();}
      if(button.hasAttribute('data-dc-github')){await prepare();await task('github-login');status('이 장비의 GitHub 로그인 완료');}
      if(button.hasAttribute('data-dc-github-status')){const result=await task('github-status');status(result.authenticated?'이 장비의 GitHub 인증됨':'이 장비의 GitHub 로그인 필요');}
      if(button.dataset.studio==='codex-login'){await prepare();await task('codex-login');await chat.load(true);}
      if(button.dataset.studio==='codex-logout'){await task('codex-logout');resetChat();status('이 장비의 Codex 로그아웃 완료');}
      if(button.dataset.dcPrompt){const input=panel.querySelector('[data-cx-id="studio-prompt"]');input.value=button.dataset.dcPrompt;input.focus();}
    }));
    setBusy(false);
  }
  async function open(id){
    if(id!=='device-codex'||!view||busy)return;
    const version=++opening,workspace=await ui.api('/workspace');if(busy||version!==opening)return;
    devices=(workspace.devices||[]).filter(device=>device.id!=='local');
    $('[data-dc-device]').innerHTML=devices.length?devices.map(device=>`<option value="${ui.escape(device.id)}">${ui.escape(device.name)} · ${ui.escape(device.host)}</option>`).join(''):'<option value="">SSH 장비 없음</option>';
    const target=pending||selected?.id||devices[0]?.id;pending=null;
    if(target!==selected?.id||!devices.some(device=>device.id===target))choose(target);
    else{$('[data-dc-device]').value=target;setBusy(false);}
  }
  return {init,open,select:id=>{pending=id;}};
})();
