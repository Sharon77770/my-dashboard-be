# 상태

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
