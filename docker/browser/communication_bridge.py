"""Optional local bridge: fixed apps, separate X/CDP/profile per account, no arbitrary scripts."""
import base64
import hashlib
import hmac
import http.server
import json
import os
from pathlib import Path
import re
import shutil
import signal
import secrets
import stat
import tempfile
import socket
import struct
import subprocess
import threading
import time
import urllib.parse
import urllib.request

ROOT = Path('/home/browser/profile/communications')
APPS = {'GMAIL': 'https://mail.google.com/', 'SLACK': 'https://slack.com/signin', 'DISCORD': 'https://discord.com/channels/@me'}
WINE_RUNTIME = os.environ.get('COMMUNICATION_RUNTIME') == 'wine'
if WINE_RUNTIME:
    APPS = {'KAKAOTALK': ''}
TOKEN = ''
TOKEN_PATH = Path('/run/communication-bridge/token')
SESSIONS = {}
LOCK = threading.RLock()


def load_token(path=TOKEN_PATH):
    """Private shared volume only; browser owns writes, dashboard mounts it read-only."""
    if path.parent.is_symlink() or path.is_symlink():
        raise ValueError('Invalid bridge credential path')
    if not path.exists():
        descriptor, temporary = tempfile.mkstemp(dir=path.parent, prefix='.token-')
        try:
            with os.fdopen(descriptor, 'w') as stream:
                stream.write(secrets.token_hex(32))
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary, path)
        finally:
            if os.path.exists(temporary):
                os.unlink(temporary)
    with os.fdopen(os.open(path, os.O_RDONLY | os.O_NOFOLLOW), 'r') as stream:
        metadata = os.fstat(stream.fileno())
        if not stat.S_ISREG(metadata.st_mode) or metadata.st_mode & 0o077:
            raise ValueError('Bridge credential must be a private file')
        token = stream.read(65)
    if not re.fullmatch(r'[a-f0-9]{64}', token):
        raise ValueError('Invalid bridge credential')
    return token


def read_json(port, path):
    with urllib.request.urlopen(f'http://127.0.0.1:{port}{path}', timeout=2) as response:
        data = response.read(4 * 1024 * 1024 + 1)
    if len(data) > 4 * 1024 * 1024:
        raise ValueError('Response too large')
    return json.loads(data)


