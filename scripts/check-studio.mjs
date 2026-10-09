import { chromium } from 'playwright';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import assert from 'node:assert/strict';

// Read only the generated fixture credentials, never the deployment .env.
const base = process.env.STUDIO_TEST_URL || 'http://127.0.0.1:18187';
const credentials = Object.fromEntries((await readFile(process.env.STUDIO_TEST_ENV || '.tools/studio-ide-qa.env', 'utf8')).trim().split(/\r?\n/).map(line => {
  const separator = line.indexOf('=');
  return [line.slice(0, separator), line.slice(separator + 1)];
}));
const fixtureBytes = process.env.STUDIO_TEST_ROOT ? Buffer.alloc(0) : await readFile('.tools/studio-acceptance-languages.log');
const fixtureLog = fixtureBytes.toString(fixtureBytes[0] === 255 && fixtureBytes[1] === 254 ? 'utf16le' : 'utf8');
const projectRoot = process.env.STUDIO_TEST_ROOT || fixtureLog.match(/Fixture project: (.+)/)?.[1].trim();
assert(projectRoot, 'Prepare an isolated acceptance fixture first');
await mkdir('artifacts', { recursive: true });
const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
page.setDefaultTimeout(15000);
const logs = [], pageErrors = [], failedRequests = [], measurements = [];
let terminalOutput = '';
page.on('websocket', socket => { if(socket.url().includes('/ws/runtime/')) socket.on('framereceived', frame => { terminalOutput = (terminalOutput + String(frame.payload)).slice(-16000); }); });
const redact = value => Object.entries(credentials).filter(([key,value])=>/PASSWORD|TOKEN|SECRET/.test(key)&&value).reduce((text, [,secret]) => text.replaceAll(secret, '[redacted]'), String(value));
page.on('console', message => logs.push({ type: message.type(), text: redact(message.text()) }));
page.on('pageerror', error => pageErrors.push(redact(error.message)));
page.on('requestfailed', request => failedRequests.push({ url: request.url().split('?')[0], error: request.failure()?.errorText }));
async function measure(label) {
  const result = await page.evaluate(() => {
    const box = selector => {
      const element = document.querySelector(selector), rect = element?.getBoundingClientRect();
      return rect ? { x: rect.x, y: rect.y, width: rect.width, height: rect.height, right: rect.right, bottom: rect.bottom, scrollWidth: element.scrollWidth, clientWidth: element.clientWidth } : null;
    };
    return { mobilePane:document.querySelector('.studio-workbench')?.dataset.mobilePane, viewport: { width: innerWidth, height: innerHeight }, body: { width: document.documentElement.scrollWidth, height: document.documentElement.scrollHeight }, workbench: box('.studio-workbench'), editor: box('.studio-editor'), explorer: box('.studio-explorer'), inspector: box('.studio-inspector'), panel: box('.studio-bottom'), terminalScreen: box('.studio-terminal-session:not([hidden]) .studio-terminal-screen'), terminalSidebar: box('.studio-terminal-sidebar'), terminalPage: box('[data-bottom-page=Terminal]'), panelInEditor: document.querySelector('.studio-bottom')?.parentElement.matches('.studio-editor') };
  });
  measurements.push({ label, ...result });return result;
}
let targetId;
async function openProject(){
  await page.locator('.activity-rail [data-view=studio]').click();
  await page.locator('#studio-device option').nth(1).waitFor({ state: 'attached' });
  const target = await page.locator('#studio-device option').evaluateAll(options => options.find(option => option.value !== 'local')?.value);
  assert(target, 'Isolated SSH fixture must be registered');targetId=target;
  await page.locator('#studio-device').selectOption(target);
  await page.locator('.studio-project-menu').evaluate(element => { element.open = true; });
  await page.locator('#studio-root').fill(projectRoot);
  await page.locator('#studio-connect-form button[type=submit]').click();
  await page.locator('.studio-workbench').waitFor({ state: 'visible', timeout: 120000 });
  await page.locator('#studio-tree [data-studio=file][data-path="app.py"]').click();
  await page.locator('#studio-code .monaco-editor').waitFor({ state: 'visible' });
}
let failure, acceptance;
try {
  console.log('CHECK login and project');
  for(let attempt=0;attempt<60;attempt++){
    try { if((await fetch(base+'/health',{signal:AbortSignal.timeout(1000)})).ok)break; } catch {}
    await new Promise(resolve=>setTimeout(resolve,500));
  }
  await page.goto(base, { waitUntil: 'domcontentloaded' });
  if (page.url().includes('/login')) {
    await page.locator('#id').fill(credentials.DASHBOARD_AUTH_ID);
    await page.locator('#password').fill(credentials.DASHBOARD_AUTH_PASSWORD);
    await Promise.all([page.waitForURL(url => !url.pathname.startsWith('/login')), page.locator('button[type=submit]').click()]);
  }
  await openProject();
  console.log('CHECK editor and terminal');
  await page.locator('[data-studio=terminal]').click();
  await page.locator('.studio-terminal-control [role=status]').filter({ hasText: projectRoot }).waitFor({ timeout: 30000 });
  await page.locator('.xterm-helper-textarea').last().focus();
  await page.keyboard.type("printf '\\123TUDIO_LAYOUT_READY\\n'");await page.keyboard.press('Enter');
  for(let attempt=0;attempt<50&&!terminalOutput.includes('STUDIO_LAYOUT_READY');attempt++)await new Promise(resolve=>setTimeout(resolve,100));
  assert(terminalOutput.includes('STUDIO_LAYOUT_READY'), 'PTY did not execute the typed command');
  const beforeSessions=await(await page.request.get(base+'/api/v1/sessions?'+new URLSearchParams({deviceId:targetId,root:projectRoot}))).json();
  function targetDevice(){return targetId;}
  const retainedId=beforeSessions.find(item=>item.attached)?.id;assert(retainedId,'Expected attached terminal');
  await page.keyboard.type('export STUDIO_RECOVERY_VALUE=retained_'+Date.now());await page.keyboard.press('Enter');
  terminalOutput='';await page.keyboard.type("printf '\\123TUDIO_PID=%s\\n' \"$$\"");await page.keyboard.press('Enter');
  for(let i=0;i<50&&!terminalOutput.match(/STUDIO_PID=(\d+)/);i++)await page.waitForTimeout(100);
  const retainedPid=terminalOutput.match(/STUDIO_PID=(\d+)/)?.[1];assert(retainedPid);
  await page.reload({waitUntil:'domcontentloaded'});await openProject();
  await page.locator('[data-bottom=Terminal]').click();
  await page.locator('.studio-terminal-control [role=status]').filter({hasText:projectRoot}).waitFor({timeout:30000});
  terminalOutput='';await page.locator('.xterm-helper-textarea').last().focus();
  await page.keyboard.type("printf '\\123TUDIO_RESTORED=%s:%s\\n' \"$$\" \"$STUDIO_RECOVERY_VALUE\"");await page.keyboard.press('Enter');
  for(let i=0;i<50&&!terminalOutput.includes('STUDIO_RESTORED='+retainedPid+':retained_');i++)await page.waitForTimeout(100);
  assert(terminalOutput.includes('STUDIO_RESTORED='+retainedPid+':retained_'),'Reload must retain shell PID and environment');
  const afterSessions=await(await page.request.get(base+'/api/v1/sessions?'+new URLSearchParams({deviceId:targetId,root:projectRoot}))).json();
  assert(afterSessions.some(item=>item.id===retainedId&&item.attached),'Reload created a new shell');
  console.log('PASS reload restores same terminal session, PID and environment');
  const initial = await measure('desktop-expanded');
  console.log('CHECK desktop geometry');
  await page.screenshot({ path: 'artifacts/studio.png', fullPage: true });
  assert(initial.terminalSidebar.x >= initial.terminalScreen.right - 1, 'Terminal controls must sit to the right');
  assert(initial.terminalScreen.height >= initial.terminalPage.height - 2, 'Terminal controls must not consume vertical space');
  assert(initial.panelInEditor, 'Bottom panel must be inside the center editor column');
  assert(initial.panel.x >= initial.editor.x - 1 && initial.panel.right <= initial.editor.right + 1, 'Panel overlaps a sidebar horizontally');
  for (const sidebar of [initial.explorer, initial.inspector]) assert(Math.abs(sidebar.bottom - initial.workbench.bottom) <= 2, 'Sidebar does not extend to workbench bottom');
  assert(initial.body.width <= initial.viewport.width, 'Document has horizontal overflow');
  const handle = await page.locator('.studio-bottom-resize').boundingBox();
  await page.mouse.move(handle.x + handle.width / 2, handle.y + handle.height / 2);await page.mouse.down();await page.mouse.move(handle.x + handle.width / 2, handle.y - 100, { steps: 8 });await page.mouse.up();
  const resized = await measure('desktop-resized');
  assert(resized.panel.height > initial.panel.height + 50, 'Panel resize did not increase height');
  assert.equal(resized.explorer.height, initial.explorer.height, 'Resize shrank explorer');assert.equal(resized.inspector.height, initial.inspector.height, 'Resize shrank inspector');
  await page.locator('[data-bottom-fold]').click();const folded = await measure('desktop-folded');assert(folded.panel.height < 50, 'Panel did not collapse');
  await page.locator('[data-bottom-fold]').click();await page.screenshot({ path: 'artifacts/studio.png', fullPage: true });
  await page.locator('[data-studio-panel=codex]').click();
  await page.locator('#studio-codex').waitFor({state:'visible'});
  await measure('desktop-codex');
  for(const width of [1280,1024,390]){
    console.log('CHECK viewport',width);
    await page.setViewportSize({width,height:width===390?844:900});
    if(width===390)await page.locator('[data-pane=editor]').click();
    await page.waitForTimeout(150);
    const narrow=await measure('viewport-'+width);
    assert(narrow.editor.width>0&&narrow.panel.height>0,'Editor/terminal not visible at '+width+'; pane='+narrow.mobilePane);
    assert(narrow.body.width<=width,'Document horizontal overflow at '+width);
    assert(narrow.panel.x>=narrow.editor.x-1&&narrow.panel.right<=narrow.editor.right+1,'Panel overlaps sidebar at '+width);
    await page.screenshot({path:'artifacts/studio-'+width+'.png',fullPage:true});
  }
  await page.setViewportSize({width:1600,height:1000});
  await page.waitForTimeout(150);
  if(process.env.STUDIO_TEST_FULL_UI==='true')acceptance=await(await import('./studio-flow.mjs')).verifyStudioFlow(page,{base,projectRoot});
  await page.screenshot({path:'artifacts/studio.png',fullPage:true});
  assert.deepEqual(pageErrors, [], 'Uncaught browser errors');
  assert.deepEqual(logs.filter(entry=>entry.type==='error'), [], 'Browser console errors');
  console.log('PASS actual Studio rendering, PTY, center-only panel, drag resize, collapse, sidebar heights and overflow');
} catch (error) {
  failure = redact(error.message);console.error('FAIL', failure);await page.screenshot({ path: 'artifacts/studio.png', fullPage: true }).catch(() => {});process.exitCode = 1;
} finally {
  console.log('CHECK write artifacts and close browser');
  await writeFile('artifacts/studio-report.json', JSON.stringify({ base, projectRoot, measurements, logs, pageErrors, failedRequests, acceptance, failure }, null, 2));
  const token=await page.locator('meta[name=csrf-token]').getAttribute('content',{timeout:1000}).catch(()=>null);
  if(token)await page.request.post(base+'/logout',{headers:{'X-CSRF-TOKEN':token}}).catch(()=>{});
  await browser.close();
}
