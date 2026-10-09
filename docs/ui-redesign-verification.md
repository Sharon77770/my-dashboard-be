# UI redesign 검증 현황

## 2026-10-05 Personal Workspace 제품 디자인 통합

이번 변경은 의미 토큰 → Shell/Home → Assistant/Studio/Device Codex → 인프라/개발/데이터 앱 → 생산성 앱/설정/Login 순서로 적용했다. API·WebSocket·SQLite·인증·백엔드 Java는 수정하지 않았다. UI 계약은 기존 ID/data 속성·HomeItem 저장을 유지하고, 모바일 요일 선택과 브라우저 세션의 Continue 문맥만 추가했다.

### 자동 검사

- `npm run build --prefix tools/ui`: 성공. 생성된 `vendor/workspace-ui.css` 포함.
- `tools/launcher/*test.cjs` 23개: 통과. Home Attention의 초기/미확인/장애/승인/조회 실패, 요일 선택의 무저장·포커스 유지, Continue의 escaping·HomeItem 불변 검사를 포함한다.
- `tools/studio-editor/test.cjs`, `device-codex-test.cjs`, `codex-chat-test.cjs`, `codex-settings-test.cjs`: 통과. 실제 CodeMirror bundle, 저장·취소·초안, 연결/기록 격리, 중복 메시지, 설정 계약을 검사한다. `npm test --prefix tools/notes-editor`도 통과했으며 실제 BlockNote bundle의 편집·표·Markdown 입출력·안전한 텍스트를 검사한다.
- Responsive CSS/DOM: 1440/1280/1024/768/710/700/430/390/360px 통과. 이는 레이아웃 엔진 검증과 별개다.
- Theme: 검사 대상 일반 텍스트 최소 대비 dark 5.55:1, light 4.83:1. Primary/state/control 대비도 통과. 모든 이미지·차트 조합을 측정한 결과는 아니다.
- 변경 JS syntax와 `git diff --check`: 통과.
- 로컬 Maven `-o -Dmaven.repo.local=C:/Users/User/.m2/repository -DskipTests spotless:check package`: 성공.
- Maven 전체 `verify`: **실패**. 187건 중 실패 2건, 오류 0건, 건너뜀 14건. 수정하지 않은 `WorkspaceMemoryIntegrationTest.crossSessionSearchRetainsTentativeAndSeparatesCalendar`와 `pastIsoTimeHintExpiresTentativeMemory`에서 실패했다. 전체 백엔드 검사가 통과했다는 의미로 패키징 성공을 해석하면 안 된다.

### 브라우저 검수 방식

연결된 Browser 목록이 비어 있어 별도의 격리된 로컬 headless Chromium을 사용했다. 실제 template/CSS/JS renderer를 localhost에서 실행하고 API는 목 데이터로 응답했다. 이 검수는 운영 계정·서버 데이터·SSH·Codex·실제 DB에 접근하지 않는다. 검수용 CSRF 메타데이터와 초기 장비 rootPath 등은 실제 DTO 모양에 맞춰 제공했다.

- Desktop 1440×1000 / Mobile 390×844, dark/light에서 28개 화면/상태 조합(112건)을 캡처했다. 추가로 360/700px에서 Home·대화·Studio·DB·Cloud·Calendar·서비스 오류·Login 32건을 확인했다. 해당 실행에서 문서/앱 본문의 의도하지 않은 가로 overflow와 미처리 JS 오류는 없었다. 캡처 수는 수동으로 모든 하위 기능을 검증했다는 뜻이 아니다.
- Home, AI 대화(복원된 Markdown·code/table·user bubble), Studio(CodeMirror 파일), Device Codex, Devices, Services 목록/상세, GitHub 목록/상세, Database, Telemetry 목록/차트, Files/Terminal/Remote 진입, Notes, Cloud 목록, Calendar, Timetable, Military, Clipboard, Apps, Recent, Settings sheet, Login을 확인했다.
- Services의 지연 응답·503·빈 목록을 별도 목 응답으로 검수했다. 지연 응답 직후의 첫 오류 캡처가 아직 로딩이어서 독립된 360/700px 실행에서 실제 503 안내를 다시 확인했다. 다른 오류·loading·empty·선택·초안 보호는 해당 앱의 회귀 검사와 구분한다.
- Files 실제 목록, BlockNote 문서, Cloud 텍스트 편집 화면도 1440/390px의 두 테마에서 추가 캡처했다.
- 검수 중 Database sidebar 버튼 폭, Calendar 보조 버튼 줄바꿈, 시간표 요일 selector 가림, 모바일 Assistant 긴 제목과 중복 focus ring, Studio touch target을 수정했다. Notes 문서에서 발견한 vendor CSS 우선순위 문제는 같은 base layer로 로드해 해결했고 제목 크기와 내부 여백을 다시 검수했다. 기능 CSS의 중복 base selector는 0개다.
- 검수 스크립트/PNG/JSON은 이 작업 공간의 무시된 `.tools/ui-review/`, `.tools/workspace-preview.cjs`에 있다. 제품 파일이나 사용자 데이터로 배포하지 않는다.

