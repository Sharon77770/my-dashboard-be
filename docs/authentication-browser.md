# 인증 브라우저

계정·시스템 메뉴 또는 앱 목록의 **인증 브라우저**에서 대시보드 서버 Chromium에 연결한다. Google 계정, GitHub 계정, ChatGPT 계정 버튼으로 미리 로그인할 수 있다. 공급자 화면에서 계정 선택·비밀번호·다중 인증·동의는 사용자가 직접 진행한다. 로그인 쿠키는 기존 `browser-profile` 볼륨에 남고 창 닫기나 대시보드 로그아웃은 원격 화면 연결만 종료한다. 공급자 만료·로그아웃·볼륨 삭제 시에는 다시 로그인해야 한다.

## 앱별 사용

- IDE Codex, IDE GitHub, 장비 Codex/GitHub, AI 비서, GitHub 앱: 기존 로그인 작업을 시작한 뒤 **대시보드에서 인증**을 누른다. 해당 작업의 기기 코드를 원격 공급자 화면에 입력하고 승인을 마친다. 원래 앱의 기존 polling과 계정 조회가 CLI 인증 완료를 판단한다. 서버 브라우저에 로그인해 두는 것만으로 모든 SSH 장비의 CLI가 로그인되지는 않는다.
- 등록된 웹앱: 앱 행의 **인증 브라우저**로 웹앱을 처음부터 서버 Chromium에서 연다. Google/GitHub 로그인 및 OAuth callback은 같은 브라우저 탭과 쿠키 세션에서 진행한다. CLIENT/REMOTE 실행 설정과 무관하게 이 버튼은 서버 Chromium을 사용한다. 다른 PC에서 시작한 OAuth state나 쿠키를 복사하지 않는다.
- Google: 현재 대시보드의 달력·클라우드 드라이브는 자체 데이터 앱이며 Google API 연결 기능이 아니다. Google 계정 로그인과 등록 웹앱의 Google 인증을 지원한다.

창 위에 현재 인증 코드를 표시하고 화면 다시 연결, 마우스·터치·키보드, 한글·텍스트 전송을 제공한다. 닫으면 진행하던 앱으로 돌아간다. 코드와 전송 텍스트는 localStorage/sessionStorage, DB, 로그에 저장하지 않는다. 원격 입력은 전송 직후 지운다. 인증 성공을 브라우저 연결 성공과 혼동하지 않는다.

## 경계 및 운영

`AuthenticationBrowserController -> AuthenticationBrowserService -> RuntimeService -> BrowserAdapter`가 기존 서버 Chromium과 Guacamole VNC 연결을 사용한다. OWNER 및 CSRF가 필요하고 원격 핸들은 로그인 세션 ID에 귀속된다. 종료는 기존 `/api/v1/sessions/{id}` 계약을 사용한다. `/ws/runtime/{id}`의 동일 origin과 세션 소유권 검사를 유지한다. 대시보드 로그아웃/세션 만료 시 VNC 연결이 닫힌다. 기존 최대 12개 런타임 제한을 공유한다.

직접 인증 링크는 HTTPS와 정확한 호스트(`accounts.google.com`, `github.com`, `auth.openai.com`, `chatgpt.com`)를 검사한다. 임의 호스트는 앱 등록 후 해당 applicationId로만 열 수 있다. CDP/VNC는 기존 Compose loopback 설정을 유지하고 외부에 새 포트를 공개하지 않는다. 비밀번호·쿠키·OAuth 토큰을 API 응답으로 추출하지 않는다. 인증 화면과 로그인 상태는 개인 대시보드 OWNER가 공유하므로 대시보드 접근 자체가 계정 사용 권한을 갖는다. 공용 PC에서 작업을 마치면 대시보드에서도 로그아웃한다.

기존 `browser-profile`을 유지한다. 브라우저 설정의 CLIENT/REMOTE와 독립적이며 신규 브라우저 프로필이나 자격증명 테이블을 만들지 않는다. MFA·보안 키·조직 정책·공급자의 브라우저 차단을 우회하지 않는다. 실제 공급자 인증은 계정 정책에 따라 추가 조작 또는 다른 신뢰할 수 있는 장비가 필요할 수 있다.

## 공급자 참고

- [Codex 인증](https://learn.chatgpt.com/docs/auth): 기존 기기 코드 인증을 유지한다.
- [GitHub 기기 인증](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#device-flow): 브라우저 승인 뒤 원래 CLI가 결과를 받는다.
- [Google OAuth 정책](https://developers.google.com/identity/protocols/oauth2/policies): 인증 문서를 iframe에 삽입하지 않고 서버의 완전한 Chromium 화면을 원격 조작한다. 공급자 허용 여부를 보장하거나 사용자 에이전트를 위장하지 않는다.

## 검증 및 배포

```powershell
docker build -t my-dashboard-auth-check:local .
docker build -t my-dashboard-auth-browser-check:local docker/browser
powershell -File tools/deployment/authentication-browser-test.ps1
node scripts/check-authentication-browser.mjs
powershell -File tools/deployment/authentication-browser-test.ps1 -Cleanup
```

테스트 준비 스크립트는 임의 계정의 별도 대시보드·Chromium·guacd와 테스트 프로필을 생성한다. 운영 `.env`나 프로필을 읽지 않는다. 검증 포트는 127.0.0.1의 18198/19298만 사용한다. 출력 이미지는 `artifacts/authentication-browser-desktop.png`, `artifacts/authentication-browser-mobile.png`이다. 실제 공급자 인증을 수행하지 않고 HTTP 가상 로그인 쿠키와 VNC 영어·한글 입력, 창 닫기/재연결, 브라우저 컨테이너 재시작 후 프로필 보존을 확인한다.

Chromium 종료 시 loopback CDP의 `Browser.close`로 정상 종료를 요청하고 X 서버보다 브라우저가 먼저 저장을 마치도록 기다린다. 무응답 시에만 제한된 대기 후 종료 신호로 정리한다. 이 순서는 빠른 재시작 직전 생성한 쿠키가 손실되는 회귀를 막는다. 창은 처음에 최대화하고 작은 화면에서는 화면 확대/맞춤을 제공한다. 원격 텍스트는 Unicode 키 입력으로 전송해 클립보드에 인증 정보를 남기지 않는다.

운영에 반영할 때는 dashboard와 browser 이미지를 모두 빌드해야 한다. 기존 Compose의 browser-profile 볼륨을 보존한다. 배포 후 실제 공급자 계정 로그인·MFA·기기 코드 완료는 별도 검증 항목이며 가상 인증 결과로 대체하지 않는다.

Communications 격리 Chromium은 환경변수 인증값 없이 전용 control 볼륨의 자동 생성 키로 내부 연결한다. dashboard는 읽기 전용, browser는 쓰기 권한을 가지며 일반 공유 브라우저 프로필과 인증키 저장소를 분리한다. 사용자 서비스 로그인은 대시보드 원격 화면에서 직접 수행하고 해당 프로필에 보존한다.
