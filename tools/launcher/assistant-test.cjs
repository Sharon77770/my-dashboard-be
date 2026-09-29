const { JSDOM } = require('jsdom');
const fs = require('node:fs');
const assert = require('node:assert/strict');
const html = fs.readFileSync('src/main/resources/templates/home.html', 'utf8');
const script = fs.readFileSync('src/main/resources/static/js/assistant.js', 'utf8');
const markdownScript = fs.readFileSync('src/main/resources/static/js/assistant-markdown.js', 'utf8');
const answer = '# fixture answer\n\n**중요** [문서](https://example.com)\n\n- 첫째\n- 둘째\n\n```js\nconst value = 1;\n```\n\n| 이름 | 값 |\n| --- | --- |\n| A | 1 |';
const tick = () => new Promise(resolve => setTimeout(resolve, 20));
const connected = { name: 'personal-dashboard', status: 'bearerToken', runtimeStatus: null,
  tools: ['list_apps', 'list_calendar_events', 'create_calendar_event', 'update_calendar_event',
    'delete_calendar_event', 'list_notes', 'read_note', 'create_note', 'append_note',
    'update_note_metadata', 'replace_note_text', 'delete_note', 'github.get_repository',
    'github.update_repository', 'github.update_release', 'github.delete_repository',
    'github.delete_release'], error: '' };

async function fixture(connection, authenticated = true, connectionFailure = false, thinking = false) {
  const dom = new JSDOM(html, { runScripts: 'outside-only', url: 'http://localhost' });
  const w = dom.window, d = w.document, calls = [], requests = [];
  const state = { connection, authenticated, connectionFailure, thinking, threads: [] };
  w.workspaceInitial = { devices: [{ id: 'local', rootPath: '/tmp/fixture' }] };
  w.setInterval = () => 0;
  w.WorkspaceAssistantRuntime = { toast() {}, async api(path, method, body) {
    if (path === '/assistant/events?after=0') return [];
    if (path === '/assistant/jobs/thinking') return { id: 'thinking', state: 'SUCCEEDED', events: [
      { assistant: { kind: 'item', item: { type: 'agentMessage', text: answer } } }
    ], result: { assistant: { thread: { id: 'fixture', turns: [] } } } };
    if (path === '/assistant/jobs/harness') return { id: 'harness', state: 'SUCCEEDED', events: [
      { assistant: { kind: 'item', item: { id: 'answer', type: 'agentMessage', text: '조회 결과입니다.' } } }
    ], result: { assistant: { thread: { id: 'fixture', turns: [{ items: [
      { id: 'notice', type: 'agentMessage', text: '요청 이해: 앱 목록 조회 / 사용할 MCP: list_apps' },
      { id: 'tool', type: 'mcpToolCall', server: 'personal-dashboard', tool: 'list_apps', status: 'completed' },
      { id: 'retry', type: 'mcpToolCall', server: 'personal-dashboard', tool: 'list_apps', status: 'failed', arguments: 'hidden credential' },
      { id: 'answer', type: 'agentMessage', text: '조회 결과입니다.' }
    ] }] } } } };
    assert.equal(method, 'POST');
    calls.push(body.action);
    requests.push(body);
    if (body.action === 'codex-run' && state.expired) return { id: 'expired', state: 'FAILED', errorStatus: 401, error: 'Codex 로그인이 만료되었습니다.' };
    if (body.action === 'codex-logout') state.authenticated = false;
    if (body.action === 'codex-thread-delete') {
      if (body.args.threadId === state.deleteFailure) return { id: 'delete-failed', state: 'FAILED', error: '삭제 실패' };
      state.threads = state.threads.filter(item => item.id !== body.args.threadId);
    }
    if (body.action === 'codex-connections' && state.connectionFailure) throw Error('MCP unavailable');
    if (body.action === 'codex-run' && state.thinking) return { id: 'thinking', state: 'RUNNING', events: [
      { assistant: { kind: 'started' } },
      { assistant: { kind: 'item', item: { type: 'reasoning', text: 'private reasoning summary' } } }
    ] };
    if (body.action === 'codex-run' && state.harness) return { id: 'harness', state: 'RUNNING', events: [
      { assistant: { kind: 'item', item: { id: 'notice', type: 'agentMessage', text: '요청 이해: 앱 목록 조회 / 사용할 MCP: list_apps' } } },
      { assistant: { kind: 'item', item: { id: 'tool', type: 'mcpToolCall', server: 'personal-dashboard', tool: 'list_apps', status: 'completed' } } }
    ] };
    const result = body.action === 'codex-account' ? { assistant: { authenticated: state.authenticated } }
      : body.action === 'codex-rate-limits' ? { assistant: { rateLimits: [{ name: 'Codex', windowDurationMins: 300, usedPercent: 25, resetsAt: 1730947200 }] } }
      : body.action === 'codex-connections' ? { assistant: { connections: state.connection ? [state.connection] : [] } }
      : body.action === 'codex-threads' ? { assistant: { threads: state.threads.slice(body.args.cursor ? 25 : 0, body.args.cursor ? 50 : 25), nextCursor: !body.args.cursor && state.threads.length > 25 ? 'next' : null } }
      : body.action === 'codex-thread-read' ? { assistant: { thread: { id: body.args.threadId, turns: [{ items: [
        { type: 'userMessage', text: '10월 일정 설명해줘' },
        { type: 'mcpToolCall', server: 'personal-dashboard', tool: 'list_calendar_events', status: 'completed' },
        { type: 'agentMessage', text: answer }
      ] }] } } }
      : body.action === 'codex-run' ? { assistant: { thread: { id: 'fixture', turns: [{ items: [
        { type: 'mcpToolCall', server: 'personal-dashboard', tool: 'list_calendar_events', status: 'completed' },
        { type: 'agentMessage', text: answer }
      ] }] } } }
      : {};
    return { id: String(calls.length), state: 'SUCCEEDED', result };
  } };
  w.eval(fs.readFileSync('src/main/resources/static/js/ui.js', 'utf8'));
  w.eval(markdownScript);
  w.eval(script);
  assert.equal(d.querySelector('#assistant-launcher'), null);
  d.querySelector('#assistant').classList.add('active');
  w.dispatchEvent(new w.CustomEvent('workspace:view', { detail: { id: 'assistant' } }));
  await tick();
  return { dom, w, d, calls, requests, state };
}