### 확인 범위의 제한

실기기 touch/IME·가상 키보드·회전, 실제 SSH/원격 제어/WebSocket proxy, Codex streaming, 외부 GitHub 쓰기, 실제 DB 쿼리·파일 업로드는 이 화면 검수로 입증하지 않는다. Terminal/Remote는 진입 화면을 확인했고, 실제 연결은 미검증이다. Notes의 실문서/이미지·장기간 편집 역시 mock/jsdom 검사와 별개다. 아래 기록은 이전 작업의 역사이며 이번 작업의 성공/실패 결과를 대체하지 않는다.

---

## 이전 작업 기록

2026-10-05 장비 Codex: AI 비서형 전체 화면·접이식 연결/기록 사이드바·중앙 본문/입력창을 적용했다. `device-codex-test.cjs`는 연결 후 닫기, 토글/Escape/배경 닫기, 포커스 복원, 입력 DOM·초안·스크롤 유지, 기록 복원, 장비 전환 시 기록 초기화와 기존 인증·설정 동작을 검사한다. `responsive-test.cjs`는 9개 폭에서 사이드바 표시, 모바일 서랍, 기록의 정상 문서 배치와 16px 입력 글자를 검사한다. Browser 연결 시도에서 사용 가능한 브라우저가 없었으므로 실제 화면·터치·가상 키보드 검증은 미실시다.

## Codex 채팅 영역과 중복 메시지 회귀 검사

`tools/studio-editor/codex-chat-test.cjs`는 IDE/장비 공통 패널에서 임시 행과 서버 echo 통합, 같은 ID 재수신, 첨부 문맥이 추가된 메시지, 의도적으로 반복한 다음 turn, 준비·steer 연속 전송, 실패 재시도를 검증한다. 기존 IDE/장비/AI 비서 검사는 확대·복원, 장비 설정 접기, AI 비서 목록 토글·복원 ID 중복 제외·연속 전송을 포함한다. 반응형 검사는 1440~360px의 9개 너비에서 대화 영역 CSS 계약을 확인한다. 실제 브라우저가 연결되지 않아 시각·터치 QA는 수행하지 않았다.

## 2026-10-05 실시간 UI 추가 검증

- Docker `mvn -B spotless:apply verify`: BUILD SUCCESS, 176 tests / 168 passed / 8 skipped. 실제 HTTP 로그인·WebSocket Upgrade·REST 변경 수신·로그아웃 종료·Origin/익명 거부·세션별 작업 분리 검증 포함.
- `npm run build --prefix tools/ui`: 성공.
- `realtime-test.cjs`, `telemetry-live-test.cjs`: DOM/편집 상태 유지와 연결 수명, 재접속, 첫 수신 전환, 지연 응답 보호 통과.
- launcher/loading/planner/services/notes/cloud/GitHub/database-poll/assistant/logs/responsive/theme 회귀 검사 통과. 변경된 feature 테스트는 공통 live DOM 모듈을 실제로 로드한다.
- 현재 Browser runtime의 `browsers.list()`는 빈 목록이다. 실제 렌더링·터치·운영 reverse proxy 경유 WebSocket 검증은 수행하지 않았다. 아래 이전 검증 기록과 분리한다.
- 적용 범위와 예외: [실시간 UI](realtime-ui.md).

2026-10-05 공통 로딩의 상호작용 차단을 사용자 요청으로 취소했다. 중앙 모달·배경 blur·inert·전역 이벤트 차단·포커스 강제 이동을 제거하고 상단 비차단 안내로 변경했다. 동시 요청과 실패 정리, quiet 요청 제외는 유지한다. `loading-test.cjs`는 클릭·키보드·제출·스크롤 이벤트 허용과 포커스 유지를, Launcher 검사는 응답 대기 중 화면 이동을 확인한다. 실제 브라우저·모바일 터치 검증은 별도 대상이다.

2026-09-30 작업 트리 기준. 이 문서는 전체 리디자인의 완료 선언이 아니다. 소스와 자동 검사에서 확인한 내용과 실제 렌더링 검증을 구분한다.

