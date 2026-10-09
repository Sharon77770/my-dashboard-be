import {chromium} from 'playwright';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import assert from 'node:assert/strict';

// Isolated fixtures only. Never use this destructive query fixture against user databases.
const base=process.env.DATABASE_TEST_URL||'http://127.0.0.1:18189';
const credentials=Object.fromEntries((await readFile('.tools/studio-ide-qa.env','utf8')).trim().split(/\r?\n/).map(line=>{const i=line.indexOf('=');return [line.slice(0,i),line.slice(i+1)];}));
const browser=await chromium.launch({headless:true});
const page=await browser.newPage({viewport:{width:1440,height:1000}});
page.setDefaultTimeout(20000);
const report={checks:[],pageErrors:[],consoleErrors:[]};
page.on('pageerror',error=>report.pageErrors.push(error.message));
page.on('console',message=>{if(message.type()==='error')report.consoleErrors.push(message.text());});
let csrf,device;const saved=[];
async function api(path,method='GET',data){
  const response=await page.request.fetch(base+'/api/v1'+path,{method,headers:{'X-CSRF-TOKEN':csrf||''},data});
  assert(response.ok(),method+' '+path+' HTTP '+response.status());
  return response.status()===204?null:response.json();
}
try{
  await mkdir('artifacts',{recursive:true});
  for(let i=0;i<60;i++){try{if((await fetch(base+'/health')).ok)break;}catch{}await page.waitForTimeout(500);}
  await page.goto(base);
  await page.locator('#id').fill(credentials.DASHBOARD_AUTH_ID);await page.locator('#password').fill(credentials.DASHBOARD_AUTH_PASSWORD);
  await Promise.all([page.waitForURL(url=>!url.pathname.startsWith('/login')),page.locator('button[type=submit]').click()]);
  csrf=await page.locator('meta[name=csrf-token]').getAttribute('content');
  device=await api('/devices/ssh','POST',{command:'ssh tester@studio-db-ssh',password:credentials.QA_SSH_PASSWORD});
  assert(device.fingerprint,'SSH registration must pin a host key');
  report.checks.push('registered SSH host key');
  await page.locator('.activity-rail [data-view=databases]').click();
  const containers=await api('/databases/devices/'+device.id+'/containers');
  assert.equal(containers.length,2);
  report.checks.push('Recorded Docker discovery metadata; actual SSH and database servers');
  const cases=[['POSTGRESQL','studio-db-postgres',5432,'DEVICE'],['MYSQL','studio-db-mysql',3306,'DEVICE'],['MARIADB','studio-db-mariadb',3306,'DEVICE'],['POSTGRESQL','studio-db-postgres',5432,'DOCKER'],['MYSQL','studio-db-mysql',3306,'DOCKER']];
  for(const [type,host,port,mode] of cases){
    await page.locator('[data-db-action=new-connection]').click();
    const form=page.locator('#db-connection-form');
    await form.locator('[name=type]').selectOption(type);
    await form.locator('[name=targetMode]').selectOption(mode);
    await form.locator('[name=deviceId] option[value="'+device.id+'"]').waitFor({state:'attached'});
    await form.locator('[name=deviceId]').selectOption(device.id);
    await form.locator('[name=name]').fill(type+' '+mode+' fixture');
    if(mode==='DEVICE')await form.locator('[name=host]').fill(host);
    else {const container=containers.find(item=>item.name===host);await form.locator('[name=containerId] option[value="'+container.id+'"]').waitFor({state:'attached'});await form.locator('[name=containerId]').selectOption(container.id);}
    await form.locator('[name=port]').fill(String(port));
    await form.locator('[name=databaseName]').fill('studio');await form.locator('[name=username]').fill('studio');
    await form.locator('[name=credential]').fill('studio-fixture-only');await form.locator('[name=accessMode]').selectOption('READ_WRITE');
    if(type==='MYSQL')await form.locator('[name=sslMode]').selectOption('REQUIRE');
    await form.locator('[data-db-action=test-draft]').click();
    await form.locator('#db-test-result').filter({hasText:'✓'}).waitFor();
    const creation=page.waitForResponse(response=>response.url()===base+'/api/v1/databases'&&response.request().method()==='POST');
    await form.locator('button.primary').click();
    const item=await(await creation).json();saved.push(item.id);
    assert.equal(item.targetMode,mode);assert.equal(item.deviceId,device.id);assert(!JSON.stringify(item).includes('studio-fixture-only'));
    await page.locator('#db-sql').waitFor();
    for(const sql of ['CREATE TABLE IF NOT EXISTS studio_target_check (id INTEGER PRIMARY KEY, message VARCHAR(30))','SELECT 42 AS answer']){
      await page.locator('#db-sql').fill(sql);
      const started=page.waitForResponse(response=>response.url()===base+'/api/v1/databases/'+item.id+'/query'&&response.request().method()==='POST');
      await page.locator('[data-db-action=run]').click();
      const execution=await(await started).json();
      await page.waitForResponse(async response=>response.url().endsWith('/query/'+execution.executionId)&&response.request().method()==='GET'&&(await response.json()).state==='SUCCEEDED');
      await page.locator('.db-result-head').filter({hasText:'SUCCEEDED'}).waitFor({timeout:30000});
    }
    await page.locator('.db-editor .db-cell').filter({hasText:'42'}).waitFor();
    await page.locator('[data-db-connection="'+item.id+'"]').click();
    if(type==='POSTGRESQL')await page.locator('[data-db-schema=public]').click();
    await page.locator('[data-db-table=studio_target_check]').click();
    await page.locator('.db-table').filter({hasText:'studio_target_check'}).waitFor();
    const readOnly={...item,credential:null,accessMode:'READ_ONLY'};
    await api('/databases/'+item.id,'PUT',readOnly);
    const denied=await page.request.post(base+'/api/v1/databases/'+item.id+'/query',{headers:{'X-CSRF-TOKEN':csrf},data:{sql:'DELETE FROM studio_target_check',confirmed:true}});
    assert.equal(denied.status(),403);
    assert((await api('/databases/'+item.id+'/test','POST',{})).connected,'Editing must retain saved credentials');
    report.checks.push(type+' '+mode+' actual SSH tunnel: UI test/save/query/metadata, read-only denial, encrypted credential reuse');
    console.log('PASS',report.checks.at(-1));
  }
  for(const width of [1440,390]){
    await page.setViewportSize({width,height:1000});
    if(width<600)await page.locator('[data-db-pane=schema]').click();
    await page.locator('[data-db-action=new-connection]').click();
    await page.locator('[name=targetMode]').selectOption('DOCKER');await page.locator('[name=deviceId]').selectOption(device.id);
    await page.locator('[name=containerId] option[value="'+containers[0].id+'"]').waitFor({state:'attached'});await page.locator('[name=containerId]').selectOption(containers[0].id);
    assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Connection form overflows');
    await page.screenshot({path:'artifacts/database-target-'+width+'.png',fullPage:true});
    report.checks.push('Connection form '+width+'px: no horizontal overflow');
    await page.locator('[data-db-action=back]').click();
  }
  assert.deepEqual(report.pageErrors,[]);
  assert.deepEqual(report.consoleErrors,[]);
}catch(error){report.failure=error.message;process.exitCode=1;console.error('FAIL',error.message);await page.screenshot({path:'artifacts/database-target-failure.png',fullPage:true}).catch(()=>{});}
finally{
  for(const id of saved)await api('/databases/'+id,'DELETE').catch(()=>{});
  await writeFile('artifacts/database-target-report.json',JSON.stringify(report,null,2));
  if(csrf)await page.request.post(base+'/logout',{headers:{'X-CSRF-TOKEN':csrf}}).catch(()=>{});
  await browser.close();
}
