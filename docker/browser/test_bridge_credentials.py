"""Linux private-volume credential lifecycle; never print the generated value."""
import os
from pathlib import Path
import tempfile
import unittest
from communication_bridge import load_token


class BridgeCredentialTest(unittest.TestCase):
    def test_creation_persistence_permissions_and_isolation(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'token'
            first = load_token(path)
            self.assertEqual(len(first), 64)
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            self.assertEqual(load_token(path), first)
            self.assertNotEqual(load_token(Path(directory) / 'other'), first)

    def test_rejects_symlink_public_and_malformed_credentials(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'token'
            load_token(path)
            link = Path(directory) / 'link'
            link.symlink_to(path)
            with self.assertRaises(ValueError):
                load_token(link)
            os.chmod(path, 0o644)
            with self.assertRaises(ValueError):
                load_token(path)
            os.chmod(path, 0o600)
            path.write_text('invalid')
            with self.assertRaises(ValueError):
                load_token(path)


if __name__ == '__main__':
    unittest.main()
