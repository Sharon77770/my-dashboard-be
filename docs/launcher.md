# Launcher와 디자인 시스템

## 사용자 동작

홈은 앱 바로가기, 폴더, 위젯을 같은 저장 모델로 관리하는 Launcher다. 모든 앱은 App Library, Activity Rail 또는 Ctrl/Cmd+K에서 접근한다. 장비·파일·터미널·원격·앱 관리·캘린더·시간표·코드 에디터·최근 작업·클립보드·브라우저 설정·설정 기능을 유지한다. 등록한 외부 웹앱도 같은 앱 목록에 추가된다.

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

Desktop shell은 작은 global bar와 54px activity rail, 앱 콘텐츠로 구성한다. rail은 Home·검색·Services·Studio·장비·Database Studio·App Library·최근 작업만 표시한다. 고정 앱은 Home의 compact quick bar에 표시한다. 실행 중인 앱/탭은 기존 전환기에서 다시 열거나 닫고, 뒤로 이동은 global bar와 Alt+Left 이력으로 제공한다.

Mobile shell은 44px app bar(이전 작업·현재 작업·검색·메뉴)와 48px Home·검색·앱·최근 작업 탐색을 사용한다. Notes, Assistant, Studio, Database Studio, 터미널/원격/파일 실행 화면은 콘텐츠 집중 모드에서 하단 탐색을 접는다. 현재 작업 버튼으로 전환기를, 상단 검색 버튼으로 palette를 연다. Safe area와 VisualViewport 높이를 반영한다.

두 shell은 같은 앱 레지스트리, HomeItem, page tab, 실행 tab, 검색 계약을 사용한다. 모바일은 Desktop의 축소판이 아니라 Notes·Studio·Database·파일에서 한 pane에 집중한다. OS 화면을 흉내 내는 우측 도구 막대나 하단 뒤로/홈/최근 앱 3버튼은 없다.

## 표현과 편집

일반 Home은 이어하기·서비스·오늘·인프라를 기존 최근 작업과 widget renderer에서 구성한다. 저장된 앱·폴더는 **내 작업 공간**의 compact 목록에, 위젯은 별도의 **위젯** 구역에 기본적으로 닫힌 disclosure 행으로 나타난다. 위젯을 펼치면 같은 열 안에서 기존 렌더러와 동작이 보인다. 편집 모드에는 모두 원래 배치 좌표로 나타난다. 홈 편집은 기존 8열/4열 투영, 드래그, 페이지, 폴더, 크기 조절을 유지한다. 편집하지 않을 때 아이콘 격자나 대형 위젯이 Home을 지배하지 않는다.

App Drawer는 App Library overlay가 된다. 고정·개발·서비스와 인프라·생산성·기타 그룹으로 표시하고 기존 앱 검색/홈 추가/길게 누르기/드래그를 보존한다. Mobile에서는 전체 화면으로 연다. Folder는 저장된 HomeItem과 폴더 dialog를 그대로 사용한다.

명령 Palette는 앱·최근 작업·서버 검색 결과에 Service와 Database Connection 결과를 더한다. 각각 기존 Service Catalog와 Database Studio 화면으로 이동한다. 새로운 검색 서버나 별도 색인은 없다.

## 앱/위젯 추가

1. 기존 앱 화면/실행 기능을 구현하고 `app-registry.js`의 builtins에 ID, 이름, 아이콘, 기존 route/action 또는 실행 kind를 등록한다. 외부 URL은 앱 관리 API로 등록하면 자동 결합된다.
2. 위젯은 `widget-registry.js`에 크기·요약 렌더러를 등록한다. 업무 데이터는 기존 인증 API helper를 사용한다.
3. Home/Library/Search/rail을 별도 등록부로 복제하지 않는다. 변경 시 저장 배치와 keyboard/drag 회귀를 검사한다.

## 검증과 제약

`tools/launcher/test.cjs`는 좌표/폴더/페이지/위젯/잠금/검색/전환/계정 저장을 확인한다. `responsive-test.cjs`는 1440/1280/1024/768/700/430/390/360px에서 shell·Home·Notes 폭·집중 모드 규칙을 확인한다. `theme-test.cjs`는 양쪽 테마의 대비를 확인한다. `drawers-test.cjs`, `github-test.cjs`, `assistant-test.cjs`, `tools/studio-editor/test.cjs`는 주요 앱 동작을 검사한다. JS DOM 검사는 실제 시각적 레이아웃·터치·가상 키보드가 아니며 브라우저/기기 검증과 구분한다.

`npm ci --prefix tools/ui`, `npm run build --prefix tools/ui`로 Tailwind 4 bundle을 생성한다. Preflight는 내장 editor의 스타일 보존을 위해 제외한다. 공통 색상과 typography는 [디자인 시스템](design-system.md)을 따른다.

## Communications

Communications를 중앙 builtins에 등록했다. 같은 Registry를 통해 Home/Dock/Library/Palette와 Workspace 내장 탭 복원에 참여한다. 메시지 작성·대화 분할은 communications.js가 담당하고 기존 실행 탭의 종류/저장 계약은 바꾸지 않는다.

Communications의 메신저 화면 버튼은 카카오톡 Wine, Slack/Discord Chromium 프로필을 생성 또는 재사용해 공통 원격 화면을 연다. 복수 프로필은 계정 관리에서 선택한다. 공식 API 수신함은 별도 선택 기능이며 화면 내용을 자동 수집하지 않는다.
