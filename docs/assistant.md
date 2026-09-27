# 서버 Codex assistant와 MCP

대시보드 오른쪽 아래의 ✦ 버튼을 눌러 어느 페이지에서든 Codex 채팅을 연다. 버튼을 드래그해 위치를 옮길 수 있고 브라우저별 위치를 기억한다. 채팅은 대시보드 서버의 Linux 계정에서 `codex app-server`로 실행된다. 첫 사용 시 Codex CLI를 준비하고 기기 코드 로그인 상태를 연결한다. 인증·thread는 서버의 기존 Codex 홈에 저장된다.

모델과 추론 강도를 선택할 수 있다. Codex가 보낸 usage 이벤트에서 현재 모델의 컨텍스트 창과 대화가 사용한 토큰을 받아 남은 컨텍스트를 계산해 표시한다. 값은 현재 context window 기준 추정치다. 프로젝트 편집기의 Codex는 별도 기능이며 SSH 실행 대상을 요구한다. 전역 assistant의 서버 권한은 프로젝트 파일·Git job API로 전달하지 않는다.

## 제공 도구

서버는 Streamable HTTP MCP를 `/api/v1/mcp`에 제공한다. 도구는 기존 `PlannerService`, `NoteService`, `CatalogService`를 통해 실행되며 validation, revision 검사, 폴더 깊이·블록 제한이 그대로 적용된다.

| Tool | 기능 |
| --- | --- |
| `open_page` | 캘린더, 메모장, 클라우드, 파일, 앱, 장비 등 등록된 대시보드 화면 열기 |
| `list_apps`, `open_app` | 등록 외부 앱 목록 및 기존 브라우저 설정에 따른 앱 열기 |
| `list_calendar_events` | 반개구간 날짜 범위의 일정 조회 |
| `create_calendar_event` | 제목, 시작/종료, 종일, 장소, 메모, 색상으로 일정 생성 |
| `list_notes`, `read_note` | 노트 폴더/문서 목록 및 문서 블록 조회 |
| `create_note_folder` | 루트 또는 기존 폴더 아래 폴더 생성 |
| `create_note` | 텍스트 블록을 포함한 문서 생성 |
| `append_note` | revision 일치 확인 후 문서 뒤에 텍스트 블록 추가 |

MCP 도구에는 폴더·문서·일정 삭제 또는 임의 파일/셸 실행 도구를 제공하지 않는다. 페이지 이동 요청은 서버 이벤트로 기록하고 브라우저가 1초 주기로 가져와 기존 화면 전환 함수를 호출한다. 이벤트 큐는 메모리에서 최대 100개를 보유한다.

## 인증 및 배포

MCP 요청은 `Authorization: Bearer` 인증을 사용한다. Compose에서 `DASHBOARD_MCP_TOKEN`을 선택적으로 설정할 수 있으며 설정할 경우 32자 이상을 사용한다. 비어 있으면 부팅 시 난수 256-bit 토큰을 만들고 서버 Codex 프로세스에만 전달한다. 외부 MCP client 연결을 사용하려면 `.env`에 임의 토큰을 지정하고 client에 같은 토큰을 설정한다. `.env`는 Git에 포함하지 않는다.

외부 MCP 연결은 HTTPS reverse proxy 또는 VPN으로 보호한 뒤 `/api/v1/mcp`를 지정한다. 대시보드 로그인 세션 쿠키는 MCP 인증에 쓰지 않는다. CSRF 검사는 MCP 경로에서만 제외하고 bearer 검증은 MCP controller에서 상수 시간 비교로 수행한다. assistant browser events와 job endpoint는 기존 OWNER 세션 및 CSRF 정책을 유지한다.

MCP 앱 동작은 현재 단일 OWNER 계정과 해당 계정의 개인 일정/노트 저장소를 사용한다. MCP navigation queue와 기본 난수 토큰은 서버 재시작 시 초기화된다. 외부 client에서 고정 bearer token을 운영하려면 `DASHBOARD_MCP_TOKEN`을 직접 제공한다.
