# 상태

Studio 파일 탭은 `content`, `saved`, `revision`, `dirty`와 활성 경로를 브라우저 메모리에 유지한다. Monaco model과 view state도 파일별로 유지하며 탭을 닫거나 프로젝트가 변경될 때 해제한다. 다른 대시보드 앱으로 이동할 때 Studio DOM을 재생성하지 않으므로 편집 상태가 유지된다. 전체 페이지 새로고침에는 버퍼를 복원하지 않으며 기존 beforeunload 경고와 마지막 장비/루트 저장만 적용한다.

IDE/장비 공통 Codex의 `sending`은 비동기 CLI 준비와 추가 지시 전송을 단일 실행으로 제한한다. 사용자 말풍선은 임시 ID로 시작해 서버의 첫 userMessage ID로 연결하며, 같은 ID의 재수신은 기존 DOM을 갱신한다. 미확인 요청 실패는 임시 행만 제거한다. 대화 전체 복원은 서버 thread를 기준으로 하고, AI 비서 복원은 turn별 중복 item ID를 제외한다. 본문 문자열만으로 중복을 판정하지 않는다. IDE `codex-focused`, 장비 설정 details.open, AI 비서 sidebarOpen은 표시 상태이며 서버 작업·인증 상태와 독립한다.

공통 foreground 로딩은 `WorkspaceUI.beginTask`의 요청별 Map으로 관리한다. 첫 요청에서 비차단 안내를 표시하고 마지막 finish 후 호출자의 렌더 microtask가 끝난 다음 숨긴다. finish는 중복 호출에 안전하며 겹치는 요청 중 하나가 먼저 끝나도 안내를 유지한다. `data-loading`은 실시간 조회 조정에만 사용하며 입력·스크롤·포커스·기존 inert 상태를 변경하지 않는다. quiet 폴링·메모 자동 저장은 이 상태에 포함하지 않는다.

장비 Codex job은 기존 `RUNNING → SUCCEEDED/FAILED/CANCELLED`를 사용한다. 서버 메모리의 owner와 deviceScope가 다른 작업 범위 접근을 거부한다. UI selected는 적용된 장비/폴더, prepared는 해당 연결에서 CLI 준비 완료 여부, busy/job은 변경 잠금·취소 상태다. 장비/폴더 적용 시 준비·계정·대화·기록 목록을 초기화하며 실행 중 전환은 막는다. 실제 계정/대화는 원격 장비별 CODEX_HOME에 저장한다. 브라우저 sessionStorage의 `device-codex:` 키에는 장비/폴더별 thread ID 및 모델·추론 강도·실행 범위·승인 정책을 기록한다. `data-sidebar-open`은 연결·기록 사이드바의 UI 상태이며 닫을 때만 사이드바에 inert를 적용한다. 토글은 본문 DOM·작성 중 입력·스크롤을 유지한다. 처음 장비 선택 시 열고 연결/기록 선택 완료 시 닫는다.

Database Studio 연결 accessMode는 READ_ONLY(SELECT 한 문장과 DB read-only 강제) 또는 READ_WRITE(DB 권한 내 실행)다. 실행은 RUNNING → SUCCEEDED/FAILED/CANCELLED로 끝나며 worker 메모리의 실행 결과는 재시작 시 복원하지 않는다. resultType은 QUERY/MUTATION 또는 미완료·실패의 빈 값이다. 편집 중 SQL 탭은 브라우저 메모리 상태이며 연결·이력·즐겨찾기는 Workspace SQLite에 남는다.

