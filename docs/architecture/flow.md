# 주요 흐름

Codex 전송: 전송 진행 플래그 설정 → CLI 준비 → 임시 사용자 행 표시 → job 시작 → 서버 userMessage로 같은 행 확인 → 후속 item ID별 갱신 → 최종 thread 표시. 준비·추가 지시 중의 연속 전송은 한 번만 실행한다. 서버 확인 전 실패하면 임시 행을 제거하고 비어 있는 입력창에 요청을 복원한다. IDE 채팅 확대와 장비 연결 설정 접기는 DOM과 작업을 유지한 채 표시 영역만 바꾼다.

사용자 조회·저장 → 공통 비차단 로딩 안내 → 응답/오류 처리 → 화면 반영 → 모든 foreground 요청 완료 후 안내 숨김. API wrapper의 finally가 finish를 호출한다. 로딩 중에도 탐색·입력·스크롤·dialog 조작을 허용한다. 자동 폴링·메모 자동 저장은 quiet로 처리한다. 회귀 검사는 `tools/launcher/loading-test.cjs`에서 동시 요청·상호작용 허용·실패 정리·포커스 유지를 확인한다.

장비 Codex: 장비 카드/앱 라이브러리 → SSH 장비·절대 작업 폴더 선택 → CLI 준비(공식 release checksum, 동일 버전 host) → 전용 CODEX_HOME에서 모델·계정 확인 → 기기 코드 로그인 → 요청·직접 또는 자동 심사·실행 결과 표시. 사용자 로그 요청은 장비 셸에서 프로세스/포트와 실제 보관 로그를 조사하도록 지시한다. 조회 한계는 실행 결과로 설명한다. 설치/SSH 오류를 표시하고 재시도할 수 있으며, 장비가 없으면 SSH 등록 안내를 표시한다. 취소/로그인 종료 시 인증 URL·코드를 지운다. 원격 데이터 변경은 SSH 사용자 OS 권한과 Codex sandbox/승인 경계 내에서 일어난다.

서비스 오류 분석: AI 비서 MCP 또는 OWNER REST → ServiceLogService의 서비스 연결·시간 구간 검증 → ServiceLogAdapter → 기존 CommandAdapter/검증된 SSH → Python의 고정 Docker 로그 명령 → 오류 후보·가림 처리·조회 한계 반환. 로그 본문은 Activity/Context에 저장하지 않는다. 비서는 부분 결과를 기간별로 나눠 확인하고 운영 오류와 CI 검사 실패를 구분한다.

Codex 계정 표시: 각 실행 환경의 `account/read` → 이메일/인증 종류/요금제 DTO → 서버 비서 사이드바·설정 또는 SSH 에디터 상단. 로그인 후 재조회하며 로그아웃/폴더 변경 시 이전 이메일을 지운다. 명령 시작의 null 출력은 빈 문자열로 전달하고 후속 출력 delta·승인·완료 이벤트를 계속 처리한다.

## Workspace Memory

명시적 저장/승인 → SQLite Memory → 관련 텍스트 검색·점수화 → 최대 8개·2,400자 컨텍스트 → AI 비서. 매일 `ACTIVE → STALE → ARCHIVED → DELETE` 정리를 수행하고 Pin/직접 저장 보호 정책을 확인한다. Calendar·Notes 승격은 기존 서비스 호출 후 원본에 대상 ID를 기록한다.

## Database Studio

OWNER가 연결을 저장하면 DatabaseStudioService가 입력을 검증하고 CredentialVault로 비밀번호를 암호화해 Workspace SQLite에 저장한다. 테스트·탐색·쿼리는 DatabaseAdapter가 새 JDBC 연결을 열고 제한된 결과를 만든 뒤 닫는다. SQL 실행은 bounded worker가 진행하며 브라우저는 execution ID로 polling·취소한다. Service의 DATABASE binding은 연결 ID를 Context의 안전한 요약으로 만들며 MCP는 읽기 metadata 도구만 호출한다. [상세](../database-studio.md).

## Service Catalog

OWNER가 Services 앱에서 Service를 만들고 기존 GitHub/장비/Telemetry 리소스를 선택한다. ServiceCatalogService가 참조를 검증하고 SQLite에 연결을 저장한다. 같은 type/reference/device 조합은 409로 거부한다. URL과 파일 경로는 형식을 검증하며 서버에서 URL을 자동 호출하지 않는다. 삭제된 장비/Telemetry 연결은 orphan으로 표시하고 다른 연결 조회는 유지한다.

