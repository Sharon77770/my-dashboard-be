# Launcher와 디자인 시스템

## 사용자 동작

홈은 앱 바로가기, 폴더, 위젯을 같은 격자에 배치하는 Launcher다. 모든 앱은 Dock의 **모든 앱**, 시스템 도구 모음 또는 Ctrl/Cmd+K에서 접근한다. 장비·파일·터미널·원격·앱 관리·캘린더·시간표·코드 에디터·최근 작업·클립보드·브라우저 설정·설정 기능을 유지한다. 등록한 외부 웹앱도 같은 앱 목록에 추가된다.

- **홈 편집**: 앱/위젯/폴더/페이지 추가, 드래그 이동, 항목 메뉴, 위젯 모서리 크기 조절. 일반 모드에서는 홈 항목이 움직이지 않는다. 레이아웃 잠금은 편집·추가·제거·Dock 변경을 차단한다.
- **배치**: 항목 메뉴의 위치/페이지/크기 폼은 드래그의 키보드 대안이다. 편집 중 항목에 초점을 두고 방향키로 한 칸씩 이동하며 Enter로 메뉴를 연다. 이동 대상에 앱을 놓으면 폴더를 만들고, 폴더 위에 놓으면 내부로 이동한다. 일반 충돌은 기존 항목을 빈 공간으로 재배치한다.
- **폴더**: 클릭해 열고 이름을 변경한다. 편집 중 내부 앱을 드래그해 순서를 바꾸거나 폴더 밖의 홈에 놓는다. 앱 메뉴의 앞으로/뒤로/폴더 밖 이동도 지원한다. 마지막 앱을 꺼내면 빈 폴더가 사라진다. 새 폴더는 포함할 앱을 선택해 만든다.
- **페이지**: 표시점 클릭, 홈의 수평 휠/스와이프, 항목 배치 폼을 사용한다. 드래그한 항목을 페이지 표시점에 놓으면 해당 페이지로 이동한다. 페이지 삭제는 빈 페이지에만 허용한다.
- **Dock**: 앱의 우클릭/길게 누르기 메뉴에서 추가/제거한다. 최대 6개 앱이며 모든 앱 버튼은 항상 제공된다.
- **App Drawer**: 이름순 정렬, 검색, 홈 추가 버튼, 홈으로 드래그를 지원한다. 모바일에서는 전체 화면으로 연다.
- **검색**: 홈 검색 위젯과 Ctrl/Cmd+K는 동일한 팔레트다. 중앙 앱 등록부의 검색 결과와 기존 서버 `/search` 결과를 함께 표시한다. 서버 검색은 등록 장비/앱/파일 경로 즐겨찾기이며 이미 로드한 최근 이력을 팔레트에서 병합한다. 서버 전체 파일 인덱스를 새로 만들지 않는다.

## 계층과 계약

모든 파일 경로는 `src/main/resources/static/` 기준이다.

| 모듈 | 책임 |
| --- | --- |
| `js/ui.js`, `css/design-system.css` | SVG 아이콘, 툴팁, 토큰 조회, 터미널 테마, 공통 컨트롤과 상태 |
| `js/launcher/app-registry.js` | `WorkspaceApp` 정의, 내장 앱과 등록 외부 앱 결합, 이름/아이콘/route/action/kind 매핑, 앱 검색 |
| `js/launcher/grid-model.js` | `HomeItem` 표시 모델, 좌표·충돌·빈 공간 탐색·이동·반응형 투영·저장 입력 검증 |
| `js/launcher/persistence.js` | 인증된 계정 이름으로 구분한 브라우저 localStorage 읽기/쓰기 |
| `js/launcher/widget-registry.js` | 위젯 정의/크기/미리보기/요약 렌더링, 기존 API 결과 사용 |
| `js/launcher/interactions.js` | 마우스 및 터치 pointer/long press/drag/swipe 입력을 의미 있는 배치 동작에 전달 |
| `js/launcher/launcher.js` | 홈 상태, 편집 트랜잭션, Drawer/Folder/Picker/Context 조립 |
| `js/workspace.js` | 기존 API·연결·검색·설정, 앱 화면 탭과 실행 탭 통합 |
| `js/studio-panels.js` | IDE 데스크톱 패널 너비, 모바일 단일 화면 모드 |

