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

## SQL 실행

OWNER 세션/CSRF가 필요하다. 실행은 최대 2 worker와 대기 8개로 제한한다. POST는 execution ID를 반환하며 GET polling과 POST cancel을 제공한다. Query timeout은 20초, 원격 연결·socket timeout은 각각 약 5초·20초다. 연결은 작업마다 열고 `try-with-resources`로 닫으며 영속 pool을 만들지 않는다. 결과는 최대 200행·64열·약 1 MiB, 문자열 cell은 최대 16,000자로 제한하고 binary는 placeholder로 반환한다. 테이블 브라우저는 25/50/100/200행 페이지를 사용하고 count가 3초 안에 끝나지 않으면 `-1`로 표시한다.

READ_ONLY는 서버에서 SELECT 한 문장만 허용하며 JDBC read-only transaction 또는 SQLite `PRAGMA query_only`도 사용한다. `DROP`, `TRUNCATE`, `ALTER`, `DELETE`·`UPDATE`에는 별도 확인 요청이 필요하다. 주석과 다중 statement는 거부한다. 이 검사는 DB 계정 권한을 대신하지 않는다. 최소 권한 DB 사용자를 등록한다. SQL은 기본적으로 Query History에 저장되므로 민감 정보가 포함된 쿼리는 실행하지 않는다. `password`, `secret`, `token`, `credential`, `identified by`가 포함된 쿼리는 이력 본문을 생략하고 즐겨찾기로 저장할 수 없다.

연결 실패는 분류 코드(`HOST_UNREACHABLE`, `AUTHENTICATION_FAILED`, `DATABASE_NOT_FOUND`, `PERMISSION_DENIED`, `TLS_ERROR`, `CONNECTION_TIMEOUT`, `QUERY_TIMEOUT_OR_CANCELLED`, `FILE_UNAVAILABLE`, `DATABASE_ERROR`)로 전달하며 JDBC 예외 문자열과 비밀번호는 반환하지 않는다. DB host는 서버에서 접근 가능한 네트워크로 나간다. 사용자는 방화벽/VPN 정책과 DB 계정 권한을 별도로 관리한다.

## Service와 MCP

Service의 `DATABASE` 리소스는 기존 `database_connections.id`를 참조한다. 삭제된 연결은 orphan으로 표시한다. Service Context의 `databases` map에는 ID·이름·종류·모드·연결 여부만 들어간다. Codex MCP의 `list_database_connections`, `get_database_metadata`, `list_database_tables`, `describe_database_table`은 동일 service 경계의 metadata만 읽는다. 임의 SQL 실행 도구와 write 권한은 없다.

## 검증

`mvn verify`는 SQLite·권한·MCP 테스트를 실행한다. 별도 DB 서버에 접속하는 `DatabaseRemoteAdapterTest`는 기본 빌드에서 건너뛴다. 일회성 PostgreSQL/MySQL 서버를 준비하고 `DATABASE_STUDIO_PG_PORT`, `DATABASE_STUDIO_MYSQL_PORT`, `DATABASE_STUDIO_TEST_PASSWORD`를 설정하면 실제 메타데이터와 READ_ONLY 거래 차단을 검증한다. DB 이름과 사용자 이름은 테스트 전용 `studio`다.
