import {chromium} from 'playwright';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import assert from 'node:assert/strict';
const base='http://127.0.0.1:18187';
const env=Object.fromEntries((await readFile('.tools/studio-ide-qa.env','utf8')).trim().split(/\r?\n/).map(s=>{const i=s.indexOf('=');return [s.slice(0,i),s.slice(i+1)];}));
const browser=await chromium.launch({headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000},acceptDownloads:true});
const report={errors:[],consoleErrors:[],screens:[],checks:[]};page.on("console",m=>{if(m.type()==="error")report.consoleErrors.push(m.text());});page.on('pageerror',e=>report.errors.push(e.message));
await page.route('**/js/cloud-drive.js*',r=>r.fulfill({path:'src/main/resources/static/js/cloud-drive.js',contentType:'application/javascript'}));
await page.route('**/vendor/workspace-ui.css*',r=>r.fulfill({path:'src/main/resources/static/vendor/workspace-ui.css',contentType:'text/css'}));
let csrf;const folder='/drive-layout-'+Date.now();
async function api(path,method='GET',data){const r=await page.request.fetch(base+'/api/v1'+path,{method,headers:{'X-CSRF-TOKEN':csrf},data});assert(r.ok(),path+': '+r.status());const text=await r.text();return text?JSON.parse(text):null;}
try{
 await mkdir('artifacts/drive',{recursive:true});await page.goto(base);await page.locator('#id').fill(env.DASHBOARD_AUTH_ID);await page.locator('#password').fill(env.DASHBOARD_AUTH_PASSWORD);
 await Promise.all([page.waitForURL(u=>!u.pathname.startsWith('/login')),page.locator('button[type=submit]').click()]);csrf=await page.locator('meta[name=csrf-token]').getAttribute('content');
 await api('/cloud/entries','POST',{path:folder,directory:true});await api('/cloud/entries','POST',{path:folder+'/Documents',directory:true});
 for(let i=1;i<=28;i++)await api('/cloud/entries','POST',{path:folder+'/document-'+String(i).padStart(2,'0')+'.txt',directory:false});
 await page.locator('.activity-rail [data-launcher=drawer]').click();await page.locator('#app-drawer [data-view=cloud]').click();
 await page.locator('[data-cloud-expand="'+folder+'"]').click();await page.locator('.cloud-sidebar [data-cloud-path="'+folder+'/Documents"]').waitFor();
 await page.locator('.cloud-sidebar [data-cloud-path="'+folder+'"]').click();await page.locator('.cloud-item').filter({hasText:'document-28.txt'}).waitFor();report.checks.push('Real folder listing and lazy folder navigation');
 await page.locator('[data-cloud-files]').setInputFiles({name:'uploaded.txt',mimeType:'text/plain',buffer:Buffer.from('drive layout check')});await page.locator('[data-cloud-open="'+folder+'/uploaded.txt"]').waitFor();report.checks.push('File upload');
 await page.locator('[data-cloud-select="'+folder+'/uploaded.txt"]').check();const download=page.waitForEvent('download');await page.locator('[data-cloud-action=download]').click();assert.equal((await download).suggestedFilename(),'uploaded.txt');report.checks.push('Selection and real download');
 await page.locator('[data-cloud-action=all]').click();await page.locator('[data-cloud-action=all]').click();
 for(const width of [1440,1024,768,390]){
  await page.setViewportSize({width,height:width<500?844:900});await page.waitForTimeout(150);
  const geometry=await page.evaluate(()=>{const root=document.querySelector('#cloud'),list=root.querySelector('.cloud-items'),bar=root.querySelector('.cloud-commandbar');const box=list.getBoundingClientRect();return {width:innerWidth,overflow:document.documentElement.scrollWidth>innerWidth,ratio:box.height/root.getBoundingClientRect().height,rowHeight:root.querySelector('.cloud-item').getBoundingClientRect().height,toolbarOverflow:bar.scrollWidth>bar.clientWidth+1};});
  report.screens.push(geometry);await page.screenshot({path:'artifacts/drive/'+width+'.png'});assert(!geometry.overflow,'Horizontal overflow '+width);assert(!geometry.toolbarOverflow,'Toolbar overflow '+width);assert(geometry.ratio>=.75,'File list too short '+width);
 }
 await page.locator('#cloud .ui-drawer-trigger').click();await page.locator('#cloud .ui-side-drawer[open]').waitFor();
 await page.locator('.ui-side-drawer [data-cloud-path="'+folder+'/Documents"]').click();await page.locator('#cloud .ui-side-drawer[open]').waitFor({state:'hidden'});report.checks.push('Mobile folder drawer navigation closes after selection');
 await page.setViewportSize({width:1440,height:900});await page.locator('.cloud-sidebar [data-cloud-path="'+folder+'"]').click();await page.locator('[data-cloud-open="'+folder+'/uploaded.txt"]').click();await page.locator('.cloud-text-editor').waitFor();
 assert(await page.evaluate(()=>document.querySelector('.cloud-editor').getBoundingClientRect().width/document.querySelector('#cloud').getBoundingClientRect().width>.9),'File editor must retain full width');await page.locator('[data-file-back]').click();report.checks.push('Existing file editor retains full width');
 assert.deepEqual(report.consoleErrors,[]);assert.deepEqual(report.errors,[]);console.log('PASS',JSON.stringify(report));
}catch(e){report.failure=e.message;process.exitCode=1;console.error(e.message);await page.screenshot({path:'artifacts/drive/failure.png'}).catch(()=>{});}
finally{if(csrf){await api('/cloud/entries?path='+encodeURIComponent(folder),'DELETE').catch(()=>{});const trash=await api('/cloud/trash').catch(()=>[]);for(const entry of trash.filter(e=>e.path===folder))await api('/cloud/trash/'+entry.id,'DELETE');}await writeFile('artifacts/drive/report.json',JSON.stringify(report,null,2));await browser.close();}
