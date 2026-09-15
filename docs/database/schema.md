# SQLite schema v3

소스: `src/main/resources/db/schema.sql`. DatabaseInitialization이 시작 시 idempotent CREATE/INSERT와 user_version=3를 적용한다.
기존 초기 프로젝트에는 업무 테이블이 없었으므로 데이터 삭제 없이 추가한다. v3는 db/migrations/V3__device_network.sql로 기존 devices에 network_mode를 추가한다. PRAGMA table_info로 적용 여부를 확인하므로 재시작 시 반복 추가하지 않는다. 기존 장비는 DIRECT로 보존한다.
기존 테이블 owner는 catalog이며 soft delete는 사용하지 않는다. 아래 표의 컬럼은 별도 명시가 없으면 NOT NULL, default 없음이다.
TEXT ID는 서버 생성 UUID이며 devices의 기본 프로필만 `local`이다. 기존 catalog 시간은 INTEGER Unix epoch milliseconds, boolean은 INTEGER 0/1이다.

## devices

목적: 서버에서 접속할 장비 프로필. PK id. FK 없음. 암호문은 CredentialVault가 생성한다.

| 컬럼 | 타입 | 필수 | Default | Index/Unique | 의미/enum |
| --- | --- | --- | --- | --- | --- |
| id | TEXT | 예 | 없음 | PK/unique | 장비 ID |
| name | TEXT | 예 | 없음 | 없음 | 표시 이름 |
| host | TEXT | 예 | 없음 | 없음 | DNS/IP (TAILSCALE이면 Tailscale IP/MagicDNS) |
| network_mode | TEXT | 예 | DIRECT | CHECK | DIRECT/TAILSCALE |
| ssh_port | INTEGER | 예 | 없음 | 없음 | SSH 포트 1~65535 |
| username | TEXT | 예 | 없음 | 없음 | SSH 사용자, 미설정 빈 문자열 |
| password_cipher | TEXT | 예 | 없음 | 없음 | AES-GCM nonce+ciphertext, 미설정 빈 문자열 |
| fingerprint | TEXT | 예 | 없음 | 없음 | 검증된 SHA256 SSH host 지문, 미설정 빈 문자열 |
| root_path | TEXT | 예 | 없음 | 없음 | local 또는 원격 파일 루트 |
| remote_protocol | TEXT | 예 | 없음 | 없음 | NONE/RDP/VNC |
| remote_port | INTEGER | 예 | 없음 | 없음 | RDP/VNC 포트 |
| remote_username | TEXT | 예 | 없음 | 없음 | 원격 사용자 또는 빈 문자열 |
| remote_password_cipher | TEXT | 예 | 없음 | 없음 | 원격 비밀번호 암호문 또는 빈 문자열 |
| mac | TEXT | 예 | 없음 | 없음 | Wake MAC 또는 빈 문자열 |
| broadcast | TEXT | 예 | 없음 | 없음 | Wake 대상 주소 또는 빈 문자열 |
| pinned | INTEGER | 예 | 0 | 없음 | 빠른 연결 고정 0/1 |

`local`은 시작 시 현재 WORKSPACE_ROOT로 갱신한다. 루트 변경은 기존 파일을 이동시키지 않는다.

## applications

목적: 이름과 웹사이트 주소. PK id, FK 없음.

| 컬럼 | 타입 | 필수 | Default | Index/Unique | 의미 |
| --- | --- | --- | --- | --- | --- |
| id | TEXT | 예 | 없음 | PK/unique | 앱 ID |
| name | TEXT | 예 | 없음 | 없음 | 이름 |
| url | TEXT | 예 | 없음 | 없음 | userinfo 없는 HTTP(S) URL |
| pinned | INTEGER | 예 | 0 | 없음 | 빠른 접근 고정 0/1 |

## clips

목적: 만료형 텍스트 전달. PK id, FK 없음. 내용은 일반 텍스트이므로 연결 비밀번호 저장소로 사용하지 않는다.

| 컬럼 | 타입 | 필수 | Default | Index/Unique | 의미 |
| --- | --- | --- | --- | --- | --- |
| id | TEXT | 예 | 없음 | PK/unique | 클립 ID |
| content | TEXT | 예 | 없음 | 없음 | 최대 32000자 텍스트 |
| expires_at | INTEGER | 예 | 없음 | clips_expiry/non-unique | 만료 시각 |

조회에서 expires_at<=now 행을 삭제한다. 삭제 전에도 만료된 행은 응답하지 않는다.

## bookmarks

목적: 장비 파일 루트 상대 경로 저장. PK id, device_id FK -> devices.id, ON DELETE CASCADE. 장비 1:N 즐겨찾기.