def cdp(port, endpoint, method, parameters=None):
    """Internal method names only; the HTTP API never accepts a CDP method or expression."""
    parsed = urllib.parse.urlsplit(endpoint)
    if parsed.hostname not in ('localhost', '127.0.0.1') or parsed.port != port or not parsed.path.startswith('/devtools/'):
        raise ValueError('Unexpected endpoint')
    with socket.create_connection(('127.0.0.1', port), timeout=5) as connection:
        key = base64.b64encode(os.urandom(16)).decode()
        connection.sendall((f'GET {parsed.path} HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n').encode())
        headers = b''
        while not headers.endswith(b'\r\n\r\n'):
            chunk = connection.recv(1)
            if not chunk:
                raise ValueError('Closed handshake')
            headers += chunk
            if len(headers) > 8192:
                raise ValueError('Handshake too large')
        accept = base64.b64encode(hashlib.sha1((key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest())
        if not headers.startswith(b'HTTP/1.1 101 ') or accept not in headers:
            raise ValueError('Handshake rejected')
        payload = json.dumps({'id': 1, 'method': method, 'params': parameters or {}}).encode()
        mask = os.urandom(4)
        length = bytes([0x81, 0x80 | len(payload)]) if len(payload) < 126 else bytes([0x81, 0xfe]) + struct.pack('!H', len(payload))
        connection.sendall(length + mask + bytes(v ^ mask[i % 4] for i, v in enumerate(payload)))
        if method == 'Browser.close':
            return {}
        def exact(size):
            value = b''
            while len(value) < size:
                chunk = connection.recv(size - len(value))
                if not chunk:
                    raise ValueError('Closed socket')
                value += chunk
            return value
        combined = b''
        for _ in range(128):
            first, second = exact(2)
            size = second & 127
            if size == 126:
                size = struct.unpack('!H', exact(2))[0]
            elif size == 127:
                size = struct.unpack('!Q', exact(8))[0]
            if size > 4 * 1024 * 1024 or len(combined) + size > 4 * 1024 * 1024:
                raise ValueError('Frame too large')
            if second & 128:
                raise ValueError('Unexpected mask')
            if first & 15 not in (0, 1):
                raise ValueError('Unexpected control frame')
            combined += exact(size)
            if first & 128:
                reply = json.loads(combined)
                if reply.get('id') == 1:
                    if 'error' in reply:
                        raise ValueError('CDP operation failed')
                    return reply.get('result', {})
                combined = b''
        raise ValueError('Response missing')


def profile_path(identifier):
    if not re.fullmatch(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}', identifier):
        raise ValueError('Invalid profile')
    ROOT.mkdir(parents=True, exist_ok=True, mode=0o700)
    path = ROOT / identifier
    if path.is_symlink() or path.resolve().parent != ROOT.resolve():
        raise ValueError('Invalid profile path')
    return path


def stop(identifier, remove=False):
    session = SESSIONS.pop(identifier, None)
    if session:
        if WINE_RUNTIME:
            try:
                subprocess.run(['wineserver', '-k'], env=dict(os.environ, WINEPREFIX=str(profile_path(identifier) / 'wine')), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10)
            except subprocess.TimeoutExpired:
                pass
        try:
            version = read_json(session['cdp'], '/json/version')
            cdp(session['cdp'], version['webSocketDebuggerUrl'], 'Browser.close')
            session['processes'][-1].wait(timeout=5)
        except Exception:
            pass
        for process in reversed(session['processes']):
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    process.kill()
    if remove:
        path = profile_path(identifier)
        if path.exists():
            shutil.rmtree(path)


def start(identifier, provider):
    path = profile_path(identifier)
    if provider not in APPS:
        raise ValueError('Unsupported app')
    existing = SESSIONS.get(identifier)
    if existing and existing['provider'] != provider:
        raise ValueError('Profile provider mismatch')
    if existing and all(process.poll() is None for process in existing['processes']):
        return existing
    stop(identifier)
    used = {item['slot'] for item in SESSIONS.values()}
    slot = next((number for number in (range(20, 24) if WINE_RUNTIME else range(10, 14)) if number not in used), None)
    if slot is None:
        raise ValueError('Close another remote app first')
    path.mkdir(mode=0o700, exist_ok=True)
    marker = path / '.provider'
    if marker.exists() and marker.read_text() != provider:
        raise ValueError('Profile provider mismatch')
    marker.write_text(provider)
    environment = dict(os.environ, DISPLAY=f':{slot}')
    environment.pop('COMMUNICATION_BROWSER_TOKEN', None)
    processes = []
    session = {'provider': provider, 'slot': slot, 'cdp': 9230 + slot, 'vncPort': 5900 + slot, 'processes': processes}
    SESSIONS[identifier] = session
    def launch(args):
        process = subprocess.Popen(args, env=environment, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        processes.append(process)
    try:
        launch(['Xvnc', f':{slot}', '-geometry', '1600x900', '-depth', '24', '-SecurityTypes', 'None', '-localhost', 'yes', '-nolisten', 'tcp', '-AlwaysShared'])
        time.sleep(0.5)
        launch(['openbox'])
        if WINE_RUNTIME:
            environment['WINEPREFIX'] = str(path / 'wine')
            environment['COMMUNICATION_PROFILE'] = str(path)
            launch(['python3', '/usr/local/lib/wine_session.py'])
            return session
        launch(['chromium', '--no-sandbox', '--disable-dev-shm-usage', '--no-first-run', '--start-maximized', '--password-store=basic', '--user-data-dir=' + str(path), '--remote-debugging-port=' + str(session['cdp']), APPS[provider]])
        for _ in range(40):
            if any(process.poll() is not None for process in processes):
                raise ValueError('Application exited')
            try:
                read_json(session['cdp'], '/json/version')
                return session
            except Exception:
                time.sleep(0.25)
        raise ValueError('Application startup timed out')
    except Exception:
        stop(identifier)
        raise


def snapshot(identifier):
    session = SESSIONS.get(identifier)
    if not session:
        return {'state': 'STOPPED', 'nodes': [], 'structuredMessages': False}
    if WINE_RUNTIME:
        state_path = profile_path(identifier) / '.runtime-state'
        state = state_path.read_text() if state_path.exists() else 'STARTING'
        if any(process.poll() is not None for process in session['processes']):
            state = 'APPLICATION_EXITED'
        return {'state': state if state in ('STARTING', 'INSTALLING', 'RUNNING', 'ERROR', 'APPLICATION_EXITED') else 'ERROR', 'nodes': [], 'structuredMessages': False}
    pages = read_json(session['cdp'], '/json/list')
    expected = urllib.parse.urlsplit(APPS[session['provider']]).hostname
    page = next((page for page in pages if page.get('type') == 'page' and urllib.parse.urlsplit(page.get('url', '')).hostname == expected), None)
    # Login pages are not harvested; the owner uses the original remote screen.
    if not page:
        return {'state': 'LOGIN_OR_NAVIGATION_REQUIRED', 'nodes': [], 'structuredMessages': False}
    tree = cdp(session['cdp'], page['webSocketDebuggerUrl'], 'Accessibility.getFullAXTree')
    nodes = []
    for node in tree.get('nodes', []):
        role = node.get('role', {}).get('value', '')
        if not node.get('ignored') and role in ('StaticText', 'heading', 'link', 'button', 'listitem', 'row'):
            nodes.append({'role': role, 'name': str(node.get('name', {}).get('value', ''))[:2000]})
        if len(nodes) >= 300:
            break
    return {'state': 'SCREEN_AVAILABLE', 'nodes': nodes, 'structuredMessages': False}


class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def reply(self, status, value):
        body = json.dumps(value).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def dispatch(self):
        if len(TOKEN) < 32 or not hmac.compare_digest(self.headers.get('Authorization', ''), 'Bearer ' + TOKEN):
            return self.reply(403, {'error': 'Forbidden'})
        match = re.fullmatch(r'/profiles/([a-f0-9-]+)(/snapshot|/stop)?', self.path)
        if not match:
            return self.reply(404, {'error': 'Not found'})
        identifier = match.group(1)
        try:
            with LOCK:
                profile_path(identifier)
                if self.command == 'POST' and not match.group(2):
                    size = int(self.headers.get('Content-Length', '0'))
                    if not 0 < size <= 1024:
                        raise ValueError('Invalid body')
                    data = json.loads(self.rfile.read(size))
                    session = start(identifier, data.get('provider'))
                    return self.reply(200, {'vncPort': session['vncPort'], 'state': 'RUNNING'})
                if self.command == 'POST' and match.group(2) == '/stop':
                    stop(identifier)
                    return self.reply(200, {'state': 'STOPPED'})
                if self.command == 'GET' and match.group(2) == '/snapshot':
                    return self.reply(200, snapshot(identifier))
                if self.command == 'DELETE' and not match.group(2):
                    stop(identifier, remove=True)
                    return self.reply(200, {'state': 'DELETED'})
                self.reply(405, {'error': 'Unsupported operation'})
        except Exception:
            self.reply(409, {'error': 'Remote application is unavailable; check profile and runtime configuration.'})

    do_GET = dispatch
    do_POST = dispatch
    do_DELETE = dispatch


def shutdown(*_):
    with LOCK:
        for identifier in list(SESSIONS):
            stop(identifier)
    raise SystemExit(0)


if __name__ == '__main__':
    if WINE_RUNTIME:
        import fcntl
        ROOT.mkdir(parents=True, exist_ok=True, mode=0o700)
        lease = (ROOT / '.bridge.lock').open('w')
        fcntl.flock(lease, fcntl.LOCK_EX | fcntl.LOCK_NB)
        for _ in range(60):
            if TOKEN_PATH.is_file():
                break
            time.sleep(1)
    TOKEN = load_token()
    signal.signal(signal.SIGTERM, shutdown)
    signal.signal(signal.SIGINT, shutdown)
    http.server.ThreadingHTTPServer(('127.0.0.1', 9225 if WINE_RUNTIME else 9224), Handler).serve_forever()
