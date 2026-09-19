# HTTP/API 계약

## 공통

별도 명시가 없으면 `/api/v1/*`는 OWNER 인증이 필요하고 JSON 요청/응답은 UTF-8 `application/json`이다.
POST/PUT/PATCH/DELETE는 로그인 HTML의 `csrf-token`과 `csrf-header` meta를 사용한 헤더가 필수다. 세션 쿠키도 함께 전송한다.
각 경로의 `{id}` 또는 `{device}`는 필수 non-null String ID다. ID는 서버 생성 UUID이며 기본 장비만 `local`이다.
아래 표에 없는 query/path/body는 사용하지 않는다. 빈 response body인 201/202/204는 Content-Type을 보장하지 않는다.
JSON 필드는 아래에 별도 optional/null 표기가 없으면 응답에서 필수·null 불가다. 빈 배열은 허용한다. boolean/int 요청 필드는 생략 시 Java 기본값(false/0)이 적용되며 최소값 제약을 검사한다.
에러는 가능한 경우 `{message: String}`를 반환한다. 인증/CSRF 등 보안 필터와 프레임워크 기본 오류는 이 JSON 형식을 보장하지 않는다.

| HTTP | 분류 | 발생 조건/안내 |
| --- | --- | --- |
| 400 | INVALID_INPUT | 필수 항목, enum, 범위, URL, 경로 오류. 입력 형식과 필수 항목 확인 |
| 401 | UNAUTHENTICATED | API/WS 세션 없음 또는 만료. 로그인 필요 |
| 403 | FORBIDDEN | OWNER 권한 없음, CSRF/같은 origin 실패, 파일 루트 탈출 |
| 404 | NOT_FOUND | 장비/앱/실행 핸들 없음 또는 다른 로그인에 속한 핸들 |
| 409 | CONFLICT | 파일 누락/권한/중복/비어 있지 않은 폴더, 실행 제한/중복 attach |
| 413 | UPLOAD_TOO_LARGE | multipart 크기 제한 초과 |
| 500 | INTERNAL_ERROR | 키 복호화, DB, 예상하지 못한 처리 오류. 상세 인증정보/예외는 반환하지 않음 |
| 502 | CONNECTION_FAILED | SSH/guacd/Chromium/UDP 등 연결·실행 실패 |
| 504 | TIMEOUT | 서버 명령 10초 제한 초과 |

분류 이름은 문서상 이름이며 별도 `code` JSON 필드는 없다. message는 사용자 설명이며 파싱하지 않는다.

## 화면·인증

| Method / path | 인증 | Query/body | 성공 | 오류/부수효과 |
| --- | --- | --- | --- | --- |
| GET /login | public | query `error`, `logout`: optional String, 값 없이 존재만 검사. body 없음 | 200 HTML | CSRF 저장용 익명 세션 생성 가능 |
| POST /login | public + CSRF | form-urlencoded `id`, `password`, `_csrf`: 필수 String/null 불가 | 302 `/` | 불일치/누락 ID·비밀번호는 302 `/login?error`, CSRF 실패 403 |
| GET / | OWNER | 없음 | 200 Thymeleaf HTML | 익명은 302 `/login`, 권한 부족 403 |
| POST /logout | CSRF | form-urlencoded `_csrf`: 필수 String/null 불가 | 302 `/login?logout` | 세션·쿠키·소유 실행 연결 폐기, 잘못된 CSRF 403 |
| GET /health | public | 없음 | 200 JSON `{status:"UP"}` | status 필수 String/enum UP/null 불가. DB/외부 서비스 준비는 보장하지 않음 |

JSESSIONID는 HttpOnly, 기본 Secure, SameSite=Lax, cookie-only다. 로그인 시 ID 교체, 기본 비활성 30분 만료, 로그아웃/재시작 시 폐기한다.
JWT/refresh/remember-me는 없다. HTML의 비밀번호 입력은 복원하지 않는다. 오류 화면은 상세 예외를 표시하지 않는다.
홈은 `accountId`와 안전한 WorkspaceView를 Thymeleaf로 렌더링한다.

## 작업 공간 및 검색

### GET /api/v1/workspace
- query/body 없음. 200 WorkspaceView.
- 필드: `devices: DeviceView[]`, `applications: ApplicationView[]`, `clips: ClipView[]`, `bookmarks: BookmarkView[]`, `activity: ActivityView[]`, `preferences: Preferences`, `browserSettings: BrowserSettings`, `tabs: TabView[]`.
- 만료된 클립을 조회에서 제외하고 정리한다. 조회 오류는 공통 오류표를 따른다.

### GET /api/v1/search
- query `query`: optional String, 기본 빈 문자열, 최대 200자, null 입력 계약 없음. body 없음.
- 200 ActivityView[] 최대 50개. 빈 검색은 빠른 실행 후보를 반환한다. 검색 결과 occurredAt은 0이다.
- 장비의 실행 종류/앱/즐겨찾기 이름을 검색한다. 400: 검색어 길이 초과.

## 장비

### POST /api/v1/devices / PUT /api/v1/devices/{id}
- body DeviceRequest, query 없음. POST 201 DeviceView, PUT 200 DeviceView.
- local은 변경할 수 없다(400). PUT 대상 없음 404. 유효성 오류 400.

