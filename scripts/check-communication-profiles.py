"""Run inside the isolated browser container. Only fixture cookies are created/read."""
import importlib.util
import json
import os
import urllib.request
import uuid
import socket
import time

spec=importlib.util.spec_from_file_location('bridge','/usr/local/lib/communication_bridge.py')
bridge=importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)
identifiers=[str(uuid.uuid4()),str(uuid.uuid4())]
def request(identifier,method='POST',body=None):
 data=json.dumps(body).encode() if body is not None else None
 req=urllib.request.Request('http://127.0.0.1:9224/profiles/'+identifier,data=data,method=method,headers={'Authorization':'Bearer '+open('/run/communication-bridge/token').read(),'Content-Type':'application/json'})
 with urllib.request.urlopen(req,timeout=20) as response:return json.load(response)
for attempt in range(40):
 try:
  with socket.create_connection(('127.0.0.1',9224),timeout=1):pass
  break
 except OSError:time.sleep(0.5)
try:
 sessions=[request(identifier,body={'provider':'GMAIL'}) for identifier in identifiers]
 pages=[]
 for session in sessions:
  port=session['vncPort']-5900+9230
  page=next(p for p in bridge.read_json(port,'/json/list') if p.get('type')=='page')
  pages.append((port,page['webSocketDebuggerUrl']))
 marker=uuid.uuid4().hex
 bridge.cdp(*pages[0],'Network.setCookie',{'name':'communication-isolation-fixture','value':marker,'url':'https://communication-fixture.invalid/','expires':2000000000})
 first=bridge.cdp(*pages[0],'Network.getCookies',{'urls':['https://communication-fixture.invalid/']})
 second=bridge.cdp(*pages[1],'Network.getCookies',{'urls':['https://communication-fixture.invalid/']})
 assert any(cookie['value']==marker for cookie in first['cookies'])
 assert not second['cookies'], 'Cookie leaked between profiles'
 request(identifiers[0]+'/stop','POST')
 restarted=request(identifiers[0],body={'provider':'GMAIL'})
 port=restarted['vncPort']-5900+9230
 page=next(p for p in bridge.read_json(port,'/json/list') if p.get('type')=='page')
 retained=bridge.cdp(port,page['webSocketDebuggerUrl'],'Network.getCookies',{'urls':['https://communication-fixture.invalid/']})
 assert any(cookie['value']==marker for cookie in retained['cookies']), 'Cookie lost on graceful restart'
 print('PASS actual Chromium profile cookie isolation; independent VNC ports; persisted cookie on stop/start; no real provider login used.')
finally:
 for identifier in identifiers:
  try:request(identifier,'DELETE')
  except Exception:pass