| 컬럼 | 타입 | 필수 | Default | Index/Unique | 의미 |
| --- | --- | --- | --- | --- | --- |
| id | TEXT | 예 | 없음 | PK/unique | 즐겨찾기 ID |
| device_id | TEXT | 예 | 없음 | 복합 unique(device_id,path) | 장비 FK |
| path | TEXT | 예 | 없음 | 복합 unique(device_id,path) | `/`로 시작하는 루트 상대 경로 |

## activity

목적: 다시 열 수 있는 최근 리소스 위치. PK id. 여러 종류의 target_id이므로 DB FK는 없다. 장비/앱 삭제 서비스가 해당 activity를 삭제한다.

| 컬럼 | 타입 | 필수 | Default | Index/Unique | 의미 |
| --- | --- | --- | --- | --- | --- |
| id | TEXT | 예 | 없음 | PK/unique | 이력 ID |
| kind | TEXT | 예 | 없음 | 없음 | 실제 이력 FILES/TERMINAL/REMOTE/APP |
| target_id | TEXT | 예 | 없음 | 없음 | 대상 장비/앱 ID |
| label | TEXT | 예 | 없음 | 없음 | 표시 이름 |
| path | TEXT | 예 | 없음 | 없음 | 파일 경로 또는 빈 문자열 |
| occurred_at | INTEGER | 예 | 없음 | activity_time/non-unique | 마지막 열기 시각 |

같은 kind/target/path는 서비스에서 갱신하며 최근 100개만 보관한다. 명령 입력과 terminal 출력은 저장하지 않는다.

## preferences

목적: 전역 표시 설정. singleton PK id=1(CHECK), FK 없음.

| 컬럼 | 타입 | 필수 | Default | Index/Unique | 의미/enum |
| --- | --- | --- | --- | --- | --- |
| id | INTEGER | 예 | 없음 | PK/unique | 반드시 1 |
| theme | TEXT | 예 | dark | 없음 | dark/light |
| compact | INTEGER | 예 | 1 | 없음 | 0/1 |
| terminal_font | INTEGER | 예 | 13 | 없음 | 10~24px |
| clip_minutes | INTEGER | 예 | 60 | 없음 | 1~1440분 |

## browser_preferences

목적: 앱 실행 위치. singleton PK id=1(CHECK). device_id는 SERVER/CLIENT의 빈 값 허용 때문에 FK가 아니며 REMOTE 저장 시 서비스가 실제 VNC 장비를 검사한다.

| 컬럼 | 타입 | 필수 | Default | Index/Unique | 의미/enum |
| --- | --- | --- | --- | --- | --- |
| id | INTEGER | 예 | 없음 | PK/unique | 반드시 1 |
| mode | TEXT | 예 | SERVER | 없음 | CLIENT/SERVER/REMOTE |
| device_id | TEXT | 예 | 빈 문자열 | 없음 | REMOTE VNC 장비 ID |
| debug_port | INTEGER | 예 | 9222 | 없음 | 원격 Chromium 디버깅 포트 |

REMOTE 장비 삭제 후 새 앱 실행은 404로 실패하며 설정에서 다른 장비를 선택해야 한다.

## workspace_tabs

목적: UI 탭 순서·위치 복원. PK id, polymorphic target이므로 FK 없음. 실행 핸들은 저장하지 않는다.

| 컬럼 | 타입 | 필수 | Default | Index/Unique | 의미/enum |
| --- | --- | --- | --- | --- | --- |
| id | TEXT | 예 | 없음 | PK/unique | 클라이언트 생성 탭 ID |
| kind | TEXT | 예 | 없음 | 없음 | FILES/TERMINAL/REMOTE/APP/DOCKER/GPU |
| target_id | TEXT | 예 | 없음 | 없음 | 장비/앱 ID |
| path | TEXT | 예 | 없음 | 없음 | 파일 경로 또는 빈 문자열 |
| title | TEXT | 예 | 없음 | 없음 | 탭 제목 |
| pinned | INTEGER | 예 | 없음 | 없음 | 고정 0/1 |
| position | INTEGER | 예 | 없음 | 없음 | 배열 순서, 0부터 |

PUT tabs가 트랜잭션으로 최대 20개 배열을 교체한다. 대상이 삭제된 오래된 탭은 열기에서 없는 대상으로 안내한다.

## 인증·연결·파일