| DeviceRequest 필드 | 타입 | 필수/null | 규칙 |
| --- | --- | --- | --- |
| name | String | 필수/null 불가 | nonblank, 최대 80 |
| host | String | 필수/null 불가 | hostname/IP, 최대 253, slash/공백/선행 - 불가 |
| sshPort | int | 필수 | 1~65535 |
| username | String | optional/null 허용 | 최대 128, null은 빈 문자열 |
| password | String | optional/null 허용 | 최대 4096, null/빈 값은 기존 암호문 유지, 새 장비는 미설정 |
| fingerprint | String | optional/null 허용 | 최대 120, 빈 값 또는 SHA256:base64 지문 |
| rootPath | String | 필수/null 불가 | `/`로 시작하는 원격 절대 경로, 최대 1024 |
| remoteProtocol | String enum | 필수/null 불가 | NONE/RDP/VNC |
| remotePort | int | 필수 | 1~65535 |
| remoteUsername | String | optional/null 허용 | 최대 128, null은 빈 문자열 |
| remotePassword | String | optional/null 허용 | 최대 4096, null/빈 값은 기존 값 유지 |
| mac | String | optional/null 허용 | 빈 값 또는 6개 16진 octet, ':' 또는 '-' 구분 |
| broadcast | String | optional/null 허용 | 최대 253, Wake 대상 주소 |
| pinned | boolean | optional | 기본 false |
| networkMode | NetworkMode enum | optional/null 허용 | DIRECT 또는 TAILSCALE, null은 기존 값 유지 |
| jumpDeviceIds | String[] | optional/null 허용 | 순서대로 연결할 등록 장비 ID, 최대 5개. null은 기존 값 유지, 빈 배열은 직접 연결 |

DeviceView: `id`, `name`, `host`, `sshPort`, `username`, `fingerprint`, `rootPath`, `remoteProtocol`, `remotePort`, `remoteUsername`, `mac`, `broadcast`, `pinned`, `networkMode`, `jumpDeviceIds`는 위 의미/타입이며 모두 필수 non-null이다.
추가 `hasPassword: boolean`, `hasRemotePassword: boolean`은 설정 여부다. password/암호문 필드는 없다.

`jumpDeviceIds`에 지정한 장비는 local이 아니고 중복될 수 없으며 SSH 사용자·암호·호스트 키 지문이 등록되어야 한다. 점프 장비 자체에는 점프 체인을 설정할 수 없다. 연결은 대시보드 → 첫 점프 장비 → 다음 점프 장비 → 대상 장비 순서로 SSH Direct-TCPIP 채널을 만든다. 각 홉의 호스트 키를 검증하며 어느 홉이라도 실패하면 전체 연결을 실패시킨다.

### DELETE /api/v1/devices/{id}
- query/body 없음. 204. local 삭제 400, 대상 없음 404. 장비와 FK 즐겨찾기 및 최근 이력을 제거한다.

### GET /api/v1/devices/{id}/status
- query/body 없음. 200 DeviceStatus.
- `state`: String enum ONLINE/REACHABLE/UNAVAILABLE.
- `cpu`, `memory`, `disk`: optional measurement Double, 키는 항상 존재하며 null 허용, 0~100 퍼센트. 미측정은 null.
- `details`: String 설명, `checkedAt`: long Unix epoch milliseconds.
- 연결 실패는 UNAVAILABLE 데이터로 반환하고 예시 수치를 넣지 않는다. 장비 미존재 404.

### GET /api/v1/devices/{id}/docker / GET /api/v1/devices/{id}/gpu
- query/body 없음. 200 `{output: String}`, 최대 256KiB 명령 출력. CLI 미설치·권한 문제도 출력에 나타날 수 있다.
- Docker 목록은 docker ps -a, GPU는 nvidia-smi다. 404/502/504 가능. 자동 재시도 없음.

### POST /api/v1/devices/{id}/docker
- body `{container: String, action: String}` 모두 필수 nonblank/null 불가.
- container는 `[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}`, action은 start/stop/restart.
- 200 `{output: String}`. 실제 실행 결과를 반환하며 exit-code 전용 필드는 없다. 입력 오류 400, 연결 실패 502, 시간 초과 504.

### POST /api/v1/devices/{id}/wake
- query/body 없음. 202 empty. MAC/broadcast 미설정 400, 전송 실패 502.
- 저장된 장비 주소로 UDP/9 magic packet을 보낸다. 응답은 전송 완료이며 기동 성공이 아니다.

## 앱·클립·즐겨찾기

### POST /api/v1/applications / PUT /api/v1/applications/{id}
- body `{name: String, url: String, pinned: boolean}`. name/url 필수 nonblank/non-null, 최대 80/2048자. pinned optional default false.
- url은 HTTP(S), host 필수, userinfo 금지. 201/200 ApplicationView `{id:String,name:String,url:String,pinned:boolean}`.
- 400: URL/입력 오류, 404: PUT 대상 없음.

### DELETE /api/v1/applications/{id}
- query/body 없음. 204. 앱과 해당 최근 작업 제거. 미존재 삭제는 204.

### POST /api/v1/clips
- body `{content:String,minutes:int}`. content 필수 nonblank/non-null 최대 32000, minutes 1~1440.
- 201 ClipView `{id:String,content:String,expiresAt:long}`. expiresAt은 Unix epoch milliseconds. 400 입력 오류.

### DELETE /api/v1/clips/{id}
- query/body 없음. 204. 미존재도 204.

### POST /api/v1/bookmarks
- body `{deviceId:String,path:String}` 필수 nonblank/non-null. path 최대 1024, `/`로 시작하며 `..` 불가.
- 201 empty. 장비 미존재 404, 경로 오류 400. 같은 device/path 중복은 추가하지 않는다.
- BookmarkView는 `{id:String,deviceId:String,path:String}`.

