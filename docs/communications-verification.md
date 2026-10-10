# Communications 완료 기준 점검

2026-10-10 기준. 이 문서는 전체 목표 완료 선언이 아니다. 구현 코드, fixture 테스트, 격리 실행, 실제 계정 검증을 구분한다. 상세 계약과 설정은 [Communications](communications.md), [API](api/specification.md), [DB](database/schema.md)에 있다.

## 원래 요구 범위

| 요구 영역 | 현재 근거 | 판정 및 남은 사항 |
| --- | --- | --- |
| 기존 구조·기능 보존 | Java/Spring/Thymeleaf/Vanilla JS, 기존 Registry/RuntimeService/Vault/WS/Guacamole 재사용. Maven·기존 JS 회귀 | 코드·격리 검증. 운영 사용자 데이터에 대한 회귀는 별도 |
| Launcher/Library/검색/Home/Dock/탭 | 중앙 app-registry에 Communications, 기존 실행·핀·탭 계약 재사용 | Registry/Launcher DOM 및 격리 브라우저 앱 실행·탭 복원 검증 |
| 서비스·계정·통합 대화·DM/그룹/채널 | account/provider 원본 ID를 유지하는 서비스와 UI | fixture 검증. 실제 다중 계정·권한 범위 미검증 |
| 읽기·작성·답장·수정·삭제·리액션 | 공식 Provider adapter, 불변 승인 payload, Gmail 답장 대상 사전 고정 | fixture 검증. 실제 전송·수신 확인 필요 |
| 스레드 | Gmail threads, Slack DM/group DM replies 페이지, Discord active guild threads | fixture 검증. Slack 채널 Bot 제한, Discord archived thread 탐색·forum 게시글 생성 미구현 |
| 첨부 관리 | 5 MiB/5개 검증, 공식 업로드, 고정 host 다운로드, 메시지·우측 목록 | fixture 검증. 실제 파일 업로드/다운로드·오류 복구 필요 |
| 읽음·알림 | Gmail UNREAD 사실, Slack/Discord 미확인, 열어둔 대화의 새로운 시각/ID 알림 | DOM 검증. 전체 계정 push/OS 알림/상대방 수신 확인은 제공하지 않음 |
| 검색·필터 | 계정 필터, 로컬 캐시 검색, Gmail 공식 검색·cursor | fixture 검증. 실제 검색 결과·조직 정책 미검증 |
| 참여자·대화 정보·AI | Slack 공식 참여자 ID 페이지, 첨부 목록, Assistant 및 MCP | fixture 검증. 표시 이름 추정 없음. 미지원 Provider 참여자 버튼 비활성화 |
| 대화 탭·분할·복원·모바일 | version 2 metadata, 허용 필드만 복원, 390px/desktop Playwright | 격리 검증. 실제 휴대폰 키보드·터치 환경은 별도 |
| 도메인·저장·중복·순서·paging | domain/DTO/entity 분리, V12, account/conversation/message 복합키, 원본 cursor | 실제 SQLite + fixture. Provider 시간·읽음 미확인 값을 만들지 않음 |
| 동기화·누락 복구 | Gmail full/history checkpoint/reset, Slack HMAC receipt, Discord heartbeat/resume | fixture 검증. 실제 장시간 끊김·제한·이력 복구 미검증; 전체 과거 이력 보장 아님 |
| Web 앱 실행·세션 격리 | 독립 Chromium/X/VNC/profile, 사용자 직접 입력, Guacamole, 제한 AX 관측 | 실제 격리 Chromium·cookie stop/start·원격 화면 검증. 실제 서비스 로그인 상태 보존은 미검증 |
| Web UI 재구성·명령 반영 | AX 역할/이름 기술 조회와 원격 화면 fallback | **미완료**. 로그인한 실제 앱 구조·정책 확인 후 서비스별 정규화/명령 adapter를 결정해야 함. 현재 structuredMessages=false |
| Windows Agent·카카오톡 | 선택적 일반 사용자 Agent, CurrentUserOnly pipe, 등록 SSH 경계, 제한 UIA 조회, 원격 화면 재사용 | 로컬 pipe/임의 invoke 거절 검증. 현재 접근 가능 창 0개. 실제 채팅 추출·자동 입력·원격 장비 연결 **미검증/미완료** |
| Gmail·Slack OAuth/Discord Bot | 공식 인증 adapter, encrypted token, scope capability, Bot만 허용 | **실계정 검증 미완료**. 로컬 Google/Slack Client·Slack Signing Secret 미설정. token refresh/rotation의 실계정 확인 필요 |
| AI 조회·요약·초안·발송 | 공유 서비스의 MCP 읽기·검색·참여자·첨부·전송 요청, untrusted wrapper, confirmation 도구 없음 | 조회 데이터 기반 Assistant 요약·초안. 승인 우회 거절 fixture 통과. 실제 계정 Assistant 전체 시나리오 미검증 |
| 보안·정리 | OWNER/CSRF, OAuth state, Vault, profile UUID, 고정 endpoint/host, attachment 제한, 연결 해제 cascade | fixture·격리 검증. Slack callback 한 곳만 HMAC 인증을 위한 public/CSRF 예외 |
| Docker·운영 | 기존 Compose/볼륨/네트워크 유지, 선택적 외부 Windows Agent | 이미지 build·Compose config·임시 배포 검증. 운영 배포는 수행하지 않음 |
| 문서 | API/DB/architecture/launcher/운영 설정 갱신 | 구현과 제한을 명시. 미검증 기능을 완료로 표시하지 않음 |

