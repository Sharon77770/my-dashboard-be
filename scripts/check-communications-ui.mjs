import {chromium} from 'playwright';
import {readFile,mkdir} from 'node:fs/promises';
import assert from 'node:assert/strict';

// Real dashboard/Chromium rendering; provider responses are explicit UI fixtures.
const fixture=JSON.parse(await readFile('.tools/communications-qa.json','utf8'));
assert.match(fixture.name,/^communications-qa-[a-f0-9]{8}$/);
assert.equal(fixture.url,'http://127.0.0.1:18197');
const browser=await chromium.launch({headless:true});
await mkdir('artifacts',{recursive:true});
const accounts=[
 {id:'slack-ui',provider:'SLACK',label:'Design studio',capabilities:['READ','SEND','REPLY','UPLOAD','ATTACHMENTS','PARTICIPANTS']},
 {id:'gmail-ui',provider:'GMAIL',label:'Personal mail',capabilities:['READ','SEND','REPLY','SEARCH']}
];
const conversations=[
 {id:'design',accountId:'slack-ui',provider:'SLACK',title:'제품 디자인',kind:'DM',preview:'새로운 대화 경험을 함께 확인해요.',updatedAt:Date.now(),unread:true},
 {id:'release',accountId:'slack-ui',provider:'SLACK',title:'릴리스 준비',kind:'GROUP',preview:'검토 항목과 첨부파일을 공유했습니다.',updatedAt:Date.now()-3600000},
 {id:'mail',accountId:'gmail-ui',provider:'GMAIL',title:'Workspace 디자인 검토',kind:'MAIL',preview:'수정한 화면을 확인해 주세요.',updatedAt:Date.now()-7200000,unread:true}
];
const messages=Array.from({length:8},(_,index)=>({id:'m'+index,accountId:'slack-ui',provider:'SLACK',conversationId:'design',sender:index%2?'민서':'지우',text:index%2?'좋아요. 작성 중인 내용은 화면을 전환해도 그대로 유지되어야 해요.':'대화 목록을 한곳에 모으고 메시지 영역에 집중할 수 있도록 정리했습니다.\n첨부파일과 스레드는 필요한 순간에 열어볼 수 있어요.',timestamp:Date.now()-(8-index)*120000,attachments:index===3?[{id:'f1',name:'design-review.pdf',size:245760}]:[],reactions:index===5?[{key:'thumbsup',label:'thumbsup',count:2}]:[]}));
try{
 for(const viewport of [{width:1920,height:1080},{width:1366,height:768},{width:390,height:844}]){
  const page=await browser.newPage({viewport});const errors=[];page.on('pageerror',error=>errors.push(error.message));
  if(process.env.COMMUNICATION_TEST_LOCAL_UI==='true'){
   await page.route('**/js/communications.js*',route=>route.fulfill({contentType:'application/javascript',path:'src/main/resources/static/js/communications.js'}));
   await page.route('**/css/communications.css*',route=>route.fulfill({contentType:'text/css',path:'src/main/resources/static/css/communications.css'}));
  }
  let mode='empty',messageError=false,slowMessages=false,pending=null,confirmations=0;
  await page.route('**/api/v1/communications/**',async route=>{
   const path=new URL(route.request().url()).pathname;let body=[];
   if(path.endsWith('/accounts')){
    if(mode==='failure')return route.fulfill({status:503,json:{message:'연결 서버에 응답이 없습니다.'}});
    body=mode==='empty'?[]:accounts;
   }else if(path.endsWith('/bridge/profiles'))body=[{id:'ui-remote-profile',provider:'SLACK',label:'UI remote fixture'}];
   else if(path.endsWith('/providers'))body=[{id:'GMAIL',configured:false,limitation:'OAuth 설정 필요'}];
   else if(path.endsWith('/actions')){
    if(route.request().method()==='POST')pending={id:'ui-action',accountId:'slack-ui',provider:'SLACK',accountLabel:'Design studio',state:'PENDING',expiresAt:Date.now()+600000,message:route.request().postDataJSON(),operation:'SEND'};
    body=route.request().method()==='POST'?pending:pending?[pending]:[];
   }else if(path.endsWith('/confirmation')){confirmations++;pending={...pending,state:'SENT'};body=pending;}
   else if(path.endsWith('/conversations'))body={items:conversations.filter(item=>path.includes(item.accountId)),nextCursor:''};
   else if(path.endsWith('/message-search')||path.endsWith('/search'))body={items:[],nextCursor:''};
   else if(path.endsWith('/cached-messages'))body=messages;
   else if(path.endsWith('/messages')){
    if(slowMessages)await new Promise(resolve=>setTimeout(resolve,1200));
    if(messageError)return route.fulfill({status:403,json:{message:'권한 부족: 이 대화에 접근할 수 없습니다.'}});
    body={items:path.includes('gmail-ui')?[{...messages[0],id:'mail1',provider:'GMAIL',accountId:'gmail-ui',conversationId:'mail',sender:'디자인 팀',text:'안녕하세요.\n\n새로운 Communications 화면 검토를 부탁드립니다.\n대화 목록, 메시지 작성, 승인 흐름을 확인해 주세요.\n\n감사합니다.',unread:true}]:messages,nextCursor:''};
   }else return route.continue();
   await route.fulfill({json:body});
  });
  for(let attempt=0;attempt<60;attempt++){try{if((await page.request.get(fixture.url+'/health')).ok())break;}catch{}await new Promise(resolve=>setTimeout(resolve,1000));}
  await page.goto(fixture.url+'/login');await page.locator('[name=id]').fill(fixture.username);await page.locator('[name=password]').fill(fixture.password);await page.locator('button[type=submit]').click();await page.waitForURL(fixture.url+'/');
  await page.evaluate(()=>window.dispatchEvent(new CustomEvent('assistant:navigate',{detail:{route:'communications'}})));
  const shot=async state=>{await page.waitForTimeout(250);assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'No page overflow');await page.screenshot({path:`artifacts/communications-ui-${viewport.width}-${state}.png`});};
  await page.locator('.comm-onboarding-content').waitFor();assert.equal(await page.locator('.comm-connect-options button').count(),4);assert.equal(await page.locator('.comm-details').isVisible(),false);await shot('onboarding');
  await page.evaluate(()=>{window.uiOriginalRemoteOpen=window.WorkspaceAuthenticationBrowser.open;window.WorkspaceAuthenticationBrowser.open=()=>new Promise(resolve=>{window.uiResolveRemote=resolve;});});
  await page.locator('[data-comm-remote=SLACK]').click();await page.locator('[data-comm-status][data-tone=loading]').waitFor();assert.equal(await page.locator('[data-comm-remote=SLACK]').isDisabled(),true);await shot('remote-wait');
  await page.evaluate(()=>{window.uiResolveRemote();window.WorkspaceAuthenticationBrowser.open=window.uiOriginalRemoteOpen;delete window.uiOriginalRemoteOpen;delete window.uiResolveRemote;});
  mode='failure';await page.locator('[data-comm=refresh]').click();await page.getByText('계정을 불러오지 못했습니다',{exact:true}).waitFor();await shot('connection-error');
  mode='connected';await page.locator('[data-comm=refresh]').first().click();await page.locator('[data-comm=conversation]').first().waitFor();await shot('list');
  await page.locator('[data-comm-list-search]').fill('존재하지않는대화');await page.getByText('일치하는 대화가 없습니다',{exact:true}).waitFor();await shot('search-empty');await page.locator('[data-comm-list-search]').fill('');
  slowMessages=true;await page.locator('[data-comm=conversation]').first().click();await page.getByText('메시지를 불러오는 중',{exact:true}).waitFor();await shot('loading');await page.locator('.comm-message').first().waitFor();slowMessages=false;
  assert.equal(await page.locator('.comm-details').isVisible(),false);
  if(viewport.width>800){const box=await page.locator('.comm-sidebar').boundingBox();assert.ok(box.width<=320);assert.ok((await page.locator('.comm-work').boundingBox()).width>viewport.width*.6);}
  await page.locator('.comm-composer textarea').fill('검토한 내용을 답장합니다. 초안이 유지되어야 합니다.');await page.locator('[data-comm=reply]').first().click();await page.locator('[data-comm=clear-reply]').waitFor();await page.locator('[data-comm=clear-reply]').click();assert.equal(await page.locator('[data-comm=clear-reply]').count(),0);await shot('conversation');
  await page.locator('.comm-pane [data-comm=details]').click();await page.locator('.comm-details').waitFor();await shot('details');await page.locator('[data-comm=details-close]').click();assert.equal(await page.locator('.comm-composer textarea').inputValue(),'검토한 내용을 답장합니다. 초안이 유지되어야 합니다.');
  if(viewport.width===390){
   await page.locator('.comm-pane:not([hidden]) [data-comm=back]').click();await page.locator('.comm-list').waitFor();await page.locator('[data-comm=refresh]').click();await page.waitForTimeout(300);assert.equal(await page.locator('.comm-work').isVisible(),false,'Refresh must retain mobile list');
   await page.locator('[data-comm=conversation]').first().click();await page.locator('.comm-message').first().waitFor();
   await page.setViewportSize({width:390,height:480});await page.locator('.comm-composer textarea').focus();await page.waitForTimeout(200);const composer=await page.locator('.comm-composer').boundingBox();assert.ok(composer.y+composer.height<=480,'Composer remains above resized viewport bottom');await shot('keyboard-viewport');await page.setViewportSize(viewport);
  }
  await page.locator('.comm-composer button.primary').click();await page.locator('#editor-dialog[open]').waitFor();assert.equal(confirmations,0);await shot('approval');await page.locator('#editor-form button[type=submit]').click();await page.locator('#editor-dialog[open]').waitFor({state:'hidden'});assert.equal(confirmations,1);
  // Visit a fresh conversation so cached content does not obscure the permission state.
  if(viewport.width===390)await page.locator('.comm-pane:not([hidden]) [data-comm=back]').click();
  messageError=true;await page.locator('[data-comm=conversation]').nth(1).click();await page.getByText('이 대화를 읽을 권한이 없습니다',{exact:true}).waitFor();await shot('permission');messageError=false;await page.locator('[data-comm=retry-messages]').click();await page.locator('.comm-pane:not([hidden]) .comm-message').first().waitFor();
  if(viewport.width===390)await page.locator('.comm-pane:not([hidden]) [data-comm=back]').click();
  await page.locator('[data-comm=conversation]').nth(2).click();await page.locator('.comm-mail-message').waitFor();assert.equal(await page.locator('[data-comm-status]').isVisible(),false,'Recovered pane error must not remain globally');await shot('email');
  assert.deepEqual(errors,[]);await page.close();console.log(`PASS ${viewport.width}x${viewport.height}: onboarding, remote wait, connection error, search, loading, conversation, draft, details, approval, permission/retry, email.`);
 }
 console.log(`Provider data: mocked UI fixtures. Assets: ${process.env.COMMUNICATION_TEST_LOCAL_UI==='true'?'workspace override':'packaged Docker image'}. Physical mobile keyboard and real provider accounts are not verified.`);
}finally{await browser.close();}