### DELETE /api/v1/bookmarks/{id}
- query/body 없음. 204. 미존재도 204.

ActivityView 필드: `id:String`, `kind:String`(FILES/TERMINAL/REMOTE/APP/DOCKER/GPU), `targetId:String`, `label:String`, `path:String`(파일 외 빈 문자열 가능), `occurredAt:long`(epoch milliseconds).
검색은 후보 종류 전체를 반환하며 실제 최근 작업은 FILES/TERMINAL/REMOTE/APP을 기록한다.

## 설정·탭

### PUT /api/v1/preferences
- body Preferences `{theme:String,compact:boolean,terminalFont:int,clipMinutes:int}`.
- theme 필수 non-null enum dark/light, compact optional false, terminalFont 10~24, clipMinutes 1~1440.
- 204 empty. 400 유효성 오류. 다음 workspace 응답에 저장값을 반환한다.

### PUT /api/v1/browser-settings
- body BrowserSettings `{mode:String,deviceId:String,debugPort:int}`.
- mode 필수 non-null enum CLIENT/SERVER/REMOTE. deviceId 필수 non-null String(빈 문자열 허용), debugPort 1~65535.
- REMOTE는 실제 VNC 장비 ID가 필요하다. SERVER/CLIENT는 deviceId를 사용하지 않는다.
- 204 empty. 400 부적합 프로토콜, 404 없는 장비. 기존 연결은 바꾸지 않고 다음 앱 실행부터 적용한다.

### PUT /api/v1/tabs
- body `{tabs:TabRequest[]}` 필수 non-null, 최대 20개. 중복 id는 400.
- TabRequest/TabView 필드: `id:String` nonblank 최대80, `kind:String` enum FILES/TERMINAL/REMOTE/APP/DOCKER/GPU, `targetId:String` nonblank 최대80, `path:String` non-null 최대1024, `title:String` nonblank 최대120, `pinned:boolean` default false.
- 204 empty. 배열 순서를 저장한다. 실행 연결은 저장하지 않는다.

## 파일

모든 `{device}`는 등록 장비 ID, 경로는 장비 rootPath에 상대적인 `/` 시작 경로다.
`..` segment, backslash, NUL, 1024자 초과 경로는 400. canonical 경로가 루트 밖이면 403.

| Method / path | Query/body | 성공 | 오류/효과 |
| --- | --- | --- | --- |
| GET /api/v1/devices/{device}/files | query path optional String default `/`; body 없음 | 200 FileListing | 최대 2000개, 최근 위치 기록. 400/403/409/502 |
| POST /api/v1/devices/{device}/files | multipart `path` 필수 String, `file` 필수 binary+filename | 201 empty | 임시파일 스트리밍 후 이동, 덮어쓰기 금지. 400/403/409/413/502 |
| POST /api/v1/devices/{device}/files/folders | JSON `{path:String,name:String}` 모두 필수/nonblank/null 불가 | 201 empty | 새 폴더. 400/403/409/502 |
| PATCH /api/v1/devices/{device}/files | JSON `{path:String,name:String}` 모두 필수/nonblank/null 불가 | 204 empty | 동일 부모 폴더에서 이름 변경. 400/403/409/502 |
| DELETE /api/v1/devices/{device}/files | query path 필수 String; body 없음 | 204 empty | 파일 또는 빈 폴더 삭제, root 삭제 불가. 400/403/409/502 |
| GET /api/v1/devices/{device}/files/content | query path 필수 String; body 없음 | 200 octet-stream | Content-Length 및 UTF-8 attachment filename, 일반 파일만 허용. 400/403/409/502 |

name/filename은 1~255자 basename, '.', '..', slash, backslash, NUL 불가다. 업로드는 기본 1GB 제한이며 multipart는 메모리 대신 디스크 spool을 사용한다.
FileListing: `{path:String,entries:FileEntry[]}`.
FileEntry: `{name:String,path:String,directory:boolean,size:long,modifiedAt:long}`. size는 bytes, modifiedAt은 epoch milliseconds. 폴더 크기는 UI에서 의미 있는 합계로 표시하지 않는다.

## 실행과 WebSocket

### POST /api/v1/sessions
- body SessionRequest `{kind:String,targetId:String,width:int,height:int}`.
- kind 필수 non-null enum TERMINAL/REMOTE/APP, targetId 필수 nonblank String, width 320~3840, height 240~2160.
- 201 SessionView `{id:String,kind:String,label:String,url:String}`. 일반 실행 url은 빈 문자열이다.
- CLIENT 브라우저 모드에서 APP 요청은 kind=CLIENT, id="", url=등록 URL을 반환하며 실제 서버 연결은 만들지 않는다.
- 최대 12개 실행 핸들. 400 프로토콜 설정 누락, 404 대상 없음, 409 제한, 502 Chromium 연결 실패.
- 브라우저 URL 열기는 서버 어댑터를 사용한다. 생성 후 60초간 attach가 없으면 핸들을 정리한다.

### DELETE /api/v1/sessions/{id}
- query/body 없음. 204 empty. 실제 연결 종료. 미존재/다른 로그인 소유 ID는 404.

