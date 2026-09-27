"""Linux smoke: isolated dashboard DB + real Codex MCP discovery and calendar call.

Run inside the built dashboard image with this file, studio resources and a Linux
Codex binary mounted read-only. Uses no existing accounts, volumes or model turns.
"""
import http.cookiejar
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


def check():
    with tempfile.TemporaryDirectory(prefix='dashboard-mcp-check-') as directory:
        root = Path(directory)
        home, files = root / 'home', root / 'files'
        files.mkdir()
        binary = home / '.local/bin/codex'
        binary.parent.mkdir(parents=True)
        shutil.copyfile('/qa/codex', binary)
        binary.chmod(0o755)
        token, password = secrets.token_hex(32), secrets.token_hex(20)
        env = dict(os.environ, HOME=str(home), DASHBOARD_MCP_TOKEN=token,
                   DASHBOARD_AUTH_ID='mcp-fixture', DASHBOARD_AUTH_PASSWORD=password,
                   DASHBOARD_DB_PATH=str(root / 'dashboard.db'), WORKSPACE_ROOT=str(files),
                   CREDENTIAL_KEY_PATH=str(root / 'credential.key'), CLOUD_ROOT=str(root / 'cloud'),
                   SESSION_COOKIE_SECURE='false', SERVER_PORT='8080')
        base = 'http://127.0.0.1:8080'
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
            server = subprocess.Popen(['java', '-jar', '/app/app.jar'], env=env, stdout=output, stderr=output)
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
                subprocess.run([str(binary), 'mcp', 'add', 'personal-dashboard', '--url', base + '/api/v1/mcp',
                    '--bearer-token-env-var', 'DASHBOARD_MCP_TOKEN'], env=env, check=True, capture_output=True)

                csrf = CsrfParser()
                csrf.feed(request('/login'))
                form = urllib.parse.urlencode(dict(id='mcp-fixture', password=password, _csrf=csrf.token)).encode()
                with client.open(urllib.request.Request(base + '/login', data=form), timeout=15) as response:
                    csrf.feed(response.read().decode())
                job = request('/api/v1/assistant/jobs', dict(deviceId='local', root=str(files),
                    action='codex-connections', args={}),
                    {'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf.token})
                deadline = time.monotonic() + 90
                while job['state'] == 'RUNNING' and time.monotonic() < deadline:
                    time.sleep(.3)
                    job = request('/api/v1/assistant/jobs/' + job['id'])
                assert job['state'] == 'SUCCEEDED', 'Assistant connection job failed: ' + str(job.get('error'))
                connection = next(c for c in job['result']['assistant']['connections'] if c['name'] == 'personal-dashboard')
                assert not connection['error'] and 'list_calendar_events' in connection['tools'], 'Calendar not discovered'
                assert connection.get('runtimeStatus') in (None, 'connected'), 'Unexpected runtime state'
                print('PASS: real Codex discovery -> Java job response includes calendar tool; runtimeStatus=' + str(connection.get('runtimeStatus')), flush=True)

                module = types.ModuleType('mcp_smoke_helper')
                source = Path('/qa/studio/remote.py').read_text().replace('# CODEX_BRIDGE', Path('/qa/studio/codex_bridge.py').read_text())
                exec(source, module.__dict__)
                module.env.update(env, PATH=str(binary.parent) + ':' + env.get('PATH', ''))
                bridge = module.CodexBridge(files)
                try:
                    bridge.call('initialize', dict(clientInfo=dict(name='mcp_smoke', version='1'), capabilities=dict(experimentalApi=True)))
                    bridge.write(dict(method='initialized'))
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
    check()
