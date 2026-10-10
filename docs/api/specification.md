# HTTP/API 계약

## 장비 Codex API

공통 접두사는 `/api/v1/devices/{deviceId}/codex/jobs`다. 모든 요청에 OWNER 세션을 적용하고 변경 요청에는 CSRF를 적용한다. `deviceId`는 등록된 SSH 장비의 문자열 ID이며 `local`은 허용하지 않는다. Query parameter는 없다. `{id}`는 시작 응답의 job ID다. 같은 로그인 세션과 같은 장비의 작업만 조회·제어할 수 있다. IDE/AI 비서 API에서는 장비 job을 조회할 수 없다.

| Method/하위 경로 | Request | Response/상태 |
| --- | --- | --- |
| POST `/` | `DeviceCodexDto.Request` | `StudioDto.JobView`, 202 |
| GET `/{id}` | 없음 | `StudioDto.JobView`, 200 |
| POST `/{id}/inputs` | 기존 `AssistantDto.Control` (approval/answer/steer/interrupt) | 본문 없음, 204 |
| DELETE `/{id}` | 없음 | 본문 없음, 204 |

시작 본문은 `root`(필수 non-null string, 절대 폴더 경로, 최대 4096자), `action`(필수 non-null string, 최대 40자), `args`(선택 nullable object, 기존 StudioDto.Args 계약)를 받는다. 장비 ID는 route로만 결정한다. 허용 action은 `setup`, `github-login`, `github-status`, `codex-status`, `codex-login`, `codex-logout`, `codex-run`, `codex-models`, `codex-threads`, `codex-thread-read`, `codex-thread-new`, `codex-thread-rename`, `codex-thread-archive`, `codex-thread-delete`, `codex-thread-unarchive`, `codex-thread-fork`, `codex-thread-compact`, `codex-thread-rollback`, `codex-skills`, `codex-connections`, `codex-account`, `codex-rate-limits`, `codex-review`다. `setup.args.refresh=true`는 release 캐시를 갱신한다. `codex-run.args.mode`는 선택 문자열 `read-only`(기본), `workspace-write`, `danger-full-access`; `args.approval`은 선택 문자열 `on-request`(기본), `never`; `args.reviewer`는 선택 문자열 `user`(기본), `auto_review`다. 세 필드의 null/생략은 기본값을 사용하며 새 대화와 재개 대화의 다음 실행에 적용한다. `auto_review`와 `never` 조합 및 알 수 없는 값은 400이다. 자동 심사는 샌드박스나 SSH 계정 권한을 넓히지 않는다. AI 비서는 `read-only`와 `on-request`를 유지하며 reviewer만 선택할 수 있다. [Studio API](studio.md#codex-app-server)에 상세 동작을 정의한다.

JobView: `id/action/state`(필수 non-null string), `events`(필수 non-null Event 배열), `result`(선택 nullable StudioDto.Result), `error`(필수 non-null string, 정상 시 빈 값), `errorStatus`(필수 int, 정상 시 0). state는 `RUNNING/SUCCEEDED/FAILED/CANCELLED`. 계정 result.assistant의 `authenticated`는 boolean, `email/accountType/plan`은 선택 nullable string이다. 키·토큰은 반환하지 않는다. setup result는 git/codex 버전 문자열을 제공한다. URL/일회용 코드는 로그인 중 Event의 선택 `url/code`로만 전달한다.

공통 오류 응답을 사용한다. 시작 검증 400(지원하지 않는 action, local 대상, 상대 root), 404(장비 없음), 413(첨부 4 MB 초과), 429(동시 실행 4개 초과). 조회·제어 404(작업 없음·다른 세션·다른 장비·다른 작업 범위), 입력 409(실행 중이 아닌 작업·연결 준비 전). 비인증/권한/CSRF 오류는 기존 보안 계약의 401/403을 따른다. 비동기 SSH·설치 실패는 조회 HTTP 200의 FAILED job과 `errorStatus=502`, 폴더·모드 오류는 400, 폴더 잠금은 409, Codex 인증 만료는 401로 표현한다. 사용자 오류 메시지는 SSH 연결·도구 설치·경로·로그인 재시도 안내이며 자격 증명은 포함하지 않는다. 취소는 이미 적용된 변경을 복구하지 않는다.

## Workspace Memory API

OWNER 세션과 CSRF를 적용한다. `GET /api/v1/assistant/memories`는 `query/status/type/confidence/scope/serviceId/offset/limit` 서버 필터와 `items/nextOffset/hasMore`를 반환한다(최대 50건). `GET /{id}`, `POST /`, `PUT /{id}`, `DELETE /{id}`로 조회·생성·수정·삭제한다. 본문의 `content/type/confidence/scope`가 필수이며 Service 범위는 유효한 `relatedServiceId`, Project 범위는 `relatedProject`가 필요하다. `POST /{id}/archive`, `/restore`, `/pin`, `DELETE /{id}/pin`, `POST /{id}/supersede`, `/promote/calendar`, `/promote/note`, `POST /promotions/note`는 상태와 대상 연결을 변경한다. `GET/PUT /preferences`는 정리 설정을 읽고 변경한다. 잘못된 입력은 400, 미존재는 404, 승인 누락은 403, 인증 실패는 401이다. MCP는 동일 service를 호출하며 별도 bearer 인증을 요구한다.

## 공통

Telemetry 수집 경로는 아래 OWNER 세션 공통 규칙 대신 service API Key Bearer 인증을 사용한다. OWNER의 로그인 인증과 분리하며 CSRF가 없다. service 관리 경로는 OWNER 인증과 기존 CSRF를 그대로 적용한다.

별도 명시가 없으면 `/api/v1/*`는 OWNER 인증이 필요하고 JSON 요청/응답은 UTF-8 `application/json`이다.
POST/PUT/PATCH/DELETE는 로그인 HTML의 `csrf-token`과 `csrf-header` meta를 사용한 헤더가 필수다. 세션 쿠키도 함께 전송한다.
각 경로의 `{id}` 또는 `{device}`는 필수 non-null String ID다. ID는 서버 생성 UUID이며 기본 장비만 `local`이다.
아래 표에 없는 query/path/body는 사용하지 않는다. 빈 response body인 201/202/204는 Content-Type을 보장하지 않는다.
JSON 필드는 아래에 별도 optional/null 표기가 없으면 응답에서 필수·null 불가다. 빈 배열은 허용한다. boolean/int 요청 필드는 생략 시 Java 기본값(false/0)이 적용되며 최소값 제약을 검사한다.
에러는 가능한 경우 `{message: String}`를 반환한다. 인증/CSRF 등 보안 필터와 프레임워크 기본 오류는 이 JSON 형식을 보장하지 않는다.

## Service Telemetry

### OWNER service management

Base path: `/api/v1/telemetry/services`. All routes below require OWNER session authentication; state-changing routes require the dashboard CSRF token.

| Method and path | Request | Success |
| --- | --- | --- |
| GET `/` | none | `200 Summary[]`; request/user totals, status, nullable latency and latest custom gauges |
| POST `/` | `{name: string(1..100), description?: string(0..500), serviceType: Backend API\|Web\|Discord Bot\|Worker\|Custom}` | `201 ServiceView`; includes `apiKey` once |
| GET `/{id}` | none | `200 ServiceView`; `apiKey` omitted |
| DELETE `/{id}` | none | `204`; disable service and retain samples |
| PUT `/{id}/enabled` | `{enabled: boolean}` | `200 ServiceView` |
| POST `/{id}/key` | empty or omitted body | `200 ServiceView`; newly generated `apiKey` once, prior key invalidated |
| DELETE `/{id}/key` | none | `204`; current key invalidated |
| GET `/{id}/analytics?range=1h\|24h\|7d\|30d` | `range` defaults to `24h` | `200 Analytics`; timeline, endpoint counts/error rates, methods, statuses, percentile latencies and user/gauge summary |

`ServiceView` fields: `serviceId`, `serviceName`, `description`, `serviceType`, `createdAt` (epoch ms), `enabled`, nullable `lastUsedAt`, `status` (`Receiving data`, `Idle`, `No recent telemetry`, `Disabled`), and `apiKey` only on create/regenerate. Summary additionally includes selected-range `requests`, rolling `requestsLastMinute`, `requestsLastHour`, `requestsToday`, error/latency values, unique users, DAU/WAU/MAU, peak concurrent users when `active_users` or `concurrent_users` gauges exist, and latest gauge map.

### External service ingestion

These endpoints use `Authorization: Bearer dash_sk_<32 base64url characters>`, with no OWNER cookie and no CSRF. A revoked, replaced, disabled, malformed or unknown key returns `401`. Accepted timestamps must be within the last 30 days and no more than five minutes in the future. Body limit is 64 KiB; limit is 120 requests per service per minute.

| Method and path | Request fields | Success |
| --- | --- | --- |
| POST `/api/v1/telemetry/events` | `type` required (1..40 chars), optional ISO-8601 `timestamp`, optional `anonymousUserId` (1..128 chars, only for `user_activity`), optional `properties` with at most 24 scalar fields and 4 KiB serialized | `200 {events: 1, gauges: 0}` |
| POST `/api/v1/telemetry/gauges` | `name` required (1..64 chars matching `[A-Za-z][A-Za-z0-9_.-]*`), `value` required finite number 0..1e12, optional ISO-8601 `timestamp` | `200 {events: 0, gauges: 1}` |
| POST `/api/v1/telemetry/batch` | `events?` and `gauges?`, each max 100 entries; at least one total entry | `200 {events: number, gauges: number}` |

For type `request`, the recommended properties are `endpoint`, `method`, `status` (100..599), and `latencyMs` (non-negative). Request statuses 400..599 count as errors. Type `error` increments error totals. Arbitrary service event types and gauge names are accepted within these bounds. Invalid body/timestamp returns `400`, oversized body returns `413`, and the per-service rate limit returns `429`. Missing measurements are `null`, never zero.

| HTTP | 분류 | 발생 조건/안내 |
| --- | --- | --- |
| 400 | INVALID_INPUT | 필수 항목, enum, 범위, URL, 경로 오류. 입력 형식과 필수 항목 확인 |
| 401 | UNAUTHENTICATED | API/WS 세션 없음 또는 만료. 로그인 필요 |
| 403 | FORBIDDEN | OWNER 권한 없음, CSRF/같은 origin 실패, 파일 루트 탈출 |
| 404 | NOT_FOUND | 장비/앱/실행 핸들 없음 또는 다른 로그인에 속한 핸들 |
| 409 | CONFLICT | 파일 누락/권한/중복/비어 있지 않은 폴더, 실행 제한/중복 attach |
| 413 | PAYLOAD_TOO_LARGE | multipart 또는 Telemetry body 크기 제한 초과 |
| 429 | RATE_LIMITED | 서비스별 Telemetry 분당 요청 한도 초과 |
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

장비 추가·수정 시 호스트 키를 새로 확인하는 연결도 요청의 점프 체인(생략/null이면 기존 체인)을 사용한다. 각 장비의 `networkMode`를 독립 적용하므로 TAILSCALE 브릿지를 거쳐 DIRECT 목표에 연결할 수 있다. DIRECT 목표의 내부 호스트명은 브릿지에서 해석하며 목표 자체에는 Tailscale이 필요하지 않다.

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

관련 조회: `GET /api/v1/sessions?deviceId=...&root=...`는 OWNER 로그인 세션이 소유한 해당 장비·정확한 프로젝트 root의 유지 중인 Studio 터미널만 반환한다. 두 query는 필수 string이며 누락 시 400이다. 응답 200은 배열이며 각 항목의 `id`, `deviceId`, `root`는 필수 non-null string, `attached`는 현재 WebSocket 연결 여부인 필수 boolean이다. 일치하는 세션이 없으면 빈 배열이다. 다른 로그인 세션의 핸들이나 출력은 노출하지 않는다. 이 GET은 셸을 생성하거나 기존 연결을 빼앗지 않는다. 인증·권한 오류는 공통 로그인/OWNER 정책을 따른다.
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
- TERMINAL client->server JSON: `{type:"input",data:String}` 또는 `{type:"resize",columns:int,rows:int}`. columns는 20~300, rows는 5~120으로 clamp한다. 서버->client는 셸 연결과 입력 바인딩 완료 후 `{type:"ready"}` 텍스트 프레임을 먼저 보내고, 이후 UTF-8 터미널 출력을 보낸다. 클라이언트는 ready 수신 후 입력과 크기 변경을 전송한다.
- REMOTE/APP 양방향은 Guacamole 1.6 프로토콜 instruction 문자열이다. 초기 빈 opcode는 터널 UUID, 내부 ping은 echo한다.
- 한 메시지 최대 64KiB, 송신 buffer 1MiB, 10초 송신 제한. 초기 생성 요청의 viewport와 remote resize/scale에 따라 화면을 표시한다.
- 연결/프로토콜 실패는 WS 1011과 연결 유형별 안전한 안내 문구, 정상 셸 종료는 WS 1000과 종료 안내 문구를 보낸다. 계정/접속정보를 close reason에 포함하지 않는다. 핸드셰이크 실패로 close reason이 없으면 클라이언트는 로그인과 프록시 WebSocket 설정을 확인하도록 안내한다.
- 일반 실행 세션은 소켓 종료·DELETE·로그아웃·로그인 만료·서버 종료 시 정리하며 새 POST로 재연결한다. `root`가 지정된 Studio TERMINAL은 소켓 단절 후 30분 동안 같은 PTY를 유지한다. 같은 로그인 소유자가 기존 `/ws/runtime/{id}`로 재연결하면 ready 뒤 최근 65,536자 출력을 재생한다. 동시 중복 연결은 거부한다. DELETE·셸 종료·로그아웃·서버 종료·유예 만료 시 종료하며, 다른 로그인 세션은 핸들을 재사용할 수 없다.

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
| networkMode | NetworkMode enum | 선택/null 허용 | DIRECT 또는 TAILSCALE. 생략/null이면 기존 장비 값 유지, 신규는 DIRECT. 목표 장비에 적용하며 점프 장비는 각각 저장된 모드 사용 |
| jumpDeviceIds | String[] | 선택/null 허용 | 순서대로 연결할 등록 장비 ID, 최대 5개. 생략/null이면 기존 체인 유지, 신규는 빈 체인. 빈 배열이면 직접 연결 |

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
응답 필드는 아래 명시한 예외 외에는 필수, null 불가. 배열은 빈 배열 가능(meetings는 최소 1개). EventView: id String(일반 일정 UUID, 병역 일정 `military:<원본 ID 또는 milestone ID>`), EventRequest와 동일한 title/start/end/allDay/location/notes/color, source String enum `CALENDAR`/`MILITARY`, sourceId nullable String(병역 사용자 일정 원본 UUID, 일반 일정과 병역 milestone은 null). 병역 연동을 켜면 조회 결과에 종일 병역 일정을 포함하며 end는 종료일 다음 날 00:00이다. `military:` ID를 일반 캘린더 PUT/DELETE에 사용하면 409이며 병역 API에서 수정한다. TermView: id String UUID 및 TermRequest와 동일한 name/start/end. MeetingView: day int, start/end LocalTime. CourseView: id String UUID, termId String UUID, title/professor/location String, credits int, color/notes String, meetings MeetingView[]. TimetableView: term TermView, courses CourseView[], totalCredits int(과목별 credits 합계). 일반 일정끼리 겹침은 허용하고, 수업은 같은 학기의 같은 요일 [start,end) 구간끼리 겹치면 거부한다. 학기 기간은 시간표 구분용이며 캘린더 이벤트를 자동 생성하지 않는다.

## SSH 코드 에디터

[Studio API 계약](studio.md)은 비동기 작업의 입력·결과·실패 상태와 소유권을 정의한다. 파일/Git 작업은 서버 자체(local, Docker 컨테이너) 또는 등록 SSH 서버에서 수행한다. Codex 작업은 등록 SSH 서버에서만 허용되며 선택한 SSH 계정의 Codex CLI와 인증을 사용한다. `local` Codex 요청은 job을 만들기 전에 HTTP 400으로 거절한다.

## Codex App Server 입력

POST `/api/v1/studio/jobs/{id}/inputs`: OWNER와 생성한 HTTP 세션만 접근하고 CSRF가 필요하다. 성공 204, 다른 세션/미존재 404, 비실행 작업 409, 검증 오류 400. 요청·이벤트·세션 계약은 [Studio API](studio.md#codex-app-server)를 따른다.

## 장비 네트워크 옵션

DeviceRequest, SshDeviceRequest에 networkMode enum DIRECT/TAILSCALE을 추가한다. DeviceView에도 필수 networkMode를 반환한다. 기존 엔드포인트와 OWNER/CSRF 요건은 유지한다. 새 장비에서 생략/null은 DIRECT, 기존 장비 수정 및 같은 SSH 프로필 재등록에서 생략/null은 저장된 설정을 유지한다. 잘못된 enum은 400이다. host 또는 SSH 명령어의 호스트에 선택한 네트워크의 주소를 입력한다. TAILSCALE은 로그인된 tailscale0 인터페이스가 없거나 DNS 실패/시간 초과이면 502, tailnet 범위 주소가 없으면 400으로 일반 연결 전에 차단한다. 상태 조회는 기존 UNAVAILABLE 형태로 반환한다. TAILSCALE 장비의 Wake는 400이며 패킷을 보내지 않는다.

## Tailscale 관리

GET `/api/v1/tailscale`, POST `/api/v1/tailscale/login`, DELETE `/api/v1/tailscale/login`은 OWNER 전용이며 변경 요청은 CSRF가 필요하다. path/query/body 매개변수는 없다. 각각 상태 조회/로그인 시작/로그아웃이며 성공은 200, Cache-Control: no-store, TailscaleView JSON을 반환한다. 로그인은 비동기이고 POST는 대기를 완료하지 않은 상태를 반환할 수 있다. 로그인 진행 중 재요청은 기존 프로세스를 유지하고 Running이면 새 인증을 시작하지 않는다.

응답 필드는 모두 필수 non-null: state String(상위 BackendState, 주요 값 Running/NeedsLogin/NeedsMachineAuth/Stopped/Starting/NoState/InUseOtherUser/Unknown; 알 수 없는 상태도 그대로 표시), hostname String(없으면 빈 문자열), ips String[](없으면 빈 배열), loginUrl String(로그인 링크가 준비되지 않았거나 연결됨이면 빈 문자열; 공식 https://login.tailscale.com/a/ 주소만 허용), pending boolean(Running이 아니면서 로그인 CLI가 실행 중인지 여부), error String(실패 안내 또는 빈 문자열; Running이면 빈 문자열). 사용자/peer 목록과 키는 반환하지 않는다.

실패는 기존 WorkspaceException 응답을 따른다: 미인증 401, 권한/CSRF 403, 브리지 데몬 오류·25초 응답 제한·중단 502, 토큰 파일 미준비 또는 내부 연결 불가 503. 내부 CLI status/logout 제한은 8초, login은 최대 5분이며 이후 실패 안내와 빈 링크를 반환한다. 데이터베이스 변경은 없으며 Tailscale 인증 상태는 기존 sidecar 상태 볼륨에 보존한다. [상세 동작](../tailscale.md).

## 클라우드 저장소 API

모든 엔드포인트는 OWNER 전용(미인증 401, 권한 부족 403), 변경 요청은 CSRF 필수(누락/불일치 403). 파일은 대시보드 서버의 영속 CLOUD_ROOT에 저장한다. `files/`는 별도 SMB NAS 계정에도 노출되며 HTTP와 SMB는 각각의 인증 경계를 사용한다. `{path}`는 OS 실제 경로가 아닌 `/`로 시작하는 가상 경로이며 최대 4096자, 단일 이름 최대 255자, `..`/`.`/역슬래시/제어 문자/심볼릭 링크는 허용하지 않는다. 디렉토리 루트 자체는 생성·이동·삭제할 수 없다. 아래에 기재하지 않은 body/query/path 매개변수는 사용하지 않는다.

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

## NAS / SMB 연결 정보

GET /api/v1/cloud/nas는 OWNER 세션으로 접근한다. 200 응답 필드는 `host`(string, 필수, 설정된 접속 주소. 미설정이면 빈 문자열), `port`(integer, 필수, 445), `share`(string, 필수, `storage`), `username`(string, 필수, SMB 사용자 이름)이다. 비밀번호는 반환하지 않는다. 미인증은 401, OWNER 권한이 없으면 403이며 기존 공통 `{message: string}` 형식을 사용한다. 파일 전송은 이 HTTP API가 아닌 Samba SMB3 서비스에서 수행한다. [접속과 운영 안내](../nas.md).

## 원격 데스크톱 자동 구성

접두사 `/api/v1/devices/{id}/remote-setup`. id는 필수 non-null 장비 ID string, query parameter는 없다. OWNER 로그인 필수이며 POST/PUT에는 CSRF가 필요하다. raw SSH 출력·자격증명은 응답하지 않는다.

| Method / 하위 경로 | 요청 | 응답 |
| --- | --- | --- |
| GET `/` | 없음 | 200 DesktopSetupView |
| GET `/plan` | 없음, 읽기 전용 | 200 DesktopSetupPlan |
| POST `/` | 선택 JSON DesktopSetupRequest, 생략/{} 가능 | 202 DesktopSetupView, 동일 장비의 실행 중 작업은 재사용 |
| PUT `/connection` | 필수 JSON DesktopConnectionRequest | 204, 원격 연결 필드만 저장, SSH·점프·다른 설정은 보존 |

DesktopSetupRequest: `sudoPassword`는 선택 nullable string, 최대 4096자, CR/LF 불가. null/빈 값/생략은 root 또는 기존 비대화형 sudo만 사용한다. 입력값은 이번 설치에만 쓰며 저장하거나 응답하지 않는다.

DesktopConnectionRequest: `protocol`은 필수 non-null string RDP/VNC, `port`는 필수 integer 1..65535, `username`은 선택 nullable string 최대 128자(null은 빈 계정), `password`는 선택 nullable string 최대 4096자(null/빈 값은 기존 암호 유지). 비밀번호는 write-only이며 저장소 암호화를 사용한다. 이 API는 대상 OS의 RDP/VNC 서비스나 방화벽을 설정하지 않는다. 진행 중 설치가 있으면 409다.

DesktopSetupView의 모든 필드는 non-null이다. `state` string은 IDLE/RUNNING/READY/BLOCKED, `message` string은 사용자 안내, `stage` string은 IDLE/CHECKING/INSTALLING/STARTING/VERIFYING/READY/BLOCKED, `code` string은 정상 시 빈 값이고 실패 시 복구 분류다. code는 ADMIN_REQUIRED, NO_SUDO, MACOS, UNSUPPORTED_OS, UNSUPPORTED_PACKAGES, PYTHON_REQUIRED, EXISTING_VNC, NO_PORT, BUSY, TIMEOUT, COMMAND_FAILED, PASSWORD_FAILED, START_FAILED, DESKTOP_FAILED, UNSAFE_STATE, INVALID_SECRET, SETUP_FAILED, CONNECTION_FAILED 중 하나 또는 미분류 오류의 빈 값이다. RUNNING 동안 단계가 진행하고 READY는 guacd 핸드셰이크까지 성공한 경우만 반환한다. 새 자동 프로필은 검증 후 저장한다.

DesktopSetupPlan의 모든 필드는 non-null이다. `mode` string은 EXISTING(기존 설정), MANAGED(관리 중인 가상 화면), LINUX(자동 준비), MANUAL(직접 연결 필요). `title/message/actionLabel`은 안내 string이며 사용 가능한 자동 동작이 없으면 actionLabel은 빈 값이다. `canStart` boolean은 자동 준비/연결 지원 여부, `requiresPassword` boolean은 설치에 sudo 비밀번호 입력이 필요한지 여부다. 사전 확인은 설치를 시작하지 않는다. 환경 변화가 있으므로 실제 POST에서 다시 확인한다.

공통 오류: 400 기본 local 장비 설치/설정 요청 및 입력 검증 실패, 401 미인증, 403 OWNER/CSRF 실패, 404 장비 없음, 409 준비 중 연결 설정 수정, 429 동시 작업 4개 초과, 502 사전 SSH 확인 실패. 오류 본문은 기존 `{message:string}`이며 설치 이후 실패는 HTTP 200 조회의 BLOCKED 상태로 안내한다. 설치·수명·지원 OS는 [원격 데스크톱](../remote-desktop.md)을 따른다. DB 스키마 변경 없음.

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

GET `/api/v1/notes/{id}`가 이전 MCP 문단 형태의 Markdown을 처음 읽으면, 검증된 블록 변환을 조건부 저장하고 증가된 revision을 반환할 수 있다. 일반 문장·이미 편집된 블록·검증 불가 원문은 유지한다. 다른 쓰기가 먼저 반영되면 그 최신 문서를 반환한다.

## GitHub API

모든 경로는 OWNER 세션이 필요하다. POST는 CSRF도 필요하다. 미인증 401, 권한 부족 403, 잘못된 입력 400, 서버 `gh` 미인증 409, upstream/CLI 실패 502 또는 503, 30초 초과 504이다. CLI 원문 오류·토큰은 반환하지 않는다. `repository` query는 필수 `owner/name`(segment당 1~100자), `owner` path는 1~39자 GitHub login, `number`/`runId`는 양의 정수다. 모든 GET의 body는 없다. 선택 필드는 아래 표에서 `?`로 표시하고 없으면 null이다. 목록은 첫 페이지 상한이며 전체 개수로 해석하지 않는다.

| Method / path (모두 `/api/v1/github` 아래) | 추가 입력 | 성공 응답 |
| --- | --- | --- |
| GET `/status` | 없음 | 200 `{authenticated: boolean}` |
| GET `/repositories` | 없음 | 200 `Repository[]` 최대 50, 기존 개인 저장소 경로 |
| GET `/issues`, `/pull-requests` | `repository` query | 200 `Issue[]` 또는 `PullRequest[]` 최대 50, 열린 항목 |
| GET `/owners`, `/organizations` | 없음 | 200 `Owner[]`; 전자는 USER와 ORGANIZATION, 후자는 ORGANIZATION만 |
| GET `/organizations/{owner}` | 없음 | 200 `Organization` |
| GET `/organizations/{owner}/members`, `/teams` | 없음 | 200 `Member[]` 또는 `Team[]`, 첫 100개 |
| GET `/owners/{owner}/repositories` | 없음 | 200 `Repository[]` 최대 100 |
| POST `/owners/{owner}/repositories` | `{name: string(1..100), description?: string(≤1000), isPrivate: boolean}` | 201 `RepositoryDetail` |
| GET `/owners/{owner}/overview` | 없음 | 200 `OwnerOverview` |
| GET `/owners/{owner}/activity` | 없음 | 200 `Activity[]` 최근 최대 30개 |
| GET `/owners/{owner}/issues` | `state`: open/closed/all 기본 open, `role`: all/assigned/created/mentioned 기본 all, `repository?`: 해당 Owner의 owner/name, `label?`: ≤100자 | 200 `Issue[]`, Search 첫 100개 |
| GET `/owners/{owner}/pull-requests` | `state`: open/closed/all 기본 open, `repository?`: 해당 Owner의 owner/name | 200 `PullRequest[]`, Search 첫 100개 |
| GET `/owners/{owner}/my-work` | 없음 | 200 `MyWork` |
| GET `/owners/{owner}/search` | `query`: 필수 1~100자, 따옴표·역슬래시 불가 | 200 `SearchResult` |
| GET `/repositories/detail` | `repository` | 200 `RepositoryDetail` |
| PATCH `/repositories` | `repository`; `{description?: string(≤1000), homepage?: http(s) URL(≤2000), topics?: string[](≤20)}` 최소 한 필드 | 200 `RepositoryDetail` |
| DELETE `/repositories` | `repository`, `approvalId`: 승인 UUID | 204; 같은 저장소의 `DELETE_REPOSITORY` 브라우저 승인 1회 소비 |
| GET `/repositories/branches`, `/tags`, `/contributors` | `repository` | 200 `Branch[]`/`Tag[]`/`Contributor[]`, 첫 100개 |
| GET `/repositories/languages` | `repository` | 200 `{언어명: int 바이트}` |
| GET `/repositories/tree` | `repository`, `ref`: 필수 브랜치/태그/커밋 1~200자 | 200 `FileEntry[]`; 응답 1 MiB 초과는 502 |
| GET `/repositories/file` | `repository`, `path`: 필수 상대 경로 ≤500자, `ref` | 200 `FileContent`; 본문 최대 512 KiB |
| GET `/repositories/commits` | `repository` | 200 `Commit[]` 최근 30개 |
| GET `/repositories/commits/{sha}` | `repository`; `sha`: 7~40자리 16진수 | 200 `Commit` |
| GET `/repositories/commits/{sha}/files` | `repository`, `sha` | 200 `ChangedFile[]` |
| GET `/repositories/context` | `repository` | 200 `DevelopmentContext` |
| GET `/issues/detail` | `repository`, `number` | 200 `IssueDetail` |
| POST `/issues` | `repository`; `{title: string(1..256), body?: string(≤60000)}` | 201 `IssueDetail` |
| PATCH `/issues/{number}` | `repository`; `{title?: string(1..256), body?: string(≤60000), state?: open\|closed, labels?: string[](≤20), assignees?: string[](≤20), milestone?: int(≥1)}`; 최소 한 필드 | 200 `IssueDetail` |
| POST `/issues/{number}/comments` | `repository`; `{body: string(1..60000)}` | 201 `IssueDetail` |
| GET `/pull-requests/detail` | `repository`, `number` | 200 `PullRequestDetail` |
| GET `/pull-requests/files` | `repository`, `number` | 200 `ChangedFile[]` 최대 100 |
| GET `/pull-requests/context` | `repository`, `number` | 200 `PullRequestContext` |
| POST `/pull-requests` | `repository`; `{title: string(1..256), base: string(1..200), head: string(1..200), body?: string(≤60000), draft: boolean}` | 201 `PullRequestDetail` |
| PATCH `/pull-requests/{number}` | `repository`; `{title?: string(1..256), body?: string(≤60000), state?: open\|closed, base?: string(1..200)}`; 최소 한 필드 | 200 `PullRequestDetail` |
| POST `/pull-requests/{number}/reviews` | `repository`; `{event: COMMENT\|APPROVE\|REQUEST_CHANGES, body?: string(≤60000)}` | 201 `PullRequestDetail` |
| GET `/actions/workflows`, `/actions/runs` | `repository` | 200 `Workflow[]` 최대 100 또는 `WorkflowRun[]` 최근 30 |
| GET `/actions/runs/{runId}` | `repository` | 200 `WorkflowRun` |
| GET `/actions/runs/{runId}/jobs` | `repository` | 200 `WorkflowJob[]` 최대 100, 각 Job의 Steps 포함 |
| GET `/actions/runs/{runId}/artifacts` | `repository` | 200 `WorkflowArtifact[]` 최대 100개 metadata |
| GET `/actions/runs/{runId}/logs` | `repository` | 200 `{failedLogs: string}`; 실패 단계 로그 최대 1 MiB |
| GET `/actions/runs/{runId}/analysis` | `repository` | 200 `WorkflowAnalysis` |
| POST `/actions/runs/{runId}/rerun` | `repository`; body 없음 | 204 |
| POST `/actions/runs/{runId}/cancel` | `repository`; body 없음 | 202 |
| POST `/actions/workflows/{workflowId}/dispatches` | `repository`; `{ref: string(1..200), inputs?: object<string,string(≤1000)>}` 최대 25개 | 202 |
| GET `/releases` | `repository` | 200 `Release[]` 최근 30 |
| GET `/releases/{releaseId}` | `repository` | 200 `Release` 및 assets |
| PATCH `/releases/{releaseId}` | `repository`; `{tag?: string(1..100), name?: string(≤256), body?: string(≤60000)}` 최소 한 필드 | 200 `Release`; Release 참조 태그 변경, Git 태그 자체 이름 변경 아님 |
| DELETE `/releases/{releaseId}` | `repository`, `approvalId`: 승인 UUID | 204; 같은 Release의 `DELETE_RELEASE` 브라우저 승인 1회 소비, Git 태그 유지 |
| POST `/releases` | `repository`; `{tag: string(1..100), name?: string(≤256), body?: string(≤60000), draft: boolean, prerelease: boolean, generateNotes: boolean}` | 201 `Release` |
| GET `/approvals` | 없음 | 200 승인 전 `Approval[]` |
| POST `/approvals/{id}` | body 없음; 만료 전 승인 ID | 200 `Approval`; ID 없음/만료 404 |

`Owner={login:string,type:USER|ORGANIZATION,avatarUrl?:string,url?:string}`. `Organization={login:string,name?:string,description?:string,url?:string,publicRepositories:int,totalPrivateRepositories?:int}`. `Member={login:string,avatarUrl?:string,url?:string}`. `Team={name:string,slug:string,description?:string,url?:string,privacy?:string,permission?:string}`.

`Repository={nameWithOwner:string,description?:string,url:string,isPrivate:boolean,isArchived:boolean,isFork:boolean,updatedAt:string}`. `RepositoryDetail={fullName:string,description?:string,homepage?:string,url:string,isPrivate:boolean,isArchived:boolean,isFork:boolean,defaultBranch:string,topics:string[],language?:string,openIssues:int,updatedAt:string}`. `Branch={name:string,sha:string,isProtected:boolean}`. `Tag={name:string,sha:string}`. `Contributor={login:string,contributions:int,url?:string}`. `FileEntry={path:string,type:string,sha:string,size:long,url?:string}`. `FileContent={path:string,sha:string,size:long,encoding:"utf-8",content:string}`. `Commit={sha:string,message:string,author?:string,date?:string,url?:string}`. `ChangedFile={filename:string,status:string,additions:int,deletions:int,changes:int,patch?:string,url?:string}`.

`Issue={number:int,title:string,state:string,url:string,updatedAt:string}`. `PullRequest={number:int,title:string,state:string,url:string,updatedAt:string,isDraft:boolean}`. `IssueDetail={number:int,title:string,state:string,url:string,body?:string,author?:string,updatedAt:string,labels:string[],assignees:string[],milestone?:string}`. `PullRequestDetail={number:int,title:string,state:string,url:string,body?:string,author?:string,base:string,head:string,headSha:string,isDraft:boolean,mergeable?:boolean,updatedAt:string}`. `DiscussionComment={author:string,body:string,path?:string,url?:string,createdAt:string}`. `CheckRun={name:string,status:string,conclusion?:string,url?:string}`. `PullRequestContext={pullRequest:PullRequestDetail,files:ChangedFile[],commits:Commit[],conversation:DiscussionComment[],reviewComments:DiscussionComment[],checks:CheckRun[]}`. `Activity={type:string,actor?:string,repository?:string,action?:string,url?:string,createdAt:string}`. `OwnerOverview={owner:Owner,repositories:Repository[],openIssues:Issue[],openPullRequests:PullRequest[],memberCount?:int,recentActivity?:Activity[]}`; 회원·이벤트 권한 또는 외부 오류가 있으면 각 선택 필드는 null이다. `MyWork={assignedIssues:Issue[],reviewRequests:PullRequest[]}`. `SearchResult={issues:Issue[],pullRequests:PullRequest[]}`.

`Workflow={id:long,name:string,path:string,state:string,url?:string}`. `WorkflowRun={id:long,name?:string,status?:string,conclusion?:string,branch?:string,event?:string,commit?:string,url?:string,createdAt?:string,updatedAt?:string}`. `WorkflowStep={name:string,status?:string,conclusion?:string,number:int}`. `WorkflowJob={id:long,name:string,status?:string,conclusion?:string,url?:string,steps:WorkflowStep[]}`. `WorkflowArtifact={id:long,name:string,size:long,expired:boolean,createdAt?:string,expiresAt?:string}`. `WorkflowAnalysis={run:WorkflowRun,failedJobs:WorkflowJob[],failedLogs:string}`. `Release={id:long,name?:string,tag:string,body?:string,url?:string,isDraft:boolean,isPrerelease:boolean,publishedAt?:string,assets:ReleaseAsset[]}`. `ReleaseAsset={name:string,size:long,contentType?:string,downloadUrl?:string}`. `DevelopmentContext={repository:RepositoryDetail,branches:Branch[],recentCommits:Commit[],openPullRequests:PullRequest[],openIssues:Issue[],recentWorkflowRuns:WorkflowRun[],recentReleases:Release[]}`. `Approval={id:UUID,operation:MERGE_PULL_REQUEST|ARCHIVE_REPOSITORY|DELETE_REPOSITORY|DELETE_RELEASE,repository:owner/name,number:long (저장소 작업은 0, Release는 ID),expiresAt:ISO-8601,approved:boolean}`.

GitHub API 403/404와 네트워크 오류는 현재 CLI adapter에서 안전한 502로 합쳐진다. 계정 권한에 따라 Organization 회원·팀·저장소가 일부만 표시되거나 요청이 실패할 수 있다. 조직 검색은 GitHub Search 첫 100개까지이며 `overview` 숫자는 그 범위의 표시 개수다. 로그인 시작·polling·취소는 기존 `/api/v1/studio/jobs`의 `github-login` action을 사용한다.

## 서버 Codex assistant와 MCP

Studio/assistant의 `codex-account`는 각 실행 환경의 App Server `account/read` 결과를 `result.assistant`에 투영한다. `authenticated`(boolean, 필수)는 계정 존재 여부, `plan`(string/null, 선택)은 요금제/인증 종류, `email`(string/null, 선택)은 로그인 이메일, `accountType`(string/null, 선택)은 공급자 인증 종류(`chatgpt`, `apiKey` 등 upstream 문자열)다. null 필드는 생략 가능하고 API 키 계정에는 이메일이 없다. 토큰·키는 반환하지 않는다. OWNER·job 소유 세션·CSRF 경계는 기존과 같다. 성공은 SUCCEEDED job, 조회 오류는 FAILED job의 502/504다. 실패 turn은 IDE와 비서 모두 FAILED job이며 인증 오류 401, 샌드박스·기타 모델 오류 502다. 명령 시작의 nullable 출력은 빈 문자열로 투영하고 출력 delta를 item 이벤트로 전달한다.

### 기간별 서비스 운영 로그

`GET /api/v1/services/{id}/resources/{resourceId}/log-history`는 OWNER 전용이다. path `id/resourceId`는 필수 서비스/연결 ID, query `since/until`은 필수 시간대 포함 ISO 8601 문자열(시작 포함, 끝 이전; 시작 < 끝, 최대 31일), `filter`는 선택 enum `errors|all`(기본 errors), body는 없다. 성공은 200 `LogHistory`, `Cache-Control: no-store`. 잘못된 기간·필터·연결 종류는 400, 미인증 401, 권한 실패 403, 없는 서비스/연결 404, 삭제된 장비 409, 원격/Python/Docker/응답 오류 502, SSH 명령 시간 초과 504이며 `message`로 안내한다.

`LogHistory`의 모든 필드는 필수 non-null이다. `serviceId/resourceId/deviceId/container`(string)는 출처, `since/until`(string)은 정규화된 UTC ISO 시각, `filter`(string enum errors/all), `output`(string)은 가림 처리된 로그 또는 빈 문자열이다. `scannedLines`(long)는 읽은 줄 수, `matchedLines`(long)는 오류·예외·5xx 후보 줄 수이며 정확한 HTTP 오류 건수가 아니다. `truncated`(boolean)는 스캔/출력 제한으로 일부 생략, `scanComplete`(boolean)는 현재 보존 로그 스트림의 끝 도달 여부다. `firstTimestamp/lastTimestamp`(string)는 스캔한 첫/마지막 Docker 시각(없으면 빈 문자열), `retentionNotice`(string)는 교체/회전/파일 로그 한계다. 스캔 7초/8 MiB, 출력 500줄/24,000자, 개별 줄 12,000자 제한이며 오류 후보 뒤 8줄을 함께 반환한다. 보존 완전성을 보장하지 않으며 Service Context/Activity에 본문을 저장하지 않는다.

MCP `get_service_runtime`은 필수 `id`(string ≤36)로 `{runtime: RuntimeSnapshot[]}`를 반환한다. `get_service_logs`는 필수 `id/resourceId`(string ≤36), `since/until`(string ≤40), 선택 `filter`(errors/all, 기본 errors)로 `{logs: LogHistory}`를 반환한다. 모두 readOnly이며 REST와 동일한 service/연결 검증을 사용한다. MCP 인증은 아래 bearer 경계이며 service 오류는 기존 tools/call 오류 응답으로 반환한다. 부분 결과는 기간을 나눠 재조회한다. 빈 Telemetry 또는 CI 로그로 운영 로그 조회를 대체하지 않는다.

서버 assistant job은 기존 Studio job 수명·이벤트·취소 DTO와 Codex 권한을 사용하며 별도 경로에서 OWNER 로그인 세션 소유권과 변경 요청 CSRF를 검사한다. 앱 assistant UI는 IDE Codex 화면과 분리되어 대시보드 기능에 맞는 요청만 표시한다.

| Method / URL | Auth | Request | Response |
| --- | --- | --- | --- |
| POST `/api/v1/assistant/jobs` | OWNER + CSRF | Studio `Request`: deviceId는 `local`, root는 서버 workspace root, action은 `setup` 또는 `codex-*` | 202 `JobView`; 설치/실행 실패는 FAILED 상태 |
| GET `/api/v1/assistant/jobs/{id}` | OWNER + 생성 세션 | path id: job UUID | 200 `JobView`; 다른 세션/미존재 404 |
| POST `/api/v1/assistant/jobs/{id}/inputs` | OWNER + 생성 세션 + CSRF | Assistant `Control`: 승인/질문 답변/steer/interrupt | 204; 미실행/지원하지 않는 입력 409 |
| DELETE `/api/v1/assistant/jobs/{id}` | OWNER + 생성 세션 + CSRF | path id: job UUID | 204; 멱등 취소, 적용된 변경은 유지 |
| GET `/api/v1/assistant/events?after={sequence}` | OWNER | 선택 정수 `after`, 기본 0 | 200 `AssistantEvent[]`; sequence, route 또는 applicationId, message. 최대 최근 100개 메모리 큐 |
| POST `/api/v1/mcp` | `Authorization: Bearer DASHBOARD_MCP_TOKEN`; CSRF 제외 | MCP JSON-RPC 2.0 body | JSON-RPC `initialize`, `ping`, `tools/list`, `tools/call`; 알림은 202 |

`codex-rate-limits` action은 Codex App Server의 `account/rateLimits/read`를 호출한다. 성공 시 `JobView.result.assistant.rateLimits`는 기간별 항목 배열이며 각 항목은 `name`(문자열, 필수), `windowDurationMins`(정수 또는 null), `usedPercent`(숫자 또는 null), `resetsAt`(Unix 초 정수 또는 null)을 포함한다. 계정에 사용량 정보가 없으면 빈 배열이다. 조회 실패는 job FAILED로 반환하며 AI 비서 대화 요청에는 영향을 주지 않는다. 인증되지 않은 요청은 기존 OWNER 경계에서 거절한다.

AI 비서는 동일한 job 경로에서 `codex-threads`(선택 `query`, `cursor`; 응답 `assistant.threads[]`, `nextCursor`), `codex-thread-read`(필수 `threadId`; 응답 `assistant.thread`), `codex-thread-rename`(필수 `threadId`, `name` ≤200), `codex-thread-archive`(필수 `threadId`), `codex-thread-delete`(필수 `threadId`; Codex App Server의 `thread/delete`로 영구 삭제, 성공 시 `result.ok=true`), `codex-login`, `codex-logout`을 사용한다. 각 job은 세션 소유권·CSRF 검사를 그대로 적용하고 완료 전에는 RUNNING 상태를 반환한다. 삭제는 선택한 작업 폴더의 세션만 허용하며 다른 폴더의 세션은 FAILED job의 403, Codex App Server 오류는 FAILED job의 502로 전달한다. `codex-run`의 `args.context`는 최대 16개이고 새 `upload` 항목은 `kind="upload"`, `name`(허용 텍스트 확장자, ≤200자), `content`(비어 있지 않은 UTF-8 텍스트, ≤64,000자)를 받는다. Python helper는 이름·크기·본문 합계 128,000자를 다시 검증한다. `image` 항목은 PNG/JPEG/WebP data URL ≤3,000,000자다. 전체 context 입력 크기는 4 MB 이하이며 초과 시 413, 형식 오류는 FAILED job의 400/413으로 전달한다. 파일 본문과 data URL은 응답·DB·브라우저 저장소에 보관하지 않는다.

`DASHBOARD_MCP_TOKEN`은 환경변수로 제공할 때 32자 이상이어야 한다. 비어 있으면 부팅마다 난수 256-bit 값이 만들어져 server Codex child process에만 전달되며 외부 client에서는 사용할 수 없다. 외부 client는 설정한 값을 사용하고 HTTPS reverse proxy 또는 VPN을 거쳐 연결한다. authorization 누락/불일치는 401, Origin이 Host와 다른 요청은 403, Accept에 JSON이 없으면 406, JSON-RPC 입력 오류는 400이다. 응답은 `application/json`, protocolVersion은 요청이 지원되는 경우 `2025-03-26`, `2025-06-18`, `2025-11-25` 중 요청값을 반환하고 그 외에는 `2025-03-26`을 반환한다. 고정 세션 ID를 만들지 않는 stateless HTTP transport다.

MCP tool 목록과 입력 계약:

| Tool | Required args | Optional args / effect |
| --- | --- | --- |
| `open_page` | `route`: allowlisted string | 내부 launcher route를 browser event queue에 전달 |
| `list_apps` | 없음 | 등록 외부 앱 메타데이터 목록 |
| `open_app` | `id`: string ≤36 | 사용자 browser 설정에 따라 등록 앱 열기 event |
| `list_calendar_events` | `from`, `to`: ISO date; `to`는 배타 | 1~366일의 일정 목록 |
| `create_calendar_event` | `title` ≤120, `start`, `end`: local ISO date-time | `allDay` boolean=false, `location` ≤200, `notes` ≤4000, `color` `#RRGGBB` 기본 `#6b8afd`; 기존 일정 검증 적용 |
| `update_calendar_event` | `id` ≤36, `title`, `start`, `end`, `allDay`, `location`, `notes`, `color` | 일정 전체 필드 전달. `location`/`notes`는 빈 문자열 허용; 없음 404 |
| `delete_calendar_event` | `id` ≤36 | 일정 삭제, `{deleted:true}`; 없음 404; DANGEROUS |
| `list_notes` | 없음 | 메모 Entry 목록, 본문 제외 |
| `read_note` | `id`: string ≤36 | Entry와 검증된 블록 본문; 이전 MCP Markdown의 첫 조회 변환 시 revision 증가 가능 |
| `create_note_folder` | `title` ≤200 | `parentId`: null 또는 string ≤36; 빈 폴더 생성 |
| `create_note` | `title` ≤200, `text` ≤100000 | `parentId`: null 또는 string ≤36; Markdown을 편집 가능한 메모 블록으로 변환해 생성 |
| `append_note` | `id` ≤36, `text` ≤100000, `revision`: 0 이상 정수 | 현재 revision과 일치할 때 Markdown 블록을 이어 붙임, 불일치 409 |
| `update_note_metadata` | `id` ≤36, `revision`: 0 이상 정수 | `title` ≤200, `parentId`: null 또는 폴더 ID; 생략 필드 유지, 최소 한 변경 필드, revision 불일치 409 |
| `replace_note_text` | `id` ≤36, `revision`: 0 이상 정수, `text` ≤100000 | Markdown으로 문서 블록 전체 교체. 기존 이미지·서식 블록 제거, revision 불일치 409; DANGEROUS |
| `delete_note` | `id` ≤36, `revision`: 0 이상 정수 | 문서 또는 빈 폴더 삭제, `{deleted:true}`; 비어 있지 않은 폴더·revision 불일치 409; DANGEROUS |
| `github_status` | 없음 | `{authenticated: boolean}` |
| `list_github_repositories` | 없음 | `{repositories: array}`; 최대 50개 |
| `list_github_pull_requests` | `repository`: owner/name 문자열 ≤201 | `{pullRequests: array}`; 최대 50개 열린 PR |
| `list_github_issues` | `repository`: owner/name 문자열 ≤201 | `{issues: array}`; 최대 50개 열린 이슈 |
| `github.list_owners`, `github.list_organizations` | 없음 | `{owners: Owner[]}` 또는 `{organizations: Owner[]}`; READ |
| `github.get_organization`, `github.list_org_members`, `github.list_org_teams` | `owner` ≤39 | Organization/Member[]/Team[]; READ |
| `github.list_repositories`, `github.get_org_overview`, `github.get_my_work` | `owner` ≤39 | Repository[]/OwnerOverview/MyWork; READ |
| `github.get_recent_activity` | `owner` ≤39 | Activity[]; READ |
| `github.search_across_org` | `owner` ≤39, `query` ≤100 | SearchResult; READ |
| `github.find_related_issues` | `repository` ≤201, `query` ≤100 | Issue[]; READ |
| `github.get_repository`, `github.get_development_context`, `github.get_repo_health` | `repository` ≤201 | RepositoryDetail/DevelopmentContext; READ |
| `github.create_repository` | `owner` ≤39, `name` ≤100 | `description`, `isPrivate`; RepositoryDetail; WRITE |
| `github.update_repository` | `repository` ≤201 | `description`, `homepage`, `topics`; RepositoryDetail; WRITE |
| `github.list_branches`, `github.list_tags`, `github.list_contributors`, `github.get_languages` | `repository` ≤201 | Branch[]/Tag[]/Contributor[]/언어별 바이트; READ |
| `github.get_tree` | `repository`, `ref` ≤200 | FileEntry[]; READ |
| `github.get_file` | `repository`, `path` ≤500, `ref` ≤200 | FileContent; READ |
| `github.get_commit`, `github.get_commit_diff` | `repository`, `sha` ≤40 | Commit/ChangedFile[]; READ |
| `github.get_issue`, `github.get_pull_request`, `github.get_pull_request_diff` | `repository`, `number` ≥1 | IssueDetail/PullRequestDetail/ChangedFile[]; READ |
| `github.list_issues`, `github.list_pull_requests` | `repository` ≤201 | `state` open/closed/all; Issue[]/PullRequest[]; READ |
| `github.search_owner_issues` | `owner` ≤39 | `state`, `role`, `repository`, `label`; Issue[]; READ |
| `github.search_owner_pull_requests` | `owner` ≤39 | `state`, `repository`; PullRequest[]; READ |
| `github.get_pr_context` | `repository`, `number` ≥1 | PullRequestContext; READ |
| `github.get_workflow_runs`, `github.list_workflows` | `repository` | WorkflowRun[]/Workflow[]; READ |
| `github.get_workflow_run`, `github.get_workflow_jobs`, `github.get_workflow_logs`, `github.analyze_failed_workflow` | `repository`, `runId` ≥1 | WorkflowRun/WorkflowJob[]/failedLogs/WorkflowAnalysis; READ |
| `github.list_workflow_artifacts` | `repository`, `runId` ≥1 | WorkflowArtifact[]; READ |
| `github.list_releases` | `repository` | Release[]; READ |
| `github.get_release` | `repository`, `releaseId` | Release와 asset metadata; READ |
| `github.update_release` | `repository`, `releaseId` | `tag` ≤100, `name` ≤256, `body` ≤60000 중 최소 한 필드; Release; WRITE. Git 태그 자체는 변경하지 않음 |
| `github.request_delete_release` | `repository`, `releaseId` | 일회성 Approval 생성; WRITE, 삭제 실행 없음 |
| `github.delete_release` | `repository`, `releaseId`, `approvalId` UUID | `{deleted:true}`; DANGEROUS, 동일 Release 브라우저 승인 필요, Git 태그 유지 |
| `github.create_issue` | `repository`, `title` ≤256 | `body` ≤60000; IssueDetail; WRITE |
| `github.update_issue` | `repository`, `number` | `title`, `body`, `state`, `labels`, `assignees`, `milestone`; IssueDetail; WRITE |
| `github.comment_issue` | `repository`, `number`, `body` ≤60000 | IssueDetail; WRITE |
| `github.create_pull_request` | `repository`, `title`, `base`, `head` | `body` ≤60000, `draft` boolean; PullRequestDetail; WRITE |
| `github.update_pull_request` | `repository`, `number` | `title`, `body`, `state`, `base`; PullRequestDetail; WRITE |
| `github.review_pull_request` | `repository`, `number`, `event` COMMENT/APPROVE/REQUEST_CHANGES | `body` ≤60000; PullRequestDetail; WRITE |
| `github.rerun_workflow` | `repository`, `runId` | `{rerunRequested:true}`; WRITE |
| `github.cancel_workflow` | `repository`, `runId` | `{cancelRequested:true}`; WRITE |
| `github.dispatch_workflow` | `repository`, `workflowId`, `ref` | `inputs` 문자열 map 최대 25개; `{dispatchRequested:true}`; WRITE |
| `github.create_release` | `repository`, `tag` | `name`, `body`, `draft`, `prerelease`, `generateNotes`; Release; WRITE |
| `github.request_merge` | `repository`, `number` | 일회성 Approval 생성; WRITE, 병합 실행 없음 |
| `github.merge_pull_request` | `repository`, `number`, `approvalId` UUID | `{merged:true}`; DANGEROUS, 대시보드 브라우저 승인 필요 |
| `github.request_archive` | `repository` | 일회성 Approval 생성; WRITE, 보관 실행 없음 |
| `github.archive_repository` | `repository`, `approvalId` UUID | RepositoryDetail; DANGEROUS, 대시보드 브라우저 승인 필요 |
| `github.request_delete_repository` | `repository` | 일회성 Approval 생성; WRITE, 삭제 실행 없음 |
| `github.delete_repository` | `repository`, `approvalId` UUID | `{deleted:true}`; DANGEROUS, 동일 저장소 브라우저 승인 필요 |

Tool 오류는 MCP `CallToolResult.isError=true` 및 text content로 반환한다. 도구는 WorkspaceException의 검증 오류를 안전하게 전달하고 예상하지 못한 예외 세부 내용은 숨긴다. `open_page` navigation 이벤트는 owner 세션 browser만 GET으로 polling한다. 일정·메모 삭제는 정확한 ID와 메모 revision을 요구한다. GitHub 저장소·릴리스 삭제는 10분 유효 브라우저 승인을 소비한다. 임의 파일·셸 작업은 제공하지 않는다.

## Database Studio API

`ConnectionRequest`에 선택적 문자열 `targetMode` (`DIRECT` 기본값, `DEVICE`, `DOCKER`), `deviceId` (최대 100자), `containerId` (최대 128자, 영문·숫자·밑줄·점·하이픈)를 추가한다. 생략/null은 DIRECT 및 빈 ID로 정규화한다. `ConnectionView`에도 세 필드가 null 없이 반환된다. DIRECT는 ID를 비워야 하고, DEVICE/DOCKER는 등록된 deviceId가 필요하다. DOCKER는 containerId가 필수다. DEVICE의 host는 장비에서 보이는 주소(기본 127.0.0.1), DOCKER의 port는 컨테이너 내부 DB 포트이고 host는 서버가 결정한다. 원격 SQLite는 400으로 거부한다. 삭제된 장비는 404, 정지/주소 없는 컨테이너는 409, Docker 조회 실패는 502이다. 연결 테스트에서 대상 해석 실패는 `connected:false`, `errorType:TARGET_UNAVAILABLE`로 반환한다.

`GET /api/v1/databases/devices/{deviceId}/containers`: OWNER 세션, 필수 path deviceId, query/body 없음. 200 응답은 최대 200개의 `{id,name,image,ports}` 배열이며 모든 필드는 null이 아닌 문자열이다. 환경 변수나 인증 정보는 포함하지 않는다. 없는 장비 404, Docker 조회 실패 502 (`message`: 장비의 Docker 목록을 읽지 못했습니다. Docker 설치와 SSH 계정의 접근 권한을 확인하세요.), 인증·권한 오류는 아래 공통 계약을 따른다.

모든 경로는 OWNER 세션이 필요하다. 쓰기·테스트·취소 요청에는 CSRF 토큰이 필요하다. 미인증 401, 권한 부족/READ_ONLY 쓰기 403, 잘못된 입력 400, 없는 연결/테이블/실행 404, 위험 SQL 미확인 또는 활성 쿼리 중 연결 삭제 409, 대기열 초과 429, 외부 DB 조회 오류 502. 실패 응답은 `{message}`이고 원본 JDBC 예외를 포함하지 않는다. [보안·수명 계약](../database-studio.md).

| Method | URL | Request | Response | Success |
| --- | --- | --- | --- | --- |
| GET/POST | `/api/v1/databases` | POST `ConnectionRequest` | `ConnectionView[]` / `ConnectionView` | 200/201 |
| GET/PUT/DELETE | `/api/v1/databases/{id}` | PUT `ConnectionRequest` | `ConnectionView` / 없음 | 200/204 |
| POST | `/api/v1/databases/{id}/test` | 없음 | `TestResult` | 200 |
| POST | `/api/v1/databases/test?id={id?}` | `ConnectionRequest`, id 선택(기존 credential 유지) | `TestResult`; 저장 없음 | 200 |
| GET | `/api/v1/databases/{id}/schemas` | 없음 | `Schema[]` | 200 |
| GET | `/api/v1/databases/{id}/tables?schema=...` | schema | `Table[]` | 200 |
| GET | `/api/v1/databases/{id}/functions?schema=...` | schema | `Function[]` | 200 |
| GET | `/api/v1/databases/{id}/tables/{table}?schema=...` | schema | `TableDetail` | 200 |
| GET | `/api/v1/databases/{id}/tables/{table}/rows` | schema, page=0, size=50, sort, direction=ASC, filterColumn, filter | `Page` | 200 |
| POST | `/api/v1/databases/{id}/query` | `{sql,confirmed:false}` | `QueryResult` RUNNING + executionId | 202 |
| GET | `/api/v1/databases/{id}/query/{executionId}` | 없음 | `QueryResult` | 200 |
| POST | `/api/v1/databases/{id}/query/{executionId}/cancel` | 없음 | `QueryResult` CANCELLED | 200 |
| GET | `/api/v1/databases/history` | 없음 | 최신 100 `History[]` | 200 |
| GET/POST | `/api/v1/databases/{id}/favorites` | POST `{name,sql}` | `Favorite[]` / `Favorite` | 200/201 |
| DELETE | `/api/v1/databases/{id}/favorites/{favoriteId}` | 없음 | 없음 | 204 |

`ConnectionRequest`: `name` 필수 1~100자, `type` POSTGRESQL/MYSQL/MARIADB/SQLITE, `host` 선택 최대 255자(서버형 필수), `port` 선택 1~65535(기본 PostgreSQL 5432/MySQL·MariaDB 3306), `databaseName` 필수 최대 500자(SQLite는 기존 local 파일의 절대 경로), `username` 선택 최대 100자, `credential` 선택 최대 500자(수정 시 빈 값이면 유지), `sslMode` DISABLE/REQUIRE, `accessMode` READ_ONLY/READ_WRITE, `metadata` 선택적 문자열 map(최대 20항목, 짧은 비밀이 아닌 값만). SQLite는 host/username/credential/SSL/port를 사용하지 않는다. 응답 `ConnectionView`에는 `id`, 이름·종류·host·port·databaseName·username·sslMode·accessMode, `passwordConfigured`, `metadata`, 생성·수정 epoch ms만 포함한다. 암호문과 평문은 반환하지 않는다. `TestResult`는 `connected/version/latencyMs/errorType`이다.

`TableDetail`은 table, columns(name/type/nullable/primaryKey), foreignKeys, indexes를 가진다. `Page`는 columns, rows, total(-1이면 count 불가), page, size를 가진다. `QueryResult`는 executionId, state RUNNING/SUCCEEDED/FAILED/CANCELLED, resultType QUERY/MUTATION/빈 값, columns, rows, affectedRows, durationMs, errorType을 가진다. 결과는 최대 200행, binary는 placeholder다. `History`는 connection ID(nullable), 연결 이름 snapshot, sql, timestamp, durationMs, resultType, success, errorType을 가진다. `Favorite`는 ID, 연결 ID, name, sql, createdAt을 가진다.

MCP read-only 도구 `list_database_connections`(입력 없음), `get_database_metadata`(id), `list_database_tables`(id, schema), `describe_database_table`(id, schema, table)은 동일 DatabaseStudioService를 호출한다. 구조화된 schema를 사용하고 임의 SQL 쓰기 도구는 제공하지 않는다.

## Service Catalog API

### Assistant Service Draft API

이 경로는 OWNER 세션이 필요하고 PUT/POST/DELETE에는 CSRF가 필요하다. `{threadId}`는 Codex 대화 ID(1~100자), `{id}`는 Draft UUID다. 미인증 401, 권한/CSRF/승인 누락 403, 잘못된 입력 400, 만료·없는 Draft 404, 오래된 revision 또는 기존 서비스 변경 409다. 모든 오류는 `WorkspaceErrors.message`를 반환한다. 서버 재시작과 30분 만료 시 초안은 사라진다.

| Method | URL | Request | Response | Success |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/assistant/service-drafts/thread/{threadId}` | 없음 | 최신 `Draft`, 없으면 본문 없음 | 200/204 |
| GET | `/api/v1/assistant/service-drafts/thread/{threadId}/resources` | 없음 | 해당 대화의 마지막 `Discovery`; 탐색 전이면 빈 목록 | 200 |
| GET | `/api/v1/assistant/service-drafts/{id}` | 없음 | `Draft` | 200 |
| PUT | `/api/v1/assistant/service-drafts/{id}` | `DraftUpdate` | 수정된 `Draft` | 200 |
| POST | `/api/v1/assistant/service-drafts/{id}/approve` | `{revision:long}` | `APPROVED` 상태의 `Draft` | 200 |
| POST | `/api/v1/assistant/service-drafts/{id}/commit` | `{revision:long}` | `ServiceView` | 200 |
| DELETE | `/api/v1/assistant/service-drafts/{id}` | 없음 | 없음 | 204 |

`Draft={id:string,threadId:string,serviceId:string|null,name:string,description:string,environment:string,status:DRAFT|APPROVED|COMMITTED,revision:long,serviceUpdatedAt:long,candidates:Candidate[],questions:string[],updatedAt:epochMs,excludedResources:ResourceLink[]}`. `Candidate={type:ResourceRequest.type,reference:string,deviceId:string,displayName:string,confidence:HIGH|MEDIUM|LOW,reason:string,selected:boolean,requiresConfirmation:boolean,composeProject:string,composeService:string,image:string,state:string,containerId:string,ports:string,workingDirectory:string}`. 빈 보조 문자열은 `""`이고 nullable은 `serviceId`뿐이다. `Discovery={candidates:Candidate[],services:ExistingService[],sources:map<string,OK|UNAVAILABLE>,questions:string[]}`. `ExistingService={id:string,name:string,environment:string,resources:ResourceLink[]}`, `ResourceLink={type:string,reference:string,deviceId:string}`. `DraftUpdate={revision:long 필수,name?:string(1~100),description?:string(최대 500),environment?:string(1~40),resources?:ResourceRequest[](최대 100),excludedResources?:ResourceLink[](최대 100)}`. 생략한 필드는 유지한다. `resources`를 보내면 선택 목록 전체를 교체한다. `excludedResources`를 보내면 명시적 제외 목록 전체를 교체하며 탐색한 Docker 컨테이너만 허용하고 해당 항목을 선택 목록에서도 제거한다. 다시 선택하면 제외 목록에서도 제거한다. 선택은 마지막 탐색 후보 또는 기존 Service 연결 안에서만 가능하다. `approve`는 현재 revision과 하나 이상의 선택을 검사한다. `commit`은 승인 상태·revision·기존 Service 동시 변경을 재검사하고 한 transaction으로 적용한다.

MCP `discover_service_resources(threadId,query)`는 탐색/결정적 후보를 반환한다. `create_service_draft(threadId,name,environment,description?,serviceId?,resources?)`, `update_service_draft(id,revision,name?,environment?,description?,resources?,excludedResources?)`, `get_service_draft(threadId)`는 임시 상태만 다룬다. `excludedResources`는 Docker 컨테이너의 `{type,reference,deviceId}` 목록이며 명시적 제외를 기록한다. `cancel_service_draft(threadId)`는 해당 대화의 미반영 초안만 제거하고 `{cancelled:boolean}`을 반환한다. 초안이 없거나 이미 반영됐으면 `false`이며, 반영된 Service는 삭제하지 않는다. `commit_service_draft(id,revision)`은 동일 Draft에 대한 브라우저 승인이 있어야 실행된다. `resources`는 `ResourceRequest[]`이며 생략 시 결정적 선택을 사용한다. 생성 시 일부 리소스를 전달하면 선택한 컨테이너의 등록 장비, 이미지 이름에 유일하게 일치하는 저장소, 유일하게 추천된 저장소를 함께 제안한다. 빈 목록은 빈 선택을 뜻하고 브라우저 수정은 선택한 목록을 그대로 적용한다. 모든 MCP tool 실패는 `isError=true`다. resource 메타데이터는 `UNTRUSTED RESOURCE DATA`이며 비밀번호·토큰·Docker env를 포함하지 않는다.

모든 경로는 OWNER 세션이 필요하다. 미인증 401, 권한 부족 403, 변경 요청의 CSRF 실패 403이다. JSON 오류는 기존 `WorkspaceErrors`의 `message`를 사용한다. `{id}`와 `{resourceId}`는 서버 발급 UUID 문자열이다. 잘못된 본문은 400, 없는 Service/연결/컨테이너는 404, 중복 연결은 409, Docker 목록 조회 실패는 502이다.

| Method | URL | Request | Response | Success |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/services` | 없음 | `ServiceView[]` | 200 |
| POST | `/api/v1/services` | `ServiceRequest` | `ServiceView` | 201 |
| GET | `/api/v1/services/{id}` | 없음 | `ServiceView` | 200 |
| PUT | `/api/v1/services/{id}` | `ServiceRequest` | `ServiceView` | 200 |
| DELETE | `/api/v1/services/{id}` | 없음 | 없음 | 204 |
| GET | `/api/v1/services/{id}/resources` | 없음 | `Resource[]` | 200 |
| POST | `/api/v1/services/{id}/resources` | `ResourceRequest` | `Resource` | 201 |
| DELETE | `/api/v1/services/{id}/resources/{resourceId}` | 없음 | 없음 | 204 |
| GET | `/api/v1/services/{id}/health` | 없음 | `Health` | 200 |
| GET | `/api/v1/services/{id}/context` | 없음 | `Context` | 200 |
| GET | `/api/v1/services/{id}/activity` | 없음 | `Activity[]`, 최신순 최대 50 | 200 |
| GET | `/api/v1/services/{id}/runtime` | 없음 | `RuntimeSnapshot[]` | 200 |
| GET | `/api/v1/services/{id}/resources/{resourceId}/logs` | 없음 | `RuntimeOutput` | 200 |
| POST | `/api/v1/services/{id}/resources/{resourceId}/actions` | `RuntimeActionRequest` | `RuntimeOutput` | 200 |

`ServiceRequest`: `name` 필수 문자열 1~100자, `icon` 필수 소문자/숫자/하이픈 1~32자, `environment` 필수 문자열 1~40자, `description` 선택 문자열 최대 500자(null이면 빈 문자열). `ServiceView`: `id` 문자열 UUID, `name/icon/environment/description` 문자열, `createdAt/updatedAt` epoch ms 정수. 모든 응답 필드는 null이 아니다.

`ResourceRequest`: `type` 필수 enum `GITHUB_REPOSITORY`, `GITHUB_ORGANIZATION`, `DEVICE`, `DOCKER_CONTAINER`, `TELEMETRY`, `ENDPOINT`, `FILE`, `DATABASE`; `reference` 필수 문자열 최대 500자; `deviceId` 선택 문자열 최대 36자(DOCKER_CONTAINER에서는 필수, FILE에서는 선택, 그 외 빈 문자열); `label` 선택 문자열 최대 100자. `Resource`: `id/serviceId/type/reference/deviceId/label` 문자열, `createdAt` epoch ms 정수, `orphaned` boolean. 선택 입력은 저장 시 빈 문자열로 정규화되고 응답은 null이 아니다. 유효하지 않은 참조나 URL/경로는 400 또는 404로 거부된다.

`Health`: `state` enum `HEALTHY/DEGRADED/DOWN/UNKNOWN`, `checkedAt` epoch ms, `signals[]`. 각 Signal은 `source/reference/state/detail` 문자열이며 signal state는 `HEALTHY/DEGRADED/DOWN/UNKNOWN`. `Context`: `service` ServiceView, `resources` Resource[], `health` Health, `github/runtime/telemetry/databases`는 참조별 데이터 map, `activity` Activity[]. 외부 모듈 조회 실패 시 해당 map 원소만 생략한다. `Activity`: `id/source/type/severity/title` 문자열, `timestamp` epoch ms, `metadata` JSON object. null 필드는 없다.

`RuntimeSnapshot`: `resourceId` 연결 ID, `type` enum `DEVICE|DOCKER_CONTAINER`, `name` 표시 문자열, `deviceId` 장비 ID, `state` enum `ONLINE|REACHABLE|UNAVAILABLE|RUNNING|STOPPED|UNKNOWN`, `cpu/memory/disk` 0~100 Double 또는 null(장비 미계측·컨테이너에서는 null), `image`와 `detail` 문자열(없으면 빈 문자열), `checkedAt` epoch ms. 장비는 기존 DeviceOperations 계측을 사용하고 컨테이너는 Docker 목록의 이름·상태·이미지만 읽는다. 연결이 삭제되거나 개별 조회에 실패하면 그 항목은 UNKNOWN이며 다른 항목은 유지한다. `RuntimeActionRequest`의 `action`은 필수 enum `start|stop|restart`이다. `RuntimeOutput`은 `output` 문자열 하나를 가진다. 로그는 Docker 최근 200줄과 기존 명령 어댑터의 최대 256 KiB·10초 제한을 적용하며 `Cache-Control: no-store`로 응답한다. 로그 본문은 Service Context나 Activity에 저장하지 않는다. 세 경로 모두 등록된 Service 연결만 대상으로 한다. 로그·작업은 DOCKER_CONTAINER 연결만 허용한다. 미인증 401, 권한·CSRF 실패 403, 잘못된 연결 종류·action은 400, 없는 서비스·연결은 404, 삭제된 장비 연결은 409, 원격 명령 실패는 502 또는 시간 초과 504이며 오류 응답은 `message` 문자열을 반환한다.

MCP read-only 도구 `list_services`(입력 없음), `get_service`, `get_service_context`, `get_service_health`(각각 `id` 필수 문자열 최대 36자)는 동일 ServiceCatalogService를 호출한다. 반환 필드는 각각 `services`, `service+resources`, `context`, `health`다. 기존 `/api/v1/mcp` bearer 인증과 OWNER 컨텍스트를 그대로 사용한다.
## Workspace 실시간 알림

`GET /ws/workspace`는 OWNER 로그인 세션과 same-origin WebSocket Upgrade를 요구한다. path/query parameter와 요청 body는 없다. 연결 성공은 HTTP 101, 비로그인은 401, 다른 Origin은 403이다. 로그인 세션 만료/교체·로그아웃 및 클라이언트 명령 전송은 close 1008, 연결 한도 초과는 1013, 송신 실패는 1011로 종료한다. 프레임은 서버에서 클라이언트로만 전송한다.

| 응답 필드 | 타입 | 필수/null | 의미 |
| --- | --- | --- | --- |
| type | string | 필수, null 불가 | `ready`, `changed`, `heartbeat` |
| epoch | string | 필수, null 불가 | 서버 프로세스 수명을 나타내는 UUID |
| revision | integer | 필수, null 불가 | 프로세스 내 단조 증가하는 알림 버전 |
| topics | string[] | 필수, null 불가 | 재조회 영역. 없으면 빈 배열 |
| jobs | string[] | 필수, null 불가 | 동일 로그인 세션에 속한 변경 작업 ID. 없으면 빈 배열 |

topic은 `workspace`, `devices`, `notes`, `calendar`, `military`, `timetables`, `services`, `telemetry`, `databases`, `github`, `cloud`, `memory`이다. 병역 쓰기는 `military`와 `calendar`를 함께 무효화한다. ready에는 빈 topics/jobs가 오며 클라이언트가 최신 REST 상태를 조회한다. 신호는 재조회 힌트이며 영속 이벤트 스트림이나 쓰기 성공 영수증이 아니다. 서버는 200ms 단위로 합치며 20초마다 heartbeat를 보낸다. 브라우저의 `all`은 재접속·누락 복구용 로컬 무효화 값이다. REST 권한 검사를 우회하지 않는다. 세부 흐름과 검증은 [실시간 UI](../realtime-ui.md)를 따른다.

## 병역 캘린더

모든 API는 OWNER 세션, 쓰기는 CSRF가 필요하다. JSON 본문과 응답을 사용한다. 아래 명시하지 않은 path/query/body는 없다. 날짜는 ISO `yyyy-MM-dd`, 서울 기준이다.

| Method / URL | Path / Query / Body | 성공 응답 |
| --- | --- | --- |
| GET `/api/v1/military` | 없음 | 200 Dashboard, 미등록도 200 |
| PUT `/api/v1/military/profile` | ProfileRequest 전체 교체 | 200 Dashboard |
| DELETE `/api/v1/military/profile` | query revision: 필수 long ≥1 | 204 빈 본문, 하위 병역 일정 cascade |
| POST `/api/v1/military/events` | EventRequest, revision=0 | 201 EventView |
| PUT `/api/v1/military/events/{id}` | path id: 원본 UUID, EventRequest 전체 교체 | 200 EventView |
| DELETE `/api/v1/military/events/{id}` | path id: 원본 UUID, query revision: 필수 long ≥1 | 204 빈 본문 |

### 요청 DTO

| ProfileRequest 필드 | 형식 / 필수·null / 의미 |
| --- | --- |
| nickname | String, 필수·null 불가, 공백 제외 1~60자 표시 이름 |
| serviceType | String enum, 필수·null 불가: ARMY 육군, NAVY 해군, AIR_FORCE 공군, MARINES 해병대, SOCIAL_SERVICE 사회복무, CUSTOM 직접 설정 |
| enlistmentDate | LocalDate, 필수·null 불가, 입대·소집일 |
| dischargeDate | LocalDate, 선택·null 허용, 실제 종료일. null이면 기본 기간 추정; CUSTOM 또는 2022년 이전 입대는 필수 |
| privateFirstDate / corporalDate / sergeantDate | 각각 LocalDate, 선택·null 허용, 실제 일병·상병·병장 진급일 |
| leaveAllowance | Integer, 선택·null 허용, 휴가 예산 0~1000일 |
| calendarEnabled | boolean, 누락/null 입력 시 false, 일반 캘린더 투영 여부; UI 기본값 true |
| revision | long ≥0, 누락/null 입력 시 0, 생성 0 / 수정은 조회한 최신 버전 |

복무기간은 1900~2199년, 입대일부터 최대 40년 이내다. 진급일은 현역 4개 유형에서만 허용하며 입대일 이후부터 종료일까지 순서대로 입력한다. 이미 저장한 일정이 새 복무기간을 벗어나면 변경을 거부한다. [계산 기준](../military-calendar.md)을 따른다.

| EventRequest 필드 | 형식 / 필수·null / 의미 |
| --- | --- |
| kind | String enum, 필수·null 불가: LEAVE 휴가, TRAINING 훈련, DUTY 근무, OTHER 기타 |
| title | String, 필수·null 불가, 공백 제외 1~120자 |
| startDate / endDate | 각각 LocalDate, 필수·null 불가, 양 끝 포함. 복무기간 안에서 최대 366일 |
| leaveDays | Integer, 선택·null 허용, 0~일정 날짜 수. LEAVE 미입력 시 날짜 수, 다른 종류는 0만 허용 |
| notes | String, 선택·null 허용, 최대 2000자, null은 빈 문자열로 저장 |
| revision | long ≥0, 누락/null 입력 시 0, 생성 0 / 수정은 조회한 최신 버전 |

전체 일정 최대 1,000개이며 휴가끼리는 날짜 겹침을 허용하지 않는다. 저장한 revision은 1부터 시작하며 성공한 수정마다 증가한다.

### 응답 DTO

아래 응답의 모든 필드는 항상 포함된다. nullable로 표시하지 않은 필드는 null 불가이며 배열은 비어 있을 수 있다.

- Dashboard: profile(nullable ProfileView), progress(nullable ProgressView), currentRank(nullable String), milestones(Milestone[]), events(EventView[]), leave(LeaveSummary), serviceTypes(ServiceOption[]), sources(Source[]), serverNow(UTC ISO instant String), timeZone(String, `Asia/Seoul`). 미등록 시 앞의 세 필드는 null, milestones/events는 빈 배열, leave는 null 예산·잔여와 0 사용·예정이다.
- ProfileView: ProfileRequest와 같은 이름·형식의 필드, dischargeDate는 계산된 날짜로 null 불가, estimatedDischarge(boolean, 자동 추정 여부) 추가. 진급일과 leaveAllowance만 nullable이다.
- ProgressView: status(String enum UPCOMING 입대 전 / SERVING 복무 중 / COMPLETED 종료 다음 날부터), startsAt/endsAt/nextDayAt(long epoch ms, 시작·종료 다음 날·다음 서울 자정), totalDays/elapsedDays/remainingDays/serviceDay/daysToDischarge(long, 전체·완료 날짜·오늘 포함 잔여·복무 일차·종료일까지 일수), percent(double 0~100). serviceDay는 입대 전 0, 복무 중 1부터, 종료 후 전체 일수로 제한한다. daysToDischarge는 종료 후 음수다.
- EventView: id(String UUID), EventRequest의 모든 필드. leaveDays는 int, notes는 String으로 null 불가다.
- Milestone: id(String, enlistment/private-first/corporal/sergeant/discharge), title(String), kind(String enum ENLISTMENT/PROMOTION/DISCHARGE), date(LocalDate), daysUntil(long, 과거 음수), reached(boolean, 오늘 또는 과거).
- LeaveSummary: allowance(nullable Integer), used(int, 시작한 휴가 전체 차감일수), planned(int, 미래 휴가 차감일수), remaining(nullable Integer, 예산-사용-예정, 초과 시 음수).
- ServiceOption: id(serviceType enum), label(String), months(nullable Integer, CUSTOM만 null).
- Source: title(String), url(String HTTPS), checkedOn(LocalDate, 자료 확인일).

### 오류와 연동

미인증 401, OWNER/CSRF 실패 403은 공통 보안 계약을 따른다. 도메인 오류 본문은 `{message: String}`이며 별도 error code 필드는 없다. 입력 형식 오류 400은 `입력 형식과 필수 항목을 확인해 주세요.`; 도메인 검증 400은 기간·진급 순서·중복 휴가·일정 한도에 대한 한국어 안내다. 프로필 미등록 404는 `복무 정보를 먼저 등록해 주세요.`, 일정 미존재 404는 `병역 일정을 찾을 수 없습니다.`. revision 불일치 409는 `다른 곳에서 변경되었습니다. 최신 내용을 확인한 뒤 다시 저장해 주세요.`다. 예상하지 못한 오류는 공통 500 응답이다.

기존 Calendar API 및 MCP `list_calendar_events`는 연동된 병역 투영도 반환한다. 병역 항목은 일반 Calendar/MCP 쓰기 대상이 아니며 원본은 위 API로 수정한다. 삭제·연동 해제 후 투영은 사라지지만 일반 일정에는 영향을 주지 않는다. 성공한 변경 후 WebSocket은 `military`, `calendar` 재조회를 알린다. 외부 군돌이/병무청 개인정보 API 호출은 없다.
## Studio workbench 추가 계약

- `SessionRequest.root`: 선택적 프로젝트 절대 경로(최대 4096자), TERMINAL에서만 적용. 대상 FileAdapter의 root 경계와 directory 여부를 검증한다.
- Studio jobs: `run-start`는 `args.name`(80), `args.content`(명령 4000), `args.mode`(`run|test|build`); `run-logs/stop/restart/delete`는 `args.path`(32자리 hex process ID). `run-list`, `run-commands`, `ports`는 추가 args 없음. `Result.tools`에는 `processes`, `commands`, `ports`, `output`이 선택적으로 들어간다. Process: id/name/command/kind/state/pid/exitCode/started/finished/truncated. Port: port/protocol/pids/project/url. Command: name/command/kind. read-only 조회는 editor/Codex mutation lock을 점유하지 않는다.
- `POST studio/browser`: `{deviceId,root,action,url?,text?,x?,y?,delta?}`. HTTP(S) URL만 허용, embedded credentials 금지. Response: `{url,title,text,consoleErrors,networkFailures,image,width,height}`, image는 JPEG base64이며 viewport는 1200×720. close는 `{ok:true}`. HTTP 세션 소유자만 같은 페이지에 접근하고 로그아웃 시 정리한다. key는 Enter/Tab/Backspace/Escape/방향키, click 좌표 범위는 화면 안으로 제한한다.
- `POST studio/api/send`: `{deviceId,root,method,url,params?,headers?,bodyType,body?,fields?,auth?}`. params/headers/fields는 `{name,value,fileName?}` 배열(각 100개 이하). bodyType은 none/json/text/form/multipart. multipart fileName이 있으면 value는 파일 base64. auth는 `{type:none|basic|bearer|api-key,username?,value?,name?,location:header|query?}`. 응답 `{id,response:{status,headers,body,base64,latency,size,truncated}}`; latency는 ms, size는 수신한 body byte수(1 MiB 제한), truncation은 별도 표시. 리다이렉트는 응답으로 돌려준다.
- `POST studio/api/history`, `environment/read`: `{deviceId,root}`. history는 `{id,time,method,url,status}` 배열이고 query는 숨긴다. environment/read는 변수 이름 배열만 반환한다.
- `POST studio/api/environment`: `{deviceId,root,values:{NAME:"value",REMOVE:null}}`; 이름은 `[A-Za-z_][A-Za-z0-9_]{0,79}`, 프로젝트당 50개, 값 최대 4000자. `{{NAME}}`은 실행 직전에 server-side 치환한다.
- `POST studio/api/replay`: `{deviceId,root,id}`; 저장된 요청을 실제로 다시 보내고 새 기록을 만든다. 인증값은 history 응답이나 브라우저 localStorage로 반환하지 않는다.
- API 상태는 SQLite entity와 무관한 vault 암호화 파일이며 key는 기존 CredentialVault를 공유한다. `workspace.studio-state-path` 기본값은 `./data/studio-api`. 최신 요청/응답 30개를 유지한다.
- local 프로젝트 Codex를 허용한다. trusted adapter `projectCodex` 플래그와 별도 CODEX_HOME으로 기존 서버 assistant의 MCP/읽기 전용 계약을 유지한다. HTTP 요청에서 이 내부 플래그는 받지 않는다.


Studio setup의 선택 boolean args.refresh=true는 local/SSH 모두 최신 Codex 캐시를 무효화한다. 명시적 갱신 실패는 JobView state=FAILED, errorStatus=502로 반환하며 기존 버전 fallback 성공을 반환하지 않는다. refresh=false/생략은 기존 초기 준비 동작을 유지한다. OWNER·CSRF와 job 소유권 검사는 동일하다.

Studio `git-status` 응답은 `repository` 여부를 제공한다. false이면 `repositories`에 장비 경계 안에서 발견한 열기 후보를 반환하며 `changes/branches/history`는 비어 있다. true이면 기존 상태 필드에 `upstream`, `ahead`, `behind`를 추가한다. 네트워크 fetch는 발생하지 않는다. GitHub 로그인 job은 기존 생성/조회/취소 계약을 유지하며 인증 중 프로젝트 편집 잠금을 보유하지 않는다.

AI 비서의 요청 root는 하위 실행 경로를 지정하지 않는다. 인증된 assistant 전용 adapter가 서버 홈의 비공개 작업 영역을 사용한다. 공개 Studio/Files 경로 경계와 OWNER·CSRF·job 소유권 검사는 유지한다. 기존 assistant 대화의 cwd 호환은 원래 서버 파일 루트로만 제한하며 IDE 경로 권한을 확장하지 않는다.

### GitHub repository navigation (2026-10-10)
- Owner search filters accessible users/organizations; organization retrieval follows pages of 100 (up to 100 pages, explicit error beyond limit).
- Repository overview displays description, topics, homepage and default branch. An explicit save form edits description/homepage/topics.
- Branches and tags open the selected ref's file tree; releases and commit details retain existing navigation.
- Issue/PR/release/Markdown file bodies and PR discussion use the safe DOM Markdown renderer, including disabled task checkboxes. Raw HTML is not executed. Full GitHub Flavored Markdown parity is not claimed.
- `PATCH /api/v1/github/repositories`: description/homepage use GitHub PATCH repository; topics use PUT repository/topics with `names`. Null topics leave them untouched; empty list removes all topics. Both writes are sequential and not atomic; a topics error can follow a successful description update.
- Browser regression: `node scripts/check-github.mjs` uses real Chromium and fixture API responses; it does not establish live GitHub authorization or remote-write success.
- GitHub product parity is not implemented: Discussions, Projects, security administration, organization/billing settings and full Markdown compatibility remain outside this change. Existing list endpoints may still be bounded.

## 인증 브라우저

### POST /api/v1/authentication-browser/sessions

OWNER + CSRF 필수. path/query parameter 없음. JSON 요청:

| 필드 | 타입 | 필수/기본값 | 의미 |
| --- | --- | --- | --- |
| provider | string | 필수, null 불가 | BROWSER, GOOGLE, GITHUB, CODEX, APP |
| url | string | 선택/null | 최대 8192자. GOOGLE/GITHUB/CODEX의 HTTPS 공식 호스트만 허용, 사용자정보·비표준 포트 금지. 생략 시 공급자 계정 페이지. BROWSER/APP은 null만 허용 |
| applicationId | string | APP에서 필수, 나머지는 null | 최대 200자, 등록 앱 ID. APP은 저장된 앱 URL로 이동 |
| width | integer | 필수 | 320–3840, 원격 화면 폭 |
| height | integer | 필수 | 240–2160, 원격 화면 높이 |

BROWSER는 새 탭을 열지 않고 기존 Chromium 화면에 연결한다. CODEX 기본 페이지는 ChatGPT 계정 로그인이며 CLI 인증 시작을 대신하지 않는다. 앱에서 받은 Codex/GitHub 기기 인증 URL을 url로 전달하면 기존 로그인 작업을 승인할 수 있다. 인증 완료 확인은 원래 작업 API의 책임이다.

응답 201: `id`(필수 non-null string, 생성된 런타임 ID), `kind`(필수 string, APP), `label`(필수 string, 표시 이름), `url`(필수 string, 항상 빈 문자열). 인증 코드/쿠키/토큰은 응답하지 않는다. `/ws/runtime/{id}` 연결과 `DELETE /api/v1/sessions/{id}` 종료는 현재 로그인 세션에만 허용된다. 창 종료는 Chromium 프로필을 지우지 않는다. 새 포트나 DB 상태는 생성하지 않는다.

오류: 400 잘못된 enum/필드 조합/URL/크기, 401 미인증, 403 OWNER 또는 CSRF 실패, 404 등록 앱 없음·다른 세션 핸들, 409 런타임 12개 제한, 502 Chromium 열기 실패. 오류 응답은 기존 안전한 message 계약을 따르고 요청 URL/인증값을 출력하지 않는다. 네트워크 실패 자동 재시도 없이 사용자가 화면 다시 연결을 선택한다.

## Communications

공통 prefix `/api/v1/communications`, OWNER 인증. 변경은 CSRF 필수. callback GET만 OAuth state+동일 HttpSession을 확인한 뒤 인증정보를 저장한다. 전송 confirmation은 브라우저 전용이고 `dashboard-mcp` principal은 거절한다. `{id}`는 계정/프로필/요청 UUID, `{deviceId}`는 등록 장비 ID. Provider의 원본 ID는 query/body에 보존한다. 필수 여부는 아래 지정하며 response는 별도 null 표기 외 non-null. query 문자열의 기본값은 빈 문자열이다.

| Method/path | Request | Response |
| --- | --- | --- |
| GET `/providers` | 없음 | 200 Provider[] |
| GET `/accounts` | 없음 | 200 Account[] |
| POST `/accounts` | provider:string 필수 DISCORD, token:string 필수 1..8192 | 201 Account; 일반 사용자 token 거절 |
| DELETE `/accounts/{id}` | 없음 | 204; 계정/cache/cursor/actions 삭제 |
| POST `/oauth` | provider:string 필수 GMAIL/SLACK | 200 {url:string}; 10분 state |
| GET `/oauth/callback` | state:string 필수, code:string 선택(취소 시 없음) | 303 `/`; no-store/referrer 없음 |
| GET `/accounts/{id}/conversations` | cursor:string 선택 ≤2048, query:string 선택 ≤500 | 200 Page<ConversationView> |
| GET `/accounts/{id}/messages` | conversationId:string 필수 1..256, cursor:string 선택 ≤2048 | 200 Page<MessageView> |
| GET `/accounts/{id}/attachment` | conversationId/messageId:string 필수, attachmentId:string 필수 ≤1024 | 200 octet-stream, sanitized original filename, no-store, 최대 5 MiB |
| GET `/search` | query:string 필수 1..500 | 200 MessageView[], 캐시 최대100 |
| GET `/actions` | 없음 | 200 Action[], 만료 제외 |
| POST `/accounts/{id}/actions` | SendRequest | 201 Action; 전송 부수효과 없음 |
| POST `/actions/{id}/confirmation` | 없음 | 200 Action; 승인한 그대로 일회성 발송 |
| DELETE `/actions/{id}` | 없음 | 204; PENDING만 취소 |
| GET `/bridge` | 없음 | 200 {configured:boolean,limitation:string} |
| GET `/bridge/profiles` | 없음 | 200 Profile[] |
| POST `/bridge/profiles` | provider:string 필수 GMAIL/SLACK/DISCORD/KAKAOTALK, label:string 필수 1..80 | 201 Profile |
| POST `/bridge/profiles/{id}/sessions` | 없음 | 201 기존 SessionView {id,kind,label,url}, kind=APP/url=빈값 |
| GET `/bridge/profiles/{id}/snapshot` | 없음 | 200 {state:string,nodes:Node[],structuredMessages:false} |
| DELETE `/bridge/profiles/{id}` | 없음 | 204; 해당 runtime을 닫고 프로필 삭제 |
| POST `/windows/{deviceId}/observations` | 없음 | 200 WindowsSnapshot |
| POST `/windows/{deviceId}/sessions` | 없음 | 201 기존 SessionView, kind=REMOTE |

- Provider: id:string GMAIL/SLACK/DISCORD/KAKAOTALK, configured:boolean, mode:string OAUTH/BOT_TOKEN/WINDOWS_AGENT, limitation:string.
- Account: id/provider/label:string, capabilities:string[]. READ/SEND/REPLY/THREADS/SEARCH/ATTACHMENTS/UPLOAD/EDIT/DELETE/REACTIONS/READ_STATE/LABELS를 Provider·grant에 따라 활성화한다. token/cipher/external identity 원문은 반환하지 않는다.
- Page<T>: items:T[], nextCursor:string (빈값=마지막).
- ConversationView: accountId/provider/id/title/kind/preview:string, updatedAt:nullable epoch-ms long, unread:nullable boolean. kind MAIL/CHANNEL/DM/GROUP/THREAD. Discord THREAD는 공식 active guild threads 응답의 원본 ID를 사용하며 읽기·승인 전송의 conversationId로 전달한다.
- MessageView: accountId/provider/id/conversationId/sender/text/threadId:string, timestamp:nullable epoch-ms long, unread:nullable boolean, attachments:AttachmentView[]. 원본 ID 보존, 미확인 본문·발신자·thread는 빈 문자열.
- AttachmentView: id/name/mediaType:string, size:long bytes. 다운로드 권한은 원본 message의 attachment 목록에서 재확인한다.
- SendRequest: text 필수 nonblank string ≤32000; conversationId/replyTo 선택 nullable string ≤256; recipient 선택 nullable string ≤320; subject 선택 nullable string ≤300. nullable/생략은 빈값 정규화. Gmail 신규 메일은 recipient 필수. Slack/Discord는 conversationId 필수. 메일 헤더 CR/LF 거절. Discord 실제 전송은 2000자 제한.
- Action: id/accountId/provider/accountLabel/state/resultId:string, expiresAt:epoch-ms long, message:정규화된 SendRequest. state=PENDING/SENDING/SENT/UNKNOWN/CANCELLED. resultId는 성공 시 원본 ID, 그 외 빈값. 승인 요청 원문은 DB에서 암호화한다.
- Profile: id/provider/label:string. 원본 로그인 계정 확인을 보장하지 않는 브라우저 프로필이다.
- Node: role/name:string; 역할·텍스트 관측만 제공한다. 브라우저 state=STOPPED/LOGIN_OR_NAVIGATION_REQUIRED/SCREEN_AVAILABLE.
- WindowsSnapshot: state:string AGENT_UNAVAILABLE/APP_NOT_RUNNING_OR_NO_WINDOW/ACCESSIBILITY_OBSERVED/ACCESSIBILITY_UNAVAILABLE/UNAVAILABLE; loginState=UNKNOWN; structuredMessages=false; revision:string (관측 hash 또는 빈값); nodes:[{role:string,name:string,canInvoke:boolean,canSetValue:boolean}]. pattern 존재는 자동 조작 지원 약속이 아니다.

오류는 기존 `{message}` 사용: 400 입력/헤더/Provider/리소스 ID 오류, 401 미인증, 403 권한/CSRF/OAuth 세션 state 불일치/MCP 승인, 404 계정·profile·attachment·만료 action 없음, 409 미설정·권한만료·중복승인·지원범위·동시profile 한도, 413 응답/첨부 한도, 429 Provider 요청한도/대기요청 한도, 502 Provider/Bridge 연결 실패. 원문 upstream 오류·token·코드는 오류 응답에 포함하지 않는다. 전송 실패는 UNKNOWN으로 남고 자동 재실행하지 않는다. 외부 HTTP는 고정 HTTPS origin, redirect 금지, 연결10초/요청25초, JSON8MiB 제한. 화면 broker는 고정 서버 host loopback Chromium 9224/Wine 9225, 연결3초/요청20초/1MiB.

### Communications 확장 계약

| Method/path (동일 prefix) | Request | Response |
| --- | --- | --- |
| GET `/accounts/{id}/labels` | 없음 | 200 [{id:string,name:string}] |
| POST `/accounts/{id}/labels` | conversationId 필수 string 1..256, add/remove 필수 string[] 각각≤20/ID≤100 | 204; 원본 계정의 유효 라벨만 허용 |
| GET `/accounts/{id}/thread-messages` | conversationId/threadId 필수 string 1..256, cursor 선택 string≤2048 | 200 Page<MessageView>; Slack Bot DM/그룹DM만 |
| GET `/accounts/{id}/cached-messages` | conversationId 필수 string 1..256 | 200 MessageView[], 시간/ID순 최근200 |
| POST `/accounts/{id}/message-actions` | MutationRequest | 201 Action; 아직 실행하지 않음 |
| POST `/accounts/{id}/synchronizations` | 없음 | 200 {phase:FULL/HISTORY,processed:int,hasMore:boolean,reset:boolean,synchronizedAt:epoch-ms long}; Gmail만 |
| POST `/events/slack` | X-Slack-Request-Timestamp/X-Slack-Signature 필수; raw JSON≤1MiB | 200 {challenge:string,accepted:boolean}; 공개 HMAC 인증/CSRF 제외 |
| GET `/gateway` | 없음 | 200 {enabled:boolean,accounts:[{accountId:string,state:string}]} |
| POST `/bridge/profiles/{id}/stops` | 없음 | 204; 로그인 프로필 유지·해당 화면 종료 |

SendRequest에 `attachments` 선택 nullable 배열≤5를 추가했다. Upload={name:string 필수1..180,mediaType:string 필수1..100,data:string 필수 base64≤7000000}. 총 decoded5MiB, 빈 파일 불허, 허용 MIME=text/plain,text/csv,application/json,application/pdf,image/png,image/jpeg. Content signature/UTF-8를 검증한다. Action 응답의 message.attachments는 data를 빈 문자열로 비운 metadata이며 재전송용 요청이 아니다.

Action은 추가 필드 `operation:string`(SEND/EDIT/DELETE/REACTION), `mutation:nullable MutationRequest`를 가진다. SEND는 mutation=null, 그 외 실제 작업 snapshot. MutationRequest={operation:필수 EDIT/DELETE/REACTION,conversationId/messageId:필수 string1..256,text:선택 nullable string≤32000,reaction:선택 nullable string≤100}. EDIT는 nonblank text, REACTION은 nonblank reaction. Slack은 emoji 이름, Discord는 Unicode/custom emoji 표현을 그대로 공식 API에 전달한다. Action.message는 모든 작업의 확인 화면용 SendRequest이며 mutation에서는 subject가 operation, replyTo가 원본 메시지 ID다. SENT는 해당 작업의 성공이고 DELETE일 때 원본 cache도 제거한다.

Capability에 UPLOAD/EDIT/DELETE/REACTIONS/READ_STATE/LABELS를 구현했다. 실제 grant 없는 Slack 파일·reaction·chat 작업과 Gmail modify 작업은 비활성화한다. Gmail scope는 gmail.modify. 첨부 전송 실패/Slack shares 메시지 ID 지연은 UNKNOWN, 자동 재시도 없음. 다운로드는 API로 소유 attachment 재확인 후 고정 files.slack.com/cdn.discordapp.com host로만 요청하고 redirect를 따르지 않는다.

Slack URL verification도 서명 필수; raw body 변경·5분 지난 timestamp·미설정 secret은403, 크기 초과413, invalid JSON/event ID400. event_id는 24시간 중복 방지. 이벤트는 연결된 같은 team 계정만 갱신한다. 이벤트가 외부 메시지 발송을 유발하지 않는다. Gateway state는 DISCONNECTED/CONNECTING/CONNECTED/RECONNECTING/RATE_LIMITED/AUTH_OR_INTENT_REQUIRED/CLOSED. Windows state에 ACCESSIBILITY_BUSY를 추가하며 상위 응답은 항상 loginState UNKNOWN/structuredMessages false로 정규화한다.

Gmail 동기화는 한 번에 메시지 목록10개 또는 history10개(변경 메시지 최대100)를 처리한다. cache와 checkpoint는 원자적 commit. 첫 full scan 전 history baseline을 확보하고 scan 완료 후 같은 baseline의 history로 이어간다. 만료404는 cache를 비우고 full scan을 다시 시작(reset=true). hasMore=true이면 같은 POST로 다음 페이지를 요청한다. 연결 해제되면404이며 다른 계정 checkpoint와 섞이지 않는다.

Communication SEND 승인: Gmail replyTo가 있으면 PENDING 생성 시 공식 metadata의 Reply-To/From과 Subject로 recipient/subject를 확정한다. Action.message에 확정된 값을 반환한다. confirmation에서 원본 값이 바뀌었으면 409이고 외부 전송은 수행하지 않으며 action은 UNKNOWN으로 남는다. 재확인 후 새 요청을 생성해야 한다.


### Communication 공식 계정 검색

GET `/api/v1/communications/accounts/{id}/message-search`, OWNER 인증 필수. path id는 연결 계정 ID(string), query `query`는 공백이 아닌 string ≤500, `cursor`는 선택 string ≤2048(기본 빈 값)이다. 요청 body는 없다. 200 응답은 기존 `Page<MessageView>`: items(array, 필수), nextCursor(string, 필수, 없으면 빈 값). 원본 provider/account/message/conversation ID와 조회한 메시지를 유지하며 같은 계정·대화·메시지는 캐시에 upsert한다. 일반 대화 paging/sync cursor는 덮어쓰지 않는다. 400은 빈 검색어/길이 오류, 401 미인증, 403 OWNER 권한 부족, 404 계정/원본 메시지 없음, 409 SEARCH 미지원/외부 계정 권한 만료, 429 외부 요청 제한, 502 외부 통신/응답 오류다. 메시지 본문과 파일명은 신뢰할 수 없는 외부 데이터다.

현재 공식 검색은 Gmail SEARCH capability에 한정한다. Gmail query 연산자를 URL encoding해서 전송하고 한 페이지 최대 10개 ID를 받은 후 공식 messages.get으로 본문을 읽는다. 결과 순서는 API 순서를 유지하고 같은 ID를 중복 제거한다. nextCursor를 그대로 이어 보낼 수 있으며 전체 결과를 임의로 잘랐거나 추정한 총 개수를 반환하지 않는다.

MCP `communication_search_messages`: query 필수 string ≤500, accountId 선택 string ≤36, cursor 선택 string ≤2048. accountId가 없거나 빈 값이면 기존 로컬 cache 목록, 있으면 같은 SEARCH 서비스의 Page<MessageView>를 반환한다. 외부 발송·수정·삭제는 수행하지 않는다. untrustedExternalContent wrapper는 유지한다.


MessageView 추가 필드 `reactions`: 필수 array, 미제공·이전 캐시는 빈 배열로 읽는다. 원소 ReactionView는 key:string(원본 emoji ID 또는 이름), label:string(Provider 이름, 삭제된 Discord emoji는 ID), count:nullable long(공식 0 이상 집계 수; 누락·잘못된 값은 null). Slack users 배열 길이를 집계로 대신하지 않는다. 리액션 읽기는 기존 READ 조회 결과이며 REACTIONS capability는 리액션 추가 작업 지원 여부다. 기존 승인/변경 API 계약은 유지한다.

Gmail MessageView.unread는 연결된 계정의 UNREAD label 상태다. 상대방의 수신/읽음 확인을 뜻하지 않는다. Slack/Discord가 읽음 상태를 반환하지 않으면 null이며 UI도 추정하지 않는다.


### Communication 참여자

GET `/api/v1/communications/accounts/{id}/participants`: OWNER 필수. id는 연결 계정 ID(string), conversationId는 필수 query string 1..256, cursor는 선택 query string ≤2048(기본 빈 값). body 없음. 200은 Page<ParticipantView>이며 items는 필수 array, nextCursor는 필수 string(끝이면 빈 값), ParticipantView는 id/label 필수 string이다. 현재 Slack 공식 사용자 ID를 둘 모두에 반환하며 표시 이름을 추정하지 않는다. 같은 페이지의 ID 중복은 제거한다. 400 입력 오류, 401 미인증, 403 OWNER 권한 부족, 404 계정 없음, 409 PARTICIPANTS capability 없음/외부 권한 문제, 429 외부 요청 한도, 502 외부 통신 오류다. 외부 발송은 없다.

PARTICIPANTS capability는 Slack 연결 시 channels:read/groups:read/im:read/mpim:read 중 하나 이상 승인된 경우에 제공한다. 각 대화의 실제 접근 권한은 Slack이 다시 검사한다. 페이지 크기는 100, 추가 users:read 범위는 요청하지 않는다. 미지원 연결은 UI에서 버튼을 비활성화한다. 기존 연결은 OAuth 재연결 시 최신 capability를 반영한다.

MCP `communication_list_participants`: accountId 필수 string ≤36, conversationId 필수 string ≤256, cursor 선택 string ≤2048. 같은 서비스의 Page<ParticipantView>를 untrustedExternalContent wrapper로 반환한다.

Communications Bridge의 configured는 자동 생성된 내부 인증 파일을 읽을 수 있는지 나타내며, 공급자 로그인 완료를 의미하지 않는다. 브라우저 준비 전 프로필 생성/실행은 409로 거절한다. 내부 키는 응답에 포함하지 않는다. 서비스 계정 로그인은 원격 화면에서 사용자가 직접 수행한다.

Bridge Create.provider는 GMAIL|SLACK|DISCORD|KAKAOTALK다. KAKAOTALK의 start/snapshot/stop/delete는 고정 Wine broker 9225로 라우팅하고 VNC 5920~5923만 허용한다. 다른 프로필은 기존 9224/5910~5913을 사용한다. Wine snapshot.state는 STARTING|INSTALLING|RUNNING|ERROR|APPLICATION_EXITED|STOPPED이며 RUNNING은 프로세스 시작 상태로 로그인 완료를 뜻하지 않는다. nodes는 빈 배열, structuredMessages=false다.

### POST /api/v1/chrome/sessions

OWNER 로그인과 CSRF가 필수다. path/query parameter는 없다. JSON 요청은 `width`(필수 non-null integer, 320..3840), `height`(필수 non-null integer, 240..2160)로 원격 화면 크기를 지정한다. 서버 Chromium의 기존 화면에 연결하며 새 탭을 생성하거나 URL로 이동하지 않는다. 브라우저 환경설정의 CLIENT/REMOTE 모드와 무관하게 서버 브라우저를 사용한다.

201 응답은 SessionView: `id`(non-null string, 세션 UUID), `kind`(non-null string, APP), `label`(non-null string, Chrome), `url`(non-null string, 빈 값). 세션은 요청의 HTTP 로그인 세션에 귀속된다. 기존 `/ws/runtime/{id}` 연결 및 `DELETE /api/v1/sessions/{id}` 종료 계약을 사용한다. 종료는 Chromium 프로세스·프로필·탭을 삭제하지 않는다. Chrome 세션에만 VNC 오디오를 협상하며 오디오 데이터는 인증된 WebSocket을 통해 전달된다.

오류는 기존 공통 오류 형식이다. 400은 화면 크기 누락/범위 위반/잘못된 JSON, 401은 비로그인, 403은 OWNER 권한 또는 CSRF 누락, 409는 전체 실행 세션 한도 12개 초과다. 한도 메시지는 기존 RuntimeService의 열린 실행 탭 안내를 사용한다. 원격 연결 실패는 생성 이후 WebSocket 1011 종료와 UI의 재연결 안내로 표시된다. 다른 로그인 세션의 조회·삭제는 기존 계약대로 404다.

### ?? ???? ??

`POST /api/v1/sessions/{id}/clipboard`? OWNER ? CSRF ??? ????. `id`? ?? ??? ??? ???? ??? REMOTE ?? ?? ID??. ?? ????? ??.

??: `{ "text": "??? ???" }`. `text`? ?? ????? null?NUL ??? ???? ???. ?? 32,000 UTF-16 ?? ??, ? ???? ???? ?????. ??? ????????? ??? ???.

?? 200: `{ "delivered": true }`. ?? boolean `delivered`? true?? ?? ?? Linux X11 ????? ?????. false?? ?? VNC/RDP ????? UI? ?? Guacamole ????? ????. ?? ?? ?? ??? false? ??? ???.

??: 400 ?? ?? ??, 401 ???, 403 OWNER/CSRF ??, 404 ?? ?? ?? ?? ??? ??, 409 REMOTE? ???? ???? ?? ??, 502 SSH??? ?? ??????? ?? ??. ?? ?? ?? ??? ????.
