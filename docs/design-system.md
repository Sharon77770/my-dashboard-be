# Refined Quiet Workspace

Personal Workspace는 개발·운영 작업을 한곳에서 다루는 개인 workstation이다. 이 리디자인은 API, 저장 데이터, 인증, 앱 라우팅, Launcher의 `HomeItem` 계약을 변경하지 않는다. 이전 Android emulator 프레임, 하단 3버튼 탐색, 우측 도구 막대, 아이콘 격자 중심 홈은 사용하지 않는다.

## 정보 구조

- **Desktop:** 46px global bar(Workspace / 현재 작업 / 명령 / 계정), 54px activity rail(Home, 검색, Services, Studio, 장비, Database, App Library, 최근 작업), 나머지는 앱 콘텐츠다. 저장된 pinned apps는 Home의 compact quick bar에 표시한다.
- **Mobile:** safe area를 포함한 44px app bar(이전 작업 / 현재 작업 / 검색 / 메뉴), 콘텐츠, 48px Home·검색·앱·최근 작업 탐색이다. Notes, Assistant, Studio, Database 및 집중형 runtime에서는 하단 탐색을 접고 상단의 검색·최근 작업 진입은 유지한다.
- **Home:** 최근 작업, Services, 오늘 일정, 장비 요약을 기존 API·widget renderer에서 조합한다. 저장된 `HomeItem`과 페이지는 그대로 읽는다. 평상시 `#home-grid`는 앱·폴더만 표시하고 `#home-widgets`는 위젯을 별도 접힌 행으로 표시한다. 홈 편집 시 모든 항목이 `#home-grid`의 기존 좌표 격자로 돌아가 드래그·크기 조절을 유지한다.
- **App Library:** 기존 앱 등록부를 고정·개발·서비스와 인프라·생산성·기타 그룹의 목록으로 제시한다. 기존 검색, 홈 추가, 길게 누르기와 drag 동작을 유지한다.

## 소스와 책임

| 파일 | 책임 |
| --- | --- |
| `css/design-system.css` | 다크/라이트 의미 토큰, 공통 입력·상태·초점·motion |
| `css/shell.css` | Desktop/Mobile global shell 및 기능 간 공통 밀도 |
| `css/workspace.css` | 파일·터미널·장비의 동작 배치 |
| `css/launcher.css` | Command Center, 저장 배치 편집, App Library |
| `css/{notes,studio,telemetry,assistant,github,services,databases,...}.css` | 기능 고유 레이아웃. 공통 CSS 묶음의 `base` 계층에 포함 |
| `tools/ui/workspace.css` | 공통·기능 CSS를 Tailwind 4로 묶는 진입점 |
| `js/ui.js` | 공통 SVG 아이콘, 진행·상태·로딩 표현 |
| `js/workspace.js` | 앱/실행 탭 전환, 전역 명령 팔레트, 현재 작업 표시 |

`tools/ui/workspace.css` → `vendor/workspace-ui.css` 빌드를 유지한다. Telemetry·Assistant·GitHub·Services·Database 스타일도 이 묶음에서 로드하여 shell의 공통 밀도 규칙이 같은 cascade 계층에서 적용되게 한다. 새 SPA framework는 없다. 홈·기능 화면의 ID와 `data-*` selector는 JS 계약이므로 외형 변경만을 이유로 바꾸지 않는다.

기존 상단 실행 탭 막대의 `.tabs`, `.tab`, `.tab-group`, `.tab-close`, `.tab-pin`, `.tab-state` CSS는 제거했다. 세션 고정·닫기·전환은 기존 App Switcher 동작을 유지한다. `workspace.css`와 `launcher.css`에 남아 있던 `.main` 및 `.topbar` 레이아웃도 제거하여 전역 shell은 `shell.css`에서 정의한다.

## 토큰과 시각 문법

기본 바탕은 녹색 기운이 없는 neutral graphite/near-black이고, 밝은 테마는 cool neutral canvas와 흰 표면을 쓴다. Accent는 제한적인 blue-violet이며 primary action은 solid `--accent-solid`이다. 초록색은 성공·연결·실행 같은 상태에만 쓴다. 앱 아이콘의 배경도 neutral 표면으로 통일한다. 그라데이션은 선택된 컨텍스트처럼 제한된 곳에만 사용하고 버튼·진행 막대·일반 카드의 기본 배경으로 사용하지 않는다.

공통 간격은 2/4/6/8/12/16/20/24/32px, radius는 control 6px·panel 8~10px·floating 12px을 기준으로 한다. Desktop 기본 버튼은 34px, compact/toolbar는 30px이며 모바일 icon touch target은 44px이다. 기능 화면의 독자적인 색과 크기 대신 이 토큰을 우선 사용한다.

목록, 상태, 활동, DB 결과, 파일, repository 항목은 row와 구분선을 사용한다. `.panel`은 화면 구조를 위한 selector로 남지만 공통 border/card 모양을 강제하지 않는다. 테두리는 editor, modal, input 등 실제 경계에 사용한다. 아이콘만 있는 버튼은 DOM의 텍스트 또는 `aria-label`로 이름을 제공한다.