AI 비서에서는 리소스 탐색 → 모호한 경계에 대한 자연어 질문 → 초안 생성·수정 → 채팅에 현재 연결과 제거 예정 연결 표시 → 명시적인 채팅 승인 문구 입력 → OWNER 브라우저의 revision 승인과 commit 순서로 진행한다. 일반적인 동의 메시지는 AI의 초안 수정 대화로 이어지며 등록을 실행하지 않는다. 기존 연결을 읽지 못하거나 초안 revision이 달라졌으면 승인을 중단한다.

목록/위젯은 연결된 운영·계측·GitHub Action 신호로 Health를 계산한다. 상세 Context는 기존 GithubService, CatalogService/DeviceOperations, TelemetryService 결과와 Resource, Activity를 묶는다. 외부 조회 실패 시 해당 Context 부분만 생략하고 Health는 UNKNOWN 신호를 표시한다. Activity는 카탈로그 이벤트와 기존 GitHub/장비/Telemetry 기록을 최신순으로 합친다. Runtime 탭은 기존 장비 목록과 선택 장비의 Docker 컨테이너 목록을 불러와 Service 리소스로 연결·해제한다. Settings의 GitHub 저장소 연결은 조회된 owner/name 목록을 입력값으로 필터링한 뒤 기존 Service API로 저장한다. Quick Action은 기존 GitHub/Terminal/Files/Remote/Telemetry 화면으로 이동한다. Docker restart는 기존 DeviceOperations API와 확인 대화상자를 사용한다. Ask Codex는 Service ID를 포함한 요청을 기존 assistant 화면에 넣고 MCP `get_service_context`로 같은 모델을 읽는다.

Runtime 탭 진입/새로고침 → ServiceRuntimeService가 연결 ID를 기준으로 장비 계측과 Docker 목록을 조회 → 항목별 상태·자원 사용량 표시. 컨테이너 로그 열기 → Service 연결 재검증 → 기존 CommandAdapter로 최근 200줄 읽기 → 현재 화면의 텍스트로만 표시. 시작·중지·재시작은 사용자 확인 및 CSRF 검증 후 동일 연결을 재검증하고 고정 Docker 작업을 실행한다. 없거나 삭제된 연결은 오류를 반환하며, 한 장비의 조회 실패는 다른 행의 표시를 막지 않는다.

Telemetry 탭 진입 → 기존 Telemetry 서비스 목록 조회 → 이미 연결된 ID 제외 → 선택한 ID를 기존 Service 리소스 API에 POST → Service Context 재조회. 연결 목록은 Context의 분석 요약과 저장된 리소스를 함께 사용해 수집 상태와 삭제된 연결을 표시한다. 연결 해제는 Service 리소스 DELETE를 사용한다. 목록이 비었거나 조회에 실패해도 기존 연결은 계속 표시한다.

## GitHub Control Center

OWNER 브라우저가 `github/status`를 조회한다. 서버 `gh`가 미인증이면 기기 코드 로그인 job을 시작하고 사용자가 GitHub에서 승인한다. 인증 후 `GithubService.owners`가 사용자와 접근 가능한 Organization을 조회한다. Owner 변경 시 저장소 선택을 초기화하고 해당 Owner의 저장소·Overview를 다시 조회한다. 저장소 선택은 같은 서비스의 이슈·PR·Workflow·파일·커밋 조회로 이어진다. 비어 있는 목록은 빈 상태로 표시하고 외부 실패는 안전한 오류 메시지로 표시한다. CLI 재시도는 자동 수행하지 않는다.

Codex의 MCP `github.*` 호출은 `AssistantMcpService`에서 입력을 검증하고 `GithubService`를 호출한다. READ는 구조화된 결과를 반환한다. WRITE는 서비스가 서버 `gh` 인증과 입력을 확인한 뒤 전용 adapter로 전송한다. PR 병합과 저장소 Archive·삭제, Release 삭제는 MCP 승인 요청 생성 → OWNER 브라우저가 작업·대상 확인 후 CSRF 보호 POST 승인 → MCP가 같은 작업·대상·UUID로 실행 요청 → 일회성 승인 소비 → GitHub 호출 순서다. 삭제 승인 버튼에는 대상이 표시되며 브라우저 재확인이 필요하다. 10분 만료, 승인 누락 또는 대상 불일치는 403이다.

