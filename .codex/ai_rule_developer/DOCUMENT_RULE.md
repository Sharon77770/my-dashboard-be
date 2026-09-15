# 문서화 규칙 - my-dashboard-be

이 프로젝트는 문서를 구현의 일부로 간주한다.
프로젝트 자체에 대한 명세는 반드시 프로젝트 루트의 `docs/` 아래에 둔다.
`.codex/ref_docs`는 외부 아키텍처 문서, PRD, 리서치, 벤더 문서 등 사용자가 임의로 추가하는 참고자료 공간일 뿐이다.

[문서 위치]
- `.codex/ai_rule_developer`: 코딩할 때 지켜야 하는 규칙.
- `.codex/ref_docs`: 외부/사용자 추가 참고자료 전용. 디렉토리만 생성하고 프로젝트 명세 파일은 생성하지 않는다.
- `docs/architecture`: 프로젝트 아키텍처와 흐름 문서.
- `docs/api`: 엔드포인트 목록과 API 계약 문서.
- `docs/database`: DB schema 문서.

[문서화 수준: standard]
- 코드 변경이 동작, API 계약, DB schema, architecture, auth, external integration에 영향을 주면 관련 문서를 함께 수정한다.
- `docs/architecture`, `docs/api`, `docs/database`는 실제 구현과 일치해야 한다.
- 작업 완료 전 문서 변경 필요 여부를 반드시 검토한다.

[docs/architecture/directory.md]
- 전체 주요 디렉토리 구조를 최신 상태로 유지한다.
- 주요 디렉토리와 주요 파일의 역할을 설명한다.
- 파일 이동, 모듈 분리, 새 계층 추가 시 반드시 갱신한다.
- 존재하지 않는 파일을 문서화하지 않는다.

[docs/architecture/architecture.md]
- 전체 시스템 구조를 설명한다.
- 계층 간 의존성 방향을 포함한다.
- 해당되는 경우 데이터 흐름, 인증 흐름, API 흐름, 저장 흐름, 외부 연동 흐름을 포함한다.
- 구현 경계에 영향을 주는 중요한 아키텍처 결정을 기록한다.

[docs/architecture/component.md]
- component/module/service 분리 기준을 설명한다.
- 공통 component/module과 기능 전용 component/module을 구분한다.
- 해당되는 경우 props, DTO, service result, module contract 설계 원칙을 설명한다.
- 재사용 기준과 소유 경계를 명확히 한다.

[docs/architecture/state.md]
- state/store/domain state 구조를 설명한다.
- server state, UI state, form state, auth state, job state, domain status 같은 상태 종류를 정의한다.
- 상태 전이 규칙과 validation 책임 위치를 문서화한다.
- enum 값은 모두 나열하고 각 값이 언제 사용되는지 설명한다.

[docs/architecture/flow.md]
- 주요 기능 흐름을 단계별로 설명한다.
- validation, empty state, error state, permission behavior, retry behavior, side effect를 포함한다.
- 사용자 관찰 가능 동작이나 내부 orchestration이 바뀌면 갱신한다.

[docs/api/endpoints.md]
- 이 문서는 endpoint index 역할만 한다.
- HTTP method, URL, auth 요구사항, 한 줄 설명만 작성한다.
- request body, response body, status code 상세, 긴 설명을 작성하지 않는다.
- public controller/route/API handler에 존재하는 모든 엔드포인트는 여기에 있어야 한다.

[docs/api/specification.md]
- 이 문서는 API 계약서 역할을 한다.
- 모든 endpoint에 대해 method, URL, 설명, auth, path params, query params, request body, response body, status code, error case를 포함한다.
- request/response의 모든 field는 type, 필수/선택 여부, 의미, enum 값, null 허용 여부를 명시한다.
- error case는 error code, message, 발생 조건을 포함한다.
- `endpoints.md`와 `specification.md`는 반드시 동일한 endpoint set을 가져야 한다.

[docs/database/schema.md]
- 각 table에 대해 table name, 목적, 역할, owner module, soft delete 여부를 기록한다.
- 각 column에 대해 name, type, description, purpose, required 여부, default value, index, unique 여부, enum 값을 기록한다.
- primary key, foreign key, relation, cardinality, delete behavior, migration note를 기록한다.
- 인증이 있으면 user/session/token/audit 관련 table을 명확히 설명한다.

[반드시 문서를 수정해야 하는 경우]
- 새로운 API 추가 또는 기존 API 수정
- Request/Response field 변경
- Status code, error code, auth behavior 변경
- DB table, column, relation, index, enum, migration 변경
- Directory, module boundary, service flow, state model, feature flow 변경
- 외부 연동 contract, timeout, retry, fallback, failure mapping 변경

[금지 사항]
- 필요한 문서 업데이트 생략
- 실제 구현과 불일치하는 문서 작성
- 존재하지 않는 API나 테이블 문서화
- 프로젝트 명세를 `.codex/ref_docs`에 작성
- `docs/api/endpoints.md`에 endpoint 상세 내용 작성
- enum 값이나 optional/null 동작을 문서에서 생략