## Goal 요구사항 대조

| 원문 항목 | 현재 근거 | 남은 확인 |
| --- | --- | --- |
| 1–2 범위·저장소 조사 | 기존 template, feature JS, API를 사용한다. Launcher와 앱 회귀 검사를 유지한다. | 모든 외부 연결의 실제 사용 회귀는 자동 UI 검사만으로 입증되지 않는다. |
| 3–5 시각 방향·색·gradient | `design-system.css`의 neutral surface, blue-violet accent, semantic state, solid primary | 각 앱의 실제 다크·라이트 화면 일관성 |
| 6–13 간격·버튼·icon·radius·border·layout | 공통 토큰, SVG icon, compact toolbar, flat row 및 앱별 모바일 layout | 실제 줄바꿈, hitbox 겹침, 중첩 padding, 전체 앱의 행동 우선순위 |
| 14–20 shell·Home·Library·typography·mobile navigation | `shell.css`, `launcher.css`, `home.html`, Launcher registry와 저장 배치·App Switcher 검사 | 실제 Desktop/Mobile 화면 비율, 긴 이름, 많은 앱·위젯 상태 |
| 21–25 Notes·Markdown | `notes.css`의 모바일 본문 크기, 넓은 code/table, drawer 및 Notes 테스트 | 실제 BlockNote 문서·코드·표·이미지 렌더링과 키보드 |
| 26 Terminal | compact runtime header, VisualViewport resize와 terminal fit 연결 | 실제 SSH 연결, 터치 입력, 키보드 등장/회전 시 terminal 크기 |
| 27 Database | Query/Result/Schema/History pane, SQL toolbar, 가로 스크롤 결과 | 긴 SQL·큰 결과·schema·history를 실제 화면에서 전환 |
| 28 Studio | Editor 중심 pane, Explorer/Inspector drawer, editor 회귀 검사 | 실제 editor와 Codex 입력·Git diff, 키보드·drawer 전환 |
| 29 Files/NAS/Cloud | 파일 이름·metadata 행, 모바일 작업 메뉴, 경로 drawer, Cloud editor·업로드·revision 검사 | 긴 경로, 많은 파일, 메뉴 위치, 실제 업로드·터치 |
| 30–31 Services/GitHub | compact service header/actions, GitHub scope drawer, 기능 테스트 | 실제 resource·PR·Issue·diff 데이터의 폭과 밀도 |
| 32 Assistant/Codex | 넓은 응답 문서, composer, Markdown·session 회귀 검사 | 긴 대화·응답 streaming·가상 키보드에서 composer 위치 |
| 33–34 지표·상태 | 공통 status dot, progress와 semantic 색, theme 검사 | 상태가 많은 실제 화면의 시각적 우선순위 |
| 35–37 form·sheet·motion | 모바일 공통 편집 sheet, 입력 스크롤/고정 작업 영역, reduced motion 규칙 | 긴 폼, validation error, 키보드와 animation 체감 |
| 38 CSS 책임 | `os-shell.css` 삭제, 오래된 tab CSS 제거, 전역 `.main`을 `shell.css`로 통합 | feature 파일의 기존 override는 추가 렌더링 비교 후 정리할 여지가 있다. |
| 39–40 실제 rendering·pixel audit | 아래 자동 검사 결과만 확보 | 필수 화면의 실제 브라우저 확인 및 screenshot 비교 미완료 |
| 41 theme·contrast | theme 검사에서 dark 최소 6.54:1, light 최소 5.22:1의 검사 대상 text contrast 통과 | 모든 콘텐츠 조합의 실제 대비를 의미하지 않는다. |
| 42–43 regression·build | Maven, Tailwind, Launcher/feature/editor 검사 통과 | 자동 검사의 범위를 넘는 live connection·touch·keyboard 검증 |
| 44–46 실패 조건·제품 완성도·최종 보고 | 구현 방향과 자동 근거를 `design-system.md`에 기록 | 실제 전체 화면 검토 전 최종 완성도를 판정할 수 없다. |

## 실행한 자동 검사

- `mvn verify`: 통과. 로컬 Maven 3.9.11과 기존 로컬 dependency 저장소 사용.
- `npm ci --prefix tools/ui`: 통과.
- `npm run build --prefix tools/ui`: 통과.
- `tools/launcher/*.cjs` 15개 및 `tools/studio-editor/test.cjs`: 통과. 이후 Calendar 수정에는 planner, theme, responsive 검사를 다시 실행했다.
- Responsive CSS/DOM: 1440, 1280, 1024, 768, 710, 700, 430, 390, 360px 통과.