### GET /ws/runtime/{id} (WebSocket Upgrade)
- OWNER 로그인과 같은 origin, 해당 로그인에서 생성한 핸들이 필요하다. query/body 없음. 성공 101 Upgrade.
- 최초 1개 소켓만 attach 가능하다. remote/APP은 `guacamole` subprotocol, TERMINAL은 subprotocol 없음.
- TERMINAL client->server JSON: `{type:"input",data:String}` 또는 `{type:"resize",columns:int,rows:int}`. columns는 20~300, rows는 5~120으로 clamp한다. 서버->client는 UTF-8 터미널 텍스트.
- REMOTE/APP 양방향은 Guacamole 1.6 프로토콜 instruction 문자열이다. 초기 빈 opcode는 터널 UUID, 내부 ping은 echo한다.
- 한 메시지 최대 64KiB, 송신 buffer 1MiB, 10초 송신 제한. 초기 생성 요청의 viewport와 remote resize/scale에 따라 화면을 표시한다.
- 연결/프로토콜 실패는 WS 1011과 일반 안내 문구. 계정/접속정보를 close reason에 포함하지 않는다.
- 소켓 종료·DELETE·로그아웃·로그인 만료·서버 종료는 리소스를 정리한다. UI 재연결은 새 POST로 새 핸들을 만든다.

## 외부 연동 계약

SSH: SSHJ 0.40.0, 비밀번호 인증과 저장 SHA256 host fingerprint, connect 5초, 기본 I/O timeout 10초. SFTP는 작업별 connection을 닫는다.
명령: local/SSH 고정 명령, 최대 10초/출력 256KiB, 자동 retry 없음. PTY 명령은 사용자가 직접 입력하는 별도 실행 채널이다.
remote: guacamole-common/guacd 1.6.0. RDP/VNC 연결은 전용 adapter, 자격증명은 서버 handshake에만 사용한다.
Chromium: 서버에서 HTTP PUT `/json/new?{encodedUrl}`; connect 5초, request 10초, HTTP 200만 성공. 등록 hostname을 서버에서 IP로 resolve한다.
Wake: 서버 DatagramSocket에서 등록 주소 UDP/9로 전송한다. 원격 API/SDK response를 공개 DTO로 그대로 반환하지 않는다.

## POST /api/v1/devices/ssh
OWNER 세션 + CSRF. path/query 없음. SSH 연결을 검증하고 장비를 생성하거나 갱신한다.

| 요청 필드 | 타입 | 필수/null | 의미 |
| --- | --- | --- | --- |
| command | String | 필수/null 불가 | 최대 512. ssh 사용자@호스트, 선택 -p 포트/-p포트(대상 앞/뒤, 1~65535). 기본 22. IPv6 대괄호 허용. 사용자명 영문/숫자/밑줄로 시작, 이후 점/하이픈 허용, 최대 128. 호스트 영문/숫자/점/하이픈/콜론, 최대 253. 다른 SSH 옵션/셸 명령 불가 |
| password | String | 필수/null 불가 | 비어 있지 않은 대상 SSH 비밀번호, 최대 4096. 응답/로그/toString에 노출하지 않음 |
| name | String | 선택/null 허용 | 최대 80. 공백 제거 후 비면 기존 이름 또는 사용자@호스트(80자까지) |

성공 200: 기존 DeviceView와 동일한 필드/타입/필수성(위 DeviceView 정의). 새로운 장비는 원격 화면 NONE, 원격 포트 3389, pinned true, SFTP canonical 홈이 rootPath다. 동일 host(대소문자 무시)/port/username이면 기존 ID/파일 루트/원격 설정/고정 상태를 유지한다. 첫 키 자동 신뢰, 이후 저장 키 일치 필요. 인증/SFTP 확인이 끝난 후에만 암호화 저장하며 실패 시 저장하지 않는다.
오류: 400 INVALID_INPUT(지원 구문 또는 필수 값 오류), 401 UNAUTHENTICATED, 403 FORBIDDEN(CSRF/OWNER), 409 CONFLICT(같은 호스트에 서로 다른 저장 키), 502 CONNECTION_FAILED(접속 거부/timeout/인증/SFTP 실패 또는 호스트 키 변경), 500 INTERNAL_ERROR(저장 실패). 응답 오류 형식은 공통 message이며 인증정보/외부 예외는 노출하지 않는다. connect 5초, I/O 10초, 자동 재시도 없음. SSH 명령은 셸 실행 없이 파싱하여 SSHJ adapter에 전달한다.

### DeviceRequest fingerprint 생략 동작
POST /api/v1/devices 및 PUT /api/v1/devices/{id}에서 fingerprint 생략/null이면 같은 주소·포트의 기존 키를 유지한다. 기존 키가 없고 username이 있으면 SSH 비밀번호(수정 시 비우면 기존 비밀번호)로 연결 및 SFTP 확인 후 키를 자동 저장한다. 저장된 동일 호스트 키와 다르거나 연결에 실패하면 502, 저장 키가 상충하면 409이며 변경을 저장하지 않는다. username도 비어 있으면 원격 화면 전용 장비로 빈 키를 저장한다. 명시적인 fingerprint 문자열/빈 값은 기존 API 계약을 유지한다. UI는 이 필드를 보내지 않는다.

## Planner API 계약
모든 아래 경로는 OWNER 세션 + 변경 요청의 CSRF를 요구한다. 명시된 항목 외 query/body 없음. `{id}`, `{termId}`는 필수 non-null UUID 문자열. 오류 공통: 400 입력/날짜/시간/자체 수업 겹침, 401 미인증, 403 OWNER/CSRF 실패, 404 대상 없음 또는 다른 학기의 과목, 409 다른 과목과 시간 겹침, 500 저장 실패. 공통 `{message}` 오류 형식을 사용한다.