| 상태 | 저장 | 소유자/수명 |
| --- | --- | --- |
| OWNER 계정 | 메모리 | 환경변수에서 시작 시 구성 |
| 로그인 세션 | Servlet 메모리 | 비활성 기본 30분, 로그아웃/재시작 시 폐기 |
| 등록 장비/앱/즐겨찾기 | SQLite | 명시적 수정/삭제까지 |
| 클립보드 | SQLite | 1~1440분, 만료 시 읽기 제외/조회 시 정리 |
| 최근 작업 | SQLite | 최대 최근 100개, 같은 대상/경로 재사용 시 갱신 |
| 화면/브라우저 설정 | SQLite singleton | 저장 후 전체 UI 및 다음 실행에 반영 |
| 탭 위치 | SQLite | 최대 20개, close 시 제거 |
| 실행 세션 | 메모리/실제 연결 | 최대 12개, 탭 종료·소켓 종료·로그아웃·만료·재시작 시 정리 |
| 측정 장비 상태 | 조회 응답/브라우저 메모리 | 새로고침 시 재측정 |
| Service/Resource binding/카탈로그 활동 | SQLite | Service 삭제까지, 외부 리소스 삭제는 orphan 표시 |
| Service Health/Context/외부 활동 | 조회 응답 | 연결된 기존 리소스에서 매 조회마다 계산 |

값:
- remoteProtocol: NONE(미사용), RDP, VNC.
- browser mode: CLIENT(현재 브라우저), SERVER(Compose Chromium), REMOTE(등록 VNC+Chromium).
- theme: dark/light. compact: boolean.
- kind: FILES, TERMINAL, REMOTE, APP, DOCKER, GPU. 최근 이력은 실제 여는 FILES/TERMINAL/REMOTE/APP을 기록한다.
- 장비 상태: ONLINE(실제 로컬/SSH 계측), REACHABLE(포트만 확인), UNAVAILABLE(접속/계측 실패). 미계측 수치는 null.
- Service Health: HEALTHY(확인된 신호 정상), DEGRADED(일부 신호 문제 또는 미확인), DOWN(운영 신호 장애), UNKNOWN(확인된 정상/장애 없음). Signal도 HEALTHY/DEGRADED/DOWN/UNKNOWN을 사용한다. 연결하지 않은 타입은 평가하지 않는다.
- ServiceResource type: GITHUB_REPOSITORY, GITHUB_ORGANIZATION, DEVICE, DOCKER_CONTAINER, TELEMETRY, ENDPOINT, FILE. 기존 장비/Telemetry 삭제 후 참조는 orphaned=true로 조회된다.

실행 세션: 생성(준비) -> 최초 WS attach -> 실행 -> 종료. 준비 상태로 60초 이상 미접속 시 정리한다. 같은 세션 핸들을 두 WS에서 붙일 수 없다.
탭을 복원해도 실행 핸들은 복원하지 않는다. 다시 활성화하면 새 연결을 생성한다. 로그아웃한 계정의 핸들은 다른 로그인에서 사용할 수 없다.
브라우저 프로필과 웹사이트 로그인은 browser-profile 볼륨에 남는다. 대시보드 로그아웃은 스트림을 끊으며 외부 웹사이트 로그아웃은 브라우저에서 별도로 수행한다.


Planner 서버 상태: calendar_events, timetable_terms, timetable_courses, timetable_meetings. 일반 일정은 allDay boolean, 수업 요일은 1~7이다. 별도 진행 상태 enum은 없다. 프런트 상태: 선택 월/날짜/학기 및 API 조회 데이터. 캘린더와 시간표의 늦은 응답은 조회 버전으로 무시한다. 저장은 성공 후 재조회하며 오류 시 폼을 유지한다.

## Studio 작업

HTTP 세션 소유 job: RUNNING → SUCCEEDED / FAILED / CANCELLED. 동시 4개, 실행 15분, 결과 최근 32개/30분. 완료 이후 늦은 응답은 취소 상태를 변경하지 않는다. local은 로컬 프로세스 핸들, 등록 장비는 SSH 연결을 소유한다. 로컬 취소는 stdin EOF 전달 후 2초 안에 종료하지 않으면 남은 자식/부모 프로세스를 강제 종료한다. 브라우저 편집 버퍼는 메모리만 사용하고 파일 revision으로 저장 충돌을 확인한다. SQLite schema 변경 없음.

## Launcher 표시 상태

홈: version=1, pages, locked, dock, items. item.type은 app/folder/widget. page/x/y/w/h는 격자 좌표, 폴더 apps는 순서가 있는 앱 ID다. 계정별 브라우저 localStorage로 저장하며 검증/반응형 투영/충돌 처리는 HomeGrid가 담당한다. editing/page/드래그는 메모리만 사용한다. 내장 앱 탭은 계정별 브라우저 저장, 실제 실행 탭은 기존 SQLite 저장이다. IDE mobilePane은 editor/explorer/inspector, 패널 너비는 브라우저 저장이다. Codex 결과는 최근 20개 메모리 기록이며 프로젝트 변경 시 지운다. [상한·복원 규칙](../launcher.md).

