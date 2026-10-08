'use strict';
/** Run/test orchestration uses real Studio jobs; output never shares the interactive PTY. */
window.StudioProcesses=(bench,host)=>{
  const esc=host.escape,output=bench.page('Output'),tests=bench.page('Tests'),ports=bench.page('Ports'),problems=bench.page('Problems');
  const controls=document.createElement('div');controls.innerHTML='<form class="studio-tool-actions"><input name="name" placeholder="프로세스 이름" value="app" required maxlength="80"><input name="command" placeholder="npm run dev / ./mvnw test / python3 app.py" required maxlength="4000"><select name="kind"><option value="run">Run</option><option value="test">Test</option><option value="build">Build</option></select><button>실행</button><button type="button" data-suggest>명령 추천</button></form><div data-recipes></div><div data-processes></div><pre data-tool-output></pre>';
  output.prepend(controls);tests.innerHTML='<div class="studio-tool-actions"><button data-test-all>Run All</button><button data-test-failed>Rerun Failed</button><span>실패한 테스트 명령 전체를 다시 실행합니다.</span></div><div data-tests></div>';
  ports.innerHTML='<div class="studio-tool-actions"><button data-port-refresh>새로고침</button><small>5초마다 대상 장비의 TCP listening port를 확인합니다.</small></div><div data-ports></div>';
  let records=[],portRecords=[],selected=null,latestOutput='',loading=false,generation=0,recipes=[];
  const guard=action=>Promise.resolve().then(action).catch(error=>host.toast(error.message));
  function render(){
    const rows=items=>items.map(item=>`<div class="studio-process-row"><button data-run="logs" data-id="${esc(item.id)}">${esc(item.name)} · ${esc(item.state)} · PID ${item.pid||'—'} · ${portRecords.filter(port=>port.pids?.includes(item.pid)).map(port=>port.port).join(', ')}</button><button data-run="stop" data-id="${esc(item.id)}">Stop</button><button data-run="restart" data-id="${esc(item.id)}">Restart</button><button data-run="delete" data-id="${esc(item.id)}">삭제</button><small>${esc(item.command)}</small></div>`).join('')||'<p>등록된 프로세스가 없습니다.</p>';
    controls.querySelector('[data-processes]').innerHTML=rows(records);
    tests.querySelector('[data-tests]').innerHTML=rows(records.filter(item=>item.kind==='test'));
  }
  function renderProblems(text){
    const found=[...(host.editor()?.diagnostics?.()||[])];
    for(const line of text.split('\n')){
      const match=line.match(/File "([^"]+\.py)", line (\d+)/)||line.match(/(?:\[ERROR\]\s*)?([^\s:]+\.(?:java|kt|ts|tsx|js|jsx|py|c|cpp|h))(?::|\()(?:(?:\[)?)(\d+)(?:[,:](\d+))?/);
      if(match){let path=match[1],root=host.project()?.root;if(path.startsWith(root+'/'))path=path.slice(root.length+1);if(!path.startsWith('/')&&!path.split('/').includes('..'))found.push({path,line:Number(match[2]),column:Number(match[3]||1),message:line});}
    }
    problems.replaceChildren();for(const problem of found.slice(0,300)){const button=document.createElement('button');button.textContent=`${problem.path}:${problem.line} ${problem.message}`;button.onclick=()=>guard(async()=>{await host.openFile(problem.path);host.editor()?.goto(problem.line,problem.column);});problems.append(button);}if(!found.length)problems.textContent='위치를 해석할 수 있는 오류가 없습니다. 전체 출력도 확인하세요.';
  }
  async function refresh(){
    if(loading||!host.project())return;loading=true;const epoch=generation,context={...host.project()};
    try{
      const result=await host.job('run-list',{},context);if(epoch!==generation)return;records=result.tools?.processes||[];
      const discovered=await host.job('ports',{},context);if(epoch!==generation)return;portRecords=discovered.tools?.ports||[];
      ports.querySelector('[data-ports]').innerHTML=portRecords.map(port=>`<div class="studio-process-row"><b>${port.port} ${port.protocol}</b><span>${port.project?'프로젝트':'장비'} · PID ${(port.pids||[]).join(', ')||'조회 불가'}</span>${port.protocol==='HTTP'?`<button data-port-preview="${port.port}">Preview</button><button data-port-copy="${port.port}">URL 복사</button>`:''}</div>`).join('')||'<p>탐지된 포트가 없습니다.</p>';
      render();
      if(selected){const logs=await host.job('run-logs',{path:selected},context);if(epoch!==generation)return;latestOutput=logs.tools?.output||'';controls.querySelector('[data-tool-output]').textContent=latestOutput;renderProblems(latestOutput);}
    }finally{loading=false;}
  }
  async function start(name,command,kind){const epoch=generation,result=await host.job('run-start',{name,content:command,mode:kind});if(epoch!==generation)return;selected=result.tools.processes[0].id;bench.show('Output');await refresh();}
  controls.querySelector('form').onsubmit=event=>{event.preventDefault();const values=new FormData(event.currentTarget);guard(()=>start(values.get('name'),values.get('command'),values.get('kind')));};
  controls.querySelector('[data-suggest]').onclick=()=>guard(async()=>{recipes=(await host.job('run-commands')).tools?.commands||[];controls.querySelector('[data-recipes]').innerHTML=recipes.map((item,index)=>`<button data-recipe="${index}">${esc(item.name)} · ${esc(item.command)}</button>`).join('')||'추천 명령이 없습니다. 직접 입력하세요.';});
  controls.addEventListener('click',event=>{const button=event.target.closest('[data-recipe]');if(!button)return;const item=recipes[Number(button.dataset.recipe)],form=controls.querySelector('form');form.elements.name.value=item.name;form.elements.command.value=item.command;form.elements.kind.value=item.kind;});
  for(const element of [controls,tests])element.addEventListener('click',event=>{const button=event.target.closest('[data-run]');if(!button)return;guard(async()=>{selected=button.dataset.id;if(button.dataset.run==='logs')bench.show('Output');else {await host.job('run-'+button.dataset.run,{path:selected});if(button.dataset.run==='delete')selected=null;}await refresh();});});
  tests.querySelector('[data-test-all]').onclick=()=>guard(async()=>{const registered=records.filter(item=>item.kind==='test');if(registered.length){for(const item of registered)if(item.state!=='RUNNING')await host.job('run-restart',{path:item.id});await refresh();return;}const commands=(await host.job('run-commands')).tools?.commands?.filter(item=>item.kind==='test')||[];if(!commands.length)throw Error('Output에서 테스트 명령을 직접 등록하세요.');for(const item of commands)await start(item.name,item.command,'test');});
  tests.querySelector('[data-test-failed]').onclick=()=>guard(async()=>{for(const item of records.filter(item=>item.kind==='test'&&['FAILED','EXITED'].includes(item.state)))await host.job('run-restart',{path:item.id});await refresh();});
  ports.querySelector('[data-port-refresh]').onclick=()=>guard(refresh);
  ports.addEventListener('click',event=>{const preview=event.target.closest('[data-port-preview]'),copy=event.target.closest('[data-port-copy]');if(preview)guard(()=>bench.browser.open('http://127.0.0.1:'+preview.dataset.portPreview));if(copy)guard(async()=>{await navigator.clipboard.writeText('http://127.0.0.1:'+copy.dataset.portCopy);host.toast('대상 장비 기준 URL을 복사했습니다. IDE Browser/API에서 사용하세요.');});});
  const timer=setInterval(()=>{if(host.project())refresh().catch(()=>{});},5000);window.addEventListener('beforeunload',()=>clearInterval(timer));
  return {projectChanged(){generation++;records=[];portRecords=[];recipes=[];selected=null;latestOutput='';controls.querySelector('form').reset();controls.querySelector('[data-recipes]').replaceChildren();controls.querySelector('[data-tool-output]').textContent='';ports.querySelector('[data-ports]').replaceChildren();problems.replaceChildren();render();refresh().catch(error=>host.toast(error.message));},context:()=>({processes:records,ports:portRecords,output:latestOutput.slice(-30000)})};
};
