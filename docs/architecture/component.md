# 컴포넌트와 모듈

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

Launcher UI → App/Widget Registry, HomeGrid, HomePersistence의 단방향 의존성을 사용한다. 마우스/터치 변환은 interactions 모듈에, 화면 조립과 표시 트랜잭션은 launcher 모듈에 둔다. 업무 요청은 기존 workspace API helper를 주입한다. UI의 전역 색상과 공통 컨트롤은 design-system.css/ui.js에서 소유하며 기능별 독립 테마를 만들지 않는다. [구체적인 모듈 계약과 추가 절차](../launcher.md).

### Codex 패널

studio-codex.js는 세션/모델/컨텍스트 및 대화 표시를 소유하고 studio.js가 제공하는 프로젝트·작업 잠금·파일 선택 facade를 사용한다. AssistantDto는 외부 프로토콜의 안전한 HTTP 투영이다. codex_bridge.py만 JSON-RPC 메서드와 스킬·파일 경계를 처리한다.

장비 로그 화면(device-logs.js)은 선택·표시·취소를 담당하고 StudioService가 작업 소유권·수명·보유 제한을 관리한다. logs.py는 고정 CLI 인자와 출력 읽기만 담당한다. StudioDto.LogTarget과 Event.sequence가 목록 및 중복 없는 출력 계약이며 파일 Entry나 Codex 이벤트 타입과 혼용하지 않는다.

Tailscale UI는 상태 표시와 링크 열기만 담당하고 Controller → TailscaleService(OWNER) → TailscaleAdapter → sidecar bridge 경계를 따른다. HTTP 응답은 전용 TailscaleView이며 daemon 원본 peer/키 정보는 반환하지 않는다.

장비 로그의 `log-presentation.js`는 받은 텍스트의 JSON 들여쓰기와 안전한 구문 색상 표시만 담당한다. `device-logs.js`가 수신 버퍼/연결을 소유하고 표현 모듈은 이 상태를 수정하지 않는다. 원문 복구와 불완전 JSON의 원문 표시를 보장한다.

CloudStorage는 파일 경로·특수 파일 방어와 IO를 전담한다. CloudService는 권한과 유스케이스 진입 및 안전한 오류 변환을 담당한다. CloudController는 multipart/JSON/stream HTTP 계약만 다룬다. cloud-drive.js는 표시와 서버 요청 조합만 수행하며 서버 파일 내용을 직접 실행하지 않는다.

DesktopSetupService는 기존 CatalogService 프로필과 RemoteAdapter 연결 검증을 조합한다. DesktopSetupAdapter의 내부 Managed 모델은 암호화된 비밀번호와 장비 식별 해시를 보존하며 HTTP DTO와 분리한다. [경계](../remote-desktop.md).

메모장은 NoteController → NoteService → NoteRepository 경계를 따른다. NoteRecord는 저장용이며 HTTP에는 NoteDto.Entry/Document만 반환한다. NoteContentValidator는 블록/링크/크기 검증을 맡는다. notes.js는 폴더 탐색·폼·저장 버전·오류를 관리하고 BlockNote 브리지는 편집·Markdown 변환·이미지 업로드 콜백만 맡는다. 템플릿은 notes-templates.js의 독립 블록 복사본이다.
