# GitHub 앱

GitHub 앱은 대시보드 서버 계정의 GitHub CLI(`gh`)를 사용한다. `GitHub 로그인`을 누르면 서버에서 검증된 CLI를 설치하고 `gh auth login --hostname github.com --git-protocol https --web`을 시작한다. 화면에 표시되는 일회용 코드를 사용자의 브라우저에서 승인한다. 완료 후 CLI가 서버 계정 홈에 인증을 저장하고 Git credential helper를 설정한다. 코드 에디터에서 SSH 장비를 선택해 로그인한 계정과는 별개다.

인증 후 내 소유 저장소 최대 50개, 선택한 `owner/name` 저장소의 열린 PR과 이슈 각각 최대 50개를 조회한다. 각 항목은 GitHub 페이지를 새 탭에서 연다. 상태 및 목록 조회는 고정된 `gh` 인자와 JSON 필드만 사용하며 최대 30초, 출력 1 MiB 제한을 둔다. 로그인 취소는 기존 Studio job 취소를 사용한다. 인증 누락은 409, 잘못된 저장소 형식은 400, CLI/네트워크 실패는 502 또는 503, 시간 초과는 504로 응답한다. CLI 오류 원문과 인증 정보는 응답하지 않는다.

도우미의 `personal-dashboard` MCP는 `github_status`, `list_github_repositories`, `list_github_pull_requests`, `list_github_issues`를 제공한다. 이 도구도 같은 서버 `gh` 계정을 사용한다. 쓰기 기능이나 임의 명령 실행은 제공하지 않는다.
