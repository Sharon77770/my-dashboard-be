# 디렉토리

`docker/tailscale/start.sh`는 공식 이미지의 tailscaled를 유지하고 인증을 별도 시도한다. 로그인 대기/거부는 공유 네트워크를 종료하지 않는다.


Java 기준 루트: `src/main/java/com/personal/dashboard/`.

| 경로 | 책임 |
| --- | --- |
| `catalog/controller/CatalogController.java` | 리소스 HTTP 계약과 장비 작업 요청 |
| `catalog/service/CatalogService.java` | 메타데이터 검증, 조회/저장, 검색·탭·브라우저 설정 |
| `catalog/service/DeviceOperations.java` | 실제 계측, Docker/GPU, Wake |
| `catalog/repository/CatalogRepository.java` | 파라미터화 SQLite 쿼리 |
| `catalog/entity/` | 영속 DeviceRecord/ApplicationRecord |
| `catalog/dto/` | 요청·응답·초기 WorkspaceView |
| `files/controller/FileController.java` | 파일 목록·업로드·다운로드·변경 HTTP |
| `files/service/FileService.java` | 경로·이름 검증, 파일 유스케이스 |
| `files/adapter/FileAdapter.java` | 로컬/SFTP 작업, 실제 루트 경계, 스트리밍 |
| `files/dto/` | 파일 목록과 변경 요청 |
| `runtime/controller/` | 세션 API, WebSocket 전송 |
| `runtime/service/RuntimeService.java` | 실행 세션 소유권·연결 수명 |
| `runtime/adapter/` | PTY/SSH, guacd, Chromium 외부 연결 |
| `runtime/dto/` | 실행 요청/응답 |
| `global/RuntimeConfiguration.java` | 같은 origin WS 및 세션 종료 이벤트 |
| `global/DatabaseInitialization.java` | 초기 idempotent schema 적용 |
| `global/WorkspaceErrors.java` | 안전한 API 오류 응답 |
| `global/integration/` | SSH/서버 명령 어댑터 |
| `global/security/` | 계정 인증 및 영속 credential vault |
| `home/controller/`, `login/controller/`, `health/` | 대시보드·로그인·liveness |
| `src/main/resources/db/schema.sql` | 스키마 v2 (기존 데이터 보존 추가) |
| `src/main/resources/templates/` | Thymeleaf 로그인·대시보드·오류 |
| `src/main/resources/static/css/workspace.css` | 앱 콘텐츠와 실행 화면 레이아웃 (테마는 design-system.css) |
| `src/main/resources/static/js/workspace.js` | UI 조작, API/WS 전송과 화면 상태 |
| `src/main/resources/static/vendor/` | 자체 제공 xterm/Guacamole JS와 라이선스 |
| `src/test/java/` | 인증·메타데이터·파일·실행 세션 테스트 |
| `docker/browser/` | Chromium+VNC 이미지 및 진입 스크립트 |
| `Dockerfile`, `compose.yaml` | 서버 및 원격 실행 서비스 구성 |
| `pom.xml`, `mvnw*`, `.mvn/` | 재현 가능한 빌드·테스트·스타일 검사 |
| `docs/features.md` | 참고 화면 기능 대조표 |

`data/`, `target/`, `.tools/`, `.m2/`, `.env`는 Git에서 제외한다. 참고 자료에는 프로젝트 명세를 작성하지 않는다.

- `docker/start.sh`: 기존/새 데이터 볼륨에 영속 셸 홈과 SSH 디렉토리를 준비한 뒤 Java 서버를 실행한다.

- `catalog/controller/SshDeviceController.java`, `catalog/dto/SshDeviceRequest.java`: SSH 명령 형태의 장비 등록 API와 비밀번호 비노출 요청.
- `catalog/service/SshDeviceService.java`: 명령 파싱, 최초 호스트 키 신뢰, 검증 후 장비 생성/갱신. SSH 및 홈 조회는 `global/integration/SshAdapter`에 위임한다.

## Planner 구성
- `planner/controller/PlannerController.java`: 캘린더/시간표/과목 HTTP 라우팅.
- `planner/dto/PlannerDto.java`: 요청·응답 record 계약.
- `planner/entity/PlannerRecords.java`: Event/Term/Course/Meeting 영속 record.
- `planner/service/PlannerService.java`: 날짜·시간·소유권·충돌 검증 및 학점 합산.
- `planner/repository/PlannerRepository.java`: 매개변수 SQL과 course/meeting 매핑.
- `static/js/planner.js`, `static/css/planner.css`: 달력/시간표 화면. workspace.js의 API/모달/탐색과 연결.
- `PlannerIntegrationTest.java`: 영속 CRUD, 경계 시간, 충돌, 권한 검증.

- `compose.yaml`: Tailscale sidecar와 3개 실행 서비스의 공유 네트워크, loopback 제어 포트, TUN 권한, 인증 상태 볼륨.
- `docker/browser/start.sh`: BROWSER_LOCAL_ONLY=true일 때 VNC/CDP loopback 제한. X11 TCP는 비활성화.
- `.env.example`: TAILSCALE_HOSTNAME/ACCEPT_DNS/ACCEPT_ROUTES 입력 계약.

- src/main/java/com/personal/dashboard/studio/: controller, service, adapter, dto, 세션 종료 설정.
- src/main/resources/studio/: SSH에서 실행하는 bootstrap.sh / remote.py.
- src/main/resources/static/{js,css}/studio.*: 원격 에디터 UI.
- tools/studio-editor/: CodeMirror 번들 소스, 잠금 파일과 재현 빌드.
- src/test/python/: 실제 Linux 파일/Git helper 테스트.

