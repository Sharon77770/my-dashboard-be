/* Explicit isolated-fixture acceptance, never run against a user's project directory. */
const fs=require('node:fs'),assert=require('node:assert/strict'),WebSocket=require('./node_modules/ws');
const base=process.env.STUDIO_TEST_URL||'http://127.0.0.1:18187';
const credentials=Object.fromEntries(fs.readFileSync(process.env.STUDIO_TEST_ENV||'.tools/studio-ide-qa.env','utf8').trim().split(/\r?\n/).map(line=>{const index=line.indexOf('=');return [line.slice(0,index),line.slice(index+1)];}));
const cookies=new Map();let csrf,project;
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function http(path,method='GET',body){const response=await fetch(base+path,{method,redirect:'manual',headers:{Cookie:[...cookies].map(([k,v])=>k+'='+v).join('; '),...(body?{'Content-Type':'application/json'}:{}),...(csrf?{'X-CSRF-TOKEN':csrf}:{})},body:body?JSON.stringify(body):undefined});for(const entry of response.headers.getSetCookie()){const cookie=entry.split(';')[0],i=cookie.indexOf('=');cookies.set(cookie.slice(0,i),cookie.slice(i+1));}return response;}
async function api(path,body,method='POST'){const response=await http('/api/v1'+path,method,body);if(!response.ok){const error=await response.json().catch(()=>({}));throw Error(path+' HTTP '+response.status+' '+(error.message||''));}return response.status===204?null:response.text().then(text=>text?JSON.parse(text):null);}
async function job(action,args={}){let value=await api('/studio/jobs',{...project,action,args});for(let i=0;value.state==='RUNNING'&&i<1800;i++){await pause(300);value=await api('/studio/jobs/'+value.id,undefined,'GET');}if(value.state!=='SUCCEEDED')throw Error(action+': '+value.state+' '+value.error);return value.result;}
async function until(action,predicate,attempts=40){let value;for(let i=0;i<attempts;i++){value=await action();if(predicate(value))return value;await pause(300);}throw Error('Fixture condition timed out');}
async function save(path,content){try{await job('create',{path});}catch(error){if(!String(error).includes('409')&&!String(error).includes('존재'))throw error;}const file=await job('read',{path});return job('save',{path,content,revision:file.revision});}
const app=`from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
def message(): return 'broken'
class Handler(BaseHTTPRequestHandler):
    def do_HEAD(self): self.send_response(200);self.end_headers()
    def do_GET(self):
        if self.path.startswith('/api'): body=json.dumps({'message':message()}).encode();mime='application/json';status=200
        elif self.path.startswith('/missing'): body=b'missing';mime='text/plain';status=404
        else: body=('<html><head><title>IDE fixture</title></head><body><main>'+message()+'</main><script>console.error("fixture console marker");fetch("/missing")</script></body></html>').encode();mime='text/html';status=200
        self.send_response(status);self.send_header('Content-Type',mime);self.end_headers();self.wfile.write(body)
    def do_POST(self):
        body=json.dumps({'auth':self.headers.get('Authorization'),'body':self.rfile.read(int(self.headers.get('Content-Length','0'))).decode()}).encode()
        self.send_response(200);self.end_headers();self.wfile.write(body)
if __name__=='__main__': ThreadingHTTPServer(('127.0.0.1',18765),Handler).serve_forever()
`;
async function main(){
  for(let attempt=0;attempt<60;attempt++){try{if((await fetch(base+'/health')).ok)break;}catch{}await pause(500);}
  const login=await(await http('/login')).text();const token=login.match(/name="_csrf" value="([^"]+)"/)[1];
  const response=await fetch(base+'/login',{method:'POST',redirect:'manual',headers:{Cookie:[...cookies].map(([k,v])=>k+'='+v).join('; '),'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({id:credentials.DASHBOARD_AUTH_ID,password:credentials.DASHBOARD_AUTH_PASSWORD,_csrf:token})});
  for(const entry of response.headers.getSetCookie()){const cookie=entry.split(';')[0],i=cookie.indexOf('=');cookies.set(cookie.slice(0,i),cookie.slice(i+1));}
  const home=await(await http('/')).text();csrf=home.match(/name="csrf-token" content="([^"]+)"/)[1];
  const workspace=await api('/workspace',undefined,'GET');project={deviceId:'local',root:workspace.devices.find(item=>item.id==='local').rootPath};
  if(process.env.STUDIO_TEST_SSH==='true'){const device=await api('/devices/ssh',{command:'ssh tester@'+(process.env.STUDIO_TEST_SSH_HOST||'localhost'),password:credentials.QA_SSH_PASSWORD});project={deviceId:device.id,root:device.rootPath};await job('setup');}
  const folder='ide-acceptance-'+Date.now();await job('mkdir',{path:folder});project.root+='/'+folder;
  console.log('PASS project open:',project.deviceId==='local'?'local Linux':'verified SSH');
  await save('app.py',app);await save('test_app.py',"import unittest\nfrom app import message\nclass AppTest(unittest.TestCase):\n    def test_message(self):self.assertEqual('fixed',message())\n");
  await job('git-init');await job('git-identity',{name:'IDE fixture',email:'fixture@example.invalid'});for(const path of ['app.py','test_app.py'])await job('git-stage',{path});await job('git-commit',{message:'Isolated acceptance baseline'});
  const runtime=await api('/sessions',{kind:'TERMINAL',targetId:project.deviceId,root:project.root,width:1200,height:480});
  const ws=new WebSocket(base.replace('http','ws')+'/ws/runtime/'+runtime.id,{headers:{Cookie:[...cookies].map(([k,v])=>k+'='+v).join('; '),Origin:base}});let output='';
  await new Promise((resolve,reject)=>{const timeout=setTimeout(()=>reject(Error('PTY ready timeout')),15000);ws.on('error',reject);ws.on('message',data=>{const text=data.toString();output+=text;if(text==='{"type":"ready"}'){clearTimeout(timeout);resolve();}});});
  ws.send(JSON.stringify({type:'input',data:'pwd; python3 app.py\r'}));
  await until(()=>job('ports'),value=>value.tools.ports.some(port=>port.port===18765&&port.protocol==='HTTP'&&port.project));assert(output.includes(project.root));console.log('PASS actual embedded-terminal backend: project cwd, dev server, automatic HTTP port detection');
  const browser=async(action,extra={})=>api('/studio/browser',{...project,action,...extra});
  await browser('open',{url:'http://127.0.0.1:18765/'});let preview=await until(()=>browser('snapshot'),value=>value.title==='IDE fixture'&&value.text.includes('broken')&&value.consoleErrors.some(error=>error.includes('fixture console marker'))&&value.networkFailures.length>0);
  assert(preview.image.startsWith('/9j/'));console.log('PASS actual Chromium screen, title/text, console error and failed HTTP request');
  await browser('open',{url:'http://127.0.0.1:18765/?view=two'});await until(()=>browser('snapshot'),value=>value.url.includes('view=two'));
  await browser('back');await until(()=>browser('snapshot'),value=>value.url==='http://127.0.0.1:18765/');
  await browser('forward');await until(()=>browser('snapshot'),value=>value.url.includes('view=two'));console.log('PASS Chromium URL, back and forward');
  const request={...project,method:'GET',url:'http://127.0.0.1:18765/api',params:[],headers:[],bodyType:'none',body:'',fields:[],auth:{type:'none'}};
  let exchange=await api('/studio/api/send',request);assert.equal(exchange.response.status,200);assert.equal(JSON.parse(exchange.response.body).message,'broken');console.log('PASS actual API request');
  assert.equal((await api('/studio/api/replay',{...project,id:exchange.id})).response.status,200);
  await api('/studio/api/environment',{...project,values:{TOKEN:'fixture-secret-do-not-print'}});
  const protectedResult=await api('/studio/api/send',{...request,method:'POST',auth:{type:'bearer',value:'{{TOKEN}}'},bodyType:'json',body:'{"check":true}'});assert(!protectedResult.response.body.includes('fixture-secret-do-not-print'));assert(protectedResult.response.body.includes('[redacted]'));assert.deepEqual(await api('/studio/api/environment/read',project),['TOKEN']);
  assert((await api('/studio/api/history',project)).length>=2);console.log('PASS encrypted environment resolution, response masking, history');
  let source=await job('read',{path:'app.py'});await job('save',{path:'app.py',revision:source.revision,content:source.content+'\n# manual save proof\n'});assert((await job('read',{path:'app.py'})).content.includes('manual save proof'));console.log('PASS real source save and reread');
  ws.send(JSON.stringify({type:'input',data:'\u0003'}));await until(()=>job('ports'),value=>!value.tools.ports.some(port=>port.port===18765&&port.protocol==='HTTP'));
  await api('/sessions/'+runtime.id,undefined,'DELETE');ws.close();
  const run=(await job('run-start',{name:'fixture app',content:'python3 app.py',mode:'run'})).tools.processes[0];
  const test=(await job('run-start',{name:'fixture tests',content:'python3 -m unittest -v',mode:'test'})).tools.processes[0];
  const failed=await until(()=>job('run-logs',{path:test.id}),value=>value.tools.processes[0].state==='FAILED');assert(failed.tools.output.includes('AssertionError'));console.log('PASS managed process and actual failing test output');
  if(process.env.STUDIO_TEST_CODEX==='true'){
    await job('setup');const auth=await job('codex-status');assert(auth.authenticated,'Codex target login required');
    const result=await job('codex-run',{prompt:'In this isolated fixture project, fix app.py message() to return fixed instead of broken. Preserve the manual save proof comment. Run python3 -m unittest -v to verify. Do not change tests or other files. This is an authorized acceptance test.',mode:'workspace-write',approval:'never',reviewer:'user',context:[{kind:'upload',name:'studio-runtime-observations.txt',content:JSON.stringify({browser:{url:preview.url,title:preview.title,text:preview.text,consoleErrors:preview.consoleErrors,networkFailures:preview.networkFailures},api:exchange.response,tests:failed.tools.output}).slice(0,64000)}]});
    assert.equal(result.assistant.status,'completed');assert((await job('read',{path:'app.py'})).content.includes("return 'fixed'")||(await job('read',{path:'app.py'})).content.includes('return "fixed"'));console.log('PASS actual authenticated Codex file modification');
  }else{
    source=await job('read',{path:'app.py'});await job('save',{path:'app.py',revision:source.revision,content:source.content.replace("return 'broken'","return 'fixed'")});console.log('NOT VERIFIED Codex edit: fixture correction made via editor save API');
  }
  await job('run-restart',{path:test.id});await until(()=>job('run-logs',{path:test.id}),value=>value.tools.processes[0].state==='SUCCEEDED');
  await job('run-restart',{path:run.id});await until(()=>job('ports'),value=>value.tools.ports.some(port=>port.port===18765&&port.protocol==='HTTP'));
  await browser('reload');preview=await until(()=>browser('snapshot'),value=>value.text.includes('fixed'));exchange=await api('/studio/api/send',request);assert.equal(JSON.parse(exchange.response.body).message,'fixed');
  const diff=await job('git-diff',{path:'app.py'});assert(diff.diff.includes('fixed'));console.log('PASS tests, process restart, Chromium/API revalidation, actual git diff');
  await job('run-stop',{path:run.id});await browser('close');console.log('Fixture project:',project.root);
}
main().then(()=>process.exit(0)).catch(error=>{console.error('FAIL',error.message);process.exit(1);});
