# 대시보드 도우미

대시보드 오른쪽 아래의 ✦ 버튼에서 여는 앱 전용 대화 화면이다. IDE의 Codex 패널과 화면·상태·API 진입점을 분리한다. 어느 대시보드 페이지에서든 버튼을 드래그해 위치를 옮길 수 있고, 브라우저별 위치와 현재 대화는 브라우저 저장소에 보관한다. 화면은 대시보드 테마와 모바일 폭을 따른다.

이 assistant는 개인 대시보드 기능을 다루는 용도로만 사용한다. 페이지나 등록 앱을 열고, 대시보드에 저장된 일정과 노트를 찾거나 작성할 수 있다. 캘린더는 외부 서비스 연결 없이 대시보드 자체 일정을 조회한다. 모델과 추론 강도는 Codex가 반환한 사용 가능한 목록에서 고른다. 첫 사용 시 대시보드 서버에서 Codex CLI를 준비하고 기기 코드로 로그인한다.

로그인이 필요하면 도우미의 `Codex 로그인` 버튼을 누른다. CLI가 발급한 OpenAI 인증 URL과 일회용 코드를 도우미 안에 표시하며, 인증 페이지는 새 창에서 열고 코드를 복사할 수 있다. 로그인 완료 후 계정 상태를 다시 확인한다. 인증 URL과 코드는 브라우저 저장소에 보관하지 않는다.

## 제공 기능

서버는 Streamable HTTP MCP를 `/api/v1/mcp`에 제공한다. 도구는 기존 `PlannerService`, `NoteService`, `CatalogService`에 위임되며 입력 검증과 revision 검사를 그대로 적용한다.

| Tool | 기능 |
| --- | --- |
| `open_page` | 등록된 대시보드 페이지 열기 |
| `list_apps`, `open_app` | 등록 앱 조회 및 기존 브라우저 설정으로 앱 열기 |
| `list_calendar_events`, `create_calendar_event` | 일정 조회 및 생성 |
| `list_notes`, `read_note` | 노트 목록 및 문서 읽기 |
| `create_note_folder`, `create_note`, `append_note` | 폴더·문서 생성 및 문서에 이어 쓰기 |

일정이나 노트처럼 대시보드 데이터를 변경하는 도구가 확인을 요청하면 대화 안에서 내용을 확인하고 계속 진행할 수 있다. 도구에는 삭제, IDE 파일 편집, 임의 명령 실행 기능을 제공하지 않는다. 페이지 이동 요청은 서버 이벤트로 기록하고 브라우저가 polling하여 기존 화면 전환 함수를 호출한다.

## 실행 경계

대시보드 API는 `/api/v1/assistant/jobs`이며 기존 OWNER 로그인 세션과 CSRF 검사를 사용한다. 대화 화면에서는 대시보드 페이지, 앱, 일정, 노트 작업에 맞는 요청만 제공하고 프로젝트 파일 컨텍스트·코드 리뷰·스킬·저장소 조작 UI를 표시하지 않는다. 일정·노트 도구의 기존 확인 질문은 대화 안에서 답한다.

assistant는 기존 서버 Codex job 경로를 사용한다. 이번 변경은 IDE의 `StudioCodex` 화면을 재사용하지 않고 앱 자체에 맞는 대화 UI를 제공한다. IDE의 프로젝트 Codex UI와 SSH job 경로는 유지한다.

## 인증 및 배포

MCP 요청은 `Authorization: Bearer` 인증을 사용한다. Compose에서 `DASHBOARD_MCP_TOKEN`을 선택적으로 설정할 수 있으며 설정할 경우 32자 이상을 사용한다. 비어 있으면 부팅 시 난수 256-bit 토큰을 만들고 대시보드 서버 Codex 프로세스에만 전달한다. 외부 MCP client 연결을 사용하려면 `.env`에 임의 토큰을 지정하고 client에 같은 토큰을 설정한다. `.env`는 Git에 포함하지 않는다.

외부 MCP 연결은 HTTPS reverse proxy 또는 VPN으로 보호한 뒤 `/api/v1/mcp`를 지정한다. 대시보드 로그인 세션 쿠키는 MCP 인증에 쓰지 않는다. CSRF 검사는 MCP 경로에서만 제외하고 bearer 검증은 MCP controller에서 상수 시간 비교로 수행한다. assistant browser events와 job endpoint는 기존 OWNER 세션 및 CSRF 정책을 유지한다.

MCP 앱 동작은 현재 단일 OWNER 계정과 해당 계정의 개인 일정·노트 저장소를 사용한다. 대화 전 Codex App Server의 `personal-dashboard` 연결 상태를 조회하며, 연결이 없거나 준비되지 않으면 도우미 요청을 시작하지 않고 복구가 필요함을 표시한다. MCP navigation queue와 기본 난수 토큰은 서버 재시작 시 초기화된다. 외부 client에서 고정 bearer token을 운영하려면 `DASHBOARD_MCP_TOKEN`을 직접 제공한다.

서버 assistant 준비는 매 setup에서 `personal-dashboard` MCP 서버를 Codex CLI에 다시 등록해 URL과 bearer 환경변수 연결을 복구한다. 등록 실패는 setup job 실패로 전달한다. 갱신 전용 setup도 CLI 업데이트 확인 후 MCP 등록을 다시 맞춘다.
