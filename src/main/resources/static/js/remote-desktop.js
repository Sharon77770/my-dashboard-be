'use strict';
/** Input and viewport controls for VNC/RDP. Session ownership remains in workspace runtime. */
window.WorkspaceRemoteDesktop = {
  /** Managed Linux bypasses VNC's Latin-1 clipboard conversion; other servers keep their stream. */
  async sendClipboard(runtime, text, mode, transfer) {
    if (!runtime.remoteConnected) throw new Error('원격 화면에 연결한 후 다시 시도하세요.');
    const client = runtime.guacamole;
    const result = await transfer(text);
    if (!runtime.remoteConnected || runtime.guacamole !== client) throw new Error('연결이 변경되었습니다. 다시 전송하세요.');
    if (!result.delivered) {
      const stream = client.createClipboardStream('text/plain');
      const bytes = new TextEncoder().encode(text);
      // Keep each WebSocket instruction below the server's frame limit, including Unicode text.
      for (let offset = 0; offset < bytes.length; offset += 4096)
        stream.sendBlob(btoa(String.fromCharCode(...bytes.subarray(offset, offset + 4096))));
      stream.sendEnd();
    }
    runtime.remoteClipboard = text;
    if (mode === 'paste' || mode === 'terminal') runtime.remoteControls.paste(mode === 'terminal');
  },
  attach(runtime, client, area, status) {
    const display = client.getDisplay(), root = runtime.element;
    root.querySelector('.live-runtime').classList.add('remote-desktop');
    area.append(display.getElement());
    let timer, lastSize = '', fitted = true;
    const key = value => {client.sendKeyEvent(1,value);client.sendKeyEvent(0,value);};
    const bar = document.createElement('form');bar.className = 'remote-input-bar';bar.hidden = true;
    bar.innerHTML = '<input name="text" aria-label="원격 화면에 보낼 텍스트" placeholder="한글·텍스트 입력" autocomplete="off" maxlength="4000"><button type="submit">전송</button><button type="button" data-key="65293">Enter</button><button type="button" data-key="65289">Tab</button><button type="button" data-key="65288" aria-label="한 글자 삭제">⌫</button><button type="button" data-key="65307">Esc</button>';
    area.after(bar);
    bar.onsubmit = event => {event.preventDefault();for (const character of bar.elements.text.value) {const point = character.codePointAt(0);key(point <= 255 ? point : 0x01000000 + point);}bar.elements.text.value = '';};
    bar.onclick = event => {const button = event.target.closest('[data-key]');if(button)key(Number(button.dataset.key));};
    const resize = () => {
      if (root.hidden || !area.clientWidth || !area.clientHeight) return;
      if(display.getWidth())display.scale(fitted ? Math.min(area.clientWidth/display.getWidth(),area.clientHeight/display.getHeight(),1) : 1);
      clearTimeout(timer);
      timer = setTimeout(() => {
        if (root.hidden || !fitted || !area.clientWidth || !area.clientHeight) return;
        const width = Math.max(640,Math.min(3840,Math.round(area.clientWidth)));
        const height = Math.max(240,Math.min(2160,Math.round(area.clientHeight*width/area.clientWidth)));
        const size = `${width}:${height}`;
        if(size !== lastSize) {lastSize = size;client.sendSize(width,height);}
      },180);
    };
    runtime.scale = resize;display.onresize = resize;
    for(const pointer of [new Guacamole.Mouse(display.getElement()),new Guacamole.Mouse.Touchpad(display.getElement())])
      pointer.onmousedown = pointer.onmouseup = pointer.onmousemove = state => client.sendMouseState(state,true);
    const keyboard = new Guacamole.Keyboard(area);runtime.keyboard = keyboard;
    keyboard.onkeydown = value => {client.sendKeyEvent(1,value);return false;};keyboard.onkeyup = value => client.sendKeyEvent(0,value);
    area.addEventListener('pointerdown',()=>area.focus({preventScroll:true}));area.addEventListener('blur',()=>keyboard.reset());
    for(const type of ['keydown','keyup','keypress']) area.addEventListener(type,event=>event.stopPropagation());
    runtime.remoteControls = {
      focus() {area.focus({preventScroll:true});},
      paste(terminal) {keyboard.reset();client.sendKeyEvent(1,0xffe3);if(terminal)client.sendKeyEvent(1,0xffe1);key(0x76);if(terminal)client.sendKeyEvent(0,0xffe1);client.sendKeyEvent(0,0xffe3);},
      keyboard(button) {bar.hidden = !bar.hidden;button.setAttribute('aria-expanded',String(!bar.hidden));if(!bar.hidden)bar.elements.text.focus();else area.focus();resize();},
      fit(button) {fitted = !fitted;button.textContent = fitted ? '화면 맞춤' : '원본 크기';button.setAttribute('aria-pressed',String(fitted));resize();},
      secureAttention() {client.sendKeyEvent(1,0xffe3);client.sendKeyEvent(1,0xffe9);key(0xffff);client.sendKeyEvent(0,0xffe9);client.sendKeyEvent(0,0xffe3);},
      dispose() {runtime.remoteConnected = false;clearTimeout(timer);keyboard.reset();keyboard.onkeydown = keyboard.onkeyup = null;bar.elements.text.value = '';}
    };
    client.onerror = () => status('화면 연결 실패 · 다시 연결하거나 연결 도우미로 점검하세요.');
    client.onstatechange = value => {runtime.remoteConnected = value === 3;status(({1:'연결 중…',2:'응답 대기 중…',3:'연결됨',4:'연결 종료 중…',5:'연결이 종료되었습니다. 다시 연결할 수 있습니다.'})[value] || '준비 중…');};
    client.onclipboard = (stream,type) => {if(type==='text/plain'){const reader=new Guacamole.StringReader(stream);let text='';reader.ontext=chunk=>{if(text.length<32000)text+=chunk;};reader.onend=()=>{runtime.remoteClipboard=text.slice(0,32000);};}};
    runtime.resizeObserver = new ResizeObserver(resize);runtime.resizeObserver.observe(area);client.connect();area.focus();
  }
};