(async () => {
  for (const runtimeStatus of [null, undefined, 'connected']) {
    const f = await fixture({ ...connected, runtimeStatus });
    try {
      assert.match(f.d.querySelector('#assistant-account-status').textContent, /연결됨/);
      assert.match(f.d.querySelector('#assistant-limits-text').textContent, /5시간/);
      assert.match(f.d.querySelector('#assistant-limits-text').textContent, /75%/);
      assert.equal(f.d.querySelector('#assistant-limits-text [role="progressbar"]').getAttribute('aria-valuenow'), '75');
      assert.ok(f.d.querySelector('.assistant-top-shortcut[data-view="assistant"]'));
      f.d.querySelector('[data-assistant-draft="오늘 일정 보여줘"]').click();
      assert.equal(f.d.querySelector('#assistant-prompt').value, '오늘 일정 보여줘');
      assert.ok(!f.calls.includes('codex-run'));
      f.d.querySelector('#assistant-prompt').value = '10월 일정 설명해줘';
      f.d.querySelector('#assistant-form').dispatchEvent(new f.w.Event('submit', { cancelable: true }));
      await tick();
      assert.ok(f.calls.includes('codex-run'));
      f.d.querySelector('#assistant').classList.remove('active');
      f.w.dispatchEvent(new f.w.CustomEvent('workspace:view', { detail: { id: 'calendar' } }));
      f.d.querySelector('#assistant').classList.add('active');
      f.w.dispatchEvent(new f.w.CustomEvent('workspace:view', { detail: { id: 'assistant' } }));
      await tick();
      assert.match(f.d.querySelector('#assistant-messages').textContent, /fixture answer/);
      assert.equal(f.d.querySelector('.assistant-message[data-role="assistant"] h1').textContent, 'fixture answer');
      assert.equal(f.d.querySelector('.assistant-message[data-role="assistant"] strong').textContent, '중요');
      assert.equal(f.d.querySelector('.assistant-message[data-role="assistant"] a').href, 'https://example.com/');
      assert.equal(f.d.querySelector('.assistant-message[data-role="assistant"] li').textContent, '첫째');
      assert.equal(f.d.querySelector('.assistant-message[data-role="assistant"] pre code').textContent, 'const value = 1;');
      assert.equal(f.d.querySelector('.assistant-message[data-role="assistant"] table td').textContent, 'A');
      assert.match(f.d.querySelector('.assistant-tool-report').textContent, /personal-dashboard \/ list_calendar_events/);
      assert.equal(f.calls.filter(action => action === 'codex-run').length, 1);
    } finally { f.dom.window.close(); }
  }
  const thinking = await fixture(connected, true, false, true);
  try {
    thinking.d.querySelector('#assistant-prompt').value = '오늘 일정 알려줘';
    thinking.d.querySelector('#assistant-form').dispatchEvent(new thinking.w.Event('submit', { cancelable: true }));
    await tick();
    assert.match(thinking.d.querySelector('#assistant-messages').textContent, /Codex가 생각하고 있어요/);
    assert.equal(thinking.d.querySelectorAll('.assistant-progress-dots span').length, 3);
    assert.doesNotMatch(thinking.d.querySelector('#assistant-messages').textContent, /private reasoning summary/);
    await new Promise(resolve => setTimeout(resolve, 500));
    assert.match(thinking.d.querySelector('#assistant-messages').textContent, /fixture answer/);
    assert.equal(thinking.d.querySelector('.assistant-progress'), null);
  } finally { thinking.dom.window.close(); }
  const harness = await fixture(connected);
  try {
    harness.state.harness = true;
    harness.d.querySelector('#assistant-prompt').value = '등록된 앱 보여줘';
    harness.d.querySelector('#assistant-form').dispatchEvent(new harness.w.Event('submit', { cancelable: true }));
    await tick();
    assert.match(harness.d.querySelector('#assistant-messages').textContent, /요청 이해: 앱 목록 조회/);
    await new Promise(resolve => setTimeout(resolve, 500));
    assert.match(harness.d.querySelector('.assistant-message-notices').textContent, /사용할 MCP: list_apps/);
    assert.match(harness.d.querySelector('.assistant-tool-report').textContent, /personal-dashboard \/ list_apps/);
    assert.equal(harness.d.querySelectorAll('.assistant-tool-report li').length, 2);
    assert.match(harness.d.querySelector('.assistant-tool-report').textContent, /실패/);
    assert.doesNotMatch(harness.d.querySelector('#assistant-messages').textContent, /hidden credential/);
  } finally { harness.dom.window.close(); }
  for (const connection of [null, { ...connected, tools: [] }, { ...connected, tools: ['list_calendar_events'] },
    { ...connected, runtimeStatus: 'failed' },
    { ...connected, error: '도구 조회 실패' }]) {
    const f = await fixture(connection);
    try {
      assert.equal(f.d.querySelector('#assistant-login').textContent, 'MCP 연결 복구');
      f.d.querySelector('#assistant-prompt').value = '10월 일정 설명해줘';
      f.d.querySelector('#assistant-form').dispatchEvent(new f.w.Event('submit', { cancelable: true }));
      await tick();
      assert.ok(!f.calls.includes('codex-run'));
      f.state.connection = connected;
      const setups = f.calls.filter(action => action === 'setup').length;
      f.d.querySelector('#assistant-login').click();
      await tick();
      assert.equal(f.calls.filter(action => action === 'setup').length, setups + 1);
      assert.match(f.d.querySelector('#assistant-account-status').textContent, /연결됨/);
    } finally { f.dom.window.close(); }
  }
  const expired = await fixture(connected);
  try {
    expired.state.expired = true;
    expired.d.querySelector('#assistant-prompt').value = '등록된 앱을 보여줘';
    expired.d.querySelector('#assistant-form').dispatchEvent(new expired.w.Event('submit', { cancelable: true }));
    await tick();
    assert.equal(expired.d.querySelector('#assistant-login').hidden, false);
    assert.equal(expired.d.querySelector('#assistant-login').textContent, 'Codex 로그인');
    assert.match(expired.d.querySelector('#assistant-account-status').textContent, /재로그인/);
    assert.match(expired.d.querySelector('#assistant-status').textContent, /만료/);
  } finally { expired.dom.window.close(); }
  const loggedOut = await fixture(null, false);
  try {
    assert.equal(loggedOut.d.querySelector('#assistant-login').hidden, false);
    assert.equal(loggedOut.d.querySelector('#assistant-login').textContent, 'Codex 로그인');
    assert.ok(!loggedOut.calls.includes('codex-connections'));
  } finally { loggedOut.dom.window.close(); }
  const unavailable = await fixture(null, true, true);
  try { assert.equal(unavailable.d.querySelector('#assistant-login').textContent, 'MCP 연결 복구'); }
  finally { unavailable.dom.window.close(); }
  const history = await fixture(connected);
  try {
    history.state.threads = [{ id: 'older', name: '지난 대화', preview: '지난 대화', updatedAt: 1730947200 }];
    history.d.querySelector('#assistant-history-refresh').click();
    await tick();
    assert.match(history.d.querySelector('#assistant-sessions').textContent, /지난 대화/);
    history.d.querySelector('[data-thread-id="older"]').click();
    await tick();
    assert.match(history.d.querySelector('#assistant-messages').textContent, /fixture answer/);
    assert.match(history.d.querySelector('.assistant-tool-report').textContent, /list_calendar_events/);
    assert.equal(history.d.querySelector('#assistant-chat-title').textContent, '지난 대화');
    history.d.querySelector('#assistant-header-settings').click();
    assert.equal(history.d.querySelector('#assistant-settings').hasAttribute('open'), true);
    history.d.querySelector('#assistant-settings-logout').click();
    await tick();
    assert.ok(history.calls.includes('codex-logout'));
    assert.equal(history.d.querySelector('#assistant-settings-login').hidden, false);
    assert.equal(history.d.querySelector('#assistant-messages .assistant-welcome') !== null, true);
  } finally { history.dom.window.close(); }
  const deletion = await fixture(connected);
  try {
    deletion.state.threads = Array.from({ length: 26 }, (_, index) => ({
      id: `session-${index}`, name: `대화 ${index}`, updatedAt: 1730947200
    }));
    deletion.d.querySelector('#assistant-delete-open').click();
    await tick();
    assert.equal(deletion.d.querySelector('#assistant-delete-dialog').hasAttribute('open'), true);
    assert.equal(deletion.d.querySelectorAll('#assistant-delete-list input').length, 25);
    deletion.d.querySelector('#assistant-delete-more').click();
    await tick();
    assert.equal(deletion.d.querySelectorAll('#assistant-delete-list input').length, 26);
    for (const id of ['session-0', 'session-25']) {
      const checkbox = deletion.d.querySelector(`#assistant-delete-list input[value="${id}"]`);
      checkbox.checked = true;
      checkbox.dispatchEvent(new deletion.w.Event('change', { bubbles: true }));
    }
    assert.equal(deletion.d.querySelector('#assistant-delete-count').textContent, '2개 선택');
    deletion.d.querySelector('#assistant-delete-selected').click();
    await tick();
    assert.equal(deletion.state.threads.length, 24);
    assert.equal(deletion.d.querySelector('#assistant-delete-dialog').hasAttribute('open'), false);
    assert.equal(deletion.requests.filter(item => item.action === 'codex-thread-delete').length, 2);

    deletion.d.querySelector('#assistant-history-refresh').click();
    await tick();
    deletion.d.querySelector('[data-thread-id="session-1"]').click();
    await tick();
    deletion.w.confirm = () => true;
    assert.equal(deletion.d.querySelector('#assistant-delete-current').disabled, false);
    deletion.d.querySelector('#assistant-delete-current').click();
    await tick();
    assert.equal(deletion.d.querySelector('#assistant-chat-title').textContent, '새 채팅');
    assert.equal(deletion.w.sessionStorage.getItem('dashboard-assistant-thread-v1'), '');
    assert.equal(deletion.state.threads.some(item => item.id === 'session-1'), false);
  } finally { deletion.dom.window.close(); }
  const failedDeletion = await fixture(connected);
  try {
    failedDeletion.state.threads = [{ id: 'keep', name: '보존할 대화', updatedAt: 1730947200 }];
    failedDeletion.state.deleteFailure = 'keep';
    failedDeletion.d.querySelector('#assistant-delete-open').click();
    await tick();
    const checkbox = failedDeletion.d.querySelector('#assistant-delete-list input');
    checkbox.checked = true;
    checkbox.dispatchEvent(new failedDeletion.w.Event('change', { bubbles: true }));
    failedDeletion.d.querySelector('#assistant-delete-selected').click();
    await tick();
    assert.equal(failedDeletion.d.querySelector('#assistant-delete-dialog').hasAttribute('open'), true);
    assert.equal(failedDeletion.d.querySelector('#assistant-delete-list input').checked, true);
    assert.match(failedDeletion.d.querySelector('#assistant-delete-status').textContent, /삭제 실패/);
    assert.equal(failedDeletion.state.threads.length, 1);
  } finally { failedDeletion.dom.window.close(); }
  const unsafe = await fixture(connected);
  try {
    const target = unsafe.d.createElement('div');
    unsafe.w.AssistantMarkdown.render(target, '**safe** <img src=x onerror=alert(1)> [bad](javascript:alert(1))');
    assert.equal(target.querySelector('strong').textContent, 'safe');
    assert.equal(target.querySelector('img'), null);
    assert.equal(target.querySelector('a'), null);
    assert.match(target.textContent, /<img src=x/);
  } finally { unsafe.dom.window.close(); }
  const attached = await fixture(connected);
  try {
    const file = new attached.w.File(['image'], 'photo.png', { type: 'image/png' });
    const input = attached.d.querySelector('#assistant-file');
    Object.defineProperty(input, 'files', { configurable: true, value: [file] });
    input.dispatchEvent(new attached.w.Event('change'));
    await tick();
    assert.match(attached.d.querySelector('#assistant-attachments').textContent, /photo.png/);
    attached.d.querySelector('#assistant-form').dispatchEvent(new attached.w.Event('submit', { cancelable: true }));
    await tick();
    const request = attached.requests.find(item => item.action === 'codex-run');
    assert.equal(request.args.context[0].kind, 'image');
    assert.match(request.args.context[0].dataUrl, /^data:image\/png;base64,/);
    assert.equal(attached.d.querySelector('#assistant-attachments').textContent, '');
  } finally { attached.dom.window.close(); }
  console.log('PASS assistant: connection, history, settings logout, attachment, and answer');
})().catch(error => { console.error(error); process.exitCode = 1; });
