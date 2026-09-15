# Personal Workspace

Spring Boot 3.5.16 / Java 21 / Thymeleaf 기반 개인 대시보드입니다. 별도 프런트엔드 서버 없이 같은 애플리케이션이 화면, API, 인증, WebSocket을 제공합니다.

## 실행

1. `.env.example`을 `.env`로 복사하고 `DASHBOARD_AUTH_ID`, `DASHBOARD_AUTH_PASSWORD`를 설정합니다.
2. 로컬 HTTP에서는 `SESSION_COOKIE_SECURE=false`, HTTPS 배포에서는 `true`를 사용합니다.
3. `docker compose up -d --build`를 실행합니다.
4. `http://localhost:8080`에 로그인합니다.

Compose는 Tailscale, 대시보드, guacd, Chromium 브라우저를 함께 실행합니다. 첫 브라우저 이미지는 Chromium과 한글 글꼴을 다운로드하므로 시간이 걸립니다.
호스트 공개 포트는 `127.0.0.1:8080`이며 Tailscale 컨테이너가 전달합니다. tailnet 로그인 후 대시보드의 8080 포트도 tailnet 접근 정책에 따라 접근 가능합니다. 배포 리버스 프록시는 같은 도메인에서 HTTP와 `/ws/` WebSocket Upgrade를 전달해야 합니다.
운영 비밀번호는 공백 불가, BCrypt 제한에 따라 UTF-8 72바이트 이하입니다. `$`, `#` 등이 있으면 `.env`에서 작은따옴표로 감싸세요.
환경변수 변경은 `docker compose up -d --force-recreate`로 반영합니다.

## 구현 기능

- 홈: 앱·폴더·위젯·여러 페이지를 배치하는 Launcher, Dock, App Drawer. 계정별 브라우저에 배치를 저장합니다. 최근 작업과 클립보드는 독립 앱으로 유지합니다.
- 장비: 추가/수정/삭제, SSH/SFTP/RDP/VNC 프로필, 고정, CPU/RAM/DISK 계측, Docker/GPU 조회, Docker start/stop/restart, Wake-on-LAN.
- 파일: 서버 및 SFTP 루트 탐색, 스트리밍 업로드/다운로드, 새 폴더, 이름 변경, 파일/빈 폴더 삭제, 경로 즐겨찾기.
- 터미널: 서버 PTY 또는 SSH 셸, 실제 입력/출력, 크기 조절, 독립 세션, 재연결/종료.
- 원격: guacd RDP/VNC 화면, 마우스/키보드/터치패드, 전체 화면, 텍스트 클립보드 전달.
- 앱: HTTP(S) URL 등록/수정/삭제/고정. 별도 브라우저 설정 창에서 실행 위치 선택.
- 작업 공간: 실행 탭 열기/고정/닫기/복원, 서버 검색, Ctrl/Cmd+K, 최근 파일 위치, 모바일 앱 전환기.
- 설정: 다크/라이트, 화면 밀도, 터미널 글자 크기, 기본 클립보드 만료 시간. SQLite에 저장합니다.

## 브라우저 설정

`브라우저 설정`에서 다음 중 선택합니다. 기존 연결은 유지하고 다음 앱 실행에 적용합니다.

| 모드 | 동작 |
| --- | --- |
| 현재 브라우저 | 대시보드에 표시된 링크를 눌러 사용자 브라우저의 새 탭에서 엽니다. |
| 서버 Chromium | Docker Chromium에서 URL을 열고 화면·입력을 대시보드의 원격 탭으로 전달합니다. 기본값입니다. |
| 원격 브라우저 서버 | VNC로 등록한 장비의 Chromium 원격 디버깅 포트로 URL을 열고 해당 장비 화면을 연결합니다. |

서버 브라우저는 하나의 지속 Chromium 프로필을 공유합니다. 앱은 Chromium의 개별 탭으로 열리며 원격 화면은 같은 데스크톱을 공유합니다.
원격 모드는 선택 장비에 VNC 서버와 Chromium DevTools HTTP endpoint가 실행 중이어야 합니다. 디버깅 포트는 대시보드 서버에서만 접근하도록 운영합니다.
클라이언트에서 실행되는 것은 화면 렌더링과 입력 전달입니다. 파일, 셸, SSH/SFTP, 원격 접속, Wake 및 서버 브라우저의 네트워크 요청은 서버에서 실행됩니다.
현재 브라우저 모드만 사용자 선택에 따라 웹사이트를 클라이언트에서 엽니다.

## 장비 연결