Launcher → 레지스트리/격자/저장 모듈 방향으로 의존한다. 업무 데이터 조회·변경은 기존 인증된 API helper를 거치며 Java controller/service/adapter 계약은 변경하지 않는다. 홈 좌표는 표시 상태이며 서버 리소스 권한을 부여하지 않는다.

`WorkspaceApp`은 `id`, `name`, `icon`과 `route`(내장 화면), `action`(기존 모달/검색), 또는 `kind`+`targetId`(기존 실행 리소스)를 가진다. 선택적인 `widgets`는 해당 앱이 제공하는 WidgetDefinition ID다. 외부 앱 ID는 `web:<서버 ID>`로 이름 충돌을 피한다.

`HomeItem` 공통 필드: `id`, `type`, `page`, `x`, `y`, `w`, `h`. `type=app`은 `appId`, `type=folder`는 `name`과 순서가 있는 `apps`, `type=widget`은 `appId`, `widgetId`를 가진다. 앱/폴더는 1×1이다. 위젯 크기는 정의에 있는 값만 복원한다. 페이지는 0부터 시작한다.

격자 표준은 8열, 모바일 투영은 4열이다. 두 환경이 별도 홈 데이터를 갖지 않는다. 투영은 원본을 변경하지 않고 너비를 맞춘 뒤 충돌을 다시 배치한다. 좁은 화면에서 편집하면 현재 보이는 투영을 기준으로 저장한다. 따라서 모바일 편집 이후 데스크톱 배치도 달라질 수 있다. 한 페이지 최대 64행, 총 12페이지/160항목, 폴더당 복원 상한 60앱이다. 이동·리사이즈가 실패하면 이전 배치를 유지한다.

## 저장과 수명

- `workspace-home-v1:<인코딩 계정>`: `{version:1,pages,locked,dock,items}`. 브라우저별 저장이다. 서버 동기화/SQLite 변경은 없다. 잘못된 버전/JSON은 기본 배치를 표시하고 알린다. 알 수 없는 앱/위젯, 빈 폴더, 중복 ID는 검증 중 제거한다. 저장 권한/용량 오류는 토스트로 표시하고 현재 창의 메모리 배치를 유지한다. 다른 탭의 storage 이벤트로 갱신하며 편집 모드를 종료한다.
- `workspace-app-tabs-v1:<인코딩 계정>`: 내장 앱 화면 ID. 서버 실행 탭과 구분한다. 기존 `/tabs` API의 kind enum과 SQLite 저장은 그대로다.
- `workspace-studio-panes-v1`: 탐색기/보조 패널 너비. 범위를 검증해 복원한다.
- 기존 `workspace-studio-project-v1`: 최근 대상과 프로젝트 폴더. 파일 내용과 Codex 대화/인증 코드는 저장하지 않는다.
- Codex 대화: 선택한 로컬/SSH 서버의 Codex App Server thread에 저장한다. 프로젝트별 세션 검색·복원·새 세션·분기·보관을 제공한다. UI는 최근 50개 turn을 표시하고 같은 thread의 전체 컨텍스트로 대화를 이어간다. 마지막 세션 ID는 프로젝트별 sessionStorage에 두며 Git 새로고침으로 대화를 지우지 않는다. [상세](codex.md)

## 위젯

