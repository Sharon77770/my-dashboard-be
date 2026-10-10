import {chromium} from 'playwright';
import {readFile, mkdir} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';

// Only generated credentials and an explicitly isolated Docker fixture are accepted.
const fixture = JSON.parse(await readFile('.tools/auth-browser-qa.json','utf8'));
assert.match(fixture.name,/^auth-browser-qa-[a-f0-9]{8}$/);
assert.equal(fixture.url,'http://127.0.0.1:18198');
assert.equal(fixture.cdp,'http://127.0.0.1:19298');
const docker = (...args) => execFileSync('docker',args,{encoding:'utf8',stdio:['ignore','pipe','pipe'],windowsHide:true});
const loginMarker = randomUUID().replaceAll('-','');
docker('exec','-d',fixture.name+'-dashboard','python3','-c',`
from http.server import BaseHTTPRequestHandler, HTTPServer
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args): pass
 def do_GET(self):
  signed='auth-browser-fixture=${loginMarker}' in self.headers.get('Cookie','') or self.path=='/signin'
  self.send_response(200)
  self.send_header('Content-Type','text/html; charset=utf-8')
  if self.path=='/signin': self.send_header('Set-Cookie','auth-browser-fixture=${loginMarker}; Max-Age=3600; HttpOnly; SameSite=Lax; Path=/')
  self.end_headers()
  self.wfile.write(('<html><head><title>Authentication browser fixture</title></head><body style="background:#f3f5fa;color:#172238;font:24px sans-serif;padding:40px"><h1>Authentication browser fixture</h1><p id="identity">'+('Fixture signed in' if signed else 'Fixture signed out')+'</p><a href="/signin">Sign in fixture</a><p><input id="fixture-input" style="font-size:24px" aria-label="Remote typing"></p></body></html>').encode())
HTTPServer(('0.0.0.0',18800),Handler).serve_forever()
`);
await mkdir('artifacts',{recursive:true});
const browser = await chromium.launch({headless:true});
const page = await browser.newPage({viewport:{width:1440,height:1000}});
const errors=[];page.on('pageerror',error=>errors.push(error.message));
let remote;
async function waitReady() {
  for(let index=0;index<40;index++) {
    try {if((await fetch(fixture.url+'/health')).ok)return;}catch{}
    await new Promise(resolve=>setTimeout(resolve,500));
  }
  throw Error('Isolated dashboard did not become ready');
}
async function api(path,method='GET',body) {
  return page.evaluate(async({path,method,body})=>{
    const headers={'Content-Type':'application/json'};
    headers[document.querySelector('meta[name=csrf-header]').content]=document.querySelector('meta[name=csrf-token]').content;
    const response=await fetch('/api/v1'+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body)});
    return {status:response.status,body:await response.json().catch(()=>null)};
  },{path,method,body});
}
async function remoteFixture() {
  for(let attempt=0;attempt<40;attempt++) {
    const target=remote.contexts()[0].pages().find(candidate=>candidate.url().startsWith('http://dashboard:18800/'));
    if(target) {await target.waitForLoadState('domcontentloaded');return target;}
    await page.waitForTimeout(250);
  }
  throw Error('Registered app did not open in server Chromium');
}
async function connected() {
  await page.locator('[data-auth-status]').filter({hasText:'서버 Chromium에 연결됨'}).waitFor({timeout:30000});
  await page.locator('.authentication-browser-screen canvas').first().waitFor({state:'attached'});
}
try {
  await waitReady();await page.goto(fixture.url);
  await page.locator('#id').fill(fixture.username);await page.locator('#password').fill(fixture.password);
  await Promise.all([page.waitForURL(url=>!url.pathname.startsWith('/login')),page.locator('button[type=submit]').click()]);
  const app=await api('/applications','POST',{name:'Authentication fixture',url:'http://dashboard:18800/',pinned:true});
  assert.equal(app.status,201);
  await page.reload({waitUntil:'domcontentloaded'});
  remote=await chromium.connectOverCDP(fixture.cdp);
  await remote.contexts()[0].clearCookies({name:'auth-browser-fixture'});
  for(const existing of remote.contexts()[0].pages())if(existing.url().startsWith('http://dashboard:18800/'))await existing.close();
  await page.evaluate(id=>window.WorkspaceAuthenticationBrowser.open({provider:'APP',applicationId:id}),app.body.id);
  await connected();let target=await remoteFixture();
  assert.equal(await target.locator('#identity').textContent(),'Fixture signed out');
  await target.locator('a').click();
  assert.equal(await target.locator('#identity').textContent(),'Fixture signed in');
  assert.ok((await remote.contexts()[0].cookies('http://dashboard:18800/')).some(cookie=>cookie.name==='auth-browser-fixture'&&cookie.value===loginMarker));
  // Input enters through the real browser UI -> WebSocket -> guacd -> Chromium path.
  await target.locator('#fixture-input').click();
  await page.locator('.authentication-browser-screen').focus();await page.keyboard.type('remote-verified');
  await target.waitForFunction(()=>document.querySelector('#fixture-input').value==='remote-verified');
  await page.locator('.authentication-browser-text input').fill('한글 확인');
  await page.locator('.authentication-browser-text button').click();
  await target.waitForFunction(()=>document.querySelector('#fixture-input').value.includes('한글 확인'));
  await page.screenshot({path:'artifacts/authentication-browser-desktop.png'});
  await page.locator('[data-auth-close]').click();
  await page.evaluate(()=>window.WorkspaceAuthenticationBrowser.open());await connected();
  assert.equal(await target.locator('#identity').textContent(),'Fixture signed in');
  await page.setViewportSize({width:390,height:844});await page.waitForTimeout(400);
  const fit=await page.locator('dialog.authentication-browser').evaluate(element=>({width:element.getBoundingClientRect().width,scroll:element.scrollWidth,client:element.clientWidth,screen:element.querySelector('.authentication-browser-screen').clientHeight}));
  assert.ok(fit.width<=390&&fit.scroll<=fit.client&&fit.screen>=180,'Mobile authentication dialog must remain usable without overflow');
  await page.screenshot({path:'artifacts/authentication-browser-mobile.png'});
  await page.locator('[data-auth-close]').click();
  // Restart only the isolated browser; its named fixture profile must retain the cookie.
  await remote.close();remote=null;docker('restart',fixture.name+'-browser');
  for(let attempt=0;attempt<40;attempt++){try{remote=await chromium.connectOverCDP(fixture.cdp);break;}catch{await page.waitForTimeout(500);}}
  assert.ok(remote,'Isolated Chromium did not restart');
  await page.evaluate(id=>window.WorkspaceAuthenticationBrowser.open({provider:'APP',applicationId:id}),app.body.id);
  await connected();target=await remoteFixture();
  assert.equal(await target.locator('#identity').textContent(),'Fixture signed in');
  assert.ok((await remote.contexts()[0].cookies('http://dashboard:18800/')).some(cookie=>cookie.name==='auth-browser-fixture'&&cookie.value===loginMarker));
  assert.equal((await api('/authentication-browser/sessions','POST',{provider:'CODEX',url:'https://auth.openai.com.evil.test/',width:1600,height:900})).status,400);
  assert.deepEqual(errors,[]);
  console.log('PASS real isolated VNC, remote keyboard, desktop/mobile layout, close/reopen and persistent fixture login after Chromium restart; no real provider login performed');
} finally {await remote?.close().catch(()=>{});await browser.close();}
