# Studio IDE 검증 기록 — 2026-10-08

기존 Studio/Monaco, FileAdapter, Runtime Terminal, SshAdapter, Codex App Server, 서버 Chromium을 확장했다. Home/Launcher/전역 Workspace/탭 구조는 변경하지 않았다. 구현 계약은 [Studio 프로젝트 도구](studio-workbench.md)에 있다.

## 구현된 범위

- 하단 Terminal/Problems/Output/Tests/Ports/Browser/API, 접기·높이 조절, 프로젝트별 다중 PTY 유지.
- 프로젝트 명령 추천·등록·실행, 독립 프로세스 start/stop/restart/logs, PID/상태, TCP/HTTP 포트 탐지.
- 기존 Chromium 실제 이미지·기본 입력·URL/back/forward/reload, console/network/title/page text.
- 대상 장비 API 요청, Params/Headers/JSON/text/form/multipart, Basic/Bearer/API Key, 응답 정보, JSON pretty, cURL 변환, 암호화 history/environment.
- 테스트 명령 Run All/Rerun Failed, 파일/라인 진단, editor 이동, tool output 분리.
- local/SSH 프로젝트 Codex 및 최근 Terminal/Run/Browser/API 관찰 전달. local 프로젝트 로그인은 assistant와 분리.

## 실제 local Linux 통합 검증

`tools/studio-editor/acceptance.cjs`를 임의 자격증명을 쓰는 격리 대시보드·Chromium 환경에서 실행했다. 운영 프로젝트와 데이터는 사용하지 않았다.

| 단계 | 실제 증거 |
| --- | --- |
| 프로젝트 열기 | local root 아래 새 실제 Git repository 생성·조회 |
| Terminal dev server | 기존 `/sessions` + WebSocket으로 PTY 연결, pwd 확인, Python HTTP server 실행 |
| 포트 탐지 | 프로젝트 PID/cwd와 HTTP listening port 확인 |
| Browser Preview | Chromium JPEG 이미지, title/text, console error와 실패 HTTP 요청 확인 |
| API Client | 실제 loopback GET/POST, 응답 status/body, 암호화 환경변수 치환·마스킹·history 확인 |
| source 저장 | revision을 사용한 save API와 실제 파일 재읽기 |
| test/build | 실제 `python3 -m unittest -v` 실패 및 Output 수집 |
| Codex 분석·수정 | 인증된 기존 Codex App Server에 관찰 전달, workspace-write로 실제 app.py 수정 |
| 재실행·재검증 | 테스트 성공, 관리 프로세스 restart, Browser/API에서 broken→fixed 변경 확인 |
| Git 확인 | 실제 `git diff`에 app.py 변경 확인 |

처음 Codex 관찰 첨부는 확장자 없는 파일명 때문에 실패했으며 `.txt`로 수정했다. 다음 실행에서는 bwrap 누락 때문에 Codex가 수정하지 못했음을 보고했다. 이미지에 bubblewrap을 추가한 후 기본 Docker 권한과 workspace-write 상태로 **실제 파일 수정과 후속 검증이 통과**했다. 답변 완료 이벤트만 성공으로 간주하지 않고 파일·테스트·diff를 확인했다. 테스트용 로그인 복사본은 격리 컨테이너에만 두었다.

## 실제 SSH 검증

최종 이미지의 대시보드/Chromium과 별도 네트워크의 실제 SSH fixture 사이에서 프로젝트 열기, PTY cwd·서버 실행, HTTP 포트, Preview 이미지·오류 수집, API 요청·암호화 변수·history, 파일 저장, 실패 테스트, 프로세스 재시작, 테스트 성공, Browser/API 변화, Git diff까지 통과했다. SSH에서는 Codex 수정 대신 save API로 fixture를 수정했으며 이를 로그에 명시했다. 실제 Codex 수정 증거는 위 local 실행이다.

초기 터널의 응답 대기 문제는 SSHJ 표준 LocalPortForwarder로 교체해 수정했다. dev server 재시작 중 target 연결 거절로 listener가 끝나는 문제도 발견하여 listening socket을 유지하고 다음 연결을 다시 받도록 수정했다. 수정 후 전체 SSH 경로가 통과했다.

## 자동 검사

- 최종 공식 Dockerfile: Python 45개 통과. Java 189개 중 180개 통과, 실패/오류 0, 외부 연동 조건부 9개 제외. Maven verify 및 Spotless/패키징 통과. 최종 이미지 `my-dashboard-ide-check:local`의 기본 Docker 권한에서 bubblewrap 실행도 확인했다.
- Monaco 실제 bundle/model/worker shim DOM 검사, 기존 Studio 저장·충돌·Codex UI 회귀 통과.
- embedded Terminal의 다중 세션·패널/프로젝트 전환 socket 유지 DOM 검사 통과.
- cURL 변환/unsupported option 거절 및 인증값 마스킹 검사 통과.
- CredentialVault 파일 저장·재시작·프로젝트 격리 Java 검사 통과.
- Python actual HTTP method/params/auth/json/form/multipart, header injection 차단, 프로세스 restart/ports/logs 테스트 통과.

## 아직 완료로 표시하지 않는 부분

위 통합 실행은 실제 backend/PTY/Chromium/Codex 경로를 검증한다. **연결 가능한 앱 내 Browser가 없어 사용자가 Studio 화면에서 수행하는 12단계 UI acceptance는 완료하지 못했다.** browser discovery 결과가 빈 배열이었다. Monaco Ctrl+S 실제 키보드, 화면 resize/touch, 파일 worker 네트워크, Browser 화면 클릭과 한글 IME까지 검증한 것으로 해석하지 않는다.

Maven/Gradle/npm 테스트 추천은 설정 탐지이고 실제 통합 프로젝트는 Python이었다. 각 언어 프로젝트의 런타임/toolchain은 대상 장비에 필요하다. Rerun Failed는 실패한 명령 전체를 다시 실행한다. Browser는 이미지 기반 기본 상호작용이며 전체 DevTools/업로드·다운로드 UI는 없다. Codex의 Browser/API 컨텍스트는 관찰 스냅샷이며 새 실시간 브라우저 제어 MCP가 아니다. 네트워크가 끊긴 PTY 재연결은 새 셸이다.

사용자가 실제 Studio 화면 검증을 직접 진행하기로 했으므로 이번 작업은 빌드·코드 검증 기준으로 마무리한다. 실제 화면 acceptance 전체 통과를 의미하지 않으며 운영 배포는 수행하지 않았다.