| Method | Path | Query / Body | 성공 응답 |
| --- | --- | --- | --- |
| GET | /api/v1/calendar/events | from/to: 필수 ISO date, from 포함/to 제외, 1~366일 | 200 EventView[], 교차하는 일정, 시작 시각/ID 순 |
| POST | /api/v1/calendar/events | EventRequest | 201 EventView |
| PUT | /api/v1/calendar/events/{id} | EventRequest, 전체 교체 | 200 EventView |
| DELETE | /api/v1/calendar/events/{id} | 없음 | 204 빈 본문 |
| GET | /api/v1/timetables | 없음 | 200 TermView[], 학기 시작일 역순 |
| POST | /api/v1/timetables | TermRequest | 201 TermView |
| PUT | /api/v1/timetables/{id} | TermRequest | 200 TermView |
| DELETE | /api/v1/timetables/{id} | 없음 | 204 빈 본문, 과목/수업시간 cascade 삭제 |
| GET | /api/v1/timetables/{id} | 없음 | 200 TimetableView |
| POST | /api/v1/timetables/{termId}/courses | CourseRequest | 201 CourseView |
| PUT | /api/v1/timetables/{termId}/courses/{id} | CourseRequest, 수업시간 전체 교체 | 200 CourseView |
| DELETE | /api/v1/timetables/{termId}/courses/{id} | 없음 | 204 빈 본문, 수업시간 cascade 삭제 |

### Planner 요청 타입
요청의 필수 항목은 null 불가. 선택 문자열 생략/null은 빈 문자열로 정규화한다. 문자열은 trim하여 저장한다. 시간대 offset 없는 현지 시각을 분 단위로 사용한다.

| 타입 | 필드 | 타입 / 필수 | 의미/제약 |
| --- | --- | --- | --- |
| EventRequest | title | String 필수 | 비어 있지 않음, 최대 120 |
| EventRequest | start / end | ISO LocalDateTime 필수 | 1900~2200, 분 단위, end > start, 종료 제외 |
| EventRequest | allDay | boolean 선택 | 생략 false; true이면 start/end 모두 00:00, UI 종료일은 포함 형태로 변환 |
| EventRequest | location | String 선택/null | 최대 200 |
| EventRequest | notes | String 선택/null | 최대 4000 |
| EventRequest | color | String 필수 | #RRGGBB (대소문자 허용) |
| TermRequest | name | String 필수 | 최대 80, 비어 있지 않음 |
| TermRequest | start / end | ISO date 필수 | 1900~2200, end >= start, 날짜 차이 최대 366일. 양끝 포함 학기 기간 |
| CourseRequest | title | String 필수 | 과목명 최대 120, 비어 있지 않음 |
| CourseRequest | professor | String 선택/null | 최대 120 |
| CourseRequest | location | String 선택/null | 최대 200, 과목 공통 강의실 |
| CourseRequest | credits | int 선택 | 0~30, 생략 0, 과목당 한 번 합산 |
| CourseRequest | color | String 필수 | #RRGGBB |
| CourseRequest | notes | String 선택/null | 최대 4000 |
| CourseRequest | meetings | MeetingRequest[] 필수 | 1~21개, 배열 항목 null 불가 |
| MeetingRequest | day | int 필수 | 1=월, 2=화, 3=수, 4=목, 5=금, 6=토, 7=일 |
| MeetingRequest | start / end | ISO LocalTime 필수 | 00:00~23:59 분 단위, end > start, 자정 넘김 불가 |

### Planner 응답 타입
모든 응답 필드는 필수, null 불가. 배열은 빈 배열 가능(meetings는 최소 1개). EventView: id String UUID 및 EventRequest와 동일한 title/start/end/allDay/location/notes/color. TermView: id String UUID 및 TermRequest와 동일한 name/start/end. MeetingView: day int, start/end LocalTime. CourseView: id String UUID, termId String UUID, title/professor/location String, credits int, color/notes String, meetings MeetingView[]. TimetableView: term TermView, courses CourseView[], totalCredits int(과목별 credits 합계). 일반 일정끼리 겹침은 허용하고, 수업은 같은 학기의 같은 요일 [start,end) 구간끼리 겹치면 거부한다. 학기 기간은 시간표 구분용이며 캘린더 이벤트를 자동 생성하지 않는다.

## SSH 코드 에디터

[Studio API 계약](studio.md)은 비동기 작업의 입력·결과·실패 상태와 소유권을 정의한다. 실행은 서버 자체(local, Docker 컨테이너) 또는 등록 SSH 서버에서 수행한다.

## Codex App Server 입력