## 시작과 인증
환경변수 계정을 검증하고 BCrypt 계정을 등록한다. schema.sql을 idempotent 적용하고 기본 local 장비 루트를 현재 환경변수로 구성한다.
credential.key가 없으면 32바이트 키를 생성한다. 기존 키는 재사용하며 길이 오류면 시작에 실패한다.
로그인 폼 GET은 CSRF를 발급하고 POST 성공 시 세션 ID 교체 후 홈으로 이동한다. 실패는 동일한 일반 메시지다.

## 메타데이터
요청 Bean Validation -> OWNER/CSRF -> 서비스 검증 -> 필요한 비밀번호 AES-GCM 암호화 -> SQLite 저장 -> 비밀번호 없는 응답 -> UI 재조회.
장비 삭제는 연결 대상 정보와 즐겨찾기를 제거한다. UI는 해당 장비 실행 탭을 먼저 닫는다. 서버 기본 local 장비는 삭제/웹 수정하지 않는다.

## 파일
장비 선택 -> root-relative 경로 검증 -> local real path 또는 SFTP canonical path 확인 -> 목록 응답 및 최근 위치 기록.
업로드는 multipart disk spool -> InputStream -> 대상과 같은 폴더의 임시 파일 -> 성공 시 이름 이동, 실패 시 임시 파일 정리다. 기존 파일을 덮어쓰지 않는다.
다운로드는 파일 스트림을 전달하고 종료 시 remote file/SFTP/SSH 연결을 닫는다. 빈 폴더만 삭제한다.
경로 탈출은 400/403, 파일 충돌·권한·누락 등 파일 작업 실패는 409, SSH 연결 실패는 502다. 자동 재시도는 없다.

## 실행
POST 세션(OWNER/CSRF) -> 런타임 UUID 및 로그인 소유권 기록 -> 같은 origin WebSocket -> 최초 attach 검증 -> 실제 셸/SSH/guacd 연결.
브라우저 선택이 CLIENT면 서버 연결을 만들지 않고 등록된 URL을 반환한다. SERVER/REMOTE는 해당 Chromium DevTools에 새 URL 탭을 요청하고 VNC 화면을 연다.
터미널의 셸 연결과 입력 바인딩이 끝나면 서버가 ready 프레임을 보내고, 클라이언트가 입력과 크기 변경을 시작한다. 셸 출력은 별도 WS 프레임으로 전달된다. 셸이 종료되거나 연결이 실패하면 close code와 안전한 사유를 표시한다. remote mouse/keyboard/clipboard는 Guacamole 프로토콜로 전달한다.
종료/탭 닫기/로그아웃 이벤트가 자원을 정리한다. 연결 중 탭이 닫혀도 뒤늦게 생성한 자원을 즉시 닫는다. 서버 연결 실패는 안전한 1011 메시지 또는 502 API 오류다.

## 장비 관리
CPU/RAM/DISK는 로컬 OS 또는 SSH 명령으로 측정한다. SSH 계측 미설정 장비는 TCP 포트만 확인하며 숫자를 만들지 않는다.
Docker/GPU 버튼은 대상 장비의 CLI 출력을 제한된 크기로 조회한다. 명령은 최대 10초, 출력 최대 256KiB다.
Wake는 저장한 MAC과 주소로 UDP 전송한다. 성공은 전송 완료이며 장비 전원 켜짐을 보장하지 않는다.

## 검색·설정·복구
서버 검색은 장비 실행 종류, 앱, 경로 즐겨찾기를 필터링한다. 빈 검색은 가능한 빠른 실행 목록을 반환한다.
설정은 저장 후 UI를 갱신한다. 브라우저 모드 변경은 새 앱 실행부터 적용한다. 탭 레이아웃 저장은 순서대로 처리한다.
401은 로그인 이동, 403은 재로그인/새 CSRF 안내, 네트워크/파일/원격 오류는 화면 내 오류와 재시도 동작을 제공한다.

