"""App Server protocol contract tests with an executable, offline CLI fixture."""
import json
from pathlib import Path
import tempfile
import types
import unittest

RESOURCE = Path(__file__).parents[2] / 'main/resources/studio'
remote = types.ModuleType('codex_remote')
exec((RESOURCE / 'remote.py').read_text().replace('# CODEX_BRIDGE', (RESOURCE / 'codex_bridge.py').read_text()), remote.__dict__)

FIXTURE = '''#!/usr/bin/python3
import json,sys,os
def send(**v): print(json.dumps(v),flush=True)
thread=dict(id='thread-1',cwd=os.getcwd(),turns=[])
for line in sys.stdin:
 f=json.loads(line);m=f.get('method');p=f.get('params',{});result={}
 if m=='initialize': result={}
 elif m=='model/list': result={'data':[dict(model='fixture',displayName='Fixture',isDefault=True,defaultReasoningEffort='medium',supportedReasoningEfforts=[dict(reasoningEffort='medium',description='Balanced')])]}
 elif m=='thread/start' or m=='thread/resume': result={'thread':thread,'model':'fixture'}
 elif m=='thread/read':
  if p['threadId']=='foreign': thread['cwd']='/other-project'
  result={'thread':thread}
 elif m=='turn/start':
  turn=dict(id='turn-1',status='inProgress',items=[])
  send(id=f['id'],result={'turn':turn})
  send(method='turn/started',params={'turn':turn})
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
