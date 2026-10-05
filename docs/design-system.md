# Personal Workspace 디자인 시스템

개발·운영·일상을 연결하는 Desktop Productivity Workspace다. REST, WebSocket, SQLite, 인증, 런타임과 HomeItem 저장 계약은 유지한다. 읽기 쉬운 위계, 중요한 작업 표면, 일관된 조작과 작은 화면의 단일 작업을 기준으로 한다.

## 공통 토큰

`css/design-system.css`가 의미 토큰과 공통 primitive를 소유한다. 기능 CSS는 이를 소비한다.

| 역할 | 토큰 | Dark | Light |
| --- | --- | --- | --- |
| 앱 배경 | `--bg-app` | #101116 | #f1f3f7 |
| 내비게이션 | `--bg-sidebar` | #15171d | #e9edf3 |
| 주요 작업 표면 | `--bg-surface` | #1c1f27 | #ffffff |
| 보조 표면 | `--bg-secondary` | #171a21 | #f6f7fa |
| 메뉴·다이얼로그 | `--bg-elevated` | #262a34 | #ffffff |
| Hover | `--bg-surface-hover` | 테마별 중립 강조 | 테마별 중립 강조 |
| 눌림 | `--bg-active` | #303748 | #dde4ee |
| 선택 | `--bg-selected` | #29324b | #e4eafe |

`--category-ai/dev/infra/monitor/productivity`는 각각 violet/blue/teal/indigo/muted warm 계열이다. Glyph·선택·차트에 제한적으로 사용하며 큰 패널을 앱별 색으로 칠하지 않는다. 성공·장애는 별도 의미 상태 토큰을 쓴다.

| 위계 | 크기 | 용도 |
| --- | --- | --- |
| Page | 24px | 화면 제목 |
| Subtitle / Body / Row | 14px | 설명·기본 내용·행 이름 |
| Section | 16px | 구역 제목 |
| Card | 17px | 주요 표면 제목 |
| Secondary | 13px | 보조 내용·일반 버튼 |
| Metadata | 12px | 시각·경로·보조 수치 |
| Badge | 11px | 짧은 상태 보조 표기 |
| Code | 13px | 코드·SQL |

AI 대화는 14–15px, Notes 본문은 Desktop 16px/Mobile 15px이다. Telemetry 핵심 수치는 28–40px이다. 모바일 입력창은 16px을 우선한다. 중요한 이름과 값을 metadata 크기로 축소하지 않는다.

Radius는 tiny 6px, control 8px, panel 12px, floating 16px이다. 기존 radius 별칭은 호환을 위해 유지한다. 간격은 기존 2/4/6/8/12/16/20/24/32px 토큰을 사용한다. Desktop 기본 control은 36px, compact 32px, Mobile 주요 조작은 44px이다.

## 표면과 조작

한 화면에서 1–2개의 주요 작업 구역에 경계·높이를 집중한다. 편집기·대화·차트·달력은 주요 표면, 탐색과 metadata는 보조 표면이다. 행은 목록 안의 선택·hover를 담당하며 모든 행을 독립 카드로 만들지 않는다.

- `.primary`: 제한된 주요 작업, solid accent.
- `.secondary`: 보조 작업, 작업 표면과 경계.
- `.ghost`, `.icon-btn`: 탐색·아이콘 작업, 기본 투명 배경.
- `.danger`: 위험 작업의 의미 색.
- `.ui-segmented`: 선택 상태가 있는 보기 전환.
- `.sm`, `.ui-compact-button`: compact 도구 막대 조작.
- 입력·select·textarea: 동일한 control radius와 focus ring. Search/command도 같은 입력 문법을 사용한다.

아이콘 단독 조작의 aria-label과 tooltip을 유지한다. Hover에서 크기나 위치를 바꾸지 않는다. 상태는 텍스트와 dot를 함께 사용한다. `.ui-status`는 online/offline/degraded/warning/running/stopped/pending/loading/unknown 및 기존 success/danger/info 값을 지원한다. Loading은 회전 indicator, unknown은 빈 dot다.

Motion은 120–240ms 범위의 opacity/background/pane 전환을 사용하며 reduced motion을 지원한다. 공통 API 진행 표시는 비차단 방식이다. Quiet 갱신은 기존 DOM·초안·선택·스크롤을 보존한다.

## Shell과 Home

Desktop은 52px global bar, 60px activity rail, 작업 본문으로 구성한다. Workspace → 이전/현재 작업 → 검색 → AI → 백그라운드 상태 → 시각/메뉴 순서다. Rail의 active는 선택 표면과 얇은 outline이다.

700px 이하에서는 44px app bar와 48px 하단 내비게이션을 사용한다. Assistant, Device Codex, Studio, Notes, Database와 집중형 runtime에서는 하단 내비게이션을 접는다. 100dvh, VisualViewport와 safe area를 유지한다.

