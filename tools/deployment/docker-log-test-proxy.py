"""Test-only Docker API proxy: permits reads of one named fixture and no Docker mutations."""
import http.client
import http.server
import os
import re
import socket
import urllib.parse

FIXTURE_ID = os.environ.get('FIXTURE_CONTAINER_ID', '')
assert re.fullmatch(r'[a-f0-9]{64}', FIXTURE_ID), 'An exact fixture container ID is required'


class DockerConnection(http.client.HTTPConnection):
    def connect(self):
        self.sock = socket.socket(socket.AF_UNIX)
        self.sock.settimeout(10)
        self.sock.connect('/var/run/docker.sock')


class ReadOnlyFixtureProxy(http.server.BaseHTTPRequestHandler):
    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        path = urllib.parse.urlsplit(self.path).path
        path = re.sub(r'^/v\d+\.\d+', '', path)
        if path not in ('/_ping', '/version', '/containers/dashboard-codex-log-fixture/json',
                        '/containers/dashboard-codex-log-fixture/logs',
                        '/containers/' + FIXTURE_ID + '/logs',
                        '/containers/dashboard-codex-log-does-not-exist/json'):
            self.send_error(403)
            return
        connection = DockerConnection('localhost')
        try:
            connection.request(self.command, self.path)
            response = connection.getresponse()
            self.send_response(response.status)
            for key, value in response.getheaders():
                if key.lower() in ('content-type', 'api-version', 'docker-experimental', 'ostype'):
                    self.send_header(key, value)
            self.end_headers()
            if self.command != 'HEAD':
                while True:
                    data = response.read(16384)
                    if not data: break
                    self.wfile.write(data)
        finally:
            connection.close()

    def log_message(self, *args):
        pass


http.server.ThreadingHTTPServer(('0.0.0.0', 2375), ReadOnlyFixtureProxy).serve_forever()