장비 화면에서 실제 주소와 인증정보를 입력합니다. 예시 IP나 가짜 장비는 생성하지 않습니다.
**장비 → SSH로 장비 연결**에서 `ssh 사용자@호스트`(포트 지정: `ssh -p 2222 사용자@호스트`)와 비밀번호를 입력합니다. 연결 성공 후 호스트 키와 SFTP 홈 경로를 자동으로 저장합니다. SHA 지문을 찾아서 입력할 필요가 없습니다. 첫 접속에서 받은 호스트 키를 신뢰하며, 저장된 키가 달라지면 이후 연결을 차단합니다. 같은 호스트·포트·사용자는 기존 장비를 갱신하고 파일 루트·원격 화면 설정을 유지합니다. 직접 설정은 RDP/VNC 등 상세 설정에 사용합니다.
빈 비밀번호로 프로필을 수정하면 기존 값을 유지합니다. 저장된 비밀번호는 AES-256-GCM 암호문이며 API는 설정 여부만 반환합니다.
SFTP 경로는 장비에 존재하는 절대 경로로 설정하세요. UI 경로 `/`는 이 루트에 대응합니다. 로컬 기본 루트는 `/app/data/files`입니다.
원격 계측은 Linux의 top/free/df, GPU 조회는 nvidia-smi가 필요합니다. Docker 조회/제어는 대상 장비의 Docker CLI 및 권한이 필요합니다.
기본 대시보드 컨테이너는 호스트 Docker 소켓을 자동으로 마운트하지 않습니다. 호스트 Docker 관리에는 해당 호스트를 SSH 장비로 등록하세요.
RDP는 대상 서버 인증서가 guacd에 신뢰되어야 합니다. 인증서 검증을 자동으로 해제하지 않습니다.
Wake는 설정한 MAC/브로드캐스트 주소로 UDP 패킷을 보냅니다. Docker 네트워크/라우터의 전달 여부와 대상 BIOS/NIC 설정에 따라 기동 여부가 달라집니다. UI 성공은 전송 성공입니다.

## 저장과 수명

- `dashboard-data`: SQLite DB, 접속정보 암호화 키, 기본 서버 파일.
- `browser-profile`: Chromium 설정·웹사이트 로그인 프로필.
- DB와 `/app/data/credential.key`를 함께 백업해야 접속 비밀번호를 복원할 수 있습니다.
- 로그인과 실행 세션은 메모리에 있습니다. 로그아웃/만료/재시작 시 연결을 종료합니다.
- 저장된 탭은 위치만 복원합니다. 선택하면 새 실행 세션을 시작하며 이전 프로세스를 재생하지 않습니다.
- 최근 작업은 최대 100개이며 실행 명령이나 비밀번호를 저장하지 않습니다.
- 클립보드는 1~1440분 후 읽기에서 제외되고 조회 시 삭제됩니다. 일반 텍스트로 DB에 저장합니다.
- 파일은 덮어쓰지 않습니다. 이름 충돌은 오류이며 폴더 삭제는 빈 폴더만 지원합니다. 심볼릭 링크를 통한 루트 밖 접근을 차단합니다.
- `docker compose down`은 데이터를 유지합니다. `down -v`는 볼륨을 삭제합니다.

## 환경변수

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| DASHBOARD_AUTH_ID / DASHBOARD_AUTH_PASSWORD | 필수 | 단일 계정 |
| DASHBOARD_PORT | 8080 | Compose 호스트 포트 |
| SERVER_PORT | 8080 | JVM HTTP 포트 |
| SESSION_COOKIE_SECURE | true | HTTPS 쿠키, 로컬 HTTP만 false |
| SESSION_TIMEOUT | 30m | 로그인 비활성 만료 |
| DASHBOARD_DB_PATH | ./data/dashboard.db | SQLite 파일, Compose에서 /app/data/dashboard.db |
| WORKSPACE_ROOT | ./data/files | 서버 파일 루트, Compose에서 /app/data/files |
| CREDENTIAL_KEY_PATH | ./data/credential.key | 영속 32바이트 암호화 키 파일 |
| UPLOAD_MAX_SIZE | 1GB | 파일 및 요청 업로드 제한 |
| GUACD_HOST / GUACD_PORT | localhost / 4822 | 원격 게이트웨이, Compose host는 127.0.0.1 |
| BROWSER_HOST / BROWSER_DEBUG_PORT / BROWSER_VNC_PORT | localhost / 9223 / 5901 | 기본 서버 Chromium 연결, Compose host는 127.0.0.1 |
| WINE_HOST / WINE_VNC_PORT | localhost / 5902 | 서버 카카오톡 화면, Compose host는 127.0.0.1 |

