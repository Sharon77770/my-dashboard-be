'use strict';
window.WorkspaceRemoteSetup = {
  open(id, ui) {
    ui.editor('원격 데스크톱 자동 연결', '<div data-desktop-setup><p>기존 VNC/RDP가 설정되어 있으면 먼저 연결합니다. 새 구성은 SSH 장비에 별도의 Linux 가상 데스크톱을 설치합니다. 실제 모니터 화면과는 다릅니다.</p><p>필요한 패키지를 설치하며 최대 12분이 걸릴 수 있습니다. SSH로만 연결하므로 VNC 방화벽 포트를 열 필요가 없습니다. Windows·macOS 및 설치 권한이 없는 환경은 필요한 조치를 안내합니다.</p><button type="button" data-setup-start>자동 구성 및 연결</button><p data-setup-status role="status" aria-live="polite"></p></div>', async()=>{}, '닫기');
    const root = document.querySelector('[data-desktop-setup]');
    const button = root.querySelector('[data-setup-start]');
    const status = root.querySelector('[data-setup-status]');
    const active = () => root.isConnected && root.closest('dialog').open;
    async function follow(result) {
      while (active()) {
        status.textContent = result.message;
        if (result.state === 'READY') {
          await ui.refresh();
          if (active()) { root.closest('dialog').close(); await ui.connect(); }
          return;
        }
        if (result.state !== 'RUNNING') return;
        await new Promise(resolve=>setTimeout(resolve,1500));
        if (!active()) return;
        result = await ui.api('/devices/'+encodeURIComponent(id)+'/remote-setup');
      }
    }
    button.onclick = async()=>{
      button.disabled = true;
      try { await follow(await ui.api('/devices/'+encodeURIComponent(id)+'/remote-setup','POST',{})); }
      catch (error) { if(active()) status.textContent = error.message; }
      finally { button.disabled = false; button.textContent = '다시 구성 및 연결'; }
    };
    ui.api('/devices/'+encodeURIComponent(id)+'/remote-setup').then(async result=>{
      if (!active() || button.disabled) return;
      if (result.state === 'RUNNING') {
        button.disabled = true;
        try { await follow(result); } finally { button.disabled = false; }
      }
    }).catch(error=>{if(active()) status.textContent=error.message;});
  }
};
