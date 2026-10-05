# Codex와 서비스 로그의 로컬 SSH 검증

`LocalSshCodexIntegrationTest`는 SSHJ로 로컬 SSH 서버 `127.0.0.1:22`에 접속하며 호스트 지문과 임시 비밀번호를 검증한다. Codex App Server는 오프라인 프로토콜 fixture를 사용한다. 실제 OpenAI 로그인·모델 응답 성공을 증명하는 검사는 아니다.

로그는 실제 Docker 컨테이너가 기록한 500 오류·Traceback 뒤에 성공 로그 400줄을 추가해 생성한다. SSH 원격 계정의 실제 Python/Docker CLI → 테스트 전용 읽기 중계기 → Docker daemon으로 읽는다. 중계기는 정확히 지정된 테스트 컨테이너의 inspect/logs와 버전 협상만 허용하며 변경 메서드·다른 컨테이너·외부 공개 포트는 허용하지 않는다. SSH 테스트 컨테이너에는 Docker 소켓을 마운트하지 않는다.

PowerShell에서 저장소 루트를 기준으로 실행한다. 아래 이름이 이미 존재하면 기존 컨테이너를 삭제하지 말고 충돌을 먼저 해결한다. 운영 `.env`, 인증정보, 데이터 볼륨은 사용하지 않는다.

```powershell
$workspaceRoot = (Get-Location).Path
docker build -f tools/deployment/ssh-codex-test.Dockerfile -t my-dashboard-be:ssh-codex-test .
docker network create --internal dashboard-codex-check
docker run -d --name dashboard-codex-log-fixture --mount "type=bind,source=$workspaceRoot/tools/deployment/service-log-fixture.sh,target=/fixture.sh,readonly" alpine:3.21 sh /fixture.sh
$fixtureId = docker inspect --format '{{.Id}}' dashboard-codex-log-fixture
docker run -d --name dashboard-log-proxy --network dashboard-codex-check --read-only --cap-drop ALL --security-opt no-new-privileges --env "FIXTURE_CONTAINER_ID=$fixtureId" --mount type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock,readonly --entrypoint python3 my-dashboard-be:ssh-codex-test /opt/docker-log-test-proxy.py
docker create --name dashboard-local-ssh-final --network dashboard-codex-check --mount "type=bind,source=$workspaceRoot,target=/workspace" --tmpfs /workspace/target -w /workspace --entrypoint sh my-dashboard-be:ssh-codex-test tools/deployment/ssh-codex-test.sh
docker network connect bridge dashboard-local-ssh-final
docker start -a dashboard-local-ssh-final
```

일반 bridge는 Maven 의존성 다운로드에 사용한다. `target`은 tmpfs이므로 과거 테스트 DB가 결과에 영향을 주지 않는다. Python unittest와 Maven verify(전체 Java 테스트·Spotless)를 실행한다. SSH 테스트는 `RUN_LOCAL_SSH_TEST=true`일 때만 실행되고 일반 빌드에서는 건너뛴다. 결과 확인 후 이번 실행에서 만든 세 컨테이너와 네트워크만 제거한다.

UI 검증: `$env:NODE_PATH='tools/studio-editor/node_modules'` 설정 후 `node tools/studio-editor/test.cjs`, `node tools/launcher/assistant-test.cjs`. 이메일 표시·SSH 환경 전환·로그인 직후 갱신·로그아웃 시 제거를 포함하며 jsdom 검사이므로 브라우저 시각 검증과 구분한다.

장비 Codex UI는 `node tools/studio-editor/device-codex-test.cjs`로 장비/폴더 경로, 이메일, 장비 전환 시 대화 분리, 인증 URL·코드 표시, 취소와 IDE DOM ID 충돌 방지를 검증한다. Java `StudioServiceTest`는 다른 세션·장비·기존 작업 API의 조회/제어를 거부하는지 확인한다. 로컬 SSH 검사는 전용 CODEX_HOME과 장비 지침, 승인 처리가 실제 SSH 경로를 통과하는지 확인한다.

공식 CLI 설치까지 검사하려면 위 `docker create`에 `--env RUN_LOCAL_SSH_INSTALL=true`를 추가한다. 별도의 임시 SSH 계정 `codexinstall`에서 실제 `setup`과 `codex-account`를 실행한다. CLI와 code-mode-host 다운로드 및 인증 없는 App Server 초기화를 검사하며, 실제 사용자 로그인·과금 모델 응답·운영 배포를 수행하지 않는다. GitHub 릴리스 다운로드를 위한 인터넷 연결이 필요하다.

2026-10-05 검증: 공식 CLI 0.160.0 설치와 실제 App Server 계정 조회, 로컬 SSH 테스트 5개, Python 38개, Maven verify 171개(실패 0, 조건부 제외 3) 통과. 비활성 MCP에도 transport가 필요한 실제 CLI 계약을 반영했다. IDE/AI 비서/장비 Codex UI 및 반응형·테마 회귀 검사와 CSS 빌드 통과. 연결 가능한 Browser가 없어 시각 검증은 수행하지 않았다.