검색, 장비 상태, 오늘 일정, 최근 터미널 연결, 최근 파일 위치, 최근 프로젝트, Codex 상태를 제공한다. 위젯은 앱 전체를 축소하지 않는다. 높이 1의 작은 위젯은 요약, 큰 위젯은 제한된 목록을 표시한다. 장비 수치는 기존 계측 결과이며 미측정은 미확인/—다. Git/Codex 위젯은 에디터가 현재 세션에 보고한 마지막 상태를 보여주며 위젯 조회가 CLI 실행을 시작하지 않는다. 오늘 일정은 기존 GET API를 사용하고 실패와 다시 시도를 표시한다.

`WidgetDefinition`: `id`, `appId`, `name`, `description`, `sizes:[[w,h],...]`, `defaultSize:[w,h]`. Picker에서 실제 요약 미리보기를 보여준다. 지원 크기에 맞춰 폼이나 모서리 드래그로 변경한다.

## Desktop / Mobile

Desktop은 44px 앱 아이콘과 작은 라벨, OS 상태바와 카드형 실행 앱 전환기, 우클릭 메뉴, 마우스 드래그, 툴팁을 사용한다. IDE는 Explorer | Editor | Git/Codex이며 구분선을 마우스 또는 방향키로 조절한다. 좁은 데스크톱에서는 패널 폭을 제한하고 Files / Git·Codex 버튼으로 패널을 접을 수 있다.

Mobile은 4열 홈, 길게 누르기 메뉴, 수평 스와이프, 하단 Dock, 전체 화면 Drawer, 하단 Context/Widget/App Switcher sheet를 사용한다. 상단에는 현재 앱을 표시하고 열린 앱 전환기를 제공한다. IDE는 Editor가 기본이며 Files와 Git/Codex는 전체 보조 화면으로 전환한다. 터미널은 불필요한 외곽을 제거하고 `100dvh`/VisualViewport 및 기존 fit/ResizeObserver로 키보드 크기 변경에 대응한다. safe-area를 헤더/Dock/sheet에 적용한다.

`manifest.webmanifest`는 standalone 이름/시작 경로/아이콘을 제공한다. 인증 세션으로 manifest를 가져온다. 서비스 워커, 오프라인 업무 데이터, push 알림은 도입하지 않는다. 브라우저별 설치 조건은 다를 수 있다.

## 시각 규칙

색상 값은 `design-system.css`에만 둔다. 기능 CSS는 배경(surface), 텍스트, 경계, accent, 상태 토큰을 사용한다. 사용자가 지정한 일정/과목 색상은 데이터로서 테두리 표시를 유지한다. 본문은 테마 텍스트 색상으로 읽히므로 임의의 사용자 색상이 글자 대비를 깨뜨리지 않는다. 브랜드 SVG/manifest는 독립 배포 자산이다.

공통 Button(primary/secondary/ghost/danger, sm/md), IconButton, Input/Select/Textarea/Checkbox, Panel, Toolbar, Badge, Dialog, details 기반 Dropdown, Tooltip, Empty/Loading/Error, Splitter를 사용한다. spacing 기준은 4/8/12/16/24/32px, 기본 컨트롤 28/32px, radius 4/6px이다. Launcher 아이콘과 mobile sheet는 별도 형태 토큰에 준하는 12px 모서리를 사용한다. 의미별 색상 토큰은 두 테마에서 같은 이름을 가진다.

primary 버튼은 단일 accent 배경과 inverse 글자 조합으로 고정하고 컨테이너별 덮어쓰기를 제거했다. xterm ANSI/배경/선택, CodeMirror 구문/검색/선택/자동완성, 캘린더/시간표도 토큰을 공유한다. 외부 VNC/RDP 화면 내부는 대상 앱의 테마를 따른다.

## 앱/위젯 추가

