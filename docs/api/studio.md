# Server editor job API

모든 경로는 `/api/v1/studio/jobs` 아래이며 OWNER 로그인과 변경 요청의 CSRF가 필요하다. 작업은 생성한 HTTP 로그인 세션만 조회/취소할 수 있다. 외부 CLI 결과나 사용자 입력을 대시보드 로그에 남기지 않는다.

| Method | Path | Result |
| --- | --- | --- |
| POST | `/api/v1/studio/jobs` | 202 JobView; 원격 작업 시작 |
| GET | `/api/v1/studio/jobs/{id}` | 200 JobView; 상태/최근 이벤트/결과 |
| DELETE | `/api/v1/studio/jobs/{id}` | 204; 멱등 취소, 완료된 변경 유지 |

요청: `{deviceId, root, action, args}`. deviceId는 서버 자체를 뜻하는 `local` 또는 등록 SSH 장비 ID다. root는 해당 장비의 파일 루트 안에 있는 절대 작업 폴더다. `local`은 Linux 대시보드 프로세스에서 SSH 없이 실행하고 Docker 기본 루트는 `/app/data/files`다. args는 아래 명시된 필드만 사용한다. 길이 제한: 경로 4096, revision 64, content 1048576자(원격 UTF-8 기준 추가 1 MiB 제한), message 4000, branch/name/email 200, url 2048, prompt 32000, model 100, mode 30.

| action | args | result |
| --- | --- | --- |
| setup | 없음 | git, codex 버전 |
| list | path (기본 `.`) | root, path, entries: [{name,path,directory}] |
| read | path | content, revision |
| save | path, content, revision | revision |
| create / mkdir / delete | path | ok |
| rename | path, target | ok |
| git-init | 없음 | ok |
| git-clone | url, target | path (복제한 절대 폴더) |
| git-status | 없음 | branch, branches, changes: [{index,worktree,path,oldPath}], history |
| git-stage / git-unstage | path | ok |
| git-diff | path, staged?, untracked? | diff |
| git-commit | message | ok |
| git-identity | name, email | ok |
| git-remote | url | ok (origin 추가/변경) |
| git-branch / git-switch | branch | ok |
| git-fetch / git-pull / git-push | 없음 | ok |
| github-login / github-status | 없음 | authenticated |
| codex-login / codex-logout | 없음 | authenticated |
| codex-status | 없음 | authenticated, version |
| codex-run | prompt, threadId?, model?, effort?, mode?, context? | assistant (아래 App Server 계약) |

JobView: `{id,action,state,events,result,error,errorStatus}`. state는 RUNNING → SUCCEEDED/FAILED/CANCELLED. result는 성공 시 위 표의 필드만, 실행 중/실패는 null. events는 `{event,text?,state?,url?,code?}`의 최근 목록이다. 이벤트 URL/코드는 의도적으로 시작한 기기 코드 인증에만 제공한다. 비밀번호/API 키/token 필드는 없다. Codex 로그인 상태는 CLI 캐시 존재 확인이며 유료 모델 호출을 통한 인증 검증이 아니다.

HTTP 오류: 익명401, 권한/CSRF403, 미존재/다른 세션 작업404, 잘못된 요청/미지원 action400, 동시 실행 제한429. 작업이 접수된 이후 원격 실패는 HTTP200 JobView의 FAILED/errorStatus로 전달한다. 원격 오류는 경로403, 미존재404, 충돌/CLI 실패409, 크기413, 텍스트 형식415, 설치/접속502, 로컬 Linux 미지원400 등이다. 취소는 CANCELLED로 표시하고 원격 프로세스를 종료한다. 자동 재시도는 없다.

## Codex App Server

기존 codex-run 결과의 ok 대신 result.assistant에 thread, status, turnId를 반환한다. 세션에는 id/name/preview/cwd/createdAt/updatedAt/status/turns가 있으며 turns는 최근 50개, 각 turn은 id/status/items/error다. item은 id/type/text/status/command/output/files(path,diff,kind)로 투영한다. 숨겨진 추론 content, 인증 토큰과 원본 CLI 설정은 반환하지 않는다.

| action | args | result.assistant |
| --- | --- | --- |
| codex-models | 없음 | models: id/name/description/defaultModel/defaultEffort/efforts/inputModalities |
| codex-threads | query?(200자), cursor?(2000자), archived? | threads, nextCursor. 선택한 cwd만, 25개씩 |
| codex-thread-new | model?, mode? | id가 빈 draft thread. 첫 turn 전에는 영속 저장되지 않음 |
| codex-thread-read | threadId | thread |
| codex-thread-rename | threadId, name(200자) | 기존 result.ok |
| codex-thread-archive / unarchive | threadId | 기존 result.ok |
| codex-thread-fork | threadId | thread |
| codex-thread-compact | threadId | 기존 result.ok, 압축 완료까지 RUNNING |
| codex-thread-rollback | threadId | thread. 마지막 1 turn 기록 제거, 파일 복구 없음 |
| codex-run | prompt, threadId?, model?, effort?, mode?, context? | thread, status, turnId |
| codex-review | threadId?, model?, mode? | 미커밋 변경 리뷰 후 thread/status/turnId |
| codex-account | 없음 | authenticated, plan (계정 주소/토큰 제외) |
| codex-skills | 없음 | skills: name/description/path/enabled |
| codex-connections | 없음 | connections: name/status (CLI 등록 MCP 인증 상태) |

