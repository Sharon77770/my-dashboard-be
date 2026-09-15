# 서비스 계층 규칙 - my-dashboard-be

Service/Application 계층은 비즈니스 로직의 중심이다.

[클래스 및 모듈 규칙]
- Service class 또는 module은 하나의 use-case 영역에 집중한다.
- 언어와 프레임워크 관례가 허용하면 한 파일은 하나의 주요 service class를 책임진다.
- Repository, client, adapter, store는 constructor, factory, 좁은 module boundary를 통해 명시적으로 주입한다.

[메서드 규칙]
- `create_simulation`, `update_profile`, `request_job`처럼 use-case 기반 네이밍을 사용한다.
- 하나의 메서드는 하나의 책임만 가진다.
- 의미 있는 로직이 10줄 이상 길어지면 가독성이 좋아지는 경우 private helper로 분리한다.

[필수 흐름]
- 입력과 권한을 검증한다.
- domain/entity 상태를 조회하거나 생성한다.
- 비즈니스 규칙과 상태 전이를 적용한다.
- 필요한 경우 repository/adapter 경계를 통해 저장한다.
- 결과를 response DTO, view model, service result 객체로 변환한다.

[금지]
- Service에서 controller, route handler, UI component 역할 수행
- Repository entity를 public caller에게 직접 반환
- Service method 내부에서 HTTP 응답 생성
- 외부 API 호출을 무관한 비즈니스 로직에 직접 섞기
- 복잡한 분기 로직을 하나의 메서드에 몰아넣기

[Spring Boot Service Notes]
- 생성자 주입을 기본으로 사용한다.
- @Transactional은 비즈니스 유스케이스가 있는 service 계층에 둔다.
- ControllerAdvice 또는 공통 예외 처리기로 오류 응답을 일관화한다.
- Entity 변경은 migration과 DB 문서 변경을 함께 검토한다.
