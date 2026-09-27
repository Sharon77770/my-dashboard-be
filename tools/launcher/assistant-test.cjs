const { JSDOM } = require('jsdom');
const fs = require('node:fs');
const assert = require('node:assert/strict');
const html = fs.readFileSync('src/main/resources/templates/home.html', 'utf8');
const script = fs.readFileSync('src/main/resources/static/js/assistant.js', 'utf8');
const tick = () => new Promise(resolve => setTimeout(resolve, 20));
const connected = { name: 'personal-dashboard', status: 'bearerToken', runtimeStatus: null,
  tools: ['list_calendar_events'], error: '' };

async function fixture(connection, authenticated = true, connectionFailure = false) {
  const dom = new JSDOM(html, { runScripts: 'outside-only', url: 'http://localhost' });
  const w = dom.window, d = w.document, calls = [];
  const state = { connection, authenticated, connectionFailure };
  w.workspaceInitial = { devices: [{ id: 'local', rootPath: '/tmp/fixture' }] };
  w.setInterval = () => 0;
  w.WorkspaceAssistantRuntime = { toast() {}, async api(path, method, body) {
    if (path === '/assistant/events?after=0') return [];
    assert.equal(method, 'POST');
    calls.push(body.action);
    if (body.action === 'codex-connections' && state.connectionFailure) throw Error('MCP unavailable');
    const result = body.action === 'codex-account' ? { assistant: { authenticated: state.authenticated } }
      : body.action === 'codex-connections' ? { assistant: { connections: state.connection ? [state.connection] : [] } }
      : body.action === 'codex-run' ? { assistant: { thread: { id: 'fixture', turns: [{ items: [{ type: 'agentMessage', text: 'fixture answer' }] }] } } }
      : {};
    return { id: String(calls.length), state: 'SUCCEEDED', result };
  } };
  w.eval(script);
  d.querySelector('#assistant-launcher').click();
  await tick();
  return { dom, w, d, calls, state };
}

(async () => {
  for (const runtimeStatus of [null, undefined, 'connected']) {
    const f = await fixture({ ...connected, runtimeStatus });
    try {
      assert.match(f.d.querySelector('#assistant-account-status').textContent, /연결됨/);
      f.d.querySelector('#assistant-prompt').value = '10월 일정 설명해줘';
      f.d.querySelector('#assistant-form').dispatchEvent(new f.w.Event('submit', { cancelable: true }));
      await tick();
      assert.ok(f.calls.includes('codex-run'));
    } finally { f.dom.window.close(); }
  }
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
  const loggedOut = await fixture(null, false);
  try {
    assert.equal(loggedOut.d.querySelector('#assistant-login').hidden, false);
    assert.equal(loggedOut.d.querySelector('#assistant-login').textContent, 'Codex 로그인');
    assert.ok(!loggedOut.calls.includes('codex-connections'));
  } finally { loggedOut.dom.window.close(); }
  const unavailable = await fixture(null, true, true);
  try { assert.equal(unavailable.d.querySelector('#assistant-login').textContent, 'MCP 연결 복구'); }
  finally { unavailable.dom.window.close(); }
  console.log('PASS assistant: null/omitted/connected state, failure blocking, single-setup recovery, login and discovery errors');
})().catch(error => { console.error(error); process.exitCode = 1; });