## 등록 없는 명령어 연결
터미널 열기 -> 기존 local TERMINAL 세션 -> 서버 PTY에서 사용자가 OpenSSH 명령 입력 -> 최초 호스트 확인 및 비밀번호 프롬프트 -> 원격 셸.
새 API나 DB 변경 없이 기존 OWNER/CSRF/WS 세션 소유권을 적용한다. OpenSSH client는 TerminalAdapter가 연 실제 셸 안에서 실행된다. 호스트 검증은 OpenSSH 기본 동작이며 검증을 끄지 않는다. known_hosts, 사용자 SSH 설정과 키는 볼륨의 /app/data/home/.ssh에 보존한다. 진입 스크립트가 기존 볼륨에도 홈을 생성하고 홈과 .ssh 권한을 700으로 설정한다. 서버 인증 환경변수는 기존 PTY 환경 필터로 제거한다. 명령어 연결은 장비 메타데이터를 생성하지 않는다.

## SSH 명령으로 장비 등록
장비 → SSH로 장비 연결 → command/password와 선택 name → OWNER/CSRF 검증 → 서비스에서 제한된 SSH 구문 파싱 → 같은 호스트/포트의 저장 키 조회 → 어댑터에서 SSH 인증 및 SFTP 홈 확인 → CatalogService에서 암호화 저장 → DeviceView → 장비 목록 갱신.
명령 문자열을 셸로 실행하지 않는다. 지원 구문은 ssh 사용자@호스트, -p 포트 또는 -p포트(대상 앞/뒤)다. IPv6는 대괄호를 허용한다. 기타 옵션·원격 명령·셸 확장은 400이다.
첫 호스트 키는 자동 신뢰하며 인증과 SFTP 확인 성공 후에만 DB에 저장한다. 저장 키가 있으면 일치해야 하고, 같은 호스트에 서로 다른 저장 키가 있으면 409다. 실패 시 새 장비나 비밀번호를 저장하지 않는다. 같은 host(대소문자 무시)/port/username은 기존 ID, 파일 루트, 원격 접속 설정과 고정 상태를 유지한다. 간편 등록 요청은 단일 서버 안에서 직렬 처리해 동시 최초 등록을 방지한다. 연결 5초/I/O 10초이며 자동 재시도는 없다. 이 키 저장은 CLI의 known_hosts와 별개다.

장비 직접 설정/수정 화면에서도 fingerprint 입력란을 제공하지 않는다. 기존 주소·포트의 키는 서버가 유지한다. 새 SSH 주소이거나 저장 키가 없는 경우 비밀번호(수정 시 빈 값은 기존 값)로 연결 검증 후 키를 자동 저장한다. SSH 사용자 없는 원격 화면 전용 설정은 SSH 연결 없이 저장할 수 있다.

## 일정 관리
캘린더 열기 -> 표시할 42일의 from/to로 서버 조회 -> 월 격자/선택 날짜 표시. 새 일정은 종일 하루 일정으로 시작하며, 종일 종료일을 비우면 시작일 하루만 저장한다. 종료일을 입력한 기간 일정은 월 격자에서 주마다 날짜 칸을 가로지르는 하나의 막대로 표시한다. 일정 저장은 Bean Validation -> 시간/종일 경계 검증 -> 트랜잭션 저장 -> 다시 조회다. API 종료 시각은 exclusive이며 종일 편집 UI만 종료일을 포함해 표시한다. 월 격자와 선택 날짜는 API가 초를 포함해 반환해도 분 단위 경계로 비교하여 종료일 다음 날 자정에 끝나는 일정을 표시하지 않는다. 겹치는 일반 일정은 허용한다. 수정/삭제 대상이 없으면 404, 입력 오류는 400이다.
시간표 열기 -> 학기 목록(시작일 역순) -> 선택 학기 상세와 서버 합계 조회. 수업 저장은 학기와 과목 소속 확인 -> 분 단위의 양수 시간 검증 -> 자체/다른 과목 겹침 검사 -> 과목과 수업시간 전체를 하나의 트랜잭션으로 저장한다. 요일 1=월~7=일, [start,end) 구간으로 충돌을 판단한다. 다른 과목 충돌은 409, 자체 겹침은 400, 인접 시간은 허용한다. 학기별로 충돌을 분리한다. 학기 삭제는 확인 창 후 FK cascade로 수업과 수업시간도 삭제한다.
일시 값은 ISO 로컬 시각(분 단위), 학기 날짜는 ISO date다. 브라우저 현지 시각 기준으로 입력/표시하고 서버 시간대로 변환하지 않는다. 날짜 범위는 캘린더 조회 1~366일, 학기 기간 최대 366일 차이, 작성 연도는 1900~2200이다. 화면 새로고침 시 데이터는 서버에서 복구하고 선택 월/학기는 화면 상태로만 유지한다.

