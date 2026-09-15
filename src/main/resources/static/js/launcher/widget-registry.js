'use strict';
/** Widgets expose summaries and existing app actions; they never run a command implicitly. */
window.WorkspaceWidgets = (() => {
  const e=value=>window.WorkspaceUI.escape(value);
  const definitions=[
    {id:'search',appId:'search',name:'Workspace 검색',sizes:[[4,1],[8,1]],defaultSize:[4,1],description:'앱, 장비, 파일, 최근 작업을 한 곳에서 검색합니다.'},
    {id:'device-status',appId:'devices',name:'장비 상태',sizes:[[2,1],[4,2]],defaultSize:[4,2],description:'마지막으로 측정한 CPU·RAM과 빠른 연결을 표시합니다.'},
    {id:'today',appId:'calendar',name:'오늘 일정',sizes:[[2,2],[4,2]],defaultSize:[4,2],description:'오늘의 일정과 시작 시간을 표시합니다.'},
    {id:'connections',appId:'terminal',name:'최근 연결',sizes:[[2,2],[4,2]],defaultSize:[2,2],description:'최근 터미널 연결을 바로 다시 엽니다.'},
    {id:'recent-files',appId:'files',name:'최근 파일',sizes:[[2,2],[4,2]],defaultSize:[2,2],description:'최근에 탐색한 서버 파일 위치를 엽니다.'},
    {id:'project',appId:'studio',name:'최근 프로젝트',sizes:[[2,1],[4,2]],defaultSize:[4,2],description:'최근 작업 폴더와 이 세션에서 확인한 Git 상태입니다.'},
    {id:'codex',appId:'studio',name:'Codex 작업',sizes:[[2,1],[4,2]],defaultSize:[2,1],description:'현재 브라우저 세션의 마지막 Codex 실행 상태입니다.'}
  ];
  let today=[],calendarError='',calendarLoading=false,studio={};
  const day=date=>`${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
  function rows(items){return items.join('')||'<p class="widget-empty">아직 표시할 정보가 없습니다.</p>';}
  return {
    all(){return definitions.filter(def=>window.WorkspaceApps.get(def.appId)?.widgets?.includes(def.id));},get(id){return definitions.find(item=>item.id===id);},
    updateStudio(detail){studio=detail;},
    async refresh(api){calendarLoading=true;try{const now=new Date(),next=new Date(now);next.setDate(next.getDate()+1);today=await api(`/calendar/events?from=${day(now)}&to=${day(next)}`);calendarError='';}catch(error){calendarError=error.message;}finally{calendarLoading=false;}},
    render(item,state,statuses){const compact=item.h===1;switch(item.widgetId){
      case 'search':return '<button class="widget-search" data-action="palette">'+window.WorkspaceUI.icon('search')+'<span>앱, 파일, 장비 검색</span><kbd>Ctrl K</kbd></button>';
      case 'device-status':{const online=state.devices.filter(device=>statuses.get(device.id)?.state==='ONLINE').length;if(compact)return `<button data-view="devices" class="widget-value">${online} online <small>/ ${state.devices.length} 장비 · 마지막 측정</small></button>`;return rows(state.devices.slice(0,3).map(device=>{const status=statuses.get(device.id);return `<button class="widget-row" data-open="TERMINAL" data-target="${e(device.id)}"><span>${e(device.name)}</span><small>${e(status?.state||'미확인')} · CPU ${status?.cpu==null?'—':Math.round(status.cpu)+'%'} · RAM ${status?.memory==null?'—':Math.round(status.memory)+'%'}</small></button>`;}))+'<button class="ghost sm" data-action="refresh-status">상태 새로고침</button>';}
      case 'today':return calendarLoading?'<p class="loading">일정 조회 중</p>':calendarError?`<p class="error-state">${e(calendarError)}</p><button data-launcher="refresh-widgets">다시 시도</button>`:rows(today.slice(0,3).map(event=>`<button class="widget-row" data-view="calendar"><b>${e(event.title)}</b><small>${event.allDay?'종일':e(event.start.slice(11,16))}</small></button>`));
      case 'connections':case 'recent-files':return rows(state.activity.filter(entry=>entry.kind===(item.widgetId==='connections'?'TERMINAL':'FILES')).slice(0,3).map(entry=>`<button class="widget-row" data-open="${entry.kind}" data-target="${e(entry.targetId)}" data-path="${e(entry.path||'/')}"><b>${e(entry.label)}</b><small>${e(entry.path||'터미널 연결')}</small></button>`));
      case 'project':{let saved;try{saved=JSON.parse(localStorage.getItem('workspace-studio-project-v1'));}catch{}return `<button class="widget-row" data-view="studio"><b>${e(saved?.root||'프로젝트 열기')}</b><small>${e(studio.branch?studio.branch+' · '+studio.changes+'개 변경':'Git 상태는 에디터에서 확인')}</small></button>`;}
      case 'codex':return `<button class="widget-row" data-view="studio"><b>${e(studio.codex||'대기 중')}</b>${compact?'':'<small>이 브라우저 세션 · 결과는 에디터에서 확인</small>'}</button>`;
      default:return '';
    }}
  };
})();
