# Database Studio

## 경계

`database_connections`, `database_query_history`, `database_query_favorites`는 **Workspace 자체 SQLite**에 저장된다. 등록된 PostgreSQL, MySQL/MariaDB, SQLite는 별도 대상 DB다. PostgreSQL/MySQL/MariaDB은 Spring Boot가 관리하는 JDBC 드라이버를 사용한다. SQLite는 기존 `local` Device의 파일 루트 내부에 이미 존재하는 일반 파일만 열며 symlink의 실제 경로도 검사한다. 브라우저가 임의 JDBC URL을 전달할 수 없다.

```text
Browser/OWNER session -> DatabaseStudioController -> DatabaseStudioService
                                                   -> DatabaseRepository -> Workspace SQLite
                                                   -> DatabaseAdapter -> external JDBC database
ServiceCatalogService -> DatabaseStudioService -> safe connection summary
AssistantMcpService -> DatabaseStudioService -> metadata only
```

비밀번호는 기존 `CredentialVault`의 AES-GCM으로 암호화하여 저장하고, 응답과 Service Context에는 `passwordConfigured` 상태만 제공한다. 암호화 키 파일을 Workspace SQLite와 함께 백업해야 한다. 연결 수정에서 비밀번호를 비워 두면 기존 암호문을 유지한다. SQLite 연결은 비밀번호를 사용하지 않는다. 선택적 metadata는 최대 20개의 짧은 문자열 항목이며 암호나 토큰을 저장하는 용도가 아니다.

## 등록 장비와 Docker 연결

연결 추가에서 직접 연결 / 장비 / Docker를 선택한다. 장비 모드는 등록된 장비의 SSH 인증·호스트 지문·점프 설정을 재사용한다. Host는 해당 장비에서 접근 가능한 DB 주소이며 장비 자체 DB는 보통 127.0.0.1이다. Docker 모드는 실행 중 컨테이너 목록을 불러오며 종류와 내부 DB 포트, DB 이름과 계정을 지정한다. 환경 변수에서 비밀번호를 추출하지 않는다. SQLite는 기존 서버 파일 연결만 지원한다.

DatabaseTargetAdapter는 매 연결마다 Docker 주소를 다시 조회한다. 원격에서는 게시된 포트를 우선 사용하고 없으면 bridge IPv4, host network는 loopback을 사용한다. JDBC 작업마다 대시보드 loopback에 임시 SSH 터널을 열고 연결 종료·실패 시 닫는다. local 장비는 SSH 없이 접근한다. Docker CLI 및 해당 계정의 Docker 접근 권한이 필요하며 대시보드가 소켓 마운트나 권한을 자동으로 추가하지 않는다. 컨테이너 재생성으로 ID가 변경되면 다시 선택해야 한다. TLS와 DB 계정 권한은 기존 설정을 유지한다.

## SQL 실행 동작

OWNER 세션/CSRF가 필요하다. 실행은 최대 2 worker와 대기 8개로 제한한다. POST는 execution ID를 반환하며 GET polling과 POST cancel을 제공한다. Query timeout은 20초, 원격 연결·socket timeout은 각각 약 5초·20초다. 연결은 작업마다 열고 `try-with-resources`로 닫으며 영속 pool을 만들지 않는다. 결과는 최대 200행·64열·약 1 MiB, 문자열 cell은 최대 16,000자로 제한하고 binary는 placeholder로 반환한다. 테이블 브라우저는 25/50/100/200행 페이지를 사용하고 count가 3초 안에 끝나지 않으면 `-1`로 표시한다.

READ_ONLY는 서버에서 SELECT 한 문장만 허용하며 JDBC read-only transaction 또는 SQLite `PRAGMA query_only`도 사용한다. `DROP`, `TRUNCATE`, `ALTER`, `DELETE`·`UPDATE`에는 별도 확인 요청이 필요하다. 주석과 다중 statement는 거부한다. 이 검사는 DB 계정 권한을 대신하지 않는다. 최소 권한 DB 사용자를 등록한다. SQL은 기본적으로 Query History에 저장되므로 민감 정보가 포함된 쿼리는 실행하지 않는다. `password`, `secret`, `token`, `credential`, `identified by`가 포함된 쿼리는 이력 본문을 생략하고 즐겨찾기로 저장할 수 없다.

