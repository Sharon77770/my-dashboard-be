'use strict';
/** Document starting points. Each creation receives independent editable blocks. */
window.WorkspaceNoteTemplates=(()=>{
 const text=value=>[{type:'text',text:value,styles:{}}];
 const paragraph=value=>({type:'paragraph',content:text(value)});
 const heading=value=>({type:'heading',props:{level:2},content:text(value)});
 const task=value=>({type:'checkListItem',props:{checked:false},content:text(value)});
 const table=rows=>({type:'table',content:{type:'tableContent',rows:rows.map(cells=>({cells:cells.map(text)}))}});
 const templates=[
  {id:'blank',name:'빈 문서',icon:'📄',description:'아무것도 없는 페이지에서 자유롭게 시작합니다.',blocks:[]},
  {id:'todo',name:'할 일',icon:'☑️',description:'오늘과 이번 주 할 일, 완료 목록을 관리합니다.',blocks:[heading('오늘 할 일'),task('가장 중요한 일'),task('다음 할 일'),heading('이번 주'),task('진행할 작업'),heading('완료한 일'),paragraph('완료한 작업을 이곳에 정리하세요.')]},
  {id:'worklog',name:'업무 기록',icon:'📝',description:'했던 일, 결과와 다음 작업을 남깁니다.',blocks:[heading('오늘 한 일'),paragraph('작업 내용과 결과를 기록하세요.'),heading('진행 중인 일'),task('이어갈 작업'),heading('문제와 해결'),paragraph('어려웠던 점과 해결 방법'),heading('다음 할 일'),task('다음 작업')]},
  {id:'ledger',name:'가계부',icon:'💰',description:'날짜·분류·수입·지출·메모를 표로 기록합니다.',blocks:[heading('이번 달 가계부'),paragraph('기간: YYYY-MM · 금액 단위: 원'),table([['날짜','분류','항목','수입','지출','메모'],['YYYY-MM-DD','프로젝트','대금 입금','','',''],['YYYY-MM-DD','업무 비용','구독료','','','']]),heading('월간 정리'),paragraph('총 수입: 0원 / 총 지출: 0원 / 잔액: 0원'),paragraph('표는 직접 작성하는 가계부입니다. 합계도 직접 입력하세요.')]},
  {id:'project',name:'프로젝트 개요',icon:'📁',description:'목표, 담당자, 일정과 산출물을 정리합니다.',blocks:[heading('프로젝트 소개'),paragraph('고객 / 조직: '),paragraph('기간: '),paragraph('담당자 / 연락처: '),heading('목표와 범위'),paragraph('이번 프로젝트에서 완성할 것'),heading('일정'),table([['단계','마감일','상태'],['기획','','예정'],['제작','','예정'],['검수 / 전달','','예정']]),heading('산출물'),task('최종 산출물 전달')]},
  {id:'meeting',name:'회의록',icon:'💬',description:'안건, 결정 사항과 후속 작업을 기록합니다.',blocks:[paragraph('일시: '),paragraph('참석자: '),heading('회의 안건'),paragraph('논의할 내용'),heading('논의 및 결정'),paragraph('결정 사항과 이유'),heading('후속 작업'),task('담당자 · 할 일 · 마감일')]},
  {id:'weekly',name:'주간 회고',icon:'📅',description:'이번 주 성과와 배운 점, 다음 주 계획입니다.',blocks:[heading('이번 주 완료한 일'),paragraph('주요 성과'),heading('좋았던 점'),paragraph('계속할 습관'),heading('개선할 점'),paragraph('다음에는 다르게 해볼 일'),heading('다음 주 계획'),task('우선순위 1'),task('우선순위 2')]},
  {id:'idea',name:'아이디어 노트',icon:'💡',description:'생각, 참고 이미지와 실험 계획을 모읍니다.',blocks:[heading('아이디어'),paragraph('떠오른 생각을 적어 보세요.'),heading('참고 자료'),paragraph('/이미지 또는 이미지 버튼으로 자료를 첨부하세요.'),heading('실험해 볼 일'),task('첫 번째 실험')]}
 ];
 return {all:()=>templates.map(({blocks,...info})=>info),create:id=>{const template=templates.find(item=>item.id===id);if(!template)throw new Error('템플릿을 선택하세요.');return JSON.parse(JSON.stringify(template));}};
})();
