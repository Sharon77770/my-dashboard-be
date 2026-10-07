# Workspace Shell 롤백 기록

2026-10-08 사용자의 범위 변경에 따라 Personal Workspace OS 확장을 중단하고 기존 Personal Workspace로 복원했다. 독립 IDE 앱은 아래 baseline 검증이 끝나기 전까지 구현하지 않는다.

## 복원 기준과 조사

- 기준 revision: `a17e2b0054fb59fc6891498fb1f0f561f7336052` (`Fix login assets, Codex controls, and calendar ranges`), branch `main`.
- status, unstaged/staged diff, log, reflog를 확인했다. 이전 Goal에서 생성한 commit과 staged 변경은 없었다.
- 원래 Goal 세션 `01a10d0c-4732-79b1-959f-b442f4b7e416`의 첫 수정 전 status 명령과 결과(기록 22/24행)를 확인했다. 당시 working tree에는 변경이 없었다.
- 수정된 추적 파일 86개와 신규 파일 317개를 복원 대상으로 확정했다. 파일별 SHA-256, 원본 파일 ZIP, binary diff, 이력과 시작 상태 증거를 로컬 `.tools/workspace-rollback-20261008-043123/`에 보관하고 ZIP 내용과 해시를 검증했다. 백업은 Git에 포함하지 않는다.
- 명시한 파일만 `git restore --source=<baseline> --worktree`로 복원하고, 해시를 다시 확인한 신규 파일만 개별 제거했다. `reset --hard`와 포괄적인 `git clean`은 사용하지 않았다.
- 이 기록 추가 전 `git diff --exit-code <baseline>`은 0이며 미추적 소스 파일도 없었다. 이전 Goal이 미커밋 상태였으므로 롤백 commit은 기존 commit의 역패치가 아니라 baseline 복원 사실을 남기는 문서 commit이다.

## 제거 범위와 보존 범위

| 제거한 이전 Goal 변경 | 복원한 기존 기능 |
| --- | --- |
| `static/js/shell/`의 AppInstance, session model/persistence, tab/split/navigation, workspace 생성·이름·고정·검색·복구 | 기존 `workspace.js`, Home, Launcher, 각 앱의 단일 화면 진입 |
| Shell 전용 CSS, Home session host 및 스크립트 주입, 각 앱의 instance 변환 | Device, Files, Terminal, Remote, GitHub, Services, Database Studio, Telemetry, Notes, Calendar, Military, Cloud Drive, AI Assistant |
| `development` 서버 모듈, development runtime/task/preview/LSP/Codex helper, V11–V20 migration 및 초기화 연결 | 기존 Studio/SSH Editor, Studio Codex, Device Codex, 기존 API/service 및 V3–V10 migration |
| Goal 전용 UI/Java/Python 테스트, Docker fixture, Monaco development bundle/build 경로와 추가 문서 | 기존 회귀 테스트, CodeMirror Studio bundle, 기존 문서 및 설정 계약 |

`.env`, 사용자 데이터, 원격 프로젝트 및 운영 DB는 변경하지 않았다. 기존 `workspace_memories`는 이전부터 있던 AI 비서 기능이므로 V9와 함께 유지했다. 운영 DB의 추가 테이블이나 브라우저 저장소를 임의 삭제하지 않았다. 이전 Goal에 대한 백업과 테스트 산출물은 제품 소스가 아닌 로컬 `.tools/`에 남아 있다.

## 복원 후 검증

- Maven `clean verify`: 187개 검사, 실패 0, 오류 0, 조건부 제외 14. JAR 패키징과 Spotless 통과. 이전 Goal의 클래스·리소스가 남지 않도록 clean 후 검증했다. `.tools/rollback-baseline-java.log`.
- 기존 Launcher·앱·Studio/Device Codex·CodeMirror·Notes UI 스크립트 28개 모두 통과. 9개 폭의 CSS/DOM 검사 포함. `.tools/rollback-baseline-ui.log`.
- Linux 격리 환경에서 기존 Python helper 검사 41개 모두 통과. 소스 읽기 전용 마운트와 네트워크 차단 사용. `.tools/rollback-baseline-python.log`.
- 실제 패키징 JAR를 loopback 임시 포트와 별도 임시 DB로 실행했다. health, 실제 로그인/CSRF, 인증 후 HTML의 기존 화면/runtime root 16개, CSS/JS 자원 39개의 실제 HTTP 응답과 잘못된 HTML redirect 부재를 확인했다. Shell session host도 없다. 프로세스는 검증 후 종료했다. `.tools/rollback-baseline-http.json`.

아직 완료하지 않은 검증: 실제 브라우저에서 주요 화면을 열고 조작하는 smoke test. 연결 가능한 브라우저 목록이 비어 있어 실행하지 못했으며, 위 DOM/HTTP 검사를 시각·클릭 검증으로 간주하지 않는다. 조건부 제외된 Java 검사도 전체 통과로 집계하지 않는다.

## 다음 단계의 진입 조건

브라우저 smoke test를 포함한 baseline 검증을 마친 뒤에만 독립 `IDE` 앱의 P0(Project Open → File Explorer → Monaco → Save)를 시작한다. IDE 안의 파일/터미널 탭과 패널만 관리하며 Dashboard 전체 Workspace Shell, AppInstance, split pane 또는 멀티탭을 재도입하지 않는다. 첫 실제 편집·저장·Terminal diff 시나리오와 최종 Codex/Run/Preview/Git push 시나리오가 성공하기 전에는 IDE 완료로 판단하지 않는다.
