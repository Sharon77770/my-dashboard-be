# 컴포넌트와 모듈

Studio의 `WorkspaceCodeEditor`는 Monaco model과 view state의 생성·활성화·해제를 담당한다. `studio.js`는 기존 프로젝트와 파일 탭, 저장 revision, 확인 대화상자, Files API 요청을 조합한다. 파일 업로드·다운로드는 기존 FileService/FileAdapter를 사용하며 새 파일 시스템 계층이나 전역 탭을 추가하지 않는다.

`WorkspaceUI.beginTask`는 foreground 요청의 비차단 로딩 안내와 동시 요청 수명을 소유한다. 각 API wrapper는 finally에서 반환된 finish를 호출한다. 배경 inert, 이벤트 차단, 포커스 이동, 로딩 backdrop은 사용하지 않는다. 스타일은 `shell.css`에서 생성 bundle에 포함한다. 자동 조회·실시간 작업 상태는 quiet로 구분한다.

`DeviceCodexController`는 장비 route와 `DeviceCodexDto.Request`를 서비스 입력으로 변환한다. `StudioService`가 장비 대상/action/root·작업 소유권과 수명을 검증하고 `StudioAdapter.executeDeviceCodex`가 SSH 실행에 서버 전용 scope를 전달한다. `WorkspaceDeviceCodex`는 연결·설치·로그인 화면을 맡으며 공통 `StudioCodex`는 host의 jobsPath/storagePrefix/idPrefix를 통해 독립 대화·DOM을 제공한다. 기존 IDE 기본 경로는 유지한다.

`ServiceLogService`는 등록된 Service의 컨테이너 연결과 ISO 시간 구간을 검증하고 `LogHistory` DTO를 조합한다. `ServiceLogAdapter`는 고정 Python reader를 기존 `CommandAdapter`로 실행하며 인증된 SSH 연결을 재사용한다. REST와 MCP는 같은 서비스를 호출하고 자체 로그 수집 로직을 갖지 않는다. Codex bridge는 외부 App Server의 nullable 실행 출력을 정규화하며 이메일·계정 종류만 공개 DTO로 투영한다.

Service Onboarding은 `ServiceDiscoveryService`가 기존 리소스 서비스에서 credential 없는 후보만 추출하고, `ServiceOnboardingService`가 대화별 임시 Draft와 승인 상태를 소유한다. `ServiceOnboardingController`는 채팅 승인 요청의 OWNER/CSRF 경계이며, 실제 카탈로그 쓰기는 `ServiceCatalogService.applyAssistantDraft`가 담당한다. AI 비서는 자연어로 후보 선택과 초안 수정을 처리하고 브라우저는 현재 초안의 미리보기와 명시적인 승인 입력을 관리한다.

Database Studio의 `DatabaseStudioService`는 연결 입력, READ_ONLY, 위험 SQL 확인, 실행 worker·취소, 이력을 소유한다. `DatabaseRepository`는 Workspace 내부 SQLite 연결 메타데이터·이력·즐겨찾기만 저장한다. `DatabaseAdapter`는 PostgreSQL/MySQL/SQLite JDBC URL과 연결 수명, schema metadata, 제한된 결과 변환을 소유한다. `DatabaseDto.ConnectionView`와 Service Context에는 암호문을 넣지 않는다. `databases.js`는 기존 Workspace 라우트의 여러 editor 탭과 탐색 표현 상태를 보유한다.

GitHub는 `GithubController`와 `AssistantMcpService`가 같은 `GithubService` 유스케이스를 사용한다. 외부 호출은 `GithubCliAdapter`, 위험 작업의 승인 상태는 `GithubApprovalService`가 소유한다. `GithubDto`는 REST/MCP의 명시적 응답 타입이고 GitHub upstream JSON을 그대로 노출하지 않는다. `github.js`는 Owner·탭·검색·선택 저장소의 화면 상태만 보유한다. GitHub 계정 데이터는 SQLite entity가 아니다. [구현 범위](../github.md).

catalog는 등록 리소스와 작업 공간 메타데이터를 소유한다. DeviceOperations는 등록 장비에 대한 상태/관리 유스케이스를 소유하며 외부 호출은 CommandAdapter/SshAdapter를 경유한다.
files는 파일 조회/변경/전송만 소유한다. FileService에서 입력을 검증하고 FileAdapter가 실제 canonical 경로와 파일 스트림을 관리한다.
runtime은 연결 핸들·최대 개수·로그인 소유권·수명만 소유한다. TerminalAdapter/RemoteAdapter/BrowserAdapter가 실제 외부 시스템에 연결한다.
로그아웃은 Servlet listener에서 RuntimeService.closeOwner로 전달한다. API controller는 서비스 결과를 상태 코드와 DTO로 변환한다.

