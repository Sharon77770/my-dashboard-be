import assert from 'node:assert/strict';

import {writeFile} from 'node:fs/promises';
/** Actual UI actions; API reads only establish file/diff evidence after those actions. */
export async function verifyStudioFlow(page, {base, projectRoot}) {
  const observedTools=new Map();
  page.on('response',async response=>{
    if(!/\/api\/v1\/studio\/jobs\/[^/]+$/.test(response.url())||response.request().method()!=='GET')return;
    try{
      const value=await response.json();if(value.action!=='codex-run')return;
      for(const event of value.events||[]){const item=event.assistant?.item;if(item?.type==='dynamicToolCall')observedTools.set(item.id,item);}
    }catch{}
  });
  const deviceId=await page.locator('#studio-device').inputValue();
  const project={deviceId,root:projectRoot};
  const csrf=await page.locator('meta[name=csrf-token]').getAttribute('content');
  async function job(action,args={},retry=0){
    const response=await page.request.post(base+'/api/v1/studio/jobs',{headers:{'X-CSRF-TOKEN':csrf},data:{...project,action,args}});
    assert(response.ok(),'Evidence job creation failed: '+action);
    let value=await response.json();
    for(let attempt=0;value.state==='RUNNING'&&attempt<300;attempt++){
      await page.waitForTimeout(200);value=await(await page.request.get(base+'/api/v1/studio/jobs/'+value.id)).json();
    }
    if(value.state==='FAILED'&&value.error?.includes('다른 작업이 진행 중')&&retry<30){await page.waitForTimeout(500);return job(action,args,retry+1);}
    assert.equal(value.state,'SUCCEEDED',action+': '+value.error);return value.result;
  }
  async function edit(content){
    await page.locator('#studio-tree [data-studio=file][data-path="app.py"]').click();
    await page.locator('#studio-code textarea.inputarea').focus();
    await page.keyboard.press('Control+A');
    await page.context().grantPermissions(['clipboard-read','clipboard-write'],{origin:base});
    await page.evaluate(text=>navigator.clipboard.writeText(text),content);
    await page.keyboard.press('Control+V');
    await page.waitForFunction(()=>document.querySelector('#studio-dirty').textContent.includes('저장하지'));
    await page.keyboard.press('Control+S');
    await page.waitForFunction(()=>document.querySelector('#studio-dirty').textContent.includes('저장됨'));
    assert.equal((await job('read',{path:'app.py'})).content,content,'Ctrl+S must save exact editor content');
  }
  async function shell(command){
    await page.locator('[data-bottom=Terminal]').click();
    await page.locator('.xterm-helper-textarea').last().focus();await page.keyboard.type(command);await page.keyboard.press('Enter');
  }
  async function run(name,command,kind){
    await page.locator('[data-bottom=Output]').click();
    const form=page.locator('[data-bottom-page=Output] form');
    await form.locator('[name=name]').fill(name);await form.locator('[name=command]').fill(command);await form.locator('[name=kind]').selectOption(kind);await form.locator('button:not([type])').click();
  }
  async function api(expected){
    await page.locator('[data-bottom=API]').click();await page.locator('.studio-api-form [name=url]').fill('http://127.0.0.1:18765/api');
    const sent=page.waitForResponse(response=>response.url().endsWith('/api/v1/studio/api/send')&&response.request().method()==='POST');
    await page.locator('[data-api-send]').click();const response=await sent;assert(response.ok(),'API send failed');
    assert.equal(JSON.parse((await response.json()).response.body).message,expected);
    await page.locator('[data-response-body]').filter({hasText:expected}).waitFor();
  }
  // Stop only this harness's previous managed servers before reusing its fixture.
  const previous=(await job('run-list')).tools?.processes||[];
  await page.locator('[data-bottom=Output]').click();
  for(const process of previous.filter(item=>item.name.startsWith('UI app ')&&item.state==='RUNNING')){
    await page.locator('[data-bottom-page=Output] [data-run=stop][data-id="'+process.id+'"]').click();
  }
  const initial=`from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
def message(): return 'fixed'
class Handler(BaseHTTPRequestHandler):
    def do_HEAD(self): self.send_response(200);self.end_headers()
    def do_GET(self):
        if self.path.startswith('/api'): body=json.dumps({'message':message()}).encode();mime='application/json';status=200
        elif self.path.startswith('/missing'): body=b'missing';mime='text/plain';status=404
        else: body=('<html><head><title>IDE fixture</title></head><body><main>'+message()+'</main><input id="echo" autofocus><output id="echo-output"></output><script>document.getElementById("echo").addEventListener("input",event=>document.getElementById("echo-output").textContent=event.target.value);console.error("fixture console marker");fetch("/missing")</script></body></html>').encode();mime='text/html';status=200
        self.send_response(status);self.send_header('Content-Type',mime);self.end_headers();self.wfile.write(body)
if __name__=='__main__': ThreadingHTTPServer(('127.0.0.1',18765),Handler).serve_forever()
`;
  const broken=initial.replace(/return ['"]fixed['"]/,"return 'broken'")+'\n# UI fixture run '+Date.now()+'\n';
  assert(broken.includes("return 'broken'"));
  await edit(broken);
  console.log('PASS UI Monaco edit and Ctrl+S save');
  await shell('python3 app.py');
  await page.locator('[data-bottom=Ports]').click();
  await page.locator('[data-port-preview="18765"]').waitFor({timeout:30000});
  const preview=page.waitForResponse(response=>response.url().endsWith('/studio/browser')&&response.request().postDataJSON()?.action==='open');
  await page.locator('[data-port-preview="18765"]').click();
  const previewResponse=await preview;assert(previewResponse.ok(),'Preview open failed: '+(previewResponse.ok()?'':(await previewResponse.json()).message));
  // Navigation can return before the page has painted; the visible panel polls snapshots.
  if(!(await previewResponse.json()).text.includes('broken'))await page.waitForResponse(async response=>{
    if(!response.url().endsWith('/studio/browser')||response.request().postDataJSON()?.action!=='snapshot')return false;
    return response.ok()&&(await response.json()).text?.includes('broken');
  },{timeout:30000});
  await page.locator('.studio-browser-screen').waitFor({state:'visible'});
  await page.locator('.studio-browser-text input').focus();
  const ime=await page.context().newCDPSession(page);
  await ime.send('Input.imeSetComposition',{text:'한글 미리보기 검증',selectionStart:10,selectionEnd:10});
  await ime.send('Input.insertText',{text:'한글 미리보기 검증'});
  await ime.detach();
  const inserted=page.waitForResponse(response=>response.url().endsWith('/studio/browser')&&response.request().postDataJSON()?.action==='text');
  await page.locator('.studio-browser-text button').click();
  assert((await(await inserted).json()).text.includes('한글 미리보기 검증'),'Korean input did not reach Chromium page');
  await page.locator('[data-bottom-page=Browser] [name=url]').fill('http://127.0.0.1:18765/second');
  const navigated=page.waitForResponse(response=>response.url().endsWith('/studio/browser')&&response.request().postDataJSON()?.action==='open');
  await page.locator('[data-bottom-page=Browser] form').first().locator('button:not([type])').click();
  assert((await navigated).ok(),'Second Preview navigation failed');
  for(const action of ['back','forward','reload','reload','reload']){
    const response=page.waitForResponse(response=>response.url().endsWith('/studio/browser')&&response.request().postDataJSON()?.action===action);
    await page.locator('[data-browser='+action+']').click();assert((await response).ok(),'Preview '+action+' failed');
  }
  await api('broken');
  await edit(broken+'\n# Playwright Korean save proof: 한글 입력 검증\n');
  console.log('PASS UI terminal dev server, detected port, real Chromium Preview, API and Korean source save');
  const suffix=Date.now().toString();const testName='UI tests '+suffix,appName='UI app '+suffix;
  await run(testName,'python3 -m unittest -v','test');
  await page.locator('[data-bottom-page=Output] [data-run=logs]').filter({hasText:testName+' · FAILED'}).waitFor({timeout:30000});
  await page.locator('[data-bottom=Problems]').click();
  await page.locator('[data-bottom-page=Problems]>button').filter({hasText:'test_app.py'}).first().click();
  await page.locator('#studio-file-label').filter({hasText:'test_app.py'}).waitFor();
  console.log('PASS UI failing tests and Problems file navigation');
  await page.locator('[data-bottom=Terminal]').click();await page.locator('.xterm-helper-textarea').last().focus();await page.keyboard.press('Control+C');
  await page.waitForTimeout(500);await run(appName,'python3 app.py','run');
  await page.locator('[data-bottom-page=Output] [data-run=logs]').filter({hasText:appName+' · RUNNING'}).waitFor();
  await page.locator('[data-studio-panel=codex]').click();
  await page.waitForFunction(()=>!document.querySelector('#studio-prompt').disabled&&document.querySelector('#studio-auth-cta').hidden&&!document.querySelector('#cx-account').textContent.includes('계정 확인 전'),{},{timeout:120000});
  await page.locator('#studio-codex [data-cx=settings]').click();
  await page.locator('#studio-codex-mode').selectOption('workspace-write');
  await page.locator('#cx-approval').selectOption('never');
  await page.locator('#studio-codex [data-cx=settings-close]').click();
  await page.locator('#studio-codex [data-cx=new]').click();
  const completion=page.waitForResponse(async response=>{
    if(!/\/api\/v1\/studio\/jobs\/[^/]+$/.test(response.url())||response.request().method()!=='GET')return false;
    try{const value=await response.json();return value.action==='codex-run'&&value.state!=='RUNNING';}catch{return false;}
  },{timeout:240000});
  await page.locator('#studio-prompt').fill('This is an isolated authorized UI acceptance fixture. You MUST use studio_browser open http://127.0.0.1:18765 and studio_api GET http://127.0.0.1:18765/api to reproduce broken before editing. Fix only app.py message() from broken to fixed. Preserve all comments including Korean. Run python3 -m unittest -v. Use studio_process list and restart the existing process named '+appName+'. Then use studio_browser reload/snapshot and studio_api again to verify fixed. Do not stop the server after verification. Report actual results.');
  await page.locator('#cx-send').click();
  const codex=await(await completion).json();assert.equal(codex.state,'SUCCEEDED',codex.error);
  const toolItems=[...observedTools.values(),...(codex.events||[]).map(event=>event.assistant?.item),...(codex.result?.assistant?.thread?.turns||[]).flatMap(turn=>turn.items||[])].filter(item=>item?.type==='dynamicToolCall');
  const toolNames=new Set(toolItems.map(item=>item.tool));
  await writeFile('artifacts/studio-codex-proof.json',JSON.stringify({state:codex.state,tools:[...toolNames],items:toolItems,summary:codex.result?.assistant?.thread?.turns?.at(-1)?.items?.filter(item=>item.type==='agentMessage')},null,2));
  for(const name of ['studio_browser','studio_api','studio_process'])assert(toolNames.has(name),'Codex did not use '+name);
  const fixed=(await job('read',{path:'app.py'})).content;
  assert.match(fixed,/return ['"]fixed['"]/);assert(fixed.includes('한글 입력 검증'));
  console.log('PASS UI Codex request and actual SSH file modification');
  await page.locator('[data-bottom=Tests]').click();
  const testRow=page.locator('[data-bottom-page=Tests] .studio-process-row').filter({hasText:testName});
  await testRow.locator('[data-run=restart]').click();
  await page.locator('[data-bottom=Tests]').click();
  await testRow.locator('[data-run=logs]').filter({hasText:'SUCCEEDED'}).waitFor({timeout:30000});
  await page.locator('[data-bottom=Browser]').click();
  const reloaded=page.waitForResponse(response=>response.url().endsWith('/studio/browser')&&response.request().postDataJSON()?.action==='reload');
  await page.locator('[data-browser=reload]').click();const reloadResponse=await reloaded;const result=await reloadResponse.json();assert(reloadResponse.ok(),'Preview reload: '+JSON.stringify(result));
  if(!result.text?.includes('fixed'))await page.waitForResponse(async response=>{
    if(!response.url().endsWith('/studio/browser')||response.request().postDataJSON()?.action!=='snapshot')return false;
    return response.ok()&&(await response.json()).text?.includes('fixed');
  },{timeout:30000});
  await api('fixed');
  await page.locator('[data-studio-panel=git]').click();await page.locator('[data-studio=git-refresh]').click();
  await page.locator('[data-studio=git-diff][data-path="app.py"]').click();
  await page.locator('#studio-diff').filter({hasText:'fixed'}).waitFor({state:'visible'});
  assert((await job('git-diff',{path:'app.py'})).diff.includes('fixed'));
  console.log('PASS UI test rerun, process restart, Browser/API verification and actual Git diff');
  const languageRuns=[];
  for(const [language,command] of [['maven','mvn test package'],['gradle','gradle test build'],['npm','npm test && npm run build'],['pytest','python3 -m pytest && python3 -m compileall -q app.py']]){
    const name='UI '+language+' '+suffix;
    await run(name,'cd '+language+' && '+command,'test');
    await page.locator('[data-bottom-page=Output] [data-run=logs]').filter({hasText:name+' · SUCCEEDED'}).waitFor({timeout:120000});
    languageRuns.push(language);console.log('PASS UI '+language+' test/build');
  }
  const appRow=page.locator('[data-bottom-page=Output] .studio-process-row').filter({hasText:appName});
  await appRow.locator('[data-run=stop]').click();
  await appRow.locator('[data-run=logs]').filter({hasText:'STOPPED'}).waitFor({timeout:30000});
  await page.locator('[data-bottom=Terminal]').click();
  return {project:projectRoot,sourceSaved:true,codexModified:true,codexTools:[...toolNames],koreanPreviewInput:true,languageRuns,testsPassed:true,browserVerified:true,apiVerified:true,gitDiffVerified:true};
}