## 필수 테스트 14개에 대한 증거 범위

| 번호 | 항목 | 증거/한계 |
| --- | --- | --- |
| 1 | 기존 회귀 | Maven verify/Spotless, Python, Launcher/IDE/Assistant/GitHub 등 JS |
| 2 | 앱 실행·탭 복원 | check-communications.mjs, communications-restore-test.cjs |
| 3 | 연결·해제 | CommunicationIntegrationTest의 mock upstream + 실제 SQLite/보안 |
| 4 | Provider 정규화 | Gmail/Slack/Discord fixture, ReactionNormalizationTest |
| 5 | 송수신·오류 | 불변 승인·1회 실행·UNKNOWN·대상 변경·첨부 검증 fixture; 실계정 아님 |
| 6 | WS 이벤트 | 기존 Workspace WS 경계 재사용, Slack 서명/중복 fixture 및 communications-events DOM; 실제 외부 서비스 이벤트 전달 미검증 |
| 7 | 중복 방지 | DB upsert, 검색·스레드·참여자 페이지 중복 fixture |
| 8 | 만료·재연결 | OAuth state, 최신 credential 재조회·동시 회전·연결 해제/재연결 경합·429/불명확한 실패 fixture, Gmail history 만료, Discord wire resume, Chromium stop/start. 실제 OAuth refresh·회전·서비스 세션 만료 검증 필요 |
| 9 | Chromium·프로필 | 실제 두 프로필 cookie 격리·영속화 fixture |
| 10 | GUI Bridge 시작·종료 | 실제 Web Bridge/Guacamole. Windows GUI 자동 조작은 미검증 |
| 11 | Windows 미연결 | API 오류 경계·로컬 Agent 미접근 상태, dashboard 독립 기동 |
| 12 | AI 무승인 발송 차단 | MCP prepare 전용, confirmation 도구 부재, MCP principal 거절, CSRF/OWNER |
| 13 | 모바일 | 390px Playwright, 기존 responsive DOM; 물리 단말은 미검증 |
| 14 | Compose 배포 | config --quiet 및 격리 컨테이너/새 DB·볼륨; 운영 검증 아님 |

## 실제 환경이 필요한 다음 검증

- Google/Slack OAuth Client·redirect·승인된 테스트 계정과 실제 접근 가능한 대시보드 URL.
- Discord Bot, 승인된 서버/채널 권한·intent와 실제 Gateway 세션.
- 공개 Slack Events callback과 signing secret(비밀값은 서버 설정으로만 입력).
- 카카오톡에 로그인한 대화형 Windows 장비, 등록 SSH 및 RDP/VNC 경로. 현재 로컬 --probe는 APP_NOT_RUNNING_OR_NO_WINDOW/0 nodes다.
- 해당 서비스 정책과 실제 DOM/UIA 구조를 확인한 뒤 UI 재구성 범위와 승인 명령을 구현·검증. 구조를 추측하거나 접근 제한을 우회하지 않음.

실제 계정이 없는 상태에서 fixture 성공을 실연동 성공으로 바꿔 기록하지 않는다. 본문·인증정보·토큰은 검증 보고서에 저장하지 않는다.

최종 로컬 검증: OAuth 경합 수정이 포함된 Docker build에서 Maven verify/Spotless 237개(실패 0·오류 0·건너뜀 9), Python 55개 통과. UI/Chromium 격리 검증과 구분하며 실제 계정·카카오톡 환경의 누락을 대체하지 않는다.