## Tailscale 시작/인증

Tailscale은 사용자 링크 승인 방식으로 인증한다. dashboard가 네트워크와 공개 포트를 소유하고 Tailscale sidecar의 데몬/브리지를 독립적으로 시작한다. 서비스 시작 시 새 로그인이나 인증 키 주입은 수행하지 않는다. 기존 인증 상태는 데몬이 재사용한다.

사용자가 웹에서 로그인 시작 → OWNER/CSRF 검증 → 전용 어댑터와 브리지 → 고정 tailscale login 명령으로 URL 발급 → 화면에 URL 표시 → 사용자의 PC/휴대폰 브라우저에서 직접 승인 → 창이 열린 동안 3초마다 상태 확인 → Running이면 완료 안내와 IP 표시. 서버 브라우저 실행이나 계정 로그인 자동화는 수행하지 않는다. 로그인은 최대 5분 대기하며 실패 후 사용자가 재시도한다. 원본 CLI 출력과 인증 URL은 로그에 남기지 않는다.

로그인 전에도 일반 앱과 DIRECT 연결은 유지된다. Tailscale 대상 연결은 기존 권한/네트워크/timeout 정책을 따르며 일반 네트워크로 자동 우회하지 않는다.

## SSH 에디터

서버 자체(local) 또는 SSH 장비/폴더 선택 → job 생성 → 선택 서버의 도구 확인/설치 → 고정 helper 명령 + JSON stdin → 대상 서버 파일/Git/Codex 실행 → JSON 결과 polling → UI 갱신. 기기 로그인은 승인 주소/코드를 표시하고 실제 토큰은 원격 CLI가 관리한다. 취소/로그아웃은 로컬 프로세스 또는 SSH stdin EOF로 자식 프로세스를 종료한다.

## GitHub 앱

OWNER가 GitHub 앱을 열면 서버 `gh auth status`로 인증을 확인한다. 로그인 시작은 기존 Studio job의 local `github-login`을 사용하고 공식 CLI 기기 코드와 URL을 화면에 전달한다. 승인 후 `gh auth setup-git`이 실행되며, 앱의 조회 API와 AI 비서 MCP는 동일 서버 계정의 `gh`로 JSON 목록을 읽는다. 잘못된 저장소명은 service에서 차단하고 CLI 실패·시간 초과는 안전한 오류로 변환한다. 브라우저를 닫거나 취소하면 인증 job을 취소할 수 있다.

## Launcher 실행과 편집

인증된 Home → Registry 구성 → 계정별 홈 복원/검증 → 이어하기·Services·일정·인프라 요약과 compact 저장 항목 표시. 홈 편집 시 화면 너비에 맞는 기존 격자 투영으로 전환한다. 앱 실행은 내장 화면 탭, 기존 설정 모달 또는 기존 실행 세션 흐름으로 분기한다. 편집은 잠금 확인 → 좌표/크기 검증 → 폴더 합치기 또는 충돌 재배치 → 성공한 레이아웃 저장이다. 실패하면 이전 상태와 오류 안내를 유지한다. Widget은 기존 데이터의 요약/앱 이동만 제공하며 CLI를 자동 실행하지 않는다. 모바일 홈 스와이프는 편집 격자 안에서만 처리해 앱 내부 스크롤과 분리한다. [사용 흐름과 실패 처리](../launcher.md).

### Codex 대화

SSH 장비의 프로젝트 열기 → Codex 탭 → 원격 model/list 및 account/read → 최근 thread 복원 또는 세션 목록 선택 → 파일/선택/이미지/스킬 첨부 → thread/start 또는 resume → turn/start → 스트림 표시 → 필요 시 승인/질문 응답 → turn/completed → thread/read. 서버 자체(local) 프로젝트에서 Codex 요청은 400으로 거부하고 SSH 장비를 선택하도록 안내한다. 전송 전 미저장 편집을 막는다. 첫 메시지 전 새 세션은 draft다. 다른 cwd의 thread 작업은 403. 자동 모델 호출 재시도는 하지 않는다.

