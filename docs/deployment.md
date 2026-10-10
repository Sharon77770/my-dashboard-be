# 운영 환경 배포

프로젝트 루트에서 `.env.example`을 **최초 한 번만** `.env`로 복사하고 `chmod 600 .env` 후 편집한다. 기존 `.env`는 덮어쓰지 않는다. `.env`는 Git에 포함하지 않는다. `docker compose --env-file .env config --quiet`로 필수 값과 문법을 검증한다. `config`만 실행하면 비밀번호까지 출력되므로 공유하지 않는다.

## HTTPS 운영 예시

클라우드 작업 공간의 Linux 도구·자원 설정은 아래 **클라우드 작업 공간 자원** 절을 참고한다.

```dotenv
DASHBOARD_AUTH_ID=your-login-id
DASHBOARD_AUTH_PASSWORD='여기에-직접-설정한-긴-비밀번호'
DASHBOARD_PORT=8080
SESSION_COOKIE_SECURE=true
SESSION_TIMEOUT=30m
UPLOAD_MAX_SIZE=1GB
TAILSCALE_HOSTNAME=personal-dashboard
TAILSCALE_ACCEPT_DNS=true
TAILSCALE_ACCEPT_ROUTES=false
CLOUD_ROOT=/app/data/cloud
NAS_USERNAME=nasuser
NAS_PASSWORD=replace-with-a-unique-password
NAS_SMB_HOST=nas.example.lan
NAS_SMB_BIND_ADDRESS=192.168.1.20
NAS_SMB_HOST_PORT=445
```

대시보드 로그인과 SMB 계정은 별도다. Samba 사용자 이름은 영문/숫자/점/밑줄/하이픈 32자 이내로 설정한다. `NAS_SMB_BIND_ADDRESS`는 LAN 또는 VPN 전용 인터페이스 IP여야 하며 공인 주소나 `0.0.0.0`을 사용하지 않는다. `NAS_SMB_HOST_PORT`는 기본 445이며 Windows Docker Desktop 로컬 테스트에서 호스트 445가 점유되어 있으면 1445 등 사용 가능한 포트로 변경할 수 있다. 운영 SMB 클라이언트가 기본 포트로 접근해야 하는 서버에서는 445를 사용한다. Docker published port는 UFW/firewalld 일반 규칙을 우회할 수 있으므로 실제 Docker firewall backend에서 LAN/VPN CIDR만 허용하도록 제한한다. 자격증명은 Git에 추가하지 않는다.

Compose의 호스트 포트는 `${DASHBOARD_BIND_ADDRESS:-127.0.0.1}:${DASHBOARD_PORT}:8080`이며 dashboard가 소유한다. 서버 IP로 직접 접속하려면 DASHBOARD_BIND_ADDRESS=0.0.0.0을 설정한다. 같은 서버에서 실행하는 HTTPS 역방향 프록시가 `127.0.0.1:8080`으로 전달하도록 구성한다. 프록시에는 WebSocket Upgrade 지원(터미널·원격 화면) 및 파일 업로드 크기/시간 제한이 필요하다. 다른 컨테이너에서 프록시를 실행하는 경우 그 컨테이너의 localhost는 이 서버가 아니므로 네트워크 연결을 별도로 구성한다. 도메인·인증서·프록시 설정은 이 Compose가 제공하지 않는다.

HTTPS 프록시가 다른 서버의 HTTP 대시보드로 전달할 때는 `Host`와 `X-Forwarded-Proto`를 함께 전달해야 한다. 애플리케이션은 신뢰 가능한 내부 프록시의 전달 헤더를 해석해 WebSocket 동일 출처 검사를 수행한다. Nginx의 대시보드 `location /` 안에 아래 설정이 필요하다. `proxy_pass` 주소는 실제 대시보드 내부 주소로 바꾼다. WebSocket이 연결 직후 403으로 끝나면 먼저 전달 헤더와 Upgrade 응답(101)을 확인한다.

