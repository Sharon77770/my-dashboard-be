"""App Server protocol contract tests with an executable, offline CLI fixture."""
import json
from pathlib import Path
import tempfile
import types
import unittest
from unittest.mock import patch

RESOURCE = Path(__file__).parents[2] / 'main/resources/studio'
remote = types.ModuleType('codex_remote')
exec((RESOURCE / 'remote.py').read_text().replace('# CODEX_BRIDGE', (RESOURCE / 'codex_bridge.py').read_text()), remote.__dict__)

FIXTURE = '''#!/usr/bin/python3
import json,sys,os
def send(**v): print(json.dumps(v),flush=True)
thread=dict(id='thread-1',cwd=os.getcwd(),turns=[])
dashboard=False
thread_checked=False
for line in sys.stdin:
 f=json.loads(line);m=f.get('method');p=f.get('params',{});result={}
 if m=='initialize': result={}
 elif m=='account/rateLimits/read': result={'rateLimitsByLimitId': {'codex': dict(limitId='codex',limitName=None,primary=dict(usedPercent=25,windowDurationMins=300,resetsAt=1730947200),secondary=dict(usedPercent=40,windowDurationMins=10080,resetsAt=1731552000))}}
 elif m=='model/list': result={'data':[dict(model='fixture',displayName='Fixture',isDefault=True,defaultReasoningEffort='medium',supportedReasoningEfforts=[dict(reasoningEffort='medium',description='Balanced')])]}
 elif m=='mcpServerStatus/list':
  assert p['detail']=='toolsAndAuthOnly'
  if p.get('threadId'):thread_checked=True
  failed=p.get('threadId') and os.path.exists('fail-thread-mcp')
  result={'data':[], 'nextCursor':'next'} if not p.get('cursor') else {'data':[dict(name='personal-dashboard',authStatus='bearerToken',runtimeStatus='failed' if failed else None,tools={} if failed else {'list_calendar_events':{},'list_apps':{},'list_notes':{}},toolsError=None)]}
 elif m=='thread/start' or m=='thread/resume':
  dashboard='developerInstructions' in p
  if dashboard:
   assert 'list_apps' in p['developerInstructions'] and 'MCP discovery inventory' in p['developerInstructions']
   assert p['config']['mcp_servers.personal-dashboard.enabled'] is True
   assert p['config']['mcp_servers.personal-dashboard.bearer_token_env_var']=='DASHBOARD_MCP_TOKEN'
   assert '-c' in sys.argv
  result={'thread':thread,'model':'fixture'}
 elif m=='thread/read':
  if p['threadId']=='foreign': thread['cwd']='/other-project'
  result={'thread':thread}
 elif m=='turn/start':
  open('turn-started','w').close()
  turn=dict(id='turn-1',status='inProgress',items=[])
  send(id=f['id'],result={'turn':turn})
  send(method='turn/started',params={'turn':turn})
  if dashboard:
   assert thread_checked
   item=dict(id='tool',type='mcpToolCall',server='personal-dashboard',tool='list_apps',status='completed')
   send(method='item/completed',params={'item':item})
   turn=dict(id='turn-1',status='completed',items=[item]);thread['turns']=[turn]
   if os.path.exists('fail-model'):
    turn['status']='failed'
    turn['error']=dict(codexErrorInfo='unauthorized' if os.path.exists('fail-auth') else 'other',message='private-upstream-detail')
   send(method='turn/completed',params={'turn':turn})
   continue
  send(id=900,method='item/commandExecution/requestApproval',params=dict(threadId=thread['id'],turnId=turn['id'],itemId='cmd',command='echo safe',reason='Run command?'))
  continue
 elif f.get('id')==900 and not m:
  assert f['result']['decision']=='decline'
  item=dict(id='answer',type='agentMessage',text='Request declined safely')
  send(method='item/completed',params={'item':item})
  turn=dict(id='turn-1',status='completed',items=[item]);thread['turns']=[turn]
  send(method='turn/completed',params={'turn':turn})
  continue
 if 'id' in f: send(id=f['id'],result=result)
'''


class CodexBridgeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        cli = self.root / 'codex'; cli.write_text(FIXTURE); cli.chmod(0o755)
        self.old_path = remote.env['PATH']; remote.env['PATH'] = str(self.root) + ':' + self.old_path
        self.events = []
        self.old_emit = remote.emit
        remote.emit = lambda **event: self.events.append(event)

    def tearDown(self):
        remote.env['PATH'] = self.old_path; remote.emit = self.old_emit; self.temp.cleanup()

    def test_dynamic_models(self):
        result = remote.codex_action(self.root, 'codex-models', {})
        self.assertEqual(result['assistant']['models'][0]['id'], 'fixture')
        self.assertEqual(result['assistant']['models'][0]['efforts'][0]['reasoningEffort'], 'medium')

    def test_rate_limits_project_both_windows_without_credentials(self):
        result = remote.codex_action(self.root, 'codex-rate-limits', {})
        limits = result['assistant']['rateLimits']
        self.assertEqual([(item['windowDurationMins'], item['usedPercent']) for item in limits],
                         [(300, 25), (10080, 40)])
        self.assertEqual(limits[0]['name'], 'Codex')

    def test_dashboard_mcp_connection_reports_runtime_and_calendar_tool(self):
        result = remote.codex_action(self.root, 'codex-connections', {})
        connection = result['assistant']['connections'][0]
        self.assertEqual(connection['name'], 'personal-dashboard')
        self.assertIsNone(connection['runtimeStatus'])
        self.assertIn('list_calendar_events', connection['tools'])

    def test_dashboard_start_and_resume_receive_config_instructions_and_thread_check(self):
        with patch.dict(remote.env, DASHBOARD_MCP_URL='http://127.0.0.1:8080/api/v1/mcp', DASHBOARD_MCP_TOKEN='f' * 32):
            for thread_id in (None, 'thread-1'):
                result = remote.codex_action(self.root, 'codex-run', dict(prompt='등록된 앱을 보여줘', threadId=thread_id), dashboard=True)
                item = result['assistant']['thread']['turns'][0]['items'][0]
                self.assertEqual(item['server'], 'personal-dashboard')
                self.assertEqual(item['tool'], 'list_apps')

    def test_thread_mcp_failure_prevents_model_turn(self):
        (self.root / 'fail-thread-mcp').touch()
        with patch.dict(remote.env, DASHBOARD_MCP_URL='http://127.0.0.1:8080/api/v1/mcp', DASHBOARD_MCP_TOKEN='f' * 32):
            with self.assertRaises(remote.Failure):
                remote.codex_action(self.root, 'codex-run', dict(prompt='등록된 앱을 보여줘'), dashboard=True)
        self.assertFalse((self.root / 'turn-started').exists())

    def test_dashboard_model_failure_is_not_reported_as_success(self):
        (self.root / 'fail-model').touch()
        with patch.dict(remote.env, DASHBOARD_MCP_URL='http://127.0.0.1:8080/api/v1/mcp', DASHBOARD_MCP_TOKEN='f' * 32):
            for authenticated, status in ((True, 502), (False, 401)):
                if not authenticated: (self.root / 'fail-auth').touch()
                with self.assertRaises(remote.Failure) as error:
                    remote.codex_action(self.root, 'codex-run', dict(prompt='등록된 앱을 보여줘'), dashboard=True)
                self.assertEqual(error.exception.status, status)
                self.assertNotIn('private-upstream-detail', error.exception.message)

    def test_mcp_string_discovery_error_is_projected_without_exposing_credentials(self):
        result = remote.assistant_connection(dict(name='personal-dashboard', runtimeStatus='failed',
            tools={}, toolsError='HTTP 401 Authorization: Bearer private-fixture-token'))
        self.assertEqual(result['runtimeStatus'], 'failed')
        self.assertEqual(result['tools'], [])
        self.assertTrue(result['error'])
        self.assertNotIn('private-fixture-token', json.dumps(result))

    def test_mcp_bearer_credentials_alone_do_not_imply_discovery(self):
        result = remote.assistant_connection(dict(name='personal-dashboard', authStatus='bearerToken', tools={}))
        self.assertEqual(result['tools'], [])
        self.assertIsNone(result['runtimeStatus'])

    def test_new_session_is_draft_until_first_turn(self):
        result = remote.codex_action(self.root, 'codex-thread-new', {})
        self.assertEqual(result['assistant']['thread']['id'], '')

    def test_turn_approval_and_authoritative_history(self):
        def receive(**value):
            self.events.append(value)
            event = value.get('assistant', {})
            if event.get('kind') == 'interaction':
                remote.controls.put(dict(type='approval', requestId=event['interaction']['id'], decision='decline'))
        remote.emit = receive
        result = remote.codex_action(self.root, 'codex-run', dict(prompt='test', model='fixture'))
        self.assertEqual(result['assistant']['status'], 'completed')
        self.assertEqual(result['assistant']['thread']['turns'][0]['items'][0]['text'], 'Request declined safely')
        self.assertIn('answered', [e['assistant']['kind'] for e in self.events])

    def test_foreign_thread_and_file_context_rejected(self):
        with self.assertRaises(remote.Failure) as error:
            remote.codex_action(self.root, 'codex-thread-read', dict(threadId='foreign'))
        self.assertEqual(error.exception.status, 403)
        with self.assertRaises(remote.Failure):
            remote.codex_input(self.root, dict(prompt='test', context=[dict(kind='file', path='/etc/passwd')]))

    def test_only_reasoning_summary_is_projected(self):
        item = remote.assistant_item(dict(id='r', type='reasoning', summary=['Progress'], content=['hidden reasoning']))
        self.assertEqual(item['text'], 'Progress')
        self.assertNotIn('hidden reasoning', json.dumps(item))

    def test_context_snapshot_and_limits(self):
        (self.root / 'main.py').write_text('print(1)')
        inputs = remote.codex_input(self.root, dict(prompt='test', context=[dict(kind='file', path='main.py')]))
        self.assertIn('print(1)', inputs[1]['text'])
        with self.assertRaises(remote.Failure):
            remote.codex_input(self.root, dict(prompt='test', context=[dict(kind='image', dataUrl='https://remote/image.png')]))


if __name__ == '__main__': unittest.main()
