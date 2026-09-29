# GitHub Control Center와 MCP Control Plane

## 구성

```text
브라우저 GitHub 화면 ── REST ──┐
                                ├── GithubService ── GithubCliAdapter ── gh CLI/API ── GitHub
Codex ── Dashboard MCP ────────┘
```

`GithubService`가 입력 검증, 로그인 확인, 응답 변환, 읽기/쓰기 유스케이스를 소유한다. REST의 `GithubController`와 MCP의 `AssistantMcpService`는 같은 서비스를 호출한다. `GithubCliAdapter`만 서버 계정의 `gh`를 실행하며 임의 명령 MCP 도구는 없다. 기존 `gh repo list`, `gh issue list`, `gh pr list` 경로는 유지했다. 세부 데이터는 `gh api`로 GitHub REST API를 호출한다. 실패 시 CLI 원문을 반환하지 않으며 30초와 출력 1 MiB 제한을 적용한다. 이슈·PR 본문은 CLI 인자가 아닌 stdin JSON으로 전달한다. 실패 단계 로그는 `gh run view --log-failed`의 제한된 텍스트를 반환한다.

대시보드 GitHub 인증은 `GitHub 로그인` → 서버 `gh auth login --hostname github.com --git-protocol https --web` 기기 코드 승인으로 수행한다. 브라우저 로그인 세션, IDE에서 선택한 SSH 장비의 GitHub 로그인, Codex의 별도 shell `gh` 인증은 서로 다르다. Dashboard MCP는 서버 `gh` 계정과 Dashboard 서비스의 의미·권한 경계를 사용한다. GitHub 토큰을 SQLite나 응답에 복사하지 않는다.

## Owner와 화면

`GET user`로 현재 USER를, `GET user/orgs`로 해당 자격증명에 표시되는 ORGANIZATION을 조회한다. Owner를 바꾸면 저장소 선택과 화면 상태가 초기화된다. Organization 회원·팀은 GitHub 계정의 권한이 허용하는 범위만 조회한다. Overview는 저장소와 Search API의 첫 100개 열린 이슈/PR, 조회 가능한 회원의 첫 100개와 최근 이벤트 30개를 조합한다. 표시 개수는 전체 합계가 아니므로 대규모 조직의 정확한 총계로 사용하지 않는다. 회원·이벤트 조회 권한이 없거나 외부 실패가 있으면 해당 부분을 생략한다. Organization Activity는 GitHub의 공개 조직 이벤트만 포함하며 비공개 저장소 활동은 포함하지 않는다. GitHub 이벤트 API는 실시간 지표가 아니며 반영이 지연될 수 있다. GitHub Search와 저장소 목록은 각각 페이지 상한이 있고 추가 페이지 조회는 아직 제공하지 않는다.

화면은 대시보드의 테마 변수를 사용하며 Owner, 저장소 검색·정렬·공개 여부/Archive/Fork 필터, Overview, 이슈, PR, Actions, Release, 파일 트리, 커밋, 회원을 보여준다. 이슈는 상태·담당/작성/멘션·label, PR은 상태를 Owner 전체 또는 선택 저장소에서 필터링한다. 좁은 화면은 Owner와 탐색을 세로로 재배치한다. 이슈, PR, Workflow, 파일, 커밋은 대시보드 내부 상세 패널에서 읽을 수 있고 원본 GitHub 링크도 제공한다. PR 상세는 변경 파일, 대화, 리뷰 댓글과 Check 상태를 조합한다. 선택한 저장소에서 이슈/PR 생성, 닫기/재열기, 이슈 댓글, PR 리뷰, Workflow 재실행/취소와 수동 실행을 시작할 수 있다. Actions에는 실패 실행만 표시하는 필터가 있다. 나머지 쓰기 기능은 REST/MCP 계약을 제공한다.

## MCP 도구 및 권한

`POST /api/v1/mcp`의 기존 Bearer 인증과 Streamable HTTP 계약을 재사용한다. `github.*` 도구는 구조화된 JSON schema 입력과 `structuredContent` 출력을 제공한다. 레거시 `github_status`, `list_github_repositories`, `list_github_issues`, `list_github_pull_requests`도 유지한다. 전체 도구와 필드는 [API 명세](api/specification.md)에 있다.

| 등급 | 도구 |
| --- | --- |
| READ | Owner·Organization·저장소·이슈·PR·커밋·파일·Workflow·Release 조회, 조직 검색, 내 작업, Overview, 개발 컨텍스트, 실패 Workflow 분석 |
| WRITE | 저장소 생성/설명·토픽 수정, 이슈 생성/수정/댓글, PR 생성/수정/리뷰, Workflow 재실행/취소/수동 실행, Release 생성·태그/설명 수정, 위험 작업 승인 요청 |
| DANGEROUS | PR 병합, 저장소 Archive·삭제, Release 삭제 |

병합·Archive·저장소 삭제·Release 삭제는 각 `github.request_*` 도구로 10분 유효 UUID를 만든다. 사용자가 대시보드 GitHub 화면의 **GitHub 승인 대기**에서 정확한 대상과 작업을 보고 승인해야 한다. 삭제 승인에는 브라우저 재확인도 요구한다. 승인 후 실행 도구가 같은 대상·작업·UUID를 요구한다. 승인은 일회성이며 외부 호출 전에 소비된다. 만료나 불일치 시 403이다. Release 삭제는 Git 태그를 남기며 Release `tag_name` 수정도 Git 태그 자체를 바꾸지 않는다. 가시성 변경과 브랜치 삭제는 도구로 노출하지 않는다.

## 새 기능 추가

1. GitHub 공식 API 또는 `gh` 명령의 응답·권한 계약을 확인한다.
2. `GithubCliAdapter`에 고정된 목적의 호출을 추가하고 입력을 `GithubService`에서 검증한다. 토큰·본문을 CLI 인자로 전달하지 않는다.
3. `GithubDto`에 명시적 응답/요청 타입을 만들고 `GithubService`에 유스케이스를 추가한다.
4. 필요한 REST와 MCP entrypoint를 같은 서비스 메서드에 연결한다. 위험 작업이면 `GithubApprovalService`와 브라우저 승인을 거친다.
5. `docs/api/endpoints.md`, `docs/api/specification.md`, 이 문서와 의미 있는 테스트를 함께 갱신한다.

## 현재 범위와 검증

지원 범위는 [API 명세](api/specification.md)의 실제 endpoint와 tool이 기준이다. Release asset metadata와 다운로드 링크, Workflow artifact metadata를 조회할 수 있다. Workflow artifact 다운로드, Release asset 업로드, Repository 가시성 변경과 기타 설정, Projects는 아직 구현되지 않았다. Organization 이벤트는 공개 이벤트만 제공한다.

개발 환경의 로컬 `gh` 인증이 만료되어 실제 GitHub 계정 조회는 확인되지 않았다. 서버 배포 후 `GitHub 로그인`으로 인증을 복구하고 USER 저장소, Organization, 해당 Organization 저장소, Issue, PR, Actions 및 MCP 필수 도구를 순서대로 호출해야 한다. 로컬 테스트와 컴파일은 외부 계정의 권한이나 실제 GitHub 응답을 증명하지 않는다.