Home의 첫 정보는 **오늘 → 확인 필요 → 이어하기**다. 오늘은 기존 일정, 확인 필요는 조회한 서비스 장애·장비 offline·GitHub CI 실패/승인 대기·Studio 중지/실패·조회 실패를 투영한다. 조회 전 skeleton과 미확인 상태를 정상으로 표현하지 않는다. GitHub CI는 기존 요약이 읽은 저장소의 최근 실행이며 전체 계정의 모든 장애를 뜻하지 않는다.

이어하기는 기존 저장된 Studio 프로젝트, 현재 브라우저 세션에서 연 서비스/저장소, 기존 runtime 활동을 최대 5행으로 표시한다. 서버 활동 스키마나 HomeItem 저장을 변경하지 않는다. 서비스·인프라 요약, 고정 앱, 사용자 배치·위젯을 그 아래에 둔다. 평상시 앱·폴더는 compact 목록, 위젯은 disclosure이며 좌표 격자·drag·resize는 홈 편집에서만 노출한다.

## 앱별 정보 위계

| 앱 | 주요 작업 | 보조 정보 | Mobile |
| --- | --- | --- | --- |
| Assistant | 대화·하단 composer, user bubble, Markdown | 접이식 대화 목록·모델·사용량 | 본문과 서랍, 16px 입력 |
| Studio | Editor | Explorer·Git/Codex·output | Files/Editor/Git·Codex 단일 pane |
| Device Codex | 대화·입력 | 장비·경로 context, 연결·기록 | context bar와 drawer |
| Devices | 이름·상태·CPU/RAM/Disk | GPU·로그·수정 overflow | 장비별 정보 묶음 |
| Services | 이름·상태·상태 신호·리소스 관계 | 런타임·저장소·계측·설정 | 한 본문, 생성 sheet |
| GitHub | repository row와 상세 | Owner·탭·검색·승인 | 탐색 drawer |
| Database | Query editor·결과 | connection/schema/history | Query/Result/Schema/History |
| Telemetry | health·request·error·latency·차트 | breakdown·사용자·설정 | 핵심 수치·차트 |
| Files | 경로·목록·선택 작업 | overflow·즐겨찾기 | 경로 drawer, 이름 아래 metadata |
| Terminal / Remote | 실행 화면 | 최소 연결 도구 막대 | 콘텐츠 전체 영역 |
| Notes | 읽기 폭 880px 문서 | 폴더·도구 막대 | drawer와 본문 |
| Cloud | 파일 목록·선택 상태 | 검색·드라이브·생성 | overlay/drawer/bottom sheet |
| Calendar / Timetable | 달력·일정·수업 | 이동·학기·편집 | 달력+agenda, 시간표 선택 요일 |
| Military | 진행·남은 기간 | 이정표·달력·설정 | 진행 표면과 줄바꿈 이정표 |
| Clipboard / Apps / Recent | 내용·최근 작업 | 만료·등록·삭제 | 읽기 쉬운 행 |
| Settings / Login | 현재 폼·제출 | 안내·오류 | 고정 작업 영역 sheet / 단일 로그인 표면 |

코드·표·DB 결과 등 본질적으로 넓은 콘텐츠만 가로 스크롤한다. 시간표 선택 요일은 표시 상태이며 저장 데이터의 7일 구조를 바꾸지 않는다. 기존 id/data-* 이벤트 계약을 유지한다.

## CSS 소유권과 빌드

| 소스 | 책임 |
| --- | --- |
| `css/design-system.css` | 토큰·버튼·입력·상태·focus·motion |
| `css/shell.css` | 전역 Desktop/Mobile shell |
| `css/launcher.css` | Home·저장 배치 편집·App Library |
| `css/workspace.css` | 장비·Files·Terminal·Remote·Clipboard·Apps |
| 기능별 CSS | 해당 앱의 표면·레이아웃·반응형 |
| `tools/ui/workspace.css` | Tailwind 진입점과 공통 drawer primitive |
| `js/ui.js` | 기존 SVG·진행·로딩 표현 |

기본 selector는 동일 조건 안에서 한 정의로 모으고 media adaptation을 뒤에 둔다. Shell에서 기능 CSS를 다시 평탄화하지 않는다. 공통 component layer에서 모든 버튼과 입력 radius를 강제하던 규칙을 제거했다. Login도 같은 bundle만 읽는다. Notes vendor CSS는 Notes 기능 CSS 앞에서 동일 base layer로 가져온다. 별도 unlayered link를 두면 라이브러리의 제목·내부 여백이 제품 토큰보다 우선하므로 중복 로드하지 않는다. `vendor/workspace-ui.css`는 `npm run build --prefix tools/ui`로 생성하며 직접 편집하지 않는다.

현재 검증과 외부 연결·실기기 제한은 [검증 기록](ui-redesign-verification.md)에 구분한다.
