import {chromium} from 'playwright';
import {readFile,mkdir} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';
import assert from 'node:assert/strict';
const fixture=JSON.parse(await readFile('.tools/communications-qa.json','utf8'));
assert.match(fixture.name,/^communications-qa-[a-f0-9]{8}$/);assert.equal(fixture.url,'http://127.0.0.1:18197');
const docker=(...args)=>execFileSync('docker',args,{encoding:'utf8',windowsHide:true,stdio:['ignore','pipe','pipe']});
const browser=await chromium.launch({headless:true});const page=await browser.newPage({viewport:{width:1600,height:1000}});
if(process.env.COMMUNICATION_TEST_LOCAL_UI==='true'){await page.route('**/js/communications.js*',route=>route.fulfill({contentType:'application/javascript',path:'src/main/resources/static/js/communications.js'}));await page.route('**/css/communications.css*',route=>route.fulfill({contentType:'text/css',path:'src/main/resources/static/css/communications.css'}));console.log('UI source: current workspace JavaScript/CSS; backend: isolated verified Docker image.');}
const errors=[];page.on('pageerror',error=>errors.push(error.message));
await mkdir('artifacts',{recursive:true});
try{
 for(let i=0;i<60;i++){try{if((await page.request.get(fixture.url+'/health')).ok())break;}catch{}await new Promise(resolve=>setTimeout(resolve,1000));}
 await page.goto(fixture.url+'/login');await page.locator('[name=id]').fill(fixture.username);await page.locator('[name=password]').fill(fixture.password);await page.locator('button[type=submit]').click();await page.waitForURL(fixture.url+'/');
 const api=async(path,method='GET',body)=>page.evaluate(async({path,method,body})=>{const header=document.querySelector('meta[name=csrf-header]').content,token=document.querySelector('meta[name=csrf-token]').content;const response=await fetch('/api/v1'+path,{method,headers:{'Content-Type':'application/json',[header]:token},body:body===undefined?undefined:JSON.stringify(body)});return {status:response.status,body:response.status===204?null:await response.json()};},{path,method,body});
 await page.locator('[data-launcher=drawer]').first().click();await page.locator('#drawer-search').fill('Communications');await page.locator('[data-drawer-app=communications] [data-view=communications]').click();
 await page.locator('#communications.active .comm-shell').waitFor();assert.equal((await api('/communications/accounts')).body.length,0);
 assert.equal((await api('/communications/windows/local/observations','POST',{})).status,400);
 const created=await api('/communications/bridge/profiles','POST',{provider:'GMAIL',label:'Isolated fixture'});assert.equal(created.status,201);const id=created.body.id;
 const started=await api(`/communications/bridge/profiles/${id}/sessions`,'POST',{});assert.equal(started.status,201);await api('/sessions/'+started.body.id,'DELETE');
 await page.evaluate(id=>window.WorkspaceAuthenticationBrowser.open({title:'Isolated communications fixture',sessionFactory:()=>window.WorkspaceAssistantRuntime.api(`/communications/bridge/profiles/${id}/sessions`,'POST',{})}),id);
 await page.locator('.authentication-browser-screen canvas').first().waitFor({timeout:30000});await page.waitForTimeout(2000);
 assert.ok(await page.locator('.authentication-browser-screen canvas').first().evaluate(node=>node.width>0));
 await page.screenshot({path:'artifacts/communications-remote.png'});await page.locator('[data-auth-close]').click();
 const observed=await api(`/communications/bridge/profiles/${id}/snapshot`);assert.equal(observed.status,200);assert.equal(observed.body.structuredMessages,false);
 await api(`/communications/bridge/profiles/${id}`,'DELETE');
 // UI fixture responses below are explicitly mock data, not real provider integration proof.
 let pending=null,confirmations=0;
 const gmail={id:'gmail-fixture',provider:'GMAIL',label:'Gmail search fixture',capabilities:['READ','SEND','SEARCH']};
 const gmailMessage={accountId:gmail.id,provider:'GMAIL',id:'gm1',conversationId:'gt1',sender:'Search fixture',text:'<img src=x> Gmail search fixture',timestamp:1700000000000,unread:true,attachments:[]};
 const account={id:'fixture-account',provider:'SLACK',label:'Workspace test',capabilities:['READ','SEND','REPLY','PARTICIPANTS']};
 await page.route('**/api/v1/communications/**',async route=>{
  const path=new URL(route.request().url()).pathname;let body;
  if(path.endsWith('/accounts'))body=[account,gmail];else if(path.endsWith('/providers'))body=[];else if(path.endsWith('/actions')){if(route.request().method()==='POST'){pending={id:'fixture-action',accountId:account.id,provider:'SLACK',accountLabel:'Workspace test',state:'PENDING',expiresAt:Date.now()+600000,message:route.request().postDataJSON(),operation:'SEND'};body=pending;}else body=pending?[pending]:[];}else if(path.endsWith('/confirmation')){confirmations++;pending={...pending,state:'SENT'};body=pending;}
  else if(path.includes('/gmail-fixture/conversations'))body={items:[],nextCursor:''};
  else if(path.endsWith('/message-search'))body={items:new URL(route.request().url()).searchParams.get('cursor')?[gmailMessage,{...gmailMessage,id:'gm2',text:'Next search result'}]:[gmailMessage],nextCursor:new URL(route.request().url()).searchParams.get('cursor')?'':'next'};
  else if(path.includes('/gmail-fixture/messages'))body={items:[gmailMessage],nextCursor:''};
  else if(path.endsWith('/conversations'))body={items:[{accountId:account.id,provider:'SLACK',id:'c1',title:'개발 대화 · fixture',kind:'DM',preview:'검증용 메시지'},{accountId:account.id,provider:'SLACK',id:'c2',title:'Second fixture',kind:'GROUP',preview:'검증용 두 번째 대화'}],nextCursor:''};
  else if(path.endsWith('/participants'))body={items:new URL(route.request().url()).searchParams.get('cursor')?[{id:'U1',label:'U1'},{id:'U2',label:'<img src=x>'}]:[{id:'U1',label:'U1'}],nextCursor:new URL(route.request().url()).searchParams.get('cursor')?'':'next'};
  else if(path.endsWith('/thread-messages'))body={items:[{id:new URL(route.request().url()).searchParams.get('cursor')?'reply2':'reply1',sender:'Thread fixture',text:'<b>untrusted reply</b>',timestamp:1700000000000}],nextCursor:new URL(route.request().url()).searchParams.get('cursor')?'':'next'};
  else if(path.endsWith('/messages'))body={items:[{accountId:account.id,provider:'SLACK',id:'m1',conversationId:'c1',sender:'테스트 사용자',text:'격리된 UI fixture입니다. 실제 Slack 메시지가 아닙니다.\n<img src=x onerror=alert(1)>',timestamp:Date.now(),threadId:'',unread:null,attachments:[],reactions:[{key:'thumbsup',label:'thumbsup',count:3}]}],nextCursor:''};
  else return route.continue();await route.fulfill({json:body});
 });
 await page.locator('[data-comm=refresh]').click();await page.locator('.comm-search-options summary').click();await page.locator('[data-comm-search] select').selectOption('gmail-fixture');await page.locator('[data-comm-search] input').fill('is:unread');await page.locator('[data-comm-search] button').click();await page.locator('[data-comm-search-more]').click();await page.waitForFunction(()=>document.querySelectorAll('[data-comm-search-results] article').length===2);assert.equal(await page.locator('[data-comm-search-results] img').count(),0);await page.locator('[data-comm-search-open="0"]').click();await page.locator('.comm-message p').waitFor();assert.ok((await page.locator('.comm-message p').textContent()).includes('Gmail search fixture'));assert.ok((await page.locator('.comm-read-state').textContent()).includes('내 메일함: 읽지 않음'));await page.locator('[data-comm=close]').click();await page.locator('[data-comm-search] select').selectOption('');await page.locator('[data-comm-search] input').fill('');await page.locator('[data-comm=conversation]').first().click();await page.locator('.comm-message p').waitFor();
 assert.equal(await page.locator('.comm-message img').count(),0);assert.ok((await page.locator('.comm-reactions li').textContent()).includes('thumbsup · 3'));await page.locator('.comm-composer textarea').fill('유지해야 하는 초안');
 await page.waitForTimeout(400);await page.screenshot({path:'artifacts/communications-desktop.png'});
 await page.locator('[data-comm=participants]').first().click();await page.locator('[data-comm-participants-more]').click();await page.waitForFunction(()=>document.querySelectorAll('[data-comm-participant-list] li').length===2);assert.equal(await page.locator('[data-comm-participant-list] img').count(),0);await page.locator('#editor-form button[type=submit]').click();
 await page.locator('[data-comm=thread]').click();await page.locator('[data-comm-thread-more]').click();await page.waitForFunction(()=>document.querySelectorAll('[data-comm-thread-content] article').length===2);assert.equal(await page.locator('[data-comm-thread-content] p b').count(),0);await page.locator('#editor-form button[type=submit]').click();assert.equal(await page.locator('.comm-composer textarea').inputValue(),'유지해야 하는 초안');
 await page.setViewportSize({width:390,height:844});await page.waitForTimeout(300);assert.equal(await page.locator('.comm-composer textarea').inputValue(),'유지해야 하는 초안');
 assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));await page.screenshot({path:'artifacts/communications-mobile.png'});
 await page.locator('.comm-composer button.primary').click();await page.locator('#editor-dialog[open]').waitFor();assert.equal(confirmations,0);await page.waitForTimeout(400);await page.screenshot({path:'artifacts/communications-mobile-approval.png'});await page.locator('#editor-form button[type=submit]').click();await page.locator('#editor-dialog[open]').waitFor({state:'hidden'});assert.equal(confirmations,1);assert.equal(await page.locator('.comm-composer textarea').inputValue(),'');
 await page.setViewportSize({width:1600,height:1000});await page.locator('[data-comm=conversation]').nth(1).click();await page.locator('[data-comm=tab][data-index="0"]').click();await page.locator('[data-comm=split]').click();
 await page.reload();await page.evaluate(()=>window.dispatchEvent(new CustomEvent('assistant:navigate',{detail:{route:'communications'}})));await page.waitForFunction(()=>document.querySelectorAll('.comm-pane [data-comm=thread]').length===2);assert.equal(await page.locator('.comm-composer textarea').first().inputValue(),'');
 assert.equal(await page.locator('.comm-pane:not([hidden])').count(),2);assert.equal(await page.locator('[data-comm=tab][data-index="0"]').getAttribute('aria-pressed'),'true');assert.equal(await page.locator('[data-comm=thread]').count(),2);assert.deepEqual(errors,[]);console.log('PASS real empty-account app/OWNER endpoints/isolated Chromium/Guacamole; fixture message UI/XSS/mobile/tab restore. Real provider accounts not tested.');
}finally{await browser.close();}
