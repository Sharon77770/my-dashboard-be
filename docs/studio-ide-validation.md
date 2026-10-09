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

각 언어 프로젝트의 런타임/toolchain은 대상 장비에 필요하다. Rerun Failed는 실패한 명령 전체를 다시 실행한다. Browser는 이미지 기반 기본 상호작용이며 전체 DevTools/업로드·다운로드 UI는 없다. Codex의 Browser/API 컨텍스트는 관찰 스냅샷이며 새 실시간 브라우저 제어 MCP가 아니다.

2026-10-08에는 사용자 요청으로 빌드·코드 검증 기준에서 중단했으며 운영 배포는 수행하지 않았다.

## 2026-10-09 재개 검증

- 하단 패널을 중앙 `.studio-editor` 안으로 이동했다. 좌우 사이드바는 전체 높이를 유지하며 패널 높이 상한은 중앙 열의 높이를 기준으로 한다. DOM 회귀 및 CSS 생성 통과. 실제 화면의 기하·터치 검증은 아직 미완료다.
- 이전 Browser back/forward 뒤 API 502 경로를 threaded HTTP fixture로 다시 실행했다. API 요청·history replay·서버 restart·Browser/API 후속 확인까지 통과했다. 당시 단일 스레드 fixture의 동시 연결 처리가 원인으로 추정되며 같은 502는 재현되지 않았다.
- SSH Codex가 workspace-write에서 실제 app.py를 수정하고 테스트를 실행했다. 관리 프로세스 재시작 후 Browser/API와 실제 git diff가 모두 수정 결과를 확인했다.
- Studio PTY의 WebSocket 단절 후 기존 핸들로 재연결했고 dev server PID가 유지됨을 확인했다. 30분 유예·로그인 소유권·명시적 종료를 유지하며 최근 출력은 65,536자로 제한한다. 일반 Terminal 앱의 세션 수명은 유지했다.
- 실제 SSH 대상의 Maven/JUnit, Gradle/JUnit, npm/node:test, pytest 프로젝트 각각에서 명령 추천·실패 테스트·source save·재실행 성공·build 성공을 확인했다. `STUDIO_TEST_LANGUAGES=true`와 `language-fixtures.cjs`로 재현 가능하다.
- `my-dashboard-ide-check:resume` Docker build 통과: Python 45개 통과, Java 190개 중 181개 통과·9개 조건부 제외, 실패/오류 0. Monaco/Studio·Terminal·Codex chat·cURL DOM 회귀 통과.
- 브라우저 연결 목록은 재확인 시에도 비어 있었고 새 Browser MCP 도구가 현재 대화에 노출되지 않았다. 실제 Studio 화면 12단계 acceptance 전체 통과로 보고하지 않는다.

## 2026-10-09 실제 Playwright UI 재검증

위 Browser 연결 제약 이후, 사용자 지시에 따라 기존 세션과 `http://127.0.0.1:18187`을 유지하고 `scripts/check-studio.mjs`로 실제 Chromium을 실행했다. `STUDIO_TEST_FULL_UI=true` 실행에서 SSH 프로젝트 열기 → 내장 Terminal 서버 실행 → 포트 탐지 → Preview → API 요청 → Monaco 편집/Ctrl+S → 실패 테스트/Problems 파일 이동 → Codex 요청/실제 SSH 파일 수정 → 서버·테스트 재실행 → Browser/API 재검증 → Git diff 표시까지 통과했다. 한글 소스 붙여넣기와 정확한 저장 내용도 확인했다.

- 실제 화면에서 발견한 모바일 Editor 클릭 가로채기, 폴더 입력 후 열기 버튼 위치 변동, API 탭 뒤에 숨는 Git diff를 수정했다.
- 1600/1280/1024/390px 렌더링, 중앙 열 안의 하단 패널, 드래그 리사이즈·접기, 데스크톱 좌우 사이드바 전체 높이, 문서 가로 overflow 없음을 확인했다.
- `artifacts/studio.png`, 반응형 캡처, `artifacts/studio-report.json`을 생성했다. 통과 실행의 console/pageerror/failed request는 모두 0건이다.
- 최종 Docker 이미지 `my-dashboard-ide-check:final` 빌드와 Maven verify 성공. Python 45개, Java 190개 중 181개 통과·9개 조건부 제외, 실패/오류 0. Studio/Terminal/drawers DOM 회귀도 통과했다.

