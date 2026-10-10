# Communications Hub

기존 Spring Boot/Thymeleaf/Vanilla JS 안의 내장 앱이다. Launcher Registry가 Home, App Library, 검색, Dock, Workspace 탭에 같은 앱을 제공한다. 별도 프런트엔드 서버가 없다.

## 현재 지원 범위

| 연결 | 구현된 동작 | 제한 |
| --- | --- | --- |
| Gmail | 공식 OAuth, 서버 refresh, threads 목록/검색, plain-text/HTML→text 읽기·새 메일·답장·본문 전달, 5 MiB 첨부 전송/다운로드, 라벨·읽음, full/history 동기화 | HTML 원본은 실행하지 않음. 전달 시 첨부는 새로 선택. OAuth 실계정 검증 필요 |
| Slack | 공식 Bot OAuth, conversations.list/history, text 전송·thread_ts 답장, DM/그룹 DM 스레드 페이지 조회, 공식 파일 upload/download, 승인 후 수정·삭제·리액션, 서명 Events API | Bot membership/scopes에 따른 접근. 채널 스레드 읽기는 Bot API 권한 제한. Slack 공유 메시지 ID가 늦게 보고되면 UNKNOWN |
| Discord | 공식 Bot token 확인, guild text/announcement channel 및 활성 스레드·forum 게시글 탐색, 메시지 읽기·작성·답장·파일, 승인 후 수정·삭제·리액션, 선택적 Gateway heartbeat/resume | 공식 active threads 응답에 포함된 스레드만 제공하며 archived thread 탐색·forum 게시글 생성은 미구현. 개인 계정·self-bot 미지원. Message Content intent/채널 권한 필요. 실 Gateway 계정 검증 필요 |
| Web Bridge | 프로필별 Chromium/X/VNC, 직접 로그인·입력, Guacamole 재연결, 접근성 기술 조회, 프로필 삭제 | DOM/접근성 정보만으로 메시지 구조화 성공을 주장하지 않음. 로그인 페이지는 추출하지 않음 |
| 카카오톡 (Wine 화면) | 공식 Windows 앱 화면 전송, 프로필별 설치/로그인 상태 저장, 직접 입력 | 로그인 화면·재시작 복구 확인. 실제 계정 로그인·송수신 미검증 |
| Windows Agent (선택 진단) | 선택적 Windows Agent, 읽기 전용 UIA 관측, registered SSH 연결, 기존 RDP/VNC 화면 | 현재 GUI 메시지 정규화/자동 발송 없음. 창이 없거나 잠긴 세션에서 UNKNOWN 반환. 서비스 자동화 허용 여부를 추정하지 않음 |

미구현 또는 권한 없는 기능은 UI에서 비활성화한다. 메시지 본문은 text로 escape하여 렌더링하며 HTML이나 첨부파일을 실행하지 않는다. 시각/읽음/참여자 정보가 없으면 추측하지 않는다. Slack/Discord의 발신자 ID는 API가 준 원본 값이며 별도 사용자 프로필 조회를 흉내내지 않는다.

## 계정 설정

- Google Cloud에서 Gmail API와 웹 OAuth Client를 설정한다. `COMMUNICATION_GOOGLE_CLIENT_ID`, `_CLIENT_SECRET`, `_REDIRECT_URI`. Redirect URI는 `https://<dashboard-host>/api/v1/communications/oauth/callback`. localhost 개발만 HTTP 허용.
- Google scope는 `gmail.modify` (읽기·전송·라벨/읽음 변경에 필요한 scope; 영구 삭제 권한을 요청하지 않음). offline access와 consent로 refresh token을 요청한다. Google 검증/테스트 사용자/조직 정책은 운영자가 설정해야 한다.
- Slack 앱의 redirect와 bot scopes는 `channels:read,groups:read,im:read,mpim:read,channels:history,groups:history,im:history,mpim:history,chat:write,reactions:write,files:read,files:write`. `COMMUNICATION_SLACK_CLIENT_ID`, `_CLIENT_SECRET`, `_REDIRECT_URI`. 앱이 실제 승인받은 scope로 전송 가능 여부를 제한한다.
- Discord Developer Portal의 공식 Bot Token을 계정 연결 비밀번호 필드에 입력한다. 토큰은 연결 확인 후 CredentialVault로만 저장한다. 초대된 서버의 필요한 채널 권한과 Message Content intent를 별도로 설정한다. 일반 사용자 계정 토큰을 받지 않는다.
- OAuth는 시작한 대시보드 로그인 세션에 귀속한다. 서버 Chromium에서 인증하려면 Chromium 안에서 대시보드를 열고 연결을 시작한다. 다른 브라우저의 state를 복사하지 않는다.
- 계정 연결 해제는 인증 암호문·캐시·cursor·전송 요청을 CASCADE 삭제한다. Provider 자체의 앱 grant를 revoke하지는 않으므로 필요하면 원본 계정 설정에서도 취소한다.