DeviceRecord에는 암호문이 있고 DeviceView에는 hasPassword/hasRemotePassword 플래그만 있다. 폼 오류에서도 credential 값을 출력하지 않는다.
WorkspaceView는 장비·앱·클립·즐겨찾기·최근 작업·설정·탭의 안전한 projection이다. Spring Security 객체나 연결 객체를 포함하지 않는다.
브라우저 UI의 state는 서버 WorkspaceView의 복사이며 실제 리소스 변경은 API가 성공한 후 반영한다. 탭 활성화·모달·화면 확대는 표현 상태다.
Thymeleaf 직렬화와 HTML escape로 사용자 입력을 출력하며, shell 명령 또는 SQL을 사용자 이름/경로 문자열로 직접 합성하지 않는다.
Docker 제어는 제한된 start/stop/restart와 검증한 container 식별자만 사용한다. 터미널에서 사용자가 직접 입력하는 셸 명령은 독립적인 OWNER 실행 기능이다.

Planner는 별도 기능 모듈로 요청/응답 DTO와 저장 record를 분리한다. 공유 UI의 API/모달만 사용하며 캘린더와 시간표의 화면 상태는 planner.js 안에 둔다. 달력의 날짜 격자와 시간표 블록 위치는 표시 계산이며 저장/시간 충돌 판단은 PlannerService가 소유한다.


- StudioController: 검증된 job 요청과 상태/취소 HTTP 계약.
- StudioService: OWNER 검사, 세션 소유권, 동시 실행/수명 제한.
- StudioAdapter: local은 환경변수를 제한한 ProcessBuilder, 원격은 SshAdapter로 고정 프로그램과 JSON stdin 전달. 취소 시 stdin EOF와 프로세스 종료.
- studio/remote.py: 원격 경로·revision 검증, 파일/Git/CLI 작업 및 EOF 취소.
- studio.js + CodeMirror bundle: 탐색기, 편집 버퍼, Git/Codex 패널과 job polling.

## Launcher와 공통 UI

Launcher UI → App/Widget Registry, HomeGrid, HomePersistence의 단방향 의존성을 사용한다. 마우스/터치 변환은 interactions 모듈에, 화면 조립과 표시 트랜잭션은 launcher 모듈에 둔다. 일반 Home 요약은 최근 작업과 기존 Widget renderer를 조합하고, 편집에서 HomeGrid 좌표를 그대로 사용한다. 업무 요청은 기존 workspace API helper를 주입한다. 전역 색상은 design-system.css, Desktop/Mobile shell은 shell.css, 아이콘과 공통 상태 표시는 ui.js가 소유한다. [구체적인 모듈 계약과 추가 절차](../launcher.md).

공통 UI는 `design-system.css`의 의미 토큰과 `ui.js`의 SVG 아이콘·진행률·빈 상태·스켈레톤 표시 함수에서 시작한다. 기능별 CSS는 배치와 정보 밀도만 소유하며 홈 Widget Registry도 같은 표시 함수를 사용한다. [디자인 토큰과 컴포넌트 사용 기준](../design-system.md).

### Codex 패널

studio-codex.js는 세션/모델/컨텍스트 및 대화 표시를 소유하고 studio.js가 제공하는 프로젝트·작업 잠금·파일 선택 facade를 사용한다. AssistantDto는 외부 프로토콜의 안전한 HTTP 투영이다. codex_bridge.py만 JSON-RPC 메서드와 스킬·파일 경계를 처리한다.

### 서버 Codex assistant

assistant.js와 assistant.css는 IDE Codex 패널과 분리된 대시보드 전용 내장 앱 뷰를 소유한다. launcher의 앱 registry가 `assistant`를 홈·Dock·모든 앱·검색에 노출하고 상단 `AI 비서` 버튼도 같은 workspace 라우터를 사용한다. 프로젝트 컨텍스트나 StudioCodex 화면을 재사용하지 않는다. 대화는 기존 assistant job과 Codex API를 호출한다. 일반 StudioController의 프로젝트 Codex는 SSH 대상을 사용한다. McpController는 Streamable HTTP 요청·bearer 검증만 담당하고 AssistantMcpService는 기존 Catalog/Planner/Notes/GitHub service의 고정 도구를 제공한다. AssistantEvents는 browser navigation event를 메모리에 보유하며 DB 모델을 추가하지 않는다.