전역 Codex assistant: 상단 `AI 비서` 또는 내장 앱 열기 → server-local setup/MCP 연결 및 계정·모델 확인 → 사용자 요청 직접 입력 → 사용자 turn을 assistant job으로 실행 → MCP tools/call로 페이지 이동·일정·노트·GitHub 조회/생성/수정/삭제 → app-server usage/item/interaction 이벤트를 채팅에 표시 → navigation queue를 브라우저가 polling해 내부 페이지 또는 등록 앱을 연다. 일정·메모 삭제 및 메모 전체 교체는 대화에서 정확한 대상을 확인한다. GitHub 저장소·Release 삭제는 승인 요청 → GitHub 화면에서 대상 확인과 재확인 → 같은 작업·대상에 묶인 일회성 승인 소비 → GitHub API 호출 순서다. 앱을 벗어나도 실행 중인 job과 세션 대화는 유지되고 다시 열면 해당 세션 대화를 표시한다. 프로젝트 편집기 job endpoint는 사용하지 않는다. 로그인 만료, 설치 오류와 MCP 도구 오류는 채팅 상태로 표시하고 임의 셸 도구는 제공하지 않는다.

Service Onboarding: 자연어 서비스 요청 → thread ID로 등록 리소스 탐색 → 결정적 correlation과 기존 Service 중복 단서 → 애매한 Compose/리소스 경계 질문 → 임시 Draft 작성·수정 → 채팅의 구조화된 카드 미리보기 → 브라우저 OWNER/CSRF 최종 승인 → `ServiceCatalogService` transaction으로 생성 또는 수정/연결/해제 → 활동 출처 기록과 Service 화면 열기. 일부 탐색 소스가 실패해도 나머지 후보를 보여준다. 승인 전 MCP commit은 403, 동시 Service 변경은 409, 연결 검증 실패 시 transaction rollback이다.

서비스 화면의 수동 생성은 목록의 추가 작업 → 생성 dialog에서 기본 정보 입력 → 저장 중 진행 상태와 중복 제출 방지 → 성공 시 dialog 닫기 및 연결·설정 화면 이동 순서다. 실패하면 입력을 유지하고 폼 안에 오류를 표시한다. 화면 전환과 API 요청에는 공통 진행 상태를 표시한다.

주기적 상태 조회는 변경 범위에 맞춰 화면을 갱신한다. Workspace 백그라운드 조회는 상태가 같으면 기존 DOM을 유지하고, 최근 작업·클립만 바뀌면 해당 목록만 교체한다. 장비 상태는 해당 카드·홈 장비 위젯에 반영한다. Database Studio 쿼리 폴링은 실행 중 상태만 갱신한 뒤 완료 결과를 표시한다. GitHub 승인 폴링은 목록 변경 시에만 승인 행을 갱신한다.

사이드바는 동일한 서버 Codex 계정의 thread/list를 검색·페이지네이션하고, 선택 시 thread/read 결과의 사용자/답변 item만 대화에 복원한다. 새 채팅은 저장 전 draft이고 첫 turn 후 목록에 나타난다. 설정 창의 로그인·로그아웃, 모델·추론 설정과 대화 이름 변경·보관은 기존 assistant job action을 사용한다. 사이드바 삭제 모달은 별도의 thread/list 페이지를 표시하고 선택한 세션을 각 thread/delete job으로 영구 삭제한다. 현재 대화 제목 옆 삭제 버튼도 같은 action을 사용하며 성공하면 세션 저장소의 현재 ID를 지우고 빈 대화로 이동한다. 실패한 세션은 목록에 남긴다. 첨부 이미지와 UTF-8 텍스트는 전송 전 브라우저 메모리에만 두고, 전송 시 검증된 context로 turn에 전달한다. 실패 시 입력과 첨부는 재시도를 위해 유지한다.

assistant UI는 응답 대기 중 진행 말풍선을 표시하고 turn/reasoning, MCP 도구, agentMessage 이벤트에 따라 단계 문구를 갱신한다. 내용 없는 agentMessage 시작 이벤트는 빈 답변으로 확정하지 않는다. 첫 실제 답변 텍스트 또는 job 완료/실패가 진행 표시를 종료한다. 새로고침 뒤 sessionStorage에 남은 미완료 표시도 완료 상태의 재시도 안내로 바꾼다.

요청이 정상 완료되면 상단의 임시 진행 상태를 비우고 결과는 채팅 답변에 표시한다. 상단 빠른 작업 버튼 줄은 두지 않는다. 오류와 로그인·연결 복구 안내는 기존 상태 영역에 유지한다.

