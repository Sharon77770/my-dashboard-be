# 도메인 모델 규칙 - my-dashboard-be

도메인 모델은 명확한 상태와 경계를 기준으로 설계한다.

[Entity 규칙]
- Entity는 저장 상태와 DB 매핑을 표현한다.
- Entity는 Request DTO, Response DTO, Schema, View model과 분리한다.
- Entity를 public API나 UI-facing service 결과로 직접 노출하지 않는다.
- Entity 변경 시 migration, repository, DB 문서를 함께 검토한다.

[DTO 및 Schema 규칙]
- Request DTO는 입력 계약을 표현한다.
- Response DTO는 출력 계약을 표현한다.
- DTO는 API 계약에 맞게 설계할 수 있지만 Entity는 persistence 중심으로 유지한다.

[Enum 및 상태 규칙]
- 상태값은 명시적 enum 또는 상수로 관리한다.
- 비즈니스 로직에서 상태 문자열을 직접 사용하지 않는다.
- 상태 전이는 Service/Application 계층에서 검증한 뒤 저장한다.
- 허용 상태와 상태 전이는 기능 명세 또는 DB 명세에 문서화한다.

[상태 전이 템플릿]
- DRAFT -> READY -> QUEUED -> RUNNING -> COMPLETED
- FAILED와 CANCELLED는 도메인에서 사용할 경우 명시적 종료 상태 또는 복구 상태로 처리한다.

[인증 도메인]
- 사용자, role, session, token, audit 상태를 명확한 모델로 표현한다.
- 인증 상태를 무관한 도메인 entity에 섞지 않는다.

[Spring Boot 도메인 메모]
- JPA Entity와 Request/Response DTO를 분리한다.
- 영속 상태는 enum type으로 관리하고 DB column 값을 문서화한다.
- transaction이 필요한 상태 전이는 service method에서 적용한다.
