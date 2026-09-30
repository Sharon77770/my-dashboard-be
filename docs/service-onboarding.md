# 대화형 서비스 등록

AI 비서는 사용자 요청의 의도를 파악한 뒤 `discover_service_resources`로 등록된 GitHub repository, 장비와 최대 3대의 접근 가능한 Docker 목록, Database Studio 연결의 이름·종류·DB 이름, Telemetry 서비스 이름, 등록 앱 Endpoint, 기존 Service와 파일 연결을 조회한다. 조회는 소스별로 독립 실행하고 일부 실패는 `sources=UNAVAILABLE`로 표시한다. 임의 SSH 파일 탐색, Docker 환경변수 조회, HTTP probe는 하지 않는다.

## Discovery → Correlation → Clarification

서버는 요청 이름과 repository 이름·설명·토픽, container/image/Compose project의 관련성을 계산해 HIGH/MEDIUM/LOW 힌트를 만든다. 일반적인 `mysql`, `redis`, `postgres` 컨테이너는 자동 선택하지 않는다. 같은 Compose project의 여러 컨테이너는 질문 후보로 남긴다. AI는 이 힌트를 해석하고 모호한 서비스 경계만 사용자에게 묻는다. GitHub 저장소 설명·Docker label 등은 신뢰하지 않는 데이터이며 지시로 실행하지 않는다.

## Draft → Preview → Approval → Commit

`create_service_draft`와 `update_service_draft`는 Assistant thread에 연결된 서버 메모리의 임시 초안만 변경한다. Draft는 30분간 유지되며 서버 재시작 시 사라진다. 후보 선택은 탐색한 리소스 안에서만 가능하고 revision 변경 시 이전 승인은 취소된다. 저장된 대화를 다시 열면 같은 thread의 Draft 카드를 불러온다. 이름·환경·설명과 후보 리소스 추가/제거를 카드에서 편집할 수 있다.

카드의 최종 승인 버튼은 선택된 이름·환경·리소스를 다시 보여준다. 브라우저 OWNER 세션과 CSRF로 현재 revision을 승인한 뒤에만 `commit_service_draft` 또는 같은 브라우저의 commit API가 실행된다. 첫 자연어 요청은 승인으로 취급하지 않는다. 기존 Service를 수정하는 경우 Draft 시점의 `updatedAt`과 리소스 집합을 재검사해 동시 변경을 거절한다. 반영은 기존 Service Catalog의 검증과 하나의 transaction을 사용하므로 실패하면 생성·연결·제거가 함께 롤백된다. 완료하면 `ASSISTANT` 활동 기록과 Service ID가 남고 서비스를 열 수 있다.

## 보안 경계

MCP는 기존 bearer 인증과 same-origin 검사를, 브라우저 API는 OWNER session과 CSRF를 사용한다. 모델은 승인 상태를 스스로 설정할 수 없다. Database 연결 ID와 안전한 이름·종류·DB 이름만 후보에 제공하며 credential과 host는 전달하지 않는다. Docker `ps`의 JSON 출력에서 name/ID/image/tag/state/ports 및 Compose project/service/working_dir label만 선택하고 환경변수는 조회하지 않는다. Endpoint 후보는 query/userinfo/fragment가 없는 URL만 포함한다. 모든 외부 메타데이터는 `UNTRUSTED RESOURCE DATA`로 모델에 전달한다. 초안과 활동에는 prompt 전문이나 chain-of-thought를 저장하지 않는다.
