# Studio 프로젝트 도구

기존 Studio와 Monaco, SSH/SFTP, Runtime Terminal, Codex 및 서버 Chromium을 확장한다. 대시보드 Home, Launcher, 전역 탐색/탭 구조는 변경하지 않는다.

## 프로젝트 폴더 생성

폴더 열기 메뉴에서 장비와 기존 상위 폴더를 선택한 뒤 `새 폴더`로 하위 폴더를 만들 수 있다. 로컬과 SSH 모두 기존 Studio `mkdir` 작업과 권한 검사를 사용한다. 이름에는 경로 구분자를 사용할 수 없으며, 이미 존재하는 이름이나 쓰기 권한 오류는 대화상자에 표시한다. 생성 후 새 경로가 자동 선택되고 `폴더 열기`로 시작한다. 생성만으로 현재 프로젝트나 저장하지 않은 편집 내용을 바꾸지 않는다.

## 하단 패널

Terminal / Problems / Output / Tests / Ports / Browser / API 탭은 중앙 에디터 열의 하단에 배치한다. 좌우 Files/Git/Codex 영역은 전체 높이를 유지한다. 경계선을 드래그하거나 키보드 방향키로 높이를 조절하며 중앙 에디터에 최소 공간을 남긴다. 접기 및 프로젝트 전환은 터미널 WebSocket을 닫지 않는다. Studio 프로젝트 터미널은 네트워크 단절 후 30분 동안 같은 셸을 유지하며 기존 핸들로 재연결하면 최근 65,536자 출력을 재생한다. 명시적인 닫기, 셸 종료, 로그아웃, 서버 종료, 재연결 유예 만료 시 정리한다. 같은 로그인 세션에서 새로고침하거나 프로젝트를 다시 열면 서버 목록으로 기존 핸들을 복원한다. 다른 탭이 연결 중인 셸은 자동으로 빼앗지 않으며 해당 탭을 닫은 뒤 재연결할 수 있다.

Terminal은 기존 `/sessions`와 `/ws/runtime/{id}`를 사용한다. 선택한 프로젝트 root를 기존 FileAdapter 경계로 검증한 다음 local Linux PTY의 cwd 또는 SSH 셸의 작업 디렉터리로 지정한다. 다른 장비의 같은 경로는 별도 세션이다. 세션 목록·생성·이름 변경·재연결·종료는 터미널 내부 우측의 작은 사이드바에 배치한다. 출력 화면은 탭 본문의 전체 높이를 사용하고 사이드바는 별도로 스크롤한다.

## Run / Tests / Problems / Output

Output에 이름, 실행 명령, Run/Test/Build 유형을 등록한다. 명령 추천은 Maven/Gradle/npm/Python 설정 파일을 참고하며 실제 프로젝트 스크립트와 맞는지 확인하여 실행한다. 명령은 해당 장비 계정의 `/bin/bash -lc`로 실행하며 임의 명령 실행은 OWNER에게만 허용된다.

관리 프로세스는 Studio HTTP 작업과 분리된 Linux 프로세스 그룹이다. 패널/요청 종료 후에도 유지되며 Stop/Restart로 제어한다. 기록은 대상 계정의 `~/.local/share/personal-workspace/runs/<project hash>`에 저장한다. 프로젝트당 실행 8개, 기록 64개, 로그 1 MiB 순환 제한이 있다. 마지막 로그 조회는 200 KB이다. 명령에 비밀번호를 직접 넣지 않는다. Run All은 등록한 Test 명령을 실행하고, 없으면 탐지된 테스트 명령을 실행한다. Rerun Failed는 실패한 **명령 전체**를 재실행한다.

Problems는 Monaco 진단과 출력에서 해석할 수 있는 파일/라인을 표시한다. 모든 언어의 구조화된 테스트 리포터를 구현한 것은 아니다. 해석하지 못하는 실패도 원문 Output에 남는다. 실행 출력은 대화형 PTY와 분리되어 있다.

## Ports / Browser

