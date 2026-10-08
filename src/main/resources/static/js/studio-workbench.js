'use strict';
/** Studio-owned panels and retained PTY clients. Dashboard navigation is not involved. */
window.StudioWorkbench = (root, host) => {
  const panel=document.createElement('section');panel.className='studio-bottom';panel.hidden=true;
  const names=['Terminal','Problems','Output','Tests','Ports','Browser','API'];
  panel.innerHTML=`<div class="studio-bottom-resize" role="separator" tabindex="0" aria-label="하단 패널 높이" aria-orientation="horizontal"></div><div class="studio-bottom-tabs" role="tablist">${names.map(name=>`<button type="button" role="tab" data-bottom="${name}" aria-selected="${name==='Terminal'}">${name}</button>`).join('')}<button type="button" data-bottom-fold aria-expanded="true" aria-label="하단 패널 접기">⌄</button></div><div class="studio-bottom-body">${names.map(name=>`<section data-bottom-page="${name}" ${name==='Terminal'?'':'hidden'} aria-label="${name}"></section>`).join('')}</div>`;
  root.append(panel);
  const page=name=>panel.querySelector(`[data-bottom-page="${name}"]`);
  const output=root.querySelector('.studio-output');page('Output').append(output);output.open=true;
  page('Terminal').innerHTML='<div class="studio-tool-actions"><button type="button" data-terminal-new>새 Terminal</button><span class="studio-terminal-tabs"></span></div><div class="studio-terminals"></div>';
  const terminals=new Map();let active=null,height=280,selected='Terminal';
  const projectKey=()=>JSON.stringify(host.project());
  const guarded=action=>Promise.resolve().then(action).catch(error=>host.toast(error.message));
  function resize(){panel.style.setProperty('--bottom-height',height+'px');for(const session of terminals.values())if(!session.element.hidden&&panel.offsetHeight)session.fit.fit();}
  function show(name){selected=name;panel.hidden=false;panel.classList.remove('collapsed');panel.querySelector('[data-bottom-fold]').setAttribute('aria-expanded','true');for(const item of panel.querySelectorAll('[data-bottom-page]'))item.hidden=item.dataset.bottomPage!==name;for(const button of panel.querySelectorAll('[data-bottom]'))button.setAttribute('aria-selected',String(button.dataset.bottom===name));resize();}
  panel.querySelectorAll('[data-bottom]').forEach(button=>button.onclick=()=>show(button.dataset.bottom));
  panel.querySelector('[data-bottom-fold]').onclick=event=>{panel.classList.toggle('collapsed');event.currentTarget.setAttribute('aria-expanded',String(!panel.classList.contains('collapsed')));resize();};
  const handle=panel.querySelector('.studio-bottom-resize');let drag;
  handle.onpointerdown=event=>{drag={y:event.clientY,height};handle.setPointerCapture(event.pointerId);event.preventDefault();};
  handle.onpointermove=event=>{if(drag){height=Math.max(140,Math.min(root.clientHeight*.7,drag.height+drag.y-event.clientY));resize();}};
  handle.onpointerup=handle.onpointercancel=()=>{drag=null;};
  handle.onkeydown=event=>{if(['ArrowUp','ArrowDown'].includes(event.key)){event.preventDefault();height=Math.max(140,Math.min(root.clientHeight*.7,height+(event.key==='ArrowUp'?24:-24)));resize();}};
  new ResizeObserver(resize).observe(panel);
  function tabs(){
    const target=page('Terminal').querySelector('.studio-terminal-tabs');target.replaceChildren();
    for(const session of terminals.values()){
      const visible=session.key===projectKey();session.element.hidden=!visible||session.id!==active;
      if(!visible)continue;
      const button=document.createElement('button');button.type='button';button.textContent=session.name;button.setAttribute('aria-pressed',String(session.id===active));button.onclick=()=>{active=session.id;tabs();resize();session.terminal.focus();};target.append(button);
    }
  }
  async function close(session){await host.api('/sessions/'+session.id,'DELETE');session.socket.close();session.terminal.dispose();session.element.remove();terminals.delete(session.id);active=[...terminals.values()].find(item=>item.key===projectKey())?.id;tabs();}
  async function newTerminal(){
    const project=host.project(),key=projectKey();if(!project)throw Error('프로젝트 폴더를 먼저 여세요.');
    show('Terminal');
    const result=await host.api('/sessions','POST',{kind:'TERMINAL',targetId:project.deviceId,root:project.root,width:1200,height:480});
    const element=document.createElement('div');element.className='studio-terminal-session';
    element.innerHTML='<div class="studio-tool-actions"><span role="status">연결 중</span><button type="button" data-rename>이름 변경</button><button type="button" data-reconnect>재연결 (새 셸)</button><button type="button" data-close>닫기</button></div><div class="studio-terminal-screen"></div>';
    page('Terminal').querySelector('.studio-terminals').append(element);
    const terminal=new Terminal({cursorBlink:true,fontSize:13,scrollback:5000,theme:window.WorkspaceUI?.terminalTheme?.()});
    const fit=new FitAddon.FitAddon();terminal.loadAddon(fit);terminal.open(element.querySelector('.studio-terminal-screen'));
    const socket=new WebSocket(`${location.protocol==='https:'?'wss':'ws'}://${location.host}/ws/runtime/${result.id}`);
    const session={id:result.id,key,element,terminal,fit,socket,name:'Terminal '+(terminals.size+1)};terminals.set(session.id,session);active=session.id;tabs();
    let ready=false;const status=text=>{element.querySelector('[role=status]').textContent=text;};
    socket.onmessage=event=>{if(!ready&&event.data==='{"type":"ready"}'){ready=true;status(project.root);resize();return;}session.output=((session.output||'')+event.data).slice(-16000);terminal.write(event.data);};
    socket.onclose=event=>{ready=false;status(event.reason||'연결 종료 · 재연결은 새 셸을 시작합니다.');};
    socket.onerror=()=>status('터미널 연결 실패');
    terminal.onData(data=>{if(ready&&socket.readyState===1)socket.send(JSON.stringify({type:'input',data}));});
    terminal.onResize(size=>{if(ready&&socket.readyState===1)socket.send(JSON.stringify({type:'resize',columns:size.cols,rows:size.rows}));});
    element.querySelector('[data-close]').onclick=()=>guarded(async()=>{if(await host.confirm('이 터미널과 실행 중인 셸 작업을 종료할까요?'))await close(session);});
    element.querySelector('[data-reconnect]').onclick=()=>guarded(async()=>{if(await host.confirm('기존 셸을 종료하고 새 셸로 연결할까요?')){await close(session);await newTerminal();}});
    element.querySelector('[data-rename]').onclick=()=>host.input('터미널 이름',[{name:'name',label:'이름',value:session.name,max:80}],async values=>{session.name=values.name;tabs();});
  }
  page('Terminal').querySelector('[data-terminal-new]').onclick=()=>guarded(newTerminal);
  window.addEventListener('beforeunload',()=>{for(const session of terminals.values())session.socket.close();});
  const bench={page,show,newTerminal,projectChanged(){panel.hidden=false;active=[...terminals.values()].find(item=>item.key===projectKey())?.id;tabs();resize();for(const tool of [bench.processes,bench.browser,bench.apiClient])tool?.projectChanged();},selected:()=>selected};
  bench.processes=window.StudioProcesses?.(bench,host);
  bench.browser=window.StudioBrowser?.(bench,host);
  bench.apiClient=window.StudioApi?.(bench,host);
  const redact=text=>text.replace(/\x1b\[[0-9;?]*[A-Za-z]/g,'').replace(/\b(?:sk-[A-Za-z0-9_-]{8,}|gh[pousr]_[A-Za-z0-9_]{8,})/g,'[redacted]').replace(/((?:authorization\s*[:=]\s*(?:Bearer|Basic)|(?:api[_-]?key|token|password|secret)\s*[:=])\s*)[^\s]+/gi,'$1[redacted]');
  bench.context=()=>({terminals:[...terminals.values()].filter(session=>session.key===projectKey()).map(session=>({name:session.name,output:redact(session.output||'')})),runtime:bench.processes?.context(),browser:bench.browser?.context(),api:bench.apiClient?.context()});
  return bench;
};