장비 로그 화면(device-logs.js)은 선택·표시·취소를 담당하고 StudioService가 작업 소유권·수명·보유 제한을 관리한다. logs.py는 고정 CLI 인자와 출력 읽기만 담당한다. StudioDto.LogTarget과 Event.sequence가 목록 및 중복 없는 출력 계약이며 파일 Entry나 Codex 이벤트 타입과 혼용하지 않는다.

Tailscale UI는 상태 표시와 링크 열기만 담당하고 Controller → TailscaleService(OWNER) → TailscaleAdapter → sidecar bridge 경계를 따른다. HTTP 응답은 전용 TailscaleView이며 daemon 원본 peer/키 정보는 반환하지 않는다.

장비 로그의 `log-presentation.js`는 받은 텍스트의 JSON 들여쓰기와 안전한 구문 색상 표시만 담당한다. `device-logs.js`가 수신 버퍼/연결을 소유하고 표현 모듈은 이 상태를 수정하지 않는다. 원문 복구와 불완전 JSON의 원문 표시를 보장한다.

CloudStorage는 파일 경로·특수 파일 방어와 IO를 전담한다. CloudService는 권한과 유스케이스 진입 및 안전한 오류 변환을 담당한다. CloudController는 multipart/JSON/stream HTTP 계약만 다룬다. cloud-drive.js는 표시와 서버 요청 조합만 수행하며 서버 파일 내용을 직접 실행하지 않는다.

DesktopSetupService는 기존 CatalogService 프로필과 RemoteAdapter 연결 검증을 조합한다. DesktopSetupAdapter의 내부 Managed 모델은 암호화된 비밀번호와 장비 식별 해시를 보존하며 HTTP DTO와 분리한다. [경계](../remote-desktop.md).

메모장은 NoteController → NoteService → NoteRepository 경계를 따른다. NoteRecord는 저장용이며 HTTP에는 NoteDto.Entry/Document만 반환한다. NoteContentValidator는 블록/링크/크기 검증을 맡는다. NoteMarkdownConverter는 MCP 입력과 이전 MCP 문단의 Markdown을 편집 가능한 블록으로 변환하며, NoteService가 조회 시 조건부 저장한다. notes.js는 폴더 탐색·폼·저장 버전·오류를 관리하고 BlockNote 브리지는 편집·브라우저 Markdown 변환·이미지 업로드 콜백만 맡는다. 템플릿은 notes-templates.js의 독립 블록 복사본이다.
## 실시간 UI 구성

WorkspaceEvents는 도메인 리소스를 읽지 않는 공통 알림 서비스다. HTTP filter는 변경 영역만 전달하고 WebSocket handler는 세션 검증·전송·연결 정리만 수행한다. 각 화면의 `refresh()`는 해당 화면의 상태 소유권을 지키면서 조용한 조회와 부분 반영을 담당한다. 공통 DOM 조정기는 form/editor의 사용자 소유 상태를 덮어쓰지 않는다. [상세](../realtime-ui.md).

병역 전용 MilitaryService는 복무기간·진급 순서·휴가 중복·revision과 집계를 소유한다. MilitaryRecords는 저장 모델, MilitaryDto는 HTTP 모델, MilitaryDates.Progress는 계산 모델로 구분한다. PlannerService에는 Calendar EventView 읽기 투영만 제공한다. military.js는 공통 editor/confirmAction/API/LiveDOM을 조합하고 서버가 반환한 시간 구간 사이의 초 단위 표시만 보간한다. 공통 live coordinator의 `military` 영역으로 갱신하며 별도 WebSocket을 만들지 않는다.
Studio 하단 도구는 StudioWorkbench가 프로젝트별 UI 상태를 소유한다. StudioProcesses는 기존 Studio jobs/helper를 재사용하며, StudioBrowserController → StudioBrowserService → StudioBrowserAdapter는 기존 서버 Chromium과 SshAdapter의 표준 TCP 포워더를 연결한다. StudioApiController → StudioApiService → StudioAdapter는 대상 장비 HTTP client를 호출한다. StudioApiRepository는 별도 persistence record를 CredentialVault로 암호화해 보관하며 DTO를 저장 모델로 직접 사용하지 않는다. 상세 경계와 수명은 [Studio 프로젝트 도구](../studio-workbench.md)를 참조한다.

## 컨테이너 검색 선택

`container-picker.js`는 `select[data-container-search]`에 공통 자동완성 입력을 붙인다. 데이터베이스 연결, 서비스 Runtime/Settings 연결, 장비 로그 대상 선택에 적용한다. 기존 select의 값·FormData·change 이벤트를 유지하며, 검색 자체로 선택을 바꾸거나 작업을 실행하지 않는다. 이름과 제공된 이미지/ID를 대소문자 구분 없이 부분 검색하고 방향키/Enter 또는 클릭으로 선택한다. Escape/포커스 이동은 확정된 값으로 복원한다. 목록 로딩·비활성화·갱신·필수 선택 검증을 기존 앱과 동기화한다. 장비 로그의 tmux 대상에도 같은 검색 UI를 사용한다.