context는 최대 16개 {kind:file|selection|image|skill,path?,name?,content?,fromLine?,toLine?,dataUrl?}. file/selection 경로는 프로젝트 안에서만 허용한다. 파일은 서버에서 읽고 selection은 사용자가 선택한 UTF-8 스냅샷을 받는다. 텍스트 합계 128000자, selection당 32000자, image는 PNG/JPEG/WebP data URL 최대 3000000자. 요청 컨텍스트 content/dataUrl 전체는 4000000자 이하. skill은 CLI skills/list의 활성 항목과 이름·경로가 일치해야 한다.

실행 이벤트에는 assistant {sequence,kind,threadId?,turnId?,item?,usage?,interaction?,requestId?,text?}가 추가된다. sequence는 job별 단조 증가하며 UI는 중복을 제거하고 item ID로 최신 내용을 갱신한다. kind는 started/item/interaction/answered/usage/plan/diff/notice/completed. usage는 누적 토큰과 마지막 컨텍스트 사용량·윈도 크기다.

POST /api/v1/studio/jobs/{id}/inputs body:
- 승인: {type:approval,requestId,decision:accept|acceptForSession|decline|cancel}
- 질문 답변: {type:answer,requestId,answers:{questionId:[문자열]}} (질문 ID 집합 일치 필요)
- 추가 지시: {type:steer,text} (최대 32000자)
- 정상 중단: {type:interrupt}

codex-run/review/thread-compact의 실행 중 job에만 입력할 수 있다. 보류 중 요청 ID/종류를 remote adapter가 재검증한다. 지원하지 않는 MCP elicitation/권한 요청은 명시적으로 거절한다. DELETE 작업은 강제 종료 경로이며 이미 적용한 파일 변경은 유지한다.


## 장비 로그 작업

기존 POST/GET/DELETE `/api/v1/studio/jobs[/{id}]` 계약과 OWNER/CSRF/HTTP 세션 소유권을 유지한다. endpoint 추가는 없다. Request.action에 `logs-targets`, `logs-follow`를 추가한다. deviceId 및 root는 기존 필수 문자열이며 root는 해당 장비 rootPath 내 존재하는 디렉토리여야 한다. Args.mode는 두 액션에서 필수 문자열 enum `docker`/`tmux` (null/생략 불가), Args.target은 logs-follow에서 필수 문자열(Docker: 소문자 hex 64자리 전체 컨테이너 ID, tmux: `%`+숫자 pane ID), logs-targets에서는 사용하지 않는다. 나머지 기존 Args 필드는 사용하지 않는다.

Result.logTargets는 선택/null 생략 가능한 LogTarget[]이며 logs-targets 성공 시 필수(빈 배열 가능). LogTarget의 id/name/status는 모두 필수 non-null 문자열이며 각각 선택 대상 ID/표시 이름/컨테이너 상태 또는 pane 명령 이름이다. logs-follow 정상 종료 결과는 기존 ok=true이다.

Event.sequence는 선택 Long(null이면 JSON 생략), 로그 이벤트에서 1부터 증가한다. event=`log-append`는 Docker text를 이어붙이고, `log-snapshot`은 tmux text로 전체 화면을 교체한다. text는 문자열(빈 문자열 가능), 나머지 이벤트 필드는 생략한다. 재조회 시 sequence로 중복을 제거한다. 보유 창보다 수신이 느리면 sequence가 건너뛸 수 있다.

작업 생성은 202; 기존 요청 바인딩 오류 400, 인증/CSRF 오류 401/403, 작업 미존재/다른 세션 404, 실행 한도 초과 429 계약을 유지한다. 비동기 실패는 JobView.state=FAILED 및 error/errorStatus로 전달한다: source/target 오류 400, CLI 누락 또는 사라진 pane 409, 목록/화면 2MiB 초과 413, 목록/캡처 10초 초과 504, Docker 데몬/로그 접근 및 tmux 소켓 실패 502. tmux 기본 서버/세션이 없으면 빈 목록이다. DELETE는 읽기 프로세스만 취소하며 대상 컨테이너/pane은 유지한다. 로그 작업의 inputs 전송은 지원하지 않는다.

운영·갱신·보유 제한은 [장비 로그](../device-logs.md)를 따른다.
