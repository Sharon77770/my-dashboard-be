'use strict';
(() => {
  let dialog, screen, status, codeLabel, connection, sequence = 0, returnFocus, screenKeyboard, currentCode, currentOptions;
  const attached = new WeakMap();
  const api = (...args) => window.WorkspaceAssistantRuntime.api(...args);
  const providerFor = value => {
    try {
      const url = new URL(value);
      if (url.protocol !== 'https:' || url.username || url.password || (url.port && url.port !== '443')) return null;
      return ({'accounts.google.com':'GOOGLE','github.com':'GITHUB','auth.openai.com':'CODEX','chatgpt.com':'CODEX'})[url.hostname] || null;
    } catch { return null; }
  };

  function build() {
    if (dialog) return;
    dialog = document.createElement('dialog');dialog.className = 'authentication-browser';dialog.setAttribute('aria-labelledby','authentication-browser-title');
    dialog.innerHTML = `<header><strong id="authentication-browser-title">인증 브라우저</strong><button type="button" data-auth-close aria-label="인증 브라우저 닫기">닫기</button></header>
      <nav aria-label="서버 계정 관리"><button type="button" data-auth-provider="GOOGLE">Google 계정</button><button type="button" data-auth-provider="GITHUB">GitHub 계정</button><button type="button" data-auth-provider="CODEX">ChatGPT 계정</button><button type="button" data-auth-reconnect>화면 다시 연결</button><button type="button" data-auth-zoom aria-pressed="false">화면 확대</button></nav>
      <p class="authentication-browser-hint">로그인 상태는 대시보드 서버에 보관됩니다. 사용을 마친 뒤 창을 닫으면 대시보드로 돌아갑니다.</p>
      <p data-auth-code hidden></p><p data-auth-status role="status" aria-live="polite"></p>
      <div class="authentication-browser-screen" tabindex="0" aria-label="서버 앱 원격 화면"></div>
      <form class="authentication-browser-text"><label>원격 입력<input name="text" type="password" autocomplete="off" maxlength="4000" placeholder="한글·텍스트 입력"></label><button type="submit">입력 전송</button></form>`;
    document.body.append(dialog);screen = dialog.querySelector('.authentication-browser-screen');status = dialog.querySelector('[data-auth-status]');codeLabel = dialog.querySelector('[data-auth-code]');
    const codeTimer = setInterval(() => {if (dialog.open) updateCode();},500);
    window.addEventListener('pagehide', () => clearInterval(codeTimer));
    dialog.querySelector('[data-auth-close]').onclick = () => dialog.close();
    dialog.querySelector('[data-auth-reconnect]').onclick = () => open({...currentOptions,code:currentCode});
    dialog.querySelector('[data-auth-zoom]').onclick = event => {
      const expanded = event.currentTarget.getAttribute('aria-pressed') !== 'true';
      event.currentTarget.setAttribute('aria-pressed',String(expanded));event.currentTarget.textContent = expanded ? '화면 맞춤' : '화면 확대';connection?.scale?.();
    };
    dialog.querySelectorAll('[data-auth-provider]').forEach(button => button.onclick = () => open({provider:button.dataset.authProvider}));
    dialog.addEventListener('close', () => {
      sequence++;currentCode = null;release();screen.replaceChildren();codeLabel.textContent = '';codeLabel.hidden = true;
      dialog.querySelector('input').value = '';if (returnFocus?.isConnected) returnFocus.focus();
    });
    screen.addEventListener('pointerdown', () => screen.focus());
    screen.addEventListener('blur', () => connection?.keyboard?.reset());
    dialog.querySelector('form').onsubmit = event => {
      event.preventDefault();const input = event.target.elements.text;
      if (!connection?.client || !input.value) return;
      // Unicode keysyms avoid leaving credentials in either local or remote clipboards.
      for (const character of input.value) {
        const point = character.codePointAt(0), key = point <= 0xff ? point : 0x01000000 + point;
        connection.client.sendKeyEvent(1,key);connection.client.sendKeyEvent(0,key);
      }
      input.value = '';screen.focus();
    };
    window.addEventListener('pagehide', () => {sequence++;release();});
  }

  async function release() {
    const previous = connection;connection = null;
    if (!previous) return;
    previous.observer?.disconnect();previous.keyboard?.reset();
    if (previous.keyboard) {previous.keyboard.onkeydown = null;previous.keyboard.onkeyup = null;}
    previous.client?.disconnect();
    if (previous.id) await api('/sessions/'+encodeURIComponent(previous.id),'DELETE',undefined,{quiet:true}).catch(()=>{});
  }

  function connect(session, version) {
    const endpoint = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/runtime/${encodeURIComponent(session.id)}`;
    const client = new Guacamole.Client(new Guacamole.WebSocketTunnel(endpoint));
    connection = {id:session.id,client};
    const display = client.getDisplay();screen.replaceChildren(display.getElement());
    const scale = () => {if (display.getWidth()) display.scale(dialog.querySelector('[data-auth-zoom]').getAttribute('aria-pressed') === 'true' ? 1 : Math.min(screen.clientWidth/display.getWidth(),screen.clientHeight/display.getHeight(),1));};
    connection.scale = scale;
    display.onresize = scale;
    for (const mouse of [new Guacamole.Mouse(display.getElement()),new Guacamole.Mouse.Touchpad(display.getElement())])
      mouse.onmousedown = mouse.onmouseup = mouse.onmousemove = state => client.sendMouseState(state,true);
    const keyboard = screenKeyboard ||= new Guacamole.Keyboard(screen);connection.keyboard = keyboard;
    keyboard.onkeydown = key => {client.sendKeyEvent(1,key);return false;};keyboard.onkeyup = key => client.sendKeyEvent(0,key);
    client.onerror = () => {if (version === sequence) status.textContent = '브라우저 화면 연결에 실패했습니다. 화면 다시 연결을 눌러 주세요.';};
    client.onstatechange = state => {if (version === sequence) status.textContent = ({1:'연결 중…',2:'응답 대기 중…',3:'서버 앱 화면에 연결됨 · 로그인과 메시지 전송은 원본 앱에서 직접 진행합니다.',4:'연결 종료 중…',5:'화면 연결이 종료되었습니다. 다시 연결할 수 있습니다.'})[state] || '연결 준비 중…';};
    connection.observer = new ResizeObserver(scale);connection.observer.observe(screen);client.connect();screen.focus();
  }

  function updateCode() {
    const value = typeof currentCode === 'function' ? currentCode() : currentCode;
    codeLabel.textContent = value ? '이번 인증 코드: '+String(value).slice(0,100) : '';codeLabel.hidden = !value;
  }

  /** Displays a session-owned VNC view; provider login cookies never enter frontend state. */
  async function open(options = {}) {
    build();const version = ++sequence;
    if (!dialog.open) {returnFocus = document.activeElement;dialog.showModal();}
    status.textContent = '서버 인증 브라우저를 여는 중…';
    currentOptions = options;
    dialog.querySelectorAll("[data-auth-provider]").forEach(button=>button.hidden=!!options.sessionFactory);
    dialog.querySelector("#authentication-browser-title").textContent=options.title||"인증 브라우저";
    currentCode = options.code;updateCode();
    dialog.querySelector('input').value = '';
    await release();if (version !== sequence || !dialog.open) return;
    screen.replaceChildren();
    try {
      const request = {provider:options.provider || 'BROWSER',width:1600,height:900};
      if (options.url) request.url = options.url;
      if (options.applicationId) request.applicationId = options.applicationId;
      const session = options.sessionFactory ? await options.sessionFactory() : await api('/authentication-browser/sessions','POST',request,{quiet:true});
      if (version !== sequence || !dialog.open) {await api('/sessions/'+encodeURIComponent(session.id),'DELETE',undefined,{quiet:true});return;}
      connection = {id:session.id};connect(session,version);
    } catch (error) {if (version === sequence) {await release();status.textContent = error.message || '인증 브라우저를 열지 못했습니다.';}}
  }

  /** Every auth presenter supplies its current code lazily so polling cannot leave a stale code. */
  function attach(link, code = () => '') {
    if (!link || !providerFor(link.href)) return;
    let button = attached.get(link);
    if (!button) {
      button = document.createElement('button');button.type = 'button';button.className = 'authentication-browser-open';button.textContent = '대시보드에서 인증';
      link.after(button);attached.set(link,button);
    }
    button.onclick = () => {const provider = providerFor(link.href);if (provider) open({provider,url:link.href,code});};
  }
  document.addEventListener('click', event => {
    const button = event.target.closest('[data-authentication-browser],[data-authentication-app]');if (!button) return;
    event.preventDefault();open(button.dataset.authenticationApp ? {provider:'APP',applicationId:button.dataset.authenticationApp} : {});
  });
  window.WorkspaceAuthenticationBrowser = {open,attach,providerFor};
})();
