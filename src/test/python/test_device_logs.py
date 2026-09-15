"""Linux CLI integration fixtures; never read production container or pane output."""
import fcntl
from pathlib import Path
import tempfile
import types
import unittest
from unittest.mock import patch


class DeviceLogsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        source = Path(__file__).parents[2] / 'main/resources/studio'
        self.remote = types.ModuleType('logs_fixture')
        exec((source / 'remote.py').read_text().replace('# LOGS_BRIDGE', (source / 'logs.py').read_text()), self.remote.__dict__)
        self.events = []
        self.remote.emit = lambda **value: self.events.append(value)
        self.remote.env = dict(self.remote.env, PATH=str(self.root) + ':/usr/bin:/bin', TMUX_TMPDIR=str(self.root))

    def tearDown(self):
        self.assertFalse(self.remote.processes)
        self.temp.cleanup()

    def fake_docker(self, body):
        script = self.root / 'docker'
        script.write_text('#!/usr/bin/python3\n' + body)
        script.chmod(0o700)

    def call(self, action, **args):
        return self.remote.handle(dict(base=str(self.root), root=str(self.root), action=action, args=args))

    def test_docker_targets_and_partial_utf8_stream(self):
        self.fake_docker("import sys,json,os,time\nif sys.argv[1]=='ps':\n print(json.dumps(dict(ID='a'*64,Names='fixture',Status='Up')))\nelse:\n assert sys.argv[1:6]==['logs','--follow','--tail','200','--timestamps']\n data='한글-no-newline'.encode();os.write(1,data[:2]);time.sleep(.2);os.write(1,data[2:])\n")
        result = self.call('logs-targets', mode='docker')
        self.assertEqual(result['logTargets'][0]['name'], 'fixture')
        self.call('logs-follow', mode='docker', target='a'*64)
        self.assertEqual(''.join(event['text'] for event in self.events), '한글-no-newline')
        self.assertEqual([event['sequence'] for event in self.events], list(range(1, len(self.events)+1)))

    def test_command_injection_and_wrong_source_rejected(self):
        self.fake_docker("raise AssertionError('must not execute')")
        for mode, target in [('docker', '--help'), ('docker', 'a'*64+';touch bad'), ('tmux', '%1;kill-server'), ('sh', '%1')]:
            with self.assertRaises(self.remote.Failure): self.call('logs-follow', mode=mode, target=target)
        self.assertFalse((self.root / 'bad').exists())

    def test_real_tmux_snapshot_updates_without_project_lock(self):
        rc, output = self.remote.log_collect(['tmux', 'new-session', '-d', '-s', 'log-fixture', 'sleep 30'], self.root)
        self.assertEqual(rc, 0)
        try:
            target = self.call('logs-targets', mode='tmux')['logTargets'][0]['id']
            rc, first = self.remote.log_collect(['tmux', 'capture-pane', '-p', '-t', target], self.root)
            self.assertEqual(rc, 0)
            lockdir = Path.home() / '.cache/personal-workspace'
            lockdir.mkdir(parents=True, exist_ok=True)
            lockfile = lockdir / (self.remote.hashlib.sha256(str(self.root).encode()).hexdigest()+'.lock')
            with lockfile.open('w') as lock:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                self.assertTrue(self.call('logs-targets', mode='tmux')['logTargets'])
            lockfile.unlink()
            class Finished(Exception): pass
            with patch.object(self.remote.time, 'sleep', side_effect=Finished):
                with self.assertRaises(Finished): self.call('logs-follow', mode='tmux', target=target)
            self.assertEqual(self.events[0]['event'], 'log-snapshot')
            self.assertEqual(self.events[0]['sequence'], 1)
        finally:
            self.remote.log_collect(['tmux', 'kill-server'], self.root)

    def test_docker_failure_and_output_bound(self):
        self.fake_docker("import sys\nsys.exit(1)")
        with self.assertRaises(self.remote.Failure): self.call('logs-targets', mode='docker')
        self.fake_docker("import sys\nsys.stdout.write('x'*3000000)")
        with self.assertRaises(self.remote.Failure) as error: self.call('logs-targets', mode='docker')
        self.assertEqual(error.exception.status, 413)