jsdom 검사는 layout engine, 실제 터치, virtual keyboard 또는 screenshot 검사가 아니다. 테스트가 선택한 DOM/CSS 계약과 mock API 동작의 근거로만 해석한다.

## 실제 화면 검증 대기 목록

브라우저 전환은 아직 승인되지 않았다. 기존 사용자 지정은 Chrome이며, 현재 세션에는 Chrome 제어 capability가 없다. 내장 Browser 사용 여부를 문의한 상태다.

검증할 viewport는 1440×900, 1280×800, 1024×768, 430×932, 390×844, 360×800이다. 각 화면을 다크·라이트에서 확인하고, 가능한 경우 모바일 가로 방향도 포함한다.

| 화면 | 우선 확인할 상태 |
| --- | --- |
| Home / App Library | 저장 배치, widget 펼침, 긴 앱 이름, 검색, 편집·drag·resize |
| Devices | 긴 서버 이름, offline/error, 여러 작업 버튼, metrics |
| Service Catalog | 목록/상세, 긴 resource, quick actions, 각 탭 |
| Database Studio | 여러 query tabs, 긴 SQL, 결과 scroll, schema/history, 실행 상태 |
| Notes / Markdown | 긴 문서, code/table/image, sidebar, 저장 상태와 keyboard |
| Terminal / Remote | 실제 연결·오류, focus, keyboard·회전, 원격 제어 |
| Files / Cloud / NAS | 긴 경로·파일명, drawer, 작업 메뉴, upload/editor/안내 폼 |
| GitHub | owner/repository drawer, PR·Issue·Actions·diff |
| Telemetry | 데이터 없음·목록·상세·그래프·설정 메뉴 |
| Studio / Codex | editor, Files/Git/Codex 전환, streaming과 composer |
| Assistant | 긴 Markdown 응답, session sidebar, keyboard와 send |
| Calendar / Timetable | 월/학기 이동, 여러 일정, 가로 scroll, 편집 form |
| Settings | 긴 폼, 오류, 저장·취소, keyboard 및 sheet 최대 높이 |
| Login / Error | 작은 화면, autofill, 잘못된 로그인, 확대와 keyboard |

이 목록을 실제로 확인하고 발견된 문제를 수정하기 전에는 Goal을 완료 처리하지 않는다.

## 2026-10-05 병역 캘린더 추가 검증

- Docker Java 21/Maven `spotless:apply verify`: BUILD SUCCESS, 185건 중 177건 통과·8건 건너뜀, 실패·오류 0. 병역 날짜 경계·통합 검사와 두 캘린더 변경 알림 포함.
- `tools/launcher/military-test.cjs`: 신규 진입·실시간 수치·DOM 유지·편집 충돌 초안 보존·캘린더 왕복·지연 응답 방어 통과.
- launcher/planner/workspace-apps, responsive 9개 너비, theme 및 `npm run build --prefix tools/ui` 통과.
- 현재 제어 가능한 브라우저 목록이 비어 있어 실제 렌더링·모바일 터치·운영 배포 검증은 수행하지 않았다. 위 결과는 서버와 jsdom/CSS 검사 결과다.

## 2026-10-09 전체 UI 밀도 조정

- 공통 본문 13px, 버튼 30px/compact 28px, 데스크톱 상단 44px·탐색 레일 48px. 편집 본문·터치 목표 크기는 유지한다.
- 앱별 도구 영역과 상태 요약을 압축하고 라이브러리를 작업별 두 열로 구성했다. 아이콘 버튼은 접근성 이름과 툴팁을 보존한다.
- scripts/check-ui.mjs의 실제 Chromium 검사: 내부 앱 21개 × 다크/라이트 × 1440/390px = 84개 화면에서 가로 overflow·잘못된 화면 폭·이름 없는 아이콘·pageerror 없음. 로그인·앱 라이브러리·화면/브라우저/Tailscale 설정도 캡처했다.
- 격리 환경의 /api/v1/tailscale은 관리 서비스 미구성으로 500을 반환했다. 해당 외부 연동 정상 동작으로 보고하지 않는다. 보고서와 캡처는 artifacts/ui/에 저장한다.
- Studio 실제 PTY·중앙 하단 패널·우측 세션 사이드바·리사이즈·접기를 1600/1280/1024/390px에서 재검증했다.
- 화면 검증은 준비된 데이터/빈 상태 범위이며 운영 데이터 전체, 물리 터치, OS IME와 실제 외부 앱 화면을 보장하지 않는다.