```nginx
location / {
    proxy_pass http://DASHBOARD_INTERNAL_HOST:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 1h;
    proxy_send_timeout 1h;
}
```

NAS 전환 시 기존 `dashboard-data` Docker 볼륨을 유지하고 실제 cloud/files 경로와 owner/group/mode를 확인한다. UID/GID 10001과 다른 기존 파일 권한을 조사 없이 변경하지 않는다. 전환 및 클라이언트 확인 절차는 [NAS 안내](nas.md)에 있다. `docker compose down -v`를 사용하지 않는다.

HTTP로 직접 접속할 때만 `SESSION_COOKIE_SECURE=false`를 사용한다. HTTP에서 true이면 로그인 쿠키가 전송되지 않아 로그인 유지가 안 된다. Tailscale HTTP 접속도 브라우저 기준으로 HTTP이므로 동일하다. 인터넷 공개 운영에는 HTTPS와 true를 사용한다. Tailscale은 웹 설정에서 로그인 시작 후 표시된 URL을 사용자 브라우저에서 직접 열어 인증한다. 서비스 시작 시 자동 인증을 하지 않으며 기존 TAILSCALE_AUTHKEY 값은 사용하지 않는다.

SQLite·드라이브·암호화 키는 dashboard-data 볼륨, Tailscale 상태는 tailscale-state에 저장된다. 정상 업데이트는 `docker compose --env-file .env up -d --build`를 사용한다. `down -v`는 영구 데이터를 삭제하므로 업데이트 명령으로 사용하지 않는다. `/dev/net/tun`과 NET_ADMIN이 허용된 Linux Docker 환경이 필요하다.


## 클라우드 작업 공간 자원

대시보드 실행 이미지는 Ubuntu 22.04 Jammy 기반 `eclipse-temurin:21-jdk-jammy`다. `apt`/`apt-get`과 Bash, coreutils/find/grep/sed/awk, Git/SSH/tmux, JDK 21, C/C++ compiler/make/pkg-config, Python 3/pip/venv, curl/wget/jq/rg, ps/top/free/ss/ip/ping/DNS/netcat, zip/unzip, less/vi를 포함한다. Linux 작업 폴더에서 빌드·테스트·일반 셸 명령을 실행할 수 있다. Maven/Gradle은 프로젝트 wrapper를 사용할 수 있으며 Node.js 등 별도 런타임은 프로젝트 요구 버전에 맞춰 추가 설치한다.

기본 CPU·컨테이너 메모리 상한은 `0`(별도 제한 없음)이다. 이는 자원 예약이나 클라우드 서버 증설이 아니며 호스트 또는 Docker VM에 실제 배정된 용량 이내에서 다른 작업과 공유한다. `.env`로 상한을 늘리거나 지정할 수 있다. 다음은 충분한 용량을 가진 서버에서 사용할 설정 예시다.

```dotenv
WORKSPACE_CPUS=8
WORKSPACE_MEMORY_LIMIT=16g
WORKSPACE_SHM_SIZE=512m
BROWSER_SHM_SIZE=2gb
DASHBOARD_JAVA_TOOL_OPTIONS=-Xms128m -Xmx1g
WORKSPACE_APT_PACKAGES=tree
```

