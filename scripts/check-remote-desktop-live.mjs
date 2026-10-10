import {chromium} from 'playwright';
import {readFile,mkdir} from 'node:fs/promises';
import assert from 'node:assert/strict';
const base=process.env.REMOTE_TEST_URL || 'http://127.0.0.1:18190';
const values=Object.fromEntries((await readFile(process.env.REMOTE_TEST_ENV || '.tools/remote-fixture.env','utf8')).trim().split(/\r?\n/).map(line=>{const i=line.indexOf('=');return [line.slice(0,i),line.slice(i+1)];}));
const browser=await chromium.launch({headless:true});
const page=await browser.newPage({viewport:{width:1440,height:960}});
page.setDefaultTimeout(30000);
const errors=[],stages=new Set();
page.on('pageerror',error=>errors.push(error.message));
page.on('response',async response=>{if(/\/remote-setup$/.test(response.url())&&response.ok()){const value=await response.json().catch(()=>null);if(value?.stage&&!stages.has(value.stage)){stages.add(value.stage);console.log('Stage:',value.stage);}}});
await mkdir('artifacts/remote-desktop',{recursive:true});
try {
 await page.goto(base+'/login');
 await page.locator('#id').fill(values.DASHBOARD_AUTH_ID);await page.locator('#password').fill(values.DASHBOARD_AUTH_PASSWORD);
 await Promise.all([page.waitForURL(base+'/'),page.locator('button[type=submit]').click()]);
 const device=await page.evaluate(async password=>{
  const saved=await WorkspaceAssistantRuntime.api('/devices/ssh','POST',{command:'ssh tester@remote-ssh-check-20261010',password,name:'Remote acceptance'});
  dispatchEvent(new CustomEvent('workspace:invalidate',{detail:{topics:['all']}}));
  return saved;
 },values.REMOTE_TEST_PASSWORD);
 await page.evaluate(()=>dispatchEvent(new CustomEvent('assistant:navigate',{detail:{route:'remote'}})));
 const card=page.locator(`#remote-choices [data-id="${device.id}"]`);
 await card.waitFor();await page.screenshot({path:'artifacts/remote-desktop/device-list.png'});
 await card.click();
 if(process.env.REMOTE_TEST_RECOVERY === '1') {
  await page.locator('[data-setup-start]').filter({hasText:'연결하기'}).waitFor();
  assert.equal(await page.locator('[data-setup-password-label]').isVisible(),false);
  await page.locator('[data-setup-start]').click();
 } else {
 await page.getByRole('button',{name:'설치하고 연결',exact:true}).waitFor();
 assert.equal(await page.locator('[data-setup-password-label]').isVisible(),true);
 await page.screenshot({path:'artifacts/remote-desktop/installation-plan.png'});
 // A wrong password must produce recovery guidance and must not alter the remote connection profile.
 await page.locator('[data-setup-password]').fill('deliberately-wrong-test-password');
 await page.locator('[data-setup-start]').click();
 await page.waitForFunction(()=>document.querySelector('[data-setup-status]')?.textContent.includes('설치 권한을 확인하지 못했습니다'));
 assert.equal(await page.locator('[data-setup-password]').inputValue(),'');
 await page.locator('[data-setup-password]').fill(values.REMOTE_TEST_PASSWORD);
 await page.locator('[data-setup-start]').click();
 await page.waitForTimeout(2000);
 await page.locator('#editor-dialog .dialog-head [data-action="dialog-close"]').click();
 await card.click();
 }
 await page.locator('.connection-state').filter({hasText:'연결됨'}).waitFor({timeout:780000});
 await page.waitForTimeout(2000);
 await page.screenshot({path:'artifacts/remote-desktop/live-desktop.png'});
 await page.locator('[data-runtime]:not([hidden]) [data-runtime-action="fullscreen"]').click();
 await page.waitForFunction(()=>!!document.fullscreenElement);
 await page.locator('[data-runtime]:not([hidden]) [data-runtime-action="fullscreen"]').click();
 await page.waitForFunction(()=>!document.fullscreenElement);
 await page.setViewportSize({width:390,height:844});await page.waitForTimeout(1000);
 await page.screenshot({path:'artifacts/remote-desktop/live-mobile.png'});
 const bounds=await page.evaluate(()=>{const area=document.querySelector('[data-runtime]:not([hidden]) .stream-area'),r=area.getBoundingClientRect();return {left:r.left,right:r.right,height:r.height};});
 assert(bounds.left>=0&&bounds.right<=391&&bounds.height>200);
 await page.locator('[data-runtime]:not([hidden]) [data-runtime-action="keyboard"]').click();
 await page.getByRole('textbox',{name:'원격 화면에 보낼 텍스트'}).fill('한글 테스트');
 await page.locator('.remote-input-bar button[type=submit]').click();
 assert.equal(await page.getByRole('textbox',{name:'원격 화면에 보낼 텍스트'}).inputValue(),'');
 assert.deepEqual(errors,[]);
 console.log(JSON.stringify({result:process.env.REMOTE_TEST_RECOVERY==='1'?'PASS: restart recovery without sudo password, desktop and mobile input':'PASS: real password-based sudo installation, resume, SSH tunnel/VNC desktop, mobile layout and input',stages:[...stages],bounds,deviceId:device.id},null,2));
} catch(error) {
 await page.screenshot({path:'artifacts/remote-desktop/failure.png'});
 console.log(JSON.stringify({errors,status:await page.locator('[data-setup-status]').textContent().catch(()=>null)}));
 throw error;
} finally {await browser.close();}
