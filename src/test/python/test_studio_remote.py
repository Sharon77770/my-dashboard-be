"""Run on Linux: python3 -m unittest discover -s src/test/python -v."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

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
        with self.assertRaises(remote.Failure):
            remote.handle(dict(base=str(self.root), root=str(self.root/'nested'), action='git-status', args={}))
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
