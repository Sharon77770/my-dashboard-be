'use strict';
/** Immersive view of the authenticated server desktop; no page content or cookies enter app state. */
(() => {
  let root, screen, status, keyboard, connection, observer, sequence = 0, resizeTimer;
  let fitted = true, lastSize = '';
  const api = (...args) => window.WorkspaceAssistantRuntime.api(...args);
  const find = selector => root.querySelector(selector);
  const report = message => { status.textContent = message; status.hidden = !message; };

  function viewport() {
    const width = Math.max(480, Math.min(3840, Math.round(screen.clientWidth)));
    const height = Math.max(240, Math.min(2160, Math.round(screen.clientHeight * width / Math.max(screen.clientWidth, 1))));
    return {width, height};
  }

  function resize() {
    if (!root?.classList.contains('active')) return;
    root.style.setProperty('--chrome-height', `${window.visualViewport?.height || window.innerHeight}px`);
    const display = connection?.client.getDisplay();
    if (!display?.getWidth()) return;
    display.scale(fitted ? Math.min(screen.clientWidth / display.getWidth(), screen.clientHeight / display.getHeight(), 1) : 1);
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => {
      if (!connection || !fitted || !root.classList.contains('active')) return;
      const size = viewport(), key = `${size.width}:${size.height}`;
      if (key !== lastSize) { lastSize = key; connection.client.sendSize(size.width, size.height); }
    }, 180);
  }

  function keypress(key) {
    connection?.client.sendKeyEvent(1, key);
    connection?.client.sendKeyEvent(0, key);
  }

  async function release() {
    const previous = connection; connection = null; lastSize = '';
    clearTimeout(resizeTimer);
    keyboard?.reset();
    if (keyboard) { keyboard.onkeydown = null; keyboard.onkeyup = null; }
    previous?.client.disconnect();
    if (previous) {
      Guacamole.AudioContextFactory.getAudioContext()?.suspend().catch(() => {});
      find('[data-chrome-audio]').setAttribute('aria-pressed', 'false'); find('[data-chrome-audio]').textContent = '소리 켜기';
    }
    screen?.replaceChildren();
    if (previous?.id) await api(`/sessions/${encodeURIComponent(previous.id)}`, 'DELETE', undefined, {quiet:true}).catch(() => {});
  }

  async function connect() {
    const version = ++sequence;
    await release();
    if (version !== sequence || !root.classList.contains('active')) return;
    report('서버 Chrome에 연결 중…');
    resize();
    try {
      const session = await api('/chrome/sessions', 'POST', viewport(), {quiet:true});
      if (version !== sequence || !root.classList.contains('active')) {
        await api(`/sessions/${encodeURIComponent(session.id)}`, 'DELETE', undefined, {quiet:true}); return;
      }
      const endpoint = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/runtime/${encodeURIComponent(session.id)}`;
      const client = new Guacamole.Client(new Guacamole.WebSocketTunnel(endpoint));
      connection = {id:session.id, client};
      const display = client.getDisplay(); screen.replaceChildren(display.getElement());
      display.onresize = resize;
      for (const pointer of [new Guacamole.Mouse(display.getElement()), new Guacamole.Mouse.Touchpad(display.getElement())]) {
        pointer.onmousedown = pointer.onmouseup = pointer.onmousemove = state => client.sendMouseState(state, true);
      }
      keyboard ||= new Guacamole.Keyboard(screen);
      keyboard.onkeydown = key => {client.sendKeyEvent(1, key); return false;};
      keyboard.onkeyup = key => client.sendKeyEvent(0, key);
      client.onerror = () => { if (version === sequence) report('연결에 실패했습니다. 메뉴에서 다시 연결을 눌러 주세요.'); };
      client.onstatechange = state => {
        if (version !== sequence) return;
        if (state === 3) {report(''); resize();}
        if (state === 5) report('연결이 종료되었습니다. 메뉴에서 다시 연결할 수 있습니다.');
      };
      Guacamole.AudioContextFactory.getAudioContext()?.suspend().catch(() => {});
      client.connect(); screen.focus();
    } catch (error) {
      if (version === sequence) { await release(); report(error.message || 'Chrome을 열지 못했습니다. 메뉴에서 다시 연결해 주세요.'); }
    }
  }

  function build() {
    root = document.getElementById('chrome');
    root.innerHTML = `<header class="chrome-toolbar"><button type="button" data-view="home" aria-label="Chrome에서 대시보드로 돌아가기">← <span>대시보드</span></button><strong>Chrome</strong><span class="chrome-toolbar-space"></span><button type="button" data-chrome-keyboard aria-expanded="false" aria-controls="chrome-input">키보드</button><button type="button" data-chrome-audio aria-pressed="false">소리 켜기</button><button type="button" data-chrome-fullscreen aria-label="Chrome 전체 화면">⛶</button><details class="chrome-menu"><summary aria-label="Chrome 메뉴">⋯</summary><div><button type="button" data-chrome-reconnect>다시 연결</button><button type="button" data-chrome-fit aria-pressed="true">화면 맞춤</button><button type="button" data-chrome-address>주소창으로 이동</button><p>터치: 한 손가락으로 포인터 이동, 탭으로 클릭, 두 손가락으로 스크롤합니다. 한글은 키보드 입력창을 이용하세요.</p><p>서버 Chromium의 탭과 로그인 상태는 인증 브라우저와 공유됩니다.</p></div></details></header><p class="chrome-status" role="status" aria-live="polite" hidden></p><div class="chrome-screen" tabindex="0" aria-label="서버 Chrome 화면"></div><form id="chrome-input" class="chrome-input" hidden><input name="text" aria-label="Chrome에 보낼 텍스트" placeholder="한글·검색어 입력" autocomplete="off" maxlength="4000"><button type="submit">전송</button><button type="button" data-chrome-enter>Enter</button><button type="button" data-chrome-backspace aria-label="한 글자 삭제">⌫</button><button type="button" data-chrome-escape>Esc</button></form>`;
    screen = find('.chrome-screen'); status = find('.chrome-status');
    // Remote Ctrl+K / Alt+Left belong to Chromium, not the dashboard command palette.
    for (const type of ['keydown', 'keyup', 'keypress']) root.addEventListener(type, event => event.stopPropagation());
    screen.addEventListener('pointerdown', () => screen.focus({preventScroll:true}));
    screen.addEventListener('blur', () => keyboard?.reset());
    find('[data-chrome-reconnect]').onclick = () => {find('details').open = false; connect();};
    find('[data-chrome-fit]').onclick = event => {
      fitted = !fitted; event.currentTarget.setAttribute('aria-pressed', String(fitted));
      event.currentTarget.textContent = fitted ? '화면 맞춤' : '원본 크기 · 스크롤';
      find('details').open = false; resize();
    };
    find('[data-chrome-keyboard]').onclick = event => {
      const form = find('form'); form.hidden = !form.hidden;
      event.currentTarget.setAttribute('aria-expanded', String(!form.hidden));
      if (!form.hidden) find('input').focus(); else screen.focus();
      resize();
    };
    find('[data-chrome-address]').onclick = () => {
      connection?.client.sendKeyEvent(1, 0xffe3); keypress(0x6c); connection?.client.sendKeyEvent(0, 0xffe3);
      find('details').open = false;
      if (matchMedia('(pointer:coarse)').matches) {
        find('form').hidden = false; find('[data-chrome-keyboard]').setAttribute('aria-expanded', 'true'); find('input').focus(); resize();
      } else screen.focus();
    };
    find('[data-chrome-enter]').onclick = () => keypress(0xff0d);
    find('[data-chrome-backspace]').onclick = () => keypress(0xff08);
    find('[data-chrome-escape]').onclick = () => keypress(0xff1b);
    find('form').onsubmit = event => {
      event.preventDefault(); if (!connection) return;
      const input = find('input');
      for (const character of input.value) { const point = character.codePointAt(0); keypress(point <= 0xff ? point : 0x01000000 + point); }
      input.value = '';
    };
    find('[data-chrome-audio]').onclick = async event => {
      const button = event.currentTarget, context = Guacamole.AudioContextFactory.getAudioContext();
      if (!context) {report('이 기기에서는 원격 오디오를 지원하지 않습니다.'); return;}
      try {
        if (button.getAttribute('aria-pressed') === 'true') await context.suspend(); else await context.resume();
        const enabled = context.state === 'running'; button.setAttribute('aria-pressed', String(enabled)); button.textContent = enabled ? '소리 끄기' : '소리 켜기';
      } catch { report('소리를 켜지 못했습니다. 다시 눌러 주세요.'); }
    };
    find('[data-chrome-fullscreen]').onclick = async () => {
      try {if (document.fullscreenElement) await document.exitFullscreen(); else if (root.requestFullscreen) await root.requestFullscreen(); else report('이 기기에서는 현재 화면이 최대 크기입니다.');}
      catch { report('전체 화면을 사용할 수 없어 현재 크기를 유지합니다.'); }
    };
    observer = new ResizeObserver(resize); observer.observe(screen);
    window.visualViewport?.addEventListener('resize', resize);
    window.addEventListener('resize', resize);
    // Also handles switching into a runtime tab, which does not emit workspace:view.
    new MutationObserver(() => {if (!root.classList.contains('active')) {sequence++; find('input').value = ''; if (document.fullscreenElement === root) document.exitFullscreen().catch(() => {}); release();}}).observe(root, {attributes:true, attributeFilter:['class']});
    window.addEventListener('pagehide', () => {sequence++; release();});
  }

  window.addEventListener('workspace:view', event => {
    if (event.detail.id !== 'chrome') return;
    if (!root) build();
    if (!connection) connect();
  });
})();
