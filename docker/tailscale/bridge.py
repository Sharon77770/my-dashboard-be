"""Loopback-only, authenticated facade for three fixed Tailscale operations."""
import hmac
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

lock = threading.Lock()
login = None
login_url = ''
login_error = ''

def cli(*args):
    result = subprocess.run(['tailscale', *args], capture_output=True, timeout=8)
    if result.returncode: raise RuntimeError('Tailscale 명령에 실패했습니다. 데몬 상태를 확인하세요.')
    return result.stdout

def status():
    data = json.loads(cli('status', '--json'))
    own = data.get('Self') or {}
    state = data.get('BackendState', 'Unknown')
    with lock:
        return dict(state=state, hostname=own.get('HostName', ''), ips=own.get('TailscaleIPs') or [],
                    loginUrl='' if state == 'Running' else login_url,
                    pending=state != 'Running' and login is not None and login.poll() is None,
                    error='' if state == 'Running' else login_error)

def begin_login():
    global login, login_url, login_error
    if status()['state'] == 'Running': return
    with lock:
        if login is not None and login.poll() is None: return
        login_url, login_error = '', ''
        # Linux CLI only emits the authentication URL and waits for the user's approval.
        # Never invoke a browser, URL opener, or credential-based authentication here.
        login = subprocess.Popen(['tailscale', 'login', '--timeout=5m',
                                  '--hostname='+os.environ.get('TS_HOSTNAME', 'personal-dashboard'),
                                  '--accept-dns='+os.environ.get('TS_ACCEPT_DNS', 'true'),
                                  '--accept-routes='+os.environ.get('TS_ACCEPT_ROUTES', 'false')], stdout=subprocess.PIPE,
                                 stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
        process = login
    def read():
        global login_url, login_error
        try:
            for line in iter(lambda: process.stdout.readline(4096), b''):
                match = re.search(r'https://login\.tailscale\.com/a/[A-Za-z0-9]+', line.decode(errors='replace'))
                if match:
                    with lock:
                        if login is process: login_url = match.group(0)
            code = process.wait()
            with lock:
                if login is process and code:
                    login_url = ''
                    login_error = '로그인이 완료되지 않았습니다. 다시 시도하세요.'
        finally: process.stdout.close()
    threading.Thread(target=read, daemon=True).start()

def logout():
    global login, login_url, login_error
    with lock:
        process, login = login, None
        login_url, login_error = '', ''
    if process is not None and process.poll() is None:
        process.terminate()
        try: process.wait(timeout=2)
        except subprocess.TimeoutExpired: process.kill(); process.wait()
    cli('logout')

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *unused): pass
    def do_GET(self): self.handle_request()
    def do_POST(self): self.handle_request()
    def do_DELETE(self): self.handle_request()
    def handle_request(self):
        if not hmac.compare_digest(self.headers.get('Authorization', ''), 'Bearer '+self.server.token):
            self.send_error(403); return
        routes = {('GET', '/status'), ('POST', '/login'), ('DELETE', '/login')}
        if (self.command, self.path) not in routes: self.send_error(404); return
        try:
            if self.command == 'POST': begin_login()
            if self.command == 'DELETE': logout()
            body = json.dumps(status()).encode()
            self.send_response(200)
        except Exception:
            body = json.dumps(dict(error='Tailscale 데몬에 연결하지 못했습니다. 잠시 후 다시 시도하세요.')).encode()
            self.send_response(502)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers(); self.wfile.write(body)

if __name__ == '__main__':
    directory = Path('/run/dashboard-tailscale')
    directory.mkdir(parents=True, exist_ok=True)
    os.chmod(directory, 0o750); os.chown(directory, 0, 10001)
    token = secrets.token_hex(32)
    path = directory / 'bridge-token'
    path.write_text(token); os.chmod(path, 0o640); os.chown(path, 0, 10001)
    server = HTTPServer(('127.0.0.1', 41113), Handler)
    server.token = token
    server.serve_forever()
