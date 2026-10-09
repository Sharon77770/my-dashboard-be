"""Actual subprocess/HTTP/git verification on Linux, without account credentials."""
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import types
import unittest


class StudioProcessesTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        source=Path(__file__).parents[2]/'main/resources/studio'
        self.remote=types.ModuleType('process_fixture')
        exec((source/'remote.py').read_text().replace('# PROCESSES_BRIDGE',(source/'processes.py').read_text()),self.remote.__dict__)
        self.store=self.root/'state';self.store.mkdir()
        self.project=self.root/'project';self.project.mkdir()
        self.remote.run_store=lambda root:self.store
        subprocess.run(['git','init',str(self.project)],check=True,capture_output=True)

    def tearDown(self):
        for item in self.store.glob('*/state.json'):
            self.remote.stop_run(item.parent)
        self.temp.cleanup()

    def action(self,name,args=None):return self.remote.process_action(self.project,name,args or {})

    def test_http_process_ports_logs_restart_and_git(self):
        (self.project/'index.html').write_text('studio real project')
        with socket.socket() as probe:
            probe.bind(('127.0.0.1',0));port=probe.getsockname()[1]
        result=self.action('run-start',dict(name='server',content='python3 -m http.server '+str(port),mode='run'))
        identity=result['tools']['processes'][0]['id'];pid=result['tools']['processes'][0]['pid']
        discovered=[]
        for unused in range(40):
            discovered=self.action('ports')['tools']['ports']
            if any(item['port']==port and item['protocol']=='HTTP' for item in discovered):break
            time.sleep(.05)
        listener=next(item for item in discovered if item['port']==port)
        self.assertEqual('HTTP',listener['protocol']);self.assertTrue(listener['project'])
        self.assertTrue(self.action('run-logs',dict(path=identity))['tools']['output'])
        self.action('run-stop',dict(path=identity))
        self.assertNotEqual('RUNNING',self.action('run-list')['tools']['processes'][0]['state'])
        restarted=self.action('run-restart',dict(path=identity))['tools']['processes'][0]
        self.assertNotEqual(pid,restarted['pid'])
        self.assertIn('index.html',subprocess.check_output(['git','status','--porcelain'],cwd=self.project).decode())

    def test_failed_tests_and_literal_process_ids(self):
        (self.project/'app.py').write_text('print("ok")')
        self.assertIn('Python app',[item['name'] for item in self.action('run-commands')['tools']['commands']])
        result=self.action('run-start',dict(name='failed-test',content="printf 'app.py:2: failure\\n'; exit 7",mode='test'))
        identity=result['tools']['processes'][0]['id']
        for unused in range(100):
            state=self.action('run-list')['tools']['processes'][0]
            if state['state']=='FAILED':break
            time.sleep(.02)
        self.assertEqual(7,state['exitCode'])
        self.assertIn('app.py:2',self.action('run-logs',dict(path=identity))['tools']['output'])
        with self.assertRaises(self.remote.Failure):self.action('run-stop',dict(path='../outside'))

    def test_process_restart_while_codex_owns_editor_lock(self):
        import fcntl, hashlib
        lockdir=Path.home()/'.cache/personal-workspace'
        lockdir.mkdir(parents=True,exist_ok=True)
        path=lockdir/(hashlib.sha256(str(self.project).encode()).hexdigest()+'.lock')
        def handle(action,args):
            return self.remote.handle(dict(base=str(self.project),root=str(self.project),action=action,args=args))
        with path.open('w') as lock:
            fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
            first=handle('run-start',dict(name='live',content='sleep 30',mode='run'))['tools']['processes'][0]
            restarted=handle('run-restart',dict(path=first['id']))['tools']['processes'][0]
            self.assertNotEqual(first['pid'],restarted['pid'])
            self.assertEqual('RUNNING',restarted['state'])