이는 격리된 SSH Python fixture의 실제 UI 검증이다. OS 한글 IME 조합, 물리 터치, 운영 배포 검증은 포함하지 않는다. Codex는 첨부된 Browser/API 관찰을 사용했으며 자율적인 실시간 Browser/API 도구 반복 제어를 검증한 것은 아니다. Maven/Gradle/npm의 실제 검증은 위 backend 통합 범위다.

## 2026-10-09 Chromium 복구와 실시간 도구 최종 검증

아래 결과가 앞선 날짜별 검증의 미완료 항목을 갱신한다.

- 운영 Chromium의 기존 프로필에 이전 컨테이너의 SingletonLock이 남아 오류 대화상태로 유지되면서 CDP 9222가 열리지 않았다. profile lease, stale singleton 정리, CDP 기동 확인·healthcheck를 추가했다. 기존 프로필과 로그인 데이터는 보존했고 동일 프로필 재시작 및 운영 재생성 후 CDP 200/healthy를 확인했다.
- 새로고침 후 같은 로그인 세션·프로젝트의 터미널을 조회해 복원한다. Playwright가 새로고침 전후 동일한 session ID, 셸 PID, 환경변수를 확인했다. 다른 탭의 연결은 빼앗지 않는다.
- Codex dynamic tools가 Browser/API의 최신 상태를 직접 조회하고 기존 관리 프로세스를 재시작한다. 실제 검증에서 Codex가 broken 응답 재현 → 파일 수정 → 단위 테스트 → process restart → Browser/API fixed 응답을 확인했다. 파일 수정 전·후의 관찰을 단순 첨부로만 검증한 결과가 아니다.
- 검증 중 발견한 Codex editor lock과 process restart의 충돌은 별도의 process lock으로 수정했다. 실제 파일 잠금을 점유한 상태의 재시작 회귀 테스트가 통과했다.
- Chromium 이동·reload 직후 컨텍스트 교체/화면 캡처 오류는 load 이벤트 대기, 제한된 context 재시도, CDP 전송 직렬화, Fetch 설정의 중복 변경 방지로 수정했다. 뒤로/앞으로/반복 reload 후 API 요청이 통과했다.
- `STUDIO_TEST_FULL_UI=true node scripts/check-studio.mjs`: SSH Python 프로젝트의 전체 12단계 UI 흐름, 실제 Codex 파일 수정과 studio_browser/studio_api/studio_process 호출, 한글 저장, IME 조합 이벤트를 거친 Preview 텍스트 전달, 테스트·재시작·Git diff가 통과했다. Maven/Gradle/npm/pytest 테스트·빌드도 실제 Output 화면에서 성공했다.
- 최종 `artifacts/studio-report.json`: console error 0, pageerror 0, failed request 0. 1600/1280/1024/390px에서 패널 중앙 열 배치·좌우 사이드바 높이·터미널 우측 제어·resize·접기·가로 overflow 검사를 통과했다. `artifacts/studio-codex-proof.json`은 실제 동적 도구 호출과 Codex 결과 증거다. fixture가 의도적으로 발생시키는 원격 페이지 console marker/404는 대시보드 오류와 구분한다.
- 최종 Docker/Maven verify: Python 47개 통과, Java 191개 중 182개 통과·9개 외부 조건부 제외, 실패/오류 0. Monaco·Terminal·cURL·Codex chat/settings 회귀도 통과했다.
- 운영 Compose dashboard/browser 및 같은 네트워크를 사용하는 서비스를 새 이미지로 반영했다. 운영 컨테이너 내부에서 실제 OWNER 로그인·CSRF, 임시 local 프로젝트의 run/ports, Chromium Preview·반복 reload, 실제 API 200/body, Terminal 생성/종료를 확인하고 임시 파일·프로세스를 정리했다. 운영 환경변수와 데이터 볼륨은 유지했다. 전체 Codex 수정 시나리오는 격리 SSH 프로젝트의 검증이며 운영 사용자 프로젝트를 수정한 것은 아니다.

남는 경계: OS 물리 키보드/터치 자체는 수동 검사 대상이다. 한글은 전용 입력란에서 조합해 원격 필드로 전송하며 이미지 안에서 직접 IME 조합하는 방식은 아니다. 이전 Codex 대화에는 동적 도구가 없어 Studio에서 새 대화를 시작해야 한다. Rerun Failed는 명령 전체 단위이며, apt는 이미지 빌드 단계에서 설치한다. 기존 Samba 컨테이너의 재시작 상태는 이번 Studio 수정 전부터 있었으며 IDE 검증과 별개다.