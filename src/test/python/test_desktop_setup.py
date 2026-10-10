"""Check safe provisioning decisions without installing packages on the test host."""
import importlib.util
from pathlib import Path
import socket
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("desktop_setup", Path(__file__).parents[2] / "main/resources/remote-desktop/setup.py")
desktop = importlib.util.module_from_spec(spec)
spec.loader.exec_module(desktop)


class DesktopSetupTest(unittest.TestCase):
    def test_busy_port_is_not_available(self):
        with socket.socket() as occupied:
            occupied.bind(("127.0.0.1", 0))
            occupied.listen()
            self.assertFalse(desktop.available(occupied.getsockname()[1]))

    def test_display_collision_skips_and_exhaustion_is_explicit(self):
        with patch.object(desktop.Path, "exists", return_value=False), patch.object(desktop, "available", side_effect=lambda port: port >= 5922):
            self.assertEqual(desktop.choose_display(), 22)
        with patch.object(desktop.Path, "exists", return_value=True):
            with self.assertRaisesRegex(desktop.SetupError, "NO_PORT"):
                desktop.choose_display()

    def test_existing_tools_skip_package_manager(self):
        with patch.object(desktop, "tools", return_value=("x", "p", "w", "t")), patch.object(desktop, "run") as runner:
            desktop.install()
            runner.assert_not_called()

    def test_unsupported_package_manager_does_not_install(self):
        with patch.object(desktop, "tools", return_value=(None,) * 4), patch.object(desktop.shutil, "which", return_value=None), patch.object(desktop, "run") as runner:
            with self.assertRaisesRegex(desktop.SetupError, "UNSUPPORTED_PACKAGES"):
                desktop.install()
            runner.assert_not_called()

    def test_sudo_password_only_travels_in_stdin(self):
        with patch.object(desktop, "tools", side_effect=[(None,) * 4, ("x", "p", "w", "t")]), patch.object(desktop.os, "geteuid", return_value=1000), patch.object(desktop.shutil, "which", return_value="/usr/bin/tool"), patch.object(desktop.subprocess, "run") as authenticate, patch.object(desktop, "run") as runner:
            authenticate.return_value.returncode = 0
            desktop.install("test-secret")
            self.assertEqual(authenticate.call_args.kwargs["input"], b"test-secret\n")
            self.assertNotIn("test-secret", " ".join(authenticate.call_args.args[0]))
            self.assertEqual(runner.call_count, 2)
            for call in runner.call_args_list:
                self.assertEqual(call.kwargs["input"], b"test-secret\n")
                self.assertNotIn("test-secret", " ".join(call.args[0]))

    def test_wrong_sudo_password_never_reaches_package_manager(self):
        with patch.object(desktop, "tools", return_value=(None,) * 4), patch.object(desktop.os, "geteuid", return_value=1000), patch.object(desktop.shutil, "which", return_value="/usr/bin/tool"), patch.object(desktop.subprocess, "run") as authenticate, patch.object(desktop, "run") as runner:
            authenticate.return_value.returncode = 1
            with self.assertRaisesRegex(desktop.SetupError, "ADMIN_REQUIRED"):
                desktop.install("wrong")
            runner.assert_not_called()

    def test_non_vnc_port_not_reused(self):
        with patch.object(desktop.socket, "create_connection") as connect:
            connect.return_value.__enter__.return_value.recv.return_value = b"HTTP/1.1 200"
            self.assertFalse(desktop.rfb(5900))


if __name__ == "__main__":
    unittest.main()
