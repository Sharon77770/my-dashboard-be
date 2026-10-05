const {JSDOM}=require('jsdom'),fs=require('node:fs'),assert=require('node:assert/strict');
const dom=new JSDOM(fs.readFileSync('src/main/resources/templates/home.html','utf8'),{runScripts:'outside-only',url:'http://localhost',pretendToBeVisual:true});
const w=dom.window,d=w.document,clone=value=>JSON.parse(JSON.stringify(value));
w.matchMedia=()=>({matches:false,addEventListener(){}});w.ResizeObserver=class{observe(){}disconnect(){}};
w.HTMLDialogElement.prototype.showModal=function(){this.open=true;};w.HTMLDialogElement.prototype.close=function(){this.open=false;this.dispatchEvent(new w.Event('close'));};
let elapsed=0;w.performance.now=()=>elapsed;let intervalId=0;const intervals=new Map();w.setInterval=(callback,ms)=>{intervals.set(++intervalId,{callback,ms});return intervalId;};w.clearInterval=id=>intervals.delete(id);
w.workspaceInitial={devices:[],applications:[],clips:[],bookmarks:[],activity:[],preferences:{theme:'dark',compact:true,terminalFont:13,clipMinutes:60},browserSettings:{mode:'CLIENT'},tabs:[]};
const baseTime=Date.parse('2026-10-05T03:00:00Z'),calls=[];let profile=null,events=[],holdRead=null;
function dashboard(){
 const start=profile?Date.parse(profile.enlistmentDate+'T00:00:00+09:00'):0,end=profile?Date.parse(profile.dischargeDate+'T00:00:00+09:00')+86400000:1;
 return {profile,events,currentRank:'Registered rank',serverNow:new Date(baseTime+elapsed).toISOString(),timeZone:'Asia/Seoul',sources:[{title:'MMA',url:'https://www.mma.go.kr/',checkedOn:'2026-10-05'}],serviceTypes:[{id:'ARMY',label:'Army',months:18},{id:'SOCIAL_SERVICE',label:'Social service',months:21},{id:'CUSTOM',label:'Custom',months:null}],milestones:profile?[{id:'enlistment',title:'Enlistment',kind:'ENLISTMENT',date:profile.enlistmentDate,daysUntil:-277,reached:true},{id:'discharge',title:'Discharge',kind:'DISCHARGE',date:profile.dischargeDate,daysUntil:268,reached:false}]:[],progress:profile?{status:'SERVING',startsAt:start,endsAt:end,totalDays:546,elapsedDays:277,remainingDays:269,serviceDay:278,daysToDischarge:268,percent:50,nextDayAt:Date.parse('2026-10-06T00:00:00+09:00')}:null,leave:{allowance:profile?.leaveAllowance??null,used:0,planned:events.reduce((sum,item)=>sum+item.leaveDays,0),remaining:profile?.leaveAllowance??null}};
}
w.fetch=async(url,options={})=>{
 const method=options.method||'GET',body=options.body?JSON.parse(options.body):null;calls.push({url,method,body});let result=[],status=200;
 if(url==='/api/v1/workspace')result=w.workspaceInitial;
 else if(url==='/api/v1/github/status')result={authenticated:false};
 else if(url==='/api/v1/military'){
  result=clone(dashboard());if(holdRead){const wait=holdRead;holdRead=null;await wait;}
 }else if(url==='/api/v1/military/profile'&&method==='PUT'){
  if(body.revision!==(profile?.revision||0)){status=409;result={message:'revision conflict'};}
  else{profile={...body,dischargeDate:body.dischargeDate||'2027-06-30',estimatedDischarge:!body.dischargeDate,revision:body.revision+1};result=dashboard();}
 }else if(url.startsWith('/api/v1/military/profile?')&&method==='DELETE'){profile=null;events=[];status=204;}
 else if(url==='/api/v1/military/events'&&method==='POST'){const item={...body,id:'event-one',leaveDays:body.leaveDays??2,revision:1};events.push(item);result=item;status=201;}
 else if(url.startsWith('/api/v1/military/events/event-one')){if(method==='DELETE'){events=[];status=204;}else {events=[{...body,id:'event-one',revision:body.revision+1}];result=events[0];}}
 else if(url.startsWith('/api/v1/calendar/events?'))result=events.map(item=>({id:'military:'+item.id,title:item.title,start:item.startDate+'T00:00:00',end:'2026-10-08T00:00:00',allDay:true,color:'#7597eb',source:'MILITARY',sourceId:item.id}));
 return {ok:status<400,status,headers:{get:()=>status===204?null:'application/json'},json:async()=>clone(result)};
};
for(const name of ['ui','live-dom','launcher/app-registry','launcher/grid-model','launcher/persistence','launcher/widget-registry','launcher/interactions','launcher/launcher','planner','military','workspace'])w.eval(fs.readFileSync('src/main/resources/static/js/'+name+'.js','utf8'));
const tick=()=>new Promise(resolve=>setTimeout(resolve,20));
async function settled(){for(let i=0;i<100;i++){await tick();if(d.documentElement.dataset.loading!=='true')return;}throw Error('Loading did not complete');}
const click=selector=>{const node=d.querySelector(selector);assert.ok(node,selector);node.click();};
const set=(name,value)=>{const input=d.querySelector('#editor-form [name="'+name+'"]');assert.ok(input,name);input.value=value;};
const submit=async()=>{d.querySelector('#editor-form').dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));await settled();};
(async()=>{
 await settled();assert.ok(w.WorkspaceApps.get('military'));click('[data-view=calendar]');await settled();click('#calendar [data-view=military]');await settled();
 assert.equal(d.body.dataset.activeView,'military');assert.ok(d.querySelector('.military-welcome'));
 click('[data-military=profile]');set('nickname','<img src=x> My service');set('enlistmentDate','2026-01-01');set('leaveAllowance','30');await submit();
 assert.equal(d.querySelectorAll('#military img').length,0);assert.ok(d.querySelector('.military-overview'));assert.equal(d.querySelectorAll('.military-day').length,42);assert.equal(calls.find(call=>call.url==='/api/v1/military/profile').body.dischargeDate,null);
 const grid=d.querySelector('.military-month-grid'),before=d.querySelector('[data-military-percent]').textContent;elapsed+=1000;[...intervals.values()].find(item=>item.ms===1000).callback();assert.notEqual(d.querySelector('[data-military-percent]').textContent,before);assert.equal(d.querySelector('.military-month-grid'),grid);
 const sources=d.querySelector('.military-sources');sources.open=true;await w.WorkspaceMilitary.refresh();assert.equal(d.querySelector('.military-sources'),sources);assert.equal(sources.open,true);assert.equal(d.querySelector('#workspace-activity').hidden,true);
 click('[data-military=new-event][data-kind=LEAVE]');set('title','<script>leave</script>');set('startDate','2026-10-06');set('endDate','2026-10-07');set('leaveDays','1');await submit();assert.equal(events[0].leaveDays,1);assert.equal(d.querySelectorAll('#military script').length,0);assert.ok(d.querySelector('[data-military=edit-event]'));
 click('#military [data-military=calendar]');await settled();assert.equal(d.body.dataset.activeView,'calendar');click('#calendar [data-plan=event-edit]');await settled();assert.equal(d.body.dataset.activeView,'military');assert.equal(d.querySelector('#editor-form [name=title]').value,'<script>leave</script>');
 click('#editor-dialog [data-action=dialog-close]');click('#military [data-military=profile]');set('nickname','local draft');const input=d.querySelector('#editor-form [name=nickname]');input.focus();profile={...profile,nickname:'Remote',revision:2};await w.WorkspaceMilitary.refresh();assert.equal(input.value,'local draft');assert.equal(d.activeElement,input);await submit();assert.equal(d.querySelector('#editor-dialog').open,true);assert.match(d.querySelector('#editor-error').textContent,/revision conflict/);assert.equal(input.value,'local draft');
 click('#editor-dialog [data-action=dialog-close]');click('#military [data-military=profile]');const type=d.querySelector('#editor-form [name=serviceType]');type.value='CUSTOM';type.dispatchEvent(new w.Event('change'));assert.equal(d.querySelector('#editor-form [name=dischargeDate]').required,true);assert.equal(d.querySelector('#editor-form [name=privateFirstDate]').disabled,true);click('#editor-dialog [data-action=dialog-close]');
 // An old background response arriving after a successful save cannot regress the profile.
 click('#military [data-military=profile]');set('nickname','Newest');let release;holdRead=new Promise(resolve=>release=resolve);const oldRead=w.WorkspaceMilitary.refresh();await submit();assert.match(d.querySelector('.military-overview h2').textContent,/Newest/);release();await oldRead;assert.match(d.querySelector('.military-overview h2').textContent,/Newest/);
 click('#military [data-military=calendar]');await settled();assert.equal([...intervals.values()].some(item=>item.ms===1000),false,'clock stops when leaving military view');
 console.log('PASS military UI: discovery, setup, live clock, stable DOM, safe text, leave, linked calendar, conflict draft, custom dates and stale-response protection');w.dispatchEvent(new w.Event('pagehide'));w.close();
})().catch(error=>{console.error(error);w.close();process.exitCode=1;});
