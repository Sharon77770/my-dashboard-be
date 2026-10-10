import { chromium } from 'playwright';
import fs from 'node:fs';
import assert from 'node:assert/strict';
const fixture=JSON.parse(fs.readFileSync('.tools/communications-qa.json','utf8').replace(/^\uFEFF/,''));
const browser=await chromium.launch({headless:true});
const page=await browser.newPage({viewport:{width:1440,height:1000}});
try{
 for(let attempt=0;attempt<60;attempt++){try{if((await page.request.get(fixture.url+'/login')).ok())break;}catch{}if(attempt===59)throw Error('Dashboard startup timeout');await page.waitForTimeout(1000);}
 await page.goto(fixture.url+'/login');await page.locator('[name=id]').fill(fixture.username);await page.locator('[name=password]').fill(fixture.password);await page.locator('button[type=submit]').click();await page.waitForURL(fixture.url+'/');
 const api=(path,method='GET',body)=>page.evaluate(async({path,method,body})=>{
  const header=document.querySelector('meta[name=csrf-header]').content,token=document.querySelector('meta[name=csrf-token]').content;
  const response=await fetch('/api/v1'+path,{method,headers:{'Content-Type':'application/json',[header]:token},body:body===undefined?undefined:JSON.stringify(body)});
  if(!response.ok)throw Error('API status '+response.status);
  return response.status===204?null:response.json();
 },{path,method,body});
 await page.locator('[data-launcher=drawer]').first().click();await page.locator('#drawer-search').fill('Communications');await page.locator('[data-drawer-app=communications] [data-view=communications]').click();
 for(const provider of (fixture.includeWine?['SLACK','DISCORD','KAKAOTALK']:['SLACK','DISCORD']).filter(value=>!process.env.COMMUNICATION_REMOTE_PROVIDER||value===process.env.COMMUNICATION_REMOTE_PROVIDER)){
  await page.locator(`[data-comm-remote="${provider}"]`).click();
  await page.locator('.authentication-browser-screen canvas').first().waitFor({timeout:30000});
  const profile=(await api('/communications/bridge/profiles')).find(item=>item.provider===provider);assert.ok(profile);
  if(provider==='KAKAOTALK'){
   for(let attempt=0;attempt<180;attempt++){
    const snapshot=await api(`/communications/bridge/profiles/${profile.id}/snapshot`);
    if(attempt%10===0){fs.mkdirSync('artifacts',{recursive:true});await page.screenshot({path:'artifacts/communications-wine-starting.png'});}
    assert.ok(!['ERROR','APPLICATION_EXITED'].includes(snapshot.state),'Wine application failed: '+snapshot.state);
    if(snapshot.state==='RUNNING')break;
    if(attempt===179)throw Error('Wine startup timed out');
    await page.waitForTimeout(2000);
   }
   await page.waitForTimeout(10000);
  }else await page.waitForTimeout(20000);
  fs.mkdirSync('artifacts',{recursive:true});await page.screenshot({path:`artifacts/communications-screen-${provider.toLowerCase()}.png`});
  await page.locator('[data-auth-close]').click();
  if(provider!=='KAKAOTALK')await api(`/communications/bridge/profiles/${profile.id}`,'DELETE');
  console.log('PASS remote canvas: '+provider+' (no account login or sending performed)');
 }
}finally{await browser.close();}
