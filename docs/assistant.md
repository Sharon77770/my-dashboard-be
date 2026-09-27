# 대시보드 도우미

홈 화면의 `대시보드 도우미` 아이콘, Dock, 모든 앱 또는 검색에서 여는 내장 앱이다. 기존 홈 배치를 저장한 브라우저에서는 모든 앱이나 검색으로 열고 홈 편집에서 아이콘을 추가할 수 있다. 도우미는 일반 앱처럼 실행 중인 앱 목록과 뒤로 가기에 참여한다. IDE의 Codex 패널과 화면·상태·API 진입점을 분리한다. 현재 대화는 브라우저 세션 저장소에 보관하며 화면은 대시보드 테마와 모바일 폭을 따른다.

이 assistant는 개인 대시보드 기능을 다루는 용도로만 사용한다. 페이지나 등록 앱을 열고, 대시보드에 저장된 일정과 노트를 찾거나 작성할 수 있다. 캘린더는 외부 서비스 연결 없이 대시보드 자체 일정을 조회한다. 모델과 추론 강도는 Codex가 반환한 사용 가능한 목록에서 고른다. 첫 사용 시 대시보드 서버에서 Codex CLI를 준비하고 기기 코드로 로그인한다.

도우미 앱에는 Codex App Server의 `account/rateLimits/read` 결과를 바탕으로 기간별 잔여 사용량 비율과 초기화 시각을 표시한다. 앱을 열거나 대화가 완료될 때 갱신하며 새로고침 버튼으로 다시 조회할 수 있다. 이 값은 계정 사용량 한도의 잔여 비율이며 정확한 잔여 토큰 수가 아니다. 조회 실패 시 대화 기능은 계속 사용할 수 있고 잔여 사용량을 확인할 수 없다고 표시한다.

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

메시지를 보낸 뒤 답변이 오기 전에는 채팅 말풍선에 움직이는 진행 표시를 띄운다. Codex turn 및 reasoning 이벤트가 오면 `생각하고 있어요`, 대시보드 도구 호출 중에는 `정보를 확인하고 있어요`, 답변 item이 시작되면 `답변을 작성하고 있어요`로 바꾼다. 실제 답변 텍스트가 도착하면 진행 표시를 답변으로 교체하고 완료·실패 시 제거한다. 추론 원문이나 요약 내용은 채팅에 표시하지 않는다. 브라우저를 새로 연 뒤에는 저장된 미완료 표시를 계속 실행 중인 요청으로 보여주지 않는다.

## 실행 경계

대시보드 API는 `/api/v1/assistant/jobs`이며 기존 OWNER 로그인 세션과 CSRF 검사를 사용한다. 대화 화면에서는 대시보드 페이지, 앱, 일정, 노트 작업에 맞는 요청만 제공하고 프로젝트 파일 컨텍스트·코드 리뷰·스킬·저장소 조작 UI를 표시하지 않는다. 일정·노트 도구의 기존 확인 질문은 대화 안에서 답한다.

assistant는 기존 서버 Codex job 경로를 사용한다. 이번 변경은 IDE의 `StudioCodex` 화면을 재사용하지 않고 앱 자체에 맞는 대화 UI를 제공한다. IDE의 프로젝트 Codex UI와 SSH job 경로는 유지한다.

## 인증 및 배포

MCP 요청은 `Authorization: Bearer` 인증을 사용한다. Compose에서 `DASHBOARD_MCP_TOKEN`을 선택적으로 설정할 수 있으며 설정할 경우 32자 이상을 사용한다. 비어 있으면 부팅 시 난수 256-bit 토큰을 만들고 대시보드 서버 Codex 프로세스에만 전달한다. 외부 MCP client 연결을 사용하려면 `.env`에 임의 토큰을 지정하고 client에 같은 토큰을 설정한다. `.env`는 Git에 포함하지 않는다.

외부 MCP 연결은 HTTPS reverse proxy 또는 VPN으로 보호한 뒤 `/api/v1/mcp`를 지정한다. 대시보드 로그인 세션 쿠키는 MCP 인증에 쓰지 않는다. CSRF 검사는 MCP 경로에서만 제외하고 bearer 검증은 MCP controller에서 상수 시간 비교로 수행한다. assistant browser events와 job endpoint는 기존 OWNER 세션 및 CSRF 정책을 유지한다.

MCP 앱 동작은 현재 단일 OWNER 계정과 해당 계정의 개인 일정·노트 저장소를 사용한다. 대화 전 Codex App Server의 `personal-dashboard` 연결 상태를 조회하며, 연결이 없거나 준비되지 않으면 도우미 요청을 시작하지 않고 복구가 필요함을 표시한다. MCP navigation queue와 기본 난수 토큰은 서버 재시작 시 초기화된다. 외부 client에서 고정 bearer token을 운영하려면 `DASHBOARD_MCP_TOKEN`을 직접 제공한다.

