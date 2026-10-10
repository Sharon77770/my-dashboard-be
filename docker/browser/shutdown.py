"""Ask the loopback Chromium process to flush its profile and close normally."""
import base64
import hashlib
import json
import os
import socket
import sys
import urllib.parse
import urllib.request


def close_browser():
    # No browser cookies, page contents or credential files are read here.
    with urllib.request.urlopen('http://127.0.0.1:9222/json/version', timeout=1) as response:
        endpoint = urllib.parse.urlsplit(json.load(response)['webSocketDebuggerUrl'])
    if (endpoint.scheme != 'ws' or endpoint.hostname != '127.0.0.1'
            or endpoint.port != 9222 or not endpoint.path.startswith('/devtools/browser/')):
        raise ValueError('Unexpected browser endpoint')
    with socket.create_connection(('127.0.0.1', 9222), timeout=1) as connection:
        key = base64.b64encode(os.urandom(16)).decode('ascii')
        request = (f'GET {endpoint.path} HTTP/1.1\r\nHost: 127.0.0.1:9222\r\n'
                   f'Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: {key}\r\n'
                   'Sec-WebSocket-Version: 13\r\n\r\n')
        connection.sendall(request.encode('ascii'))
        headers = b''
        while b'\r\n\r\n' not in headers:
            chunk = connection.recv(1024)
            if not chunk or len(headers) > 8192:
                raise ValueError('Invalid websocket handshake')
            headers += chunk
        accept = base64.b64encode(hashlib.sha1(
            (key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode('ascii')).digest())
        if not headers.startswith(b'HTTP/1.1 101 ') or accept not in headers:
            raise ValueError('Websocket handshake rejected')
        payload = b'{"id":1,"method":"Browser.close"}'
        mask = os.urandom(4)
        frame = bytes([0x81, 0x80 | len(payload)]) + mask
        frame += bytes(value ^ mask[index % 4] for index, value in enumerate(payload))
        connection.sendall(frame)
        # The shell waits for process exit before terminating X; do not kill it here.
        try:
            connection.recv(1024)
        except socket.timeout:
            pass


if __name__ == '__main__':
    try:
        close_browser()
    except Exception:
        # Startup failure and an already-closed browser use the shell's bounded fallback.
        sys.exit(1)