POST `/api/v1/studio/jobs/{id}/inputs`: OWNER와 생성한 HTTP 세션만 접근하고 CSRF가 필요하다. 성공 204, 다른 세션/미존재 404, 비실행 작업 409, 검증 오류 400. 요청·이벤트·세션 계약은 [Studio API](studio.md#codex-app-server)를 따른다.

## 장비 네트워크 옵션

DeviceRequest, SshDeviceRequest에 networkMode enum DIRECT/TAILSCALE을 추가한다. DeviceView에도 필수 networkMode를 반환한다. 기존 엔드포인트와 OWNER/CSRF 요건은 유지한다. 새 장비에서 생략/null은 DIRECT, 기존 장비 수정 및 같은 SSH 프로필 재등록에서 생략/null은 저장된 설정을 유지한다. 잘못된 enum은 400이다. host 또는 SSH 명령어의 호스트에 선택한 네트워크의 주소를 입력한다. TAILSCALE은 로그인된 tailscale0 인터페이스가 없거나 DNS 실패/시간 초과이면 502, tailnet 범위 주소가 없으면 400으로 일반 연결 전에 차단한다. 상태 조회는 기존 UNAVAILABLE 형태로 반환한다. TAILSCALE 장비의 Wake는 400이며 패킷을 보내지 않는다.

## Tailscale 관리

GET `/api/v1/tailscale`, POST `/api/v1/tailscale/login`, DELETE `/api/v1/tailscale/login`은 OWNER 전용이며 변경 요청은 CSRF가 필요하다. path/query/body 매개변수는 없다. 각각 상태 조회/로그인 시작/로그아웃이며 성공은 200, Cache-Control: no-store, TailscaleView JSON을 반환한다. 로그인은 비동기이고 POST는 대기를 완료하지 않은 상태를 반환할 수 있다. 로그인 진행 중 재요청은 기존 프로세스를 유지하고 Running이면 새 인증을 시작하지 않는다.

응답 필드는 모두 필수 non-null: state String(상위 BackendState, 주요 값 Running/NeedsLogin/NeedsMachineAuth/Stopped/Starting/NoState/InUseOtherUser/Unknown; 알 수 없는 상태도 그대로 표시), hostname String(없으면 빈 문자열), ips String[](없으면 빈 배열), loginUrl String(로그인 링크가 준비되지 않았거나 연결됨이면 빈 문자열; 공식 https://login.tailscale.com/a/ 주소만 허용), pending boolean(Running이 아니면서 로그인 CLI가 실행 중인지 여부), error String(실패 안내 또는 빈 문자열; Running이면 빈 문자열). 사용자/peer 목록과 키는 반환하지 않는다.

실패는 기존 WorkspaceException 응답을 따른다: 미인증 401, 권한/CSRF 403, 브리지 데몬 오류·25초 응답 제한·중단 502, 토큰 파일 미준비 또는 내부 연결 불가 503. 내부 CLI status/logout 제한은 8초, login은 최대 5분이며 이후 실패 안내와 빈 링크를 반환한다. 데이터베이스 변경은 없으며 Tailscale 인증 상태는 기존 sidecar 상태 볼륨에 보존한다. [상세 동작](../tailscale.md).

## 클라우드 저장소 API

모든 엔드포인트는 OWNER 전용(미인증 401, 권한 부족 403), 변경 요청은 CSRF 필수(누락/불일치 403). 파일은 대시보드 서버의 private CLOUD_ROOT에 보관한다. `{path}`는 OS 실제 경로가 아닌 `/`로 시작하는 가상 경로이며 최대 4096자, 단일 이름 최대 255자, `..`/`.`/역슬래시/제어 문자/심볼릭 링크는 허용하지 않는다. 디렉토리 루트 자체는 생성·이동·삭제할 수 없다. 아래에 기재하지 않은 body/query/path 매개변수는 사용하지 않는다.

| Method / URL | 입력 | 성공 응답 |
| --- | --- | --- |
| GET /api/v1/cloud | query path String 선택 기본 `/`, query String 선택 기본 빈 문자열 최대 200자 | 200 Listing |
| GET /api/v1/cloud/info | query path String 필수 | 200 Entry |
| POST /api/v1/cloud/entries | JSON path String 필수 nonblank, directory boolean 기본 false | 201 body 없음 |
| DELETE /api/v1/cloud/entries | query path String 필수 | 204 body 없음 |
| POST /api/v1/cloud/uploads | multipart file MultipartFile 필수, path String 필수(대상 파일 전체 경로), overwrite boolean 선택 기본 false | 201 body 없음 |
| POST /api/v1/cloud/transfers | JSON source/target String 필수 nonblank, copy boolean 선택 기본 false | 204 body 없음 |
| GET /api/v1/cloud/content | query path String 필수 | 200 attachment/octet-stream, Content-Length; 폴더는 ZIP |
| GET /api/v1/cloud/archive | 반복 query path String[] 필수 1~100개 | 200 ZIP attachment/octet-stream, Content-Length |
| GET /api/v1/cloud/preview | query path String 필수 | 200 Text |
| PUT /api/v1/cloud/text | JSON path String 필수, content String 필수/빈 문자열 허용 최대 1,048,576자 및 UTF-8 1MiB, revision String 필수 최대 64자 | 204 body 없음 |
| GET /api/v1/cloud/trash | 입력 없음 | 200 TrashItem[] |
| POST /api/v1/cloud/trash/{id}/restoration | path id String 필수(서버가 발급한 UUID) | 204 body 없음 |
| DELETE /api/v1/cloud/trash/{id} | path id String 필수(서버가 발급한 UUID) | 204 body 없음 |

모든 응답 필드는 필수/null 불가. Listing: path String, entries Entry[], truncated boolean(스캔 한도 10,000개 초과), totalSpace/usableSpace long(해당 서버 파일시스템 용량 바이트). Entry: name/path String, directory boolean, size long(파일 바이트; 폴더는 0), modified long(epoch ms). Text: path/content/revision String(revision은 읽은 내용 SHA-256 hex). TrashItem: id/path String, deletedAt long(epoch ms), directory boolean. 비어 있는 목록은 빈 배열이다. 검색은 이름의 대소문자를 구분하지 않으며 현재 폴더 하위로 수행한다. 정렬/100개 단위 페이지 표시는 UI가 수행한다.

업로드는 필요한 상위 폴더를 생성하지만 entries 생성 및 transfers는 대상 부모 폴더가 있어야 한다. copy=false는 이동/이름 변경, true는 재귀 복사다. target은 대상 전체 이름까지 지정한다. transfers/restore는 충돌 시 덮어쓰지 않는다. 텍스트 저장은 기존 revision이 일치해야 한다. 영구 삭제는 휴지통 id만 받는다. ZIP은 부모·자식 중복 선택을 제거하며 이름 충돌은 거부한다. 파일 응답은 Cache-Control: no-store 및 UTF-8 attachment 파일명을 사용한다.

실패는 공통 `{message:String}` 응답. 400 입력/경로/루트 변경/하위 폴더 자기 이동/ZIP 선택량 오류, 403 파일 접근 권한 부족, 404 존재하지 않는 파일·폴더·휴지통 항목, 409 이름 충돌·텍스트 revision 충돌, 413 업로드/텍스트/재귀 10,000개 한도, 415 UTF-8 텍스트가 아닌 미리보기, 500 기타 디스크 I/O 실패. 다중 UI 작업은 항목별 성공/실패를 표시하며 전체 원자적 batch API는 없다. 자세한 파일 보존·삭제·용량 제한은 [클라우드 드라이브](../cloud-drive.md)를 따른다.

## NAS / WebDAV

GET /api/v1/cloud/nas 응답: enabled(boolean), path(/dav/), publicUrl(string, 미설정 시 빈 문자열), username(string). 비밀번호는 반환하지 않는다. 기존 OWNER 세션이 필요하다.

/dav/**는 별도 무상태 OWNER Basic 인증으로 동작하며 인증 실패는 401과 WWW-Authenticate 헤더를 반환한다. 메서드, 상태 코드와 잠금·조건부 저장 제약은 [NAS 계약](../nas.md)을 따른다. 기존 JSON API와 달리 WebDAV XML/파일 응답을 사용한다.

## 원격 데스크톱 자동 구성

POST /api/v1/devices/{id}/remote-setup: id는 필수 String 장비 ID. 본문 없음. OWNER 세션과 CSRF 필수. 202 응답으로 구성 시작 또는 동일 장비의 진행 중 작업 반환. GET은 같은 경로에서 OWNER 세션으로 상태를 조회하며 본문 없이 200을 반환한다.

응답 DesktopSetupView: state(String, 필수/null 불가, IDLE/RUNNING/READY/BLOCKED), message(String, 필수/null 불가, 사용자 안내). IDLE은 아직 작업 없음, RUNNING은 검사·설치·검증 중, READY는 연결 검증 성공, BLOCKED는 조치 후 재시도 필요. 작업 상세 실패는 BLOCKED의 message로 제공하고 명령 출력이나 자격증명은 반환하지 않는다.

공통 오류: 400 기본 local 장비 설치 요청, 401 미인증, 403 권한/CSRF 실패, 404 장비 없음, 429 동시 작업 4개 초과. 오류 본문은 기존 message 형식이다. 설치·저장 위치·지원 OS·포트·수명은 [원격 자동 구성](../remote-desktop.md)을 따른다. SQLite schema 변경 없음.

## 메모장 API

모든 경로는 OWNER 세션이 필수이고 POST/PUT/DELETE는 기존 CSRF 헤더가 필요하다. ID는 서버 생성 UUID다. 목록 API는 문서 본문과 이미지 바이너리를 제외한 메타데이터를 반환한다. 성공 JSON은 아래 계약을 따르며 실패는 공통 `{message: string}`이다. 코드 필드는 없으며 HTTP status로 구분한다. 인증 실패 401, OWNER/CSRF 실패 403은 공통 정책이다.

### 모델

- Entry: `id` string 필수 non-null(UUID), `parentId` string|null 필수(최상위는 null, 값이 있으면 FOLDER ID), `kind` string 필수 non-null(FOLDER/DOCUMENT), `title` string 필수 non-null(공백 아닌 1~200자), `icon` string 필수 non-null(0~16 UTF-16 code units, 이모지 또는 빈 문자열), `revision` integer 필수 non-null(0부터 증가), `createdAt`/`updatedAt` integer 필수 non-null(epoch milliseconds).
- Document: `entry` Entry 필수 non-null, `blocks` Block[] 필수 non-null. 폴더의 blocks는 빈 배열이다.
- Create: `kind` FOLDER/DOCUMENT 필수, `parentId` string|null 선택(생략/null은 최상위, 최대 36자), `title` string 필수 1~200자 non-blank, `icon` string 필수 0~16자, `blocks` Block[] 필수. parentId 외 null은 허용하지 않는다.
- Metadata: `parentId`, `title`, `icon`은 Create와 동일. `revision` integer 필수 non-null, 0 이상이며 마지막 읽기/저장에서 받은 값이다. 종류는 변경할 수 없다.
- Content: `blocks` Block[] 필수 non-null, `revision` integer 필수 non-null 0 이상.
- ImageView: `id` string 필수 non-null(UUID), `url` string 필수 non-null(`/api/v1/notes/images/{id}` 상대 주소). 원래 파일명이나 물리 경로는 포함하지 않는다.

Block 배열은 BlockNote 0.54.2의 JSON 문서다. 전체 UTF-8 직렬화 2 MiB 이하, 최대 2,000블록, children 중첩 16단계다. `type` string 필수는 paragraph/heading/bulletListItem/numberedListItem/checkListItem/toggleListItem/quote/codeBlock/divider/image/table 중 하나다. `id` string 선택(에디터가 생성), `props` object 선택(생략 시 블록 기본값), `children` Block[] 선택(생략 시 자식 없음), `content`는 본문 종류에 따라 선택한다.

인라인 본문은 string 또는 text/link 객체 배열이다. text는 `{type:'text',text:string,styles:object}`, link는 `{type:'link',href:string,content:인라인 배열}`이다. styles는 bold/italic/underline/strike/code boolean, textColor/backgroundColor string을 사용한다. heading.props.level은 1~6, checkListItem.props.checked는 boolean, image.props는 url/name/caption string과 showPreview boolean, previewWidth number를 사용한다. 표 본문은 `{type:'tableContent',rows:[{cells:[인라인 배열 또는 tableCell 객체]}]}`이고 tableCell은 `{type:'tableCell',content:인라인 배열,props:object}`다. 셀 속성은 색상·정렬·행/열 병합 값이며 기본값은 에디터가 관리한다. 코드의 props.language는 언어 이름 문자열이다. 번호 목록의 start는 양의 정수이며 기본값은 1이다. 색상·정렬·접기 등 부가 속성은 고정된 에디터 schema로 해석한다. HTML 원문은 실행하지 않고 블록 텍스트로 처리한다.

`url`은 빈 문자열(미첨부 이미지), HTTPS 이미지 URL 또는 해당 문서가 소유한 첨부 상대 URL만 허용한다. `href` 링크는 HTTP(S)/mailto 또는 해당 문서 첨부 URL을 허용한다. 외부 이미지는 브라우저에서 직접 읽으며 서버가 가져오지 않는다. data/javascript/file/프로토콜 상대 URL은 거부한다. 서버는 자격증명이 포함된 HTTP(S) 주소를 거부한다.

### 엔드포인트

| Method / URL | Path / Query | Request body | 성공 응답 |
| --- | --- | --- | --- |
| GET /api/v1/notes | 없음 | 없음 | 200 Entry[], 비어 있으면 [] |
| POST /api/v1/notes | 없음 | Create JSON | 201 Document, revision=0 |
| GET /api/v1/notes/{id} | id: 항목 UUID 필수 | 없음 | 200 Document |
| PUT /api/v1/notes/{id} | id: 항목 UUID 필수 | Metadata JSON | 200 Entry, revision+1 |
| PUT /api/v1/notes/{id}/content | id: DOCUMENT UUID 필수 | Content JSON | 200 Entry, revision+1 |
| DELETE /api/v1/notes/{id} | id 필수, query revision: integer 필수 | 없음 | 204 body 없음 |
| POST /api/v1/notes/{id}/images | id: DOCUMENT UUID 필수 | multipart `file`: binary 필수 non-empty | 201 ImageView |
| GET /api/v1/notes/images/{id} | id: 이미지 UUID 필수 | 없음 | 200 PNG/JPEG/GIF/WebP binary, Cache-Control:no-store, X-Content-Type-Options:nosniff |

다른 query/body 필드는 요구하지 않는다. 문서·폴더는 총 5,000개까지 생성할 수 있다. 상위 폴더 chain을 검사해 자기 자신·자손·DOCUMENT를 상위 폴더로 지정할 수 없고 최대 32단계까지 허용한다. 이미지 업로드는 파일별 10 MiB까지이며 Content-Type 주장 대신 바이트 서명으로 형식을 판별한다. 업로드만으로 문서 revision은 증가하지 않는다. 첨부 URL을 포함하는 본문 저장은 별도 요청이다.

삭제 시 DOCUMENT의 이미지 FK는 cascade하지만 폴더의 자식은 RESTRICT한다. 비어 있지 않은 폴더 삭제는 409이며 먼저 항목을 이동/삭제해야 한다. 첨부 블록 제거만으로 이미지를 지우지 않는다. 이미지는 문서 삭제 때 함께 제거된다.

### 오류

| Status | 조건 | message 예시 |
| --- | --- | --- |
| 400 | 필수값/타입/범위 오류, 폴더 본문, 잘못된 블록·URL·순환 이동 | 입력 형식과 필수 항목을 확인해 주세요. / 지원하지 않는 블록 형식입니다. / 자기 자신이나 하위 폴더로 이동할 수 없습니다. |
| 404 | 항목/상위 폴더/이미지가 없음 | 문서 또는 폴더를 찾을 수 없습니다. / 이미지를 찾을 수 없습니다. |
| 409 | revision 불일치, 비어 있지 않은 폴더 삭제, 총 개수 초과 | 다른 창에서 변경되었습니다. 내용을 보관한 뒤 최신 문서를 다시 여세요. / 폴더 안의 문서와 하위 폴더를 먼저 이동하거나 삭제하세요. |
| 413 | 본문 2 MiB 또는 이미지 10 MiB 초과, 빈 이미지 | 문서는 2 MiB 이하여야 합니다. / 이미지는 0바이트 초과, 10 MiB 이하여야 합니다. |
| 415 | 허용하지 않는 이미지 형식 | PNG, JPEG, GIF, WebP 이미지만 첨부할 수 있습니다. |
| 500 | DB/저장 실패 등 예기치 않은 오류 | 요청 처리에 실패했습니다. 설정과 연결 상태를 확인해 주세요. |

명시적 revision 비교와 조건부 UPDATE/DELETE로 오래된 쓰기를 거부한다. 자동 덮어쓰기·서버 측 재시도는 없다. API에 사용자별 ownerId를 받지 않으며 기존 단일 OWNER 계정의 비공개 저장소다.