공통 앱 헤더의 작업 버튼은 모바일에서 SVG 아이콘과 접근 가능한 이름을 사용한다. 장비·앱·GitHub 같은 주요 화면의 작업은 한 줄에 배치하고 다른 화면은 폭에 맞게 줄바꿈한다. 저장된 위젯은 기본 높이 42px의 disclosure로 표시하며, 펼치면 기존 위젯의 데이터와 동작을 그대로 사용한다. 화면·위젯·버튼에 떠오르는 이동 효과를 반복하지 않고 짧은 opacity/background 전환을 공통으로 사용한다.

Desktop 작업 본문은 대략 12~14px, 모바일 고밀도 목록은 11~13px을 기본으로 한다. Notes 본문은 모바일 12.5px, h1/h2/h3는 각각 21/18/15.5px이다. 브라우저 확대를 막지 않는다. `:focus-visible`, 색상과 함께 있는 상태 텍스트, reduced motion, safe area, 36~44px 모바일 hitbox를 유지한다.

## Mobile 콘텐츠 원칙

Services 목록은 서비스 이름을 먼저 렌더하고 상태와 연결 정보를 각 서비스의 조회가 끝나는 대로 채운다. 전체·정상·확인 필요 건수, 이름·장비·저장소 검색, 상태·환경 필터, 각 서비스의 장비/컨테이너·저장소·텔레메트리 연결 상태를 한 화면에서 보여준다. 모바일에서도 상태 텍스트를 유지한다. 상세의 기본 탭은 상태 신호와 연결 현황, 런타임·개발·계측 요약 및 최근 활동을 표시하고 관련 탭으로 바로 이동한다. 서비스 생성 화면은 기본 정보를 저장한 다음 연결을 이어서 진행함을 명시한다. 개별 상태 조회 실패는 전체 목록을 가리지 않고 해당 서비스의 상태를 미확인으로 표시한다.

- Notes는 문서 폭을 최대화하고 toolbar를 아이콘과 overflow로 축약한다. 코드·표는 축소하지 않고 가로 스크롤한다. 폴더 목록은 기존 drawer를 사용한다.
- Database Studio는 Query / Result / Schema / History를 한 pane씩 표시한다. SQL 실행·취소는 이름이 있는 SVG 아이콘 작업이며 나머지 편집 명령은 overflow에 둔다. 결과 grid는 가로 스크롤한다.
- Studio는 Editor를 기본으로 하며 Files·Git/Codex는 기존 drawer/단일 pane 전환을 유지한다. 서버 Files는 장비·경로 작업을 두 줄로 정렬하고 즐겨찾기·최근 경로를 drawer에 둔다. 파일 행의 수정 시각·크기는 이름 아래에 두고 이름 변경·삭제는 행 작업 메뉴에서 연다. Terminal·원격 화면은 한 줄의 연결 상태와 아이콘 작업만 남겨 앱 영역 전체를 사용한다.
- Cloud는 루트 경로 중복을 접고 제목 줄과 목록 옵션 줄을 분리한다. 한 줄 파일 행, 선택 개수가 있는 조건부 작업줄, 검색 overlay, 드라이브 drawer 및 생성 하단 시트를 사용한다. Service와 Telemetry Detail은 목록 헤더를 접고 뒤로·서비스 상태를 한 줄에 배치한다. Telemetry 목록은 서비스명·상태·요청·오류율 중심의 compact row를 사용하고 나머지 지표는 상세 화면에서 확인한다. GitHub의 Owner·탭·저장소 검색은 모바일 drawer에 두어 상세 본문을 먼저 표시한다. 파일·GitHub·Service 목록도 compact row를 우선한다.
- `100dvh`, `visualViewport`가 갱신하는 `--viewport-height`, `env(safe-area-inset-*)`를 shell과 작업 editor에 적용한다.
- 공통 설정·편집 dialog는 모바일 하단 시트로 표시한다. 제목과 저장·취소 영역은 고정하고 입력 필드만 스크롤하며, 시트의 최대 높이는 VisualViewport와 safe area를 따른다.
- Calendar·Timetable은 설명문 없이 작은 앱 제목과 추가 작업을 표시한다. 모바일 월·학기 선택과 이동/설정 작업은 한 줄에 배치하고, 날짜별 일정 및 시간표 데이터 영역을 우선한다.

## 검증 경계

요구사항별 근거와 실제 화면 검증 대기 목록은 [UI redesign 검증 현황](ui-redesign-verification.md)을 따른다.

`mvn verify`, `npm ci --prefix tools/ui`, `npm run build --prefix tools/ui`, `tools/launcher/*.cjs`, `tools/studio-editor/test.cjs`를 실행한다. Responsive DOM 테스트는 1440, 1280, 1024, 768, 710, 700, 430, 390, 360px에서 shell과 focus CSS 계약을 확인한다. 710px 검사는 Database 탐색기가 모바일 탭이 나타나기 전에 사라지지 않는지도 확인한다. jsdom은 실제 터치, virtual keyboard, line wrapping, 시각 대비를 렌더링하지 않으므로 이 결과를 기기 검증으로 부르지 않는다.