서버 assistant 준비는 매 setup에서 `personal-dashboard` MCP 서버를 Codex CLI에 다시 등록해 URL과 bearer 환경변수 연결을 복구한다. 등록 실패는 setup job 실패로 전달한다. 갱신 전용 setup도 CLI 업데이트 확인 후 MCP 등록을 다시 맞춘다.

Codex 배포에는 `codex`와 같은 버전의 `codex-code-mode-host`가 모두 필요하다. CLI만 설치되면 MCP 목록 조회는 성공해도 모델의 도구 호출에서 `failed to spawn code-mode host`가 발생할 수 있다. setup은 공식 릴리스의 두 실행 파일을 각각 SHA256 검증해 설치하며, 이미 최신 CLI가 있어도 host 누락·버전 불일치를 복구한다. host를 복구하지 못하면 준비 완료로 처리하지 않는다. 기존 설치는 배포 후 도우미 앱을 새로 열 때 setup에서 복구한다.

모델 turn 실패는 성공한 job으로 처리하지 않는다. 인증 만료·갱신 토큰 오류는 재로그인 안내와 Codex 로그인 버튼으로 연결하며, MCP 연결 실패와 구분한다. upstream 인증 오류 원문은 사용자 응답에 복사하지 않는다.

연결 확인은 Codex 계정 로그인 확인 뒤 `codex-connections`로 수행한다. Python helper에서 Java DTO와 브라우저까지 `runtimeStatus`, `tools`, `error`를 전달한다. 스레드 없는 조회에서 runtimeStatus가 null/생략되어도 오류 없이 `list_calendar_events` 도구가 발견되면 연결된 것으로 판단한다. 명시적인 실패 상태·도구 누락·도구 조회 오류는 연결 복구 버튼을 표시한다. `toolsError`는 문자열로 처리하고 인증 정보가 포함될 수 있는 원문은 노출하지 않는다. 복구는 setup 한 번과 계정·연결 재조회로 수행한다.

## 연결 회귀 검증

질문을 실행하는 Codex 프로세스마다 대시보드 MCP 주소와 bearer 환경변수 연결을 적용한다. 새 대화와 기존 대화 재개 모두 대시보드 역할·도구 사용 지침과 현재 MCP 목록을 전달하며, 실제 threadId의 도구 목록을 확인한 뒤 turn을 시작한다. 등록 앱 질문은 `list_apps`, 일정 질문은 `list_calendar_events`를 사용한다. 연결된 MCP 질문은 전달된 조회 결과를 바탕으로 답한다. 이 검사는 창을 열 때 수행하는 연결 검사와 별도로 매 질문에 적용된다.

- `AssistantConnectionJsonTest`: 실제 Spring Jackson 설정으로 helper → Java DTO → HTTP 응답 변환 시 연결 정보 보존을 검사한다.
- `src/test/python/test_codex_bridge.py`: MCP 목록 pagination, null 상태, 문자열 오류를 검사한다.
- `src/test/python/test_codex_install.py`: 같은 버전 CLI의 host 복구, 버전 일치, SHA256 검증, 두 파일 설치, 이전 cache 갱신을 검사한다.
- `tools/launcher/assistant-test.cjs`: jsdom으로 정상 연결, 실패 시 전송 차단, 단일 setup 복구, 로그인 안내를 검사한다. 기존 UI 테스트와 같은 `jsdom` 환경에서 실행한다.
- `tools/deployment/check-mcp.py`: 새 Linux dashboard 이미지 안에서 임시 DB·홈·계정으로 서버를 띄우고, 실제 Codex로 도구 발견 → assistant HTTP job 응답 → 10월 일정 조회를 검사한다. 모델 turn은 실행하지 않는다. `/qa/codex`에 Linux Codex 바이너리, `/qa/studio`에 `src/main/resources/studio`, `/qa/check.py`에 이 스크립트를 읽기 전용 마운트하고 `--entrypoint python3 <image> /qa/check.py`로 실행한다. 운영 볼륨과 포트를 연결하지 않는다.

같은 스크립트의 `--live-turns`는 기존 HOME의 Codex 로그인으로 실제 모델 3회를 실행하는 명시적 검증 옵션이다. 별도 임시 DB와 계정에 fixture 앱·일정을 저장하고 앱 조회 → 동일 대화에서 MCP 목록 → 10월 일정 조회를 검사하며, 실제 `mcpToolCall`의 server/tool과 fixture가 응답에 포함됐는지 확인한다. `--port`, `--jar`, `--codex`, `--studio`로 검증 환경을 지정한다. 기존 MCP 설정 파일은 바꾸지 않으며 임시 서버를 종료하고 성공한 진단 대화는 보관 처리한다.

실제 대화 검증에 쓰는 Codex 실행 파일 옆에도 동일 버전 `codex-code-mode-host`가 있어야 한다. 인증 상태 조회 성공만으로 모델 실행 성공을 판정하지 않으며, 각 turn의 완료 상태와 실제 도구 호출·응답을 검사한다.

App Server 호출은 [공식 Codex App Server 문서](https://learn.chatgpt.com/docs/app-server)의 `mcpServerStatus/list` 및 `mcpServer/tool/call` 계약을 따른다.
