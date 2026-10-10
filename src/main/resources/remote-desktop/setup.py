"""Create an isolated Linux desktop without changing existing VNC services."""
import fcntl
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import time


class SetupError(Exception):
    pass


def run(args, timeout=30, **kwargs):
    try:
        return subprocess.run(args, timeout=timeout, check=True,
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, **kwargs)
    except subprocess.TimeoutExpired:
        raise SetupError("TIMEOUT")
    except (OSError, subprocess.CalledProcessError):
        raise SetupError("COMMAND_FAILED")


def available(port):
    with socket.socket() as sock:
        try:
            sock.bind(("127.0.0.1", port))
            return True
        except OSError:
            return False


def rfb(port):
    try:
        with socket.create_connection(("127.0.0.1", port), 0.3) as sock:
            sock.settimeout(0.3)
            return sock.recv(12).startswith(b"RFB ")
    except OSError:
        return False


def choose_display():
    # Never remove another user's X locks or kill a process to free a port.
    for display in range(20, 100):
        if not Path(f"/tmp/.X{display}-lock").exists() and not Path(f"/tmp/.X11-unix/X{display}").exists() and available(5900 + display):
            return display
    raise SetupError("NO_PORT")


def tools():
    return (shutil.which("Xtigervnc") or shutil.which("Xvnc"),
            shutil.which("tigervncpasswd") or shutil.which("vncpasswd"),
            shutil.which("openbox"), shutil.which("xterm"))


def install(sudo_password=""):
    if all(tools()):
        return
    if not shutil.which("apt-get"):
        raise SetupError("UNSUPPORTED_PACKAGES")
    prefix = []
    install_input = {}
    if os.geteuid() != 0:
        if not shutil.which("sudo"):
            raise SetupError("ADMIN_REQUIRED")
        prefix = ["sudo", "-S", "-p", ""] if sudo_password else ["sudo", "-n"]
        install_input = {"input": (sudo_password + "\n").encode()} if sudo_password else {}
        if subprocess.run(prefix + ["true"], timeout=15, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, **install_input).returncode:
            raise SetupError("ADMIN_REQUIRED")
    env = dict(os.environ, DEBIAN_FRONTEND="noninteractive")
    run(prefix + ["env", "DEBIAN_FRONTEND=noninteractive", "apt-get", "-o", "DPkg::Lock::Timeout=60", "update"], 180, env=env, **install_input)
    run(prefix + ["env", "DEBIAN_FRONTEND=noninteractive", "apt-get", "-o", "DPkg::Lock::Timeout=60", "install", "-y", "--no-install-recommends",
                  "tigervnc-standalone-server", "tigervnc-tools", "openbox", "xterm", "xfonts-base", "xfce4", "dbus-x11", "fonts-noto-cjk"], 420, env=env, **install_input)
    if not all(tools()):
        raise SetupError("UNSUPPORTED_PACKAGES")


def setup(secret, sudo_password="", progress=lambda stage: None):
    progress("CHECKING")
    os.umask(0o077)
    root = Path.home() / ".personal-dashboard-desktop"
    if root.is_symlink():
        raise SetupError("UNSAFE_STATE")
    root.mkdir(mode=0o700, exist_ok=True)
    if root.stat().st_uid != os.getuid():
        raise SetupError("UNSAFE_STATE")
    root.chmod(0o700)
    for name in ("lock", "state.json", "passwd"):
        if (root / name).is_symlink():
            raise SetupError("UNSAFE_STATE")
    with (root / "lock").open("a") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise SetupError("BUSY")
        state_file = root / "state.json"
        if state_file.exists():
            state = json.loads(state_file.read_text())
            port = int(state["port"])
            # Reuse only our own live process, identified by its unique password file.
            cmdline = Path(f"/proc/{int(state['pid'])}/cmdline")
            if cmdline.exists() and str(root / "passwd").encode() in cmdline.read_bytes().split(b"\0") and rfb(port):
                return port
        # Existing VNC installations are never reconfigured or have passwords reset.
        if any(rfb(port) for port in range(5900, 5920)):
            raise SetupError("EXISTING_VNC")
        progress("INSTALLING")
        install(sudo_password)
        progress("STARTING")
        server, passwd, wm, terminal = tools()
        with (root / "passwd").open("wb") as output:
            try:
                subprocess.run([passwd, "-f"], input=(secret + "\n").encode(), stdout=output,
                               stderr=subprocess.DEVNULL, timeout=10, check=True)
            except (OSError, subprocess.SubprocessError):
                raise SetupError("PASSWORD_FAILED")
        for attempt in range(3):
            display = choose_display()
            port = 5900 + display
            process = subprocess.Popen([server, f":{display}", "-localhost", "yes", "-rfbport", str(port),
                                        "-SecurityTypes", "VncAuth", "-PasswordFile", str(root / "passwd"),
                                        "-geometry", "1280x800", "-depth", "24", "-nolisten", "tcp", "-AlwaysShared"],
                                       stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                                       stderr=subprocess.DEVNULL, start_new_session=True)
            ready = False
            for _ in range(30):
                if process.poll() is not None:
                    break
                if rfb(port):
                    ready = True
                    break
                time.sleep(0.2)
            if not ready:
                if process.poll() is None:
                    process.terminate()
                    try:
                        process.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        process.kill()
                continue
            env = dict(os.environ, DISPLAY=f":{display}")
            env.pop("SESSION_MANAGER", None)
            env.pop("DBUS_SESSION_BUS_ADDRESS", None)
            children = []
            try:
                desktop_commands = (["dbus-launch", "--exit-with-session", "startxfce4"],) if shutil.which("startxfce4") and shutil.which("dbus-launch") else ([wm], [terminal])
                for command in desktop_commands:
                    children.append(subprocess.Popen(command, env=env, stdin=subprocess.DEVNULL,
                                                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                                     start_new_session=True))
                time.sleep(1)
                if any(child.poll() is not None for child in children):
                    raise SetupError("DESKTOP_FAILED")
                state_file.write_text(json.dumps({"port": port, "pid": process.pid}))
                return port
            except Exception:
                for child in children:
                    if child.poll() is None:
                        child.terminate()
                process.terminate()
                raise
        raise SetupError("START_FAILED")


if __name__ == "__main__":
    try:
        request = json.loads(sys.stdin.readline(16384))
        secret = request.get("password", "")
        sudo_password = request.get("sudoPassword", "")
        if not isinstance(sudo_password, str) or len(sudo_password) > 4096 or "\n" in sudo_password or "\r" in sudo_password:
            raise SetupError("INVALID_SECRET")
        if len(secret) != 8 or not secret.isalnum():
            raise SetupError("INVALID_SECRET")
        print(json.dumps({"code": "READY", "port": setup(secret, sudo_password, lambda stage: print(json.dumps({"stage": stage}), flush=True))}), flush=True)
    except SetupError as error:
        print(json.dumps({"code": str(error), "port": 0}))
    except Exception:
        print(json.dumps({"code": "SETUP_FAILED", "port": 0}))
