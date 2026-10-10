import { chromium } from 'playwright';
import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
const values=Object.fromEntries((await readFile(process.env.CHROME_TEST_ENV || '.tools/chrome-fixture.env','utf8')).trim().split(/\r?\n/).map(l=>{const i=l.indexOf('=');return [l.slice(0,i),l.slice(i+1)];}));
const base = process.env.CHROME_TEST_URL || 'http://127.0.0.1:18189';
const browser=await chromium.launch({headless:true});
const page=await browser.newPage({viewport:{width:1440,height:900}});
page.setDefaultTimeout(20000);
const errors=[];page.on('pageerror',e=>errors.push(e.message));
await page.goto(base + '/login');
await page.locator('#id').fill(values.DASHBOARD_AUTH_ID);await page.locator('#password').fill(values.DASHBOARD_AUTH_PASSWORD);
await Promise.all([page.waitForURL(base + '/'),page.locator('button[type=submit]').click()]);
await page.evaluate(()=>{
  window.mediaProof={audioPackets:0,nonzero:0};
  const Original=Guacamole.Client;
  Guacamole.Client=function(...args){const client=new Original(...args);window.testChromeClient=client;return client;}; Object.assign(Guacamole.Client, Original);
  const create=Guacamole.AudioPlayer.getInstance;
  Guacamole.AudioPlayer.getInstance=function(stream,type){const player=create(stream,type),read=stream.onblob;stream.onblob=data=>{mediaProof.audioPackets++;if([...atob(data)].some(c=>c.charCodeAt(0)!==0))mediaProof.nonzero++;read?.(data);};return player;};
  dispatchEvent(new CustomEvent('assistant:navigate',{detail:{route:'chrome'}}));
});
try { await page.waitForFunction(()=>window.testChromeClient?.getDisplay().getWidth()>0); } catch(error) { console.log(JSON.stringify({errors, state:await page.evaluate(()=>({active:document.getElementById('chrome')?.className,status:document.querySelector('.chrome-status')?.textContent,registered:!!window.WorkspaceApps?.get('chrome'),runtime:!!window.WorkspaceAssistantRuntime,client:!!window.testChromeClient}))})); await page.screenshot({path:'artifacts/chrome/live-failure.png'}); await browser.close();throw error; }
await page.waitForTimeout(1500);
assert.equal(await page.locator('.chrome-status').isVisible(),false);
await page.screenshot({path:'artifacts/chrome/live-desktop.png'});
await page.locator('.chrome-menu summary').click();await page.getByRole('button',{name:'주소창으로 이동'}).click();
await page.keyboard.type('http://127.0.0.1:8765/chrome-media.html');await page.keyboard.press('Enter');
await page.waitForTimeout(1500);
await page.getByRole('button',{name:'소리 켜기',exact:true}).click();
// Click the fixture button below the Chromium toolbar and startup notice.
await page.locator('.chrome-screen').click({position:{x:180,y:200}});
await page.screenshot({path:'artifacts/chrome/live-before-audio.png'});
try {await page.waitForFunction(()=>mediaProof.nonzero>5,{},{timeout:15000});} catch(error) { console.log(JSON.stringify(await page.evaluate(()=>({mediaProof,status:document.querySelector('.chrome-status').textContent,audio:Guacamole.AudioContextFactory.getAudioContext()?.state})))); await browser.close(); throw error; }
await page.screenshot({path:'artifacts/chrome/live-media.png'});
const desktop=await page.evaluate(()=>({width:testChromeClient.getDisplay().getWidth(),height:testChromeClient.getDisplay().getHeight(),...mediaProof}));
await page.setViewportSize({width:390,height:844});
await page.waitForTimeout(1200);
const mobile=await page.evaluate(()=>({width:testChromeClient.getDisplay().getWidth(),height:testChromeClient.getDisplay().getHeight(),scale:testChromeClient.getDisplay().getScale(),viewport:document.querySelector('.chrome-screen').clientWidth}));
assert.equal(mobile.width,480);assert(mobile.scale<=390/480+.01);
await page.screenshot({path:'artifacts/chrome/live-mobile.png'});
await page.getByRole('button',{name:'Chrome에서 대시보드로 돌아가기'}).click();
await page.waitForTimeout(300);
assert.deepEqual(errors,[]);
console.log(JSON.stringify({desktop,mobile,result:'PASS: real Chromium, VNC resize, animated media and nonzero audio packets, exit'},null,2));
await browser.close();