2026-10-10 Chromium 자동 내부 인증 변경 검증: 새 토큰 환경변수 없이 전용 control 볼륨으로 dashboard/browser를 실행해 Playwright 원격 화면 및 UI 검증 통과. 실제 Chromium 프로필 쿠키 격리·중지/재시작, browser 컨테이너 재시작 후 내부 키 유지, dashboard 읽기 전용 마운트 확인. Linux 키 생성/권한/심볼릭 링크 거절 테스트 2개, Java 집중 28개 및 전체 Maven verify/Spotless 238개(실패 0·오류 0·건너뜀 9), 기존 Python 55개 통과. Compose config 통과. 실제 공급자 로그인은 검증하지 않았으며 운영 컨테이너에는 적용하지 않았다.


## 사용자 요청 변경: 원본 앱 화면을 기본으로 사용

이후 요청에 따라 Slack/Discord는 Chromium 웹 화면, 카카오톡은 Wine 앱 화면을 기본 진입점으로 제공한다. 위 최초 요구의 DOM/UIA 재구성 미완료 항목은 원격 화면 기능의 완료 여부와 구분한다. 기존 공식 API 기능은 선택 경로로 유지한다.

Wine 11.0 + 공식 카카오톡 26.8.2.5324의 빈 prefix 설치 후 실제 계정/비밀번호/QR 로그인 화면이 대시보드 Guacamole canvas에 표시됨을 확인했다. Slack/Discord도 실제 사이트 화면과 원격 canvas 연결을 확인했다. 실제 로그인, MFA, 메시지 송수신, 통화, 로컬 첨부파일 전달은 수행하지 않았다. Wine 실행 성공을 해당 기능들의 성공으로 보고하지 않는다.

최종 화면 모드 검증: 최종 dashboard/browser/wine 이미지 조합에서 Playwright로 세 서비스의 실제 로그인 화면 확인. 카카오톡은 컨테이너 재시작 후 같은 prefix 재사용 확인. Wine broker의 무인증 403 및 다른 Provider 실행 409 거절 확인. 기존 수신함 모바일·승인·탭 복원 Playwright, 인증 브라우저 및 Communications DOM 회귀 통과. 최종 Maven verify/Spotless 238개(실패 0·오류 0·건너뜀 9), Python 55개 통과. Compose config 통과. 격리 검증 환경을 정리했으며 운영 배포·실계정 로그인·메시지 전송은 수행하지 않았다.

## Communications UI 리디자인 검증 (2026-10-10)

검증 대상은 통합 사이드바, 기본 닫힘 상세 패널, 채팅/메일 표현, 고정 작성기, 온보딩과 독립 상태 화면이다. Java/API/MCP/OAuth/Bridge 구현은 이 UI 변경에서 수정하지 않았다.

- 최종 Docker 이미지의 Maven verify/Spotless: 238개, 실패 0·오류 0·건너뜀 9. 기존 Python 55개 통과.
- Communications DOM 5종 및 Launcher 회귀 통과. 상세 패널 기본 닫힘, 초안·포커스·탭/분할 복원, 메시지 escaping, 승인 경계, 검색/중복 제거, 이벤트 갱신 후 모바일 목록 유지 포함.
- `node scripts/check-communications-ui.mjs`: 패키지에 포함된 JS/CSS를 실제 Chromium에서 사용. 1920×1080, 1366×768, 390×844별 온보딩·원격 준비 중·연결 오류·목록/검색 없음·메시지 로딩·대화·초안/답장 취소·상세 열기/닫기·승인·권한 오류/재시도·이메일 확인. 390px에서 목록→대화→상세 전환과 높이 480px 축소 후 작성기 노출 확인. 오류 복구 후 이전 대화 오류가 전역 상태에 남지 않음 확인.
- `node scripts/check-communications.mjs`: 실제 격리 DB/OWNER API/Chromium/Guacamole 연결과 기존 검색·참여자·스레드·XSS·모바일 승인·탭/분할 복원 회귀.
- 화면 기록: `artifacts/communications-ui-{1920,1366,390}-{state}.png`. 데스크톱/모바일 온보딩, 대화, 메일, 상세, 오류 스크린샷 직접 검토. 가로 넘침과 pageerror 없음.

Provider 계정/메시지/오류/승인 응답 및 원격 연결 지연은 명시적인 테스트 fixture다. 실제 원격 canvas 연결은 별도 기존 회귀로 확인했다. 실제 Gmail/Slack/Discord 계정 송수신, 물리 휴대폰 키보드, 운영 배포 성공을 의미하지 않는다. 초기 파일 route를 통한 개발 검증과 달리 최종 실행은 로컬 UI override 없이 Docker 패키지로 수행했다.

재현: `tools/deployment/communications-test.ps1`로 격리 fixture를 시작하고 위 두 Playwright 스크립트를 실행한다. 종료 시 같은 스크립트의 `-Cleanup`으로 해당 임시 컨테이너/볼륨만 정리한다. 생성 인증정보는 ignored `.tools`에만 저장하며 출력하지 않는다.
