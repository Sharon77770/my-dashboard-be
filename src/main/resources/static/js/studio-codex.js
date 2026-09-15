'use strict';
(() => {
  window.StudioCodex = (panel, host) => {
    const $ = selector => panel.querySelector(selector), esc = host.escape;
    let thread=null, models=[], contexts=[], running=null, sequence=0, loaded=false, cursor=null, archived=false;
    const items=new Map();
    panel.innerHTML=`<div class="studio-toolbar"><div class="cx-panel-title"><strong>Codex</strong><span id="cx-title">새 세션</span><small id="cx-thread-id" hidden></small></div><button data-cx="history" title="세션 목록" aria-label="세션 목록">◷</button><button data-cx="new" title="새 세션" aria-label="새 세션">＋</button><details class="ui-menu"><summary aria-label="세션 메뉴">⋯</summary><div class="ui-menu-content"><span id="studio-auth" class="badge">인증 확인 전</span><button data-cx="rename">이름 변경</button><button data-cx="fork">세션 분기</button><button data-cx="compact">컨텍스트 압축</button><button data-cx="rollback">마지막 대화 되돌리기</button><button data-cx="archive">세션 보관</button><button data-cx="review">변경 사항 리뷰</button><button data-cx="skills">스킬 첨부</button><button data-cx="connections">MCP 연결 상태</button><button data-cx="refresh">모델·인증 새로고침</button><button data-studio="codex-login">기기 코드 로그인</button><button data-studio="codex-logout" hidden>로그아웃</button></div></details></div>
      <div id="studio-auth-cta" hidden><p>선택한 서버에서 로그인해 주세요.</p><button data-studio="codex-login">기기 코드 로그인</button></div>
      <section id="cx-history" hidden aria-label="세션 목록"><form id="cx-search"><input name="query" aria-label="세션 검색" placeholder="세션 검색"><button>검색</button></form><label><input id="cx-archived" type="checkbox"> 보관된 세션</label><div id="cx-sessions"></div><button data-cx="more" hidden>더 보기</button><button data-cx="history-close">대화로 돌아가기</button></section>
      <div id="studio-conversation" role="log" aria-label="Codex 대화"></div><div id="cx-interactions" aria-live="polite"></div><p id="cx-status" role="status"></p>
      <form id="studio-prompt-form"><div class="composer-context"><span id="studio-context">작업 폴더</span><span id="studio-mode-label">읽기 · 분석</span></div><div id="cx-contexts"></div><textarea id="studio-prompt" placeholder="작업을 요청하세요. / 로 명령 보기" aria-label="Codex 작업 요청" rows="3" maxlength="32000" required></textarea>
      <footer class="cx-composer-toolbar"><details class="ui-menu cx-attachments"><summary title="컨텍스트 추가" aria-label="컨텍스트 추가">＋</summary><div class="ui-menu-content"><button type="button" data-cx="file">현재 파일 첨부</button><button type="button" data-cx="selection">선택 영역 첨부</button><button type="button" data-cx="path">파일 경로 입력</button><button type="button" data-cx="image">이미지 첨부</button><button type="button" data-cx="skills">스킬 첨부</button></div></details><input id="cx-image" type="file" accept="image/png,image/jpeg,image/webp" hidden><div class="cx-settings"><label><span class="studio-sr-only">모델</span><select id="studio-codex-model" title="모델"><option value="">기본 모델</option></select></label><label><span class="studio-sr-only">추론 강도</span><select id="cx-effort" title="추론 강도"><option value="">기본</option></select></label></div><button type="button" data-cx="stop" aria-label="Codex 중지" hidden>■</button><button type="submit" class="primary" id="cx-send" aria-label="Codex 전송">↑</button></footer>
      <div class="cx-composer-meta"><label><span class="studio-sr-only">작업 권한</span><select id="studio-codex-mode" title="작업 권한"><option value="read-only">읽기 · 분석</option><option value="workspace-write">파일 수정 허용</option></select></label><small id="cx-usage" title="토큰 사용량">컨텍스트 사용량 대기</small></div></form>`;
    function status(text){$('#cx-status').textContent=text;}
    function reset(){thread=null;contexts=[];loaded=false;models=[];items.clear();$('#cx-usage').textContent='컨텍스트 사용량 대기';renderThread();renderContexts();$('#studio-context').textContent=host.project()?.root||'작업 폴더';}
    function renderThread(){
      items.clear();$('#studio-conversation').replaceChildren();
      $('#cx-title').textContent=thread?.name||thread?.preview||'새 세션';$('#cx-thread-id').textContent=thread?.id?.slice(0,8)||'';
      for(const turn of thread?.turns||[]){for(const item of turn.items||[])renderItem(item);if(turn.error)renderItem({id:turn.id+'-error',type:'오류',text:turn.error});}
      if(!items.size)$('#studio-conversation').innerHTML='<div class="assistant-welcome"><span>✦</span><h3>무엇을 만들까요?</h3><p>파일을 첨부하거나 프로젝트에 관해 질문하세요.</p><small>대화는 선택한 서버에 저장됩니다.</small></div>';
    }
    function renderItem(item){
      $('#studio-conversation .assistant-welcome')?.remove();
      let row=items.get(item.id);
      if(!row){row=document.createElement('article');row.className='cx-message';items.set(item.id,row);$('#studio-conversation').append(row);}
      const titles={userMessage:'나',agentMessage:'Codex',commandExecution:'터미널',fileChange:'파일 변경',reasoning:'진행 요약',plan:'계획',contextCompaction:'컨텍스트 압축'};
      row.dataset.kind=item.type;row.replaceChildren();
      const heading=document.createElement('small');heading.textContent=(titles[item.type]||item.type)+(item.status?' · '+item.status:'');row.append(heading);
      if(item.text){const text=document.createElement('div');text.className='cx-message-text';if(item.type==='agentMessage')markdown(text,item.text);else text.textContent=item.text;row.append(text);}
      if(item.command||item.output){const details=document.createElement('details');const summary=document.createElement('summary');summary.textContent=item.command||'명령 출력';details.append(summary);const pre=document.createElement('pre');pre.textContent=item.output||'실행 중…';details.append(pre);row.append(details);}
      for(const file of item.files||[]){const details=document.createElement('details');const summary=document.createElement('summary');summary.textContent=file.path;const pre=document.createElement('pre');pre.textContent=file.diff;details.append(summary,pre);row.append(details);}
      const log=$('#studio-conversation');if(log.scrollHeight-log.scrollTop-log.clientHeight<300)log.scrollTop=log.scrollHeight;
    }
    function markdown(target,source){
      // Code fences are DOM text nodes; model output never becomes executable HTML.
      const expression=/```([^\n]*)\n([\s\S]*?)```/g;let offset=0,match;
      const prose=value=>{const block=document.createElement('div');block.textContent=value;target.append(block);};
      while((match=expression.exec(source))){prose(source.slice(offset,match.index));const block=document.createElement('div');block.className='cx-code';const label=document.createElement('small');label.textContent=match[1]||'code';const pre=document.createElement('pre');pre.textContent=match[2];const copy=document.createElement('button');copy.type='button';copy.textContent='복사';copy.onclick=()=>guard(async()=>{if(navigator.clipboard&&window.isSecureContext)await navigator.clipboard.writeText(matchText);else{const field=document.createElement('textarea');field.value=matchText;document.body.append(field);field.select();const ok=document.execCommand('copy');field.remove();if(!ok)throw Error('코드를 선택해 복사해 주세요.');}copy.textContent='복사됨';});const matchText=match[2];block.append(label,copy,pre);target.append(block);offset=expression.lastIndex;}prose(source.slice(offset));
    }
    function renderContexts(){$('#cx-contexts').innerHTML=contexts.map((c,i)=>`<button type="button" data-cx="remove" data-index="${i}" title="첨부 제거">${esc(c.name||c.path)} ×</button>`).join('');}
    function effort(){const model=models.find(m=>m.id===$('#studio-codex-model').value);$('#cx-effort').innerHTML=(model?.efforts||[]).map(e=>`<option value="${esc(e.reasoningEffort)}" ${e.reasoningEffort===model.defaultEffort?'selected':''}>${esc(e.reasoningEffort)}</option>`).join('')||'<option value="">기본</option>';}
    async function run(action,args={}){
      if(host.busy())throw Error('진행 중인 작업이 끝난 뒤 실행해 주세요.');
      if(!host.project())throw Error('먼저 작업 폴더를 열어 주세요.');
      host.setBusy(true);sequence=0;status('연결 중…');
      try{
        let job=await host.api('/studio/jobs','POST',{...host.project(),action,args});running=job.id;host.job?.(job.id);
        const interactive=['codex-run','codex-review','codex-thread-compact'].includes(action);
        if(interactive){panel.querySelectorAll('#studio-prompt, #cx-send, [data-cx="stop"]').forEach(x=>x.disabled=false);$('[data-cx="stop"]').hidden=false;$('#cx-send').textContent='↑';$('#cx-send').setAttribute('aria-label','추가 지시 보내기');host.publish({codex:'실행 중'});}
        for(;;){
          for(const event of job.events||[])if(event.assistant&&event.assistant.sequence>sequence){sequence=event.assistant.sequence;receive(event.assistant);}
          if(job.state!=='RUNNING')break;
          await new Promise(resolve=>setTimeout(resolve,400));job=await host.api('/studio/jobs/'+job.id);
        }
        if(job.state!=='SUCCEEDED')throw Error(job.error||'작업이 중지되었습니다.');
        const result=job.result?.assistant||{};
        if(result.thread){if(thread?.id!==result.thread.id)$('#cx-usage').textContent='컨텍스트 사용량 대기';thread=result.thread;renderThread();remember();}
        status(result.status==='failed'?'요청 실패 · 대화의 오류를 확인하세요.':result.status==='interrupted'?'중지됨':'완료');host.publish({codex:result.status||'완료'});
        return result;
      }finally{running=null;host.job?.(null);host.setBusy(false);$('[data-cx="stop"]').hidden=true;$('#cx-send').textContent='↑';$('#cx-send').setAttribute('aria-label','Codex 전송');$('#cx-interactions').replaceChildren();}
    }
    function remember(){try{sessionStorage.setItem('studio-codex:'+JSON.stringify(host.project()),thread?.id||'');}catch{}}
    async function control(value){if(!running)throw Error('진행 중인 요청이 없습니다.');await host.api('/studio/jobs/'+running+'/inputs','POST',value);}
    function receive(event){
      if(event.threadId&&!thread?.id){thread={id:event.threadId,turns:[]};remember();}
      if(event.kind==='item')renderItem(event.item);
      if(event.kind==='started')status('Codex 작업 중…');
      if(event.kind==='usage'){const u=event.usage;$('#cx-usage').textContent=`컨텍스트 ${u.contextTokens?.toLocaleString()||'—'} / ${u.contextWindow?.toLocaleString()||'—'} · 총 ${u.totalTokens?.toLocaleString()||'—'} 토큰`;}
      if(['notice','plan','diff'].includes(event.kind))renderItem({id:event.kind,type:event.kind,text:event.text});
      if(event.kind==='answered')panel.querySelector(`[data-request="${CSS.escape(event.requestId)}"]`)?.remove();
      if(event.kind==='interaction'){
        const request=event.interaction, box=document.createElement('form');box.className='cx-approval';box.dataset.request=request.id;
        const title=document.createElement('p');title.textContent=request.reason;box.append(title);
        if(request.command){const pre=document.createElement('pre');pre.textContent=request.command;box.append(pre);}
        if(request.kind==='approval'){
          for(const [decision,label]of [['accept','한 번 허용'],['acceptForSession','세션 동안 허용'],['decline','거절']]){const button=document.createElement('button');button.type='button';button.textContent=label;button.onclick=()=>guard(async()=>{await control({type:'approval',requestId:request.id,decision});box.remove();});box.append(button);}
        }else{
          for(const q of request.questions||[]){const label=document.createElement('label');label.textContent=q.question;const input=document.createElement('input');input.name=q.id;input.type=q.secret?'password':'text';input.maxLength=4000;input.required=true;label.append(input);if(q.options?.length){const hint=document.createElement('small');hint.textContent=q.options.map(o=>o.label+': '+o.description).join(' · ');label.append(hint);}box.append(label);}
          const button=document.createElement('button');button.textContent='답변 보내기';box.append(button);box.onsubmit=event=>{event.preventDefault();event.stopPropagation();guard(async()=>{const answers=Object.fromEntries([...new FormData(box)].map(([k,v])=>[k,[v]]));await control({type:'answer',requestId:request.id,answers});box.remove();});};
        }
        $('#cx-interactions').append(box);status('입력을 기다리고 있습니다.');
      }
    }
    async function load(){if(loaded||host.busy()||!host.project())return;const result=await run('codex-models');models=result.models||[];$('#studio-codex-model').innerHTML=models.map(m=>`<option value="${esc(m.id)}" ${m.defaultModel?'selected':''}>${esc(m.name)}</option>`).join('')||'<option value="">CLI 기본 모델</option>';effort();const auth=await run('codex-account');host.auth(auth.authenticated);loaded=true;let saved;try{saved=sessionStorage.getItem('studio-codex:'+JSON.stringify(host.project()));}catch{}if(saved&&!thread)try{await run('codex-thread-read',{threadId:saved});}catch{status('이전 세션을 불러오지 못했습니다. 목록에서 다시 선택하세요.');}}
    async function history(more=false){$('#cx-history').hidden=false;const result=await run('codex-threads',{query:$('#cx-search input').value,archived,cursor:more?cursor:null});if(!more)$('#cx-sessions').replaceChildren();for(const t of result.threads||[]){const row=document.createElement('button');row.className='cx-session';row.textContent=(t.name||t.preview||'제목 없는 세션')+' · '+new Date(t.updatedAt*1000).toLocaleDateString();row.onclick=()=>guard(async()=>{if(archived)await run('codex-thread-unarchive',{threadId:t.id});await run('codex-thread-read',{threadId:t.id});$('#cx-history').hidden=true;});$('#cx-sessions').append(row);}cursor=result.nextCursor;$('[data-cx="more"]').hidden=!cursor;if(!$('#cx-sessions').children.length)$('#cx-sessions').textContent='저장된 세션이 없습니다.';}
    async function send(){
      const prompt=$('#studio-prompt').value.trim();if(!prompt)return;
      if(running){await control({type:'steer',text:prompt});$('#studio-prompt').value='';status('추가 지시를 보냈습니다.');return;}
      if(prompt==='/new'){await action('new');$('#studio-prompt').value='';return;}
      if(prompt==='/compact'){await action('compact');$('#studio-prompt').value='';return;}
      if(prompt==='/history'){await history();return;}
      if(prompt==='/help'){status('/new 새 세션 · /history 세션 목록 · /compact 컨텍스트 압축. 모델과 권한은 입력창 아래에서 선택하세요.');return;}
      if(host.dirty())throw Error('서버 파일과 일치하도록 편집 내용을 먼저 저장해 주세요.');
      const args={threadId:thread?.id,prompt,model:$('#studio-codex-model').value,effort:$('#cx-effort').value,mode:$('#studio-codex-mode').value,context:[...contexts]};
      $('#studio-prompt').value='';renderItem({id:'pending-'+Date.now(),type:'userMessage',text:prompt});
      try{await run('codex-run',args);contexts=[];renderContexts();}catch(error){$('#studio-prompt').value=prompt;throw error;}
    }
    async function action(name,button){
      if(name==='stop'){await control({type:'interrupt'});status('중지 요청을 보냈습니다.');return;}
      if(name==='history')return history();if(name==='more')return history(true);if(name==='history-close'){$('#cx-history').hidden=true;return;}
      if(name==='review'){if(host.dirty())throw Error('편집 내용을 먼저 저장하세요.');return run('codex-review',{threadId:thread?.id,model:$('#studio-codex-model').value,mode:'read-only'});}
      if(name==='connections'){const result=await run('codex-connections');status(result.connections?.map(c=>c.name+': '+c.status).join(' · ')||'등록된 MCP 서버가 없습니다.');return;}
      if(name==='skills'){const result=await run('codex-skills');const skills=(result.skills||[]).filter(s=>s.enabled);if(!skills.length){status('활성화된 스킬이 없습니다.');return;}host.editor('스킬 첨부','<label>스킬<select name="skill">'+skills.map((s,i)=>'<option value="'+i+'">'+esc(s.name)+' — '+esc(s.description)+'</option>').join('')+'</select></label>',async form=>{const skill=skills[Number(form.get('skill'))];contexts.push({kind:'skill',name:skill.name,path:skill.path});renderContexts();});return;}
      if(name==='refresh'){loaded=false;return load();}
      if(name==='new'){await run('codex-thread-new',{model:$('#studio-codex-model').value,mode:$('#studio-codex-mode').value});contexts=[];renderContexts();$('#cx-history').hidden=true;return;}
      if(['rename','fork','compact','archive','rollback'].includes(name)){
        if(!thread)throw Error('먼저 세션을 선택해 주세요.');
        if(name==='rename'){host.editor('세션 이름',`<label>이름<input name="name" maxlength="200" required value="${esc(thread.name||'')}"></label>`,async form=>{await run('codex-thread-rename',{threadId:thread.id,name:form.get('name')});thread.name=form.get('name');renderThread();});return;}
        if(name==='rollback'&&!await host.confirm('마지막 대화를 기록에서 제거할까요? 이미 변경된 파일은 복구되지 않습니다.'))return;
        await run('codex-thread-'+name,{threadId:thread.id});if(name==='archive'){thread=null;remember();renderThread();}if(name==='rollback')await run('codex-thread-read',{threadId:thread.id});return;
      }
      if(name==='remove'){contexts.splice(Number(button.dataset.index),1);renderContexts();return;}
      if(contexts.length>=16)throw Error('컨텍스트는 최대 16개까지 첨부할 수 있습니다.');
      if(name==='image'){$('#cx-image').click();return;}
      if(name==='path'){host.editor('파일 첨부','<label>프로젝트 기준 경로<input name="path" required maxlength="4096"></label>',async form=>{const path=form.get('path');contexts.push({kind:'file',path,name:path});renderContexts();});return;}
      if(name==='file'||name==='selection'){const context=host.context(name);if(!context)throw Error(name==='file'?'먼저 파일을 여세요.':'에디터에서 텍스트를 선택하세요.');contexts.push(context);renderContexts();}
    }
    async function guard(work){try{await work();}catch(error){status(error.message);host.toast(error.message);}}
    panel.addEventListener('click',event=>{const button=event.target.closest('[data-cx]');if(button){event.stopPropagation();button.closest('details')?.removeAttribute('open');guard(()=>action(button.dataset.cx,button));}});
    panel.addEventListener('submit',event=>{event.preventDefault();event.stopPropagation();if(event.target.id==='studio-prompt-form')guard(send);if(event.target.id==='cx-search')guard(()=>history());});
    $('#studio-prompt').addEventListener('keydown',event=>{if(event.key==='Enter'&&!event.shiftKey&&!event.isComposing){event.preventDefault();guard(send);}});
    $('#studio-codex-model').addEventListener('change',effort);$('#cx-archived').onchange=()=>{archived=$('#cx-archived').checked;guard(()=>history());};
    $('#studio-codex-mode').onchange=()=>{$('#studio-mode-label').textContent=$('#studio-codex-mode').selectedOptions[0].textContent;};
    $('#cx-image').onchange=()=>guard(async()=>{const file=$('#cx-image').files[0];if(!file)return;if(file.size>2000000||!['image/png','image/jpeg','image/webp'].includes(file.type))throw Error('2 MB 이하의 PNG, JPEG, WebP를 선택하세요.');const dataUrl=await new Promise((resolve,reject)=>{const r=new FileReader();r.onload=()=>resolve(r.result);r.onerror=reject;r.readAsDataURL(file);});contexts.push({kind:'image',name:file.name,dataUrl});renderContexts();$('#cx-image').value='';});
    reset();return {reset,load:()=>guard(load)};
  };
})();
