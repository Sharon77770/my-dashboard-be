import io
import json
import threading
import unittest
from pathlib import Path
from unittest.mock import patch, MagicMock
from urllib.request import Request, urlopen
from urllib.error import HTTPError
import bridge

class BridgeTest(unittest.TestCase):
    def setUp(self):
        bridge.login=None;bridge.login_url='';bridge.login_error=''
    def test_status_minimal_projection_and_connected_url_hidden(self):
        bridge.login_url='https://login.tailscale.com/a/test'
        bridge.login=MagicMock();bridge.login.poll.return_value=None
        bridge.login_error='old failure'
        with patch.object(bridge, 'cli', return_value=json.dumps({'BackendState':'Running','Self':{'HostName':'fixture','TailscaleIPs':['100.64.1.2']},'PrivateKey':'hidden','Peer':{'secret':'hidden'}}).encode()):
            result=bridge.status()
        self.assertEqual(result['loginUrl'],'')
        self.assertFalse(result['pending'])
        self.assertEqual(result['error'],'')
        self.assertEqual(result['ips'],['100.64.1.2'])
        self.assertNotIn('hidden',json.dumps(result))
    def test_pending_login_is_idempotent(self):
        bridge.login=MagicMock();bridge.login.poll.return_value=None
        with patch.object(bridge,'status',return_value={'state':'NeedsLogin'}), patch.object(bridge.subprocess,'Popen') as spawn:
            bridge.begin_login();spawn.assert_not_called()
    def test_startup_does_not_initiate_authentication(self):
        script=Path(__file__).with_name('start.sh').read_text()
        self.assertNotIn('tailscale up',script)
        self.assertNotIn('tailscale login',script)
        self.assertNotIn('--auth-key',script)
        self.assertIn('tailscaled --state=',script)
    def test_login_only_emits_url_without_browser_or_credentials(self):
        process=MagicMock();process.stdout=io.BytesIO(b'https://login.tailscale.com/a/fixture\n');process.wait.return_value=0
        with patch.object(bridge,'status',return_value={'state':'NeedsLogin'}),patch.object(bridge.subprocess,'Popen',return_value=process) as spawn:
            bridge.begin_login()
            for thread in threading.enumerate():
                if thread is not threading.current_thread() and thread.name.startswith('Thread'):thread.join(timeout=1)
            spawn.assert_called_once()
            command=spawn.call_args.args[0]
            self.assertEqual(command[:3],['tailscale','login','--timeout=5m'])
            self.assertEqual(len(command),6)
            self.assertEqual(spawn.call_args.kwargs['stdin'],bridge.subprocess.DEVNULL)
            self.assertNotIn('shell',spawn.call_args.kwargs)
        self.assertEqual(bridge.login_url,'https://login.tailscale.com/a/fixture')
    def test_failure_clears_url_and_fixed_command(self):
        process=MagicMock();process.stdout=io.BytesIO(b'https://login.tailscale.com/a/fixture\n');process.wait.return_value=1
        with patch.object(bridge,'status',return_value={'state':'NeedsLogin'}),patch.object(bridge.subprocess,'Popen',return_value=process) as spawn:
            bridge.begin_login()
            for thread in threading.enumerate():
                if thread is not threading.current_thread() and thread.name.startswith('Thread'):thread.join(timeout=1)
            self.assertEqual(spawn.call_args.args[0][:3],['tailscale','login','--timeout=5m'])
        self.assertEqual(bridge.login_url,'');self.assertTrue(bridge.login_error)
    def test_http_rejects_missing_token_and_unknown_command(self):
        server=bridge.HTTPServer(('127.0.0.1',0),bridge.Handler);server.token='fixture'
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        base='http://127.0.0.1:'+str(server.server_port)
        try:
            for path,headers,code in [('/status',{},403),('/shell',{'Authorization':'Bearer fixture'},404)]:
                with self.assertRaises(HTTPError) as error:urlopen(Request(base+path,headers=headers),timeout=2)
                self.assertEqual(error.exception.code,code)
            with patch.object(bridge,'status',return_value={'state':'NeedsLogin'}):
                with urlopen(Request(base+'/status',headers={'Authorization':'Bearer fixture'}),timeout=2) as response:
                    self.assertEqual(json.load(response)['state'],'NeedsLogin')
                    self.assertEqual(response.headers['Cache-Control'],'no-store')
        finally:server.shutdown();server.server_close();thread.join()

if __name__=='__main__':unittest.main()