users/roles/sessions/tokens/audit 테이블은 없다. 계정은 환경변수, 로그인 세션과 실시간 연결은 메모리에만 존재한다.
접속정보 암호화 키는 DB 외부 영속 파일 `credential.key`(32바이트)이며 Linux에서는 소유자 읽기/쓰기 권한으로 생성한다.
파일 내용은 configured local root/원격 파일시스템, Chromium 프로필은 별도 Docker volume에 저장한다.
Hikari 최대 연결 1, 연결마다 PRAGMA foreign_keys=ON, SQLite busy_timeout=5000ms다.

## SSH 간편 등록 저장 규칙
schema 변경 없음. `devices.fingerprint`는 SSH 간편 등록 시 최초 연결에서 얻은 SHA256 키를 자동 저장한다. 인증/SFTP 조회 실패 시 저장하지 않는다. 기존 host/ssh_port/username이 일치하면 기존 id와 세부 설정을 유지한다. 비밀번호는 기존 password_cipher에 AES-GCM으로 저장한다. fingerprint 자동 저장은 CLI known_hosts 파일과 독립적이다.

## Planner 테이블 (v2)
owner는 planner, soft delete 없음. 아래 모든 열은 NOT NULL/default 없음이며 별도 명시 외 UNIQUE/인덱스 없음. TEXT UUID PK. ISO 날짜/현지 시각은 TEXT, boolean은 INTEGER 0/1. v1 -> v2는 CREATE TABLE/INDEX IF NOT EXISTS만 수행하여 기존 데이터를 보존한다. 시작 시 user_version=2를 적용한다.

| 테이블 | 열 | 타입 | 역할/제약 |
| --- | --- | --- | --- |
| calendar_events | id | TEXT PK | 일정 UUID |
| calendar_events | title | TEXT | 일정명, 최대 120 |
| calendar_events | starts_at / ends_at | TEXT | ISO LocalDateTime, 분 단위, 종료 제외. calendar_range(starts_at,ends_at) 비고유 인덱스 |
| calendar_events | all_day | INTEGER | 종일 0/1 |
| calendar_events | location | TEXT | 장소, 없으면 빈 값 |
| calendar_events | notes | TEXT | 메모, 없으면 빈 값 |
| calendar_events | color | TEXT | #RRGGBB |
| timetable_terms | id | TEXT PK | 학기 시간표 UUID |
| timetable_terms | name | TEXT | 학기/시간표 이름 |
| timetable_terms | starts_on / ends_on | TEXT | ISO date 학기 기간(양끝 포함) |
| timetable_courses | id | TEXT PK | 과목 UUID |
| timetable_courses | term_id | TEXT FK | timetable_terms.id, ON DELETE CASCADE. courses_term(term_id) 비고유 인덱스 |
| timetable_courses | title | TEXT | 과목명 |
| timetable_courses | professor / location | TEXT | 교수/강의실, 없으면 빈 값 |
| timetable_courses | credits | INTEGER | 학점 0~30, 서버 검증 |
| timetable_courses | color / notes | TEXT | #RRGGBB/메모 |
| timetable_meetings | course_id | TEXT FK, 복합 PK | timetable_courses.id, ON DELETE CASCADE |
| timetable_meetings | day | INTEGER, 복합 PK | CHECK 1~7 (월~일) |
| timetable_meetings | starts_at | TEXT, 복합 PK | ISO LocalTime 시작 분 |
| timetable_meetings | ends_at | TEXT | ISO LocalTime 종료 분 |

관계: term 1:N course, course 1:N meeting. meeting의 PK는 (course_id,day,starts_at). 과목 저장은 meeting 교체까지 하나의 트랜잭션이며 학기/과목 삭제 시 하위 데이터도 제거한다. 일반 calendar_events는 FK 없이 독립적이다. 인증 테이블은 추가하지 않고 기존 환경변수 단일 OWNER를 사용한다.

## 클라우드 드라이브 (파일시스템 저장)

SQLite 테이블/마이그레이션 추가 없음. cloud 모듈 소유 CLOUD_ROOT/files는 활성 파일, trash/{UUID}/payload는 삭제된 원본, record.json의 path(String 필수 가상 원래 경로)와 deletedAt(long 필수 epoch ms)는 내부 TrashRecord이다. staging은 임시 업로드/복사/ZIP 용도다. 서버 단일 계정과 파일시스템 권한으로 보호한다. 휴지통으로 소프트 삭제하고 명시적인 영구 삭제만 재귀 제거한다. 기존 장비/사용자/세션 테이블 관계는 변경하지 않는다.

카카오톡 제거 후 기존 DESKTOP 행은 물리 삭제하지 않고 activity 및 workspace_tabs 조회에서 제외한다. 새 요청에는 DESKTOP을 허용하지 않는다. 테이블 구조 변경은 없다.
