# API 설계 규칙 - my-dashboard-be

모든 공개 API는 리소스 중심 REST 설계를 따른다.

[기본 규칙]
- 프로젝트에 별도 버전 규칙이 없으면 기본 prefix는 `/api/v1`로 둔다.
- URL은 명사형으로 작성하고 동작은 HTTP method로 표현한다.
- 구현 함수가 아니라 프로젝트 도메인 리소스를 기준으로 API를 설계한다.
- Request DTO/schema, Response DTO/schema, persistence entity/model을 분리한다.
- 공개 엔드포인트는 모두 `docs/api/endpoints.md`와 `docs/api/specification.md`에 문서화한다.
- 각 엔드포인트에는 method, path, request field, response field, status code, error case를 포함한다.
- 문서화되지 않은 응답 필드나 숨은 부수효과를 추가하지 않는다.

[리소스 흐름]
- 엔드포인트를 추가하기 전에 도메인 리소스 흐름을 먼저 정의한다.
- 하위 리소스는 실제 소유 관계나 생명주기 관계가 있을 때만 사용한다.
- pipeline, worker 같은 내부 구현 이름은 제품 계약이 아닌 한 public resource로 노출하지 않는다.

[Spring Boot]
- Request DTO와 Response DTO를 분리한다.
- 상태 코드는 Controller에서 명시적으로 결정한다.
- Bean Validation을 DTO 입력 계약에 적용한다.

[인증 및 권한]
- 각 엔드포인트가 public, authenticated, admin-only 중 무엇인지 명시한다.
- token/session 동작, refresh 동작, 만료 동작을 API 문서에 정의한다.
- 인증되지 않은 요청은 401, 권한이 부족한 인증 사용자는 403으로 구분한다.

[외부 API 경계]
- 내부 API 계약과 외부 API 계약을 분리한다.
- 외부 응답 형태는 내부 DTO로 변환한 뒤 호출자에게 반환한다.
- 계약이 확인된 경우 timeout, retry, fallback, failure mapping 규칙을 문서화한다.
