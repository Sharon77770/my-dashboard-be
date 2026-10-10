 'use strict';
/** Communications owns message panes; provider facts and authorization remain server-side. */
window.WorkspaceCommunications=(()=>{
  let ui,root,accounts=[],providers=[],actions=[],filter='',conversations=[],panes=[],selected=0,loading=false,lastRefresh=0,sequence=0,threadView=null,searchView=null,participantsView=null,cacheRefreshing=false,cachePending=false,loaded=false,refreshError='',detailsOpen=false,listQuery='';
  const connectionStates=new Map();
  const drafts=new Map(),files=new Map(),pages=new Map();
  const e=value=>ui.escape(value??''),url=value=>encodeURIComponent(value??'');
  const key=item=>`${item.accountId}:${item.id}`;
  const account=id=>accounts.find(item=>item.id===id);
  const storageKey=()=>`workspace-communications-v1:${encodeURIComponent(document.body.dataset.account||'owner')}`;
  const paint=(node,html)=>window.WorkspaceLiveDOM?window.WorkspaceLiveDOM.patch(node,html):node.innerHTML=html;
  async function openRemote(provider){
    if(!['SLACK','DISCORD','KAKAOTALK'].includes(provider))return;
    const profiles=await ui.api('/communications/bridge/profiles');
    const matching=profiles.filter(profile=>profile.provider===provider);
    if(matching.length>1){await manage();return;}
    const label={SLACK:'Slack',DISCORD:'Discord',KAKAOTALK:'카카오톡'}[provider];
    const profile=matching[0]||await ui.api('/communications/bridge/profiles','POST',{provider,label});
    await window.WorkspaceAuthenticationBrowser.open({title:label,sessionFactory:()=>ui.api(`/communications/bridge/profiles/${url(profile.id)}/sessions`,'POST',{})});
  }
  async function uploads(input){
    const chosen=[...input.files];if(chosen.length>5||chosen.reduce((sum,file)=>sum+file.size,0)>5*1024*1024)throw new Error('첨부파일은 최대 5개, 총 5 MiB입니다.');
    return Promise.all(chosen.map(file=>new Promise((resolve,reject)=>{const reader=new FileReader();reader.onerror=()=>reject(new Error('파일을 읽지 못했습니다.'));reader.onload=()=>resolve({name:file.name,mediaType:file.type||({txt:'text/plain',csv:'text/csv',json:'application/json'}[file.name.split('.').at(-1)]||'application/octet-stream'),data:String(reader.result).split(',')[1]});reader.readAsDataURL(file);})));
  }
  const fileInput=()=>'<label class="comm-file-label">파일 첨부<input type="file" name="attachments" multiple accept=".txt,.csv,.json,.pdf,.png,.jpg,.jpeg"></label>';
  const conversationKinds=new Set(['MAIL','CHANNEL','DM','GROUP','THREAD']);
  function save(){try{sessionStorage.setItem(storageKey(),JSON.stringify({version:2,panes:panes.map(({accountId,id,title,kind})=>({accountId,id,title,kind:conversationKinds.has(kind)?kind:''})),selected:panes[selected]?key(panes[selected]):'',split:root.classList.contains('comm-split')}));}catch{}}
  function restore(){
    try{
      const saved=JSON.parse(sessionStorage.getItem(storageKey())||'[]'),items=Array.isArray(saved)?saved:saved?.version===2?saved.panes:[];
      if(!Array.isArray(items))return;
      const unique=new Map();
      for(const item of items){
        if(!item||typeof item.accountId!=='string'||!account(item.accountId)||typeof item.id!=='string'||!item.id||item.id.length>256||typeof item.title!=='string'||item.title.length>1000)continue;
        unique.set(key(item),{accountId:item.accountId,id:item.id,title:item.title,kind:conversationKinds.has(item.kind)?item.kind:''});
        if(unique.size===6)break;
      }
      panes=[...unique.values()];selected=Math.max(0,panes.findIndex(pane=>key(pane)===saved?.selected));
      root.classList.toggle('comm-split',panes.length>1&&saved?.split===true);
    }catch{}
  }
  const icon=name=>window.WorkspaceUI?.icon(name)||'';
  const serviceName=value=>({GMAIL:'Gmail',SLACK:'Slack',DISCORD:'Discord',KAKAOTALK:'카카오톡'})[value]||value||'계정';
  function serviceMark(provider){return `<span class="comm-service-mark" data-service="${e(provider)}" aria-hidden="true">${e(({GMAIL:'M',SLACK:'#',DISCORD:'D',KAKAOTALK:'T'})[provider]||'•')}</span>`;}
  function timeLabel(value){if(!value||!Number.isFinite(new Date(value).getTime()))return '';return new Intl.DateTimeFormat('ko-KR',new Date(value).toDateString()===new Date().toDateString()?{hour:'2-digit',minute:'2-digit'}:{month:'short',day:'numeric'}).format(new Date(value));}
  function stateView(kind,title,description,action=''){
    return `<div class="comm-state comm-state-${kind}" data-comm-state="${kind}"><span class="comm-state-icon">${icon(({loading:'refresh',error:'warning',permission:'lock',empty:'search',welcome:'talk',remote:'remote'})[kind]||'talk')}</span><h2>${e(title)}</h2><p>${e(description)}</p>${action}</div>`;
  }
  function status(message,tone='neutral'){const node=root.querySelector('[data-comm-status]');if(node){node.textContent=message;node.dataset.tone=tone;node.hidden=!message;}}
  function shell(){
    if(root.querySelector('.comm-shell'))return;
    root.dataset.messageOpen='false';root.dataset.detailsOpen='false';
    root.innerHTML=`<div class="comm-shell"><div class="comm-body"><aside class="comm-sidebar" aria-label="계정과 대화"><header class="comm-sidebar-head"><h1>Communications</h1><div class="comm-head-actions"><button class="ghost icon-btn" data-comm="new" aria-label="새 메일" title="새 메일">${icon('edit')}</button><button class="ghost icon-btn" data-comm="refresh" aria-label="새로고침" title="새로고침">${icon('refresh')}</button><button class="ghost icon-btn" data-comm="accounts" aria-label="계정 관리" title="계정 관리">${icon('settings')}</button><button class="comm-approval-toggle ghost" data-comm="details" hidden aria-label="전송 승인 목록"><span data-approval-count></span></button></div></header><div class="comm-accounts"></div><div class="comm-search-area"><label class="comm-list-search">${icon('search')}<input data-comm-list-search type="search" placeholder="대화 찾기" aria-label="대화 목록 검색" maxlength="500"></label><details class="comm-search-options"><summary>메시지 검색</summary><form data-comm-search><select name="scope" aria-label="검색 범위"><option value="">저장된 메시지</option></select><div><input name="query" type="search" placeholder="저장된 메시지 검색" aria-label="통합 검색" maxlength="500"><button class="ghost icon-btn" aria-label="메시지 검색 실행">${icon('search')}</button></div></form></details></div><section class="comm-list" aria-label="대화 목록"></section></aside><section class="comm-work" aria-label="대화 작업 영역"><nav class="comm-tabs" aria-label="대화 탭"></nav><div class="comm-panes"></div><p data-comm-status role="status" aria-live="polite" hidden></p></section><aside class="comm-details" id="comm-details" aria-label="대화 상세 정보" hidden></aside></div></div>`;
  }
  function renderAccounts(){
    const current=account(filter);
    paint(root.querySelector('.comm-accounts'),accounts.length?`<details class="comm-account-switch"><summary>${current?serviceMark(current.provider):icon('talk')}<span>${e(current?.label||'모든 계정')}<small>${current?e(serviceName(current.provider)):`연결된 계정 ${accounts.length}개`}</small></span>${icon('menu')}</summary><div><button data-comm="filter" data-id="" aria-pressed="${!filter}">${icon('talk')}<span>모든 계정</span></button>${accounts.map(item=>`<button data-comm="filter" data-id="${e(item.id)}" aria-pressed="${filter===item.id}">${serviceMark(item.provider)}<span>${e(item.label)}<small>${e(serviceName(item.provider))} · ${e(connectionStates.get(item.id)?.error?'확인 필요':connectionStates.get(item.id)?.ready?'조회 완료':'조회 대기')}</small></span>${connectionStates.get(item.id)?.error?icon('warning'):''}</button>`).join('')}<button data-comm="accounts">${icon('plus')}계정 연결 · 관리</button></div></details><nav class="comm-remote-shortcuts" aria-label="원격 메신저">${['KAKAOTALK','SLACK','DISCORD'].map(provider=>`<button class="ghost" data-comm-remote="${provider}" title="${e(serviceName(provider))} 원격 화면">${serviceMark(provider)}<span>${e(serviceName(provider))}</span></button>`).join('')}</nav>`:'');
  }
  function renderList(){
    const visible=conversations.filter(item=>!listQuery||`${item.title||''} ${item.preview||''}`.toLocaleLowerCase().includes(listQuery.toLocaleLowerCase()));
    const failure=refreshError||accounts.filter(item=>!filter||item.id===filter).map(item=>connectionStates.get(item.id)).find(item=>item?.error)?.error;
    const rows=visible.map(item=>`<button class="comm-conversation" data-comm="conversation" data-key="${e(key(item))}" aria-pressed="${!!panes[selected]&&key(panes[selected])===key(item)}">${serviceMark(item.provider||account(item.accountId)?.provider)}<span class="comm-conversation-copy"><span class="comm-conversation-top"><strong>${e(item.title||item.id)}</strong><time>${e(timeLabel(item.updatedAt))}</time></span><span class="comm-conversation-preview">${e(item.preview||({MAIL:'메일 스레드',DM:'다이렉트 메시지',GROUP:'그룹 대화',THREAD:'스레드',CHANNEL:'채널'})[item.kind]||'대화')}</span><span class="comm-conversation-meta">${e(account(item.accountId)?.label||serviceName(item.provider))}${item.unread===true?'<span class="comm-unread" aria-label="읽지 않음" title="읽지 않음 · 메시지 수는 제공되지 않음">읽지 않음</span>':''}</span></span></button>`).join('');
    paint(root.querySelector('.comm-list'),`<div class="comm-list-heading"><h2>${listQuery?'검색 결과':'대화'}</h2><span>${visible.length}</span>${loading?'<span class="comm-loading-dot" aria-label="조회 중"></span>':''}</div>${failure?`<div class="comm-inline-error" role="alert">${icon('warning')}<span>${e(failure)}</span><button class="ghost" data-comm="refresh">다시 시도</button></div>`:''}${rows||stateView(loading?'loading':listQuery?'empty':'idle',loading?'대화를 불러오는 중':listQuery?'일치하는 대화가 없습니다':'아직 대화가 없습니다',listQuery?'다른 이름이나 단어로 찾아보세요.':'계정을 선택하거나 새로고침해 보세요.')}${[...pages].filter(([id,cursor])=>cursor&&(!filter||id===filter)).map(([id])=>`<button class="ghost comm-load-more" data-comm="more-conversations" data-id="${e(id)}">${e(account(id)?.label)} 더 보기</button>`).join('')}`);
  }
  function render(){shell();root.classList.toggle('comm-onboarding',!accounts.length);renderAccounts();renderSearchScopes();renderList();renderPanes();renderDetails();}
  function renderSearchScopes(){
    const select=root.querySelector('[data-comm-search] select'),previous=select.value;
    paint(select,'<option value="">저장된 메시지 (모든 계정)</option>'+accounts.filter(item=>item.capabilities.includes('SEARCH')).map(item=>`<option value="${e(item.id)}">${e(item.provider)} · ${e(item.label)}</option>`).join(''));
    select.value=[...select.options].some(item=>item.value===previous)?previous:'';
    root.querySelector('[data-comm-search] input').placeholder=select.value?'메일 검색 · from:, is:unread':'저장된 메시지 검색';
  }
  async function openConversation(conversation){
    if(!conversation||!account(conversation.accountId))return;
    let found=panes.findIndex(item=>key(item)===key(conversation));
    if(found<0){if(panes.length>=6){ui.toast('대화 탭은 최대 6개입니다.');return;}panes.push({...conversation,messages:[]});found=panes.length-1;}
    selected=found;detailsOpen=false;root.dataset.messageOpen='true';save();render();await loadMessages(found,false);
  }
  async function loadSearch(view){
    if(view.loading)return;view.loading=true;
    try{
      const response=await ui.api(view.accountId?`/communications/accounts/${url(view.accountId)}/message-search?query=${url(view.query)}&cursor=${url(view.cursor)}`:`/communications/search?query=${url(view.query)}`);
      if(searchView!==view||(view.rendered&&!document.querySelector('[data-comm-search-results]')))return;
      const page=view.accountId?response:{items:response,nextCursor:''},merged=new Map(view.items.map(item=>[`${item.accountId}:${item.conversationId}:${item.id}`,item]));
      page.items.forEach(item=>merged.set(`${item.accountId}:${item.conversationId}:${item.id}`,item));view.items=[...merged.values()];view.cursor=page.nextCursor;
      ui.editor(view.accountId?'계정 메시지 검색':'저장된 메시지 검색',`<div data-comm-search-results><p>${e(view.query)} · ${view.accountId?'공식 Provider 검색':'조회한 캐시에서 최대 100개'}</p>${view.items.map((item,index)=>`<article><b>${e(item.provider)} · ${e(item.sender)}</b><p class="comm-preview">${e(item.text)}</p><button type="button" data-comm-search-open="${index}">대화 열기</button></article>`).join('')||'<p>검색 결과 없음</p>'}${view.cursor?'<button type="button" data-comm-search-more>검색 결과 더 보기</button>':''}</div>`,async()=>{searchView=null;},'닫기');view.rendered=true;
    }finally{view.loading=false;}
  }
  function emptyWorkspace(){
    if(!loaded&&loading)return stateView('loading','대화를 준비하고 있어요','연결된 계정과 최근 대화를 불러옵니다.');
    if(refreshError&&!accounts.length)return stateView('error','계정을 불러오지 못했습니다',refreshError,'<button class="primary" data-comm="refresh">다시 시도</button>');
    if(accounts.length)return stateView('idle','대화를 이어가세요','왼쪽에서 대화를 선택하세요. 여러 대화를 탭이나 분할 화면으로 함께 볼 수 있습니다.','<button class="ghost" data-comm="new">'+icon('edit')+' 새 메일 작성</button>');
    return `<div class="comm-onboarding-content"><span class="comm-welcome-symbol">${icon('talk')}</span><span class="comm-eyebrow">YOUR CONVERSATIONS, ONE PLACE</span><h2>대화가 모이는 나의 공간</h2><p>메일을 연결하거나 익숙한 메신저 화면을 열어보세요.<br>로그인과 대화는 나의 Workspace 안에서 이어집니다.</p><div class="comm-connect-options"><button ${providers.find(item=>item.id==='GMAIL')?.configured?'data-comm-connect="GMAIL"':'data-comm="accounts"'}>${serviceMark('GMAIL')}<strong>Gmail 연결</strong><span>메일과 스레드를 한곳에서</span>${icon('plus')}</button><button data-comm-remote="SLACK">${serviceMark('SLACK')}<strong>Slack 열기</strong><span>워크스페이스에 직접 로그인</span>${icon('remote')}</button><button data-comm-remote="DISCORD">${serviceMark('DISCORD')}<strong>Discord 열기</strong><span>채널과 대화를 원래 화면에서</span>${icon('remote')}</button><button data-comm-remote="KAKAOTALK">${serviceMark('KAKAOTALK')}<strong>카카오톡 열기</strong><span>서버 앱에서 이어가는 대화</span>${icon('remote')}</button></div><button class="ghost" data-comm="accounts">${icon('settings')} 계정 · Remote Bridge 관리</button><small>공식 API 계정 연결에는 서버 OAuth 설정이 필요합니다. 원격 화면에서는 직접 로그인하세요.</small></div>`;
  }
  function messageMarkup(message,pane,index,current){
    const canSend=current?.capabilities.includes('SEND'),email=current?.provider==='GMAIL';
    return `<article class="comm-message ${email?'comm-mail-message':''}" data-live-key="${e(message.id)}"><span class="comm-avatar" aria-hidden="true">${e((message.sender||'?').slice(0,2))}</span><div class="comm-message-content"><header><b>${e(message.sender||'발신자 미확인')}</b><time title="${e(message.timestamp?new Date(message.timestamp).toLocaleString():'시각 미제공')}">${e(timeLabel(message.timestamp)||'시각 미제공')}</time>${email&&message.unread!==null&&message.unread!==undefined?`<small class="comm-read-state">내 메일함: ${message.unread?'읽지 않음':'읽음'}</small>`:''}</header>${email?'<div class="comm-mail-meta">메일 스레드 · 수신자 정보는 원본 메일에서 확인</div>':''}<p>${e(message.text||'(텍스트 본문 없음)')}</p>${(message.attachments||[]).length?`<div class="comm-message-files">${message.attachments.map(file=>`<button data-comm="attachment" data-index="${index}" data-message="${e(message.id)}" data-attachment="${e(file.id)}" ${current?.capabilities.includes('ATTACHMENTS')?'':'disabled title="이 연결은 파일 다운로드를 지원하지 않습니다."'}>${icon('clip')}<span>${e(file.name)}<small>${e(file.size)} B</small></span></button>`).join('')}</div>`:''}${reactionMarkup(message)}<div class="comm-message-actions"><button class="ghost sm" data-comm="reply" data-index="${index}" data-message="${e(message.id)}" ${canSend?'':'disabled'}>${icon('back')} 답장</button>${email?`<button class="ghost sm" data-comm="forward" data-index="${index}" data-message="${e(message.id)}">전달</button>`:''}${current?.provider==='SLACK'&&['DM','GROUP'].includes(pane.kind)?`<button class="ghost sm" data-comm="thread" data-index="${index}" data-message="${e(message.threadId||message.id)}">${icon('talk')} 스레드</button>`:''}<details class="comm-message-menu"><summary aria-label="메시지 작업" title="메시지 작업">${icon('more')}</summary><div>${[['EDIT','수정'],['DELETE','삭제'],['REACTIONS','리액션']].map(([capability,label])=>`<button class="ghost sm" data-comm="mutation" data-operation="${capability==='REACTIONS'?'REACTION':capability}" data-index="${index}" data-message="${e(message.id)}" ${current?.capabilities.includes(capability)?'':'disabled title="이 연결은 해당 작업을 지원하지 않습니다."'}>${label}</button>`).join('')}</div></details></div></div></article>`;
  }
  function renderPanes(){
    paint(root.querySelector('.comm-tabs'),panes.map((pane,index)=>`<span class="comm-tab" data-live-key="tab:${e(key(pane))}" data-selected="${selected===index}"><button data-comm="tab" data-index="${index}" aria-pressed="${selected===index}">${e(pane.title)}</button><button data-comm="close" data-index="${index}" aria-label="${e(pane.title)} 대화 닫기" title="대화 닫기">${icon('close')}</button></span>`).join('')+(panes.length>1?`<button class="ghost" data-comm="split" aria-pressed="${root.classList.contains('comm-split')}" title="분할 보기">${icon('apps')}<span>분할</span></button>`:''));
    root.querySelector('.comm-tabs').hidden=!panes.length;
    paint(root.querySelector('.comm-panes'),panes.length?panes.map((pane,index)=>{
      const current=account(pane.accountId),draft=drafts.get(key(pane))||'',canSend=current?.capabilities.includes('SEND');
      const denied=/권한|permission|403|scope/i.test(pane.error||'');
      let day='';const messages=(pane.messages||[]).map(message=>{const next=message.timestamp?new Date(message.timestamp).toLocaleDateString('ko-KR',{month:'long',day:'numeric',weekday:'short'}):'';const divider=next&&next!==day?`<div class="comm-date-divider" data-live-key="date:${e(next)}"><span>${e(next)}</span></div>`:'';day=next;return divider+messageMarkup(message,pane,index,current);}).join('');
      return `<article class="comm-pane" data-live-key="${e(key(pane))}" data-pane="${index}" ${index!==selected&&!(root.classList.contains('comm-split')&&index===(selected+1)%panes.length)?'hidden':''}><header class="comm-conversation-header"><button class="comm-back ghost icon-btn" data-comm="back" aria-label="대화 목록으로">${icon('back')}</button>${serviceMark(current?.provider)}<div class="comm-conversation-title"><h2>${e(pane.title)}</h2><span>${e(current?.label)} · ${current?.provider==='GMAIL'?'메일 스레드':e(({CHANNEL:'채널',DM:'다이렉트 메시지',GROUP:'그룹 대화',THREAD:'스레드'})[pane.kind]||'대화')}</span></div><button class="ghost icon-btn" data-comm="participants" data-index="${index}" aria-label="참여자" title="참여자" ${current?.capabilities.includes('PARTICIPANTS')?'':'disabled'}>${icon('people')}</button><button class="ghost icon-btn" data-comm="details" data-index="${index}" aria-label="대화 상세 정보" aria-controls="comm-details" aria-expanded="${detailsOpen}" title="대화 상세 정보">${icon('menu')}</button></header><div class="comm-messages" role="log" aria-label="메시지" aria-busy="${!!pane.loading}" data-live-key="messages:${e(key(pane))}">${pane.error?`<div class="comm-inline-error" role="alert">${icon('warning')}<span>${e(pane.error)}</span><button class="ghost" data-comm="retry-messages" data-index="${index}">다시 시도</button></div>`:''}${pane.nextCursor?`<button class="ghost comm-load-more" data-comm="more-messages" data-index="${index}">이전 메시지</button>`:''}${messages||stateView(pane.loading?'loading':pane.error?(denied?'permission':'error'):'idle',pane.loading?'메시지를 불러오는 중':pane.error?(denied?'이 대화를 읽을 권한이 없습니다':'대화를 불러오지 못했습니다'):'첫 대화를 시작해 보세요',pane.error?'연결 상태와 서비스 접근 권한을 확인한 뒤 다시 시도하세요.':pane.loading?'잠시만 기다려 주세요.':'아직 표시할 메시지가 없습니다.')}</div><form class="comm-composer" data-compose="${index}"><div data-compose-context></div><textarea name="text" rows="2" maxlength="32000" aria-label="메시지 작성" placeholder="${current?.provider==='GMAIL'?'이 메일에 답장하기…':'메시지를 작성하세요…'}" ${canSend?'':'disabled'}>${e(draft)}</textarea><div data-compose-files></div><div class="comm-composer-tools">${current?.capabilities.includes('UPLOAD')?`<label class="comm-file-label" title="첨부파일 추가">${icon('clip')}<span>첨부</span><input type="file" name="attachments" aria-label="첨부파일 추가" multiple accept=".txt,.csv,.json,.pdf,.png,.jpg,.jpeg"></label>`:`<button class="ghost icon-btn" type="button" disabled title="이 연결은 파일 업로드를 지원하지 않습니다." aria-label="첨부파일 추가">${icon('clip')}</button>`}<span data-compose-status>${draft?'초안 · 이 창에 보관':'전송 전 내용을 확인합니다'}</span><button class="primary" ${canSend?'':'disabled'}>${icon('up')}<span>전송 내용 확인</span></button></div></form></article>`;
    }).join(''):emptyWorkspace());
    if(!panes.length)root.dataset.messageOpen='false';
    syncComposers();
  }
  function syncComposers(){
    for(const form of root.querySelectorAll('[data-compose]')){
      const pane=panes[Number(form.dataset.compose)];if(!pane)continue;
      const index=Number(form.dataset.compose),canSend=account(pane.accountId)?.capabilities.includes('SEND');
      const reply=(pane.messages||[]).find(message=>message.id===pane.replyTo);
      paint(form.querySelector('[data-compose-context]'),pane.replyTo?`<span class="comm-reply-context">${icon('back')}<span>${e(reply?.sender||'선택한 메시지')}에게 답장${reply?`<small>${e(reply.text.slice(0,120))}</small>`:''}</span><button type="button" class="ghost icon-btn" data-comm="clear-reply" data-index="${index}" aria-label="답장 취소">${icon('close')}</button></span>`:'');
      paint(form.querySelector('[data-compose-files]'),(files.get(key(pane))||[]).map((file,fileIndex)=>`<span class="comm-file-chip">${icon('clip')}${e(file.name)}<button type="button" class="ghost" data-comm="remove-file" data-index="${index}" data-file-index="${fileIndex}" aria-label="${e(file.name)} 첨부 취소">×</button></span>`).join(''));
      form.querySelector('textarea').disabled=!canSend;form.querySelector('button.primary').disabled=!canSend||!!pane.sending;
      form.querySelector('[data-compose-status]').textContent=!canSend?'이 연결은 메시지 전송을 지원하지 않습니다.':pane.sending?'승인 요청 준비 중…':drafts.get(key(pane))?'초안 · 이 창에 보관':'전송 전 내용을 확인합니다';
    }
  }
  function reactionMarkup(message){
    const items=message.reactions||[];
    return items.length?`<ul class="comm-reactions" aria-label="리액션">${items.map(item=>`<li>${e(item.label)}${item.count===null||item.count===undefined?'':` · ${e(item.count)}`}</li>`).join('')}</ul>`:'';
  }
  function renderDetails(){
    const pane=panes[selected],current=pane&&account(pane.accountId),panel=root.querySelector('.comm-details');
    const pending=actions.filter(action=>action.state==='PENDING');
    for(const button of root.querySelectorAll('.comm-approval-toggle')){button.hidden=!pending.length;button.querySelector('[data-approval-count]').textContent=`승인 ${pending.length}`;button.setAttribute('aria-label',`전송 승인 ${pending.length}개`);}
    panel.hidden=!detailsOpen;root.dataset.detailsOpen=String(detailsOpen);
    for(const button of root.querySelectorAll('[aria-controls=comm-details]'))button.setAttribute('aria-expanded',String(detailsOpen));
    paint(panel,`<header><h2>대화 상세</h2><button class="ghost icon-btn" data-comm="details-close" aria-label="상세 정보 닫기">${icon('close')}</button></header><div class="comm-details-scroll"><section><span class="comm-eyebrow">CONVERSATION</span><h3>${e(pane?.title||'전송 승인')}</h3><p>${e(current?.label||'대화를 선택하면 상세 정보가 표시됩니다.')}</p><button class="ghost" data-comm="participants" data-index="${selected}" ${current?.capabilities.includes('PARTICIPANTS')?'':'disabled'}>${icon('people')} 참여자 보기</button></section><section><h3>첨부파일</h3>${(pane?.messages||[]).flatMap(message=>(message.attachments||[]).map(file=>`<button class="comm-detail-file ghost" data-comm="attachment" data-index="${selected}" data-message="${e(message.id)}" data-attachment="${e(file.id)}" ${current?.capabilities.includes('ATTACHMENTS')?'':'disabled'}>${icon('clip')}<span>${e(file.name)}</span></button>`)).join('')||'<p>현재 조회한 메시지에 첨부파일이 없습니다.</p>'}</section>${current?.provider==='GMAIL'?`<section><h3>메일 관리</h3><button class="ghost" data-comm="sync">동기화</button>${current.capabilities.includes('READ_STATE')?'<button class="ghost" data-comm="read">읽음으로 표시</button>':''}${current.capabilities.includes('LABELS')?'<button class="ghost" data-comm="labels">라벨</button>':''}</section>`:''}<section class="comm-approvals"><h3>전송 승인 <span>${pending.length}</span></h3>${actions.map(action=>`<div class="comm-pending-action"><small>${e(action.provider)} · ${e(action.accountLabel)}</small><p>${e(action.message.text.slice(0,180))}</p><span class="comm-action-state">${e({PENDING:'승인 대기',SENDING:'전송 중 · 재전송 금지',SENT:'작업 완료',UNKNOWN:'결과 미확인 · 원본 앱 확인',CANCELLED:'취소됨'}[action.state])}</span>${action.state==='PENDING'?`<div><button class="primary sm" data-comm="review" data-id="${e(action.id)}">내용 확인</button><button class="ghost sm" data-comm="cancel" data-id="${e(action.id)}">취소</button></div>`:''}</div>`).join('')||'<p>확인이 필요한 전송 요청이 없습니다.</p>'}</section><section class="comm-ai-section">${icon('codex')}<h3>AI와 함께</h3><p>대화를 요약하거나 답장 초안을 준비하세요. 실제 발송에는 승인이 필요합니다.</p><button class="ghost" data-view="assistant">AI 비서 열기</button></section></div>`);
  }
  async function refresh(force=false){
    if(loading||(!force&&Date.now()-lastRefresh<60000))return;loading=true;refreshError='';const current=++sequence;render();
    try{
      [accounts,providers,actions]=await Promise.all([ui.api('/communications/accounts'),ui.api('/communications/providers'),ui.api('/communications/actions')]);
      panes=panes.filter(pane=>account(pane.accountId));if(filter&&!account(filter))filter='';
      loaded=true;
      if(!panes.length){restore();if(panes.length)root.dataset.messageOpen='true';}
      const next=[],errors=[];pages.clear();
      for(const item of accounts.filter(item=>!filter||item.id===filter)){
        try{const page=await ui.api(`/communications/accounts/${url(item.id)}/conversations`,'GET',undefined,{quiet:true});next.push(...page.items);pages.set(item.id,page.nextCursor);connectionStates.set(item.id,{ready:true});}catch(error){errors.push(`${item.label}: ${error.message}`);connectionStates.set(item.id,{error:error.message});next.push(...conversations.filter(conversation=>conversation.accountId===item.id));}
      }
      if(current!==sequence)return;conversations=next;for(const pane of panes){const currentConversation=next.find(item=>key(item)===key(pane));if(currentConversation){pane.kind=currentConversation.kind;pane.title=currentConversation.title;}}selected=Math.min(selected,Math.max(0,panes.length-1));render();
      for(let index=0;index<panes.length;index++){try{await loadMessages(index,false);}catch(error){errors.push(error.message);}}
      status(errors.length?errors.join(' · '):'',errors.length?'error':'neutral');lastRefresh=Date.now();
    }catch(error){refreshError=error.message;status(error.message,'error');}finally{loading=false;render();}
  }
  async function refreshCache(){
    if(!root||(!root.classList.contains('active')&&!panes.length))return;
    if(cacheRefreshing){cachePending=true;return;}cacheRefreshing=true;
    try{
      actions=await ui.api('/communications/actions','GET',undefined,{quiet:true});renderDetails();let additions=0;
      for(const pane of [...panes]){
        const previous=pane.messages,known=new Set((previous||[]).map(item=>item.id));
        const newest=Math.max(...(previous||[]).map(item=>item.timestamp??-Infinity));
        const messages=await ui.api(`/communications/accounts/${url(pane.accountId)}/cached-messages?conversationId=${url(pane.id)}`,'GET',undefined,{quiet:true});
        if(!panes.includes(pane)||pane.messages!==previous)continue;
        if(Number.isFinite(newest))additions+=messages.filter(item=>!known.has(item.id)&&item.timestamp!==null&&item.timestamp>newest).length;
        pane.messages=messages;
      }
      renderPanes();renderDetails();
      if(additions){const notice=`열어둔 대화에 새 메시지 ${additions}개가 추가되었습니다.`;if(root.classList.contains('active'))status(notice);else ui.toast(notice);}
    }catch{}finally{cacheRefreshing=false;if(cachePending){cachePending=false;refreshCache();}}
  }
  async function loadMessages(index,older){
    const pane=panes[index];if(!pane||pane.loading)return;pane.loading=true;pane.error='';renderPanes();
    try{
      const page=await ui.api(`/communications/accounts/${url(pane.accountId)}/messages?conversationId=${url(pane.id)}&cursor=${url(older?pane.nextCursor:'')}`,'GET',undefined,{quiet:true});
      if(!panes.includes(pane))return;
      const merged=new Map((older?pane.messages||[]:[]).map(message=>[message.id,message]));page.items.forEach(message=>merged.set(message.id,message));
      pane.messages=[...merged.values()].sort((a,b)=>(a.timestamp??Infinity)-(b.timestamp??Infinity)||a.id.localeCompare(b.id));pane.nextCursor=page.nextCursor;
    }catch(error){pane.error=error.message;throw error;}finally{pane.loading=false;renderPanes();renderDetails();}
  }
  async function loadThread(view){
    if(view.loading)return;view.loading=true;
    try{
      const page=await ui.api(`/communications/accounts/${url(view.pane.accountId)}/thread-messages?conversationId=${url(view.pane.id)}&threadId=${url(view.id)}&cursor=${url(view.cursor)}`);
      if(threadView!==view||!panes.includes(view.pane)||(view.rendered&&!document.querySelector('[data-comm-thread-content]')))return;
      const merged=new Map(view.items.map(item=>[item.id,item]));page.items.forEach(item=>merged.set(item.id,item));
      view.items=[...merged.values()].sort((a,b)=>(a.timestamp??Infinity)-(b.timestamp??Infinity)||a.id.localeCompare(b.id));view.cursor=page.nextCursor;
      ui.editor('스레드','<div data-comm-thread-content>'+view.items.map(message=>`<article><b>${e(message.sender)}</b><p class="comm-preview">${e(message.text)}</p></article>`).join('')+(view.cursor?'<button type="button" data-comm-thread-more>다음 답글 더 보기</button>':'')+'</div>',async()=>{threadView=null;},'닫기');view.rendered=true;
    }finally{view.loading=false;}
  }
  async function loadParticipants(view){
    if(view.loading)return;view.loading=true;
    try{
      const page=await ui.api(`/communications/accounts/${url(view.pane.accountId)}/participants?conversationId=${url(view.pane.id)}&cursor=${url(view.cursor)}`);
      if(participantsView!==view||!panes.includes(view.pane)||(view.rendered&&!document.querySelector('[data-comm-participant-list]')))return;
      const unique=new Map(view.items.map(item=>[item.id,item]));page.items.forEach(item=>unique.set(item.id,item));view.items=[...unique.values()];view.cursor=page.nextCursor;
      ui.editor('대화 참여자',`<div data-comm-participant-list><p>Provider가 확인한 사용자 ID입니다. 표시 이름은 추정하지 않습니다.</p><ul>${view.items.map(item=>`<li>${e(item.label)}</li>`).join('')}</ul>${view.cursor?'<button type="button" data-comm-participants-more>참여자 더 보기</button>':'<p>목록 끝</p>'}</div>`,async()=>{participantsView=null;},'닫기');view.rendered=true;
    }finally{view.loading=false;}
  }
  async function manage(){
    const [bridge,profiles]=await Promise.all([ui.api('/communications/bridge'),ui.api('/communications/bridge/profiles')]);
    const bridgeHtml=`<h2>원격 앱 프로필</h2><p>환경변수 설정 없이 프로필을 추가하고 원격 화면에서 직접 로그인하세요. 로그인은 해당 프로필에 유지됩니다.</p><p>${e(bridge.limitation)}</p>${profiles.map(profile=>`<section><b>${e(profile.provider)} · ${e(profile.label)}</b><button type="button" data-comm-bridge="${e(profile.id)}">원격 화면</button><button type="button" data-comm-snapshot="${e(profile.id)}">접근성 확인</button><button type="button" data-comm-stop-profile="${e(profile.id)}">중지 · 로그인 유지</button><button type="button" data-comm-remove-profile="${e(profile.id)}">프로필 삭제</button></section>`).join('')}<button type="button" data-comm-new-profile ${bridge.configured?'':'disabled'}>${bridge.configured?'격리 프로필 추가':'브라우저 준비 중'}</button>`;
    ui.editor('Communication 계정',`<div class="comm-account-manager">${bridgeHtml}<details><summary>공식 API 연결 (선택)</summary>${providers.map(provider=>`<section><b>${e(provider.id)}</b><p>${e(provider.limitation)}</p><button type="button" data-comm-connect="${e(provider.id)}" ${provider.configured?'':'disabled'}>${provider.configured?'계정 연결':'서버 설정 필요'}</button></section>`).join('')}</details>${accounts.map(item=>`<section><span>${e(item.provider)} · ${e(item.label)}</span><button type="button" data-comm-disconnect="${e(item.id)}">연결 해제·캐시 삭제</button></section>`).join('')}</div>`,async()=>{},'닫기');}
  async function connect(provider){
    if(provider==='DISCORD'){ui.editor('Discord Bot 연결',ui.fields.input('token','공식 Bot Token','','password','required autocomplete="off" maxlength="8192"')+'<p>개인 사용자 토큰은 지원하지 않습니다.</p>',async form=>{await ui.api('/communications/accounts','POST',{provider,token:form.get('token')});document.querySelector('#editor-fields input[name=token]').value='';await refresh(true);},'연결');return;}
    const result=await ui.api('/communications/oauth','POST',{provider});
    ui.editor('공식 계정 인증',`<p>이 브라우저에서 시작한 인증은 같은 브라우저로 완료해야 합니다.</p><a href="${e(result.url)}" target="_blank" rel="noopener noreferrer">${e(provider)} 인증 페이지 열기</a><p>서버 브라우저를 사용하려면 서버 브라우저 안에서 대시보드를 열고 이 연결을 시작하세요.</p>`,async()=>refresh(true),'연결 확인');
  }
  function review(action){const verb=({EDIT:'수정',DELETE:'삭제',REACTION:'리액션 추가'})[action.operation]||'전송';ui.editor(`이 메시지를 ${verb}할까요?`,`<dl><dt>계정</dt><dd>${e(action.provider)} · ${e(action.accountLabel)}</dd><dt>수신자 / 대화</dt><dd>${e(action.message.recipient||action.message.conversationId)}</dd><dt>작업</dt><dd>${e(verb)} ${e(action.mutation?.messageId||'')}</dd><dt>제목</dt><dd>${e(action.message.subject)}</dd></dl><pre class="comm-preview">${e(action.message.text)}</pre><p>첨부: ${e((action.message.attachments||[]).map(file=>file.name+' ('+file.mediaType+')').join(', ')||'없음')}</p><p>승인하면 원본 서비스에 실제 ${e(verb)} 작업을 적용합니다.</p>`,async()=>{await ui.api(`/communications/actions/${url(action.id)}/confirmation`,'POST',{});const pane=panes.find(p=>p.accountId===action.accountId&&p.id===action.message.conversationId);if(pane&&(!action.operation||action.operation==='SEND')){drafts.delete(key(pane));files.delete(key(pane));pane.replyTo='';const composer=root.querySelector(`[data-compose="${panes.indexOf(pane)}"]`);if(composer){composer.querySelector('textarea').value='';const upload=composer.querySelector('input[type=file]');if(upload)upload.value='';}}await refresh(true);},`승인하고 ${verb}`);}
  function newMessage(seed){const gmail=accounts.filter(a=>a.provider==='GMAIL'&&a.capabilities.includes('SEND'));if(!gmail.length){ui.toast('채널 메시지는 대화를 선택해 작성하세요. 새 메일은 Gmail 연결이 필요합니다.');return;}ui.editor('새 메일',ui.fields.select('accountId','발신 계정',gmail[0].id,gmail.map(a=>[a.id,a.label]))+ui.fields.input('recipient','받는 사람','','email','required maxlength="320"')+ui.fields.input('subject','제목',seed?'Fwd: 전달 메시지':'','text','maxlength="300"')+'<label>본문<textarea name="text" required maxlength="32000">'+e(seed?`전달 메시지\n보낸 사람: ${seed.sender}\n${seed.text}`:'')+'</textarea></label>'+fileInput(),async form=>{await ui.api(`/communications/accounts/${url(form.get('accountId'))}/actions`,'POST',{recipient:form.get('recipient'),subject:form.get('subject'),text:form.get('text'),attachments:await uploads(document.querySelector('#editor-fields input[type=file]'))});actions=await ui.api('/communications/actions');renderDetails();ui.toast('전송 승인 목록에서 내용을 확인하세요.');},'전송 요청 만들기');}
  async function click(event){const button=event.target.closest('[data-comm]');if(!button)return;const index=Number(button.dataset.index);switch(button.dataset.comm){
    case 'accounts':await manage();break;
    case 'refresh':await refresh(true);break;
    case 'filter':filter=button.dataset.id;button.closest('details')?.removeAttribute('open');await refresh(true);break;
    case 'conversation':await openConversation(conversations.find(item=>key(item)===button.dataset.key));break;
    case 'tab':selected=index;root.dataset.messageOpen='true';save();renderList();renderPanes();renderDetails();break;
    case 'close':drafts.delete(key(panes[index]));files.delete(key(panes[index]));panes.splice(index,1);selected=Math.min(selected,Math.max(0,panes.length-1));save();render();break;
    case 'split':{button.setAttribute('aria-pressed',String(!root.classList.contains('comm-split')));const nodes=root.querySelectorAll('.comm-pane');root.classList.toggle('comm-split');nodes.forEach((node,i)=>node.hidden=root.classList.contains('comm-split')?i!==selected&&i!==(selected+1)%panes.length:i!==selected);save();break;}
    case 'back':detailsOpen=false;root.dataset.messageOpen='false';renderDetails();break;
    case 'details':if(button.dataset.index!==undefined)selected=index;detailsOpen=!detailsOpen;renderDetails();if(detailsOpen)root.querySelector('[data-comm=details-close]')?.focus();break;
    case 'details-close':detailsOpen=false;renderDetails();root.querySelector('.comm-pane:not([hidden]) [data-comm=details]')?.focus();break;
    case 'clear-reply':panes[index].replyTo='';syncComposers();break;
    case 'remove-file':{const pane=panes[index],items=files.get(key(pane))||[];items.splice(Number(button.dataset.fileIndex),1);files.set(key(pane),items);syncComposers();break;}
    case 'retry-messages':await loadMessages(index,false);break;
    case 'reply':panes[index].replyTo=button.dataset.message;renderPanes();root.querySelector(`[data-compose="${index}"] textarea`)?.focus();break;
    case 'more-messages':await loadMessages(index,true);break;
    case 'more-conversations':{const id=button.dataset.id,page=await ui.api(`/communications/accounts/${url(id)}/conversations?cursor=${url(pages.get(id))}`);const merged=new Map(conversations.map(item=>[key(item),item]));page.items.forEach(item=>merged.set(key(item),item));conversations=[...merged.values()];pages.set(id,page.nextCursor);renderList();break;}
    case 'sync':{const pane=panes[selected],progress=await ui.api(`/communications/accounts/${url(pane.accountId)}/synchronizations`,'POST',{});await refreshCache();status(`Gmail ${progress.phase}: ${progress.processed}개 반영 · ${progress.hasMore?'동기화를 다시 누르면 다음 페이지를 이어갑니다.':'최신 변경 반영 완료'}${progress.reset?' · 만료된 이력의 전체 동기화를 다시 시작했습니다.':''}`);break;}
    case 'read':{const pane=panes[selected];await ui.api(`/communications/accounts/${url(pane.accountId)}/labels`,'POST',{conversationId:pane.id,add:[],remove:['UNREAD']});await loadMessages(selected,false);break;}
    case 'labels':{const pane=panes[selected],labels=await ui.api(`/communications/accounts/${url(pane.accountId)}/labels`);ui.editor('대화 라벨',ui.fields.select('labelId','라벨',labels[0]?.id||'',labels.map(label=>[label.id,label.name]))+ui.fields.select('operation','변경','add',[['add','추가'],['remove','제거']]),async form=>{await ui.api(`/communications/accounts/${url(pane.accountId)}/labels`,'POST',{conversationId:pane.id,add:form.get('operation')==='add'?[form.get('labelId')]:[],remove:form.get('operation')==='remove'?[form.get('labelId')]:[]});await loadMessages(selected,false);},'적용');break;}
    case 'participants':{if(!panes[index])return;participantsView={pane:panes[index],items:[],cursor:'',loading:false};await loadParticipants(participantsView);break;}
    case 'thread':{threadView={pane:panes[index],id:button.dataset.message,items:[],cursor:'',loading:false};await loadThread(threadView);break;}
    case 'mutation':{const pane=panes[index],message=pane.messages.find(item=>item.id===button.dataset.message),operation=button.dataset.operation;ui.editor(({EDIT:'메시지 수정',DELETE:'메시지 삭제',REACTION:'리액션 추가'})[operation],`<pre class="comm-preview">${e(message.text)}</pre>`+(operation==='EDIT'?`<label>수정 본문<textarea name="text" required maxlength="32000">${e(message.text)}</textarea></label>`:operation==='REACTION'?ui.fields.input('reaction','Slack 이모지 이름 / Discord 이모지','','text','required maxlength="100"'):'<p>원본 서비스에서 삭제할 메시지를 확인하세요.</p>'),async form=>{await ui.api(`/communications/accounts/${url(pane.accountId)}/message-actions`,'POST',{operation,conversationId:pane.id,messageId:message.id,text:form.get('text')||'',reaction:form.get('reaction')||''});actions=await ui.api('/communications/actions');renderDetails();ui.toast('승인 목록에서 작업을 확인해 주세요.');},'승인 요청 만들기');break;}
    case 'forward':newMessage(panes[index].messages.find(message=>message.id===button.dataset.message));break;
    case 'new':newMessage();break;
    case 'review':{const action=actions.find(item=>item.id===button.dataset.id);if(action)review(action);break;}
    case 'cancel':await ui.api(`/communications/actions/${url(button.dataset.id)}`,'DELETE');actions=await ui.api('/communications/actions');renderDetails();break;
    case 'attachment':{const pane=panes[index];const link=document.createElement('a');link.href=`api/v1/communications/accounts/${url(pane.accountId)}/attachment?conversationId=${url(pane.id)}&messageId=${url(button.dataset.message)}&attachmentId=${url(button.dataset.attachment)}`;link.click();break;}
  }}
  return {init(shared){ui=shared;root=document.querySelector('#communications');if(!root)return;
    const fitViewport=()=>{if(root.classList.contains('active')&&window.visualViewport){const top=Math.max(0,root.getBoundingClientRect().top-window.visualViewport.offsetTop);root.style.setProperty('--comm-visible-height',Math.max(220,window.visualViewport.height-top)+'px');}};
    window.visualViewport?.addEventListener('resize',fitViewport);window.visualViewport?.addEventListener('scroll',fitViewport);window.addEventListener('resize',fitViewport);
    root.addEventListener('keydown',event=>{if(event.key==='Escape'&&detailsOpen){detailsOpen=false;renderDetails();root.querySelector('.comm-pane:not([hidden]) [data-comm=details]')?.focus();}});
    root.addEventListener('focusin',()=>window.requestAnimationFrame?.(fitViewport));
    window.addEventListener('workspace:invalidate',event=>{if(event.detail.topics.includes('communications'))refreshCache();});
    root.addEventListener('click',event=>click(event).catch(error=>{if(['conversation','retry-messages','more-messages'].includes(event.target.closest('[data-comm]')?.dataset.comm))return;status(error.message,'error');ui.toast(error.message);}));
    root.addEventListener('input',event=>{if(event.target.matches('[data-comm-list-search]')){listQuery=event.target.value;renderList();return;}const form=event.target.closest('[data-compose]');if(form&&event.target.matches('textarea')){drafts.set(key(panes[Number(form.dataset.compose)]),event.target.value);event.target.style.height='auto';event.target.style.height=Math.min(160,event.target.scrollHeight)+'px';syncComposers();}});
    document.querySelector('#editor-dialog')?.addEventListener('close',()=>{threadView=null;searchView=null;participantsView=null;});
    root.addEventListener('change',async event=>{if(event.target.matches('[data-comm-search] select')){renderSearchScopes();return;}if(!event.target.matches('input[type=file]'))return;const form=event.target.closest('[data-compose]');if(!form)return;try{files.set(key(panes[Number(form.dataset.compose)]),await uploads(event.target));renderPanes();}catch(error){event.target.value='';ui.toast(error.message);}});
    root.addEventListener('submit',async event=>{event.preventDefault();try{if(event.target.matches('[data-comm-search]')){const form=new FormData(event.target);searchView={accountId:String(form.get('scope')||''),query:String(form.get('query')||''),cursor:'',items:[],loading:false};await loadSearch(searchView);return;}const index=Number(event.target.dataset.compose),pane=panes[index],text=new FormData(event.target).get('text');if(!text?.trim()||pane.sending)return;pane.sending=true;syncComposers();try{const action=await ui.api(`/communications/accounts/${url(pane.accountId)}/actions`,'POST',{conversationId:pane.id,text,attachments:files.get(key(pane))||[],replyTo:pane.replyTo||(account(pane.accountId)?.provider==='GMAIL'?pane.messages?.at(-1)?.id:'')});actions=await ui.api('/communications/actions');renderDetails();review(action);}finally{pane.sending=false;syncComposers();}}catch(error){status(error.message,'error');ui.toast(error.message);}});
    document.addEventListener('click',async event=>{try{
      if(event.target.closest('[data-comm-participants-more]')){if(participantsView)await loadParticipants(participantsView);return;}
      if(event.target.closest('[data-comm-search-more]')){if(searchView)await loadSearch(searchView);return;}
      const resultButton=event.target.closest('[data-comm-search-open]');
      if(resultButton){const item=searchView?.items[Number(resultButton.dataset.commSearchOpen)];if(item){document.querySelector('#editor-dialog')?.close();await openConversation({accountId:item.accountId,id:item.conversationId,title:`${item.provider} · ${item.conversationId}`,kind:item.provider==='GMAIL'?'MAIL':''});}return;}
      if(event.target.closest('[data-comm-thread-more]')){if(threadView)await loadThread(threadView);return;}
      const button=event.target.closest('[data-comm-remote],[data-comm-bridge],[data-comm-snapshot],[data-comm-remove-profile],[data-comm-new-profile],[data-comm-windows],[data-comm-stop-profile]');if(!button)return;
      if(button.hasAttribute('data-comm-windows')){const workspace=await ui.api('/workspace'),devices=workspace.devices.filter(device=>device.id!=='local');if(!devices.length){ui.toast('장비 앱에서 Windows SSH/RDP 또는 VNC 장비를 등록해 주세요.');return;}ui.editor('Windows Runtime Agent',ui.fields.select('deviceId','등록된 Windows 장비',devices[0].id,devices.map(device=>[device.id,device.name]))+ui.fields.select('operation','작업','snapshot',[['snapshot','접근성 확인 (읽기 전용)'],['screen','원격 화면']]),async form=>{const id=form.get('deviceId');if(form.get('operation')==='screen'){await window.WorkspaceAuthenticationBrowser.open({title:'Windows 원격 앱',sessionFactory:()=>ui.api(`/communications/windows/${url(id)}/sessions`,'POST',{})});}else{const result=await ui.api(`/communications/windows/${url(id)}/observations`,'POST',{});status(`Windows Agent: ${result.state} · 관측 요소 ${result.nodes.length}개 · 구조화 미검증`);ui.toast(`Windows Agent: ${result.state} · ${result.nodes.length}개 접근성 요소`);}},'실행');}
      else if(button.dataset.commRemote){button.disabled=true;status(`${serviceName(button.dataset.commRemote)} 원격 화면을 준비하고 있습니다…`,'loading');try{await openRemote(button.dataset.commRemote);status('');}catch(error){status(error.message,'error');throw error;}finally{button.disabled=false;}}
      else if(button.hasAttribute('data-comm-new-profile')){ui.editor('메신저 화면 프로필',ui.fields.select('provider','서비스','GMAIL',[['GMAIL','Gmail'],['SLACK','Slack'],['DISCORD','Discord'],['KAKAOTALK','카카오톡 (Wine)']])+ui.fields.input('label','프로필 이름','','text','required maxlength="80"'),async form=>{await ui.api('/communications/bridge/profiles','POST',Object.fromEntries(form));ui.toast('프로필을 만들었습니다. 계정 관리에서 원격 화면을 열어 직접 로그인하세요.');},'생성');}
      else if(button.dataset.commBridge){const id=button.dataset.commBridge;window.WorkspaceAuthenticationBrowser.open({title:'Communication 원격 앱',sessionFactory:()=>ui.api(`/communications/bridge/profiles/${url(id)}/sessions`,'POST',{})});}
      else if(button.dataset.commSnapshot){const snapshot=await ui.api(`/communications/bridge/profiles/${url(button.dataset.commSnapshot)}/snapshot`);ui.editor('접근성 기술 확인',`<p>${e(snapshot.state)} · 메시지 구조화 미검증</p><pre class="comm-preview">${e(snapshot.nodes.map(node=>node.role+': '+node.name).join('\n'))}</pre>`,async()=>{},'닫기');}
      else if(button.dataset.commStopProfile){await ui.api(`/communications/bridge/profiles/${url(button.dataset.commStopProfile)}/stops`,'POST',{});ui.toast('원격 앱을 중지했습니다. 로그인 프로필은 유지됩니다.');}
      else if(button.dataset.commRemoveProfile){const id=button.dataset.commRemoveProfile;ui.confirmAction('브라우저 프로필 삭제','이 프로필의 원격 연결과 서버에 저장된 로그인 상태를 삭제합니다.',async()=>{await ui.api(`/communications/bridge/profiles/${url(id)}`,'DELETE');});}
    }catch(error){ui.toast(error.message);}});
    document.addEventListener('click',event=>{const connectButton=event.target.closest('[data-comm-connect]'),disconnect=event.target.closest('[data-comm-disconnect]');if(connectButton)connect(connectButton.dataset.commConnect).catch(error=>ui.toast(error.message));if(disconnect)ui.confirmAction('계정 연결 해제','저장된 인증정보, 메시지 캐시와 전송 요청을 삭제합니다. 원본 서비스 메시지는 유지됩니다.',async()=>{await ui.api(`/communications/accounts/${url(disconnect.dataset.commDisconnect)}`,'DELETE');panes=panes.filter(p=>p.accountId!==disconnect.dataset.commDisconnect);save();await refresh(true);});});
  },async open(id){if(id==='communications'&&root){shell();await refresh(true);window.dispatchEvent(new Event('resize'));}},refresh(){return refresh(false);}};
})();
