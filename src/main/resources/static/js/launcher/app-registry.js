'use strict';
/** @typedef {{id:string,name:string,icon:string,route?:string,action?:string,kind?:string,targetId?:string,widgets?:string[]}} WorkspaceApp */
window.WorkspaceApps = (() => {
  const builtins = [
    {id:'devices',name:'장비',icon:'devices',route:'devices',widgets:['device-status']},
    {id:'notes',name:'메모장',icon:'clip',route:'notes'},
    {id:'cloud',name:'클라우드 드라이브',icon:'files',route:'cloud'},
    {id:'files',name:'파일',icon:'files',route:'files',widgets:['recent-files']},
    {id:'logs',name:'장비 로그',icon:'terminal',route:'logs'},
    {id:'terminal',name:'터미널',icon:'terminal',route:'terminal',widgets:['connections']},
    {id:'remote',name:'원격',icon:'remote',route:'remote'},
    {id:'apps',name:'앱 관리',icon:'apps',route:'apps'},
    {id:'calendar',name:'캘린더',icon:'calendar',route:'calendar',widgets:['today']},
    {id:'timetable',name:'시간표',icon:'timetable',route:'timetable'},
    {id:'studio',name:'코드 에디터',icon:'studio',route:'studio',widgets:['project','codex']},
    {id:'recent',name:'최근 작업',icon:'recent',route:'recent'},
    {id:'clipboard',name:'클립보드',icon:'clip',route:'clipboard'},
    {id:'tailscale-settings',name:'Tailscale 설정',icon:'devices',action:'tailscale-settings'},
    {id:'browser-settings',name:'브라우저 설정',icon:'browser',action:'browser-settings'},
    {id:'settings',name:'설정',icon:'settings',action:'settings'},
    {id:'search',name:'검색 및 명령',icon:'search',action:'palette',widgets:['search']}
  ];
  let applications = builtins;
  return {
    sync(state){applications=[...builtins,...state.applications.map(app=>({id:`web:${app.id}`,name:app.name,icon:'browser',kind:'APP',targetId:app.id}))];},
    all(){return [...applications].sort((a,b)=>a.name.localeCompare(b.name,'ko'));},
    get(id){return applications.find(app=>app.id===id);},
    search(query){return this.all().filter(app=>`${app.name} ${app.id}`.toLowerCase().includes(query.trim().toLowerCase()));},
    attributes(app){const e=window.WorkspaceUI.escape;return app.route?`data-view="${e(app.route)}"`:app.action?`data-action="${e(app.action)}"`:`data-open="${e(app.kind)}" data-target="${e(app.targetId)}"`;}
  };
})();