## 전송 승인과 복구

UI/MCP 모두 먼저 immutable `PENDING` 요청을 만든다. OWNER 브라우저가 수신 대상/본문을 보고 CSRF 보호된 confirmation POST를 수행해야 전송된다. MCP에는 승인/실행 도구가 없다. 요청은 10분 유효하고 승인 시 DB compare-and-swap으로 한 번만 `SENDING`을 선점한다. 성공은 `SENT`, 실패는 `UNKNOWN`으로 남는다. 네트워크 실패 후 자동 재전송하지 않는다. 원본 서비스에서 확인한 후 필요한 경우 새 요청을 만든다. 서버가 전송 중 재시작되면 `SENDING`을 재실행하지 않는다.

메시지 cache PK는 account + conversation + original message ID다. 재조회는 upsert하고 시각/ID로 정렬한다. cursor는 계정·대화별 보관한다. Gmail은 별도 동기화 버튼으로 한 페이지씩 full scan→history를 진행하며 SQLite checkpoint를 이어간다. 첫 scan 전 history 기준을 확보해 scan 중 변경도 이어 읽는다. history 만료 404는 cache를 비우고 새 full scan 기준으로 재시작한다. Slack Events는 HMAC/timestamp 검증 후 중복 event_id를 제거하고 cache를 갱신한다. Discord Gateway는 sequence resume하며 partial update는 REST invalidation으로 처리한다. Slack 이벤트 보존 범위 밖 또는 Discord resume 불가 구간의 전체 과거 이력은 Provider paging으로 확인해야 한다. Provider마다 429/권한 실패를 안전한 오류로 변환한다. 기본 검색은 로컬에 조회했던 메시지 최대 100개이며 전체 서비스 인덱스가 아니다. 검색 범위에서 Gmail 계정을 선택하면 공식 query 연산자와 cursor로 계정 메시지를 검색한다(한 페이지 10개). Slack/Discord에 공식 계정 검색을 제공하는 것처럼 표시하지 않는다. 기본 목록은 Gmail 25 threads, Slack 100 channels/15 messages, Discord 10 guilds/50 messages 페이지다.

WS는 기존 WorkspaceEvents와 동일 origin/session 소유권을 재사용한다. 변경 frame은 `communications` topic만 보낸다. 본문·토큰을 WS 알림에 넣지 않는다. WS reconnect/epoch gap은 기존 all invalidation을 통해 REST를 다시 읽는다. 자동 Provider 조회는 60초 이상 간격이다. Slack의 배포 유형별 요청 한도로 추가 대기가 필요할 수 있다.

대화 탭 최대 6개와 분할 화면은 동일 앱 안에서 동작한다. sessionStorage에는 계정 ID/대화 ID/제목/종류와 선택 탭·분할 여부만 저장하고 본문·초안·인증정보는 저장하지 않는다. 초안은 열려 있는 페이지 메모리에만 보관한다. 모바일은 대화 목록/본문 전환을 사용한다.

## Chromium Bridge 운영

기존 browser 이미지와 profile volume을 재사용한다. 별도 환경변수 없이 loopback 9224 broker가 시작된다. browser가 256-bit 난수 내부 인증키를 전용 `communication-bridge-control` 볼륨에 자동 생성하고 재시작 시 재사용한다. 키 파일은 UID 10001 전용 0600이며 dashboard는 볼륨을 읽기 전용으로 마운트한다. 키를 API/UI/로그로 전달하지 않는다. Communications → 계정 관리 → 격리 프로필 추가 → 원격 화면에서 사용자가 직접 로그인한다. 기존 `COMMUNICATION_BROWSER_TOKEN` 환경변수는 더 이상 사용하지 않으므로 제거해도 된다. 이 변경을 적용하려면 dashboard/browser 이미지를 빌드하고 Compose 컨테이너를 재생성해야 한다. Gmail/Slack 공식 API OAuth 설정은 별개다. 공유 Chromium은 기존 :1/5901/9222를 유지한다. Communication profile은 `profile/communications/<UUID>`이며 `.provider`로 서비스 변경을 거절한다. 각 실행은 독립 user-data-dir, X display :10~:13, VNC 5910~5913, CDP 9240~9243이다. 최대 4개 동시 실행/16개 저장 프로필. 다른 프로필의 쿠키를 복사하거나 JS로 읽지 않는다. 프로필별 Chromium은 기존 비루트 browser UID로 실행한다.

broker/CDP/VNC 포트를 외부에 게시하지 않는다. 기존 Compose의 dashboard 네트워크 네임스페이스 안에서 loopback만 사용한다. 추가 컨테이너나 Docker socket 권한은 필요 없다. 화면을 닫으면 RuntimeService의 VNC 세션만 종료하고 로그인 프로필은 유지한다. 중지 버튼은 로그인 상태를 보존하며 실행 슬롯을 비운다. 명시적 프로필 삭제는 해당 화면들을 닫고 Chromium을 정상 종료한 뒤 프로필만 제거한다. API는 임의 URL/명령/CDP 메서드를 받지 않는다.

