"""Offline checks for the matched Codex CLI and code-mode host installation."""
import hashlib
import io
import json
from pathlib import Path
import tarfile
import tempfile
import types
import unittest
from unittest.mock import patch

RESOURCE = Path(__file__).parents[2] / 'main/resources/studio'
remote = types.ModuleType('codex_install')
exec((RESOURCE / 'remote.py').read_text(), remote.__dict__)
PREFIX = 'https://github.com/openai/codex/releases/download/rust-v0.154.0/'


class CodexInstallTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.binary = self.root / 'codex'
        self.release = dict(version='0.154.0', url=PREFIX + 'codex.tar.gz', digest='sha256:' + 'a' * 64,
                            hostUrl=PREFIX + 'host.tar.gz', hostDigest='sha256:' + 'b' * 64)

    def tearDown(self):
        self.temp.cleanup()

    def test_explicit_ssh_refresh_uses_latest_and_clears_cache(self):
        cache = self.root / '.cache/personal-workspace/codex-release.json'
        cache.parent.mkdir(parents=True)
        cache.write_text('{}')
        with patch.object(remote.Path, 'home', return_value=self.root), \
                patch.object(remote.os, 'uname', return_value=types.SimpleNamespace(machine='x86_64')), \
                patch.object(remote, 'emit'), patch.object(remote, 'ensure_server_codex') as update, \
                patch.object(remote, 'install_github_cli'), \
                patch.object(remote, 'git', return_value=(0, b'git version test')), \
                patch.object(remote, 'run', return_value=(0, b'codex-cli 0.154.0')):
            result = remote.setup('ssh-device', refresh=True, project_codex=True)
            update.assert_called_once()
            self.assertFalse(cache.exists())
            self.assertEqual(result['codex'], 'codex-cli 0.154.0')

    def test_explicit_refresh_failure_never_reports_existing_version_as_success(self):
        with patch.object(remote.Path, 'home', return_value=self.root), \
                patch.object(remote.os, 'uname', return_value=types.SimpleNamespace(machine='x86_64')), \
                patch.object(remote, 'emit'), \
                patch.object(remote, 'ensure_server_codex', side_effect=OSError('network unavailable')), \
                patch.object(remote, 'install_codex_artifact') as fallback:
            with self.assertRaises(remote.Failure):
                remote.setup('ssh-device', refresh=True, project_codex=True)
            fallback.assert_not_called()

    def test_same_cli_version_repairs_missing_host(self):
        with patch.object(remote, 'latest_codex_release', return_value=self.release), \
                patch.object(remote, 'installed_codex_version', return_value='0.154.0'), \
                patch.object(remote, 'install_codex_host') as host, patch.object(remote, 'install_codex_artifact') as cli:
            remote.ensure_server_codex(self.binary, 'x86_64', self.root / 'cache')
            host.assert_called_once_with(self.binary, 'x86_64', self.release)
            cli.assert_not_called()

    def test_complete_matching_install_is_reused(self):
        host = self.root / 'codex-code-mode-host'
        host.write_text('fixture'); host.chmod(0o755)
        host.with_suffix('.version').write_text('0.154.0')
        with patch.object(remote, 'latest_codex_release', return_value=self.release), \
                patch.object(remote, 'installed_codex_version', return_value='0.154.0'), \
                patch.object(remote, 'install_codex_artifact') as install:
            remote.ensure_server_codex(self.binary, 'x86_64', self.root / 'cache')
            install.assert_not_called()
        host.with_suffix('.version').write_text('0.153.0')
        self.assertFalse(remote.codex_host_ready(self.binary, '0.154.0'))

    def test_new_install_installs_both_components(self):
        with patch.object(remote, 'latest_codex_release', return_value=self.release), \
                patch.object(remote, 'installed_codex_version', return_value=''), \
                patch.object(remote, 'install_codex_artifact') as install:
            remote.ensure_server_codex(self.binary, 'x86_64', self.root / 'cache')
            self.assertEqual(install.call_count, 2)
            self.assertEqual(install.call_args_list[0].args[-1], 'codex-code-mode-host')
            self.assertEqual(install.call_args_list[1].args[0], self.binary)

    def test_host_download_failure_is_not_a_complete_install(self):
        with patch.object(remote, 'latest_codex_release', return_value=self.release), \
                patch.object(remote, 'installed_codex_version', return_value='0.154.0'), \
                patch.object(remote, 'install_codex_artifact', side_effect=remote.Failure('download failed')):
            with self.assertRaises(remote.Failure):
                remote.ensure_server_codex(self.binary, 'x86_64', self.root / 'cache')
        self.assertFalse(remote.codex_host_ready(self.binary, '0.154.0'))

    def test_verified_host_archive_is_installed_executable(self):
        data = io.BytesIO()
        with tarfile.open(fileobj=data, mode='w:gz') as archive:
            content = b'fixture executable'
            member = tarfile.TarInfo('codex-code-mode-host-x86_64-unknown-linux-musl')
            member.size = len(content)
            archive.addfile(member, io.BytesIO(content))
        packed = data.getvalue()
        digest = 'sha256:' + hashlib.sha256(packed).hexdigest()
        host = self.root / 'codex-code-mode-host'
        with patch.object(remote.urllib.request, 'urlopen', return_value=io.BytesIO(packed)):
            remote.install_codex_artifact(host, 'x86_64', PREFIX + 'host.tar.gz', digest, 'codex-code-mode-host')
        self.assertEqual(host.read_bytes(), content)
        self.assertTrue(host.stat().st_mode & 0o111)
        with patch.object(remote.urllib.request, 'urlopen', return_value=io.BytesIO(packed)):
            with self.assertRaises(remote.Failure):
                remote.install_codex_artifact(host, 'x86_64', PREFIX + 'host.tar.gz', 'sha256:' + '0' * 64, 'codex-code-mode-host')
        self.assertEqual(host.read_bytes(), content)

    def test_old_cache_without_host_metadata_is_refreshed(self):
        cache = self.root / 'cache.json'
        cache.write_text(json.dumps(dict(version='0.154.0', url=self.release['url'],
                                        digest=self.release['digest'], checkedAt=remote.time.time())))
        assets = [dict(name=component + '-x86_64-unknown-linux-musl.tar.gz',
                       browser_download_url=PREFIX + component + '.tar.gz', digest='sha256:' + 'a' * 64)
                  for component in ('codex', 'codex-code-mode-host')]
        data = json.dumps(dict(tag_name='rust-v0.154.0', assets=assets)).encode()
        with patch.object(remote.urllib.request, 'urlopen', return_value=io.BytesIO(data)) as download:
            release = remote.latest_codex_release(cache, 'x86_64')
            download.assert_called_once()
        self.assertIn('hostUrl', release)
        self.assertIn('hostDigest', json.loads(cache.read_text()))


if __name__ == '__main__': unittest.main()
