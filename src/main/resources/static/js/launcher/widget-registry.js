'use strict';
/** Widgets expose summaries and existing app actions; they never run a command implicitly. */
window.WorkspaceWidgets = (() => {
  const e=value=>window.WorkspaceUI.escape(value);
  const definitions=[
    {id:'search',appId:'search',name:'Workspace 검색',sizes:[[4,1],[8,1]],defaultSize:[4,1],description:'앱, 장비, 파일, 최근 작업을 한 곳에서 검색합니다.'},
    {id:'device-status',appId:'devices',name:'장비 상태',sizes:[[2,1],[4,2]],defaultSize:[4,2],description:'마지막으로 측정한 CPU·RAM과 빠른 연결을 표시합니다.'},
    {id:'today',appId:'calendar',name:'오늘 일정',sizes:[[2,2],[4,2]],defaultSize:[4,2],description:'오늘의 일정과 시작 시간을 표시합니다.'},
    {id:'service-analytics',appId:'telemetry',name:'Service Analytics',sizes:[[2,1],[4,2]],defaultSize:[4,2],description:'등록한 서비스 요청량과 사용자, 오류 상태를 간단히 보여줍니다.'},
    {id:'services-status',appId:'services',name:'Services',sizes:[[2,1],[4,2]],defaultSize:[4,2],description:'서비스 상태와 빠른 이동을 표시합니다.'},
    {id:'database-status',appId:'databases',name:'Databases',sizes:[[2,1],[4,2]],defaultSize:[4,2],description:'등록한 데이터베이스 연결을 표시합니다.'},
    {id:'github-status',appId:'github',name:'GitHub 작업',sizes:[[2,1],[4,2]],defaultSize:[4,2],description:'열린 PR, 이슈와 최근 CI 상태를 보여줍니다.'},
    {id:'connections',appId:'terminal',name:'최근 연결',sizes:[[2,2],[4,2]],defaultSize:[2,2],description:'최근 터미널 연결을 바로 다시 엽니다.'},
    {id:'recent-files',appId:'files',name:'최근 파일',sizes:[[2,2],[4,2]],defaultSize:[2,2],description:'최근에 탐색한 서버 파일 위치를 엽니다.'},
    {id:'project',appId:'studio',name:'최근 프로젝트',sizes:[[2,1],[4,2]],defaultSize:[4,2],description:'최근 작업 폴더와 이 세션에서 확인한 Git 상태입니다.'},
    {id:'codex',appId:'studio',name:'Codex 작업',sizes:[[2,1],[4,2]],defaultSize:[2,1],description:'현재 브라우저 세션의 마지막 Codex 실행 상태입니다.'}
  ];
  let today=[],calendarError='',calendarLoading=false,studio={},telemetry=[],github=null,githubLoading=false,serviceItems=[],databaseItems=[],summaryLoaded=false,summaryStale=false;
  const day=date=>`${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
  function rows(items){return items.join('')||window.WorkspaceUI.emptyState('표시할 항목 없음');}
  return {
    all(){return definitions.filter(def=>window.WorkspaceApps.get(def.appId)?.widgets?.includes(def.id));},get(id){return definitions.find(item=>item.id===id);},
    updateStudio(detail){studio=detail;},
    updateTelemetry(items){telemetry=items||[];},
    updateServices(items){serviceItems=items||[];},
    updateDatabases(items){const known=new Map(databaseItems.map(item=>[item.id,item.connected]));databaseItems=(items||[]).map(item=>({...item,connected:known.get(item.id)}));},
    /** Attention projects already loaded signals; unknown data is never reported as healthy. */
    attention(state,statuses){
      const signals=[];
      const signal=(title,detail,action,tone='warning')=>`<button class="widget-row attention-row" ${action}><b>${e(title)}</b><span class="ui-status" data-state="${tone}">${e(detail)}</span></button>`;
      for(const service of serviceItems.filter(item=>['DOWN','DEGRADED'].includes(item.health?.state)))signals.push(signal(service.name,service.health.state==='DOWN'?'서비스 중단':'상태 확인 필요',`data-service-open="${e(service.id)}"`,service.health.state==='DOWN'?'danger':'warning'));
      for(const device of state.devices.filter(item=>statuses.get(item.id)?.state==='OFFLINE'))signals.push(signal(device.name,'장비 오프라인','data-view="devices"','offline'));
      if(github?.run?.conclusion==='failure')signals.push(signal('GitHub Actions','최근 실행 실패','data-view="github"','danger'));
      if(github?.approvals?.length)signals.push(signal('GitHub 승인 대기',`${github.approvals.length}건 확인 필요`,'data-view="github"','pending'));
      if(studio.codex==='중지 또는 실패')signals.push(signal('Studio Codex','작업 중지 또는 실패','data-view="studio"','warning'));
      if(calendarError)signals.push(signal('오늘 일정','일정 조회 실패','data-view="calendar"'));
      if(summaryStale)signals.push(signal('일부 상태를 확인하지 못했습니다','새로고침으로 다시 확인','data-launcher="refresh-widgets"','unknown'));
      if(signals.length)return signals.slice(0,4).join('')+(signals.length>4?`<p class="overview-empty">추가 ${signals.length-4}건 · 서비스와 장비에서 확인</p>`:'');
      if(!summaryLoaded)return window.WorkspaceUI.skeleton(2);
      const unverified=state.devices.some(item=>!statuses.has(item.id)||!['ONLINE','OFFLINE'].includes(statuses.get(item.id)?.state))||serviceItems.some(item=>!item.health?.state||item.health.state==='UNKNOWN');
      return window.WorkspaceUI.emptyState(unverified?'아직 확인 중인 상태가 있습니다':'확인된 알림이 없습니다',unverified?'장비와 서비스에서 연결 상태를 확인하세요.':'현재 조회한 서비스·장비·CI 기준',unverified?'info':'check');
    },
    async refresh(api,options={}){
      if(!options.quiet){calendarLoading=true;githubLoading=true;}
      const now=new Date(),next=new Date(now);next.setDate(next.getDate()+1);
      const results=await Promise.allSettled([
        api(`/calendar/events?from=${day(now)}&to=${day(next)}`),
        api('/telemetry/services'),
        (async()=>{
          const status=await api('/github/status');if(!status.authenticated)return {authenticated:false};
          const approvals=await api('/github/approvals');
          const owners=await api('/github/owners');const owner=owners[0];if(!owner)return {authenticated:true,approvals};
          const overview=await api('/github/owners/'+encodeURIComponent(owner.login)+'/overview');
          const firstRepository=overview.repositories?.[0]?.nameWithOwner;
          const runs=firstRepository?await api('/github/actions/runs?repository='+encodeURIComponent(firstRepository)).catch(()=>[]):[];
          return {authenticated:true,overview,run:runs[0],approvals};
        })(),
        (async()=>Promise.all((await api('/services')).map(async service=>({...service,health:await api('/services/'+encodeURIComponent(service.id)+'/health')}))))(),
        (async()=>{const items=await api('/databases');return Promise.all(items.map(async(item,index)=>index<4?{...item,connected:(await api('/databases/'+encodeURIComponent(item.id)+'/test','POST').catch(()=>({connected:false}))).connected}:item))})()
      ]);
      if(results[0].status==='fulfilled'){today=results[0].value;calendarError='';}else if(!options.quiet)calendarError=results[0].reason?.message||'일정 조회 실패';
      if(results[1].status==='fulfilled')telemetry=results[1].value||[];
      if(results[2].status==='fulfilled')github=results[2].value;else if(!options.quiet)github={error:true};
      if(results[3].status==='fulfilled')serviceItems=results[3].value;
      if(results[4].status==='fulfilled')databaseItems=results[4].value;
      calendarLoading=false;githubLoading=false;
      summaryLoaded=true;summaryStale=results.some(result=>result.status==='rejected');
      return {stale:results.some(result=>result.status==='rejected')};
    },
    render(item,state,statuses){const compact=item.h===1;switch(item.widgetId){
      case 'search':return '<button class="widget-search" data-action="palette">'+window.WorkspaceUI.icon('search')+'<span>앱, 파일, 장비 검색</span><kbd>Ctrl K</kbd></button>';
      case 'device-status':{
        const online=state.devices.filter(device=>statuses.get(device.id)?.state==='ONLINE').length;
        const summary=`<button data-view="devices" class="widget-metric"><span class="ui-status" data-state="${online===state.devices.length&&online>0?'success':'warning'}">${online} / ${state.devices.length}</span><small>장비 온라인</small></button>`;
        if(compact)return summary;
        return summary+rows(state.devices.slice(0,2).map(device=>{const status=statuses.get(device.id);
          return `<button class="widget-row widget-device" data-open="TERMINAL" data-target="${e(device.id)}"><b>${e(device.name)}</b><span class="widget-device-metrics"><span>CPU ${status?.cpu==null?'—':Math.round(status.cpu)+'%'}</span>${window.WorkspaceUI.progress(status?.cpu,'CPU')}</span><span class="widget-device-metrics"><span>RAM ${status?.memory==null?'—':Math.round(status.memory)+'%'}</span>${window.WorkspaceUI.progress(status?.memory,'RAM')}</span></button>`;
        }));
      }
      case 'service-analytics':{
        if(!telemetry.length)return window.WorkspaceUI.emptyState('서비스 없음','Telemetry에서 서비스를 등록하세요.','chart');
        const active=telemetry.filter(service=>service.status==='Receiving data').length,requests=telemetry.reduce((sum,service)=>sum+(service.requestsToday||0),0),users=telemetry.reduce((sum,service)=>sum+(service.dau||0),0);
        const weightedErrors=telemetry.reduce((sum,service)=>sum+(service.requestsToday||0)*(service.errorRate||0)/100,0),errors=Math.round(weightedErrors);
        const errorRate=requests?Math.min(100,weightedErrors/requests*100):0;
        return `<button class="widget-analytics" data-view="telemetry"><span class="ui-status" data-state="${errors?'warning':'success'}">${active} / ${telemetry.length} 수신</span><span class="widget-stat-grid"><span><b>${requests.toLocaleString()}</b><small>요청</small></span><span><b>${users.toLocaleString()}</b><small>사용자</small></span><span><b>${errors.toLocaleString()}</b><small>오류</small></span></span>${window.WorkspaceUI.progress(errorRate,'오류율',errors?'warning':'success')}</button>`;
      }
      case 'services-status':{
        if(!serviceItems.length)return window.WorkspaceUI.emptyState('서비스 없음','Services에서 연결하세요.','server');
        const count=state=>serviceItems.filter(item=>item.health?.state===state).length;
        return `<button class="widget-analytics" data-view="services"><span class="widget-stat-grid"><span><b>${count('HEALTHY')}</b><small>Healthy</small></span><span><b>${count('DEGRADED')}</b><small>Degraded</small></span><span><b>${count('DOWN')}</b><small>Down</small></span></span></button>${compact?'':serviceItems.slice(0,4).map(item=>`<button class="widget-row" data-service-open="${e(item.id)}"><b>${e(item.name)}</b><span class="ui-status" data-state="${item.health?.state==='HEALTHY'?'success':item.health?.state==='DOWN'?'danger':'warning'}">${e(item.health?.state||'UNKNOWN')}</span></button>`).join('')}`;
      }
      case 'database-status':return `<button class="widget-analytics" data-view="databases"><b>${databaseItems.filter(item=>item.connected).length} / ${databaseItems.length}</b><small>Connected</small></button>${compact?'':databaseItems.slice(0,3).map(item=>`<button class="widget-row" data-database-open="${e(item.id)}"><span class="ui-status" data-state="${item.connected?'success':'info'}">${item.connected?'●':'○'}</span><b>${e(item.name)}</b></button>`).join('')}`;
      case 'github-status':{
        if(githubLoading)return window.WorkspaceUI.skeleton(2);
        if(!github?.authenticated)return window.WorkspaceUI.emptyState(github?.error?'GitHub 조회 실패':'GitHub 로그인 필요','','git');
        const overview=github.overview;if(!overview)return window.WorkspaceUI.emptyState('Owner 없음','','git');
        const failed=github.run?.conclusion==='failure';
        return `<button class="widget-analytics" data-view="github"><span class="widget-stat-grid"><span><b>${overview.openPullRequests?.length||0}</b><small>PR</small></span><span><b>${overview.openIssues?.length||0}</b><small>Issue</small></span><span><b>${overview.repositories?.length||0}</b><small>Repo</small></span></span><span class="ui-status" data-state="${failed?'danger':'success'}">${github.run?failed?'최근 CI 실패':'최근 CI '+e(github.run.conclusion||github.run.status||'진행 중'):'최근 CI 없음'}</span></button>`;
      }
      case 'today':return calendarLoading?window.WorkspaceUI.skeleton(2):calendarError?window.WorkspaceUI.emptyState('일정 조회 실패',calendarError,'warning')+'<button data-launcher="refresh-widgets">다시 시도</button>':rows(today.slice(0,3).map(event=>`<button class="widget-row" data-view="calendar"><b>${e(event.title)}</b><small>${event.allDay?'종일':e(event.start.slice(11,16))}</small></button>`));
      case 'connections':case 'recent-files':return rows(state.activity.filter(entry=>entry.kind===(item.widgetId==='connections'?'TERMINAL':'FILES')).slice(0,3).map(entry=>`<button class="widget-row" data-open="${entry.kind}" data-target="${e(entry.targetId)}" data-path="${e(entry.path||'/')}"><b>${e(entry.label)}</b><small>${e(entry.path||'터미널 연결')}</small></button>`));
      case 'project':{let saved;try{saved=JSON.parse(localStorage.getItem('workspace-studio-project-v1'));}catch{}return `<button class="widget-row" data-view="studio"><b>${e(saved?.root||'프로젝트 열기')}</b><span class="ui-status" data-state="${studio.changes?'warning':'success'}">${e(studio.branch||'브랜치 미확인')} · ${Number(studio.changes)||0} 변경</span></button>`;}
      case 'codex':return `<button class="widget-row" data-view="studio"><span class="ui-status" data-state="${studio.codex?'info':'success'}">${e(studio.codex||'대기 중')}</span>${compact?'':'<small>Codex 작업</small>'}</button>`;
      default:return '';
    }}
  };
})();
