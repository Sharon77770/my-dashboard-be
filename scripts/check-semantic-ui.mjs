import {chromium} from 'playwright';
import {readFile,writeFile,mkdir,access} from 'node:fs/promises';
import assert from 'node:assert/strict';
const env=Object.fromEntries((await readFile('.tools/studio-ide-qa.env','utf8')).trim().split(/\r?\n/).map(s=>{const i=s.indexOf('=');return [s.slice(0,i),s.slice(i+1)];}));
const browser=await chromium.launch({headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
try {
 await page.route('**/js/**',async route=>{const path='src/main/resources/static'+new URL(route.request().url()).pathname;try{await access(path);await route.fulfill({path,contentType:'application/javascript'});}catch{await route.continue();}});
 await page.route('**/vendor/workspace-ui.css*',route=>route.fulfill({path:'src/main/resources/static/vendor/workspace-ui.css',contentType:'text/css'}));
 await page.goto('http://127.0.0.1:18187');
 if(await page.locator('#id').count()){await page.locator('#id').fill(env.DASHBOARD_AUTH_ID);await page.locator('#password').fill(env.DASHBOARD_AUTH_PASSWORD);await Promise.all([page.waitForURL(u=>!u.pathname.startsWith('/login')),page.locator('button[type=submit]').click()]);}
 await page.waitForFunction(()=>window.WorkspaceUI?.stateTone);await mkdir('artifacts/semantic-ui',{recursive:true});
 const results=[];
 for(const theme of ['dark','light']) {
  await page.evaluate(theme=>document.documentElement.dataset.theme=theme,theme);
  for(const id of ['home','devices','services','databases','telemetry','cloud','notes','calendar','timetable','military','assistant','github','studio','clipboard','recent','apps','logs','files','terminal','remote','device-codex']) {
   await page.evaluate(route=>{if(route==='home')document.querySelector('[data-view=home]').click();else window.dispatchEvent(new CustomEvent('assistant:navigate',{detail:{route}}));},id);await page.waitForTimeout(180);
   const result=await page.evaluate(id=>{const el=document.getElementById(id);return {id,active:el?.classList.contains('active'),width:el?.clientWidth,overflow:document.documentElement.scrollWidth>innerWidth+2,badges:el?.querySelectorAll('.ui-status').length};},id);results.push({theme,...result});
   if(['devices','cloud','databases','home'].includes(id))await page.screenshot({path:`artifacts/semantic-ui/${theme}-${id}.png`,animations:'disabled'});
  }
 }
 // Check visual state roles independently of live service health/availability.
 const roles=await page.evaluate(()=>['RUNNING','FAILED','SUCCEEDED','IDLE','unknown'].map(value=>[value,WorkspaceUI.stateTone(value)]));
 assert.deepEqual(roles,[['RUNNING','info'],['FAILED','danger'],['SUCCEEDED','success'],['IDLE','neutral'],['unknown','neutral']]);
 await page.setViewportSize({width:390,height:844});
 for(const id of ['home','devices','cloud','databases','notes','studio']){await page.evaluate(route=>{if(route==='home')document.querySelector('[data-view=home]').click();else window.dispatchEvent(new CustomEvent('assistant:navigate',{detail:{route}}));},id);await page.waitForTimeout(150);results.push({theme:'light',mobile:true,id,overflow:await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+2)});}
 await writeFile('artifacts/semantic-ui/report.json',JSON.stringify({errors,results},null,2));
 assert.equal(results.filter(r=>r.active===false).length,0,'Every requested route must be active');assert.deepEqual(errors,[]);assert.equal(results.filter(r=>r.overflow).length,0,'Page overflow');console.log('PASS live app rendering, dark/light and mobile overflow, state mapping; see report for active routes.');
} finally {await browser.close();}