assistant 실행 하네스: 사용자 요청의 결과와 MCP 필요 여부 판단 → MCP가 필요하면 이해한 요청과 사용할 도구를 채팅에 미리 알림 → 해당 도구 호출 → 결과와 사용 기능 보고. 진행 중 먼저 보낸 안내는 최종 답변 위에 유지한다. 별도의 `실제 MCP 호출` 목록은 app-server의 `mcpToolCall` item에서 생성하고, 저장된 thread/read를 열 때 turn의 item으로 복원한다. 호출 인수와 결과 본문은 목록에 넣지 않으며 실패 상태만 표시한다. 도구를 쓰지 않은 요청에는 호출 목록을 만들지 않는다.

장비 네트워크 선택과 점프 장비 순서 지정 → 저장/SSH 등록의 호스트 키 확인에도 동일한 점프 체인 적용 → 각 장비의 networkMode가 TAILSCALE이면 공유 tailscale0 인터페이스 주소 확인 및 Tailscale 주소 해석 → 첫 점프 장비부터 SSH 호스트 키 검증·인증 → 각 점프 장비에서 Direct-TCPIP 채널로 다음 주소에 연결 → 대상 장비 호스트 키 검증·인증 → SFTP/터미널/원격 어댑터 연결. TAILSCALE 브릿지 뒤의 DIRECT 목표는 Tailscale 확인 없이 브릿지에서 목표 주소를 해석·연결한다. 점프 체인은 최대 5개이며 어느 홉이라도 실패하면 전체 연결을 실패시킨다. 실패 시 기본 네트워크로 재시도하지 않는다. SSH 재등록에서 jumpDeviceIds 생략/null은 기존 체인을 유지하고 빈 배열은 직접 연결을 지정한다. 프로필 변경 전 열린 실행 세션은 생성 당시 설정을 유지하므로 새 연결로 적용한다.

장비 로그 흐름: 장비/종류 선택 → 세션 소유 logs-targets 작업 → 대상 선택 → logs-follow 작업 → 400ms 조회로 append/snapshot 반영 → 중지/앱 이동/15분 제한으로 읽기 작업 종료. 빈 목록은 생성된 컨테이너·tmux 세션이 없다는 안내, CLI/권한/연결 실패는 재시도 가능한 오류를 표시한다. 장비 연결에는 저장된 SSH/Tailscale 설정을 사용하고 원본 세션·컨테이너는 변경하지 않는다.

Tailscale 로그인: 설정 열기 → OWNER 상태 조회 → 로그인 시작(POST+CSRF) → 서버 CLI가 공식 링크 발급 → 사용자가 공식 화면에서 승인 → 상태 Running/IP 반영. 진행 중 반복 요청은 합쳐지고 실패/5분 만료 시 재시도한다. 로그아웃 확인 → DELETE+CSRF → 진행 중 CLI 종료와 서버 logout → 상태 재조회. 내부 서비스 미준비는 503 안내를 표시한다.

장비 로그 표시 변경: 보기/컬러맵/줄바꿈/코드 블록 선택 → 기존 수신 버퍼를 안전하게 재표시 → 브라우저 계정별 옵션 저장. 수신 작업은 유지한다. JSON 파싱 실패·미완성 레코드·표시 크기 제한에서는 원문을 유지하며, 자동 스크롤 해제 상태의 스크롤 위치는 가능한 범위에서 보존한다.

클라우드 흐름: 탐색/검색 → 선택 → 작업 요청 → 서버 경로·권한 검증 → 디스크 처리 → 목록 새로고침. 업로드는 파일별 진행률과 취소를 제공한다. 삭제는 휴지통으로 이동하고 복원 충돌은 기존 항목을 보존한다. 텍스트 저장은 revision 불일치 시 409로 다시 읽도록 안내한다. 다중 선택 작업은 성공과 실패를 각각 보고하며 재시도 때 이미 성공한 작업을 자동 되돌리지 않는다.

장비 원격 자동 연결 → OWNER/CSRF 검증 → 기존 프로필 연결 검사 또는 SSH OS·도구 확인 → 필요한 지원 패키지 설치 → 미사용 포트·디스플레이 선택 → loopback VNC 실행 → 프로필 저장 → SSH 터널·guacd 인증 검증 → READY 및 원격 탭 열기. 실패는 BLOCKED와 조치 안내를 제공한다. [세부 흐름](../remote-desktop.md).

