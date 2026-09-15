# 드라이브 NAS 연결 (WebDAV)

클라우드 드라이브 → **NAS 연결**에서 주소 복사와 기기별 안내를 연다. 기존 드라이브를 WebDAV 네트워크 저장소로 제공한다. NFS/SMB 서버나 POSIX 권한·실행·심볼릭 링크 기능은 제공하지 않는다. 웹과 네트워크 클라이언트는 같은 파일을 읽고 쓴다.

## 설정과 연결

`NAS_ENABLED=true`가 기본이다. `false`이면 WebDAV 요청에 503을 반환한다. `NAS_PUBLIC_URL=https://your-host/dav/`를 지정하면 연결 창이 해당 주소를 사용한다. 비워두면 현재 웹 주소의 `/dav/`를 사용한다. HTTP(S) URL만 허용하며 사용자 정보·쿼리·fragment는 허용하지 않는다. 이 설정 자체가 DNS, TLS, 포트 개방을 수행하지는 않는다.

아이디와 비밀번호는 `DASHBOARD_AUTH_ID`, `DASHBOARD_AUTH_PASSWORD`와 동일하다. 별도 NAS 비밀번호를 만들지 않는다. 비밀번호는 연결 창이나 API에 반환하지 않는다. Basic 인증이므로 인터넷 연결은 HTTPS로 구성한다. 프록시는 `/dav/`와 WebDAV 메서드, Authorization·Destination·If·Lock-Token 헤더 및 업로드 크기를 허용해야 한다. COPY/MOVE는 설정된 공개 주소를 대상으로 사용한다.

Compose는 기본적으로 호스트의 `127.0.0.1:8080`에만 게시한다. 외부 기기는 HTTPS 역방향 프록시를 사용하거나, 기존 Tailscale 로그인 후 같은 tailnet에서 공유 네트워크의 Tailscale IP와 `:8080/dav/`로 연결한다. 후자는 클라이언트가 HTTP WebDAV를 허용해야 하며 전송은 tailnet을 경유한다. Windows 기본 연결에는 HTTPS 주소를 사용한다. 일반 LAN에 HTTP 포트를 공개하지 않는다.

- Windows: 파일 탐색기 → 내 PC → 네트워크 위치 추가에서 HTTPS 주소를 입력한다. WebClient 서비스와 운영체제의 WebDAV 정책·파일 크기 제한이 적용된다. [Microsoft 안내](https://learn.microsoft.com/en-us/iis/publish/using-webdav/using-the-webdav-redirector).
- macOS: Finder → 이동 → 서버에 연결(⌘K)에서 주소를 입력한다. [Apple 안내](https://support.apple.com/en-in/guide/mac-help/mchlp1546/mac).
- Linux: 파일 관리자의 WebDAV 연결 또는 설치된 davfs2로 `mount -t davfs https://your-host/dav/ /your/mountpoint`를 사용한다. 자격 증명은 명령행 인자로 남기지 않고 연결 시 입력한다.
- Android/iOS: WebDAV 지원 파일 앱에서 서버 주소와 계정을 등록한다. iOS 기본 파일 앱의 서버 연결은 SMB용이므로 이 주소를 직접 등록하는 방식은 지원하지 않는다. 앱의 파일 제공자 지원에 따라 다른 앱에서 접근 가능한 범위가 다르며 휴대폰 전체 파일시스템 마운트를 보장하지 않는다.

## 파일 동작과 호환 범위

OPTIONS, PROPFIND, GET, HEAD, PUT, MKCOL, COPY, MOVE, DELETE, LOCK, UNLOCK을 제공한다. PROPFIND는 Depth 0/1이며 무제한 재귀 요청은 403이다. 기본 파일 정보·ETag·잠금 정보를 반환한다. 임의 사용자 속성 저장은 지원하지 않아 PROPPATCH의 속성별 결과는 403이다. WebDAV의 모든 확장이나 클라이언트별 메타데이터 보존을 구현한 것은 아니다. [프로토콜 기준 RFC 4918](https://www.rfc-editor.org/rfc/rfc4918).

DELETE는 휴지통으로 이동한다. COPY/MOVE 덮어쓰기 대상도 먼저 휴지통에 보존하고 전송 실패 시 복원을 시도한다. PUT 덮어쓰기는 별도 버전 이력 없이 교체한다. 루트 변경·경로 이탈·심볼릭 링크는 거부한다. 업로드에는 기존 `UPLOAD_MAX_SIZE`가 적용된다. GET/HEAD의 단일 bytes Range와 ETag 조건부 읽기/저장을 지원한다. 복수 범위 및 모든 HTTP 조건부 헤더 조합을 지원하지는 않는다.

배타적 쓰기 잠금은 웹 드라이브 변경에도 적용되며 충돌 시 423을 반환한다. 잠금은 기본 10분, 최대 1시간, 최대 256개이며 서버 메모리에 저장하므로 재시작 시 사라진다. 갱신과 UNLOCK을 지원한다. If 헤더는 양의 잠금 토큰 목록 및 선택적인 대상 URL 형식을 지원한다. Not 조건이나 If 내부 ETag 등 지원하지 않는 조건은 412로 거부한다.

XML 본문은 64 KiB, 깊이 64로 제한하고 DTD/외부 엔티티를 금지한다. PROPFIND 요청 속성은 최대 64개, 생성 XML은 최대 8 MiB다. 기존 드라이브의 최대 10,000개 항목 제한도 적용된다. 잠금은 영구 파일 접근 권한이나 데이터베이스 트랜잭션을 대체하지 않는다.

## 인증과 검증

`/dav/**`는 OWNER Basic 인증만 사용하는 별도 무상태 보안 체인이다. 브라우저 세션 쿠키만으로 접근할 수 없다. 이 경로만 CSRF 토큰을 요구하지 않으며 기존 웹 API의 세션·CSRF 검사는 유지한다. POST는 파일을 변경하지 않고 405를 반환한다. `GET /api/v1/cloud/nas`는 기존 OWNER 세션을 요구한다.

단위 테스트, UI 동작 테스트 및 격리된 Docker HTTP 테스트로 인증 성공/실패, 메서드, 잠금, 웹과 파일 공유, 부분 다운로드, 조건부 저장, 휴지통, XML 차단을 검증한다. 실제 Windows/macOS 마운트 및 휴대폰 앱 연결은 별도 기기 검증 대상이다. SQLite 스키마 변경은 없다.
