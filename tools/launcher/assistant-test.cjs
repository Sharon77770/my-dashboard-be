const { JSDOM } = require('jsdom');
const fs = require('node:fs');
const assert = require('node:assert/strict');
const html = fs.readFileSync('src/main/resources/templates/home.html', 'utf8');
const script = fs.readFileSync('src/main/resources/static/js/assistant.js', 'utf8');
const tick = () => new Promise(resolve => setTimeout(resolve, 20));
const connected = { name: 'personal-dashboard', status: 'bearerToken', runtimeStatus: null,
  tools: ['list_calendar_events'], error: '' };

async function fixture(connection, authenticated = true, connectionFailure = false, thinking = false) {
  const dom = new JSDOM(html, { runScripts: 'outside-only', url: 'http://localhost' });
  const w = dom.window, d = w.document, calls = [];
  const state = { connection, authenticated, connectionFailure, thinking };
  w.workspaceInitial = { devices: [{ id: 'local', rootPath: '/tmp/fixture' }] };
  w.setInterval = () => 0;
  w.WorkspaceAssistantRuntime = { toast() {}, async api(path, method, body) {
    if (path === '/assistant/events?after=0') return [];
    if (path === '/assistant/jobs/thinking') return { id: 'thinking', state: 'SUCCEEDED', events: [
      { assistant: { kind: 'item', item: { type: 'agentMessage', text: 'fixture answer' } } }
    ], result: { assistant: { thread: { id: 'fixture', turns: [] } } } };
    assert.equal(method, 'POST');
    calls.push(body.action);
    if (body.action === 'codex-run' && state.expired) return { id: 'expired', state: 'FAILED', errorStatus: 401, error: 'Codex 로그인이 만료되었습니다.' };
    if (body.action === 'codex-connections' && state.connectionFailure) throw Error('MCP unavailable');
    if (body.action === 'codex-run' && state.thinking) return { id: 'thinking', state: 'RUNNING', events: [
      { assistant: { kind: 'started' } },
      { assistant: { kind: 'item', item: { type: 'reasoning', text: 'private reasoning summary' } } }
    ] };
    const result = body.action === 'codex-account' ? { assistant: { authenticated: state.authenticated } }
      : body.action === 'codex-rate-limits' ? { assistant: { rateLimits: [{ name: 'Codex', windowDurationMins: 300, usedPercent: 25, resetsAt: 1730947200 }] } }
      : body.action === 'codex-connections' ? { assistant: { connections: state.connection ? [state.connection] : [] } }
      : body.action === 'codex-run' ? { assistant: { thread: { id: 'fixture', turns: [{ items: [{ type: 'agentMessage', text: 'fixture answer' }] }] } } }
      : {};
    return { id: String(calls.length), state: 'SUCCEEDED', result };
  } };
  w.eval(script);
  assert.equal(d.querySelector('#assistant-launcher'), null);
  d.querySelector('#assistant').classList.add('active');
  w.dispatchEvent(new w.CustomEvent('workspace:view', { detail: { id: 'assistant' } }));
  await tick();
  return { dom, w, d, calls, state };
}

(async () => {
  for (const runtimeStatus of [null, undefined, 'connected']) {
    const f = await fixture({ ...connected, runtimeStatus });
    try {
      assert.match(f.d.querySelector('#assistant-account-status').textContent, /연결됨/);
      assert.match(f.d.querySelector('#assistant-limits-text').textContent, /5시간 잔여 75%/);
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
  for (const connection of [null, { ...connected, tools: [] }, { ...connected, runtimeStatus: 'failed' },
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
  console.log('PASS assistant: connection recovery, login, thinking progress, and completed answer');
})().catch(error => { console.error(error); process.exitCode = 1; });
