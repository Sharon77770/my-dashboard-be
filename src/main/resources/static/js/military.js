'use strict';
/** Personal military calendar. Server dates own the rules; the browser interpolates the clock only. */
window.WorkspaceMilitary=(()=>{
  let ui,root,data=null,month='',selectedDay='',timer=null,loading=null,queuedEvent=null,clockAt=0,receivedAt=0,generation=0,retryAt=0;
  const kinds={LEAVE:'휴가',TRAINING:'훈련',DUTY:'근무',OTHER:'기타'};
  const weekdays=['월','화','수','목','금','토','일'];
  const e=value=>ui.escape(value),$=selector=>root.querySelector(selector);
  const iso=date=>date.toISOString().slice(0,10);
  const addDays=(value,count)=>{const date=new Date(value+'T12:00:00Z');date.setUTCDate(date.getUTCDate()+count);return iso(date);};
  const seoulDay=instant=>new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Seoul',year:'numeric',month:'2-digit',day:'2-digit'}).format(instant);
  const dayLabel=value=>value?value.replaceAll('-','. '):'미등록';
  const dday=days=>days===0?'D-DAY':days>0?'D-'+days:'D+'+Math.abs(days);
  const paint=(target,html)=>window.WorkspaceLiveDOM?window.WorkspaceLiveDOM.patch(target,html):target.innerHTML=html;
  const field=(name,label,value,type='text',extra='')=>ui.fields.input(name,label,value??'',type,extra);
  const dateField=(name,label,value,required=false)=>field(name,label,value,'date',`${required?'required ':''}min="1900-01-01" max="2199-12-31"`);

  async function load(options={}){
    if(loading){if(options.quiet)return loading;await loading.catch(()=>{});}
    const version=++generation;
    const request=(async()=>{
      const next=await ui.api('/military','GET',undefined,options);
      if(version!==generation)return;
      data=next;clockAt=Date.parse(next.serverNow);receivedAt=performance.now();retryAt=0;
      if(!month){selectedDay=seoulDay(clockAt);month=selectedDay.slice(0,7);}
      render();tick();
    })().finally(()=>{if(loading===request)loading=null;});
    loading=request;return request;
  }

  function sources(){return `<details class="military-sources"><summary>계산 기준과 참고 자료</summary><p>복무율은 한국 시간 입대일 0시부터 전역일이 끝나는 시점까지 계산합니다. 자동 전역일은 기본 복무기간으로 계산한 예상일이며, 실제 통보받은 날짜로 수정할 수 있습니다. 진급일과 휴가 배정은 본인에게 안내된 내용을 입력하세요.</p><p>휴가의 양 끝 날짜를 포함합니다. 실제 차감일수가 다르면 일정에서 직접 조정하세요. 진급일을 등록하지 않은 계급은 추정하지 않습니다.</p><ul>${data.sources.map(source=>`<li><a href="${e(source.url)}" target="_blank" rel="noopener noreferrer">${e(source.title)}</a><small> · 확인 ${e(source.checkedOn)}</small></li>`).join('')}</ul><p>입력한 복무 정보를 이 대시보드에 저장합니다.</p></details>`;}

  function render(){
    if(!data)return;
    const profile=data.profile;
    if(!profile){
      paint(root,`<div class="page-head"><div><span class="eyebrow">MY SERVICE</span><h1>병역 캘린더</h1></div><button data-military="calendar">전체 캘린더</button></div><section class="military-welcome"><span class="military-welcome-icon">${window.WorkspaceUI.icon('calendar')}</span><h2>나의 군생활, 하루씩 가까워지는 전역</h2><p>입대일과 복무 유형을 등록하면 복무율과 남은 기간을 실시간으로 볼 수 있습니다.<br>휴가·훈련·진급 일정을 기존 캘린더와 함께 관리하세요.</p><button class="primary" data-military="profile">복무 정보 등록</button></section>${sources()}`);
      return;
    }
    const progress=data.progress,status={UPCOMING:'입대 예정',SERVING:'복무 중',COMPLETED:'복무 완료'}[progress.status];
    const type=data.serviceTypes.find(item=>item.id===profile.serviceType)?.label||profile.serviceType;
    const next=data.milestones.find(item=>!item.reached);
    const leave=data.leave;
    paint(root,`<div class="page-head"><div><span class="eyebrow">MY SERVICE</span><h1>병역 캘린더</h1></div><div class="actions"><button data-military="calendar">전체 캘린더</button><button data-military="profile">복무 정보 수정</button></div></div>
      <section class="military-overview" aria-label="나의 복무 현황"><header><div><h2>${e(profile.nickname)}</h2><span>${e(type)} · ${e(data.currentRank)}</span></div><span class="ui-status" data-state="${progress.status==='COMPLETED'?'success':'info'}">${status}</span></header>
      <div class="military-progress-row"><div><small>지금까지의 군생활</small><div class="military-percent"><strong data-military-percent>${progress.percent.toFixed(6)}</strong><span>%</span></div></div><div class="military-countdown"><small>${profile.serviceType==='SOCIAL_SERVICE'?'소집해제':'전역'}까지${profile.estimatedDischarge?' · 예상':''}</small><strong>${dday(progress.daysToDischarge)}</strong><span data-military-countdown></span></div></div>
      <div class="military-progress-track" role="progressbar" aria-label="복무 진행률" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${progress.percent.toFixed(2)}"><i data-military-bar style="width:${progress.percent}%"></i></div>
      <dl class="military-facts"><div><dt>입대·소집일</dt><dd>${dayLabel(profile.enlistmentDate)}</dd></div><div><dt>${profile.estimatedDischarge?'예상 ':''}전역·소집해제일</dt><dd>${dayLabel(profile.dischargeDate)}</dd></div><div><dt>복무한 날 / 전체</dt><dd>${progress.elapsedDays} / ${progress.totalDays}일</dd></div><div><dt>오늘을 포함해 남은 날</dt><dd>${progress.remainingDays}일</dd></div></dl>
      <p class="military-next">${next?`다음 이정표 <b>${e(next.title)}</b> ${dayLabel(next.date)} · ${dday(next.daysUntil)}`:'모든 이정표를 지났습니다.'}${!profile.privateFirstDate&&profile.serviceType!=='SOCIAL_SERVICE'&&profile.serviceType!=='CUSTOM'?' · 복무 정보에서 실제 진급일을 추가할 수 있습니다.':''}</p></section>
      <ol class="military-milestones" aria-label="복무 이정표">${data.milestones.map(item=>`<li data-live-key="${e(item.id)}" class="${item.reached?'reached':''}"><button data-military="focus-date" data-date="${item.date}"><span>${e(item.title)}</span><b>${dayLabel(item.date)}</b><small>${dday(item.daysUntil)}</small></button></li>`).join('')}</ol>
      <section class="military-leave" aria-label="휴가 현황"><div><small>휴가 배정</small><b>${leave.allowance==null?'미등록':leave.allowance+'일'}</b></div><div><small>사용·진행 중</small><b>${leave.used}일</b></div><div><small>예정</small><b>${leave.planned}일</b></div><div><small>${leave.remaining!=null&&leave.remaining<0?'배정 초과':'계획 후 남은 휴가'}</small><b>${leave.remaining==null?'—':Math.abs(leave.remaining)+'일'}</b></div><button data-military="new-event" data-kind="LEAVE">휴가 등록</button></section>
      <div class="military-calendar-layout"><section class="military-month" aria-label="병역 월간 일정"><header><div class="actions"><button data-military="previous" aria-label="이전 달">${window.WorkspaceUI.icon('back')}</button><h2>${month.slice(0,4)}년 ${Number(month.slice(5))}월</h2><button data-military="next" aria-label="다음 달">${window.WorkspaceUI.icon('arrowRight')}</button><button data-military="today">오늘</button></div><button class="primary" data-military="new-event">일정 추가</button></header><div class="military-weekdays">${weekdays.map(day=>`<span>${day}</span>`).join('')}</div><div class="military-month-grid">${monthCells()}</div><p class="section-hint">${profile.calendarEnabled?'병역 일정이 전체 캘린더와 오늘 일정 위젯에 함께 표시됩니다.':'전체 캘린더 연동이 꺼져 있습니다. 복무 정보에서 켤 수 있습니다.'}</p></section><aside class="military-agenda" aria-label="선택한 날짜 일정"><h2>${dayLabel(selectedDay)}</h2>${agenda()}</aside></div>${sources()}`);
  }

  function calendarItems(){return [...data.milestones.map(item=>({id:'milestone:'+item.id,title:item.title,startDate:item.date,endDate:item.date,milestone:true})),...data.events];}
  function monthCells(){
    const first=new Date(month+'-01T12:00:00Z'),offset=(first.getUTCDay()+6)%7,from=addDays(month+'-01',-offset),items=calendarItems(),today=seoulDay(clockAt+performance.now()-receivedAt);
    return Array.from({length:42},(_,index)=>{
      const date=addDays(from,index),entries=items.filter(item=>item.startDate<=date&&item.endDate>=date);
      return `<button class="military-day ${date.slice(0,7)!==month?'outside':''} ${date===today?'today':''}" data-live-key="${date}" data-military="day" data-date="${date}" aria-pressed="${date===selectedDay}" aria-label="${date}, 일정 ${entries.length}개"><span class="military-day-number">${Number(date.slice(8))}</span><span class="military-day-events">${entries.slice(0,2).map(item=>`<span class="military-chip" data-kind="${e(item.kind||'MILESTONE')}">${e(item.title)}</span>`).join('')}${entries.length>2?`<small>+${entries.length-2}</small>`:''}</span></button>`;
    }).join('');
  }
  function agenda(){
    const entries=calendarItems().filter(item=>item.startDate<=selectedDay&&item.endDate>=selectedDay);
    if(!entries.length)return window.WorkspaceUI.emptyState('등록한 일정이 없습니다.','휴가·훈련·근무 일정을 추가하세요.','calendar');
    return entries.map(item=>`<article data-live-key="${e(item.id)}" class="military-agenda-item"><small>${item.milestone?'이정표':kinds[item.kind]}</small><h3>${e(item.title)}</h3><p>${dayLabel(item.startDate)}${item.endDate!==item.startDate?' ~ '+dayLabel(item.endDate):''}</p>${item.kind==='LEAVE'?`<p>휴가 차감 ${item.leaveDays}일</p>`:''}${item.notes?`<p class="military-event-notes">${e(item.notes)}</p>`:''}<button data-military="${item.milestone?'profile':'edit-event'}" data-id="${e(item.id)}">${item.milestone?'복무 정보 수정':'일정 수정'}</button></article>`).join('');
  }

  function tick(){
    if(document.hidden||!root?.classList.contains('active')||!data?.progress)return;
    const now=clockAt+performance.now()-receivedAt,progress=data.progress;
    if(now>=progress.nextDayAt&&!loading&&performance.now()>=retryAt){retryAt=performance.now()+30000;load({quiet:true}).catch(error=>ui.toast(error.message));return;}
    const percent=Math.max(0,Math.min(100,(now-progress.startsAt)*100/(progress.endsAt-progress.startsAt))),number=$('[data-military-percent]');
    if(number&&number.textContent!==percent.toFixed(6))number.textContent=percent.toFixed(6);
    const bar=$('[data-military-bar]');if(bar){bar.style.width=percent+'%';bar.parentElement.setAttribute('aria-valuenow',percent.toFixed(2));}
    const remaining=Math.max(0,Math.floor((progress.endsAt-now)/1000)),label=$('[data-military-countdown]');
    if(label)label.textContent=progress.status==='UPCOMING'?'입대를 준비하는 시간':remaining===0?'수고하셨습니다.':`복무 종료까지 ${Math.floor(remaining/86400)}일 ${Math.floor(remaining%86400/3600)}시간 ${Math.floor(remaining%3600/60)}분 ${remaining%60}초`;
  }

  function editProfile(){
    const profile=data.profile||{nickname:'나의 군생활',serviceType:'ARMY',calendarEnabled:true,revision:0};
    ui.editor(profile.revision?'복무 정보 수정':'복무 정보 등록',`<div class="form-grid">${field('nickname','표시 이름',profile.nickname,'text','required maxlength="60"')}${ui.fields.select('serviceType','복무 유형',profile.serviceType,data.serviceTypes.map(type=>[type.id,type.label+(type.months?' · '+type.months+'개월':'')]))}${dateField('enlistmentDate','입대·소집일',profile.enlistmentDate,true)}${dateField('dischargeDate','전역·소집해제일 (선택)',profile.estimatedDischarge?'':profile.dischargeDate)}${field('leaveAllowance','총 휴가 배정일수 (선택)',profile.leaveAllowance,'number','min="0" max="1000"')}</div><p class="section-hint">전역일을 비우면 기본 복무기간으로 예상일을 계산합니다. 직접 설정 유형과 2022년 이전 복무는 실제 전역일을 입력해 주세요.</p><details class="military-promotion-fields"><summary>실제 진급일 등록·조정</summary><p class="section-hint">현역병의 안내받은 날짜를 입력하세요. 입력하지 않은 진급일은 자동으로 확정하지 않습니다.</p><div class="form-grid">${dateField('privateFirstDate','일병 진급일',profile.privateFirstDate)}${dateField('corporalDate','상병 진급일',profile.corporalDate)}${dateField('sergeantDate','병장 진급일',profile.sergeantDate)}</div></details>${ui.fields.check('calendarEnabled','전체 캘린더에 병역 일정 표시',profile.calendarEnabled)}${profile.revision?'<button type="button" class="danger" id="military-delete-profile">복무 정보와 병역 일정 삭제</button>':''}`,async form=>{
      const date=name=>form.get(name)||null;
      generation++;
      const next=await ui.api('/military/profile','PUT',{nickname:form.get('nickname'),serviceType:form.get('serviceType'),enlistmentDate:date('enlistmentDate'),dischargeDate:date('dischargeDate'),privateFirstDate:date('privateFirstDate'),corporalDate:date('corporalDate'),sergeantDate:date('sergeantDate'),leaveAllowance:form.get('leaveAllowance')===''?null:Number(form.get('leaveAllowance')),calendarEnabled:form.has('calendarEnabled'),revision:profile.revision});
      data=next;clockAt=Date.parse(next.serverNow);receivedAt=performance.now();render();tick();ui.toast('복무 정보를 저장했습니다.');
    });
    const form=document.querySelector('#editor-form'),type=form.querySelector('[name=serviceType]');
    const update=()=>{const soldier=!['SOCIAL_SERVICE','CUSTOM'].includes(type.value);form.querySelector('.military-promotion-fields').hidden=!soldier;for(const name of ['privateFirstDate','corporalDate','sergeantDate'])form.querySelector(`[name=${name}]`).disabled=!soldier;form.querySelector('[name=dischargeDate]').required=type.value==='CUSTOM';};
    type.addEventListener('change',update);update();
    const remove=document.querySelector('#military-delete-profile');if(remove)remove.onclick=()=>ui.confirmAction('복무 정보 삭제',`복무 정보와 병역 일정 ${data.events.length}개를 삭제합니다. 일반 캘린더 일정은 유지됩니다.`,async()=>{await ui.api('/military/profile?revision='+profile.revision,'DELETE');await load();});
  }

  function editEvent(id,kind='OTHER'){
    const profile=data.profile;if(!profile)return editProfile();
    const date=selectedDay<profile.enlistmentDate?profile.enlistmentDate:selectedDay>profile.dischargeDate?profile.dischargeDate:selectedDay;
    const event=data.events.find(item=>item.id===id)||{kind,title:kind==='LEAVE'?'휴가':'',startDate:date,endDate:date,notes:'',leaveDays:null,revision:0};
    ui.editor(id?'병역 일정 수정':'병역 일정 추가',`<div class="form-grid">${ui.fields.select('kind','종류',event.kind,Object.entries(kinds))}${field('title','일정 이름',event.title,'text','required maxlength="120"')}${dateField('startDate','시작일',event.startDate,true)}${dateField('endDate','종료일 (포함)',event.endDate,true)}${field('leaveDays','휴가 차감일수 (비우면 기간 일수)',event.leaveDays,'number','min="0" max="366"')}</div><label>메모<textarea name="notes" rows="3" maxlength="2000">${e(event.notes)}</textarea></label><p class="section-hint">복무기간 안의 일정만 등록할 수 있습니다. 주말·공휴일 등 차감 방식이 다르면 실제 차감일수를 입력하세요.</p>${id?'<button type="button" class="danger" id="military-delete-event">일정 삭제</button>':''}`,async form=>{
      await ui.api(id?'/military/events/'+encodeURIComponent(id):'/military/events',id?'PUT':'POST',{kind:form.get('kind'),title:form.get('title'),startDate:form.get('startDate'),endDate:form.get('endDate'),leaveDays:form.get('kind')==='LEAVE'&&form.get('leaveDays')!==''?Number(form.get('leaveDays')):null,notes:form.get('notes'),revision:event.revision});
      selectedDay=form.get('startDate');month=selectedDay.slice(0,7);await load();ui.toast('병역 일정을 저장했습니다.');
    });
    const form=document.querySelector('#editor-form'),type=form.querySelector('[name=kind]'),leave=form.querySelector('[name=leaveDays]');
    const update=()=>{leave.disabled=type.value!=='LEAVE';leave.closest('label').hidden=leave.disabled;};type.addEventListener('change',update);update();
    for(const name of ['startDate','endDate']){const input=form.querySelector(`[name=${name}]`);input.min=profile.enlistmentDate;input.max=profile.dischargeDate;}
    const remove=document.querySelector('#military-delete-event');if(remove)remove.onclick=()=>ui.confirmAction('병역 일정 삭제','이 일정을 삭제할까요?',async()=>{await ui.api('/military/events/'+encodeURIComponent(id)+'?revision='+event.revision,'DELETE');await load();});
  }

  async function click(event){
    const button=event.target.closest('[data-military]');if(!button)return;
    switch(button.dataset.military){
      case 'profile':editProfile();break;
      case 'new-event':editEvent(null,button.dataset.kind||'OTHER');break;
      case 'edit-event':editEvent(button.dataset.id);break;
      case 'day':selectedDay=button.dataset.date;render();tick();break;
      case 'focus-date':selectedDay=button.dataset.date;month=selectedDay.slice(0,7);render();tick();break;
      case 'today':selectedDay=seoulDay(clockAt+performance.now()-receivedAt);month=selectedDay.slice(0,7);render();tick();break;
      case 'previous':case 'next':{const date=new Date(month+'-01T12:00:00Z');date.setUTCMonth(date.getUTCMonth()+(button.dataset.military==='previous'?-1:1));if(date.getUTCFullYear()<1900||date.getUTCFullYear()>2199)return;month=iso(date).slice(0,7);selectedDay=month+'-01';render();tick();break;}
      case 'calendar':window.WorkspacePlanner?.focusDate(selectedDay);window.dispatchEvent(new CustomEvent('assistant:navigate',{detail:{route:'calendar'}}));break;
      case 'retry':await load();break;
    }
  }
  return {
    init(shared){ui=shared;root=document.querySelector('#military');if(!root)return;root.addEventListener('click',event=>click(event).catch(error=>ui.toast(error.message)));window.addEventListener('workspace:view',event=>{clearInterval(timer);timer=null;if(event.detail.id==='military'){tick();timer=setInterval(tick,1000);}});window.addEventListener('pagehide',()=>{clearInterval(timer);timer=null;});window.addEventListener('pageshow',event=>{if(event.persisted&&root.classList.contains('active')){clearInterval(timer);timer=setInterval(tick,1000);load({quiet:true}).catch(error=>ui.toast(error.message));}});},
    queueEvent(id){queuedEvent=id;},
    async open(id){if(id!=='military'||!root)return;try{await load();if(queuedEvent){const target=queuedEvent;queuedEvent=null;if(data.events.some(item=>item.id===target))editEvent(target);}}catch(error){if(!data)paint(root,window.WorkspaceUI.emptyState('병역 정보를 불러오지 못했습니다.',error.message,'warning')+'<button data-military="retry">다시 시도</button>');throw error;}},
    refresh(){if(root)return load({quiet:true});}
  };
})();
