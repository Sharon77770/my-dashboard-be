'use strict';
window.WorkspaceTailscale=(()=>{
 let ui,dialog,timer,busy=false;
 const $=selector=>dialog.querySelector(selector);
 function render(state){
  const names={Running:'연결됨',NeedsLogin:'로그인 필요',NeedsMachineAuth:'관리자 승인 대기',Stopped:'연결 중지됨',Starting:'연결 준비 중'};
  $('[data-ts-status]').textContent=names[state.state]||state.state;
  $('[data-ts-host]').textContent=[state.hostname,...(state.ips||[])].filter(Boolean).join(' · ')||'연결된 주소가 없습니다.';
  $('[data-ts-message]').textContent=state.error||(state.pending?'로그인 승인을 기다리는 중입니다.':'');
  const link=$('[data-ts-link]');link.hidden=true;link.removeAttribute('href');
  if(/^https:\/\/login\.tailscale\.com\/a\/[A-Za-z0-9]+$/.test(state.loginUrl||'')){link.href=state.loginUrl;link.hidden=false;}
  $('[data-ts-login]').disabled=busy||state.state==='Running'||state.pending;
  $('[data-ts-logout]').disabled=busy;
 }
 async function refresh(){
  clearTimeout(timer);if(!dialog.open)return;
  try{render(await ui.api('/tailscale'));}catch(error){$('[data-ts-message]').textContent=error.message;}
  if(dialog.open)timer=setTimeout(refresh,3000);
 }
 async function action(method){
  if(busy)return;busy=true;clearTimeout(timer);$('[data-ts-login]').disabled=true;$('[data-ts-logout]').disabled=true;
  try{render(await ui.api('/tailscale/login',method));}catch(error){$('[data-ts-message]').textContent=error.message;}
  finally{busy=false;await refresh();}
 }
 return {
  init(helpers){ui=helpers;dialog=document.createElement('dialog');dialog.className='editor-dialog';dialog.setAttribute('aria-label','Tailscale 설정');
   dialog.innerHTML='<header class="panel-head"><h2>Tailscale 설정</h2><button data-ts-close aria-label="닫기">×</button></header><div style="padding:16px"><h3 data-ts-status role="status">연결 확인 중…</h3><p data-ts-host></p><p class="section-hint">대시보드 서버를 Tailscale에 연결합니다. 로그인 시작 후 공식 인증 화면에서 계정을 승인하세요.</p><p data-ts-message role="status"></p><p><a data-ts-link target="_blank" rel="noopener noreferrer" hidden>Tailscale 로그인 화면 열기 ↗</a></p><div class="actions"><button class="primary" data-ts-login>로그인 시작 / 연결</button><button data-ts-refresh>새로고침</button><button class="danger" data-ts-logout>로그아웃</button></div><p class="section-hint">로그아웃하면 Tailscale을 사용하는 장비 연결이 끊어질 수 있습니다. 대시보드 로그인에는 영향을 주지 않습니다.</p></div>';
   document.body.append(dialog);$('[data-ts-close]').onclick=()=>dialog.close();dialog.addEventListener('close',()=>clearTimeout(timer));
   $('[data-ts-refresh]').onclick=refresh;$('[data-ts-login]').onclick=()=>action('POST');
   $('[data-ts-logout]').onclick=()=>ui.confirmAction('Tailscale 로그아웃','Tailscale 장비 연결이 끊어질 수 있습니다. 로그아웃하시겠습니까?',()=>action('DELETE'));
  },
  open(){dialog.showModal();refresh();}
 };
})();
