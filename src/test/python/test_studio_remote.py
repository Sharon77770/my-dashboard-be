"""Run on Linux: python3 -m unittest discover -s src/test/python -v."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('remote', Path(__file__).parents[2] / 'main/resources/studio/remote.py')
remote = importlib.util.module_from_spec(spec)
spec.loader.exec_module(remote)


class RemoteWorkspaceTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.root = Path(self.folder.name)

    def tearDown(self):
        self.folder.cleanup()

    def call(self, action, **args):
        return remote.handle(dict(base=str(self.root), root=str(self.root), action=action, args=args))

    def test_assistant_uses_private_root_while_ide_root_is_locked(self):
        home = self.root / 'home'
        files = self.root / 'files'
        home.mkdir(); files.mkdir()
        lockdir = home / '.cache/personal-workspace'
        lockdir.mkdir(parents=True)
        lockpath = lockdir / (remote.hashlib.sha256(str(files).encode()).hexdigest() + '.lock')
        def assistant(root, action, args, **kwargs):
            self.assertTrue(kwargs['dashboard'])
            return dict(root=str(root))
        with patch.object(remote.Path, 'home', return_value=home), patch.object(remote, 'codex_action', side_effect=assistant, create=True):
            with lockpath.open('w') as held:
                remote.fcntl.flock(held, remote.fcntl.LOCK_EX | remote.fcntl.LOCK_NB)
                result = remote.handle(dict(deviceId='local', base=str(files), root=str(files), assistantWorkspace=True, action='codex-thread-new', args={}))
            private = home / '.local/share/personal-workspace/assistant-workspace'
            self.assertEqual(result['root'], str(private))
            self.assertEqual(private.stat().st_mode & 0o777, 0o700)
            self.assertEqual(remote.assistant_legacy_root, files)
            self.assertEqual(list(files.iterdir()), [])
            with self.assertRaises(remote.Failure):
                remote.handle(dict(deviceId='local', base=str(files), root=str(private), action='list', args={}))
            # Assistant startup is independent even when the editable root is gone.
            files.rmdir()
            self.assertEqual(remote.handle(dict(deviceId='local', base=str(files), root='/ignored', assistantWorkspace=True, action='codex-thread-new', args={}))['root'], str(private))

    def test_external_init_and_nested_repository_discovery(self):
        self.assertFalse(self.call('git-status')['repository'])
        child = self.root / 'child'
        child.mkdir()
        remote.git(child, 'init')
        self.assertEqual(self.call('git-status')['repositories'], [str(child)])
        remote.git(self.root, 'init')
        self.assertTrue(self.call('git-status')['repository'])
        self.assertEqual(self.call('git-status')['history'], '')

    def test_github_login_does_not_lock_project_files(self):
        original_run = remote.run
        def command(args, **kwargs):
            if args[:3] == ['gh', 'auth', 'login']:
                self.call('create', path='during-login.txt')
                self.assertFalse(self.call('git-status')['repository'])
                return 0, b''
            if args[0] == 'gh': return 0, b''
            return original_run(args, **kwargs)
        with patch.object(remote, 'install_github_cli'), patch.object(remote, 'run', side_effect=command), patch.object(remote, 'emit'):
            self.assertTrue(self.call('github-login')['authenticated'])
        self.assertTrue((self.root / 'during-login.txt').exists())

    def test_atomic_utf8_edit_and_conflict(self):
        self.call('create', path='한글 file.py')
        initial = self.call('read', path='한글 file.py')
        self.call('save', path='한글 file.py', revision=initial['revision'], content='print("안녕")\r\n')
        self.assertEqual(self.call('read', path='한글 file.py')['content'], 'print("안녕")\r\n')
        with self.assertRaises(remote.Failure):
            self.call('save', path='한글 file.py', revision=initial['revision'], content='overwrite')

    def test_traversal_symlink_and_git_internal_rejected(self):
        (self.root / 'escape').symlink_to('/etc')
        for path in ('../etc/passwd', '/etc/passwd', 'escape/passwd', '.git/config'):
            with self.assertRaises(remote.Failure): self.call('read', path=path)

    def test_binary_size_and_nonempty_directory(self):
        (self.root / 'binary').write_bytes(b'\x00')
        with self.assertRaises(remote.Failure): self.call('read', path='binary')
        (self.root / 'large').write_bytes(b'x' * (remote.LIMIT + 1))
        with self.assertRaises(remote.Failure): self.call('read', path='large')
        self.call('mkdir', path='folder'); self.call('create', path='folder/file')
        with self.assertRaises(OSError): self.call('delete', path='folder')

    def test_git_stage_unstage_commit_branch_and_literal_paths(self):
        self.call('git-init')
        self.call('git-identity', name='Fixture', email='fixture@example.invalid')
        name = 'file $(touch INJECTED);.txt'
        self.call('create', path=name)
        self.call('git-stage', path=name)
        self.call('git-unstage', path=name)
        self.assertEqual(self.call('git-status')['changes'][0]['index'], '?')
        self.call('git-stage', path=name)
        self.call('git-commit', message='first $(touch INJECTED)')
        self.call('git-branch', branch='feature/fixture')
        self.assertEqual(self.call('git-status')['branch'], 'feature/fixture')
        self.assertFalse((self.root / 'INJECTED').exists())
        self.call('rename', path=name, target='renamed.txt')
        self.call('git-stage', path='.')
        changes = self.call('git-status')['changes']
        self.assertEqual(changes[0]['oldPath'], name)

    def test_parent_repository_and_credential_url_rejected(self):
        self.call('git-init'); self.call('mkdir', path='nested')
        status = remote.handle(dict(base=str(self.root), root=str(self.root/'nested'), action='git-status', args={}))
        self.assertFalse(status['repository'])
        self.assertEqual(status['repositories'], [str(self.root)])
        with self.assertRaises(remote.Failure):
            remote.handle(dict(base=str(self.root/'nested'), root=str(self.root/'nested'), action='git-status', args={}))
        with self.assertRaises(remote.Failure):
            self.call('git-clone', url='https://user:secret@example.invalid/repo', target='clone')

    def test_push_sets_upstream_and_fetch_pull_use_real_git(self):
        self.call('git-init')
        self.call('git-identity', name='Fixture', email='fixture@example.invalid')
        self.call('create', path='file.txt')
        self.call('git-stage', path='file.txt')
        self.call('git-commit', message='fixture')
        self.call('git-remote', url='https://example.invalid/fixture.git')
        with tempfile.TemporaryDirectory() as bare:
            remote.run(['git', 'init', '--bare', bare])
            remote.git(self.root, 'remote', 'set-url', 'origin', bare)
            self.call('git-push')
            self.call('git-fetch')
            self.call('git-pull')
            self.assertEqual(remote.git(self.root, 'rev-parse', 'HEAD')[1],
                             remote.run(['git', '-C', bare, 'rev-parse', 'HEAD'])[1])


if __name__ == '__main__': unittest.main()
