'use strict';
/** Read-only recommendation precedes installation; polling belongs to the currently open dialog. */
window.WorkspaceRemoteSetup = (() => {
  let generation = 0;
  function open(id, ui) {
    const version = ++generation;
    ui.editor('원격 데스크톱 연결', `<section class="remote-setup" data-desktop-setup>
      <header><span class="remote-setup-icon" aria-hidden="true">▰</span><div><h3 data-setup-title>장비를 확인하고 있습니다</h3><p data-setup-description>사용 가능한 연결 방법을 찾습니다. 아직 장비를 변경하지 않습니다.</p></div></header>
      <ol class="remote-setup-steps" aria-label="화면 준비 단계"><li data-step="CHECKING">장비 확인</li><li data-step="INSTALLING">도구 준비</li><li data-step="STARTING">화면 시작</li><li data-step="VERIFYING">연결 확인</li></ol>
      <p data-setup-status role="status" aria-live="polite"></p>
      <label data-setup-password-label hidden>설치 권한 비밀번호 (sudo)<input data-setup-password type="password" autocomplete="off" maxlength="4096"><small>SSH 계정의 sudo 비밀번호입니다. 이번 설치에만 사용하고 저장하지 않습니다.</small></label>
      <div class="remote-setup-actions"><button class="primary" type="button" data-setup-start disabled>확인 중…</button><button type="button" data-setup-check>다시 확인</button></div>
      <p data-setup-background hidden>이 창을 닫아도 준비는 계속됩니다. 같은 장비를 다시 선택하면 진행 상황을 볼 수 있습니다.</p>
      <details data-setup-manual><summary>기존 원격 화면 연결 · 설정 수정</summary><p data-setup-manual-help>Windows는 원격 데스크톱을 허용한 PC의 계정 비밀번호를 사용합니다(PIN 제외). macOS는 화면 공유를 먼저 허용하세요.</p>
        <div class="remote-connection-fields"><label>연결 방식<select data-remote-protocol><option value="RDP">Windows · RDP</option><option value="VNC">화면 공유 · VNC</option></select></label><label>포트<input data-remote-port type="number" min="1" max="65535" value="3389"></label><label data-remote-username-label>원격 계정<input data-remote-username autocomplete="username" maxlength="128"></label><label>원격 비밀번호<input data-remote-password type="password" autocomplete="new-password" maxlength="4096" placeholder="저장된 비밀번호가 있으면 비워 두세요"></label></div>
        <button type="button" data-setup-save>저장하고 연결</button><button type="button" data-setup-device>SSH 장비 설정</button>
      </details></section>`, async () => {}, '닫기');
    const root = document.querySelector('[data-desktop-setup]');
    const find = selector => root.querySelector(selector);
    const button = find('[data-setup-start]'), status = find('[data-setup-status]');
    const active = () => version === generation && root.isConnected && root.closest('dialog')?.open;
    let plan, busy = false, checking = false;
    const endpoint = '/devices/' + encodeURIComponent(id) + '/remote-setup';
    const details = ui.device || {};
    find('[data-remote-protocol]').value = details.remoteProtocol === 'VNC' ? 'VNC' : 'RDP';
    find('[data-remote-port]').value = details.remoteProtocol && details.remoteProtocol !== 'NONE' ? details.remotePort : 3389;
    find('[data-remote-username]').value = details.remoteUsername || details.username || '';
    function protocolChanged() {
      const rdp = find('[data-remote-protocol]').value === 'RDP';
      find('[data-remote-username-label]').hidden = !rdp;
    }
    protocolChanged();
    find('[data-remote-protocol]').onchange = () => {find('[data-remote-port]').value = find('[data-remote-protocol]').value === 'RDP' ? 3389 : 5900;protocolChanged();};
    function controls() {
      button.disabled = busy || checking || !plan?.canStart;
      find('[data-setup-check]').disabled = busy || checking;
      for (const element of root.querySelectorAll('[data-setup-manual] input,[data-setup-manual] select,[data-setup-manual] button')) element.disabled = busy || checking;
      find('[data-setup-background]').hidden = !busy;
      find('[data-setup-password]').disabled = busy || checking;
      root.setAttribute('aria-busy', String(busy || checking));
    }
    function renderProgress(result) {
      status.textContent = result.message;
      const order = ['CHECKING','INSTALLING','STARTING','VERIFYING'];
      const current = order.indexOf(result.stage || 'CHECKING');
      root.querySelectorAll('[data-step]').forEach((element,index) => {
        element.dataset.state = result.state === 'READY' || index < current ? 'done' : index === current && result.state === 'RUNNING' ? 'active' : 'waiting';
        if(index === current && result.state === 'RUNNING') element.setAttribute('aria-current','step'); else element.removeAttribute('aria-current');
      });
      if (result.code === 'ADMIN_REQUIRED') find('[data-setup-password-label]').hidden = false;
    }
    async function follow(result) {
      while (active()) {
        renderProgress(result);
        if (result.state === 'READY') {
          await ui.refresh();
          if (active()) {root.closest('dialog').close();await ui.connect();}
          return;
        }
        if (result.state !== 'RUNNING') return;
        await new Promise(resolve => setTimeout(resolve, 1200));
        if (!active()) return;
        result = await ui.api(endpoint, 'GET', undefined, {quiet:true});
      }
    }
    async function inspect() {
      if (busy || checking) return;
      checking = true;controls();status.textContent = '장비 확인 중…';
      try {
        const job = await ui.api(endpoint, 'GET', undefined, {quiet:true});
        if (!active()) return;
        if (job.state === 'RUNNING') {busy = true;checking = false;controls();button.textContent = '준비 중…';await follow(job);return;}
        plan = await ui.api(endpoint + '/plan', 'GET', undefined, {quiet:true});
        if (!active()) return;
        find('[data-setup-title]').textContent = plan.title;
        find('[data-setup-description]').textContent = plan.message;
        button.textContent = plan.actionLabel || '자동 준비 불가';
        find('[data-setup-password-label]').hidden = !plan.requiresPassword;
        if (!plan.canStart) find('[data-setup-manual]').open = true;
        status.textContent = job.state === 'BLOCKED' ? job.message : '';
      } catch (error) {if (active()) {status.textContent = error.message;button.textContent = '연결 준비';}}
      finally {if (active()) {busy = false;checking = false;controls();}}
    }
    async function start() {
      busy = true;button.textContent = '준비 중…';controls();
      const input = find('[data-setup-password]');
      const request = {sudoPassword:input.value}; input.value = '';
      try { await follow(await ui.api(endpoint, 'POST', request, {quiet:true})); }
      catch (error) {if (active()) status.textContent = error.message;}
      finally {request.sudoPassword = '';if (active()) {busy = false;button.textContent = '다시 준비하고 연결';controls();}}
    }
    button.onclick = start;
    find('[data-setup-check]').onclick = inspect;
    find('[data-setup-device]').onclick = () => {root.closest('dialog').close();ui.edit?.();};
    find('[data-setup-save]').onclick = async () => {
      const port = Number(find('[data-remote-port]').value);
      if (!Number.isInteger(port) || port < 1 || port > 65535) {status.textContent = '포트는 1~65535 사이로 입력해 주세요.';return;}
      busy = true;controls();
      const password = find('[data-remote-password]');
      const request = {protocol:find('[data-remote-protocol]').value,port,username:find('[data-remote-username]').value,password:password.value}; password.value = '';
      try {
        await ui.api(endpoint + '/connection','PUT',request,{quiet:true});
        if (active()) {plan = {canStart:true};await start();}
      } catch (error) {if (active()) status.textContent = error.message;}
      finally {request.password = '';if (active()) {busy = false;controls();}}
    };
    root.closest('dialog').addEventListener('close', () => {
      // The server job deliberately continues. A later open resumes its status instead of restarting.
      root.querySelectorAll('input[type=password]').forEach(input => {input.value = '';});
    }, {once:true});
    inspect();
  }
  return {open};
})();