## AI 비서 전용 작업 영역

서버 AI 비서는 `$HOME/.local/share/personal-workspace/assistant-workspace`를 cwd로 사용한다. 기본 Docker 구성에서는 `/app/data/home/.local/share/personal-workspace/assistant-workspace`이며, 일반 Files/IDE 루트 `/app/data/files` 밖에 있고 권한은 0700이다. 폴더 선택 UI에 등록하지 않는다. 이는 동일 OS 계정의 셸 접근까지 막는 별도 보안 sandbox는 아니다.

Assistant 전용 adapter가 내부 `assistantWorkspace` 플래그를 설정하며 브라우저가 보낸 root는 실행 cwd로 사용하지 않는다. IDE 및 SSH 장비 Codex에는 이 플래그를 부여하지 않는다. AI 비서와 IDE는 cwd 기반 작업 잠금도 분리된다. 인증과 대화 저장소인 기존 CODEX_HOME은 유지한다. 기존 서버 파일 루트에서 생성된 AI 비서 대화는 전용 assistant 경로에서만 호환 조회하며, 다음 대화 실행은 전용 cwd로 resume한다. 이전 대화를 복사하거나 삭제하지 않는다.

## Semantic UI roles (2026-10-10)

All application views share `semantic-ui.css`, compiled from `tools/ui/workspace.css`. Body text uses a softer cool gray; meaning is emphasized through foreground, subtle background, border and a symbol/text label together.

- Green: successful/completed/connected or added Git files. Amber: pending/modified/attention. Red: failure/disconnection/conflict/deletion. Blue: running/loading/information. Neutral: unknown, idle or stopped; absence of data is never success.
- App category accents identify location only, not health. All 21 views use common focus, selection and status primitives. Calendar/course colors remain user-owned categories.
- Devices: existing CPU/RAM/disk thresholds (75/90 percent) emphasize numeric values as well as bars. Services/Home reuse health badges. Telemetry distinguishes receiving data from unknown.
- Drive and device files: folder/code/media/config/archive icon roles. Clipboard: expiry within five minutes is amber, retaining the expiry text. Notes: saved/dirty/error status. AI: user/assistant boundaries and error/notice roles.
- Database query lifecycle and Studio process/API response states use explicit state data. API 2xx is green, 3xx amber, 4xx/5xx red; reset clears prior response tone.
- IDE explorer uses actual Git changes, includes parent-directory cues and textual labels, and refreshes while the IDE is visible even when the Git inspector is hidden. Conflict takes priority over modification/addition.
- Raw terminal ANSI colors and external remote/app content remain owned by their producers; dashboard chrome uses shared roles. Embedded third-party pages cannot be restyled reliably across origins.

Verification: `node scripts/check-semantic-ui.mjs` renders the authenticated QA dashboard using current source assets, checks all 21 views in dark/light mode, and checks six mobile views for page overflow. Screenshots/report are in `artifacts/semantic-ui`. Empty and disconnected states are valid render checks, not proof of every connected service workflow. `node tools/studio-editor/test.cjs` verifies real Git state-to-explorer decoration alongside existing editor behavior.

## Communications

CommunicationProvider는 공식 API의 Identity/Conversation/Message/Page/Send를 domain record로 정규화한다. service에서 API DTO와 저장 record를 분리한다. capability는 모든 Provider가 동일한 기능을 제공한다는 가정을 막는다. 기존 WorkspaceEvents/RuntimeService/Guacamole/CredentialVault를 공통으로 사용한다.
상세: [Communications](../communications.md).

Communications 화면 모드: BrowserBridgeService가 프로필 provider로 고정 Chromium/Wine 브로커를 선택한다. 공통 broker는 별도 컨테이너에서 서비스 allowlist와 VNC 포트 범위를 나누며, Wine 실행기는 고정 카카오 설치/실행만 수행한다. 공식 API Provider/MCP는 별도 선택 기능으로 유지한다.

원격 화면 조작은 `WorkspaceRemoteDesktop`에 모아 원격 입력·크기·UI 수명을 관리하고 `workspace.js`는 탭/세션 생성·삭제를 소유한다. 준비 화면은 `WorkspaceRemoteSetup`이 표현하고 환경 판정·설치·프로필 갱신은 서비스/adapter가 수행한다. 설치 권한과 연결 암호는 서로 다른 요청 계약으로 취급한다.
