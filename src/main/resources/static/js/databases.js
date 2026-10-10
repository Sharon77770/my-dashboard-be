'use strict';
/** Database Studio stays inside the existing Workspace app and widget navigation. */
window.WorkspaceDatabases=(()=>{
  const root=()=>document.querySelector('#databases');
  const e=value=>window.WorkspaceUI.escape(value);
  const icon=name=>window.WorkspaceUI.icon(name);
  const csrf=()=>({[document.querySelector('meta[name=csrf-header]').content]:document.querySelector('meta[name=csrf-token]').content,'Content-Type':'application/json'});
  const errors={HOST_UNREACHABLE:'Host unreachable',AUTHENTICATION_FAILED:'Authentication failed',DATABASE_NOT_FOUND:'Database not found',PERMISSION_DENIED:'Permission denied',TLS_ERROR:'TLS error',CONNECTION_TIMEOUT:'Connection timeout',QUERY_TIMEOUT_OR_CANCELLED:'Query timed out or cancelled',TARGET_UNAVAILABLE:'SSH / Docker target unavailable',FILE_UNAVAILABLE:'SQLite file unavailable',DATABASE_ERROR:'Database error'};
  let connections=[],selected=null,functions=[],schemas=[],tables=[],schema='main',table=null,detail=null,page=null,pageNumber=0,pageSize=50,sort='',direction='ASC',filterColumn='',filter='';
  let tabs=[{id:crypto.randomUUID(),name:'Query 1',connectionId:null,sql:'',state:'IDLE',result:null}],active=tabs[0].id,history=[],favorites=[],side='explorer',mobilePane='query',pendingId=null;
  async function api(path,method='GET',body,options={}){
    const polling=method==='GET'&&/^\/databases\/[^/]+\/query\/[^/]+$/.test(path);
    const finishTask=(polling||options.quiet)?()=>{}:window.WorkspaceUI.beginTask?.(method==='GET'?'데이터베이스 정보를 불러오는 중…':'데이터베이스 요청을 처리하는 중…')||(()=>{});
    try{
      const response=await fetch('/api/v1'+path,{method,headers:method==='GET'?{}:csrf(),body:body===undefined?undefined:JSON.stringify(body)});
      if(!response.ok){const error=await response.json().catch(()=>({}));const failure=new Error(error.message||'요청에 실패했습니다.');failure.status=response.status;throw failure}
      return response.status===204?null:await response.json();
    }finally{finishTask();}
  }
  const current=()=>tabs.find(tab=>tab.id===active);
  const connection=()=>connections.find(item=>item.id===selected);
  async function load(){connections=await api('/databases');window.WorkspaceWidgets?.updateDatabases(connections);if(selected&&!connections.some(item=>item.id===selected))selected=null;render();}
  let selectionVersion=0;
  async function select(id){
    const version=++selectionVersion;selected=id;current().connectionId=id;
    root()?.querySelectorAll('[data-db-schema],[data-db-table],[data-db-action=run]').forEach(button=>{button.disabled=true;});
    const nextSchemas=await api(`/databases/${id}/schemas`);
    const nextSchema=nextSchemas.find(item=>item.name==='public')?.name||nextSchemas[0]?.name||'main';
    const [nextTables,nextFunctions,nextFavorites]=await Promise.all([
      api(`/databases/${id}/tables?schema=${encodeURIComponent(nextSchema)}`),
      api(`/databases/${id}/functions?schema=${encodeURIComponent(nextSchema)}`).catch(()=>[]),
      api(`/databases/${id}/favorites`)]);
    if(version!==selectionVersion)return;
    schemas=nextSchemas;schema=nextSchema;tables=nextTables;functions=nextFunctions;favorites=nextFavorites;table=null;detail=null;page=null;render();
  }
  async function selectSchema(name){
    const version=++selectionVersion,id=selected;
    root()?.querySelectorAll('[data-db-table]').forEach(button=>{button.disabled=true;});
    const [nextTables,nextFunctions]=await Promise.all([
      api(`/databases/${id}/tables?schema=${encodeURIComponent(name)}`),
      api(`/databases/${id}/functions?schema=${encodeURIComponent(name)}`).catch(()=>[])]);
    if(version!==selectionVersion)return;
    schema=name;tables=nextTables;functions=nextFunctions;table=null;detail=null;page=null;render();
  }
  async function selectTable(name){side='explorer';table=name;pageNumber=0;detail=await api(`/databases/${selected}/tables/${encodeURIComponent(name)}?schema=${encodeURIComponent(schema)}`);await rows();}
  async function rows(){const params=new URLSearchParams({schema,page:String(pageNumber),size:String(pageSize),sort,direction,filterColumn,filter});page=await api(`/databases/${selected}/tables/${encodeURIComponent(table)}/rows?${params}`);render()}
  function connectionForm(item){const value=(key,fallback='')=>e(item?.[key]??fallback);return `<form class="panel db-form" id="db-connection-form"><h3>${item?'연결 수정':'DB 연결'}</h3><label>데이터베이스 종류<select name="type">${['POSTGRESQL','MYSQL','MARIADB','SQLITE'].map(type=>`<option ${item?.type===type?'selected':''}>${type}</option>`).join('')}</select></label><label>서버 주소<input name="host" maxlength="255" placeholder="예: 192.168.1.10" value="${value('host')}"></label><label><span data-database-label>데이터베이스 이름</span><input name="databaseName" required maxlength="500" value="${value('databaseName')}"><small data-database-help>접속할 서버에 이미 만들어진 DB 이름입니다.</small></label><label>사용자 이름<input name="username" maxlength="100" autocomplete="username" value="${value('username')}"></label><label>비밀번호<input name="credential" type="password" autocomplete="new-password" maxlength="500" placeholder="${item?.passwordConfigured?'저장됨 · 변경할 때만 입력':'DB 계정 비밀번호'}"></label><details class="db-advanced"><summary>고급 설정</summary><div class="db-advanced-fields"><label>연결 별칭 (선택)<input name="name" maxlength="100" placeholder="비워 두면 자동 지정" value="${value('name')}"><small>연결 목록에서 구분할 이름입니다.</small></label><label>포트<input name="port" type="number" min="0" max="65535" value="${value('port')}"><small>기본 포트가 자동 입력됩니다.</small></label><label>암호화 연결 (TLS)<select name="sslMode"><option value="DISABLE">사용 안 함</option><option value="REQUIRE" ${item?.sslMode==='REQUIRE'?'selected':''}>사용</option></select></label><label>데이터 변경 권한<select name="accessMode"><option value="READ_ONLY">조회만 허용</option><option value="READ_WRITE" ${item?.accessMode==='READ_WRITE'?'selected':''}>조회·수정 허용</option></select></label></div></details><input type="hidden" name="metadata" value="${e(JSON.stringify(item?.metadata||{}))}"><div class="db-buttons"><button class="primary">연결 저장</button><button type="button" data-db-action="test-draft">연결 테스트</button><output id="db-test-result" role="status"></output><button type="button" data-db-action="back">취소</button></div></form>`}
  function connectionName(form){return String(form.get('name')||'').trim()||[String(form.get('databaseName')||'').split(/[\\/]/).pop(),form.get('targetMode')==='DOCKER'?'Docker':form.get('host')||form.get('type')].filter(Boolean).join(' · ').slice(0,100);}
  function sidebar(){return `<aside class="db-sidebar panel"><header><b>Connections</b><button data-db-action="new-connection" aria-label="연결 추가" title="연결 추가">${icon('plus')}</button></header>${connections.map(item=>`<button class="db-tree-row" data-db-connection="${e(item.id)}" aria-current="${selected===item.id}">${icon('disk')}<span>${e(item.name)}</span><small>${e(item.type)}${item.targetMode==='DEVICE'?' / SSH':item.targetMode==='DOCKER'?' / Docker':''}</small></button>`).join('')||'<p>등록된 DB 없음</p>'}${selected?`<div class="db-sidebar-actions"><button data-db-action="edit-connection">설정</button><button data-db-action="test">연결 테스트</button><button data-db-action="delete-connection">삭제</button></div><h3>Schemas</h3>${schemas.map(item=>`<button class="db-tree-row" data-db-schema="${e(item.name)}" aria-current="${schema===item.name}">${icon('folder')}<span>${e(item.name)}</span></button>`).join('')}<h3>Tables / Views</h3>${tables.map(item=>`<button class="db-tree-row" data-db-table="${e(item.name)}" aria-current="${table===item.name}">${icon(item.kind==='VIEW'?'file':'apps')}<span>${e(item.name)}</span></button>`).join('')}`:''}<nav class="db-sidebar-actions"><button data-db-side="explorer">Explorer</button><button data-db-side="history">History</button><button data-db-side="favorites">Favorites</button></nav><h3>Functions</h3>${functions.map(item=>`<span class="db-tree-row">ƒ ${e(item.name)}</span>`).join('')}</aside>`}
  function resultGrid(data){if(!data)return '<p>쿼리 또는 테이블을 선택하세요.</p>';const rows=data.rows||[],columns=data.columns||[];return `<div class="db-grid-wrap"><table class="db-grid"><thead><tr>${columns.map(column=>`<th>${e(column)}</th>`).join('')}</tr></thead><tbody>${rows.map((row,index)=>`<tr>${columns.map(column=>{const value=row[column];const text=value===null?'NULL':String(value);return `<td><button class="db-cell" data-db-cell="${index}" data-db-column="${e(column)}" title="자세히 보기">${value===null?'<em>NULL</em>':e(text.length>120?text.slice(0,120)+'…':text)}</button></td>`}).join('')}</tr>`).join('')}</tbody></table></div>`}
  function tableView(){if(!table||!detail)return '';return `<section class="panel db-table"><header><h3>${e(schema)}.${e(table)}</h3><button data-db-action="refresh-rows" aria-label="테이블 새로고침" title="테이블 새로고침">${icon('refresh')}</button></header><details><summary>Columns · Keys · Indexes</summary><div class="db-columns">${detail.columns.map(item=>`<span>${e(item.name)} <small>${e(item.type)}${item.primaryKey?' · PK':''}${item.nullable?'':' · NOT NULL'}</small></span>`).join('')}</div><p>${detail.foreignKeys.map(item=>`${e(item.column)} → ${e(item.targetTable)}.${e(item.targetColumn)}`).join(' · ')}</p><p>${detail.indexes.map(item=>e(item.name)).join(' · ')}</p></details><form id="db-filter" class="db-filter"><select name="filterColumn"><option value="">Filter column</option>${detail.columns.map(item=>`<option value="${e(item.name)}" ${filterColumn===item.name?'selected':''}>${e(item.name)}</option>`).join('')}</select><input name="filter" value="${e(filter)}" placeholder="Contains"><select name="sort"><option value="">Sort</option>${detail.columns.map(item=>`<option value="${e(item.name)}" ${sort===item.name?'selected':''}>${e(item.name)}</option>`).join('')}</select><select name="direction"><option>ASC</option><option ${direction==='DESC'?'selected':''}>DESC</option></select><select name="size">${[25,50,100,200].map(size=>`<option ${pageSize===size?'selected':''}>${size}</option>`).join('')}</select><button>적용</button></form>${resultGrid(page)}<footer><span>${page?.total<0?'Count unavailable':`${page?.total??0} rows`}</span><button data-db-action="previous" aria-label="이전 페이지" title="이전 페이지" ${pageNumber===0?'disabled':''}>${icon('back')}</button><span>${pageNumber+1}</span><button data-db-action="next" aria-label="다음 페이지" title="다음 페이지" ${page?.rows?.length<pageSize?'disabled':''}>${icon('arrowRight')}</button></footer></section>`}
  function queryView(){
   const tab=current();
   const tabButtons=tabs.map(item=>`<span class="db-tab"><button data-db-tab="${e(item.id)}" aria-current="${active===item.id}">${e(item.name)}</button><button class="db-tab-close" data-db-close="${e(item.id)}" aria-label="${e(item.name)} 닫기" title="탭 닫기">${icon('close')}</button></span>`).join('');
   return `<section class="panel db-editor"><nav class="db-tabs" aria-label="SQL 탭">${tabButtons}<button class="db-new-tab" data-db-action="new-tab" aria-label="새 SQL 탭" title="새 SQL 탭">${icon('plus')}</button></nav><div class="db-toolbar"><span>${e(connection()?.name||'연결 선택')} · ${e(connection()?.accessMode||'')}</span><button class="db-run" data-db-action="run" aria-label="SQL 실행" title="SQL 실행">${icon('play')}<span class="db-action-label">Run</span></button><button data-db-action="cancel" aria-label="실행 취소" title="실행 취소" ${tab.state==='RUNNING'?'':'disabled'}>${icon('stop')}<span class="db-action-label">Cancel</span></button><details class="db-more"><summary aria-label="SQL 편집 작업" title="SQL 편집 작업">${icon('more')}</summary><div><button data-db-action="format">Format</button><button data-db-action="clear">Clear</button><button data-db-action="favorite">☆ Save</button></div></details></div><textarea id="db-sql" spellcheck="false" aria-label="SQL editor" placeholder="SELECT * FROM ...">${e(tab.sql)}</textarea><div class="db-result-head"><b>Result</b><span class="ui-status" data-state="${window.WorkspaceUI.stateTone(tab.state)}">${e(tab.state)} ${tab.result?.durationMs??0} ms ${tab.result?.resultType==='MUTATION'?`· ${tab.result.affectedRows} affected`:tab.result?.rows?`· ${tab.result.rows.length} rows`:''} ${e(tab.result?.errorType||'')}</span></div>${resultGrid(tab.result)}</section>`;
  }
  function sidePanel(){if(side==='history')return `<section class="panel db-side-list"><h3>Query History</h3>${history.map(item=>`<button data-db-history="${e(item.id)}"><b>${e(item.connectionName)}</b><small>${new Date(item.timestamp).toLocaleString('ko-KR')} · ${item.durationMs} ms · ${item.success?'SUCCESS':e(item.errorType)}</small><code>${e(item.sql.slice(0,120))}</code></button>`).join('')||'<p>이력 없음</p>'}</section>`;if(side==='favorites')return `<section class="panel db-side-list"><h3>Favorites</h3>${favorites.map(item=>`<div><button data-db-favorite="${e(item.id)}"><b>${e(item.name)}</b><code>${e(item.sql.slice(0,120))}</code></button><button data-db-remove-favorite="${e(item.id)}">×</button></div>`).join('')||'<p>즐겨찾기 없음</p>'}</section>`;return tableView()}
  function render(){if(!root())return;root().innerHTML=`<div class="page-head"><div><h1>Database Studio</h1></div><button data-db-action="toggle-sidebar" aria-label="연결 및 Schema 열기" title="연결 및 Schema 열기">${icon('menu')}<span>Connections</span></button></div><nav class="db-mobile-tabs" aria-label="Database Studio 화면">${[['query','Query'],['result','Result'],['schema','Schema'],['history','History']].map(([id,label])=>`<button data-db-pane="${id}" aria-current="${mobilePane===id}">${label}</button>`).join('')}</nav><div class="db-layout" data-mobile-pane="${mobilePane}">${sidebar()}<main class="db-main"><div id="db-notice" role="status"></div>${sidePanel()}${queryView()}<section class="db-mobile-result panel"><header><b>Result</b><span class="ui-status" data-state="${window.WorkspaceUI.stateTone(current().state)}">${e(current().state)} · ${current().result?.durationMs??0} ms</span></header>${resultGrid(current().result)}</section></main></div>`}
  function notice(text){const node=document.querySelector('#db-notice');if(node)node.textContent=text}
  async function testDraftForm(){const element=document.querySelector('#db-connection-form');if(!element.reportValidity())return;const form=new FormData(element);const request=Object.fromEntries(form);request.name=connectionName(form);request.port=request.port?Number(request.port):null;const output=document.querySelector('#db-test-result');output.textContent='Connecting...';try{request.metadata=JSON.parse(request.metadata||'{}');const result=await api('/databases/test'+(selected?'?id='+encodeURIComponent(selected):''),'POST',request);output.textContent=result.connected?`✓ ${result.version} · ${result.latencyMs} ms`:(errors[result.errorType]||result.errorType)}catch(error){output.textContent=error.message}}
  function showCell(value){let dialog=document.querySelector('#db-cell-dialog');if(!dialog){dialog=document.createElement('dialog');dialog.id='db-cell-dialog';const close=document.createElement('button');close.type='button';close.textContent='닫기';close.addEventListener('click',()=>dialog.close());const content=document.createElement('pre');content.id='db-cell-detail';dialog.append(close,content);document.body.append(dialog)}dialog.querySelector('#db-cell-detail').textContent=value;dialog.showModal()}
  async function editConnection(item){
    selectionVersion++;
    root().innerHTML=connectionForm(item);
    const form=document.querySelector('#db-connection-form'),controls=document.createElement('fieldset');
    controls.className='db-target-controls';
    controls.innerHTML='<legend>연결 대상</legend><label>연결 방식<select name="targetMode"><option value="DIRECT">직접 연결</option><option value="DEVICE">등록 장비 · SSH</option><option value="DOCKER">장비의 Docker 컨테이너</option></select></label><label data-device-field>장비<select name="deviceId"><option value="">장비 선택</option></select></label><label data-container-field>컨테이너<select name="containerId" data-container-search><option value="">장비를 먼저 선택하세요</option></select><button type="button" data-containers-refresh>목록 새로고침</button></label><p class="muted" data-target-help></p><output data-target-error role="status"></output>';
    form.querySelector('h3').after(controls);
    const mode=form.elements.targetMode,device=form.elements.deviceId,container=form.elements.containerId,type=form.elements.type,host=form.elements.host,port=form.elements.port;
    const error=controls.querySelector('[data-target-error]');let generation=0;
    mode.value=item?.targetMode||'DIRECT';
    function sync(){
      const direct=mode.value==='DIRECT',docker=mode.value==='DOCKER',sqlite=type.value==='SQLITE';
      controls.querySelector('[data-device-field]').hidden=direct;device.disabled=direct;
      controls.querySelector('[data-container-field]').hidden=!docker;container.disabled=!docker;container.required=docker;device.required=!direct;
      host.closest('label').hidden=docker||sqlite;host.disabled=docker||sqlite;
      form.querySelector('[data-database-label]').textContent=sqlite?'SQLite 파일 경로':'데이터베이스 이름';
      form.querySelector('[data-database-help]').textContent=sqlite?'대시보드 서버에 있는 기존 .db 파일의 전체 경로입니다. SQL 스크립트 파일이 아닙니다.':'접속할 서버에 이미 만들어진 DB 이름입니다.';
      form.elements.databaseName.placeholder=sqlite?'/app/data/files/example.db':'예: my_database';
      port.closest('label').hidden=sqlite;form.elements.sslMode.closest('label').hidden=sqlite;host.required=!sqlite&&!docker;
      form.elements.username.closest('label').hidden=sqlite;form.elements.credential.closest('label').hidden=sqlite;
      [...type.options].find(option=>option.value==='SQLITE').disabled=!direct;
      controls.querySelector('[data-target-help]').textContent=direct?(sqlite?'대시보드 파일 영역의 SQLite 파일을 사용합니다.':'대시보드 서버에서 접근 가능한 주소를 입력하세요.'):(docker?'컨테이너 내부 DB 포트를 입력하세요. DB 계정은 별도로 입력하며 Docker의 비밀번호를 읽지 않습니다.':'Host는 선택 장비 기준입니다. 장비 자체 DB는 127.0.0.1을 사용하세요.');
      if(sqlite){host.value='';port.value='0';form.elements.username.value='';form.elements.sslMode.value='DISABLE';}
    }
    async function containers(preferred=''){
      const version=++generation;error.textContent='';container.replaceChildren(new Option('컨테이너 선택',''));
      if(mode.value!=='DOCKER'||!device.value)return;
      container.disabled=true;
      try{
        const values=await api('/databases/devices/'+encodeURIComponent(device.value)+'/containers');
        if(!form.isConnected||version!==generation)return;
        for(const value of values){const option=new Option(value.name+' · '+value.image+(value.ports?' · '+value.ports:''),value.id);option.dataset.image=value.image;container.add(option);}
        container.value=preferred;
        if(!values.length)error.textContent='실행 중인 컨테이너가 없습니다.';
        else if(preferred&&!container.value)error.textContent='저장된 컨테이너가 없습니다. 새 컨테이너를 선택하세요.';
      }catch(failure){if(version===generation&&form.isConnected)error.textContent=failure.message;}
      finally{if(version===generation&&form.isConnected)container.disabled=mode.value!=='DOCKER';}
    }
    mode.onchange=()=>{generation++;if(mode.value!=='DIRECT'&&type.value==='SQLITE'){type.value='POSTGRESQL';port.value='5432';}if(mode.value==='DEVICE'&&!host.value)host.value='127.0.0.1';sync();containers();};
    device.onchange=()=>containers();controls.querySelector('[data-containers-refresh]').onclick=()=>containers(container.value);
    type.onchange=()=>{port.value=type.value==='POSTGRESQL'?'5432':type.value==='SQLITE'?'0':'3306';sync();};
    container.onchange=()=>{const image=container.selectedOptions[0]?.dataset.image||'';if(/postgres/i.test(image))type.value='POSTGRESQL';else if(/mariadb/i.test(image))type.value='MARIADB';else if(/mysql/i.test(image))type.value='MYSQL';port.value=type.value==='POSTGRESQL'?'5432':'3306';sync();};
    if(!port.value)port.value=type.value==='POSTGRESQL'?'5432':'3306';sync();
    try{const devices=(await api('/workspace')).devices;if(!form.isConnected)return;for(const value of devices)device.add(new Option(value.name+(value.id==='local'?' · 대시보드 서버':''),value.id));device.value=item?.deviceId||'';if(item?.deviceId&&!device.value)error.textContent='등록 장비가 삭제되었습니다. 장비를 다시 선택하세요.';if(mode.value==='DOCKER')await containers(item?.containerId||'');}
    catch(failure){if(form.isConnected)error.textContent=failure.message;}
  }

  function updateQueryProgress(tab){
    if(active!==tab.id)return;
    const stateNode=root()?.querySelector('.db-editor .db-result-head span');
    if(stateNode)stateNode.dataset.state=window.WorkspaceUI.stateTone(tab.state);
    if(stateNode)stateNode.textContent=`${tab.state} ${tab.result?.durationMs??0} ms`;
    const mobileState=root()?.querySelector('.db-mobile-result header span');
    if(mobileState)mobileState.dataset.state=window.WorkspaceUI.stateTone(tab.state);
    if(mobileState)mobileState.textContent=`${tab.state} · ${tab.result?.durationMs??0} ms`;
    const cancelButton=root()?.querySelector('[data-db-action="cancel"]');
    if(cancelButton)cancelButton.disabled=tab.state!=='RUNNING';
  }
  async function run(confirmed=false){
    const tab=current(),connectionId=selected,completionPane=mobilePane;
    tab.sql=document.querySelector('#db-sql').value;
    if(!selected)throw new Error('데이터베이스 연결을 선택하세요.');
    try{
      const result=await api(`/databases/${connectionId}/query`,'POST',{sql:tab.sql,confirmed});
      tab.executionId=result.executionId;tab.state='RUNNING';tab.result=result;
      updateQueryProgress(tab);
      while(tab.state==='RUNNING'){
        await new Promise(resolve=>setTimeout(resolve,350));
        if(tab.state!=='RUNNING')break;
        const next=await api(`/databases/${connectionId}/query/${result.executionId}`);
        if(tab.state!=='RUNNING')break;
        tab.state=next.state;tab.result=next;
        if(next.state==='RUNNING')updateQueryProgress(tab);
        else {if(mobilePane===completionPane)mobilePane='result';if(active===tab.id)render()}
      }
    }catch(error){if(error.status===409&&!confirmed&&confirm('위험한 SQL입니다. 실행할까요?'))return run(true);throw error}
  }
  document.addEventListener('input',event=>{if(event.target.id==='db-sql')current().sql=event.target.value});
  document.addEventListener('click',async event=>{const open=event.target.closest('[data-database-open]');if(open){pendingId=open.dataset.databaseOpen;window.dispatchEvent(new CustomEvent('assistant:navigate',{detail:{route:'databases'}}));return}const node=event.target.closest('[data-db-pane],[data-db-connection],[data-db-schema],[data-db-table],[data-db-tab],[data-db-close],[data-db-side],[data-db-action],[data-db-history],[data-db-favorite],[data-db-remove-favorite],[data-db-cell]');if(!node)return;try{if(node.dataset.dbPane){mobilePane=node.dataset.dbPane;side=mobilePane==='history'?'history':'explorer';if(side==='history')history=await api('/databases/history');render()}else if(node.dataset.dbConnection)await select(node.dataset.dbConnection);else if(node.dataset.dbSchema)await selectSchema(node.dataset.dbSchema);else if(node.dataset.dbTable)await selectTable(node.dataset.dbTable);else if(node.dataset.dbClose){const item=tabs.find(tab=>tab.id===node.dataset.dbClose);if(item?.sql.trim()&&!confirm('작성 중인 SQL 탭을 닫을까요?'))return;tabs=tabs.filter(tab=>tab.id!==node.dataset.dbClose);if(!tabs.length)tabs=[{id:crypto.randomUUID(),name:'Query 1',connectionId:selected,sql:'',state:'IDLE',result:null}];active=tabs[0].id;render()}else if(node.dataset.dbTab){active=node.dataset.dbTab;if(current().connectionId)await select(current().connectionId);else render()}else if(node.dataset.dbSide){side=node.dataset.dbSide;if(side==='history')history=await api('/databases/history');render()}else if(node.dataset.dbHistory){const item=history.find(value=>value.id===node.dataset.dbHistory);if(item&&item.sql!=='[sensitive query omitted]'){current().sql=item.sql;mobilePane='query';render()}}else if(node.dataset.dbFavorite){current().sql=favorites.find(value=>value.id===node.dataset.dbFavorite)?.sql||'';mobilePane='query';render()}else if(node.dataset.dbRemoveFavorite){await api(`/databases/${selected}/favorites/${node.dataset.dbRemoveFavorite}`,'DELETE');favorites=await api(`/databases/${selected}/favorites`);render()}else if(node.dataset.dbCell!==undefined){const value=(node.closest('.db-table')?page:current().result)?.rows?.[Number(node.dataset.dbCell)]?.[node.dataset.dbColumn];showCell(value===null?'NULL':String(value))}else{switch(node.dataset.dbAction){case 'new-connection':selected=null;await editConnection();break;case 'edit-connection':await editConnection(connection());break;case 'back':render();break;case 'toggle-sidebar':mobilePane='schema';render();break;case 'test-draft':await testDraftForm();break;case 'test':{notice('Connecting...');const result=await api(`/databases/${selected}/test`,'POST');notice(result.connected?`✓ ${result.version} · ${result.latencyMs} ms`:result.errorType);break}case 'refresh-rows':await rows();break;case 'previous':pageNumber--;await rows();break;case 'next':pageNumber++;await rows();break;case 'new-tab':{const item={id:crypto.randomUUID(),name:`Query ${tabs.length+1}`,connectionId:selected,sql:'',state:'IDLE',result:null};tabs.push(item);active=item.id;render();break}case 'run':await run();break;case 'cancel':await api(`/databases/${current().connectionId}/query/${current().executionId}/cancel`,'POST');current().state='CANCELLED';render();break;case 'format':current().sql=current().sql.replace(/\s+(FROM|WHERE|ORDER BY|GROUP BY|LIMIT|JOIN)\b/gi,'\n$1');render();break;case 'clear':if(!current().sql.trim()||confirm('SQL을 지울까요?')){current().sql='';render()}break;case 'favorite':{const name=prompt('즐겨찾기 이름');if(name){await api(`/databases/${selected}/favorites`,'POST',{name,sql:current().sql});favorites=await api(`/databases/${selected}/favorites`)}break}case 'delete-connection':if(confirm('연결을 삭제할까요?')){await api(`/databases/${selected}`,'DELETE');selected=null;await load()}break}}}catch(error){notice(error.message)}});
  document.addEventListener('submit',async event=>{if(!['db-connection-form','db-filter'].includes(event.target.id))return;event.preventDefault();try{const form=new FormData(event.target);if(event.target.id==='db-filter'){filterColumn=form.get('filterColumn');filter=form.get('filter');sort=form.get('sort');direction=form.get('direction');pageSize=Number(form.get('size'));pageNumber=0;await rows();return}const input={name:connectionName(form),type:form.get('type'),host:form.get('host'),port:form.get('port')?Number(form.get('port')):null,databaseName:form.get('databaseName'),username:form.get('username'),credential:form.get('credential')||null,sslMode:form.get('sslMode'),accessMode:form.get('accessMode'),metadata:JSON.parse(form.get('metadata')||'{}'),targetMode:form.get('targetMode')||'DIRECT',deviceId:form.get('deviceId')||'',containerId:form.get('containerId')||''};const item=await api(selected?`/databases/${selected}`:'/databases',selected?'PUT':'POST',input);await load();await select(item.id)}catch(error){alert(error.message)}});
  return {async refresh(){const host=root()?.querySelector('.db-sidebar');if(!host)return;const next=await api('/databases','GET',undefined,{quiet:true});if(!host.isConnected)return;connections=next;const template=document.createElement('template');template.innerHTML=sidebar();window.WorkspaceLiveDOM?.patch(host,template.content.firstElementChild.innerHTML);window.WorkspaceWidgets?.updateDatabases(connections);},open(id){if(id!=='databases')return;load().then(async()=>{if(pendingId){const id=pendingId;pendingId=null;await select(id)}}).catch(error=>notice(error.message))}};
})();
