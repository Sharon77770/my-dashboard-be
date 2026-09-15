# 아키텍처

Java 21 / Spring Boot 3.5.16 / Thymeleaf / SQLite 단일 웹 애플리케이션이다.
Thymeleaf가 계정·CSRF·초기 WorkspaceView와 화면 구조를 렌더링하고, 프레임워크 없는 JavaScript가 같은 서버 API/WS를 호출하여 조작·스트림 표시를 담당한다.
별도 프런트엔드 서버나 클라이언트의 SSH/SFTP 실행은 없다.

## 계층

- `catalog/controller -> catalog/service -> catalog/repository -> SQLite`: 장비·앱·클립보드·즐겨찾기·최근 작업·화면 설정·탭.
- `files/controller -> files/service -> files/adapter -> local filesystem 또는 SshAdapter/SFTP`: 경로 검증, 탐색, 스트리밍 파일 작업.
- `runtime/controller -> runtime/service -> runtime/adapter`: 실행 세션 생성·소유권·종료, PTY/SSH, Chromium URL 열기, guacd 연결.
- WebSocket handler는 입출력 변환과 연결 수명만 처리하고 세션 소유권은 RuntimeService가 검증한다.
- `global/security`: 단일 계정/세션/CSRF/OWNER, AES-GCM credential vault. `global/integration`: 고정된 서버 명령과 SSH 연결 어댑터.
- persistence DeviceRecord/ApplicationRecord, HTTP request DTO, response/view DTO, runtime connection을 분리한다. 비밀번호 암호문은 HTTP로 반환하지 않는다.

## 서버 구성

```text
Browser -- HTTPS/HTTP + session + CSRF --> Spring Boot
Browser -- same-origin WebSocket ------> Spring Boot
Spring Boot --> SQLite + file volume
Spring Boot --> local PTY / SSH / SFTP / UDP Wake
Spring Boot --> guacd --> registered RDP/VNC device
Spring Boot --> Chromium DevTools (URL launch)
Spring Boot --> guacd --> Chromium VNC desktop
```

Compose는 tailscale, dashboard, guacd, browser, wine 5개 서비스를 사용한다. 앱/guacd/browser/wine은 tailscale의 네트워크 네임스페이스를 공유한다. guacd와 Chromium/VNC/DevTools는 loopback에만 바인딩한다.
Chromium은 비루트 컨테이너에서 실행하고 프로필을 별도 볼륨에 보관한다. 서버 브라우저는 공유 데스크톱 하나이며 각 앱 URL은 Chromium 탭으로 열린다.
REMOTE 브라우저 모드는 등록 VNC 장비와 해당 Chromium 디버깅 포트를 사용한다. CLIENT 모드는 사용자가 명시적으로 선택한 예외로 현재 브라우저에 링크를 제공한다.

## 경계

단일 계정은 환경변수에만 존재한다. 메타데이터 변경과 실행 리소스 생성/삭제는 OWNER와 CSRF가 필요하다.
HTML 익명 요청은 로그인으로 302, API/WS 익명 요청은 401, 권한 부족은 403이다.
런타임은 로그인 세션 ID에 귀속되고 다른 로그인 세션의 핸들로 접속할 수 없다. WebSocket은 동일 origin만 허용한다.
로그아웃/세션 만료 이벤트가 해당 셸/SSH/guacd 연결을 닫는다. 페이지 재접속에서 기존 프로세스 복원은 하지 않는다.
파일 API는 구성된 실제 루트 내부만 접근하며 심볼릭 링크 탈출·경로 traversal을 막는다. 터미널은 서버 사용자 권한의 실제 셸이다.
SSH 호스트 지문과 RDP 인증서는 검증하며 자동 신뢰를 하지 않는다. 원격 실패를 사용자 메시지로 매핑하고 자동 실행 재시도는 하지 않는다.

## 결정