## Launcher UI

`static/css/design-system.css`는 공통 테마/컨트롤, `static/css/launcher.css`는 Launcher/모바일 전환을 소유한다. `static/js/ui.js`는 아이콘/툴팁/테마 helper이며 `static/js/launcher/`의 app-registry, grid-model, persistence, widget-registry, interactions, launcher 모듈을 조합한다. `static/js/studio-panels.js`는 IDE 패널 표시만 소유한다. `static/manifest.webmanifest`, `static/css/workspace-icon.svg`는 standalone 기반이다. `tools/launcher/`는 UI·격자·대비 회귀 검사다. 세부 계약은 [Launcher](../launcher.md)를 따른다.

- studio/dto/AssistantDto.java: Codex 세션·모델·입력·이벤트 계약.
- resources/studio/codex_bridge.py: 고정 helper에 포함되는 App Server stdio adapter.
- static/js/studio-codex.js, static/css/studio-codex.css: IDE의 Codex 세션 패널.
- src/test/python/test_codex_bridge.py: 외부 모델 호출 없는 실행형 CLI fixture 계약 테스트.

- static/css/os-shell.css: OS 상태바·시스템 도구·탐색 버튼·실행 앱 카드의 테마 및 반응형 배치.

- global/integration/DeviceNetworkAdapter.java: Tailscale 인터페이스 및 목적지 주소 해석.
- catalog/entity/NetworkMode.java: DIRECT/TAILSCALE 장비 설정.
- resources/db/migrations/V3__device_network.sql: 기존 장비의 네트워크 기본값을 보존하는 v3 마이그레이션.

- `src/main/resources/studio/logs.py`: Docker/tmux 읽기 전용 목록·실시간 출력 CLI 어댑터.
- `src/main/resources/static/js/device-logs.js`, `static/css/device-logs.css`: 장비 로그 앱의 선택·수신·취소·표시.
- `src/test/python/test_device_logs.py`, `tools/launcher/logs-test.cjs`: CLI 격리 fixture와 로그 UI 생명주기 검증.

- `tailscale/{controller,service,adapter,dto}`: 서버 Tailscale 관리 API, 권한, 내부 브리지 호출과 최소 응답 계약.
- `docker/tailscale/{Dockerfile,bridge.py,start.sh,test_bridge.py}`: 고정 버전 sidecar, 인증된 제한 명령 브리지 및 테스트.
- `static/js/tailscale.js`: 상태 자동 조회, 인증 링크, 로그아웃 확인 UI.

- `static/js/log-presentation.js`: 원문/JSON/JSONL 표시와 제한된 text-only 구문 색상 렌더러.
- `tools/launcher/log-presentation-test.cjs`: 큰 숫자 보존, JSONL, 불완전 입력, XSS, 출력 제한 검증.

- `cloud/{controller,service,adapter,dto,entity}`: 개인 드라이브 API, 사용 사례, 로컬 저장소 어댑터, HTTP DTO 및 내부 휴지통 기록.
- `static/js/cloud-drive.js`, `static/css/cloud-drive.css`: 드라이브 탐색/선택/업로드/미리보기/휴지통 화면.
- `src/test/java/com/personal/dashboard/cloud`, `tools/launcher/cloud-test.cjs`: 경로·파일 보존·HTTP 권한과 UI 작업 검증.

- runtime/controller/DesktopSetupController.java: 자동 구성 시작·조회 HTTP 경계.
- runtime/service/DesktopSetupService.java: 동시 작업·기존 설정 보존·연결 검증.
- runtime/adapter/DesktopSetupAdapter.java: SSH 프로그램 실행과 암호화된 관리 연결 상태.
- runtime/dto/DesktopSetupView.java: 안전한 상태 응답.
- src/main/resources/remote-desktop/setup.py: Linux 도구 설치·별도 화면 시작·포트 탐색.
- src/main/resources/static/js/remote-setup.js: 구성 안내·상태 조회·성공 후 원격 탭 열기.

## 메모장
- `notes/controller/NoteController.java`: 문서/폴더/이미지 HTTP 리소스.
- `notes/service/NoteService.java`, `NoteContentValidator.java`: OWNER, 계층/버전/콘텐츠/첨부 검증.
- `notes/repository/NoteRepository.java`, `notes/entity/NoteRecord.java`: SQLite 저장과 내부 record.
- `notes/domain/NoteKind.java`, `notes/dto/NoteDto.java`: 종류 enum과 HTTP 계약.
- `db/migrations/V4__notes.sql`: 비파괴 테이블/인덱스 추가.
- `static/js/notes.js`, `notes-templates.js`, `static/css/notes.css`: 화면과 템플릿.
- `tools/notes-editor/`: 고정 npm 의존성, 빌드 소스, 실제 편집기 DOM 테스트.
- `static/vendor/notes-editor.js`, `.css`, `.LICENSE.txt`: 배포 번들과 라이선스 고지.

- `tools/ui/`: Tailwind CSS 빌드 진입점과 잠금 파일. 생성물은 `static/vendor/workspace-ui.css`이며 홈·로그인·오류 템플릿에서 공통 사용한다.
- `static/js/drawers.js`: 모바일 모달 사이드바의 열기·닫기, 패널 상태와 포커스 복원 담당.
