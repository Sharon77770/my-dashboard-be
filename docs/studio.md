# 서버 코드 워크스페이스

왼쪽 **코드 에디터**에서 **서버 자체** 또는 등록한 Linux SSH 장비와 작업 폴더를 선택하고 **폴더 열기 · 도구 준비**를 누른다. 서버 자체는 장비 등록이나 SSH 데몬 없이 사용할 수 있다. 원격 장비는 **장비 → SSH로 장비 연결**에서 SSH 명령과 비밀번호로 등록한다. 호스트 키는 기존 자동 등록/검증을 사용한다.

## 지원 동작

- 탐색기: 폴더 이동, UTF-8 파일 생성/열기/저장, 폴더 생성, 이름 변경, 파일/빈 폴더 삭제.
- 편집기: 여러 파일 탭, 변경 표시, Ctrl+S 저장, 구문 강조(JavaScript/TypeScript/JSX, Python, HTML, CSS, Java, JSON, Markdown), 줄 번호, 찾기/바꾸기, 실행 취소. CodeMirror 정적 번들은 같은 서버에서 제공한다.
- Git: 초기화, HTTPS/SSH 저장소 복제, origin 주소 설정, 변경/스테이징 상태, diff, stage/unstage, 저장소별 작성자, 커밋, 브랜치 생성/전환, fetch, pull(--ff-only), push, 최근 커밋. 새 브랜치의 첫 push는 origin/HEAD에 upstream을 설정한다. 저장소 최상위 폴더를 열어 사용한다. 복제 후 입력된 새 작업 폴더를 연다.
- GitHub 로그인: 원격 `gh auth login --hostname github.com --git-protocol https --web`, 승인 후 `gh auth setup-git`. 기존 원격 Git credential helper/SSH 인증도 사용한다. 다른 Git 호스팅은 SSH 터미널에서 해당 CLI로 인증한다.
- Codex: 원격 로그인 상태, 기기 코드 로그인/로그아웃, 프롬프트·선택 모델, 읽기 전용 또는 작업 폴더 수정 권한으로 실행. JSONL에서 응답·명령 실행·파일 변경을 화면에 표시한다. 결과 확인 후 파일을 다시 읽고 Git diff로 검토한다.
- 실행 중지: 진행 중인 원격 작업을 취소한다. 이미 저장된 파일이나 완료된 커밋은 되돌리지 않는다. 로그아웃/세션 만료 시에도 작업을 취소한다.

## 자동 설치와 실행 환경

Git/Python이 없는 서버는 `apt-get`, `dnf`, `apk`로 `git python3 ca-certificates`를 설치한다. 패키지 설치에는 root 또는 `sudo -n` 권한이 필요하다. 권한이 없으면 오류에 필요한 패키지를 안내하고 중단하며 sudo 비밀번호를 웹에서 수집하지 않는다. 패키지가 이미 있으면 관리자 권한이 필요 없다.

Codex 0.154.0 및 GitHub CLI 2.100.0은 Linux x86_64/aarch64의 공식 릴리스 파일을 다운로드하고 고정 SHA256을 검사하여 `~/.local/bin`에 설치한다. 해당 경로에 이미 있는 바이너리는 유지한다. 버전 갱신 시 `src/main/resources/studio/remote.py`의 버전·해시를 함께 변경한다. 서버가 GitHub 릴리스와 필요한 인증/API 주소에 접근할 수 있어야 한다. ARM 설치 해시는 제공하지만 실제 실행 검증은 x86_64에서 수행한다.

서버 자체 모드는 대시보드가 실행되는 Linux 환경에서 같은 helper/CLI를 직접 실행한다. Docker 배포 시 실행 위치는 dashboard 컨테이너이며 기본 작업 루트는 `/app/data/files`다. Git·Python·CA 인증서는 이미지에 포함하고 Codex·GitHub CLI는 첫 도구 준비 때 `/app/data/home/.local/bin`에 설치한다. 인증과 CLI 설정도 `/app/data/home`에 보관하므로 기존 `/app/data` 볼륨으로 컨테이너 재생성 후 유지된다. 로컬 CLI에는 대시보드 로그인 비밀번호 등 서버 환경변수를 상속하지 않는다. PATH/HOME/LANG/LC_ALL/TMPDIR만 전달한다.

원격 모드는 계속 대상 SSH 계정에서 실행한다. 프로젝트 파일과 `.git`, CLI 인증/설정은 선택한 실행 환경에 남는다. 브라우저에는 현재 편집 버퍼만 있고 마지막 장비/폴더 선택만 localStorage에 기억한다. 대시보드 DB 스키마는 바뀌지 않는다. Docker 호스트의 다른 폴더를 편집하려면 해당 폴더를 작업 루트 아래로 명시적으로 마운트한다. Windows에서 JAR를 직접 실행하는 로컬 IDE는 지원하지 않으며 Docker의 Linux 환경을 사용한다.