### Codex 세션 상태

새 세션은 id가 빈 draft이며 첫 turn 때 thread/start로 저장된다. thread ID는 CLI 저장 상태, sessionStorage의 마지막 선택은 UI 상태다. run은 RUNNING 동안 item 갱신과 승인 대기를 표시하고 완료된 thread/read로 동기화한다. 중단/실패 시 프롬프트를 유지한다. 프로젝트를 바꾸면 표시·첨부를 초기화한다.

Workspace의 navigationTrail은 현재 페이지 메모리의 최대 50개 앱/런타임 화면 이력이다. 뒤로 이동은 global bar 또는 Alt+Left에서 실행하며 닫힌 대상을 건너뛰고 복원 중에는 새 이력을 추가하지 않는다. `data-active-view`와 `data-runtime-focus`는 mobile shell의 표시 상태이며 저장하지 않는다. 앱 및 서버 세션의 기존 영속 저장 계약은 유지한다.

장비 networkMode는 DIRECT/TAILSCALE 중 하나다. 초기값은 DIRECT이고 OWNER가 장비 수정 또는 SSH 등록 시 변경한다. 생략된 기존 프로필 수정은 기존 모드를 유지한다. 기본 로컬 서버 프로필은 DIRECT다. Tailscale 인증 상태 자체는 기존 sidecar 볼륨이 소유한다.

장비 로그 UI 상태는 선택 장비/종류/대상, 활성 job ID, 세대 번호, 마지막 이벤트 sequence, 최대 200,000자 화면 버퍼로 구성한다. 대상·앱 변경 시 취소하고 늦게 생성된 job도 취소한다. 서버 상태는 기존 Studio QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED를 재사용한다. 저장된 장비 프로필 외 새 DB 상태는 없다.

Tailscale 설정은 tailscale.js의 dialog 상태(열림/닫힘, 요청 중)와 서버의 BackendState/loginUrl/pending/error를 분리한다. 창이 열린 동안 3초마다 읽고 닫으면 예약 조회를 중지한다. 인증 상태는 sidecar가 소유하고 UI나 SQLite에 토큰/링크를 저장하지 않는다.

로그 표현 옵션(format raw/json, color syntax/levels/none, wrap/frame boolean)은 브라우저 계정별 localStorage에 저장한다. 로그 본문은 저장하지 않으며 표현 옵션 변경은 실행 작업을 취소하거나 생성하지 않는다.

클라우드 파일은 ACTIVE(files) → TRASHED(trash) → ACTIVE(복원) 또는 영구 제거로 이동한다. TrashRecord(path 가상 경로, deletedAt epoch ms)는 휴지통 UUID 디렉토리의 record.json에 보관하며 실제 payload와 함께 소유한다. 드라이브 UI는 현재 폴더/휴지통 모드, 선택 경로 집합, 내부 클립보드, 업로드 진행/취소 상태를 메모리에 보유한다. 파일 데이터·선택 목록은 localStorage에 저장하지 않는다.

원격 구성 상태는 IDLE/RUNNING/READY/BLOCKED이며 서버 메모리에 저장한다. 관리된 VNC 연결 정보는 서버 데이터 디렉터리에 암호화된 JSON으로 보존하고, 대상 계정의 화면 프로세스 상태는 별도 사용자 디렉터리에 둔다. [상태·재시작 정책](../remote-desktop.md).

Tailscale 인증 상태는 tailscale-state 볼륨이 소유한다. NeedsLogin 또는 인증 실패가 공유 네트워크를 재생성하지 않는다.

클라우드 에디터는 원본 텍스트, 편집 버퍼, revision, 저장 중 상태와 이미지 object URL을 메모리로 보유한다. 앱 전환 시 유지하고 목록 복귀 시 폐기하며 이미지 URL을 해제한다. 목록 단축키는 에디터에서 비활성화한다.