| 날짜 | 결정 | 이유 |
| --- | --- | --- |
| 2026-09-07 | Thymeleaf와 환경변수 단일 계정 | 백엔드 only 개인 웹사이트 |
| 2026-09-08 | 실제 서버 실행 + 어댑터 분리 | 참고 UI 전체 동작 구현 |
| 2026-09-08 | 독립 브라우저 설정 CLIENT/SERVER/REMOTE | 사용자가 실행 위치 선택 |
| 2026-09-08 | SQLite 메타데이터, 별도 영속 암호화 키 | 접속정보 보관과 응답 분리 |
| 2026-09-08 | Java LF 고정 | Windows/Linux 빌드 검사 일치 |

## Planner
`planner/controller -> planner/service -> planner/repository -> SQLite`. HTML/JS는 입력·표시를 담당하고 날짜 범위/수업 겹침/학점 합산/학기 소유 관계는 서버가 검증한다. 요청 DTO·응답 DTO·영속 record를 구분한다. 일반 일정과 주간 수업은 별도 리소스이며 자동 복제하지 않는다. 단일 OWNER와 기존 CSRF 정책을 재사용한다. 스키마 v2는 v1 데이터를 유지하면서 4개 테이블과 인덱스를 추가한다.

## Tailscale 네트워크

Wine도 Tailscale과 네트워크를 공유한다. 고정 서버 데스크톱은 RuntimeService가 WINE_HOST/WINE_VNC_PORT로 매핑하고 RemoteAdapter → guacd → Wine x11vnc(127.0.0.1:5902)로 연결한다. Xvfb가 디스플레이를 제공하고 x11vnc는 XDamage 최적화를 끈 전체 화면 갱신을 사용한다. 앱 설치/실행은 Wine 컨테이너 시작 스크립트가 담당한다. HTTP controller에서 프로세스를 실행하지 않는다. 외부 인증 정책은 [authentication.md](../authentication.md)를 따른다.
공식 tailscale 이미지 1.102.3을 digest로 고정한다. tailscaled --tun=tailscale0의 커널 TUN 네트워크로 기존 SSHJ, guacd, Chromium의 일반 TCP 연결이 tailnet 경로를 사용한다. 별도 SOCKS proxy는 사용하지 않는다. 장비별 networkMode 옵션과 DeviceNetworkAdapter가 선택한 Tailscale 주소를 검사한다. sidecar만 /dev/net/tun 및 NET_ADMIN/NET_RAW를 가진다. TS_AUTHKEY는 sidecar에만 전달하고 /var/lib/tailscale을 별도 tailscale-state 볼륨에 저장한다. Java/브라우저에는 상태 볼륨과 LocalAPI 소켓을 마운트하지 않는다.
모든 서비스가 같은 네트워크와 resolver 파일을 공유하므로 GUACD_HOST/BROWSER_HOST는 127.0.0.1이다. VNC/CDP/guacd는 loopback에만 바인딩하여 tailnet에서 직접 제어할 수 없다. X11 TCP는 비활성화한다. Tailscale은 localhost 호스트 포트 8080을 소유하고 tailnet 정책 허용 시 tailnet의 8080에도 앱이 응답한다. 공용 Funnel/Serve/서브넷 광고/exit node/내장 SSH 서버는 활성화하지 않는다.

## 원격 개발 작업 공간

studio/controller → studio/service → studio/adapter → 로컬 프로세스 또는 SSH의 고정 Python/CLI helper. Thymeleaf/CodeMirror는 UI를 담당하고 파일·Git·Codex 처리는 대상 서버에서 실행한다. SQLite 변경 없이 로그인 소유 작업을 메모리에서 관리한다. [상세](../studio.md).

## Launcher 표시 아키텍처 (2026-09-14)

Sidebar와 대시보드형 Home을 앱/폴더/위젯/페이지/Dock/Drawer 구조로 교체한다. 중앙 App Registry를 Launcher, 검색, 내장 앱 탭이 공유한다. HomeItem은 브라우저 표시 모델이며 계정별 localStorage에 저장한다. 기존 서버 실행 탭과 업무 API/SQLite 계약은 유지한다. Desktop과 Mobile은 같은 모델을 사용하지만 모바일은 전체 앱 화면과 전환기, IDE 단일 패널 모드를 사용한다. [모듈·격자·저장 계약](../launcher.md).

