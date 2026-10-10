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
| Page | 20px | 화면 제목 |
| Subtitle / Body / Row | 13px | 설명·기본 내용·행 이름 |
| Section | 14px | 구역 제목 |
| Card | 15px | 주요 표면 제목 |
| Secondary | 12px | 보조 내용·일반 버튼 |
| Metadata | 12px | 시각·경로·보조 수치 |
| Badge | 11px | 짧은 상태 보조 표기 |
| Code | 13px | 코드·SQL |

AI 대화는 14–15px, Notes 본문은 Desktop 16px/Mobile 15px이다. Telemetry 핵심 수치는 28–40px이다. 모바일 입력창은 16px을 우선한다. 중요한 이름과 값을 metadata 크기로 축소하지 않는다.

Radius는 tiny 6px, control 8px, panel 12px, floating 16px이다. 기존 radius 별칭은 호환을 위해 유지한다. 간격은 기존 2/4/6/8/12/16/20/24/32px 토큰을 사용한다. Desktop 기본 control은 30px, compact 28px, Mobile 주요 조작은 44px이다.

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

Desktop은 44px global bar, 48px activity rail, 작업 본문으로 구성한다. Workspace → 이전/현재 작업 → 검색 → AI → 백그라운드 상태 → 시각/메뉴 순서다. Rail의 active는 선택 표면과 얇은 outline이다.

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

## 전체 앱 밀도와 작업 흐름

공통 토큰은 design-system.css, 앱 간 밀도와 반응형 조정은 compact-workspace.css에서 관리한다. 글·코드 편집 본문은 기존 가독성을 유지한다. 새로고침·종료·삭제 등 반복 동작은 공통 SVG 아이콘과 접근성 이름/툴팁을 제공하고, 작은 화면에서는 보조 레이블을 접는다. 터치 포인터의 아이콘 버튼은 44px 목표 영역을 유지한다. 동적 앱 화면은 ui.js에서 기존 버튼과 이벤트를 유지한 채 아이콘을 적용한다.

Home은 오늘·확인 필요·이어하기, 앱 라이브러리는 고정 → 코드 작성·검증 → 운영·상태 확인 → 기록·일정 → 연결·설정 순서다. 라이브러리는 그룹별 두 열, 서비스는 상태 요약 → 검색/필터 → 대상 선택, 파일·문서·SQL은 탐색 → 편집 → 결과 흐름을 유지하며 도구 영역을 압축한다. Studio 하단 터미널은 중앙 열 안에서 출력 전체 높이와 우측 세션 사이드바를 유지한다.

검증: scripts/check-ui.mjs는 격리 서버에서 21개 화면의 다크/라이트·1440/390px 탐색과 overflow/아이콘 이름, 로그인·라이브러리·설정 캡처를 수행한다. 결과는 artifacts/ui/report.json이다. 실제 외부 서비스 연결이나 운영 배포 성공을 뜻하지 않는다.

## 드라이브 파일 탐색 레이아웃 (2026-10-09)

드라이브는 compact 제목·breadcrumb·검색 헤더와 통합 작업 툴바 아래에 폴더 탐색/파일 목록을 배치한다. 목록이 기본 보기이며 행 높이는 데스크톱 34px, 작은 화면 40px이다. 유형·수정일은 좁은 화면에서 상세 정보로 확인한다. 일반 안내 문구는 목록 위에 표시하지 않고 비어 있는 폴더에서 업로드 안내만 제공한다. 파일 목록과 탐색 패널은 독립적으로 스크롤한다.

800px 이하에서는 기존 드로어로 폴더 탐색을 열며 폴더 선택 후 닫힌다. 툴바는 아이콘 중심 작업 줄과 정렬·보기·선택 제어 줄로 구성한다. 하위 폴더는 펼칠 때 기존 cloud 조회 API를 사용하며 전체 디렉터리를 미리 순회하지 않는다. 업로드·생성·선택·다운로드·휴지통·편집 API 계약은 유지한다.

`scripts/check-drive.mjs`는 격리 서버 18187에서 로컬 수정 JS/CSS를 Playwright route로 제공해 검증한다. 테스트 전용 폴더를 만들고 실제 목록·폴더 탐색·업로드·다운로드를 검사한 후 해당 폴더만 정리한다. 1440/1024/768/390px에서 파일 목록 높이는 드라이브 영역의 77~84%, 가로/툴바 넘침 없음. 모바일 드로어 선택 후 닫힘과 console/pageerror 0건도 확인했다. 결과·화면은 `artifacts/drive/`에 저장한다. 운영 배포 결과를 의미하지 않는다.
## Communications 메신저 레이아웃 (2026-10-10)

`communications.css`는 공통 색상·타이포그래피·버튼과 `WorkspaceUI.icon`을 사용한다. 통합 사이드바 + 유연한 중앙 영역이 기본이며 상세 패널은 명시적 토글로만 열린다. 메시지는 미묘한 표면 대비, 날짜 구분선, 작은 발신자 아바타와 160~180ms 상태 전환을 사용한다. 모션 감소 설정을 존중한다. 과도한 관리자 카드와 상단 도구 행을 없애고 대화별 도구를 헤더에 배치했다.

미연결 화면은 빈 목록/정보 패널 대신 중앙 온보딩을 제공한다. 모바일은 800px 기준 단일 패널이며 입력 글자 크기 16px, visualViewport 높이 대응, 안전 영역 여백을 사용한다. API가 제공하지 않는 메시지 수·수신자·연결 상태를 시각적으로 추측하지 않는다. `scripts/check-communications-ui.mjs`가 1920×1080, 1366×768, 390×844의 상태별 실제 Chromium 렌더링을 검사한다. 외부 서비스 데이터는 명시적인 테스트 fixture이다.