접근성 조회는 원본 앱 host의 표시 역할과 이름만 제한적으로 읽는다. 로그인/비밀번호 input 값은 추출하지 않는다. `structuredMessages=false`이므로 이를 대화/메시지로 자동 승격하지 않는다. 카카오톡/Discord의 내부 토큰이나 프로토콜을 추출하지 않고 탐지/로그인 제한을 우회하지 않는다.

## Windows Agent (기존 선택적 진단 경로)

`tools/windows-runtime-agent/WindowsRuntimeAgent.csproj`를 .NET 10 Windows Desktop SDK로 빌드한다. GUI를 사용하는 일반 Windows 사용자로 실행한다. 관리자/서비스 session 0에서 실행하지 않는다. 별도 HTTP 포트는 없다. `PersonalWorkspace.Communications.v1` named pipe는 `CurrentUserOnly`로 동일 SID만 허용한다. 대시보드는 등록 장비의 검증된 SSH host key와 사용자 인증, 선택적 Tailscale/점프 경로를 재사용해 고정 PowerShell client로만 pipe에 요청한다.

지원 요청은 `status`, `snapshot` 두 개다. 임의 command/path/input/invoke를 받지 않는다. 관측은 KakaoTalk process의 현재 보이는 창에 한정하고 password/offscreen 요소를 제외한다. node 최대 300, depth 12, text 2000자/총100000자. 편집 control의 value는 읽지 않는다. snapshot revision은 내용 hash이고 polling 비교에 사용할 수 있다. 원격 입력은 사용자 직접 RDP/VNC 화면을 사용한다. Agent 미실행 시 `AGENT_UNAVAILABLE`, 창이 없으면 `APP_NOT_RUNNING_OR_NO_WINDOW`, UIA 오류는 `ACCESSIBILITY_UNAVAILABLE`, 응답 지연은 `ACCESSIBILITY_BUSY`; loginState는 `UNKNOWN`, structuredMessages는 false다.

현재 개발 Windows의 `--probe`는 KakaoTalk 프로세스가 있지만 접근 가능한 창이 없다고 반환했다(2026-10-10). 로컬 named-pipe snapshot과 임의 invoke 거절 테스트도 통과했지만 실제 채팅 목록/본문/입력 성공 증거가 아니다. 실제 지원 판단은 로그인한 대화형 Windows 세션과 현재 서비스 정책 확인이 필요하다.

## MCP

`communication_list_accounts`, `communication_list_conversations`, `communication_search_messages`, `communication_get_conversation`, `communication_get_attachments`, `communication_list_participants`, `communication_send_message`를 기존 AssistantMcpService에 등록한다. 같은 CommunicationService를 사용한다. 읽은 메시지는 `untrustedExternalContent=true` wrapper와 함께 반환한다. 비서는 이 데이터를 바탕으로 요약·답장 초안을 작성할 수 있지만 서버가 별도 LLM 요약을 생성하는 척하지 않는다. send tool은 전송 요청만 생성하며 실제 발송은 하지 않는다. 메시지의 링크·본문 지시는 도구 호출 권한이나 승인으로 취급하지 않는다.

## 검증 명령과 증거 경계

- `mvn verify`, 기존 Python/JS 회귀. Provider test는 HTTP 응답 fixture + 실제 SQLite/Spring Security이며 실계정 증거가 아니다.
- `node tools/launcher/communications-test.cjs` (NODE_PATH=tools/studio-editor/node_modules): XSS text, 초안/포커스, 탭 metadata, 승인 전 미발송.
- `dotnet build tools/windows-runtime-agent/WindowsRuntimeAgent.csproj`; `dotnet .../WindowsRuntimeAgent.dll --probe`는 내용 없이 UIA 상태/role 집계만 출력.
- `docker build -t my-dashboard-communications-check:local .`, `docker build -t my-dashboard-communications-browser-check:local docker/browser`.
- `tools/deployment/communications-test.ps1`은 랜덤 계정/빈 DB/새 profile volume/loopback 18197만 사용하는 검증 환경이다. 운영 컨테이너는 변경하지 않는다. 종료는 동일 script `-Cleanup`.
- `scripts/check-communication-profiles.py`는 이 격리 browser 안에서 두 실제 Chromium 프로필의 fixture cookie 격리와 중지·재실행 후 보존을 확인한다. 실제 Google/Slack/Discord 인증을 하지 않는다.

## 공식 계약