Linux `/proc/net/tcp{,6}`의 listening 포트를 탐지하고 읽을 수 있는 프로세스 cwd/FD를 대조한다. 최대 64개 포트에 짧은 HTTP HEAD를 보내 응답이 HTTP인 경우에만 Preview를 제공한다. 권한상 PID를 읽지 못하거나 IPv6-only/TLS 포트가 HTTP로 확인되지 않으면 TCP로 남는다. 복사 URL은 **대상 장비의 localhost 기준**으로 IDE Browser/API에 사용한다.

Browser는 `workspace.browser-host`/`workspace.browser-port`의 기존 Chromium CDP 서버에 프로젝트 전용 탭을 생성한다. JPEG 실제 화면을 주기적으로 갱신하고 클릭, 기본 키 입력, 스크롤, 뒤로/앞으로/새로고침을 전달한다. 별도 프레임 임베드가 아니므로 대상 사이트의 frame 제한과 무관하다. 콘솔 오류, 실패한 네트워크 요청, title 및 최대 24 KB page text를 수집한다. 한글 IME는 별도 텍스트 입력란에서 조합한 결과를 전송한다. 원격 필드 안의 직접 조합 편집, 파일 업로드/다운로드, 전체 DevTools UI는 제공하지 않는다.

Chromium은 대시보드와 같은 네트워크 namespace를 사용하는 기존 Compose 구성을 전제로 한다. SSH localhost 미리보기는 지문 검증된 기존 SshAdapter로 서버 loopback 터널을 연다. 서버를 외부로 공개하는 포트 포워딩은 생성하지 않는다. TLS 검증을 끄지 않으므로 터널 URL과 일치하지 않는 인증서는 실패할 수 있다. 로그인 세션별/프로젝트별 탭이며 전체 최대 8개, 30분 유휴 정리한다. Chromium 프로필의 쿠키는 기존 서버 브라우저와 공유된다.

SSH 터널은 프로젝트 서버 재시작 중 연결 거절이 발생해도 다음 연결을 계속 수신한다. 앱이 절대 origin/별도 HMR WebSocket 포트를 강제하는 경우 앱의 proxy/HMR 설정이 필요할 수 있다. 검증 프로젝트는 Python HTTP 서버이며 모든 프레임워크의 HMR까지 검증하지는 않았다.

## API

GET/POST/PUT/PATCH/DELETE/HEAD/OPTIONS, Params, Headers, JSON/text/form/multipart, Basic/Bearer/API Key를 지원한다. Python 표준 HTTP client가 프로젝트 장비에서 실행하므로 SSH의 localhost도 접근 가능하다. TLS 인증서를 검증하고 리다이렉트는 자동 추적하지 않는다. 헤더 주입 및 전송 계층 헤더 직접 지정은 거부한다. 요청/응답 1 MiB, 연결/읽기 15초 및 요청 종료 타이머 20초 제한이다. UI 파일 첨부는 500 KB 이하이다.

`{{NAME}}` 환경변수는 서버에서 치환한다. 환경변수 값은 저장 이후 API로 다시 반환하지 않는다. 프로젝트별 최신 요청/응답 30개와 환경변수는 `workspace.studio-state-path`(기본 `./data/studio-api`)의 암호화 파일에 저장하며 기존 CredentialVault의 AES-GCM key를 사용한다. 파일과 key를 함께 백업하되 key를 외부에 노출하지 않는다. SQLite schema 변경은 없다. 로그인 세션이 달라도 같은 OWNER 프로젝트 기록은 재사용한다. 기록의 다시 전송은 저장한 요청을 실제로 재실행한다.

알려진 인증값/환경변수 및 민감한 응답 헤더는 응답 표시에서 마스킹한다. 요청 본문에 임의로 포함한 별도 비밀값까지 자동 판별하지는 못하므로 주의한다. 비밀값은 plain localStorage에 저장하지 않는다. cURL 가져오기는 일반적인 URL, method, header, data, Basic, form 옵션을 파싱하며 셸을 실행하지 않는다. 지원하지 않는 옵션/로컬 파일 경로는 오류로 알린다. 내보내기는 인증값을 `{{SECRET}}`로 치환한다.

## Codex

현재 프로젝트 root를 사용하고 파일/Git/명령 실행은 기존 Codex App Server 기능을 유지한다. local 프로젝트 Codex는 대시보드 assistant와 분리된 trusted adapter 플래그를 사용하며 대시보드 MCP 자격증명을 주입하지 않는다. 기존 assistant의 읽기 전용 경계는 유지한다.

