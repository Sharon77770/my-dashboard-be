import base64
import json
from pathlib import Path
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def do_GET(self):
        body=json.dumps(dict(path=self.path,auth=self.headers.get('Authorization'),body=self.rfile.read(int(self.headers.get('Content-Length','0'))).decode(errors='replace'))).encode()
        self.send_response(201);self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(body)
    do_POST=do_PUT=do_PATCH=do_DELETE=do_OPTIONS=do_GET
    def do_HEAD(self):self.send_response(200);self.end_headers()


class StudioApiTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server=ThreadingHTTPServer(('127.0.0.1',0),Handler);cls.thread=threading.Thread(target=cls.server.serve_forever,daemon=True);cls.thread.start()
        source=Path('src/main/resources/studio/remote.py').read_text().replace('# API_BRIDGE',Path('src/main/resources/studio/api_client.py').read_text())
        cls.module={'__name__':'fixture'};exec(compile(source,'remote.py','exec'),cls.module)
    @classmethod
    def tearDownClass(cls):cls.server.shutdown();cls.server.server_close();cls.thread.join()
    def request(self,**args):
        value=dict(url='http://127.0.0.1:'+str(self.server.server_port)+'/echo',method='GET',headers=[],params=[],bodyType='none',fields=[]);value.update(args)
        return self.module['api_request'](dict(content=json.dumps(value)))['api']
    def test_methods_params_auth_json_and_form(self):
        for method in ('GET','POST','PUT','PATCH','DELETE','OPTIONS'):
            result=self.request(method=method,params=[dict(name='q',value='a & b')],auth=dict(type='bearer',value='fixture-only'),bodyType='json',body='{"ok":true}')
            echo=json.loads(result['body']);self.assertEqual(201,result['status']);self.assertEqual('Bearer fixture-only',echo['auth']);self.assertIn('q=a+%26+b',echo['path']);self.assertEqual('{"ok":true}',echo['body'])
        self.assertEqual(200,self.request(method='HEAD')['status'])
        self.assertEqual(201,self.request(params=None,headers=None,fields=None)['status'])
        echo=json.loads(self.request(method='POST',bodyType='form',fields=[dict(name='a',value='b c')])['body']);self.assertEqual('a=b+c',echo['body'])
    def test_multipart_and_rejections(self):
        result=self.request(method='POST',bodyType='multipart',fields=[dict(name='file',fileName='test.txt',value=base64.b64encode(b'actual file').decode())])
        self.assertIn('actual file',json.loads(result['body'])['body'])
        for options in (dict(url='file:///etc/passwd'),dict(headers=[dict(name='x',value='a\r\nb')]),dict(auth=dict(type='bearer',value='a\r\nb')),dict(bodyType='json',body='invalid')):
            with self.assertRaises(self.module['Failure']):self.request(**options)


if __name__=='__main__':unittest.main()
