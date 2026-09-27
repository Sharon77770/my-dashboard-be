# 운영 환경 배포

프로젝트 루트에서 `.env.example`을 **최초 한 번만** `.env`로 복사하고 `chmod 600 .env` 후 편집한다. 기존 `.env`는 덮어쓰지 않는다. `.env`는 Git에 포함하지 않는다. `docker compose --env-file .env config --quiet`로 필수 값과 문법을 검증한다. `config`만 실행하면 비밀번호까지 출력되므로 공유하지 않는다.

## HTTPS 운영 예시

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
```

대시보드 로그인과 SMB 계정은 별도다. Samba 사용자 이름은 영문/숫자/점/밑줄/하이픈 32자 이내로 설정한다. `NAS_SMB_BIND_ADDRESS`는 LAN 또는 VPN 전용 인터페이스 IP여야 하며 공인 주소나 `0.0.0.0`을 사용하지 않는다. Docker published port는 UFW/firewalld 일반 규칙을 우회할 수 있으므로 실제 Docker firewall backend에서 LAN/VPN CIDR만 허용하도록 제한한다. 자격증명은 Git에 추가하지 않는다.

Compose의 호스트 포트는 `${DASHBOARD_BIND_ADDRESS:-127.0.0.1}:${DASHBOARD_PORT}:8080`이며 dashboard가 소유한다. 서버 IP로 직접 접속하려면 DASHBOARD_BIND_ADDRESS=0.0.0.0을 설정한다. 같은 서버에서 실행하는 HTTPS 역방향 프록시가 `127.0.0.1:8080`으로 전달하도록 구성한다. 프록시에는 WebSocket Upgrade 지원(터미널·원격 화면) 및 파일 업로드 크기/시간 제한이 필요하다. 다른 컨테이너에서 프록시를 실행하는 경우 그 컨테이너의 localhost는 이 서버가 아니므로 네트워크 연결을 별도로 구성한다. 도메인·인증서·프록시 설정은 이 Compose가 제공하지 않는다.

NAS 전환 시 기존 `dashboard-data` Docker 볼륨을 유지하고 실제 cloud/files 경로와 owner/group/mode를 확인한다. UID/GID 10001과 다른 기존 파일 권한을 조사 없이 변경하지 않는다. 전환 및 클라이언트 확인 절차는 [NAS 안내](nas.md)에 있다. `docker compose down -v`를 사용하지 않는다.

HTTP로 직접 접속할 때만 `SESSION_COOKIE_SECURE=false`를 사용한다. HTTP에서 true이면 로그인 쿠키가 전송되지 않아 로그인 유지가 안 된다. Tailscale HTTP 접속도 브라우저 기준으로 HTTP이므로 동일하다. 인터넷 공개 운영에는 HTTPS와 true를 사용한다. Tailscale은 웹 설정에서 로그인 시작 후 표시된 URL을 사용자 브라우저에서 직접 열어 인증한다. 서비스 시작 시 자동 인증을 하지 않으며 기존 TAILSCALE_AUTHKEY 값은 사용하지 않는다.

SQLite·드라이브·암호화 키는 dashboard-data 볼륨, Tailscale 상태는 tailscale-state에 저장된다. 정상 업데이트는 `docker compose --env-file .env up -d --build`를 사용한다. `down -v`는 영구 데이터를 삭제하므로 업데이트 명령으로 사용하지 않는다. `/dev/net/tun`과 NET_ADMIN이 허용된 Linux Docker 환경이 필요하다.


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