메모장 서버 상태: NoteKind=FOLDER/DOCUMENT, revision은 0부터 시작하고 메타데이터/본문 저장 및 기존 MCP Markdown의 첫 조회 변환 성공마다 증가한다. 이미지 FK는 문서 삭제 시 cascade한다. UI는 현재 폴더/문서, 펼친 폴더, 검색, 변경 횟수/저장 완료 횟수와 단일 진행 중 저장 Promise를 메모리에 가진다. 저장 중 추가 편집은 미저장 상태를 유지하고 다음 revision으로 저장한다. 충돌/실패 시 자동 덮어쓰기 대신 수동 재시도·Markdown 보관·최신 문서 열기를 제공한다. 앱 전환은 편집기를 폐기하지 않는다.

서버 Codex assistant의 Studio Job 상태는 RUNNING → SUCCEEDED/FAILED/CANCELLED이며 owner HTTP session으로 제한된다. Thread와 usage는 Codex CLI/App Server가 소유한다. 사이드바 목록은 thread/list의 페이지와 검색 결과이고, 선택한 thread/read 본문을 현재 화면에 표시한다. 새 채팅은 ID 없는 draft이며 첫 turn 뒤 목록에 저장된다. 앱 열림 상태는 workspace의 활성 뷰와 실행 중인 앱 목록에 반영하고, 마지막 열린 thread ID·화면 대화 캐시·navigation delivery cursor는 sessionStorage에 둔다. 첨부 파일 본문과 이미지 data URL은 전송 전 브라우저 메모리에만 두고 성공 시 제거하며, 이름만 대화 캐시에 남긴다. Codex 로그아웃은 현재 브라우저의 대화·첨부 상태를 지우고 서버 CLI 인증을 종료하지만 저장된 thread를 삭제하지 않는다. AssistantEvents는 최근 100개의 navigation event를 메모리에 보유하며 재시작하면 초기화된다. MCP bearer token은 env 설정값 또는 프로세스 시작 때 생성한 메모리 값이며 저장하지 않는다. GitHub 위험 작업 승인은 작업·저장소·Release ID에 묶인 10분 메모리 상태이고 실행 전 한 번만 소비한다. assistant 실행 취소는 현재 job만 종료하고 적용된 일정/노트/GitHub 변경을 되돌리지 않는다.

AI 비서의 모델·추론 강도 선택은 설정 창과 채팅 입력창에서 동기화한다. 사용자가 마지막으로 고른 모델 ID와 추론 강도만 localStorage에 보관하고, Codex 모델 목록을 다시 불러올 때 가능한 값인지 검증한다. 저장한 값이 현재 목록에 없으면 사용 가능한 기본값을 표시하되 저장값은 자동으로 덮어쓰지 않는다. 대화 내용과 첨부 본문은 이 설정 저장소에 넣지 않는다.

Service Onboarding Draft는 Codex thread ID당 최신 하나를 서버 메모리에 연결한다. 상태는 `DRAFT → APPROVED → COMMITTED`이며 수정하면 `APPROVED → DRAFT`, 취소 또는 30분 만료 시 제거된다. Draft revision과 기존 Service의 수정 시각·연결 집합을 검사한다. `DRAFT`의 후보 선택은 영속 Service와 분리되고, `APPROVED`는 현재 revision의 브라우저 승인만 의미한다. 서버 재시작 후 재탐색이 필요하다.
## 실시간 연결 상태

브라우저 연결 상태는 `connecting → connected → reconnecting`이며 인증 종료 시 `expired`로 재시도를 멈춘다. 화면 갱신 실패는 표시 상태 `stale`로 나타낸다. epoch/revision은 프로세스별 알림 순서를 위한 메모리 상태로 SQLite에 저장하지 않는다. dirty topic은 조회 중 누적해 다음 한 번의 갱신으로 합친다. 폼·에디터·선택·스크롤은 클라이언트 소유 상태다. 메모의 외부 변경은 기존 revision 충돌 검증을 유지한다.

