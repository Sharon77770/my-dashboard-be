# SMB3 네트워크 저장소

NAS는 별도 Samba 컨테이너가 SMB3 파일 공유를 제공합니다. 대시보드 파일 브라우저는 기존대로 동작하고 NAS 클라이언트는 SMB로 같은 데이터를 직접 읽고 씁니다. SMB1, guest access, NetBIOS, 프린터 공유는 비활성화되어 있습니다. 최소 프로토콜은 SMB 3.0이며 최대 SMB 3.1.1입니다.

## 기존 데이터와 경로

Compose의 영구 볼륨은 기존 `dashboard-data`이며 SQLite, 키, 클라우드 파일을 포함합니다. 클라우드 드라이브는 `CLOUD_ROOT=/app/data/cloud`를 사용하므로 실제 NAS 저장 디렉터리는 이 볼륨의 `/app/data/cloud/files`입니다. SMB 컨테이너는 볼륨의 `cloud/files` 하위 경로만 `/nas/files`로 마운트합니다. 데이터 복사·초기화·볼륨 교체는 하지 않습니다. 초기화 컨테이너는 디렉터리가 없을 때만 UID/GID 10001 소유로 만듭니다. 기존 디렉터리와 파일의 소유권·mode는 수정하지 않습니다. 직접 JVM을 실행한 개발/비-Compose 설치에서는 기본 경로가 `./data/cloud/files`이므로 해당 데이터를 기존 Compose 볼륨에 자동 병합하지 않습니다. 그 형태로 운영 중이었다면 배포 전에 실제 저장 위치를 확인하고, 같은 host directory를 기존 저장소로 유지하도록 별도 bind mount 설정을 하십시오.

기존 Spring 대시보드 컨테이너는 UID/GID 10001로 실행됩니다. Samba는 별도 Linux/Samba 계정을 같은 UID/GID 10001에 매핑하므로 새 파일은 대시보드와 공유됩니다. 파일 및 디렉터리는 신규 생성 시 각각 0600/0700으로 제한합니다. 기존 파일 소유권은 운영 호스트에서 확인하십시오. 다른 UID/GID로 된 파일은 그 사용자에게 읽기·쓰기 권한이 있어야 합니다. 전체 파일에 재귀 chmod/chown을 실행하지 마십시오.

## 최초 설정과 안전한 전환

운영 서버에서 Compose 프로젝트 경로와 `.env`가 기존 배포와 동일한지 확인해 기존 `dashboard-data` 볼륨을 재사용하십시오. `docker compose config`에서 `CLOUD_ROOT=/app/data/cloud`와 `dashboard-data` project volume이 기존 배포 값과 같은지 확인하십시오. 경로가 다르면 현재 Samba subpath와 일치하지 않으므로 Samba를 시작하지 말고 실제 기존 persistent path에 맞게 구성을 조정하십시오. `docker volume inspect <기존-volume>`과 컨테이너의 mount 정보를 확인하고, 서버에서 데이터 경로와 파일 owner/group/mode를 확인하십시오. 기존 호스트 Samba가 TCP 445를 사용 중인지 `ss -ltnp`로 확인하고 충돌을 정리하십시오. Samba를 켜기 전 백업을 확보하고 다른 쓰기 작업을 잠시 중지하십시오. 이 체크아웃에서 Docker daemon에 연결할 수 없었으므로 실제 운영 볼륨 owner와 내용 검증은 아직 되지 않았습니다.

`.env`에 다음을 설정하고 권한을 제한합니다 (`chmod 600 .env`). 기존 파일을 덮어쓰지 마십시오.

```dotenv
NAS_USERNAME=nasuser
NAS_PASSWORD=replace-with-a-unique-password
NAS_SMB_HOST=nas.example.lan
NAS_SMB_BIND_ADDRESS=192.168.1.20
```

LAN 클라이언트도 연결할 경우 `NAS_SMB_BIND_ADDRESS`는 클라이언트가 들어오는 전용 LAN 인터페이스 IP여야 합니다. VPN-only 구성에서는 이 호스트 게시 주소를 `127.0.0.1`로 제한해도 되며 Samba는 dashboard와 Tailscale network namespace를 공유하므로 tailnet 클라이언트는 Tailscale 주소로 직접 연결할 수 있습니다. 공인 IP나 전체 인터페이스 바인딩 `0.0.0.0`은 사용하지 마십시오. `NAS_SMB_HOST`는 클라이언트가 사용할 LAN/VPN DNS 이름 또는 주소입니다. 하나의 호스트 이름이 두 네트워크에서 모두 해석되도록 설정하거나 `.env`의 주소를 해당 연결 환경에 맞추십시오. Samba 계정은 대시보드 로그인과 별도입니다. 비밀번호는 저장소에 넣지 말고 `.env` 또는 운영환경 secret 주입을 사용합니다. `.env`에서 `$` 등 Compose 특수 문자를 쓰는 비밀번호는 작은따옴표로 감싸십시오.

Linux 서버에서 실제 저장 디렉터리의 기존 권한을 확인합니다. 기본 배포에서는 파일을 소유한 앱 UID/GID가 10001입니다.

