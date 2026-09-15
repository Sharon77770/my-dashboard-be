'use strict';
window.WorkspacePlanner = (() => {
  let ui, month, selectedDay, events=[], terms=[], table=null, activeTerm='', calendarVersion=0, tableVersion=0;
  const weekdays=['월','화','수','목','금','토','일'];
  const palette=Array.from({length:6},(_,index)=>window.WorkspaceUI.token('calendar-'+(index+1)));
  const $=(selector,root=document)=>root.querySelector(selector);
  const pad=value=>String(value).padStart(2,'0');
  const dateString=date=>`${date.getFullYear()}-${pad(date.getMonth()+1)}-${pad(date.getDate())}`;
  const asDate=value=>new Date(`${value}T12:00:00`);
  const addDays=(value,count)=>{const date=asDate(value);date.setDate(date.getDate()+count);return dateString(date);};
  const minutes=value=>Number(value.slice(0,2))*60+Number(value.slice(3,5));
  const time=value=>`${pad(Math.floor(value/60))}:${pad(value%60)}`;
  const safe=value=>ui.escape(value);
  const field=(name,label,value,type='text',extra='')=>ui.fields.input(name,label,value,type,extra);
  const today=()=>dateString(new Date());
  const notice=message=>`<p class="empty-state">${safe(message)}</p>`;
  const colorField=value=>field('color','색상',value,'color','required');
  const memo=value=>`<label>메모<textarea name="notes" rows="3" maxlength="4000">${safe(value)}</textarea></label>`;
  const onDay=(event,day)=>event.start<`${addDays(day,1)}T00:00`&&event.end>`${day}T00:00`;
  const eventTime=event=>event.allDay?'종일':`${event.start.slice(11,16)}–${event.end.slice(11,16)}`;
  function init(shared) {
    ui=shared; selectedDay=today(); month=selectedDay.slice(0,7);
    for(const id of ['calendar','timetable']) {
      const root=$(`#${id}`);
      root.addEventListener('click',event=>handleClick(event).catch(error=>ui.toast(error.message)));
      root.addEventListener('change',event=>handleChange(event).catch(error=>ui.toast(error.message)));
    }
  }
  async function open(id) { if(id==='calendar')await loadCalendar();if(id==='timetable')await loadTerms(); }
  async function loadCalendar() {
    const version=++calendarVersion;
    const first=asDate(`${month}-01`), offset=(first.getDay()+6)%7;
    const from=addDays(`${month}-01`,-offset), to=addDays(from,42);
    $('#calendar').setAttribute('aria-busy','true');
    try {
      const result=await ui.api(`/calendar/events?from=${from}&to=${to}`);
      if(version!==calendarVersion)return;
      events=result; renderCalendar(from);
    } catch(error) { if(version===calendarVersion)$('#calendar').innerHTML=notice(error.message)+'<button data-plan="calendar-reload">다시 시도</button>'; }
    finally { if(version===calendarVersion)$('#calendar').removeAttribute('aria-busy'); }
  }
  function renderCalendar(from) {
    if(!from){const first=asDate(`${month}-01`);from=addDays(`${month}-01`,-((first.getDay()+6)%7));}
    const daily=events.filter(event=>onDay(event,selectedDay));
    $('#calendar').innerHTML=`<div class="page-head"><div><span class="eyebrow">MY CALENDAR</span><h1>캘린더</h1><p class="planner-subtitle">약속부터 과제 마감까지, 하루를 계획하세요.</p></div><button class="primary" data-plan="event-new">＋ 일정 추가</button></div>
      <div class="planner-toolbar"><div class="actions"><button data-plan="month-prev" aria-label="이전 달">‹</button><h2>${Number(month.slice(0,4))}년 ${Number(month.slice(5))}월</h2><button data-plan="month-next" aria-label="다음 달">›</button><button data-plan="today">오늘</button></div><input type="month" aria-label="조회할 월" data-month value="${month}" min="1900-01" max="2199-12"></div>
      <div class="calendar-layout"><div class="month-board"><div class="week-labels">${weekdays.map(day=>`<span>${day}</span>`).join('')}</div><div class="month-grid">${Array.from({length:42},(_,index)=>{
        const day=addDays(from,index), items=events.filter(event=>onDay(event,day));
        return `<div class="calendar-day ${day.slice(0,7)!==month?'outside':''} ${day===selectedDay?'selected':''}"><button class="day-number ${day===today()?'is-today':''}" data-plan="day" data-day="${day}" aria-label="${day} 일정 보기" aria-pressed="${day===selectedDay}">${Number(day.slice(8))}</button>${items.slice(0,3).map(event=>`<button class="event-chip" style="--event-color:${safe(event.color)}" data-plan="event-edit" data-id="${safe(event.id)}" title="${safe(event.title)}"><span>${event.allDay?'':event.start.slice(11,16)}</span> ${safe(event.title)}</button>`).join('')}${items.length>3?`<button class="more-events" data-plan="day" data-day="${day}">+${items.length-3}개 더 보기</button>`:''}</div>`;
      }).join('')}</div></div><aside class="day-agenda"><div class="panel-head"><div><span>선택한 날짜</span><h2>${Number(selectedDay.slice(5,7))}월 ${Number(selectedDay.slice(8))}일</h2></div><button data-plan="event-new" aria-label="선택한 날짜에 일정 추가">＋</button></div>${daily.map(event=>`<button class="agenda-event" data-plan="event-edit" data-id="${safe(event.id)}" style="--event-color:${safe(event.color)}"><small>${safe(eventTime(event))}</small><strong>${safe(event.title)}</strong><span>${safe(event.location)}</span><small>${event.start.slice(0,10)!==event.end.slice(0,10)?safe(event.start.slice(0,10)+' ~ '+(event.allDay?addDays(event.end.slice(0,10),-1):event.end.slice(0,10))):''}</small></button>`).join('')||notice('등록된 일정이 없습니다. 새로운 일정을 추가해 보세요.')}</aside></div>`;
  }
  function editEvent(id) {
    const item=events.find(event=>event.id===id)||{title:'',start:`${selectedDay}T09:00`,end:`${selectedDay}T10:00`,allDay:false,location:'',notes:'',color:palette[0]};
    ui.editor(id?'일정 수정':'새 일정',field('title','일정 제목',item.title,'text','required maxlength="120"')+ui.fields.check('allDay','종일',item.allDay)+`<div class="form-grid">${field('start','시작',item.start.slice(0,16),'datetime-local','required')}${field('end','종료',item.end.slice(0,16),'datetime-local','required')}${field('location','장소',item.location,'text','maxlength="200"')}${colorField(item.color)}</div>`+memo(item.notes)+(id?'<button type="button" class="danger" id="delete-planner-item">일정 삭제</button>':''),async form=>{
      const allDay=form.has('allDay');
      const start=allDay?`${form.get('start')}T00:00`:form.get('start');
      const end=allDay?`${addDays(form.get('end'),1)}T00:00`:form.get('end');
      await ui.api(id?`/calendar/events/${id}`:'/calendar/events',id?'PUT':'POST',{title:form.get('title'),start,end,allDay,location:form.get('location'),notes:form.get('notes'),color:form.get('color')});
      selectedDay=start.slice(0,10);month=selectedDay.slice(0,7);await loadCalendar();ui.toast('일정을 저장했습니다.');
    });
    const toggle=()=>{
      const allDay=$('[name=allDay]',$('#editor-form')).checked;
      for(const name of ['start','end']) {const input=$(`[name=${name}]`,$('#editor-form'));const previous=input.value;input.type=allDay?'date':'datetime-local';input.value=allDay?previous.slice(0,10):`${previous.slice(0,10)}T${name==='start'?'09:00':'10:00'}`;}
    };
    if(item.allDay){for(const name of ['start','end']){const input=$(`[name=${name}]`,$('#editor-form'));input.type='date';input.value=name==='start'?item.start.slice(0,10):addDays(item.end.slice(0,10),-1);}}
    $('[name=allDay]',$('#editor-form')).addEventListener('change',toggle);
    if(id)$('#delete-planner-item').onclick=()=>remove('일정 삭제','이 일정을 삭제할까요?',`/calendar/events/${id}`,loadCalendar);
  }
  function remove(title,message,path,reload) {
    ui.confirmAction(title,message,async()=>{await ui.api(path,'DELETE');await reload();ui.toast('삭제했습니다.');});
  }
  async function loadTerms() {
    terms=await ui.api('/timetables');
    if(!terms.some(term=>term.id===activeTerm))activeTerm=terms[0]?.id||'';
    await loadTable();
  }
  async function loadTable() {
    const version=++tableVersion;
    if(!activeTerm){table=null;renderTable();return;}
    const result=await ui.api(`/timetables/${activeTerm}`);
    if(version!==tableVersion)return;table=result;renderTable();
  }
  function renderTable() {
    const courses=table?.courses||[], slots=courses.flatMap(course=>course.meetings);
    const start=Math.min(8,...slots.map(slot=>Math.floor(minutes(slot.start)/60)));
    const end=Math.max(20,...slots.map(slot=>Math.ceil(minutes(slot.end)/60)));
    $('#timetable').innerHTML=`<div class="page-head"><div><span class="eyebrow">CAMPUS PLANNER</span><h1>시간표</h1><p class="planner-subtitle">월요일부터 일요일까지, 나만의 학기 계획.</p></div><button class="primary" data-plan="${table?'course-new':'term-new'}">＋ ${table?'수업 추가':'시간표 만들기'}</button></div>
      <div class="planner-toolbar"><div class="actions">${terms.length?`<select data-term aria-label="학기 시간표 선택">${terms.map(term=>`<option value="${safe(term.id)}" ${term.id===activeTerm?'selected':''}>${safe(term.name)}</option>`).join('')}</select>`:''}<button data-plan="term-new">＋ 새 시간표</button>${table?'<button data-plan="term-edit">학기 설정</button>':''}</div>${table?`<div class="semester-summary"><b>${table.totalCredits} 학점</b><span>${courses.length} 과목</span><small>${safe(table.term.start)} ~ ${safe(table.term.end)}</small></div>`:''}</div>
      ${!table?`<div class="planner-welcome"><span>▦</span><h2>이번 학기를 그려보세요</h2><p>학기별 시간표를 만들고 과목, 강의실, 교수님과 수업 시간을 기록하세요.</p><button class="primary" data-plan="term-new">첫 시간표 만들기</button></div>`:`<div class="timetable-scroll"><div class="weekly-table"><div class="timetable-heading"><span>시간</span>${weekdays.map(day=>`<b>${day}</b>`).join('')}</div><div class="timetable-body" style="height:${(end-start)*64}px"><div class="hour-axis">${Array.from({length:end-start},(_,index)=>`<span style="top:${index*64}px">${pad(start+index)}:00</span>`).join('')}</div>${weekdays.map((day,index)=>`<div class="weekday-column">${Array.from({length:end-start},(_,hour)=>`<button class="empty-slot" style="top:${hour*64}px" data-plan="slot-new" data-day="${index+1}" data-time="${pad(start+hour)}:00" aria-label="${day}요일 ${start+hour}시 수업 추가"></button>`).join('')}${courses.flatMap(course=>course.meetings.filter(slot=>slot.day===index+1).map(slot=>`<button class="course-block" style="top:${(minutes(slot.start)-start*60)*64/60}px;height:${(minutes(slot.end)-minutes(slot.start))*64/60}px;--course-color:${safe(course.color)}" data-plan="course-edit" data-id="${safe(course.id)}" title="${safe(course.title+' '+slot.start.slice(0,5)+'–'+slot.end.slice(0,5)+' '+course.location)}"><strong>${safe(course.title)}</strong><span>${safe(course.location)}</span><small>${slot.start.slice(0,5)}–${slot.end.slice(0,5)}</small></button>`)).join('')}</div>`).join('')}</div></div></div><div class="course-cards">${courses.map(course=>`<button data-plan="course-edit" data-id="${safe(course.id)}" class="course-summary" style="--event-color:${safe(course.color)}"><strong>${safe(course.title)}</strong><span>${course.credits}학점 · ${safe(course.professor||'교수 미입력')} · ${safe(course.location||'강의실 미입력')}</span><small>${course.meetings.map(slot=>`${weekdays[slot.day-1]} ${slot.start.slice(0,5)}–${slot.end.slice(0,5)}`).join(' / ')}</small></button>`).join('')||notice('빈 시간 칸을 눌러 첫 수업을 추가하세요.')}</div>`}`;
  }
  function editTerm(edit=false) {
    const year=new Date().getFullYear(), second=new Date().getMonth()>=6;
    const item=edit?table.term:{name:`${year}년 ${second?'2':'1'}학기`,start:`${year}-${second?'09':'03'}-01`,end:`${year}-${second?'12':'06'}-30`};
    ui.editor(edit?'학기 설정':'새 시간표',field('name','시간표 이름',item.name,'text','required maxlength="80"')+`<div class="form-grid">${field('start','학기 시작일',item.start,'date','required')}${field('end','학기 종료일',item.end,'date','required')}</div>`+(edit?'<button type="button" class="danger" id="delete-planner-item">시간표 삭제</button>':''),async form=>{
      const saved=await ui.api(edit?`/timetables/${item.id}`:'/timetables',edit?'PUT':'POST',Object.fromEntries(form));activeTerm=saved.id;await loadTerms();
    });
    if(edit)$('#delete-planner-item').onclick=()=>remove('시간표 삭제','이 시간표와 모든 수업이 삭제됩니다.',`/timetables/${item.id}`,loadTerms);
  }
  function meetingRow(slot) { return `<div class="meeting-row">${ui.fields.select('day','요일',String(slot.day),weekdays.map((day,index)=>[String(index+1),day]))}${field('start','시작',slot.start.slice(0,5),'time','required')}${field('end','종료',slot.end.slice(0,5),'time','required')}<button type="button" class="remove-meeting" aria-label="수업 시간 삭제">×</button></div>`; }
  function editCourse(id,day=1,start='09:00') {
    const item=table.courses.find(course=>course.id===id)||{title:'',professor:'',location:'',credits:3,color:palette[table.courses.length%palette.length],notes:'',meetings:[{day,start,end:time(Math.min(1439,minutes(start)+60))}]};
    ui.editor(id?'수업 수정':'새 수업',field('title','과목명',item.title,'text','required maxlength="120"')+`<div class="form-grid">${field('professor','교수',item.professor,'text','maxlength="120"')}${field('location','강의실',item.location,'text','maxlength="200"')}${field('credits','학점',item.credits,'number','required min="0" max="30"')}${colorField(item.color)}</div><div id="meeting-rows">${item.meetings.map(meetingRow).join('')}</div><button type="button" id="add-meeting">＋ 수업 시간 추가</button>`+memo(item.notes)+(id?'<button type="button" class="danger" id="delete-planner-item">수업 삭제</button>':''),async form=>{
      const meetings=[...document.querySelectorAll('#meeting-rows .meeting-row')].map(row=>({day:Number($('[name=day]',row).value),start:$('[name=start]',row).value,end:$('[name=end]',row).value}));
      await ui.api(`/timetables/${activeTerm}/courses${id?'/'+id:''}`,id?'PUT':'POST',{title:form.get('title'),professor:form.get('professor'),location:form.get('location'),credits:Number(form.get('credits')),color:form.get('color'),notes:form.get('notes'),meetings});await loadTable();ui.toast('수업을 저장했습니다.');
    });
    $('#add-meeting').onclick=()=>{if($('#meeting-rows').children.length>=21){ui.toast('수업 시간은 최대 21개입니다.');return;}$('#meeting-rows').insertAdjacentHTML('beforeend',meetingRow({day:1,start:'09:00',end:'10:00'}));};
    $('#meeting-rows').onclick=event=>{if(event.target.closest('.remove-meeting')){if($('#meeting-rows').children.length===1){ui.toast('수업 시간을 하나 이상 입력해 주세요.');return;}event.target.closest('.meeting-row').remove();}};
    if(id)$('#delete-planner-item').onclick=()=>remove('수업 삭제','이 과목의 모든 수업 시간을 삭제할까요?',`/timetables/${activeTerm}/courses/${id}`,loadTable);
  }
  async function handleClick(event) {
    const button=event.target.closest('[data-plan]');if(!button)return;
    switch(button.dataset.plan) {
      case 'calendar-reload':await loadCalendar();break;
      case 'month-prev':case 'month-next':{const next=asDate(`${month}-01`);next.setMonth(next.getMonth()+(button.dataset.plan==='month-prev'?-1:1));month=dateString(next).slice(0,7);selectedDay=`${month}-01`;await loadCalendar();break;}
      case 'today':selectedDay=today();month=selectedDay.slice(0,7);await loadCalendar();break;
      case 'day':selectedDay=button.dataset.day;renderCalendar();break;
      case 'event-new':editEvent();break;
      case 'event-edit':editEvent(button.dataset.id);break;
      case 'term-new':editTerm();break;
      case 'term-edit':editTerm(true);break;
      case 'course-new':editCourse();break;
      case 'course-edit':editCourse(button.dataset.id);break;
      case 'slot-new':editCourse(null,Number(button.dataset.day),button.dataset.time);break;
    }
  }
  async function handleChange(event) {
    if(event.target.matches('[data-month]')&&event.target.value){month=event.target.value;selectedDay=`${month}-01`;await loadCalendar();}
    if(event.target.matches('[data-term]')){activeTerm=event.target.value;await loadTable();}
  }
  return {init,open};
})();