1. 기존 앱 화면/실행 기능을 구현하고 `app-registry.js`의 builtins에 ID, 이름, 아이콘, 기존 route/action 또는 실행 kind를 등록한다. 임의 외부 URL은 앱 관리 API로 등록하면 자동 결합된다.
2. 위젯이 필요하면 해당 앱의 `widgets`에 ID를 추가하고 `widget-registry.js`에 크기/설명과 요약 렌더러를 등록한다. 업무 데이터를 새로 조회해야 한다면 기존 인증 API helper를 사용한다.
3. Home/Drawer/Dock/Search를 각각 수정하지 않는다. 새 앱을 모든 사용자의 Home에 강제로 추가하지 않는다.
4. 격자/등록부/DOM 회귀 검사와 양쪽 테마 대비 검사를 실행한다. API 변경이 있으면 별도로 서비스·계약 검사를 추가한다.

## 검증과 남은 제약

`tools/launcher/test.cjs`: 격자 충돌/투영, 등록부 escape, 앱 탭/전환, 페이지, Drawer, 폴더 생성/해제/드래그 순서, 위젯/크기, 잠금, 공통 검색, 계정 저장.
`tools/launcher/planner-test.cjs`: 일정/학기/수업 CRUD 회귀.
`tools/launcher/responsive-test.cjs`: 1440/1280/1024/768/700/390px CSS 규칙과 IDE 모드 선택.
`tools/launcher/theme-test.cjs`: 양쪽 테마 텍스트 4.5:1, 주요 컨트롤 경계 3:1, 기능 CSS의 고정 색상 방지.
`tools/studio-editor/test.cjs`: 실제 CodeMirror 번들, 파일 저장/수정 보호, Codex 인증 안내/Enter 실행/취소/결과 유지, 모바일 패널 상태.

jsdom은 화면 배치와 실제 터치·가상 키보드를 렌더링하지 않는다. 이번 환경에는 연결 가능한 브라우저가 없어 실제 viewport/스크린샷 검증은 미완료다. 실제 기기에서 모바일 키보드와 드래그, 저해상도 패널 폭, 설치 UX를 추가 확인해야 한다.

테스트 실행은 `tools/studio-editor`의 잠금 파일에 맞춰 `npm ci` 후 저장소 루트에서 `NODE_PATH`를 해당 `node_modules`로 지정하고 각 `.cjs` 파일을 `node`로 실행한다. Java 검사는 `mvn verify`, Linux 전용 검사를 포함한 배포 검사는 Dockerfile의 `mvn verify`를 사용한다.

IDE 공간 배치: 큰 제목과 상단 연결 폼을 한 줄 명령 모음으로 통합했다. 실행 대상은 직접 선택하고, 경로 입력·폴더 열기는 폴더 메뉴에 배치한다. 초기 안내는 미연결 상태에만 표시한다. 작업 결과는 하단 접이식 영역으로 제공하며 IDE는 앱의 남은 높이를 사용한다. Codex 첨부는 ＋ 메뉴, 모델·추론·전송은 하단 입력창에 표시한다.

## OS 에뮬레이터 프레임

상단 웹 탭은 제거하고 현재 앱 이름·실제 시각·시스템 메뉴를 표시한다. 데스크톱 우측 도구 막대는 앱 목록, 검색, 전체 화면, 설정을 제공한다. 하단의 뒤로·홈·실행 중인 앱 버튼은 모든 앱에서 접근할 수 있다. 모바일에서는 우측 막대를 감추고 하단에 앱 목록 버튼을 추가한다. 전체 화면은 브라우저 Fullscreen API가 지원될 때만 동작한다.

앱 전환기는 아이콘·이름·상태를 표시하는 카드이며 실제 화면 캡처를 흉내 내지 않는다. 기존 앱 닫기·서버 세션 종료·고정 기능을 유지한다. 뒤로는 열린 다이얼로그와 메뉴를 먼저 닫고, 이후 최근 방문한 앱 화면으로 돌아간다. 닫힌 앱은 건너뛰며 기록이 없으면 홈으로 이동한다. 방문 기록은 현재 페이지 메모리에 최대 50개 유지하고 외부 브라우저 방문 기록을 변경하지 않는다.
