# 실시간 화면 갱신

## 갱신 경계

브라우저는 로그인 후 `/ws/workspace`에 읽기 전용 WebSocket 하나를 연결한다. 서버는 변경된 영역과 해당 로그인 세션의 작업 ID만 전달한다. 브라우저는 기존 REST API로 최신 상태를 읽고 변경된 DOM만 반영한다. WebSocket에는 데이터 본문, 명령, 인증 정보, 로그 본문을 보내지 않는다.

- 성공한 REST 변경과 MCP 요청 완료는 관련 영역의 재조회를 알린다. MCP는 여러 도메인을 다루므로 넓은 범위의 무효화 신호를 보낸다. MCP 응답 자체의 성공 판정이나 쓰기 실행을 대신하지 않는다.
- Codex/Studio 작업 이벤트·완료·실패·취소는 소유 세션에만 작업 ID를 알린다. AI 비서, 코드 에디터, 장비 Codex, 장비 로그, GitHub 로그인 화면은 이 알림으로 작업 결과를 조회한다. 연결 중 3초, 연결되지 않았을 때 기존 250~700ms 간격을 누락 복구용으로 유지한다.
- 장비 CPU·RAM, GitHub 등 외부 상태는 외부 서버가 직접 push하는 구조가 아니다. 서버의 20초 heartbeat에 맞춰 현재 화면과 홈 위젯의 기존 조회 API를 호출한다. 숨겨진 브라우저 탭에서는 화면 조회를 미루고, 다시 보이면 최신 상태를 읽는다.
- 재접속, 서버 재시작(epoch 변경), revision 누락 시 전체 조회 대상을 무효화한다. 연결 실패 시 지수 backoff(최대 30초 + jitter), 연결되지 않았을 때 60초 간격 조회를 사용한다. 수동 새로고침도 유지한다.

## 화면과 편집 상태

`live-dom.js`는 신뢰 가능한 앱 템플릿을 stable key로 비교한다. 기존 노드·이벤트 위임을 유지하고, 추가·삭제·텍스트·속성 변경만 적용한다. 열린 details, 스크롤, 포커스, 입력값, form, contenteditable, canvas와 iframe은 유지한다. 템플릿의 외부 값은 기존 escape 경로를 사용한다. 변경된 텍스트와 새 항목은 짧은 opacity 전환을 사용하고 모션 축소 설정에서는 생략한다.

홈·장비·클립·앱 목록, 일정/시간표, 서비스 목록과 상태, Telemetry 분석, GitHub 목록, 메모 트리, 드라이브 목록, DB 연결 목록을 자동 갱신한다. Telemetry의 첫 수신도 분석 화면에 자동 반영한다. GitHub의 열린 상세·작성 폼, 드라이브 파일 편집/업로드, 메모 본문, SQL 편집기는 자동 갱신으로 다시 만들지 않는다. 메모가 다른 곳에서 바뀌거나 삭제되면 기존 편집 내용과 revision을 유지하고 안내한다. SQL 실행 상태 조회는 기존 최소 영역 갱신을 사용한다.

서버 알림은 200ms, 브라우저 무효화는 120ms 단위로 합친다. 화면 조회는 180ms 단위로 묶어 직렬화하고, 사용자 foreground 요청이 끝난 뒤 실행한다. foreground 요청 중에도 사용자 상호작용은 허용하며 상단의 작은 로딩 안내만 표시한다. 백그라운드 조회는 이 안내를 띄우지 않는다. 실패 시 기존 화면을 유지하고 다음 알림/heartbeat에서 재시도한다. 상단에 연결·재연결·재시도 상태를 표시한다.

병역 캘린더도 현재 화면 갱신에 포함한다. 병역 쓰기는 `military`, `calendar`를 함께 무효화한다. 복무율·카운트다운은 서버 시간 구간을 브라우저에서 매초 보간하며 매초 HTTP 요청을 보내지 않는다. 서울 자정에 집계를 다시 조회하고, 화면 이탈 시 타이머를 정리한다. 월·선택 날짜·열린 안내·폼 초안을 보존하고 이전 GET 응답이 저장 결과를 되돌리지 않도록 generation을 검사한다.

## 보안과 운영

기존 OWNER 로그인 세션과 기본 same-origin WebSocket handshake 검사를 사용한다. 로그아웃·세션 만료/교체 시 연결을 종료한다. 전역 64개, 세션당 8개 연결 제한과 송신 버퍼 제한을 둔다. 수신 명령은 허용하지 않는다. reverse proxy는 기존 `/ws/runtime/**`와 함께 `/ws/workspace`의 Upgrade도 Spring으로 전달해야 한다. 실제 운영 proxy를 거친 연결은 별도 검증 대상이다.

Spring의 동일 출처 기본값과 동시 송신 경계는 [Spring WebSocket 서버 문서](https://docs.spring.io/spring-framework/reference/web/websocket/server.html), 페이지 이탈/복귀 연결 수명은 [MDN WebSocket 클라이언트 문서](https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API/Writing_WebSocket_client_applications)를 참고했다.

## 검증

- `WorkspaceEventsTest`: 중복 알림 합치기, 세션별 작업 ID 분리, 성공한 변경만 알림, DB 연결 테스트의 반복 무효화 방지.
- `WorkspaceRealtimeIntegrationTest`: 실제 HTTP 로그인과 WebSocket Upgrade, REST 변경 수신, 로그아웃 종료, 익명/다른 Origin 거부, 서로 다른 로그인 세션 작업 분리, 수신 명령 거부.
- `tools/launcher/realtime-test.cjs`: DOM identity, 입력/포커스/선택/스크롤/details, 재접속, epoch/revision 복구, hidden/pagehide/pageshow, 세션 만료.
- `tools/launcher/telemetry-live-test.cjs`: 첫 수신 전환, 조용한 수치 갱신, 차트/메뉴 유지, 지연 응답과 가이드/작성 폼 보호.
- 기존 launcher/planner/services/notes/cloud/GitHub/database-poll/loading 테스트를 함께 실행한다. DOM/CSS 테스트는 실제 브라우저 렌더링·모바일 터치 검증과 구분한다.
