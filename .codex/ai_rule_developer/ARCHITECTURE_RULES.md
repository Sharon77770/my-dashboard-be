# 아키텍처 규칙 - my-dashboard-be

이 프로젝트는 기능 우선 계층형 아키텍처를 따른다. 책임과 의존성 방향을 반드시 분리한다.

[디렉토리 구조]
- `src/main/java/.../<feature>/controller`
- `src/main/java/.../<feature>/service`
- `src/main/java/.../<feature>/repository`
- `src/main/java/.../<feature>/entity`
- `src/main/java/.../<feature>/dto`
- `src/main/java/.../global`

[기능 모듈 배치]
- `login`, `post`, `talk` 같은 기능/도메인 폴더를 바깥 경계로 우선한다.
- 활성 profile에서 사용하는 `controllers`, `services`, `repositories`, `schemas`, `entities`, `components`, `hooks` 같은 계층 폴더는 각 기능 폴더 안에 둔다.
- 여러 기능에서 공유하는 설정, 공통 middleware, 공통 client만 shared/core/infrastructure에 둔다.

[계층 책임]
- Presentation/API 계층은 요청 파싱, 응답 변환, 라우팅, 화면 조립만 담당한다.
- Service/Application 계층은 유스케이스 조합과 비즈니스 판단을 담당한다.
- Repository/Adapter/Infrastructure 계층은 DB 접근과 외부 시스템 접근을 담당한다.
- Entity/Domain model은 저장 또는 도메인 상태를 표현하며 public response 객체로 직접 사용하지 않는다.
- Schema/DTO/View model은 외부 계약을 표현하며 persistence model과 분리한다.

[의존성 방향]
- 의존성은 Presentation/API/UI 계층에서 Service/Application, Repository/Adapter, Infrastructure 방향으로 흐른다.
- 상위 계층은 명시적 인터페이스 또는 좁은 모듈을 통해 하위 계층을 호출한다.
- 하위 계층은 UI, HTTP 응답, request, framework 객체를 상위 계층에서 가져오지 않는다.

[프로필별 아키텍처]

### Spring Boot
- 기능 경계는 `.../<feature>` package 아래에 둔다. 예: login, post, talk.
- Controller는 각 기능 package 안에서 HTTP 요청/응답과 인증 컨텍스트 연결만 담당한다.
- Service는 같은 기능 package 안에서 트랜잭션 경계와 비즈니스 규칙의 중심이다.
- Repository는 JPA 또는 데이터 접근 인터페이스로 제한한다.
- Entity는 DB 영속성 모델이고 DTO는 API 계약이다. Entity를 외부 응답으로 직접 노출하지 않는다.
- 공통 config, security, exception handler만 전역 package로 분리한다.
- 권장 흐름: Controller -> Service -> Repository -> DB, 단 각 계층은 같은 기능 package 안에 둔다.

## 외부 연동 계층
- 외부 HTTP/SDK 연동은 client 또는 adapter 모듈 뒤에 둔다.
- Service는 비즈니스 의도를 드러내는 좁은 메서드로 adapter를 호출한다.
- Upstream DTO와 내부 DTO를 분리한다.

## DB 및 Repository
- `sqlite` 접근은 repository 또는 persistence 모듈 안에 격리한다.
- Schema/entity 변경 시 migration, repository method, DB 문서를 함께 검토한다.
- Persistence entity를 외부 API 응답으로 직접 노출하지 않는다.

## 인증 경계
- Authentication은 호출자를 식별하고 authorization은 해당 동작 가능 여부를 결정한다.
- Password hashing, token handling, session persistence는 전용 모듈에 둔다.
- 보호된 service method는 필요한 경우 명시적 actor/user context를 전달받는다.