### IDE Codex 통신

StudioController → StudioService(OWNER/HTTP 세션·수명·크기 제한) → StudioAdapter(고정 helper와 stdin 입력) → codex_bridge.py → Codex App Server stdio. 로컬/SSH가 같은 경로를 사용한다. App Server 포트는 열지 않는다. CLI thread 저장소가 대화 원본이며 SQLite 모델은 추가하지 않는다. 승인 응답은 동일 job의 stdin으로 전달한다.

장비의 NetworkMode는 DIRECT/TAILSCALE이다. SshAdapter와 RemoteAdapter는 주입된 DeviceNetworkAdapter를 사용하고, 원격 브라우저·단순 포트 상태 조회는 CatalogService.connectionHost를 통해 같은 adapter를 사용한다. 네트워크/DNS 처리는 controller나 UI에서 수행하지 않는다. TAILSCALE은 tailscale0에 tailnet 주소가 존재하는지 확인한 뒤 3초 이내 DNS 결과 중 tailnet 주소만 선택하여 숫자 IP로 접속한다. 일반 주소 fallback은 없다. CLI 로그인 상태 저장이나 LocalAPI 노출은 추가하지 않는다.

장비 로그는 StudioService의 세션 소유 비동기 작업으로 실행하고 StudioAdapter → 고정 logs.py → 로컬/SSH CLI를 경유한다. 기존 SSH/Tailscale 경계를 재사용한다. 로그 작업은 프로젝트 잠금을 잡지 않고 최근 이벤트만 메모리에 보유한다. 상세 계약은 [장비 로그](../device-logs.md)를 따른다.

TailscaleService는 OWNER 검증 후 전용 TailscaleAdapter를 통해 loopback 인증 브리지를 호출한다. 브리지는 sidecar 내부에서 고정 CLI 세 가지만 실행한다. dashboard는 브리지 토큰 파일만 읽으며 데몬 소켓·상태·Docker 권한을 얻지 않는다. 초기 환경변수 자동 로그인 완료 후 브리지를 시작하여 CLI 인증 경쟁을 방지한다. [설정과 제한](../tailscale.md).

클라우드 드라이브는 cloud/controller → cloud/service(OWNER 및 실패 변환) → cloud/adapter/CloudStorage(검증된 전용 디스크 작업)로 분리한다. 사용자 가상 경로는 CLOUD_ROOT/files 하위에만 매핑하며 기존 SSH 파일 탐색과 독립한다. 업로드/복사는 staging을 거쳐 게시하고 파일시스템 변경은 단일 adapter 잠금으로 직렬화한다. 다운로드는 열린 InputStream으로 전달하고 ZIP은 스트림 종료 시 임시 파일을 정리한다. TrashRecord는 내부 저장 모델, CloudDto는 HTTP 모델이다. [저장·한도](../cloud-drive.md).

NAS는 nas/controller/DavServlet → nas/service/DavService → cloud/adapter/CloudStorage를 경유한다. DavXml은 제한된 XML 파싱·생성을 담당한다. DavLocks의 일시적 잠금 상태를 CloudStorage도 검사하여 웹과 WebDAV 쓰기 충돌을 방지한다. 별도 Basic 보안 체인과 서블릿을 사용하며 기존 세션 API와 분리한다. DB 스키마 변경 없이 기존 영구 드라이브를 공유한다. [상세](../nas.md).

원격 자동 구성은 DesktopSetupController → DesktopSetupService → DesktopSetupAdapter → 검증된 SSH의 고정 setup.py 순서로 실행한다. RemoteAdapter는 관리된 VNC만 SSH loopback 터널로 guacd에 연결하며, 기존 직접 연결은 유지한다. [지원 환경과 수명](../remote-desktop.md).