Codex는 `codex app-server`를 stdio JSON-RPC로 실행한다. 모델 목록과 저장된 세션을 조회하고 같은 thread를 resume하여 후속 turn을 보낸다. 읽기 전용/작업 폴더 수정 권한 및 on-request 승인을 사용하며 샌드박스 우회 옵션은 제공하지 않는다. 상세 기능과 제한은 [Codex 패널](codex.md)을 참조한다.

## 경계와 실패 처리

파일은 등록 장비의 루트 아래 선택한 작업 폴더로 제한한다. `..`, 심볼릭 링크, `.git` 직접 편집을 차단한다. 1 MiB 이하 UTF-8 일반 파일과 폴더당 2,000개 항목을 지원한다. 저장에는 읽을 때 받은 SHA256 revision이 필요하며 변경된 파일은 409 충돌로 거절한다. 같은 폴더의 대시보드 작업은 원격 `flock`으로 직렬화한다. 외부 프로그램이 저장 직전/직후에 파일을 바꾸는 경쟁까지 OS 전체에서 잠그지는 않는다.

로컬/SSH 작업은 세션 소유권을 확인하고 최대 4개 동시 실행, 15분 제한, 최근 32개 결과를 메모리에 유지한다. 결과는 30분 또는 서버 재시작/로그아웃으로 사라진다. 이벤트는 최근 150개/약 200,000자, CLI 출력은 4 MiB로 제한한다. 원격 helper는 SSH stdin EOF와 실행 기한에 자식 프로세스 그룹을 종료한다. Python 설치 전 패키지 관리자 단계는 SSH를 끊어도 이미 시작한 시스템 패키지 작업이 마무리될 수 있으므로 설치 직후 상태를 다시 확인한다.

일반 Git UI에는 강제 push/reset/충돌 자동 덮어쓰기가 없다. Git hook/원격 CLI는 대상 SSH 계정의 권한으로 실행되므로 자신이 관리하는 프로젝트를 연다. Git 명령은 고정 인자 배열과 literal pathspec을 사용하며 인증 프롬프트로 HTTP 요청이 멈추지 않도록 비대화형으로 실행한다. 토큰/비밀번호를 포함한 복제 URL은 거절한다. CLI 인증 토큰은 수집하지 않으며 의도적으로 요청한 일회용 코드와 인증 주소만 해당 로그인 세션 UI에 표시한다.

## 확인 명령

```sh
mvn -B verify
python3 -m unittest discover -s src/test/python -v
```

Python 검사는 Git/Python이 설치된 Linux에서 수행한다. 에디터 번들 재생성 방법은 `tools/studio-editor/README.md`에 있다. HTTP/SSH 실제 통합 검증은 실제 계정 대신 격리된 SSH 서버와 임의 자격증명을 사용한다. 정상 계정의 Codex 코드 생성, 유료 모델 응답, 외부 저장소 push는 로그인 실패 검증에 포함하지 않는다.

## 공식 계약

- [Codex App Server](https://learn.chatgpt.com/docs/app-server)
- [Codex 기기 코드 인증](https://learn.chatgpt.com/docs/auth)
- [GitHub CLI 로그인](https://cli.github.com/manual/gh_auth_login)
- [Codex 0.154.0 릴리스](https://github.com/openai/codex/releases/tag/rust-v0.154.0)

## 검증 결과 (2026-09-14)

Docker Maven 검증: Java 테스트 43개 통과. Linux 원격 helper 테스트: 6개 통과(파일 revision 충돌, 경로/링크 차단, UTF-8/크기 제한, Git stage/commit/branch/rename, 로컬 bare 저장소 push/fetch/pull). UI DOM 검사: 연결·파일 탭·편집 상태·저장·취소·미저장 Codex 차단 통과. 실제 localhost SSH fixture: 도구 설치, 파일/Git 동작, GitHub/Codex 인증 주소·코드 표시 및 프로세스 취소 확인. 임의 인증 키는 양쪽 HTTP401 거부를 확인했다.

자동 브라우저가 제공되지 않아 실제 브라우저 화면 캡처/시각 검증은 수행하지 못했다. 실제 계정의 Codex 코드 생성과 외부 저장소 쓰기는 수행하지 않았다.

서버 자체 추가 검증: SSH 데몬이나 추가 장비가 없는 Docker 컨테이너에서 local 작업 API로 CLI 설치, 파일 생성/저장/revision 충돌, 루트 탈출 차단, Git 커밋/브랜치, Codex/GitHub 미인증 상태 조회를 확인했다. 로컬 프로세스 취소와 취소 후 연결 경쟁도 Linux Java 테스트에 포함한다.

Codex 패널 확장 검증: Docker Java 44개·Python 12개 테스트 통과. 별도 무인증 CLI 컨테이너에서 OWNER HTTP API를 통한 모델 6개·draft·세션·스킬·MCP 조회 및 24개 웹 자산을 확인했다. [기능과 검증 범위](codex.md).