`WORKSPACE_CPUS`/`WORKSPACE_MEMORY_LIMIT`는 dashboard 컨테이너와 그 안의 터미널·빌드·프로젝트 프로세스가 함께 사용하는 상한이다. browser/guacd/Tailscale/Samba는 별도 컨테이너이므로 전체 호스트 용량을 별도로 고려한다. `WORKSPACE_SHM_SIZE` 기본값은 256m, browser의 `BROWSER_SHM_SIZE` 기본값은 1gb다. 공유 메모리는 실제 사용량만큼 메모리를 소비하며 컨테이너 메모리 상한과 독립된 추가 RAM이 아니다. 설정은 [Compose 서비스 자원 옵션](https://docs.docker.com/reference/compose-file/services/)을 따른다.

대시보드 JVM 힙 기본값은 `-Xms128m -Xmx1g`다. JVM native memory와 프로젝트 빌드·실행에 쓸 공간을 남겨야 하므로 컨테이너 전체 RAM을 JVM 힙에 배정하지 않는다. `DASHBOARD_JAVA_TOOL_OPTIONS`는 Compose에서 서버 JVM에 전달한다. 기존 local Studio/Terminal 어댑터는 작업 프로세스 환경을 선별하므로 이 서버 옵션을 프로젝트 Java 프로세스에 자동 상속하지 않는다.

`WORKSPACE_APT_PACKAGES`에는 신뢰하는 추가 Ubuntu 패키지 이름을 공백으로 구분해 넣고 이미지를 재빌드한다. apt 설치는 Dockerfile의 root 빌드 단계에서 수행하며, 웹앱·Studio 셸의 기본 실행 계정은 계속 UID/GID 10001이다. 따라서 Studio 셸에서 시스템 `apt install`을 바로 실행할 권한은 없다. 기본 배포에는 비밀번호 없는 sudo나 root 웹앱 실행을 추가하지 않는다. 아래 개발용 구성을 선택하면 sudo를 사용할 수 있다. 운영자가 일회성으로 컨테이너 내부에서 설치한 시스템 패키지는 재생성 시 사라지므로 지속적으로 필요한 도구는 이 빌드 설정에 포함한다. Python 프로젝트 의존성은 프로젝트별 `python3 -m venv .venv`를 사용할 수 있다.

프로젝트 파일과 홈·CLI 설정은 기존 `/app/data` 볼륨에 유지된다. 자원 옵션 변경은 컨테이너 재생성, apt 패키지 변경은 이미지 재빌드가 필요하다. 기존 세션이 종료될 수 있으므로 작업 저장 후 `docker compose --env-file .env config --quiet`와 `docker compose --env-file .env up -d --build`를 실행한다. `init: true`로 작업 자식 프로세스의 회수를 지원한다. Docker CLI는 포함되지만 호스트 Docker 소켓은 기존처럼 자동 마운트하지 않는다.

2026-10-08 격리 검증: 추가 apt 패키지 `tree`를 포함한 최종 이미지 빌드 성공. 이미지 빌드 중 Python 41개와 Maven verify(Java 178개 통과·9개 skip, 패키징·Spotless) 통과. UID 10001, cap-drop ALL, no-new-privileges 조건에서 Java/C 컴파일·실행과 Python venv 생성 성공. 컨테이너 내부에서 CPU 2개·메모리 2GiB·공유 메모리 256MiB 제한을 확인했다. 기본 설정 및 8 CPU/16g 설정의 Compose config 검사도 통과했다. 운영 서버의 자원 증설·재배포 및 실제 부하 검증은 수행하지 않았다.

## 카카오톡 기능 제거 후 업데이트

카카오톡·Wine 서비스와 설치 파일 다운로드를 제거했다. KAKAO_INSTALLER_SHA256 환경변수는 더 이상 필요하지 않다. 이전 `.env`의 해당 줄은 삭제해도 된다.

```sh
git pull --ff-only
docker compose --env-file .env config --quiet
docker compose --env-file .env up -d --build --remove-orphans
```

`--remove-orphans`는 현재 Compose에서 제거된 기존 Wine 컨테이너를 정리한다. 기존 wine-profile 볼륨은 자동 삭제하지 않는다. `down -v`는 사용하지 않는다. 저장된 카카오톡 실행 탭과 이력은 조회에서 제외되며 새 DESKTOP 세션 요청은 거부된다.

## Tailscale 없이 실행

대시보드는 Tailscale에 로그인하지 않아도 실행되며 Tailscale 컨테이너가 없어도 웹 로그인·드라이브·일반 SSH를 사용할 수 있다. 서버 브라우저와 원격 화면은 각각 browser와 guacd가 필요하다. Tailscale 장비 연결만 로그인과 TUN이 필요하다.

```sh
# 기본 기능만 기동: Tailscale 이미지 빌드·TUN 접근도 필요 없음
docker compose --env-file .env up -d --build dashboard guacd browser
# 필요할 때 Tailscale 관리 서비스를 추가하고 웹 설정에서 로그인
docker compose --env-file .env up -d --build tailscale
```

기존 버전에서 처음 업데이트할 때는 포트 소유자가 tailscale에서 dashboard로 바뀐다. 이전 컨테이너의 포트 점유와 네트워크를 정리하려고 다음처럼 한 번 중지 후 시작한다. **데이터 볼륨을 지우는 -v는 붙이지 않는다.**

```sh
docker compose --env-file .env down --remove-orphans
docker compose --env-file .env up -d --build dashboard guacd browser tailscale
```

서버 IP의 HTTP 주소로 바로 접속하려면 `.env`에 `DASHBOARD_BIND_ADDRESS=0.0.0.0`, `SESSION_COOKIE_SECURE=false`를 사용하고 호스트 방화벽에서 대시보드 포트를 허용한다. HTTPS 프록시 운영은 기존 `127.0.0.1`, `SESSION_COOKIE_SECURE=true`를 유지한다. 포트 바인딩과 쿠키 정책은 Tailscale 로그인과 별개다.

Tailscale의 NeedsLogin/unhealthy는 해당 장비 연결만 사용할 수 없다는 뜻이다. 기존 Tailscale 상태·대시보드 데이터 볼륨은 유지한다. dashboard가 재생성되면 공유 네트워크의 보조 서비스도 전체 up으로 재생성해야 한다. [Compose 의존성 기준](https://docs.docker.com/compose/how-tos/startup-order/).

회귀 검증: tools/deployment/test-network.ps1은 별도 QA 프로젝트와 임시 계정·포트로 Tailscale 미기동, NeedsLogin, 중지 후 웹 로그인·드라이브·Samba 기동·실제 DIRECT SSH 연결을 확인한다. QA 전용 볼륨만 정리하며 운영 데이터는 사용하지 않는다.

### Chromium CDP 복구

브라우저 프로필은 기존 volume에 보존한다. browser 시작 시 volume 내 flock을 확보하고 이전 컨테이너의 SingletonLock/SingletonCookie/SingletonSocket만 정리한다. 9223 `/json/version` 기동 확인이 실패하면 컨테이너를 종료하며 CDP healthcheck로 준비 상태를 표시한다. 운영 프로필 전체를 삭제하지 않는다. 서버 브라우저 재시작 후 Studio의 끊어진 탭은 URL 열기로 다시 생성한다. dashboard 재생성 시에는 같은 network namespace를 공유하는 browser/guacd/tailscale/samba도 Compose로 함께 재연결해야 한다.
## 로컬 IDE 개발 모드: Ubuntu + apt + Docker 빌드

기본 이미지도 Ubuntu 22.04 Jammy다. OS 변경보다 패키지 설치 권한과 Docker 데몬 연결이 필요하다. `compose.development.yaml`은 단일 소유자의 신뢰하는 개발 코드 실행을 위한 선택 구성이다. 기본 Compose의 비특권 설정은 유지한다.

- `WORKSPACE_DEVELOPMENT=true` 빌드에서는 UID 10001 계정에 비밀번호 없는 sudo를 제공한다. 웹앱은 계속 dashboard 계정으로 실행하지만 IDE 셸은 `sudo apt-get update`, `sudo apt-get install -y tree`를 실행할 수 있다. 개발 구성은 no-new-privileges와 cap-drop ALL을 해제하므로 컨테이너 내부 root 권한을 얻을 수 있다.
- Docker CLI 28.5.2와 Buildx/Compose 플러그인을 포함한다. 전용 `workspace-docker` DinD 28.5.2에 TLS 인증으로 연결하며 호스트 Docker 소켓은 마운트하지 않는다. Docker 데몬 API 포트는 호스트에 게시하지 않는다. 클라이언트 인증서는 UID 10001 전용 디렉터리, 개인 키 0600으로 공유한다.
- **DinD는 privileged 컨테이너다. 호스트 제어 권한으로 이어질 수 있어 보안 격리가 아니다.** 운영 서버 적용 전에 이 권한을 승인해야 한다. 신뢰하지 않는 프로젝트를 실행하는 공유 서비스용 설정이 아니다.
- 기존 dashboard-data 볼륨은 유지하고 Docker 이미지·캐시는 workspace-docker-data에 저장한다. apt로 실행 중 설치한 패키지는 컨테이너 재생성 시 사라지므로 지속할 패키지는 기존 WORKSPACE_APT_PACKAGES에 넣어 재빌드한다. `down -v`를 사용하지 않는다.

승인 후 적용 명령(Compose 2.24.4 이상):

```sh
docker compose --env-file .env -f compose.yaml -f compose.development.yaml config --quiet
docker compose --env-file .env -f compose.yaml -f compose.development.yaml up -d --build
docker compose --env-file .env -f compose.yaml -f compose.development.yaml ps workspace-docker
```

Docker 데몬이 healthy가 되면 IDE의 대시보드 서버 자체 터미널에서:

```sh
sudo apt-get update
sudo apt-get install -y tree
docker version
docker build -t project-check .
docker run --rm project-check
```

터미널·Studio 실행·로컬 장비 명령에는 DOCKER_HOST/DOCKER_TLS_VERIFY/DOCKER_CERT_PATH만 추가로 전달하며 다른 대시보드 비밀 환경 변수는 계속 제거한다. 원격 SSH 장비의 Docker 설정은 변경하지 않는다. Docker build의 빌드 컨텍스트는 CLI가 전송한다. `docker run -v`의 호스트 경로는 DinD 컨테이너 기준이므로 대시보드 경로를 그대로 bind mount할 수 없다. 테스트 파일은 이미지에 COPY하거나 docker cp, Docker named volume을 사용한다. 실행 포트는 workspace-docker:<port>에서 접근한다.

기본 모드로 복귀할 때 전용 데몬을 먼저 `docker compose --env-file .env -f compose.yaml -f compose.development.yaml stop workspace-docker`로 중지하고, 기본 compose.yaml만으로 대시보드 이미지를 다시 빌드·재생성한다. 전용 Docker 데이터 볼륨은 삭제하지 않는다.
2026-10-10 검증: 개발 이미지·전용 Docker 이미지 빌드, 기본/개발 Compose config 통과. 임시 비특권 개발 컨테이너에서 UID 10001의 sudo apt-get install tree 성공, Docker 28.5.2 / Buildx 0.29.1 / Compose 2.40.3 실행 확인. Java 186개 통과·9개 skip, Python 49개 통과. 기본 이미지의 UID 10001·sudo 없음 확인. 사용자 승인 후 격리된 privileged DinD를 기동해 개발 컨테이너 UID 10001의 TLS 연결, BusyBox 기반 COPY/RUN 이미지 빌드와 실행을 확인했다. 데몬 재시작 후에도 TLS 재연결과 기존 이미지 실행이 성공했다. 테스트 컨테이너·전용 볼륨·네트워크는 정리했으며 운영 배포와 실제 IDE 화면에서의 명령 실행은 이번 검증에 포함하지 않았다.

AI 비서 실행 폴더는 홈 볼륨의 `.local/share/personal-workspace/assistant-workspace`에 자동 생성된다. 별도 환경 변수는 필요하지 않다. 기존 홈/Codex 인증 볼륨을 유지하면 로그인과 대화 기록도 유지되며, IDE의 서버 파일 루트 선택과 비서 작업 잠금은 독립적이다. 이 변경을 실행 중인 서버에 적용하려면 이미지를 재빌드하고 컨테이너를 재생성한다.
