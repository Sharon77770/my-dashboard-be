# HTTP 엔드포인트 목록

모든 `/api/v1` 및 `/ws` 경로는 OWNER 인증이 필요하다. 상태 변경 API는 CSRF가 필요하다.

| Method | URL | Auth | 설명 |
| --- | --- | --- | --- |
| GET | /login | public | 로그인 화면 |
| POST | /login | public + CSRF | 폼 로그인 |
| GET | / | OWNER | 대시보드 |
| POST | /logout | CSRF | 세션 종료 |
| GET | /health | public | 프로세스 liveness |
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
| GET | /api/v1/search | OWNER | 장비·앱·즐겨찾기 검색 |
| POST | /api/v1/devices | OWNER | 장비 생성 |
| POST | /api/v1/devices/ssh | OWNER + CSRF | SSH 명령과 비밀번호로 검증 후 장비 생성/갱신 |
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
| DELETE | /api/v1/sessions/{id} | OWNER | 실행 세션 종료 |
| GET (WS Upgrade) | /ws/runtime/{id} | OWNER + same origin | 터미널/원격 스트림 |

`/css/**`는 공개 정적 파일이다. JS/vendor는 인증 후 제공한다. Spring Boot의 내부 오류 디스패치는 기능 API가 아니다.

## SSH 코드 에디터

POST /api/v1/studio/jobs, GET/DELETE /api/v1/studio/jobs/{id}: 세션 소유 로컬/SSH 파일·Git·Codex 작업. [계약](studio.md).

| POST | /api/v1/studio/jobs/{id}/inputs | OWNER + 작업 소유 세션 + CSRF | Codex 승인·답변·추가 지시·중지 |

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
- /dav/**: OWNER Basic 인증 WebDAV. [메서드·제약](../nas.md).

## 원격 자동 구성

- POST /api/v1/devices/{id}/remote-setup — OWNER 세션·CSRF, 비동기 자동 구성 및 연결 검증 시작.
- GET /api/v1/devices/{id}/remote-setup — OWNER 세션, 구성 상태 조회.

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
