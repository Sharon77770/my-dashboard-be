# Studio 프로젝트 도구

기존 Studio와 Monaco, SSH/SFTP, Runtime Terminal, Codex 및 서버 Chromium을 확장한다. 대시보드 Home, Launcher, 전역 탐색/탭 구조는 변경하지 않는다.

## 하단 패널

Terminal / Problems / Output / Tests / Ports / Browser / API 탭을 제공한다. 경계선을 드래그하거나 키보드 방향키로 높이를 조절한다. 접기 및 프로젝트 전환은 터미널 WebSocket을 닫지 않는다. 명시적인 터미널 닫기, 페이지 새로고침, 네트워크 연결 종료, 로그아웃 시 PTY는 종료된다. 네트워크 재연결은 새 셸이다.

Terminal은 기존 `/sessions`와 `/ws/runtime/{id}`를 사용한다. 선택한 프로젝트 root를 기존 FileAdapter 경계로 검증한 다음 local Linux PTY의 cwd 또는 SSH 셸의 작업 디렉터리로 지정한다. 다른 장비의 같은 경로는 별도 세션이다.

## Run / Tests / Problems / Output

Output에 이름, 실행 명령, Run/Test/Build 유형을 등록한다. 명령 추천은 Maven/Gradle/npm/Python 설정 파일을 참고하며 실제 프로젝트 스크립트와 맞는지 확인하여 실행한다. 명령은 해당 장비 계정의 `/bin/bash -lc`로 실행하며 임의 명령 실행은 OWNER에게만 허용된다.

관리 프로세스는 Studio HTTP 작업과 분리된 Linux 프로세스 그룹이다. 패널/요청 종료 후에도 유지되며 Stop/Restart로 제어한다. 기록은 대상 계정의 `~/.local/share/personal-workspace/runs/<project hash>`에 저장한다. 프로젝트당 실행 8개, 기록 64개, 로그 1 MiB 순환 제한이 있다. 마지막 로그 조회는 200 KB이다. 명령에 비밀번호를 직접 넣지 않는다. Run All은 등록한 Test 명령을 실행하고, 없으면 탐지된 테스트 명령을 실행한다. Rerun Failed는 실패한 **명령 전체**를 재실행한다.

Problems는 Monaco 진단과 출력에서 해석할 수 있는 파일/라인을 표시한다. 모든 언어의 구조화된 테스트 리포터를 구현한 것은 아니다. 해석하지 못하는 실패도 원문 Output에 남는다. 실행 출력은 대화형 PTY와 분리되어 있다.

## Ports / Browser

Linux `/proc/net/tcp{,6}`의 listening 포트를 탐지하고 읽을 수 있는 프로세스 cwd/FD를 대조한다. 최대 64개 포트에 짧은 HTTP HEAD를 보내 응답이 HTTP인 경우에만 Preview를 제공한다. 권한상 PID를 읽지 못하거나 IPv6-only/TLS 포트가 HTTP로 확인되지 않으면 TCP로 남는다. 복사 URL은 **대상 장비의 localhost 기준**으로 IDE Browser/API에 사용한다.

Browser는 `workspace.browser-host`/`workspace.browser-port`의 기존 Chromium CDP 서버에 프로젝트 전용 탭을 생성한다. JPEG 실제 화면을 주기적으로 갱신하고 클릭, 기본 키 입력, 스크롤, 뒤로/앞으로/새로고침을 전달한다. 별도 프레임 임베드가 아니므로 대상 사이트의 frame 제한과 무관하다. 콘솔 오류, 실패한 네트워크 요청, title 및 최대 24 KB page text를 수집한다. 파일 업로드/다운로드, 복합 IME 편집, 전체 DevTools UI는 제공하지 않는다.

Chromium은 대시보드와 같은 네트워크 namespace를 사용하는 기존 Compose 구성을 전제로 한다. SSH localhost 미리보기는 지문 검증된 기존 SshAdapter로 서버 loopback 터널을 연다. 서버를 외부로 공개하는 포트 포워딩은 생성하지 않는다. TLS 검증을 끄지 않으므로 터널 URL과 일치하지 않는 인증서는 실패할 수 있다. 로그인 세션별/프로젝트별 탭이며 전체 최대 8개, 30분 유휴 정리한다. Chromium 프로필의 쿠키는 기존 서버 브라우저와 공유된다.