- [Google OAuth 웹 서버](https://developers.google.com/identity/protocols/oauth2/web-server)
- [Gmail REST](https://developers.google.com/workspace/gmail/api/reference/rest), [동기화](https://developers.google.com/workspace/gmail/api/guides/sync)
- [Slack OAuth](https://docs.slack.dev/authentication/installing-with-oauth/), [메시지 조회](https://docs.slack.dev/messaging/retrieving-messages/)
- [Discord 메시지](https://docs.discord.com/developers/resources/message)
- [Windows UI Automation](https://learn.microsoft.com/en-us/dotnet/api/system.windows.automation.automationelement)
- [카카오 운영정책](https://www.kakao.com/policy/oppolicy)

## 이벤트 운영

Slack Events 요청 URL은 `/api/v1/communications/events/slack`. 공개 endpoint이지만 raw body의 HMAC-SHA256과 5분 timestamp를 검증한다. `COMMUNICATION_SLACK_SIGNING_SECRET`을 설정하고 Slack 앱에서 필요한 `message.channels`, `message.groups`, `message.im`, `message.mpim` 이벤트를 구독한다. URL verification도 서명을 요구한다. 1 MiB 제한, 24시간 event_id receipt로 재전송을 제거한다. 다른 API는 OWNER/CSRF가 그대로다.

Discord Gateway는 `COMMUNICATION_DISCORD_GATEWAY_ENABLED=true`일 때만 자동 시작한다. bot 인증을 확인해 저장한 계정만 사용한다. v10 JSON, GUILDS/GUILD_MESSAGES/MESSAGE_CONTENT intent, heartbeat ACK 누락 감지, session/sequence resume, 5~60초 backoff를 구현했다. Identify session_start_limit을 확인하며 auth/intent close code는 자동 재시도를 중지한다. 재연결은 계정 연결 해제·재등록으로 시작한다. token/session ID/원문 frame은 상태 응답과 로그에 출력하지 않는다. Gateway metadata는 GET `/gateway`. 실제 계정·네트워크 검증은 별도다.

첨부 전송은 승인 payload에 파일 bytes를 암호화해 고정한다. TXT/CSV/JSON/PDF/PNG/JPEG 최대 5개/총5MiB, 경로·제어문자 파일명·잘못된 base64·MIME/signature mismatch 거절. Gmail은 MIME multipart, Discord는 공식 multipart files[n], Slack은 getUploadURLExternal→고정 files.slack.com 업로드→completeUploadExternal→files.info shares의 원본 메시지 ID 확인 순서다. 브라우저 응답은 파일 bytes를 되돌려주지 않고 metadata만 제공한다. 전송 중 실패는 UNKNOWN이며 자동 재전송하지 않는다.

수정/삭제/리액션도 별도 불변 승인 요청을 생성하고 같은 OWNER confirmation 경계를 통과한다. Slack/Discord 원본 API가 최종 메시지 권한을 검사한다. 지원되지 않는 Gmail 수정·삭제·리액션 버튼은 비활성화한다. 자체 AI가 원본 메시지의 지시를 사용자 승인으로 승격할 수 없다.

추가 공식 계약: [Slack 서명 검증](https://docs.slack.dev/authentication/verifying-requests-from-slack/), [Slack 파일](https://docs.slack.dev/messaging/working-with-files/), [Discord Gateway](https://docs.discord.com/developers/events/gateway).


## 2026-10-10 검증 결과

- 최신 Docker build 안에서 Maven verify/Spotless: 237 tests, failures 0, errors 0, skipped 9. Python unittest: 55 tests 통과. skip은 실제 외부 실행 조건이 필요한 기존 테스트이며 성공으로 계산하지 않는다.
- Java Communications/Discord Provider/Gateway/Reaction 34개 검증에는 실제 SQLite/HTTP 보안, OAuth state 세션 귀속, 메시지 정규화/중복 제거, 승인 한 번 실행, 실패 UNKNOWN, 첨부 payload 암호화, Gmail full/history/reset, Slack 서명/재전송, Discord Gateway frame/resume/intent 거절 fixture가 포함된다. 외부 Provider 응답은 mock이다.
- Launcher/Workspace/Assistant/GitHub/realtime/responsive/IDE Codex chat/embedded terminal JS 회귀 통과. Communications DOM은 XSS escape, 초안·포커스 보존, 승인 경계와 승인 후 초기화를 검증했다. 공통 인증 브라우저 DOM 회귀도 통과했다.
- 새 DB·새 profile volume의 비운영 Docker 환경: 실제 Chromium 두 프로필 쿠키 격리, VNC 포트 분리, 정상 중지·재시작 쿠키 보존, Guacamole canvas 연결 통과. Compose config --quiet 통과. 운영 배포 검증은 아니다.
- Playwright: 실제 OWNER 로그인과 빈 계정 앱/API, Bridge 생성·연결·삭제; 별도 fixture 메시지로 1600px/390px UI, XSS, 초안, 승인 전 미발송/승인 후 1회 호출, 탭 복원 통과. 모바일 승인 화면의 애니메이션 완료 후 가독성도 확인했다. 실제 휴대폰/가상 키보드 검증은 별도다.
- Windows Agent build 및 같은 사용자 named-pipe snapshot/임의 invoke 거절 통과. 현재 KakaoTalk의 접근 가능한 창이 없어 실제 채팅 조회/입력은 검증하지 못했다.

남은 외부 검증에는 Google/Slack OAuth Client와 승인된 테스트 계정, Discord Bot/intent/채널 권한, 공개 Slack Events callback, 로그인한 Windows 대화형 세션과 SSH/RDP 또는 VNC 등록이 필요하다. 비밀값은 서버 환경설정 또는 계정 연결 UI로만 입력한다.

현재 Web/Windows Bridge는 원격 화면과 제한된 접근성 관측을 제공한다. 서비스별 DOM/UIA를 정규화된 대화·메시지로 재구성하는 adapter와 GUI 자동 발송은 구현하지 않았다. 정책과 실제 화면 구조를 검증하기 전 지원을 약속하지 않는다. 공식 API Provider의 실계정 연결·토큰 갱신·송수신·이벤트 복구도 아직 완료로 표시하지 않는다.


### Discord 대화 탐색 보완

`GET /guilds/{guild.id}/threads/active`의 원본 thread ID를 `Conversation.kind=THREAD`로 제공한다. forum 컨테이너 자체를 전송 가능한 대화로 표시하지 않고 활성 게시글을 별도 대화로 표시한다. 가능한 경우 API가 제공한 부모 채널 이름을 제목에 포함한다. 읽기와 승인 후 전송은 기존 `/channels/{thread.id}/messages` 공식 경로를 사용하며, API의 권한·잠금·보관 상태 거절을 우회하지 않는다. thread 생성/가입/보관 해제는 수행하지 않는다. 기존 guild cursor는 빈 검색 결과에서도 유지하고 같은 thread ID는 중복 제거한다.

계약 근거: [Discord List Active Guild Threads](https://docs.discord.com/developers/resources/guild#list-active-guild-threads). 추가 단위 테스트는 API fixture를 사용하며 실제 Bot/서버 접근 검증을 대신하지 않는다.

Slack DM/그룹 DM의 스레드 화면에서 다음 답글 페이지를 계속 읽을 수 있다. 원본 message ID로 중복을 제거하고 작성 중인 본문은 보존한다. 채널 스레드의 Bot 권한 제한은 그대로다.

추가 환경 확인: 현재 로컬 `.env`에 Communication용 Google/Slack OAuth Client와 Slack Signing Secret 설정이 없다(값 출력 없이 설정 여부만 검사). 운영 서버에 동일하게 없다는 의미는 아니며, 실계정 테스트를 시작할 수 있는 설정·접속 경로가 필요하다.

추가 Playwright 검증: Slack DM fixture에서 스레드 다음 페이지 조회, 외부 본문 HTML 비실행, 스레드 창을 닫은 후 작성 초안 보존이 최신 Docker 이미지에서 통과했다. 원격 Chromium/Guacamole 및 모바일 승인·탭 복원 회귀도 통과했다. 검증용 컨테이너와 프로필 볼륨은 정리했다.


### 복원 및 답장 승인 보완

Communication 탭 저장 값은 version 2 object(panes, selected, split)다. 기존 배열 형식도 읽는다. 복원 시 계정 존재 여부, ID/제목 길이, kind enum, 중복을 검사하고 허용된 metadata만 새 객체로 옮긴다. 저장 값에 메시지·초안·replyTo·권한이 섞여 있어도 복원하지 않는다. 현재 목록의 같은 대화가 있으면 최신 제목·종류를 반영한다. 선택 탭과 분할 여부, 현재 목록 첫 페이지에 없는 DM/그룹 DM 종류도 복원한다.

Gmail 답장은 PENDING 생성 전에 공식 metadata에서 Reply-To(없으면 From)와 제목을 확인해 암호화된 승인 내용에 고정한다. 승인 창에 실제 수신자·제목을 표시한다. 발송 전 원본 대화 ID와 수신자·제목을 다시 확인하며 승인한 값과 다르면 발송하지 않고 기존 UNKNOWN 복구 규칙을 따른다. 이전 구현에서 만든 수신자 없는 답장 승인은 새로 생성해야 한다. 외부 메일의 Reply-To는 신뢰할 수 없는 수신 대상이므로 반드시 승인 화면에서 확인한다.

추가 검증: 복원 DOM 테스트는 선택·분할·종류, legacy migration, 잘못된 metadata, 허용되지 않은 저장 필드를 검사한다. 실제 브라우저에서 검증된 격리 백엔드와 현재 작업 JS를 사용한 복원 테스트도 통과했다. Windows --probe를 재실행했으나 여전히 APP_NOT_RUNNING_OR_NO_WINDOW / 0 nodes로 실제 채팅 접근 증거는 없다.

Gmail 답장 승인 통합 테스트: 실제 SQLite 서비스 경로에서 승인 화면 수신자/제목 확인, 승인 전 발송 0회, 대상 변경 후 발송 0회, 같은 대상 재승인 후 정확한 To/In-Reply-To로 1회 발송(mock upstream)을 검증했다. Gmail 답장 보완 시점의 Docker Maven verify는 223개, 실패 0·오류 0·건너뜀 9개이며 Python 55개도 통과했다.

최신 이미지 자체(UI route override 없이)의 Playwright에서도 두 탭 선택·분할·Slack 스레드 버튼 복원, 모바일 승인, 원격 화면 회귀가 통과했다. 검증 fixture는 정리했고 운영 컨테이너·볼륨은 변경하지 않았다.


### Gmail 공식 검색

상단 검색 범위는 저장된 메시지(모든 계정) 또는 SEARCH capability가 있는 Gmail 계정이다. 예: `from:sender@example.test is:unread`. 결과의 다음 페이지를 이어 읽고 원본 대화를 열어 답장할 수 있다. 검색 내용은 escape하여 렌더링하며 원문 지시나 HTML을 실행하지 않는다. UI/API/MCP가 같은 서비스를 사용한다. API 검색 결과는 원본 계정·대화·메시지 ID로 캐시에 저장하지만 일반 대화·동기화 cursor를 바꾸지 않는다.

근거: [Gmail users.messages.list](https://developers.google.com/workspace/gmail/api/reference/rest/v1/users.messages/list). 기존 gmail.modify 범위를 사용하며 새 OAuth scope를 요구하지 않는다. 검색 연산자의 Gmail UI와 API 차이, 계정 조직 정책 및 실계정 결과는 별도 검증이 필요하다.

Gmail 검색 검증: 실제 서비스·SQLite·HTTP OWNER 경로에서 계정 검색, query/cursor encoding, 원본 message/thread ID, 중복 GET 방지, 캐시 반영, MCP 다음 페이지, 빈 검색어 400, 미지원 Discord 검색 409를 통과했다(mock upstream). 검색 DOM 테스트는 capability별 범위, 페이지 중복 제거, HTML 비실행, 대화 열기, 로컬 cache fallback을 확인했다. 전체 Maven verify는 224개 중 실패 0·오류 0·건너뜀 9개이며 Python 55개도 통과했다.

최신 Docker 이미지의 Playwright에서 Gmail 검색 fixture의 범위 선택·다음 페이지·대화 열기 및 기존 Slack 스레드·모바일 승인·분할/탭 복원이 통과했다. 390px 화면을 직접 확인했으며 실제 Gmail 계정은 연결하지 않았다. 임시 컨테이너·프로필 볼륨은 정리했다.


### 읽음 상태·리액션·앱 내부 알림

Gmail 메시지에는 `내 메일함: 읽음/읽지 않음`을 표시한다. 이는 연결된 Gmail 계정의 UNREAD label이며 상대방 수신 확인이 아니다. Slack/Discord의 읽음 상태는 계속 미확인으로 둔다. 리액션은 공식 응답의 이름·emoji ID·count를 읽기 화면에 표시한다. Slack count가 users 배열보다 클 수 있으므로 배열 길이를 쓰지 않는다. 알려지지 않은 수는 생략하고 라벨은 escape한다. 이전 캐시 payload도 읽을 수 있다.

Communications 이벤트가 오면 열어둔 대화의 캐시를 갱신한다. 이미 확인한 메시지보다 시각이 최신이고 원본 ID가 새로운 항목만 추가 알림 대상으로 삼는다. 활성 화면에서는 상태 영역, 다른 앱을 보는 중에는 공통 toast를 사용한다. 반복 이벤트·과거 이력·시각 미확인 항목에는 신규 알림을 만들지 않으며 알림에 메시지 본문은 넣지 않는다. 이는 열어둔 대화의 앱 내부 알림이며 모든 계정의 모든 메시지 알림, OS push, 미열람 수 또는 수신 확인을 보장하지 않는다.

근거: [Slack message event](https://docs.slack.dev/reference/events/message/), [Discord Reaction Object](https://docs.discord.com/developers/resources/message#reaction-object).

읽음·리액션 검증: Slack 불완전 users 배열과 공식 count 차이, Discord custom emoji ID·삭제된 이름·미제공 count, 이전 캐시의 reactions 누락, 서비스·캐시 매핑을 검증했다. 이벤트 DOM 검증은 과거 이력 제외, 반복 이벤트 중복 알림 방지, 비활성 앱 알림, 삭제 반영, 대화 탭이 없는 상태의 AI 승인 요청 갱신까지 통과했다. 전체 Maven verify는 228개 중 실패 0·오류 0·건너뜀 9개, Python 55개 통과다.

Playwright는 검증된 최신 Java 백엔드 이미지와 현재 작업 JS를 사용해 Gmail 개인 메일함 읽음 표시·Slack 리액션 표시, 검색·모바일 승인·탭 복원을 통과했다. 메시지는 명시적인 API fixture이며 실제 계정/이벤트 증거가 아니다. 대화 없는 승인 갱신의 마지막 JS 수정은 DOM 및 현재 파일 브라우저 검증이며, 해당 이미지에 재포장한 증거와 구분한다. 테스트 환경은 정리했다.


### 참여자·대화 첨부 정보

Slack PARTICIPANTS capability가 있는 대화는 메시지 화면과 우측 정보 패널에서 참여자 목록을 읽고 다음 페이지를 이어 볼 수 있다. 공식 사용자 ID를 유지하고 중복을 제거하며 이름을 추정하지 않는다. 추가 users:read 권한 없이 기존 conversation read scopes를 사용한다. 미지원 연결의 버튼은 비활성화한다. 우측 첨부파일 목록은 현재 조회한 메시지에서만 구성하고 기존 안전한 다운로드 경로를 재사용한다. 목록은 메시지 조회·이벤트 갱신 후 갱신한다.

[Slack conversations.members 공식 계약](https://docs.slack.dev/reference/methods/conversations.members/). API와 MCP `communication_list_participants`는 같은 서비스를 사용하며 권한 검사를 우회하지 않는다. 실제 Slack 계정·채널 접근은 여전히 미검증이다.

원래 요구사항과 필수 테스트별 완료 증거·미완료 범위는 [완료 기준 점검](communications-verification.md)을 참고한다.

최신 검증: 참여자 scope/ID/paging/MCP 집중 테스트 26개, 전체 Maven verify 229개(실패 0·오류 0·건너뜀 9), Python 55개 통과. 현재 UI 전체가 포함된 이미지에서 App Library 검색·실제 앱 버튼 실행, 참여자 다음 페이지·HTML 비실행, 검색·모바일 승인·탭/분할 복원 Playwright 통과. 이전 UI 파일 override 단계와 달리 이번에는 이미지 자체를 검증했다. 임시 환경은 정리했다.


### OAuth 갱신 경합과 실패 복구

갱신 시 DB의 최신 암호화 자격증명을 다시 읽으며, 이전 암호문이 일치하는 경우에만 원자적으로 교체한다. 회전 전 refresh token을 담은 오래된 계정 객체를 재사용하지 않는다. 갱신 도중 연결 해제 또는 재인증이 발생해도 계정을 다시 생성하거나 새 자격증명을 덮어쓰지 않는다.

일회용 refresh 교환 전에 암호화 payload의 reconnectRequired를 true로 기록한다. 교환 성공 시 검증한 만료 시각과 새 토큰을 저장하고 false로 되돌린다. Google이 refresh token을 생략하면 기존 값을 유지하며 Slack 회전 응답에는 새 refresh token이 필요하다. 불명확한 네트워크 실패·잘못된 응답·중간 종료 뒤에는 같은 토큰을 자동 재전송하지 않고 계정 재연결을 요구한다. 명시적인 HTTP 429만 기존 상태로 복원하여 다음 사용자 요청에서 재시도할 수 있다. 이 보수적인 처리는 일시 장애에도 재인증이 필요할 수 있다.

실제 SQLite와 mock OAuth 응답으로 동시 갱신, 오래된 snapshot, 연속 Slack 회전, 연결 해제/재연결 경합, 429, 불명확한 실패, 잘못된 만료 값 및 refresh token 누락을 검증했다. 실제 공급자의 토큰 회전 성공을 의미하지 않는다.

OAuth 갱신 보강 후 최신 Docker 이미지 build 성공: Maven verify/Spotless 237개 중 실패 0·오류 0·건너뜀 9개, Python 55개 통과. CommunicationIntegrationTest 27개 통과. 이전 UI/격리 Chromium 검증 이후 UI와 브라우저 코드는 변경하지 않았다. 실제 OAuth 및 GUI 환경 검증은 여전히 미완료다.

2026-10-10 Chromium 자동 내부 인증 변경 검증: 새 토큰 환경변수 없이 전용 control 볼륨으로 dashboard/browser를 실행해 Playwright 원격 화면 및 UI 검증 통과. 실제 Chromium 프로필 쿠키 격리·중지/재시작, browser 컨테이너 재시작 후 내부 키 유지, dashboard 읽기 전용 마운트 확인. Linux 키 생성/권한/심볼릭 링크 거절 테스트 2개, Java 집중 28개 및 전체 Maven verify/Spotless 238개(실패 0·오류 0·건너뜀 9), 기존 Python 55개 통과. Compose config 통과. 실제 공급자 로그인은 검증하지 않았으며 운영 컨테이너에는 적용하지 않았다.


## 메신저 원격 화면 모드 (현재 기본 진입점)

Communications 왼쪽의 카카오톡/Slack/Discord 버튼으로 원본 앱 화면을 연다. 처음 클릭하면 해당 서비스 프로필을 생성하며 다음 클릭에서는 기존 프로필을 재사용한다. 같은 서비스 프로필이 여러 개면 계정 관리에서 선택한다. Slack/Discord는 서버 Chromium의 공식 웹사이트이고 카카오톡은 별도 Wine 컨테이너의 공식 Windows 앱이다. 사용자 직접 로그인·마우스·키보드 입력은 기존 OWNER/CSRF/세션 소유권 및 Guacamole 경계를 사용한다. 공개 방송이나 타인 공유 링크는 제공하지 않는다.

화면 모드에는 Google/Slack OAuth Client, Discord Bot Token이 필요하지 않다. 공식 API 연결과 수신함은 선택 기능으로 유지한다. 원격 앱에서 직접 보낸 메시지는 원본 앱의 전송 동작이며 API의 승인 대기 목록을 거치지 않는다. AI에는 화면 조작/전송 도구를 추가하지 않는다. 원격 화면 본문을 DB·검색·MCP 메시지로 추출하지 않는다. 로컬 파일 drag/drop, 음성/영상 통화 전달은 이번 구현 범위가 아니다.

Wine은 linux/amd64 별도 서비스이며 기존 browser/guacd/dashboard를 재사용한다. 내부 broker 9225와 VNC 5920~5923은 loopback에만 바인딩한다. `communication-wine-profile` 볼륨의 UUID별 Wine prefix에 설치/로그인 상태를 저장하고 기존 `wine-profile` 볼륨을 가져오거나 지우지 않는다. browser가 생성한 내부 키는 Wine에서도 읽기 전용으로 사용한다. 브로커 중복 실행은 볼륨 lease로 거절한다. 서비스 실행은 사용자가 프로필 화면을 열 때 시작하며 최초 설치는 시간이 걸린다.

카카오 설치 파일은 공식 CDN의 Kakao Corp. 유효 서명, 버전 26.8.2.5324 및 SHA-256을 확인해 Dockerfile에 고정했다. 상류 파일 변경으로 해시가 달라지면 빌드를 거절하며 새 공식 서명을 확인한 뒤 코드의 해시를 갱신한다. 사용자가 환경변수에 해시를 입력하지 않는다. Wine 호환성과 실제 로그인·송수신은 별도 검증 대상이며 설치 성공만으로 보장하지 않는다.

최종 화면 모드 검증: 최종 dashboard/browser/wine 이미지 조합에서 Playwright로 세 서비스의 실제 로그인 화면 확인. 카카오톡은 컨테이너 재시작 후 같은 prefix 재사용 확인. Wine broker의 무인증 403 및 다른 Provider 실행 409 거절 확인. 기존 수신함 모바일·승인·탭 복원 Playwright, 인증 브라우저 및 Communications DOM 회귀 통과. 최종 Maven verify/Spotless 238개(실패 0·오류 0·건너뜀 9), Python 55개 통과. Compose config 통과. 격리 검증 환경을 정리했으며 운영 배포·실계정 로그인·메시지 전송은 수행하지 않았다.

## 메신저 UI (2026-10-10)

전역 Workspace Activity Rail은 그대로 두고, 계정 선택·원격 메신저 진입·대화 검색·대화 목록을 단일 사이드바에 모았다. 기본 너비는 300px(중간 화면 280px)이며 나머지는 대화 영역이 사용한다. 상세 정보·첨부파일·AI·승인 목록은 기본적으로 닫혀 있고 대화 헤더 버튼 또는 승인 배지로 연다. 검색은 대화 목록의 즉시 필터와 접이식 공식/캐시 메시지 검색으로 구분한다.

메시지는 발신자·시각·본문·첨부·리액션·스레드 작업 순서다. Gmail은 제목과 본문을 메일 스레드로 표시한다. API가 수신자 목록이나 정확한 미읽음 개수를 제공하지 않으므로 이를 만들지 않는다. 읽음 boolean은 읽지 않음 표시로, 조회 성공/실패는 계정 조회 상태로 표시한다. 서버 조회 성공을 서비스 온라인 상태로 표시하지 않는다.

작성기는 대화 하단에 유지하며 답장 취소, 첨부 취소, 초안 표시, 전송 내용 확인을 제공한다. 발송은 기존 불변 승인 API를 그대로 사용한다. 로딩·목록 없음·대화 미선택·조회 오류·권한 부족·원격 화면 준비 상태는 각각 표시한다. 미연결 상태는 Gmail/Slack/Discord/카카오톡 및 계정·Remote Bridge 관리 진입점을 제공한다.

800px 이하에서는 목록 → 대화 → 상세 정보의 한 화면 전환을 사용한다. 목록으로 돌아간 뒤 갱신 이벤트가 대화를 강제로 다시 열지 않는다. visualViewport 변화에 맞춰 작성기 영역 높이를 조정한다. 탭·분할 복원의 sessionStorage version 2와 기존 공개 init/open/refresh, data-comm 이벤트, Workspace 무효화 계약은 유지한다. 본문 초안은 기존처럼 창 메모리에만 보관한다.
