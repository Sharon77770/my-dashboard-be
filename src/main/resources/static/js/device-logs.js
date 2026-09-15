'use strict';
window.WorkspaceLogs = (() => {
  let ui, root, devices=[], job=null, generation=0, timer=null, sequence=0, buffer='', selected=null;
  let paintedBuffer=null, paintedOptions='';
  const preferenceKey='workspace-log-view-v1:'+encodeURIComponent(document.body.dataset.account||'owner');
  const $=selector=>root.querySelector(selector);
  const status=text=>{$('[data-log-status]').textContent=text;};
  function controls(busy){$('[data-log-start]').disabled=busy||!$('[data-log-target]').value;$('[data-log-stop]').disabled=!busy;}
  async function stop(){
    generation++;clearTimeout(timer);const id=job;job=null;controls(false);
    if(id)await ui.api(`/studio/jobs/${id}`,'DELETE');
  }
  function paint(){
    const options={format:$('[data-log-format]').value,color:$('[data-log-color]').value,wrap:$('[data-log-wrap]').checked,frame:$('[data-log-frame]').checked};
    const signature=JSON.stringify(options), output=$('[data-log-output]');
    if(paintedBuffer===buffer&&paintedOptions===signature)return;
    const top=output.scrollTop,left=output.scrollLeft;
    if(window.LogPresentation)window.LogPresentation.render(output,buffer,options);else output.textContent=buffer;
    paintedBuffer=buffer;paintedOptions=signature;
    output.scrollTop=top;output.scrollLeft=left;
    if($('[data-log-scroll]').checked)$('[data-log-output]').scrollTop=$('[data-log-output]').scrollHeight;
  }
  async function run(action, onUpdate){
    const stopping=stop(), version=generation;await stopping;if(version!==generation)return;
    const device=devices.find(item=>item.id===$('[data-log-device]').value);
    if(!device)throw new Error('장비를 선택하세요.');
    controls(true);status(action==='logs-targets'?'목록을 불러오는 중…':'연결 중…');
    try {
      const created=await ui.api('/studio/jobs','POST',{deviceId:device.id,root:device.rootPath,action,args:{mode:$('[data-log-source]').value,target:$('[data-log-target]').value}});
      if(version!==generation){await ui.api(`/studio/jobs/${created.id}`,'DELETE');return;}
      job=created.id;
      const poll=async()=>{
        try{
          const view=await ui.api(`/studio/jobs/${created.id}`);
          if(version!==generation)return;
          onUpdate(view);
          if(['QUEUED','RUNNING'].includes(view.state))timer=setTimeout(poll,400);
          else{job=null;controls(false);if(view.state==='FAILED')status(view.error||'로그 조회에 실패했습니다.');else if(view.state==='CANCELLED')status('중지됨 · 다시 시작할 수 있습니다.');}
        }catch(error){if(version===generation){await stop().catch(()=>{});status(error.message);}}
      };
      await poll();
    }catch(error){if(version===generation){controls(false);status(error.message);}}
  }
  async function targets(){
    $('[data-log-target]').innerHTML='<option value="">대상 선택</option>';
    await run('logs-targets',view=>{
      if(view.state!=='SUCCEEDED')return;
      const items=view.result?.logTargets||[];
      $('[data-log-target]').innerHTML=items.length?items.map(item=>`<option value="${ui.escape(item.id)}">${ui.escape(item.name)} · ${ui.escape(item.status)}</option>`).join(''):'<option value="">실행할 대상 없음</option>';
      status(items.length?'대상을 선택하고 실시간 보기를 시작하세요.':'컨테이너 또는 현재 계정의 tmux 세션이 없습니다.');
    });
  }
  async function follow(){
    buffer='';sequence=0;paint();
    await run('logs-follow',view=>{
      for(const event of view.events||[]){
        if(!event.sequence||event.sequence<=sequence)continue;
        if(event.event==='log-snapshot')buffer=event.text||'';
        else if(event.event==='log-append'){
          if(event.sequence>sequence+1)buffer+='\n[수신 간격으로 일부 로그가 생략되었습니다.]\n';
          buffer+=event.text||'';
        }
        sequence=event.sequence;buffer=buffer.slice(-200000);
      }
      paint();status(view.state==='SUCCEEDED'?'출력이 종료되었습니다. 다시 시작할 수 있습니다.':$('[data-log-source]').value==='tmux'?'실시간 · tmux 화면 1초 갱신':'실시간 · Docker 로그 수신 중');
    });
  }
  function guard(action){Promise.resolve().then(action).catch(error=>status(error.message));}
  return {
    init(helpers){
      ui=helpers;root=document.getElementById('logs');if(!root)return;
      root.innerHTML=`<div class="log-toolbar"><h1>장비 로그</h1><label>장비<select data-log-device></select></label><label>종류<select data-log-source><option value="docker">Docker</option><option value="tmux">tmux</option></select></label><button data-log-refresh aria-label="로그 대상 새로고침">↻ 새로고침</button></div><div class="log-toolbar"><label class="log-target">대상<select data-log-target><option value="">대상 선택</option></select></label><button class="primary" data-log-start disabled>실시간 보기</button><button data-log-stop disabled>중지</button><label class="log-check"><input type="checkbox" data-log-scroll checked>자동 스크롤</label><button data-log-clear>화면 지우기</button></div><p class="section-hint">서버에서 읽기 전용으로 조회합니다. 장비의 SSH·Tailscale 설정을 사용합니다. tmux는 현재 계정의 기본 소켓을 조회합니다. 화면 이동 시 중지되며, 한 번에 최대 15분 동안 연결됩니다.</p><div class="log-toolbar log-display-options" aria-label="로그 표시 옵션"><label>보기<select data-log-format><option value="raw">원문</option><option value="json">JSON 정렬 (자동 인식)</option></select></label><label>컬러맵<select data-log-color><option value="syntax">구문 강조</option><option value="levels">로그 수준</option><option value="none">단색</option></select></label><label class="log-check"><input type="checkbox" data-log-wrap checked>줄바꿈</label><label class="log-check"><input type="checkbox" data-log-frame checked>코드 블록</label></div><p data-log-status role="status">장비를 선택하세요.</p><pre data-log-output tabindex="0" aria-label="실시간 로그 출력"></pre>`;
      try {
        const saved=JSON.parse(localStorage.getItem(preferenceKey)||'{}');
        if(['raw','json'].includes(saved.format))$('[data-log-format]').value=saved.format;
        if(['syntax','levels','none'].includes(saved.color))$('[data-log-color]').value=saved.color;
        for(const key of ['wrap','frame'])if(typeof saved[key]==='boolean')$('[data-log-'+key+']').checked=saved[key];
      }catch{}
      for(const key of ['format','color','wrap','frame'])$('[data-log-'+key+']').addEventListener('change',()=>{paint();try{localStorage.setItem(preferenceKey,paintedOptions);}catch{}});
      $('[data-log-scroll]').addEventListener('change',()=>{if($('[data-log-scroll]').checked)$('[data-log-output]').scrollTop=$('[data-log-output]').scrollHeight;});
      paint();
      $('[data-log-device]').addEventListener('change',()=>guard(targets));
      $('[data-log-source]').addEventListener('change',()=>guard(targets));
      $('[data-log-target]').addEventListener('change',()=>guard(async()=>{await stop();status('대상을 선택했습니다. 실시간 보기를 시작하세요.');}));
      $('[data-log-refresh]').addEventListener('click',()=>guard(targets));
      $('[data-log-start]').addEventListener('click',()=>guard(follow));
      $('[data-log-stop]').addEventListener('click',()=>guard(async()=>{await stop();status('중지됨');}));
      $('[data-log-clear]').addEventListener('click',()=>{buffer='';paint();});
      window.addEventListener('pagehide',()=>{if(job)ui.api(`/studio/jobs/${job}`,'DELETE').catch(()=>{});});
    },
    select(id){selected=id;},
    async open(view){
      if(!root)return;
      if(view!=='logs'){await stop();return;}
      const stopping=stop(), version=generation;await stopping;if(version!==generation)return;
      const state=await ui.api('/workspace');
      if(version!==generation)return;
      const previous=selected||$('[data-log-device]').value||'local';selected=null;devices=state.devices;
      $('[data-log-device]').innerHTML=devices.map(item=>`<option value="${ui.escape(item.id)}">${ui.escape(item.name)}${item.networkMode==='TAILSCALE'?' · Tailscale':''}</option>`).join('');
      $('[data-log-device]').value=devices.some(item=>item.id===previous)?previous:devices[0]?.id||'';
      await targets();
    }
  };
})();