```sh
docker compose exec dashboard sh -lc 'id; stat -c "%u:%g %a %n" /app/data/cloud /app/data/cloud/files; find /app/data/cloud/files -xdev -maxdepth 2 -printf "%u:%g %m %p\\n" | head -100'
docker compose --env-file .env config --quiet
docker compose --env-file .env up -d --build
```

Compose의 volume `subpath` 마운트에는 Docker Compose 2.24.1 이상과 해당 지원이 포함된 Docker Engine이 필요합니다. 빌드 및 시작 후 `docker compose ps`에서 `nas-storage-init` 성공과 Samba 상태를 확인하고 Samba 로그에서 설정 오류를 확인합니다. UID/GID 확인 결과가 예상과 다르면 서비스를 시작하기 전에 원인을 해결하십시오. 권한을 일괄 변경하지 마십시오. 신규 설치는 디렉터리만 초기화 컨테이너가 만듭니다. `docker compose down -v`를 실행하지 마십시오.

Samba 암호를 바꾸려면 `.env`를 갱신한 뒤 Samba 컨테이너를 재생성합니다:

```sh
docker compose --env-file .env up -d --build --force-recreate samba
```

## 네트워크 경계

Compose는 TCP 445를 지정된 `NAS_SMB_BIND_ADDRESS`에만 게시합니다. 서버 방화벽에서도 허용된 LAN CIDR 또는 VPN CIDR에서 해당 인터페이스의 TCP 445로 오는 연결만 허용하십시오. Docker가 게시한 포트는 UFW/firewalld의 일반 INPUT 규칙을 우회할 수 있습니다. 운영 서버가 사용하는 Docker firewall backend를 먼저 확인하고, iptables backend라면 `DOCKER-USER` 체인으로 제한하며 nftables backend라면 해당 backend 방식으로 허용 대역을 제한하십시오. 기존 규칙을 조사 없이 변경하지 않습니다. 라우터에서 TCP 445 포트 포워딩을 만들거나 공용 인터넷에 SMB를 노출하지 마십시오. 원격 접속은 Tailscale/WireGuard에 연결된 서버 인터페이스 주소를 통해 수행하십시오. [Docker firewall 및 포트 게시 문서](https://docs.docker.com/engine/network/firewall-iptables/).

## 클라이언트 연결

### Windows

탐색기 주소창 또는 실행 창에 `\\NAS_HOST\storage`를 입력하고 NAS 사용자 이름/비밀번호로 로그인합니다. 드라이브 문자 연결은 파일 탐색기 → 내 PC → 네트워크 드라이브 연결에서 문자를 선택하고 폴더를 `\\NAS_HOST\storage`로 설정합니다. “로그인할 때 다시 연결”과 “다른 자격 증명을 사용하여 연결”을 선택하면 재부팅 후 다시 연결됩니다. PowerShell에서는 다음 명령을 사용합니다. 암호를 명령행에 넣지 않아 암호 프롬프트가 표시됩니다.

```powershell
net use Z: \\NAS_HOST\storage * /user:NAS_USERNAME /persistent:yes
```

### Linux

```sh
sudo apt install cifs-utils
sudo mkdir -p /mnt/nas
sudo install -m 600 /dev/null /etc/samba/credentials-nas
sudoedit /etc/samba/credentials-nas
```

자격 증명 파일에는 다음을 기록하고 root만 읽게 둡니다.

```text
username=NAS_USERNAME
password=NAS_PASSWORD
```

수동 마운트:

```sh
sudo mount -t cifs //NAS_HOST/storage /mnt/nas -o credentials=/etc/samba/credentials-nas,vers=3.1.1
```

`/etc/fstab` 자동 마운트:

```fstab
//NAS_HOST/storage /mnt/nas cifs credentials=/etc/samba/credentials-nas,vers=3.1.1,_netdev,nofail,x-systemd.automount 0 0
```

암호를 `/etc/fstab`에 직접 쓰지 마십시오.

### Android

SMB3를 지원하는 파일 관리자의 네트워크 위치 추가 화면에서 호스트 `NAS_HOST`, 포트 `445`, 공유 `storage`, NAS 사용자 이름과 비밀번호를 입력합니다. 표준 SMB 공유를 사용하며 별도 Android 서버 API는 제공하지 않습니다.

## 확인 범위

SMB locking은 Samba 기본 파일 잠금으로 처리합니다. 대시보드의 웹 휴지통과 버전 관리는 SMB에서 수행한 변경에 적용되지 않습니다. SMB 클라이언트에서 바로 삭제한 파일은 대시보드 휴지통에 들어가지 않습니다. 동시에 같은 파일을 편집하는 앱 간 협업 잠금은 응용프로그램별 동작에 달려 있습니다.

운영 확인은 Windows와 Linux 클라이언트에서 UTF-8/한글 이름(`테스트.txt`, `사진/`, `프로젝트/`)으로 생성·읽기·수정·이름 변경·삭제, 폴더 작업, 대용량 파일, 다중 파일 전송, 복수 클라이언트 잠금 동작을 확인합니다. 재시작 후 같은 파일이 남는지 검사합니다. 이 환경은 Windows이며 Docker daemon, Linux NAS 서버, Windows SMB 서버에 연결되지 않아 클라이언트 실동작 검증은 수행할 수 없었습니다.