JVM은 `.env`를 직접 읽지 않습니다. IDE/셸에서 환경변수를 주입해야 합니다. Docker Compose만 `.env`를 읽습니다.

## 개발과 검증

JDK 21 이상에서 다음을 실행합니다. 로컬 데이터 디렉토리 `data`를 먼저 만들고 계정 환경변수를 설정하세요.

```powershell
.\mvnw.cmd spring-boot:run
.\mvnw.cmd verify
.\mvnw.cmd spotless:check
```

Linux/macOS는 `sh ./mvnw`를 사용합니다. Java 줄바꿈은 `.gitattributes`와 Spotless에서 LF로 고정합니다.
기존 CRLF 소스는 `./mvnw.cmd spotless:apply`로 정규화합니다. `verify`는 테스트, JAR 패키징, 스타일 검사를 수행합니다.
파일/메타데이터 테스트는 `target/` 아래 테스트 DB·키·파일만 사용합니다. 서버 PTY는 Linux `script`와 bash가 필요하며 Docker 이미지에 포함됩니다.

명세: [아키텍처](docs/architecture/architecture.md), [기능 대조표](docs/features.md), [HTTP API](docs/api/specification.md), [DB](docs/database/schema.md).
외부 라이브러리: [Apache Guacamole](https://guacamole.apache.org/doc/gug/), [SSHJ](https://github.com/hierynomus/sshj), [xterm.js](https://github.com/xtermjs/xterm.js).
클라이언트 라이브러리는 서버에서 제공하며 라이선스는 `src/main/resources/static/vendor/`에 포함합니다.

## 장비 등록 없이 SSH 명령 사용
터미널 화면의 **터미널 열기**를 누르고 `ssh 사용자@호스트`를 입력한다. 다른 포트는 `ssh -p 2222 사용자@호스트`로 지정한다. 대상은 대시보드 컨테이너에서 접근 가능한 주소여야 한다.

OpenSSH가 최초 접속 시 호스트 확인을 요청하면 터미널에서 응답하고, 비밀번호 프롬프트에 대상 계정 비밀번호를 입력한다(입력 문자는 표시되지 않는다). 지문을 따로 복사해 장비 설정에 붙일 필요가 없다. 확인한 호스트는 `/app/data/home/.ssh/known_hosts`에 보존하며, 이후 호스트 키가 바뀌면 OpenSSH 기본 검증이 연결을 차단한다. `exit`로 원격 셸에서 나올 수 있다. `sftp`, `scp`, SSH 키와 `~/.ssh/config`도 OpenSSH 방식으로 사용할 수 있다.

명령은 서버의 실제 PTY에서 실행된다. 터미널 연결만으로 장비 프로필이 자동 등록되지는 않는다. 파일 탐색 UI와 장비 계측 연동이 필요하면 기존 장비 등록을 사용한다. Docker 이미지에 OpenSSH client가 포함되며, Docker 외부 실행은 실행 OS에 해당 CLI가 필요하다. 셸 홈은 데이터 볼륨에 저장하고 기본 파일 탐색 루트(`/app/data/files`) 밖에 둔다.

## 캘린더와 대학생 시간표
사이드바의 **캘린더**에서 월 이동/오늘 이동/날짜 선택 후 일정을 추가한다. 제목, 시작·종료, 종일, 장소, 메모, 색상을 저장한다. 여러 날에 걸친 일정도 표시하며 날짜 옆 목록에서 모든 일정을 확인하고 수정·삭제할 수 있다. 종일 일정의 화면 종료일은 포함한다.

**시간표**에서 학기 이름과 기간을 지정해 시간표를 만든 뒤 빈 시간 칸 또는 수업 추가를 누른다. 과목명·교수·강의실·학점·색상·메모와 월~일의 여러 수업 시간을 한 과목에 등록할 수 있다. 학점은 과목당 한 번만 합산한다. 같은 학기 안의 수업 시간 충돌은 서버에서 차단하며, 끝나는 시각에 다른 수업이 시작하는 것은 허용한다. 학기별 시간표는 독립적으로 저장된다. 학기 삭제 시 그 안의 수업도 삭제된다.

데이터는 SQLite/Docker 데이터 볼륨에 보존한다. 시간은 입력한 현지 시각을 그대로 저장하며 시간대 변환은 하지 않는다. 학기 수업은 주간 시간표로 관리하며 캘린더에 자동 복제하지 않는다. 모바일 시간표는 월~일 열을 유지하며 가로 스크롤한다. 반복 일정·알림·외부 캘린더 동기화·학교 강의 목록 연동은 포함하지 않는다.

## Docker 내 Tailscale
별도 설치 없이 `docker compose up -d --build`로 Tailscale도 함께 실행한다. Linux Docker 엔진의 `/dev/net/tun`과 NET_ADMIN/NET_RAW가 필요하며 이 권한은 Tailscale 컨테이너에만 부여한다. Docker Desktop은 Linux 컨테이너 모드를 사용한다.

최초 한 번, 다음 중 하나로 본인 tailnet에 로그인한다.

1. 권장: 로컬 `.env`의 `TAILSCALE_AUTHKEY`에 본인 tailnet 인증 키를 넣고 `docker compose up -d` 실행. 브라우저 로그인 없이 키로 인증하며, 인증 키는 저장소에 커밋하지 않는다.
2. 인증 키를 사용할 수 없는 경우 아래 명령이 안내하는 URL에서 로그인한다.

```bash
docker compose exec tailscale tailscale login
```

접속 상태와 IP는 다음으로 확인한다.

```bash
docker compose exec tailscale tailscale status
docker compose exec tailscale tailscale ip -4
```

`NeedsLogin`/로그인 전 unhealthy는 아직 tailnet에 가입하지 않았다는 뜻이며 로컬 대시보드는 계속 실행된다. 잘못된 키/네트워크/관리자 승인/키 만료는 tailnet 접속을 막을 수 있다. 인증 정보는 `tailscale-state` 볼륨에 보존하고 기존 장비 인증을 재사용한다. 시작 스크립트가 인증을 백그라운드에서 최대 30초 시도하며 실패해도 데몬과 공유 네트워크를 유지한다. 인증 키는 Java 앱이나 브라우저 환경에 전달하지 않는다.

로그인 후 **SSH로 장비 연결**에 `ssh 사용자@100.x.y.z` 또는 `ssh 사용자@장비이름`을 입력하고 대상 SSH 계정 비밀번호를 사용한다. 파일·상태 조회·RDP/VNC·서버 브라우저도 같은 Tailscale 경로를 사용한다. 대상도 같은 tailnet에 연결되어 있어야 하며 접근 정책과 대상 포트가 허용되어야 한다. `TAILSCALE_ACCEPT_DNS=true` 기본값은 MagicDNS를 사용하고, 이름 해석이 불가하면 Tailscale IP로 접속한다. 서브넷 라우터로 내보낸 사설 LAN에 접근하려면 승인된 경로가 있는 상태에서 `TAILSCALE_ACCEPT_ROUTES=true`를 사용한다.

이 구성은 일반 SSH를 Tailscale 네트워크로 운반한다. 대시보드의 장비 등록은 기존 비밀번호 인증을 사용하며, Tailscale SSH 전용 접근 정책/재인증이나 학교·회사 SSO를 자동 구성하지 않는다. 대시보드 터미널에서 직접 입력하는 SSH 명령은 대상 서버 인증 방식을 따른다. Wake 브로드캐스트는 Tailscale을 통해 자동 전달되지 않는다.

Tailscale CLI는 `tailscale` 컨테이너에서 실행한다. guacd(4822), VNC(5901/5902), Chromium 제어(9222/9223)는 공유 네트워크의 loopback에만 바인딩한다. 대시보드에는 기존 계정 인증이 유지된다. HTTPS와 SESSION_COOKIE_SECURE 설정은 기존 배포 정책을 따른다.

기존 3개 컨테이너 배포에서 최초 전환할 때는 8080 포트 소유자가 달라지므로 한 번 다음 순서로 실행한다. 데이터 볼륨은 유지된다.

```bash
docker compose stop dashboard guacd browser
docker compose up -d --build
```

Tailscale 컨테이너를 새로 만들면 공유 네트워크를 사용하는 dashboard/guacd/browser/wine도 함께 재생성한다. `docker compose up -d --force-recreate`를 전체 스택에 사용한다. `down -v`는 인증 상태와 대시보드 데이터를 삭제하므로 일반 재배포에는 사용하지 않는다.

| 환경변수 | 기본값 | 역할 |
| --- | --- | --- |
| TAILSCALE_AUTHKEY | 빈 값 | 최초 자동 로그인 인증 키, 비우면 대화형 로그인 |
| TAILSCALE_HOSTNAME | personal-dashboard | tailnet 장비 이름 |
| TAILSCALE_ACCEPT_DNS | true | tailnet DNS 설정 수락 |
| TAILSCALE_ACCEPT_ROUTES | false | 승인된 서브넷 경로 수락 |

공식 참고: [Docker Compose 연결](https://tailscale.com/docs/features/containers/docker/how-to/connect-docker-container), [Docker 환경변수](https://tailscale.com/docs/features/containers/docker/docker-params), [SSH over Tailscale](https://tailscale.com/docs/reference/ssh-over-tailscale).

## 서버 카카오톡 (Wine)

`docker compose up -d --build` 후 사이드바의 **카카오톡** 또는 앱 목록의 **카카오톡 → 열기**를 선택한다. Wine, 한글 글꼴/입력기, 공식 PC 설치 파일이 이미지에 포함되고 최초 실행 시 자동 설치한다. 첫 화면 준비에는 수 분이 걸릴 수 있다. 카카오톡은 항상 서버에서 실행되며 브라우저 위치 설정의 영향을 받지 않는다.

카카오톡 로그인은 앱 화면에서 직접 한다. 설치/실행은 CLI로 처리하지만 공식 CLI 로그인 수단은 확인되지 않았다. 한/영 전환은 `Ctrl+Space` 또는 한/영 키를 사용한다. 창을 닫았다면 바탕화면 오른쪽 클릭 → **카카오톡 실행**, 또는 다음 명령으로 다시 연다.

```bash
docker compose exec -d wine launch-kakao
```

자동 설치가 실패하면 바탕화면 메뉴의 **카카오톡 수동 설치**를 사용한다. 설치 진단은 Wine 컨테이너의 `/home/wine/install.log`, 초기화 진단은 `/home/wine/provision.log`에 있다. 계정/채팅이 저장될 수 있는 `wine-profile` 볼륨은 개인 데이터로 취급한다. 탭을 닫거나 대시보드에서 로그아웃해도 카카오톡 프로세스와 프로필은 유지되므로 카카오톡 계정 로그아웃은 앱에서 직접 한다.

Wine 데스크톱 VNC 5902는 공유 컨테이너 네트워크의 loopback에만 열고 대시보드 OWNER 인증을 통과한 Guacamole 터널로 접근한다. Linux amd64 실행 환경을 사용하며 ARM Docker에서는 amd64 에뮬레이션이 필요하다. Wine 호환성은 카카오톡 버전에 따라 달라질 수 있으며 통화/영상 기능은 검증하지 않는다.

카카오톡 팝업 호환성을 위해 서버 화면 합성과 32비트 Microsoft GDI+를 사용한다. 전용 Wine 프로필의 SysWOW64와 앱 디렉토리에 DLL을 설치하고 카카오톡 실행 시 native override를 적용한다. [Winetricks 공식 설치 절차](https://github.com/Winetricks/winetricks/blob/20250102/src/winetricks)의 원본/해시를 기준으로 Microsoft HTTPS 배포 파일을 검증하고 빌드 단계에서 해당 DLL만 추출한다. 최초 빌드에는 약 538MB의 추가 다운로드가 필요하며 원본 패키지 전체를 실행 이미지에 포함하지 않는다.

설치 파일은 [카카오 공식 배포](https://app-pc.kakaocdn.net/talk/win32/KakaoTalk_Setup.exe)에서 받고 Dockerfile의 SHA256으로 검증한다(현재 26.7.1.5263). upstream 파일이 교체되면 빌드를 중단하므로 공식 파일의 게시자 서명과 새 해시를 확인한 뒤 `KAKAO_INSTALLER_SHA256` build arg를 갱신한다. 기존 설치 프로필은 이미지 재빌드로 초기화하지 않는다.

외부 인증의 CLI 우선 원칙과 로그인 실패 검증 범위는 [인증 문서](docs/authentication.md)를 따른다. Git·Codex는 후속 연동 대상이다.

현재 Wine에서는 일부 팝업 뒤 배경 요소의 재그리기 누락이 남아 있다. 로그인 화면과 임의 계정 거부는 확인했지만 전체 채팅 UI의 호환성은 미검증이다. 필요한 경우 `docker compose restart wine`으로 카카오톡 화면을 다시 시작한다.

## SSH 코드 에디터

왼쪽 **코드 에디터**에서 **서버 자체** 또는 SSH 장비와 폴더를 선택하면 필요한 CLI를 준비하고 파일 편집·Git·Codex를 사용할 수 있습니다. [사용법과 설치 조건](docs/studio.md), [API 계약](docs/api/studio.md)을 참고하세요.

## Launcher 사용과 확장

[Launcher 문서](docs/launcher.md)에서 홈 편집, 폴더, 위젯, Desktop/Mobile 동작과 앱 등록 방법을 확인하세요. 공통 디자인 시스템과 저장 모델, UI 검증 방법도 함께 설명합니다.
