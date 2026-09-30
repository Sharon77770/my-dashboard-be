"""Linux smoke: isolated dashboard DB + real Codex MCP discovery and calendar call.

Run inside the built dashboard image with this file, studio resources and a Linux
Codex binary mounted read-only. By default uses no existing accounts or model turns.
With --live-turns, uses the existing Codex login for three real model responses
against temporary fixture data and archives the diagnostic conversation.
"""
import http.cookiejar
import argparse
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import tempfile
import time
import types
import urllib.error
import urllib.parse
import urllib.request


class CsrfParser(HTMLParser):
    token = ''

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if attrs.get('name') == '_csrf':
            self.token = attrs.get('value', attrs.get('content', ''))


def check(options):
    with tempfile.TemporaryDirectory(prefix='dashboard-mcp-check-') as directory:
        root = Path(directory)
        home, files = (Path(os.environ['HOME']) if options.live_turns else root / 'home'), root / 'files'
        files.mkdir()
        binary = Path(options.codex) if options.live_turns else home / '.local/bin/codex'
        if not options.live_turns:
            binary.parent.mkdir(parents=True)
            shutil.copyfile(options.codex, binary)
            binary.chmod(0o755)
        token, password = secrets.token_hex(32), secrets.token_hex(20)
        env = dict(os.environ, HOME=str(home), DASHBOARD_MCP_TOKEN=token,
                   DASHBOARD_AUTH_ID='mcp-fixture', DASHBOARD_AUTH_PASSWORD=password,
                   DASHBOARD_DB_PATH=str(root / 'dashboard.db'), WORKSPACE_ROOT=str(files),
                   CREDENTIAL_KEY_PATH=str(root / 'credential.key'), CLOUD_ROOT=str(root / 'cloud'),
                   SESSION_COOKIE_SECURE='false', SERVER_PORT=str(options.port))
        base = 'http://127.0.0.1:' + str(options.port)
        env['DASHBOARD_MCP_URL'] = base + '/api/v1/mcp'
        client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

        def request(path, body=None, headers=None):
            data = None if body is None else json.dumps(body).encode()
            req = urllib.request.Request(base + path, data=data, headers=headers or {})
            with client.open(req, timeout=75) as response:
                content = response.read()
                return json.loads(content) if 'json' in response.headers.get('Content-Type', '') else content.decode()

        def mcp(name, arguments):
            return request('/api/v1/mcp', dict(jsonrpc='2.0', id=1, method='tools/call',
                params=dict(name=name, arguments=arguments)),
                {'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token})['result']

        with (root / 'server.log').open('w') as output:
            server = subprocess.Popen(['java', '-jar', options.jar], env=env, stdout=output, stderr=output)
            try:
                deadline = time.monotonic() + 60
                while True:
                    try:
                        request('/health')
                        break
                    except (OSError, urllib.error.URLError):
                        if server.poll() is not None or time.monotonic() > deadline:
                            raise RuntimeError('Isolated dashboard failed to start')
                        time.sleep(.25)
                created = mcp('create_calendar_event', dict(title='MCP fixture',
                    start='2026-10-05T09:00:00', end='2026-10-05T10:00:00'))
                assert not created['isError'], 'Fixture event creation failed'
                remembered = mcp('create_memory', dict(content='Late October laboratory dinner may happen',
                    type='POSSIBILITY', confidence='TENTATIVE', scope='PERSONAL', sourceThreadId='smoke-thread'))
                assert not remembered['isError'], 'Memory creation failed'
                memory_id = remembered['structuredContent']['memory']['id']
                recalled = mcp('search_memories', dict(query='laboratory dinner'))
                assert not recalled['isError'] and any(item['id'] == memory_id for item in recalled['structuredContent']['page']['items']), 'Cross-session Memory search failed'
                context = mcp('compose_memory_context', dict(query='laboratory dinner in October'))
                assert not context['isError'] and '[TENTATIVE]' in context['structuredContent']['context']['text'], 'Memory uncertainty lost in context'
                denied = mcp('promote_memory_to_calendar', dict(id=memory_id,title='Laboratory dinner',
                    start='2026-10-28T19:00:00',end='2026-10-28T21:00:00',confirmed=False))
                assert denied['isError'], 'Unconfirmed Memory promotion was accepted'
                print('PASS: MCP Memory create -> search -> bounded tentative context -> blocked promotion', flush=True)
                csrf = CsrfParser()
                csrf.feed(request('/login'))
                form = urllib.parse.urlencode(dict(id='mcp-fixture', password=password, _csrf=csrf.token)).encode()
                with client.open(urllib.request.Request(base + '/login', data=form), timeout=15) as response:
                    csrf.feed(response.read().decode())
                discovery = mcp('discover_service_resources', dict(threadId='mcp-smoke-thread', query='Dashboard Server'))
                assert not discovery['isError'], 'Service resource discovery failed'
                candidates = discovery['structuredContent']['discovery']['candidates']
                assert any(item['type'] == 'DEVICE' and item['reference'] == 'local' for item in candidates), 'Local device missing from discovery'
                prepared = mcp('create_service_draft', dict(threadId='mcp-smoke-thread', name='MCP Smoke Service',
                    environment='Development', resources=[dict(type='DEVICE', reference='local', deviceId='', label='Dashboard Server')]))
                assert not prepared['isError'], 'Service draft creation failed'
                draft = prepared['structuredContent']['draft']
                blocked = mcp('commit_service_draft', dict(id=draft['id'], revision=draft['revision']))
                assert blocked['isError'], 'Service commit bypassed browser approval'
                request('/api/v1/assistant/service-drafts/' + draft['id'] + '/approve',
                    dict(revision=draft['revision']),
                    {'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf.token})
                committed = mcp('commit_service_draft', dict(id=draft['id'], revision=draft['revision']))
                assert not committed['isError'] and committed['structuredContent']['service']['name'] == 'MCP Smoke Service', 'Approved Service draft commit failed'
                print('PASS: MCP discovery -> draft -> blocked commit -> browser approval -> atomic catalog commit', flush=True)
                job = request('/api/v1/assistant/jobs', dict(deviceId='local', root=str(files),
                    action='codex-connections', args={}),
                    {'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf.token})
                deadline = time.monotonic() + 90
                while job['state'] == 'RUNNING' and time.monotonic() < deadline:
                    time.sleep(.3)
                    job = request('/api/v1/assistant/jobs/' + job['id'])
                assert job['state'] == 'SUCCEEDED', 'Assistant connection job failed: ' + str(job.get('error'))
                connection = next(c for c in job['result']['assistant']['connections'] if c['name'] == 'personal-dashboard')
                assert not connection['error'] and {'list_calendar_events', 'discover_service_resources',
                    'create_service_draft', 'update_service_draft', 'get_service_draft',
                    'commit_service_draft', 'search_memories', 'get_memory', 'create_memory',
                    'compose_memory_context', 'promote_memories_to_note'}.issubset(connection['tools']), 'Dashboard Assistant tools not discovered'
                assert connection.get('runtimeStatus') in (None, 'connected'), 'Unexpected runtime state'
                print('PASS: real Codex discovery -> Java job response includes calendar tool; runtimeStatus=' + str(connection.get('runtimeStatus')), flush=True)

                if options.live_turns:
                    request('/api/v1/applications', dict(name='MCP fixture app', url='https://example.invalid', pinned=False),
                        {'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf.token})
                    thread_id = None
                    for prompt, tool, expected in [('등록된 앱을 보여줘', 'list_apps', 'MCP fixture app'),
                                                  ('연결된 mcp들 알려줘', None, 'personal-dashboard'),
                                                  ('2026년 10월 일정 설명해줘', 'list_calendar_events', 'MCP fixture')]:
                        job = request('/api/v1/assistant/jobs', dict(deviceId='local', root=str(files), action='codex-run',
                            args=dict(prompt=prompt, threadId=thread_id, mode='read-only')),
                            {'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf.token})
                        deadline = time.monotonic() + 180
                        while job['state'] == 'RUNNING' and time.monotonic() < deadline:
                            time.sleep(.5)
                            job = request('/api/v1/assistant/jobs/' + job['id'])
                        if job['state'] == 'RUNNING':
                            client.open(urllib.request.Request(base + '/api/v1/assistant/jobs/' + job['id'], method='DELETE',
                                headers={'X-CSRF-TOKEN': csrf.token})).close()
                        assert job['state'] == 'SUCCEEDED', 'Live model turn failed: ' + str(job.get('error'))
                        thread = job['result']['assistant']['thread']
                        thread_id = thread['id']
                        last_turn = thread['turns'][-1]
                        assert last_turn['status'] == 'completed', 'Model turn status=' + last_turn['status']
                        items = thread['turns'][-1]['items']
                        answer = '\n'.join(item.get('text', '') for item in items if item['type'] == 'agentMessage')
                        if tool:
                            assert any(item.get('server') == 'personal-dashboard' and item.get('tool') == tool for item in items), 'Model did not call ' + tool
                        assert expected in answer, 'Model did not return verified dashboard data'
                        print('PASS: live model turn ' + (tool or 'MCP inventory') + ' returned verified fixture data', flush=True)

                module = types.ModuleType('mcp_smoke_helper')
                resources = Path(options.studio)
                source = (resources / 'remote.py').read_text().replace('# CODEX_BRIDGE', (resources / 'codex_bridge.py').read_text())
                exec(source, module.__dict__)
                module.env.update(env, PATH=str(binary.parent) + ':' + env.get('PATH', ''))
                bridge = module.CodexBridge(files, dashboard=True)
                try:
                    bridge.call('initialize', dict(clientInfo=dict(name='mcp_smoke', version='1'), capabilities=dict(experimentalApi=True)))
                    bridge.write(dict(method='initialized'))
                    if options.live_turns and thread_id:
                        bridge.call('thread/archive', dict(threadId=thread_id))
                    thread = bridge.call('thread/start', dict(cwd=str(files), sandbox='read-only', approvalPolicy='never'))
                    result = bridge.call('mcpServer/tool/call', dict(threadId=thread['thread']['id'], server='personal-dashboard',
                        tool='list_calendar_events', arguments={'from': '2026-10-01', 'to': '2026-11-01'}))
                    assert not result.get('isError'), 'MCP calendar invocation failed'
                    data = result.get('structuredContent') or json.loads(result['content'][0]['text'])
                    assert any(event['title'] == 'MCP fixture' for event in data['events']), 'Calendar fixture missing'
                    print('PASS: real Codex MCP tool call reads October fixture from SQLite (no model turn)', flush=True)
                finally:
                    bridge.close()
            finally:
                server.terminate()
                try:
                    server.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    server.kill()
                    server.wait()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--live-turns', action='store_true', help='Use the existing Codex login for three real model turns against fixture data')
    parser.add_argument('--codex', default='/qa/codex')
    parser.add_argument('--jar', default='/app/app.jar')
    parser.add_argument('--studio', default='/qa/studio')
    parser.add_argument('--port', default=8080, type=int)
    check(parser.parse_args())
