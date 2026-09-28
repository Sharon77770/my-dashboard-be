# Dashboard 디자인 시스템

대시보드의 모든 앱은 같은 화면 문법을 사용한다. 홈에서는 핵심 수치와 상태를 먼저 보여 주고, 자세한 설명과 작업은 각 앱의 상세 화면에 둔다. 화면 리디자인은 서버 API, 인증, 저장 데이터, 앱 라우팅을 바꾸지 않는다.

## 소스와 빌드

- `src/main/resources/static/css/design-system.css`: 다크/라이트 의미 토큰, 기본 컨트롤, 공통 상태·진행률·카드·로딩·모달 스타일.
- `src/main/resources/static/js/ui.js`: 하나의 선형 SVG 아이콘 집합과 `icon`, `progress`, `ring`, `emptyState`, `skeleton` 표시 함수. 반환 문자열에 넣는 데이터는 함수 내부 또는 호출부에서 escape한다.
- `src/main/resources/static/css/{app,workspace,launcher,os-shell,...}.css`: 각 앱의 배치와 화면 전용 스타일. 색상과 상호작용의 공통 의미를 다시 정의하지 않는다.
- `tools/ui/workspace.css`: 공통 CSS를 불러와 Tailwind로 묶는 진입점. 수정 후 `npm --prefix tools/ui run build`로 `static/vendor/workspace-ui.css`를 갱신한다. Telemetry, GitHub, Assistant는 자체 CSS를 같은 토큰으로 사용한다.

## 의미 토큰

| 역할 | 토큰 |
| --- | --- |
| 바탕과 표면 | `--bg-app`, `--bg-sidebar`, `--bg-surface`, `--bg-elevated`, `--bg-surface-hover` |
| 문자와 경계 | `--text-primary`, `--text-secondary`, `--text-muted`, `--border-subtle`, `--border-default`, `--border-strong` |
| 강조와 상태 | `--accent`, `--accent-solid`, `--success`, `--warning`, `--danger`, `--info` 및 각 `*-subtle` |
| 깊이와 선택 | `--shadow-soft`, `--shadow-raised`, `--accent-gradient`, `--selection-gradient` |
| 간격과 모양 | `--space-1`에서 `--space-8`, `--radius-sm`에서 `--radius-xl`, `--control-sm/md/touch` |
| 움직임 | `--motion-fast/base/slow`, `--motion-ease` |

토큰 이름은 두 테마에서 같다. 기능별 CSS에 색상 리터럴을 추가하지 않는다. 기본 문자와 표면, 주 버튼, 상태색은 `tools/launcher/theme-test.cjs`에서 대비를 검증한다. OS나 아이콘 자체에 특정 의미가 없는 한 색상 하나만으로 상태를 전달하지 않고 짧은 레이블을 함께 둔다.

## 공통 컴포넌트

| 패턴 | 사용 기준 |
| --- | --- |
| `.ui-card`, `.ui-card-interactive`, `.ui-card-active`, `.ui-card-hero` | 일반 표면, 클릭 가능 카드, 선택 항목, 주요 위젯을 구분한다. Hover elevation은 클릭 가능한 카드에만 쓴다. |
| `.ui-status[data-state]` | `success`, `warning`, `danger`, `info` 점과 짧은 상태 이름을 함께 보여 준다. |
| `WorkspaceUI.progress/ring` | 0~100 수치. `null`은 미확인 상태이며 0%와 구분한다. `aria-label`과 수치를 제공한다. |
| `.ui-kpi` | 아이콘, 값, 짧은 레이블 순서. 추가 설명은 툴팁이나 상세 화면에 둔다. |
| `WorkspaceUI.skeleton` | 목록과 카드 형태를 유지하면서 데이터를 기다린다. |
| `WorkspaceUI.emptyState` | 아이콘, 짧은 제목, 선택적인 한 줄 안내. 재시도가 가능한 오류에는 실제 재시도 동작을 연결한다. |
| `.ui-icon-button`, `data-tooltip` | 아이콘만 있는 버튼에는 접근 가능한 이름과 툴팁을 함께 제공한다. |

버튼·탭·모달·드로어는 150~300ms 안에서 입력이나 상태 변화에만 반응한다. `prefers-reduced-motion: reduce`에서는 애니메이션을 끈다. 좁은 화면에서는 네 열 홈과 앱 전환 시트, 전용 드로어를 사용하며 터치 컨트롤은 가능한 곳에서 `--control-touch`를 사용한다.

## 화면별 적용

홈 위젯은 서버, GitHub, Telemetry, 일정, Codex를 값·상태·작은 진행 표시 중심으로 요약한다. 장비 화면은 CPU/RAM/Disk 막대와 상태 레이블을 표시하고 상세 진단은 펼침 영역에 둔다. GitHub Overview는 Owner 작업 지표와 최근 항목을 사용한다. Assistant는 사용량을 숫자와 진행 막대로 표시한다. 파일, 메모, 일정, Telemetry는 공통 로딩·빈 상태를 사용한다. 로그인도 동일한 표면·입력·강조 토큰을 쓴다.

새 앱은 먼저 이 공통 패턴을 선택한 뒤 화면 전용 CSS에는 배치, 밀도, 반응형 전환만 추가한다. 아이콘 이름을 추가할 때는 `ui.js`의 SVG 집합에 넣고 해당 버튼에 `aria-label`을 준다.
