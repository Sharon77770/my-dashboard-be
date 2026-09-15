const {JSDOM}=require('jsdom');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..');
const dom=new JSDOM(fs.readFileSync(path.join(root,'src/main/resources/templates/home.html'),'utf8'),{runScripts:'outside-only',url:'http://localhost'});
const w=dom.window,d=w.document;
w.HTMLDialogElement.prototype.showModal=function(){this.open=true;};w.HTMLDialogElement.prototype.close=function(){this.open=false;};
w.matchMedia=()=>({matches:false,addEventListener(){}});
w.ResizeObserver=class{observe(){}disconnect(){}};w.setInterval=()=>0;
w.workspaceInitial={devices:[],applications:[],clips:[],bookmarks:[],activity:[],preferences:{theme:'dark',compact:true,terminalFont:13,clipMinutes:60},browserSettings:{mode:'CLIENT'},tabs:[]};
let events=[],terms=[],courses=[];const calls=[];
w.fetch=async(url,opt={})=>{const method=opt.method||'GET',body=opt.body?JSON.parse(opt.body):null;calls.push({url,method,body});let data=null,status=200;
if(url==='/api/v1/workspace')data=w.workspaceInitial;
else if(url.startsWith('/api/v1/calendar/events')){if(method==='GET')data=events;else if(method==='POST'){data={id:'event-1',...body};events=[data];}else if(method==='PUT'){data={id:'event-1',...body};events=[data];}else{events=[];status=204;}}
else if(url==='/api/v1/timetables'){if(method==='POST'){data={id:'term-1',...body};terms=[data];}else data=terms;}
else if(url.includes('/courses')){if(method==='POST'){data={id:'course-1',termId:'term-1',...body};courses=[data];}else if(method==='PUT'){data={id:'course-1',termId:'term-1',...body};courses=[data];}else{courses=[];status=204;}}
else if(url.includes('/timetables/'))data={term:terms[0],courses,totalCredits:courses.reduce((sum,c)=>sum+c.credits,0)};
return {ok:true,status,headers:{get:()=>status===204?null:'application/json'},json:async()=>data};};
const tick=()=>new Promise(r=>setTimeout(r,20));
const click=s=>{assert.ok(d.querySelector(s),s);d.querySelector(s).click();};
const set=(name,value)=>{const input=d.querySelector('#editor-form [name='+name+']');assert.ok(input,name);input.value=value;};
const submit=async()=>{d.querySelector('#editor-form').dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));await tick();};
(async()=>{
for(const file of ['ui.js','launcher/app-registry.js','launcher/grid-model.js','launcher/persistence.js','launcher/widget-registry.js','launcher/interactions.js','launcher/launcher.js','planner.js'])w.eval(fs.readFileSync(path.join(root,'src/main/resources/static/js',file),'utf8'));
w.eval(fs.readFileSync(path.join(root,'src/main/resources/static/js/workspace.js'),'utf8'));
const initialCalendarReads=calls.filter(c=>c.url.includes('/calendar/events?')).length;click('[data-view=calendar]');await tick();assert.equal(d.querySelectorAll('.calendar-day').length,42);
click('[data-plan=month-next]');await tick();assert.equal(calls.filter(c=>c.url.includes('/calendar/events?')).length,initialCalendarReads+2);
click('[data-plan=event-new]');set('title','과제 <img src=x>');const check=d.querySelector('[name=allDay]');check.checked=true;check.dispatchEvent(new w.Event('change'));
assert.equal(d.querySelector('[name=start]').type,'date');set('start','2026-10-01');set('end','2026-10-03');await submit();
const saved=calls.find(c=>c.method==='POST'&&c.url.includes('/calendar/events')).body;assert.equal(saved.end,'2026-10-04T00:00');assert.equal(saved.start,'2026-10-01T00:00');assert.equal(d.querySelectorAll('#calendar img').length,0);
click('[data-plan=event-edit]');assert.equal(d.querySelector('[name=end]').value,'2026-10-03');click('#editor-dialog [data-action=dialog-close]');
click('[data-view=timetable]');await tick();assert.ok(d.querySelector('.planner-welcome'));
click('[data-plan=term-new]');await submit();assert.equal(d.querySelectorAll('.weekday-column').length,7);
click('[data-plan=slot-new][data-day="7"]');set('title','일요일 세미나');click('#add-meeting');assert.equal(d.querySelectorAll('.meeting-row').length,2);await submit();
assert.equal(courses[0].meetings[0].day,7);assert.equal(d.querySelectorAll('.course-block').length,2);assert.ok(d.querySelector('.semester-summary').textContent.includes('3 학점'));
click('[data-plan=course-edit]');click('#delete-planner-item');await submit();assert.equal(d.querySelectorAll('.course-block').length,0);
console.log('PASS: calendar navigation, inclusive all-day form, escaped titles, semester creation, seven-day grid, multi-slot courses, credit summary and deletion');w.close();
})().catch(e=>{console.error(e);w.close();process.exitCode=1;});
