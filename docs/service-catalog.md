# Service Catalog

AI 비서의 Service Onboarding은 별도의 임시 Draft에서 진행한다. `discover_service_resources`로 기존 리소스를 읽고, 이름·이미지·Compose label·기존 연결을 조합한 후보와 짧은 이유를 만든다. Database host/username/password, 장비 자격 증명, Docker env, Telemetry API key는 탐색 결과에 포함하지 않는다. 사용자가 Draft 카드를 수정하고 브라우저에서 승인한 현재 revision만 `ServiceCatalogService.applyAssistantDraft`가 하나의 SQLite transaction으로 생성/연결/해제를 반영한다. 활동에는 `ASSISTANT` 출처가 남는다. [대화형 서비스 등록](service-onboarding.md).

Service는 앱 또는 운영 서비스의 이름, 아이콘, 환경을 소유한다. GitHub, 장비, Docker, Telemetry를 복제하지 않는다. `service_resources`의 타입과 참조를 통해 기존 모듈에 연결한다.

```text
Service
 ├─ GitHub Repository / Organization → GithubService → GithubCliAdapter
 ├─ Device / Docker Container        → CatalogService / DeviceOperations
 ├─ Telemetry                        → TelemetryService
 ├─ Endpoint                         → 사용자 브라우저 HTTP(S) 링크
 ├─ File                             → 기존 Files 화면의 위치
 └─ Database                         → DatabaseStudioService의 연결 ID
```

`ServiceCatalogService`는 저장된 연결을 해석하고 Health, Activity, Context를 만든다. `ServiceDto.Context`는 HTTP와 MCP `get_service_context`가 함께 사용한다. MCP는 기존 비공개 bearer 인증을 거치고 OWNER 권한으로 읽는다. UI의 Ask Codex는 선택 서비스 ID를 입력창에 넣어 이 도구 사용을 요청한다. 자격 증명은 Context에 포함하지 않는다. DATABASE binding은 기존 연결 ID를 검증하며 Context에는 이름·종류·모드·접속 여부만 넣는다.

## 연결과 삭제

장비와 Telemetry는 기존 ID를 선택한다. GitHub 저장소는 접근 가능한 `owner/name`을 기존 GithubService로 확인한다. Docker는 기존 장비의 컨테이너 목록에서 선택하고 DeviceOperations로 존재를 검증한다. 컨테이너 재시작도 기존 DeviceOperations를 사용한다. 목록 조회 실패는 502로 응답한다. Endpoint는 HTTP(S) URL이며 서버에서 자동 호출하지 않는다. File은 절대 경로와 선택한 기존 장비 ID를 연결하고 Files 화면으로 전달한다. 같은 Service 안의 같은 type/reference/device 조합은 유일하다.

서비스 상세의 Runtime 탭에서 등록된 장비를 선택해 연결하거나 장비별 Docker 컨테이너 목록에서 컨테이너를 선택해 연결한다. 기존 연결을 이 탭에서 해제할 수도 있다. Settings의 리소스 연결 폼에서는 GitHub 저장소 목록을 `owner/name`으로 검색한 뒤 선택한다. 검색은 이미 조회한 목록을 필터링하며 연결 검증과 저장은 기존 Service API가 수행한다.

Telemetry 탭에서는 등록된 Telemetry 서비스 목록에서 아직 연결하지 않은 항목을 선택해 바로 연결한다. 현재 연결의 수집 상태·오늘 요청·오류율을 보고 연결을 해제할 수 있다. 삭제된 Telemetry 서비스의 연결은 조회 불가 상태로 남아 해제할 수 있다. 등록된 항목이 없거나 목록 조회가 실패하면 안내를 표시하며, Telemetry 앱으로 이동해 먼저 서비스를 등록할 수 있다.

Runtime 탭은 연결된 장비의 CPU·RAM·디스크 사용률과 접속 상태, 컨테이너의 실행 상태·이미지를 현재 값으로 조회한다. 컨테이너별 최근 로그 200줄을 화면에서 열고 다시 조회할 수 있으며, 서비스 상단의 Logs 바로 가기로도 첫 연결 컨테이너의 로그를 열 수 있다. 실행 상태에 따라 시작·중지·재시작할 수 있다. 로그는 브라우저의 현재 화면에만 표시하고 Context·Activity·SQLite에 저장하지 않는다. 각 작업은 Service에 저장된 연결 ID를 검증한 뒤 기존 DeviceOperations와 CommandAdapter의 고정 Docker 명령을 사용한다. 개별 상태 조회 실패는 다른 리소스 표시를 막지 않는다.

장비, Telemetry 또는 Database Connection이 삭제되면 연결은 남고 `orphaned=true`로 반환된다. 사용자가 설정에서 해제하거나 다시 유효한 리소스를 선택할 수 있다. Service 삭제는 그 Service의 연결과 카탈로그 활동만 삭제하며 기존 리소스는 삭제하지 않는다. GitHub 권한 변경 또는 외부 삭제는 Context에서 해당 항목을 건너뛰며 다른 데이터 조회를 유지한다.

## 상태와 활동

Device의 ONLINE/REACHABLE은 HEALTHY, 연결 실패는 DOWN이다. Docker `up`은 HEALTHY, 목록에 있고 실행 중이 아니면 DOWN, 조회 실패나 찾지 못한 컨테이너는 UNKNOWN이다. Telemetry의 `Receiving data`는 HEALTHY, 그 외는 UNKNOWN이다. 최근 GitHub Action 성공은 HEALTHY, 실패는 DEGRADED, 미확인은 UNKNOWN이다. Database 연결 테스트 성공은 HEALTHY, 실패는 DEGRADED다. Endpoint는 HTTP probe를 하지 않아 UNKNOWN이다. Orphan도 UNKNOWN이다. 연결하지 않은 타입은 평가하지 않는다. 운영 신호(Device/Docker)에 DOWN이 있고 다른 운영 신호가 HEALTHY면 DEGRADED, 그렇지 않으면 DOWN이다. 운영 신호가 정상이면서 UNKNOWN이 섞이거나 DEGRADED 신호가 있으면 DEGRADED다. HEALTHY만 있으면 HEALTHY, 관측 신호가 없거나 UNKNOWN만 있으면 UNKNOWN이다. GitHub Actions 실패만으로 서비스를 DOWN으로 판단하지 않는다.

활동은 `service_activity`의 연결/수정 이벤트와 기존 GitHub commit/Actions, 장비 최근 작업, Telemetry 마지막 수신 시각을 시간순으로 합친다. Event는 source, type, timestamp, severity, title, metadata를 가진다. 추후 Event Bus가 들어오면 같은 모델을 영속 이벤트 입력으로 확장할 수 있다. Notification과 Automation은 이번 단계에 포함되지 않는다.
