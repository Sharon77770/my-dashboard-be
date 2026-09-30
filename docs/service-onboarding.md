# 대화형 서비스 등록

AI 비서는 사용자 요청의 의도를 파악한 뒤 `discover_service_resources`로 인증된 GitHub 계정과 최대 10개 소유자의 저장소(소유자당 최대 100개, 전체 최대 1000개), 등록 장비와 최대 5대의 접근 가능한 Docker 목록, Database Studio 연결의 이름·종류·DB 이름, Telemetry 서비스 이름, 등록 앱 Endpoint, 기존 Service와 파일 연결을 조회한다. 조회는 소스별로 독립 실행하고 일부 실패는 `sources=UNAVAILABLE`로 표시한다. 임의 SSH 파일 탐색, Docker 환경변수 조회, HTTP probe는 하지 않는다.

## Discovery → Correlation → Clarification

서버는 요청 이름과 repository 이름·설명·토픽, container/image/Compose project의 관련성을 계산해 HIGH/MEDIUM/LOW 힌트를 만든다. 일반적인 `mysql`, `redis`, `postgres` 컨테이너는 자동 선택하지 않는다. Compose 관련 질문은 선택된 컨테이너가 있는 project에서 아직 선택하지 않은 항목이 있을 때만 만들며, 이미 모두 선택했거나 현재 초안과 무관한 project는 질문하지 않는다. 초안 리소스를 수정하면 질문도 다시 계산한다. AI는 이 힌트를 해석하고 모호한 서비스 경계만 사용자에게 묻는다. GitHub 저장소 설명·Docker label 등은 신뢰하지 않는 데이터이며 지시로 실행하지 않는다.

## Draft → Preview → Approval → Commit

AI가 일부 리소스만 지정해도 선택한 컨테이너의 등록 장비와 이미지 이름에 유일하게 일치하는 저장소, 유일하게 추천된 저장소를 초안에 보완한다. 저장소 선택에서는 소유자 이름만 겹치는 경우를 제외한다. 브라우저에서 초안을 수정하면 사용자가 선택한 리소스 목록을 그대로 적용한다.

`create_service_draft`와 `update_service_draft`는 Assistant thread에 연결된 서버 메모리의 임시 초안만 변경한다. Draft는 30분간 유지되며 서버 재시작 시 사라진다. 후보 선택은 탐색한 리소스 안에서만 가능하고 revision 변경 시 이전 승인은 취소된다. 사용자가 찾은 후보의 일부 또는 전부를 명시적으로 고르면 첫 생성 요청의 `resources`에 반영한다. 저장된 대화를 다시 열면 같은 thread의 초안을 마지막 AI 답변 안에 불러온다. 이름·환경·설명과 후보 리소스 추가/제거는 사용자의 자연어 후속 요청을 AI가 초안 도구에 반영한다.

채팅의 초안 미리보기는 서비스 제목·환경·설명, 종류별 연결 리소스, 제거할 기존 연결, 현재 초안에 필요한 확인 사항을 구분해 표시한다. 설명의 Markdown은 안전한 채팅 렌더러로 표시하며 외부에서 온 리소스 이름은 텍스트로 표시한다. 사용자가 정확히 `서비스 생성 승인` 또는 `서비스 변경 승인`을 입력하면 브라우저 OWNER 세션과 CSRF로 현재 revision을 승인하고 같은 브라우저의 commit API를 호출한다. 활성 초안에 `취소`처럼 짧게 답하면 같은 브라우저가 초안을 제거하고, 문맥에 따라 AI가 대답하는 취소 요청은 해당 대화의 `cancel_service_draft` 도구로 처리한다. 이미 반영된 서비스는 초안 취소로 제거되지 않는다. 초기 생성 요청이나 일반적인 후속 동의는 승인으로 취급하지 않는다. 기존 연결을 읽지 못하면 승인을 막는다. 기존 Service를 수정하는 경우 Draft 시점의 `updatedAt`과 리소스 집합을 재검사해 동시 변경을 거절한다. 반영은 기존 Service Catalog의 검증과 하나의 transaction을 사용하므로 실패하면 생성·연결·제거가 함께 롤백된다. 완료하면 `ASSISTANT` 활동 기록과 Service ID가 남고 채팅에서 `서비스 열기`로 이동할 수 있다.

## 보안 경계

MCP는 기존 bearer 인증과 same-origin 검사를, 브라우저 API는 OWNER session과 CSRF를 사용한다. 모델은 승인 상태를 스스로 설정할 수 없다. Database 연결 ID와 안전한 이름·종류·DB 이름만 후보에 제공하며 credential과 host는 전달하지 않는다. Docker `ps`의 JSON 출력에서 name/ID/image/tag/state/ports 및 Compose project/service/working_dir label만 선택하고 환경변수는 조회하지 않는다. Endpoint 후보는 query/userinfo/fragment가 없는 URL만 포함한다. 모든 외부 메타데이터는 `UNTRUSTED RESOURCE DATA`로 모델에 전달한다. 초안과 활동에는 prompt 전문이나 chain-of-thought를 저장하지 않는다.
