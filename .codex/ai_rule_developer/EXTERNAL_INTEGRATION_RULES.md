# 외부 연동 규칙 - my-dashboard-be

외부 시스템 연동은 반드시 명시적인 client 또는 adapter 경계 뒤에 둔다.

[대상]
- LLM API
- Lab, simulation, batch, worker server
- Third-party HTTP API 또는 SDK
- 현재 저장소 프로세스 밖의 모든 시스템

[구현 규칙]
- 확인된 인증 정보, 계약, timeout, 실패 매핑을 기준으로 외부 호출을 구현한다.
- Service는 비즈니스 의도를 드러내는 좁은 메서드로 외부 연동을 호출한다.
- Upstream DTO는 내부 DTO로 변환한 뒤 호출자에게 반환한다.
- Request/Response schema와 연동 문서는 실제 구현과 함께 갱신한다.

[금지]
- controller 또는 UI component에서 외부 HTTP/SDK 직접 호출

[Spring Boot 연동 메모]
- Upstream HTTP/SDK 호출은 전용 client bean으로 분리한다.
- Retry, timeout, circuit 동작은 controller 밖에서 관리한다.
- 외부 실패는 ControllerAdvice가 처리하기 전에 service/domain exception으로 매핑한다.