local 프로젝트의 CODEX_HOME은 `~/.local/share/personal-workspace/project-codex`이며 assistant 로그인과 분리한다. Linux sandbox용 `bubblewrap`은 dashboard 이미지에 포함한다. SSH 계정에서는 배포 환경에 맞게 설치해야 한다. 실제 기본 컨테이너 권한으로 workspace-write 실행을 검증했으며, 호스트별 user namespace 정책 차이는 별도 확인한다. [공식 sandbox 조건](https://learn.chatgpt.com/docs/sandboxing).

Studio에서 전송한 메시지에는 최근 관리 프로세스/port/tool output, Browser URL/title/text/console/network, API request 개요와 마스킹된 response를 최대 64 KB의 tool observation으로 첨부한다. 외부 페이지/API/출력은 신뢰할 수 없는 데이터다. 첨부는 관찰 시점의 스냅샷이고, 아래 동적 도구가 실행 중 최신 Browser/API 결과를 추가로 조회한다. 대상 계정에 실제 Codex 로그인이 필요하고 파일 수정은 사용자가 선택한 실행 권한을 따른다.

새 Studio Codex 대화에는 App Server dynamic tools인 `studio_browser`, `studio_api`, `studio_process`를 등록한다. Browser는 open/snapshot/reload/back/forward/click/text/key/scroll, API는 GET/HEAD/OPTIONS/POST/PUT/PATCH/DELETE와 none/json/text 본문, Process는 list/ports/logs/stop/restart를 제공한다. 현재 job의 로그인 소유자·장비·root로 고정하며 모델이 다른 프로젝트를 지정할 수 없다. 파일 수정 허용 모드에서만 실행하고, Browser/API URL은 현재 프로젝트 프로세스가 실제 사용하는 loopback listening port로 제한한다. Browser 도구 제어 중 다른 origin의 네트워크 요청은 차단한다. 도구 응답은 64,000자로 제한하며 외부 출력은 신뢰할 수 없는 관찰이다. 임의 셸 명령 도구를 추가하지 않고 파일/명령 실행은 기존 Codex sandbox를 사용한다. 기존 관리 프로세스를 재시작할 수 있다.

업데이트 이전 Codex 대화에는 동적 도구가 등록되어 있지 않으므로 실시간 도구를 사용하려면 Studio에서 새 대화를 시작한다. 기존 대화와 기록은 삭제하지 않는다. 서버 Chromium이 재시작되면 끊어진 탭을 정리하고 URL 열기로 다시 연결한다. 이미지 미리보기의 입력칸에 포커스를 둔 후 전용 텍스트 입력란으로 한글 조합·붙여넣기 결과를 전송할 수 있다.

브라우저 이미지는 profile volume의 exclusive flock을 확보한 뒤 이전 컨테이너의 Singleton 잠금 3종만 제거한다. 프로필·로그인은 보존한다. CDP 기동 실패는 컨테이너 실패로 처리하며 9223 `/json/version` healthcheck를 제공한다.

실제 검증 결과와 아직 검증하지 못한 항목은 `studio-ide-validation.md`에 별도로 기록한다.

### IDE Codex CLI 업데이트

Codex 패널의 세션 메뉴(⋯)에서 **Codex 업데이트**를 선택한다. 열린 프로젝트의 deviceId/root로 기존 `POST /api/v1/studio/jobs`의 `setup` action과 `args.refresh:true`를 사용한다. 로컬과 SSH 모두 최신 stable 릴리스 캐시를 무효화하고 공식 릴리스의 SHA256 검증 설치를 실행한다. 별도 endpoint나 임의 설치 명령은 추가하지 않는다.

현재 탭의 작업 실행 중에는 업데이트를 시작할 수 없다. 설치 잠금은 기존 장비별 파일 잠금을 재사용한다. 명시적인 업데이트 실패는 FAILED/502이며 이전 버전으로 계속하면서 성공으로 처리하지 않는다. 일반 초기 준비의 기존 fallback은 유지한다. 완료 버전은 메뉴와 상태줄에 표시하고 모델·인증을 새로 조회한다. 대화·첨부·입력 초안·미저장 소스를 초기화하지 않는다. Linux x86_64/aarch64 및 장비의 외부 GitHub 접근이 필요하다. 운영 장비를 자동 업데이트하거나 재배포하지 않는다.
검증: 격리 Linux 임시 홈에서 공식 릴리스 다운로드·SHA256 검사 후 codex-cli 0.162.0 실행 확인. 기존 QA 대시보드에 수정 프런트엔드를 제공한 Playwright 검사에서 local 프로젝트의 setup(refresh=true) 전송과 완료 버전·모델/인증 갱신을 확인했다. SSH 최신 갱신 분기는 오프라인 회귀 검사 및 격리 helper 실행으로 검증했으며 실제 SSH 장비의 버튼 종단 검증은 별도다. Java 186개 통과·9개 건너뜀, Python 49개 통과, Studio DOM 회귀 검사 통과. 운영 배포는 하지 않았다.

편집기 상단 명령은 30px 단일 행으로 표시한다. 파일명은 말줄임과 전체 경로 툴팁으로 제공하고 검색·저장은 아이콘으로 유지한다. 다시 읽기, 찾기, 바꾸기, 줄 이동, 모두 저장은 더보기 메뉴에서 실행하며 기존 키보드 단축키는 유지한다.

## Git 상태와 GitHub 인증

Git 패널 표시 중 5초 간격 및 창 복귀 시 읽기 전용 상태를 갱신한다. 변경 없는 응답은 목록 DOM을 다시 그리지 않는다. 터미널에서 init·add·commit한 결과도 다음 조회에 반영된다. 저장소가 없으면 초기화/복제를 안내하고, 장비 접근 경계 안의 상위 저장소 또는 최대 200개 직계 항목에서 발견한 하위 저장소 열기를 제안한다. 다른 저장소를 자동으로 수정하지 않는다. Git worktree의 외부 gitdir는 기존 경로 제한을 유지한다.

변경 목록 → 스테이징 → 커밋 순서로 배치하고 diff는 Git 패널 안에서 확인한다. 충돌 그룹은 충돌이 있을 때만 표시한다. 원격 추적 브랜치 및 ahead/behind는 마지막 fetch 기준이며 자동 fetch/pull/push는 수행하지 않는다.

GitHub 로그인은 편집기 전역 busy와 프로젝트 쓰기 잠금을 사용하지 않는다. 계정별 별도 인증 잠금으로 중복 로그인을 막는다. 인증 URL/일회용 코드를 Git 패널에서 표시하고 취소할 수 있다. 종료 후 코드는 화면에서 제거한다. 사용자 GitHub 승인 및 외부 연결 상태에 따라 인증이 완료되며, 로그인 중에도 파일 편집·저장·Git 조회를 사용할 수 있다.

검증: `scripts/check-studio-git.mjs`는 격리 서버의 실제 IDE Terminal에서 git init/파일 생성 후 자동 감지, diff, stage, commit, Monaco 저장과 모바일 overflow를 확인한다. GitHub 승인 대기/취소만 테스트 응답으로 재현한다. Python 회귀 테스트는 실제 Git과 로그인 실행 모의 중 파일 생성으로 잠금 분리를 확인한다. 실제 GitHub 계정의 승인 완료 또는 SSH 장비의 브라우저 E2E를 이 검증 결과로 간주하지 않는다.

### Browser 미리보기 창

하단 Browser 탭 또는 Ports Preview에서 화면을 띄우면 큰 모달로 표시한다. 데스크톱은 최대 1440px 너비, 모바일은 전체 화면을 사용한다. URL·뒤로/앞으로·새로고침·텍스트 입력·Console/Network 기능은 같은 서버 Chromium 세션에 연결된다. `창 닫기`나 URL 입력란에서 Escape는 모달만 닫으며, 다시 열면 URL과 세션을 유지한다. 서버 탭을 끝내려면 별도의 `세션 종료`를 누른다. 모달을 숨긴 동안 screenshot 폴링을 중지하고, 프로젝트를 바꾸면 모달과 로컬 관찰 상태를 초기화한다.