병역 복무 유형은 `ARMY` 육군, `NAVY` 해군, `AIR_FORCE` 공군, `MARINES` 해병대, `SOCIAL_SERVICE` 사회복무, `CUSTOM` 직접 설정이다. 일정 종류는 `LEAVE` 휴가, `TRAINING` 훈련, `DUTY` 근무, `OTHER` 기타다. 계산 상태는 서울 날짜에 따라 `UPCOMING` 입대 전 → `SERVING` 전역 당일까지 → `COMPLETED` 다음 날부터이며 DB에 저장하지 않는다. 이정표 종류는 `ENLISTMENT`/`PROMOTION`/`DISCHARGE`다. 저장 revision은 생성 1, 수정 시 증가하고 요청의 기존 revision 불일치는 409다. 월·선택 날짜·폼 초안·서버 시각 보간용 단조 증가 시계는 브라우저 상태다. 조회 generation이 달라진 지연 응답은 저장 결과를 덮어쓰지 않는다.

## 인증 브라우저 화면

원격 화면 핸들은 기존 로그인 세션 소유 런타임 상태이며 영속 DB에 저장하지 않는다. 연결 중/연결됨/연결 종료/오류는 화면 연결 상태일 뿐 공급자 인증 완료 상태가 아니다. UI는 세대 번호로 닫기·재연결 후 늦은 응답의 핸들을 즉시 정리하고 인증 코드·입력값을 창 닫기에 제거한다. Chromium 쿠키는 browser-profile에 유지되며 각 CLI 인증 상태와 별개이다.

## Communications

Communications 계정과 메시지 cache는 SQLite, OAuth state는 10분 세션 소유 메모리, draft는 브라우저 메모리, 탭 metadata는 계정별 sessionStorage다. action=PENDING/SENDING/SENT/UNKNOWN/CANCELLED; 허용 전이는 PENDING→SENDING→SENT 또는 UNKNOWN, PENDING→CANCELLED. Bridge 관측은 login/구조화 성공을 추정하지 않는다.
상세: [Communications](../communications.md).

Communications 브라우저 탭 저장 version 2: panes(accountId/id/title/kind), selected(계정·대화 key), split(boolean). 기존 배열도 읽고 허용 필드만 복원한다. kind는 MAIL/CHANNEL/DM/GROUP/THREAD 또는 빈 값이며 계정 권한은 서버에서 다시 읽는다. 메시지·초안·replyTo·인증정보는 복원 대상이 아니다.

### Communications 표현 상태

기존 탭·분할 저장 계약은 유지한다. `detailsOpen`은 기본 false인 일시적 UI 상태이며 `messageOpen`은 모바일 목록/대화 전환 상태다. 실시간 캐시 갱신은 목록으로 돌아간 사용자를 대화로 이동시키지 않는다. `listQuery`는 서버 검색과 별개의 로컬 목록 필터다. 계정별 조회 오류와 각 pane의 loading/error는 분리하며, 오류가 발생해도 이미 읽은 메시지와 작성 중인 초안을 보존한다. API/DB/인증 상태 변경은 없다.

## Chrome UI 상태

Chrome의 connection/id, 요청 sequence, fit 여부는 페이지 메모리에만 둔다. 재연결 시 이전 원격 연결을 닫고 새 세션을 생성한다. 원격 profile/tabs는 browser-profile 볼륨의 Chromium 상태이며 DB 변경은 없다. 텍스트 입력은 전송·화면 이탈 시 비우고 clipboard/localStorage에 보관하지 않는다. 화면을 나가면 연결을 해제한다.

원격 준비 상태는 IDLE/RUNNING/READY/BLOCKED를 유지하고 `stage`로 CHECKING/INSTALLING/STARTING/VERIFYING을 구분한다. `code`는 복구 분류이며 원문 실행 출력은 포함하지 않는다. 설치용 sudoPassword는 요청 write-only 값으로 작업 동안만 유지하며 입력창은 전송/종료 시 지운다. 연결 도우미의 세대 번호로 오래된 조회가 다른 장비의 화면을 수정하지 못하게 한다. 창을 닫아도 서버 작업은 유지한다.

- ?? ???? ??? ??? REMOTE ????? ????. ?? ? ??? ??? ???? ?? ? ?? ??? ????. ?? Linux ???? ?? ????? ?? ?? ?? ?? 30? ? ???? DB ??? ???? ???.
