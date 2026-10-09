# HTTP 엔드포인트 목록

| Method | URL | Auth | 설명 |
| --- | --- | --- | --- |
| GET (WebSocket Upgrade) | `/ws/workspace` | OWNER 로그인 세션 + same-origin | 화면 변경 영역·소유 작업 알림 |

## 장비 Codex

| Method | URL | Auth | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/devices/{deviceId}/codex/jobs` | OWNER + CSRF | SSH 장비 Codex 작업 시작 |
| GET | `/api/v1/devices/{deviceId}/codex/jobs/{id}` | OWNER + 동일 세션 | 장비 작업 상태·이벤트 조회 |
| POST | `/api/v1/devices/{deviceId}/codex/jobs/{id}/inputs` | OWNER + 동일 세션 + CSRF | 승인·추가 지시·중지 입력 |
| DELETE | `/api/v1/devices/{deviceId}/codex/jobs/{id}` | OWNER + 동일 세션 + CSRF | 장비 작업 취소 |

## AI 비서 Workspace Memory

`GET/POST /api/v1/assistant/memories`, `GET/PUT/DELETE /api/v1/assistant/memories/{id}`, `POST /{id}/archive|restore|pin|supersede|promote/calendar|promote/note`, `DELETE /{id}/pin`, `POST /api/v1/assistant/memories/promotions/note`, `GET/PUT /api/v1/assistant/memories/preferences`. 모든 경로는 OWNER 전용이며 변경 요청에는 CSRF가 필요하다. 요청·응답과 오류는 [계약](specification.md#workspace-memory-api)을 따른다.

별도 표시가 없는 `/api/v1` 및 `/ws` 경로는 OWNER 인증이 필요하다. OWNER 세션 변경 API는 CSRF가 필요하다. MCP와 Telemetry ingestion만 각각 분리된 Bearer 인증을 사용한다.
예외: `/api/v1/telemetry/events`, `/gauges`, `/batch`는 service API Key Bearer 인증을 쓰며 dashboard session/CSRF 없이 수집한다. `/api/v1/telemetry/services/**`는 OWNER 전용이다.

| Method | URL | Auth | 설명 |
| --- | --- | --- | --- |
| GET | /login | public | 로그인 화면 |
| POST | /login | public + CSRF | 폼 로그인 |
| GET | / | OWNER | 대시보드 |
| POST | /logout | CSRF | 세션 종료 |
| GET | /health | public | 프로세스 liveness |
| GET | /api/v1/telemetry/services | OWNER | 서비스 요약 목록 |
| POST | /api/v1/telemetry/services | OWNER + CSRF | 서비스 생성, API Key는 응답에서 1회 노출 |
| GET | /api/v1/telemetry/services/{id} | OWNER | 서비스 설정 |
| DELETE | /api/v1/telemetry/services/{id} | OWNER + CSRF | 서비스 비활성화, 데이터 보존 |
| PUT | /api/v1/telemetry/services/{id}/enabled | OWNER + CSRF | 수집 활성화 상태 변경 |
| POST | /api/v1/telemetry/services/{id}/key | OWNER + CSRF | key 교체, 신규 원문 1회 노출 |
| DELETE | /api/v1/telemetry/services/{id}/key | OWNER + CSRF | key 폐기 |
| GET | /api/v1/telemetry/services/{id}/analytics?range=1h\|24h\|7d\|30d | OWNER | 기간별 집계와 분포 조회 |
| POST | /api/v1/telemetry/events | Service API Key | event 수집 |
| POST | /api/v1/telemetry/gauges | Service API Key | gauge sample 수집 |
| POST | /api/v1/telemetry/batch | Service API Key | 최대 100 events + 100 gauges 수집 |
| GET | /api/v1/calendar/events | OWNER | 날짜 범위의 일정 조회 |
| POST | /api/v1/calendar/events | OWNER | 일정 생성 |
| PUT | /api/v1/calendar/events/{id} | OWNER | 일정 수정 |
| DELETE | /api/v1/calendar/events/{id} | OWNER | 일정 삭제 |
| GET | /api/v1/timetables | OWNER | 학기 시간표 목록 |
| POST | /api/v1/timetables | OWNER | 학기 시간표 생성 |
| GET | /api/v1/timetables/{id} | OWNER | 수업과 학점 합계 조회 |
| PUT | /api/v1/timetables/{id} | OWNER | 학기 설정 수정 |
| DELETE | /api/v1/timetables/{id} | OWNER | 학기와 수업 삭제 |
| POST | /api/v1/timetables/{termId}/courses | OWNER | 수업 생성 |
| PUT | /api/v1/timetables/{termId}/courses/{id} | OWNER | 수업 수정 |
| DELETE | /api/v1/timetables/{termId}/courses/{id} | OWNER | 수업 삭제 |
| GET | /api/v1/workspace | OWNER | 작업 공간 전체 상태 |
| GET | /api/v1/github/status | OWNER | 서버 GitHub CLI 인증 상태 |
| GET | /api/v1/github/repositories | OWNER | 내 GitHub 저장소 목록 |
| GET | /api/v1/github/pull-requests?repository={owner/name} | OWNER | 저장소의 열린 PR 목록 |
| GET | /api/v1/github/issues?repository={owner/name} | OWNER | 저장소의 열린 이슈 목록 |
| GET | /api/v1/github/owners | OWNER | 사용자와 접근 가능한 Organization |
| GET | /api/v1/github/organizations | OWNER | 접근 가능한 Organization |
| GET | /api/v1/github/organizations/{owner} | OWNER | Organization 상세 |
| GET | /api/v1/github/organizations/{owner}/members | OWNER | Organization 회원 |
| GET | /api/v1/github/organizations/{owner}/teams | OWNER | Organization 팀 |
| GET | /api/v1/github/owners/{owner}/repositories | OWNER | Owner 저장소 |
| POST | /api/v1/github/owners/{owner}/repositories | OWNER + CSRF | Owner 저장소 생성 |
| GET | /api/v1/github/owners/{owner}/overview | OWNER | Owner 요약과 열린 작업 |
| GET | /api/v1/github/owners/{owner}/activity | OWNER | 보이는 최근 GitHub 이벤트 |
| GET | /api/v1/github/owners/{owner}/issues | OWNER | Owner 이슈 필터 조회 |
| GET | /api/v1/github/owners/{owner}/pull-requests | OWNER | Owner PR 필터 조회 |
| GET | /api/v1/github/owners/{owner}/my-work | OWNER | 내 담당 이슈와 리뷰 요청 |
| GET | /api/v1/github/owners/{owner}/search | OWNER | Owner 이슈·PR 검색 |
| GET | /api/v1/github/repositories/detail | OWNER | 저장소 상세 |
| PATCH | /api/v1/github/repositories | OWNER + CSRF | 저장소 설명·홈페이지 수정 |
| DELETE | /api/v1/github/repositories | OWNER + CSRF | 승인된 저장소 영구 삭제 |
| GET | /api/v1/github/repositories/branches | OWNER | Branch 목록 |
| GET | /api/v1/github/repositories/tags | OWNER | Tag 목록 |
| GET | /api/v1/github/repositories/contributors | OWNER | Contributor 목록 |
| GET | /api/v1/github/repositories/languages | OWNER | 언어별 코드 크기 |
| GET | /api/v1/github/repositories/tree | OWNER | 저장소 파일 트리 |
| GET | /api/v1/github/repositories/file | OWNER | 파일 내용 |
| GET | /api/v1/github/repositories/commits | OWNER | 최근 커밋 |
| GET | /api/v1/github/repositories/commits/{sha} | OWNER | 커밋 상세 |
| GET | /api/v1/github/repositories/commits/{sha}/files | OWNER | 커밋 변경 파일 |
| GET | /api/v1/github/repositories/context | OWNER | 저장소 개발 컨텍스트 |
| GET | /api/v1/github/issues/detail | OWNER | 이슈 상세 |
| POST | /api/v1/github/issues | OWNER + CSRF | 이슈 생성 |
| PATCH | /api/v1/github/issues/{number} | OWNER + CSRF | 이슈 제목·본문·상태·담당자 등 수정 |
| POST | /api/v1/github/issues/{number}/comments | OWNER + CSRF | 이슈 댓글 |
| GET | /api/v1/github/pull-requests/detail | OWNER | PR 상세 |
| GET | /api/v1/github/pull-requests/files | OWNER | PR 변경 파일 |
| GET | /api/v1/github/pull-requests/context | OWNER | PR 대화·커밋·검사 컨텍스트 |
| POST | /api/v1/github/pull-requests | OWNER + CSRF | PR 생성 |
| PATCH | /api/v1/github/pull-requests/{number} | OWNER + CSRF | PR 제목·본문·상태·base 수정 |
| POST | /api/v1/github/pull-requests/{number}/reviews | OWNER + CSRF | PR 리뷰 |
| GET | /api/v1/github/actions/workflows | OWNER | Workflow 목록 |
| GET | /api/v1/github/actions/runs | OWNER | Workflow 실행 목록 |
| GET | /api/v1/github/actions/runs/{runId} | OWNER | 실행 상세 |
| GET | /api/v1/github/actions/runs/{runId}/jobs | OWNER | Job·Step 목록 |
| GET | /api/v1/github/actions/runs/{runId}/artifacts | OWNER | Artifact metadata |
| GET | /api/v1/github/actions/runs/{runId}/logs | OWNER | 실패 Step 로그 |
| GET | /api/v1/github/actions/runs/{runId}/analysis | OWNER | 실패 분석 데이터 |
| POST | /api/v1/github/actions/runs/{runId}/rerun | OWNER + CSRF | Workflow 재실행 |
| POST | /api/v1/github/actions/runs/{runId}/cancel | OWNER + CSRF | Workflow 실행 취소 요청 |
| POST | /api/v1/github/actions/workflows/{workflowId}/dispatches | OWNER + CSRF | Workflow 수동 실행 |
| GET | /api/v1/github/releases | OWNER | Release 목록 |
| GET | /api/v1/github/releases/{releaseId} | OWNER | Release 상세와 asset metadata |
| PATCH | /api/v1/github/releases/{releaseId} | OWNER + CSRF | Release 태그·이름·설명 수정 |
| DELETE | /api/v1/github/releases/{releaseId} | OWNER + CSRF | 승인된 Release 삭제 |
| POST | /api/v1/github/releases | OWNER + CSRF | Release 생성 |
| GET | /api/v1/github/approvals | OWNER | GitHub 위험 작업 승인 대기 |
| POST | /api/v1/github/approvals/{id} | OWNER + CSRF | 일회성 병합 승인 |
| GET | /api/v1/search | OWNER | 장비·앱·즐겨찾기 검색 |
| POST | /api/v1/devices | OWNER | 장비 생성(최대 5단계 점프 프록시 설정 포함) |
| POST | /api/v1/devices/ssh | OWNER + CSRF | SSH 명령과 비밀번호로 검증 후 장비 생성/갱신(점프 프록시 포함) |
| PUT | /api/v1/devices/{id} | OWNER | 장비 수정 |
| DELETE | /api/v1/devices/{id} | OWNER | 장비 삭제 |
| GET | /api/v1/devices/{id}/status | OWNER | 실제 상태 측정 |
| GET | /api/v1/devices/{id}/docker | OWNER | Docker 목록 |
| POST | /api/v1/devices/{id}/docker | OWNER | 컨테이너 제어 |
| GET | /api/v1/devices/{id}/gpu | OWNER | GPU 조회 |
| POST | /api/v1/devices/{id}/wake | OWNER | Wake 전송 |
| POST | /api/v1/applications | OWNER | 앱 생성 |
| PUT | /api/v1/applications/{id} | OWNER | 앱 수정 |
| DELETE | /api/v1/applications/{id} | OWNER | 앱 삭제 |
| POST | /api/v1/clips | OWNER | 임시 텍스트 저장 |
| DELETE | /api/v1/clips/{id} | OWNER | 텍스트 삭제 |
| POST | /api/v1/bookmarks | OWNER | 경로 즐겨찾기 생성 |
| DELETE | /api/v1/bookmarks/{id} | OWNER | 즐겨찾기 삭제 |
| PUT | /api/v1/preferences | OWNER | 화면 설정 저장 |
| PUT | /api/v1/browser-settings | OWNER | 브라우저 실행 위치 저장 |
| PUT | /api/v1/tabs | OWNER | 탭 배치 저장 |
| GET | /api/v1/devices/{device}/files | OWNER | 파일 목록 |
| POST | /api/v1/devices/{device}/files | OWNER | 파일 업로드 |
| POST | /api/v1/devices/{device}/files/folders | OWNER | 폴더 생성 |
| PATCH | /api/v1/devices/{device}/files | OWNER | 이름 변경 |
| DELETE | /api/v1/devices/{device}/files | OWNER | 파일/빈 폴더 삭제 |
| GET | /api/v1/devices/{device}/files/content | OWNER | 파일 다운로드 |
| POST | /api/v1/sessions | OWNER | 실행 세션 생성 |
| GET | /api/v1/sessions | OWNER + 생성한 로그인 세션 | 현재 프로젝트의 유지 중인 Studio 터미널 목록 |
| DELETE | /api/v1/sessions/{id} | OWNER | 실행 세션 종료 |
| GET (WS Upgrade) | /ws/runtime/{id} | OWNER + same origin | 터미널/원격 스트림 |

`/css/**`는 공개 정적 파일이다. JS/vendor는 인증 후 제공한다. Spring Boot의 내부 오류 디스패치는 기능 API가 아니다.

## SSH 코드 에디터

POST /api/v1/studio/jobs, GET/DELETE /api/v1/studio/jobs/{id}: 세션 소유 로컬/SSH 파일·Git·Codex 작업. [계약](studio.md).

| POST | /api/v1/studio/jobs/{id}/inputs | OWNER + 작업 소유 세션 + CSRF | Codex 승인·답변·추가 지시·중지 |
| POST | /api/v1/assistant/jobs | OWNER + CSRF | 서버 Codex assistant job 시작 |
| GET | /api/v1/assistant/jobs/{id} | OWNER + 작업 소유 세션 | assistant 상태·이벤트·결과 조회 |
| POST | /api/v1/assistant/jobs/{id}/inputs | OWNER + 작업 소유 세션 + CSRF | assistant 승인·답변·추가 지시·중지 |
| DELETE | /api/v1/assistant/jobs/{id} | OWNER + 작업 소유 세션 + CSRF | assistant job 취소 |
| GET | /api/v1/assistant/events | OWNER | Codex가 요청한 브라우저 페이지 이동 확인 |
| GET | /api/v1/assistant/service-drafts/thread/{threadId} | OWNER | 대화의 최신 Service Draft 조회 |
| GET | /api/v1/assistant/service-drafts/thread/{threadId}/resources | OWNER | 대화에서 탐색한 리소스 후보 조회 |
| GET | /api/v1/assistant/service-drafts/{id} | OWNER | Service Draft 조회 |
| PUT | /api/v1/assistant/service-drafts/{id} | OWNER + CSRF | Service Draft 수정 |
| POST | /api/v1/assistant/service-drafts/{id}/approve | OWNER + CSRF | 현재 Draft revision 승인 |
| POST | /api/v1/assistant/service-drafts/{id}/commit | OWNER + CSRF | 승인한 Draft를 카탈로그에 반영 |
| DELETE | /api/v1/assistant/service-drafts/{id} | OWNER + CSRF | Service Draft 취소 |
| POST | /api/v1/mcp | Bearer token | Streamable HTTP MCP 초기화와 도구 호출 |

| GET | /api/v1/tailscale | OWNER | 서버 Tailscale 연결 상태 조회 |
| POST | /api/v1/tailscale/login | OWNER + CSRF | 서버 Tailscale 인증 링크 발급 시작 |
| DELETE | /api/v1/tailscale/login | OWNER + CSRF | 서버 Tailscale 로그아웃 |

## 클라우드 저장소

| Method | URL | Auth | 설명 |
| --- | --- | --- | --- |
| GET | /api/v1/cloud | OWNER | 디렉토리 목록 및 하위 검색 |
| GET | /api/v1/cloud/info | OWNER | 파일·폴더 메타데이터 |
| POST | /api/v1/cloud/entries | OWNER + CSRF | 파일·폴더 생성 |
| DELETE | /api/v1/cloud/entries | OWNER + CSRF | 휴지통으로 이동 |
| POST | /api/v1/cloud/uploads | OWNER + CSRF | 단일 파일 업로드 |
| POST | /api/v1/cloud/transfers | OWNER + CSRF | 파일·폴더 복사·이동·이름 변경 |
| GET | /api/v1/cloud/content | OWNER | 파일 또는 폴더 ZIP 다운로드 |
| GET | /api/v1/cloud/archive | OWNER | 다중 선택 ZIP 다운로드 |
| GET | /api/v1/cloud/preview | OWNER | UTF-8 텍스트 미리보기 |
| PUT | /api/v1/cloud/text | OWNER + CSRF | revision 검증 후 텍스트 저장 |
| GET | /api/v1/cloud/trash | OWNER | 휴지통 목록 |
| POST | /api/v1/cloud/trash/{id}/restoration | OWNER + CSRF | 원래 위치로 복원 |
| DELETE | /api/v1/cloud/trash/{id} | OWNER + CSRF | 영구 삭제 |

## NAS

- GET /api/v1/cloud/nas: OWNER 세션으로 연결 설정 조회.

## 원격 자동 구성

- POST /api/v1/devices/{id}/remote-setup — OWNER 세션·CSRF, 비동기 자동 구성 및 연결 검증 시작.
- GET /api/v1/devices/{id}/remote-setup — OWNER 세션, 구성 상태 조회.

## Database Studio

| Method | URL | Auth | 설명 |
| --- | --- | --- | --- |
| GET/POST | /api/v1/databases | OWNER / OWNER + CSRF | 연결 목록·생성 |
| GET/PUT/DELETE | /api/v1/databases/{id} | OWNER / OWNER + CSRF | 연결 조회·수정·삭제 |
| POST | /api/v1/databases/{id}/test | OWNER + CSRF | 연결 테스트 |
| POST | /api/v1/databases/test | OWNER + CSRF | 저장 전 연결 설정 테스트 |
| GET | /api/v1/databases/devices/{deviceId}/containers | OWNER | 등록 장비의 실행 중 Docker 컨테이너 목록 |
| GET | /api/v1/databases/{id}/schemas | OWNER | schema 목록 |
| GET | /api/v1/databases/{id}/tables | OWNER | table/view 목록 |
| GET | /api/v1/databases/{id}/functions | OWNER | function 목록 |
| GET | /api/v1/databases/{id}/tables/{table} | OWNER | 컬럼·키·인덱스 |
| GET | /api/v1/databases/{id}/tables/{table}/rows | OWNER | 페이징 데이터 |
| POST | /api/v1/databases/{id}/query | OWNER + CSRF | SQL 실행 시작 |
| GET | /api/v1/databases/{id}/query/{executionId} | OWNER | 실행 결과 polling |
| POST | /api/v1/databases/{id}/query/{executionId}/cancel | OWNER + CSRF | 실행 취소 |
| GET | /api/v1/databases/history | OWNER | Query History |
| GET/POST | /api/v1/databases/{id}/favorites | OWNER / OWNER + CSRF | 즐겨찾기 목록·생성 |
| DELETE | /api/v1/databases/{id}/favorites/{favoriteId} | OWNER + CSRF | 즐겨찾기 삭제 |

## Service Catalog

| Method | URL | Auth | 설명 |
| --- | --- | --- | --- |
| GET | /api/v1/services | OWNER | 서비스 목록 |
| POST | /api/v1/services | OWNER + CSRF | 서비스 생성 |
| GET | /api/v1/services/{id} | OWNER | 서비스 조회 |
| PUT | /api/v1/services/{id} | OWNER + CSRF | 서비스 수정 |
| DELETE | /api/v1/services/{id} | OWNER + CSRF | 서비스와 연결·활동 삭제 |
| GET | /api/v1/services/{id}/resources | OWNER | 리소스 연결 목록 |
| POST | /api/v1/services/{id}/resources | OWNER + CSRF | 기존 리소스 연결 |
| DELETE | /api/v1/services/{id}/resources/{resourceId} | OWNER + CSRF | 연결 해제 |
| GET | /api/v1/services/{id}/health | OWNER | 연결된 신호의 집계 상태 |
| GET | /api/v1/services/{id}/context | OWNER | 공통 Service Context |
| GET | /api/v1/services/{id}/activity | OWNER | 서비스 활동 |
| GET | /api/v1/services/{id}/runtime | OWNER | 연결된 장비·컨테이너의 실시간 상태 |
| GET | /api/v1/services/{id}/resources/{resourceId}/logs | OWNER | 연결된 컨테이너의 최근 로그 |
| GET | /api/v1/services/{id}/resources/{resourceId}/log-history | OWNER | 기간별 컨테이너 로그와 오류 후보·조회 범위 |
| POST | /api/v1/services/{id}/resources/{resourceId}/actions | OWNER + CSRF | 연결된 컨테이너 시작·중지·재시작 |

## Studio 프로젝트 도구

| Method | URL | Auth | 설명 |
| --- | --- | --- | --- |
| POST | /api/v1/studio/jobs | OWNER + CSRF | 기존 작업 API에 run-commands/start/list/logs/stop/restart/delete 및 ports 추가 |
| POST | /api/v1/studio/browser | OWNER + CSRF | 프로젝트 Chromium 탭 open/snapshot/back/forward/reload/click/text/key/scroll/close |
| POST | /api/v1/studio/api/send | OWNER + CSRF | 대상 장비에서 HTTP 요청 실행·기록 암호화 저장 |
| POST | /api/v1/studio/api/history | OWNER + CSRF | 프로젝트 요청 기록의 비밀값 없는 개요 |
| POST | /api/v1/studio/api/replay | OWNER + CSRF | 암호화된 기록을 읽어 실제 요청 재전송 |
| POST | /api/v1/studio/api/environment/read | OWNER + CSRF | 프로젝트 환경변수 이름만 조회 |
| POST | /api/v1/studio/api/environment | OWNER + CSRF | 환경변수 병합 저장, null 값은 삭제 |

Terminal은 기존 `POST /api/v1/sessions`에 선택 필드 `root`를 전달한다. 상세 계약은 `specification.md`와 `../studio-workbench.md`를 참조한다.

## 메모장

| Method | URL | Auth | 설명 |
| --- | --- | --- | --- |
| GET | /api/v1/notes | OWNER | 폴더/문서 메타데이터 전체 목록 |
| POST | /api/v1/notes | OWNER + CSRF | 폴더 또는 문서 생성 |
| GET | /api/v1/notes/{id} | OWNER | 단일 항목과 블록 본문 조회 |
| PUT | /api/v1/notes/{id} | OWNER + CSRF | 이름/아이콘/상위 폴더 변경 |
| PUT | /api/v1/notes/{id}/content | OWNER + CSRF | 버전을 검사하여 블록 본문 저장 |
| DELETE | /api/v1/notes/{id} | OWNER + CSRF | 문서 또는 빈 폴더 영구 삭제 |
| POST | /api/v1/notes/{id}/images | OWNER + CSRF | 문서 이미지 첨부 |
| GET | /api/v1/notes/images/{id} | OWNER | 첨부 이미지 읽기 |

## 병역 캘린더

| Method | URL | Auth | 설명 |
| --- | --- | --- | --- |
| GET | /api/v1/military | OWNER | 복무 현황·일정·집계 조회 |
| PUT | /api/v1/military/profile | OWNER + CSRF | 개인 복무 정보 생성·전체 수정 |
| DELETE | /api/v1/military/profile | OWNER + CSRF | 복무 정보와 병역 일정 삭제 |
| POST | /api/v1/military/events | OWNER + CSRF | 병역 일정 생성 |
| PUT | /api/v1/military/events/{id} | OWNER + CSRF | 병역 일정 전체 수정 |
| DELETE | /api/v1/military/events/{id} | OWNER + CSRF | 병역 일정 삭제 |