SSH 터널은 프로젝트 서버 재시작 중 연결 거절이 발생해도 다음 연결을 계속 수신한다. 앱이 절대 origin/별도 HMR WebSocket 포트를 강제하는 경우 앱의 proxy/HMR 설정이 필요할 수 있다. 검증 프로젝트는 Python HTTP 서버이며 모든 프레임워크의 HMR까지 검증하지는 않았다.

## API

GET/POST/PUT/PATCH/DELETE/HEAD/OPTIONS, Params, Headers, JSON/text/form/multipart, Basic/Bearer/API Key를 지원한다. Python 표준 HTTP client가 프로젝트 장비에서 실행하므로 SSH의 localhost도 접근 가능하다. TLS 인증서를 검증하고 리다이렉트는 자동 추적하지 않는다. 헤더 주입 및 전송 계층 헤더 직접 지정은 거부한다. 요청/응답 1 MiB, 연결/읽기 15초 및 요청 종료 타이머 20초 제한이다. UI 파일 첨부는 500 KB 이하이다.

`{{NAME}}` 환경변수는 서버에서 치환한다. 환경변수 값은 저장 이후 API로 다시 반환하지 않는다. 프로젝트별 최신 요청/응답 30개와 환경변수는 `workspace.studio-state-path`(기본 `./data/studio-api`)의 암호화 파일에 저장하며 기존 CredentialVault의 AES-GCM key를 사용한다. 파일과 key를 함께 백업하되 key를 외부에 노출하지 않는다. SQLite schema 변경은 없다. 로그인 세션이 달라도 같은 OWNER 프로젝트 기록은 재사용한다. 기록의 다시 전송은 저장한 요청을 실제로 재실행한다.

알려진 인증값/환경변수 및 민감한 응답 헤더는 응답 표시에서 마스킹한다. 요청 본문에 임의로 포함한 별도 비밀값까지 자동 판별하지는 못하므로 주의한다. 비밀값은 plain localStorage에 저장하지 않는다. cURL 가져오기는 일반적인 URL, method, header, data, Basic, form 옵션을 파싱하며 셸을 실행하지 않는다. 지원하지 않는 옵션/로컬 파일 경로는 오류로 알린다. 내보내기는 인증값을 `{{SECRET}}`로 치환한다.

## Codex

현재 프로젝트 root를 사용하고 파일/Git/명령 실행은 기존 Codex App Server 기능을 유지한다. local 프로젝트 Codex는 대시보드 assistant와 분리된 trusted adapter 플래그를 사용하며 대시보드 MCP 자격증명을 주입하지 않는다. 기존 assistant의 읽기 전용 경계는 유지한다.

local 프로젝트의 CODEX_HOME은 `~/.local/share/personal-workspace/project-codex`이며 assistant 로그인과 분리한다. Linux sandbox용 `bubblewrap`은 dashboard 이미지에 포함한다. SSH 계정에서는 배포 환경에 맞게 설치해야 한다. 실제 기본 컨테이너 권한으로 workspace-write 실행을 검증했으며, 호스트별 user namespace 정책 차이는 별도 확인한다. [공식 sandbox 조건](https://learn.chatgpt.com/docs/sandboxing).

Studio에서 전송한 메시지에는 최근 관리 프로세스/port/tool output, Browser URL/title/text/console/network, API request 개요와 마스킹된 response를 최대 64 KB의 tool observation으로 첨부한다. 외부 페이지/API/출력은 신뢰할 수 없는 데이터다. 이는 관찰 시점의 스냅샷이며 실시간 브라우저 제어 MCP를 새로 제공하는 것은 아니다. 대상 계정에 실제 Codex 로그인이 필요하고 파일 수정은 사용자가 선택한 실행 권한을 따른다.

실제 검증 결과와 아직 검증하지 못한 항목은 `studio-ide-validation.md`에 별도로 기록한다.