대시보드가 네트워크와 공개 포트를 소유한다. Tailscale 미기동·미로그인·중지는 일반 서비스 기동을 막지 않는다. 최초 구조 변경은 [운영 배포 안내](../deployment.md)의 중지 후 재생성 절차를 따른다.

클라우드 파일 열기 → 전용 에디터 화면 → 텍스트 편집/이미지 열람 또는 다운로드. 저장 성공 후 내용이 동일한 경우에만 새 revision을 채택한다. 저장 실패는 편집 내용을 유지하고 복귀 시 미저장 변경을 확인한 뒤 목록을 다시 조회한다.

메모장: 조직 폴더 → 프로젝트 폴더 → 템플릿 선택/문서 생성 → 블록 편집 → 1초 자동 저장 또는 저장 버튼 → OWNER/CSRF → 블록/계층 검증 → revision 조건부 UPDATE → 새 revision 반영. 실패 시 편집 버퍼와 현재 문서를 유지하며 강제 덮어쓰기를 하지 않는다. 새로고침 전 미저장 내용을 확인한다. 이미지 업로드는 형식/크기 검증 후 문서 FK로 DB에 저장하며 문서 삭제와 함께 제거한다. 비어 있지 않은 폴더 삭제는 409다. [자세히](../notes.md).

MCP 문서 생성·이어 쓰기: `text` Markdown → 서버의 편집 가능 블록 변환 → 기존 NoteService 검증·저장 → 브라우저 BlockNote 렌더링. 이어 쓰기는 입력 revision과 현재 문서 revision을 비교한다.

기존 MCP 문서 조회: 이전 MCP 문단 형태와 Markdown 서식 확인 → 변환 블록 검증 → 현재 revision 조건부 저장 → 최신 문서 반환. 일반 문장·편집된 블록·검증 실패 원문은 유지한다. 동시 수정이 먼저 저장되면 최신 내용을 반환하고 마이그레이션은 다음 조회로 미룬다.
## 실시간 화면 흐름

Home 표시 흐름: 기존 일정·서비스 health·장비 상태·GitHub 요약과 승인 목록 → 오늘/확인 필요/이어하기에 투영한다. 첫 응답 전은 로딩, 조회 실패 후는 상태 미확인으로 표시한다. 현재 브라우저에서 연 서비스·저장소를 최대 3개 메모리 항목으로 유지하고, 기존 Studio 프로젝트와 runtime 활동을 합쳐 최대 5개 이어하기 링크를 표시한다. 새 서버 상태·HomeItem 필드·저장 API는 없다. 링크는 기존 앱 진입 계약을 사용한다.

모바일 시간표는 선택한 요일만 표시한다. 요일 전환은 클라이언트 표시 상태와 포커스만 갱신하며 REST 요청이나 수업 저장을 수행하지 않는다. Desktop의 7일 격자와 수업 편집 계약은 유지한다.

OWNER 로그인 후 `/ws/workspace`의 ready를 받으면 현재 화면과 공통 Workspace를 조회한다. 변경 알림은 영역별로 합치고 사용자 요청의 로딩 해제 후 기존 REST API를 조용히 호출한다. 응답은 동일 화면·버전일 때 부분 반영하며 폼과 에디터를 유지한다. 재접속·버전 누락·탭 복귀는 최신 상태를 재조회한다. heartbeat는 현재 화면의 외부 상태도 갱신한다. 소켓 미연결 때는 60초 조회를 사용하며 로그아웃 시 연결을 정리한다. [상세](../realtime-ui.md).

병역 캘린더: 앱 또는 기존 캘린더에서 진입 → GET 현황 → 미등록이면 복무 정보 등록 → OWNER/CSRF·날짜·revision 검사 → SQLite 저장 → 서버 계산 현황 반환 → 매초 시계 표시. 휴가·훈련 일정도 같은 경계로 저장하고 성공한 쓰기는 `military`와 `calendar`를 함께 무효화한다. 캘린더는 원본을 투영하고 항목 클릭 시 병역 편집기로 이동한다. 폼 실패·409는 입력을 유지하고, 조용한 조회 실패는 기존 화면을 유지한다. 서울 자정에는 일별 집계를 다시 읽으며 실패 시 30초 이후 재시도한다. 프로필 삭제는 병역 일정만 cascade하고, 연동 해제는 원본을 유지한다. [상세](../military-calendar.md).