연결 실패는 분류 코드(`HOST_UNREACHABLE`, `AUTHENTICATION_FAILED`, `DATABASE_NOT_FOUND`, `PERMISSION_DENIED`, `TLS_ERROR`, `CONNECTION_TIMEOUT`, `QUERY_TIMEOUT_OR_CANCELLED`, `FILE_UNAVAILABLE`, `DATABASE_ERROR`)로 전달하며 JDBC 예외 문자열과 비밀번호는 반환하지 않는다. DB host는 서버에서 접근 가능한 네트워크로 나간다. 사용자는 방화벽/VPN 정책과 DB 계정 권한을 별도로 관리한다.

## Service와 MCP

Service의 `DATABASE` 리소스는 기존 `database_connections.id`를 참조한다. 삭제된 연결은 orphan으로 표시한다. Service Context의 `databases` map에는 ID·이름·종류·모드·연결 여부만 들어간다. Codex MCP의 `list_database_connections`, `get_database_metadata`, `list_database_tables`, `describe_database_table`은 동일 service 경계의 metadata만 읽는다. 임의 SQL 실행 도구와 write 권한은 없다.

## 검증

`mvn verify`는 SQLite·권한·MCP 테스트를 실행한다. 별도 DB 서버에 접속하는 `DatabaseRemoteAdapterTest`는 기본 빌드에서 건너뛴다. 일회성 PostgreSQL/MySQL 서버를 준비하고 `DATABASE_STUDIO_PG_PORT`, `DATABASE_STUDIO_MYSQL_PORT`, `DATABASE_STUDIO_TEST_PASSWORD`를 설정하면 실제 메타데이터와 READ_ONLY 거래 차단을 검증한다. DB 이름과 사용자 이름은 테스트 전용 `studio`다.

### 장비 대상 검증 (2026-10-09)

`my-dashboard-db-check:targets` 격리 이미지에서 Java 195개 중 186개 통과·9개 외부 연동 테스트 건너뜀, Python 47개 통과. DatabaseTargetMigrationTest는 기존 연결·암호문 보존과 반복 초기화를, DatabaseTargetAdapterTest는 주소 재조회·잘못된 대상 거부·실패 시 터널 정리를 확인한다. 기존 SQL 에디터 polling/focus 회귀 검사도 통과했다.

`scripts/check-databases.mjs`는 격리 대시보드 18189에서 PostgreSQL/MySQL/MariaDB DEVICE 연결과 PostgreSQL/MySQL DOCKER 연결을 실제 SSH/JDBC로 검증한다. UI 연결 테스트·저장·DDL/SELECT·테이블 조회, READ_ONLY 쓰기 거부와 자격 증명 유지가 대상이다. MySQL fixture는 TLS REQUIRE를 사용한다. fixture는 사용자 DB에 실행하면 안 된다.

Docker 목록/inspect는 실제 테스트 컨테이너의 ID·네트워크 정보를 기록해 재생하는 CLI fixture를 사용했다. 따라서 실제 원격 Docker 데몬 권한·CLI 실행의 종단 검증과는 구분한다. 호스트 Docker 소켓을 테스트 SSH 컨테이너에 연결하는 방식은 자동 승인 검토가 호스트 제어 권한 위험으로 거부했으며 적용하지 않았다. 운영 장비의 Docker CLI와 SSH 계정 접근 권한은 별도 확인 대상이다. 이번 DB 기능은 검증 이미지에 반영했으며 운영 컨테이너는 재배포하지 않았다.

최종 Playwright 결과: 위 5개 연결 흐름 통과, 1440px/390px 가로 넘침 없음, console/pageerror 0건. 결과는 artifacts/database-target-report.json, 화면은 artifacts/database-target-1440.png 및 database-target-390.png에 저장한다.

## 간단한 연결 폼

기본 화면에는 연결 방식·DB 종류와 해당 대상에 필요한 접속 정보만 표시한다. 서버형의 데이터베이스 이름과 SQLite 기존 파일 경로를 별도 라벨·도움말로 구분한다. SQLite에서는 서버 주소·사용자·비밀번호·포트·TLS를 숨긴다. 연결 별칭은 선택 사항이며 비워 두면 DB/파일 이름과 접속 대상으로 자동 생성한다. API의 필수 name 계약은 유지한다.

포트(종류별 기본값), TLS, 조회/수정 권한, 별칭은 접힌 고급 설정에 둔다. 조회만 허용이 기본이다. Metadata JSON은 입력 화면에서 제거하고 기존 값을 보존한다. 연결 테스트도 브라우저 필수 입력 검증을 거친다. `scripts/check-database-form.mjs`는 격리 QA SQLite에서 별칭 없는 테스트·저장 및 desktop/mobile 화면을 확인한다.