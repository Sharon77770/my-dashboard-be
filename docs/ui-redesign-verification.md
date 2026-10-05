# UI redesign 검증 현황

## 2026-10-05 실시간 UI 추가 검증

- Docker `mvn -B spotless:apply verify`: BUILD SUCCESS, 176 tests / 168 passed / 8 skipped. 실제 HTTP 로그인·WebSocket Upgrade·REST 변경 수신·로그아웃 종료·Origin/익명 거부·세션별 작업 분리 검증 포함.
- `npm run build --prefix tools/ui`: 성공.
- `realtime-test.cjs`, `telemetry-live-test.cjs`: DOM/편집 상태 유지와 연결 수명, 재접속, 첫 수신 전환, 지연 응답 보호 통과.
- launcher/loading/planner/services/notes/cloud/GitHub/database-poll/assistant/logs/responsive/theme 회귀 검사 통과. 변경된 feature 테스트는 공통 live DOM 모듈을 실제로 로드한다.
- 현재 Browser runtime의 `browsers.list()`는 빈 목록이다. 실제 렌더링·터치·운영 reverse proxy 경유 WebSocket 검증은 수행하지 않았다. 아래 이전 검증 기록과 분리한다.
- 적용 범위와 예외: [실시간 UI](realtime-ui.md).

2026-10-05 공통 로딩 변경: 중앙 모달·3px 배경 blur·입력 잠금과 동시 요청/오류 해제/포커스 복원을 적용했다. `loading-test.cjs`와 Launcher의 실제 API wrapper 검사에서 응답 본문 수신 완료 전 잠금, 탐색 차단, 실패 해제 및 AI 비서 폴링 제외를 확인했다. Launcher, Database polling, Services, Notes, Planner, GitHub, Assistant, responsive/theme 검사와 CSS build 및 Maven verify(171개, 실패 0, 조건부 제외 8)가 통과했다. 실제 브라우저 blur·top-layer·터치 렌더링 검증은 수행하지 않았다.

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
