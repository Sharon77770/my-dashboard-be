import {chromium} from 'playwright';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import assert from 'node:assert/strict';

// Read-only navigation of the existing isolated QA dashboard, never production credentials.
const base=process.env.STUDIO_TEST_URL||'http://127.0.0.1:18187';
const credentials=Object.fromEntries((await readFile('.tools/studio-ide-qa.env','utf8')).trim().split(/\r?\n/).map(line=>{const index=line.indexOf('=');return [line.slice(0,index),line.slice(index+1)];}));
const browser=await chromium.launch({headless:true});
const page=await browser.newPage({viewport:{width:1440,height:1000}});
page.setDefaultTimeout(15000);
const errors=[],consoleErrors=[],httpErrors=[],screens=[],failures=[];
const redact=text=>Object.entries(credentials).filter(([key,value])=>/PASSWORD|TOKEN|SECRET/.test(key)&&value).reduce((result,[,secret])=>result.replaceAll(secret,'[redacted]'),String(text));
page.on('pageerror',error=>errors.push(redact(error.message)));
page.on('console',message=>{if(message.type()==='error')consoleErrors.push(redact(message.text()));});
page.on('response',response=>{if(response.status()>=400)httpErrors.push({path:new URL(response.url()).pathname,status:response.status()});});
await mkdir('artifacts/ui',{recursive:true});
try{
  await page.goto(base);
  for(const theme of ['dark','light'])for(const width of [1440,390]){
    await page.setViewportSize({width,height:width===390?844:1000});
    await page.evaluate(theme=>document.documentElement.dataset.theme=theme,theme);
    await page.screenshot({path:`artifacts/ui/login-${theme}-${width}.png`});
  }
  await page.locator('#id').fill(credentials.DASHBOARD_AUTH_ID);
  await page.locator('#password').fill(credentials.DASHBOARD_AUTH_PASSWORD);
  await Promise.all([page.waitForURL(url=>!url.pathname.startsWith('/login')),page.locator('button[type=submit]').click()]);
  await page.waitForFunction(()=>window.WorkspaceApps?.all().length);
  const apps=await page.evaluate(()=>window.WorkspaceApps.all().filter(app=>app.route).map(app=>({id:app.id,route:app.route})));
  for(const theme of ['dark','light'])for(const width of [1440,390]){
    await page.setViewportSize({width,height:width===390?844:1000});
    await page.evaluate(theme=>document.documentElement.dataset.theme=theme,theme);
    for(const app of [{id:'home',route:'home'},...apps]){
      try{
        if(app.id==='home')await page.locator(width===390?'.os-navigation [data-view=home]':'.workspace-bar [data-view=home]').evaluate(button=>button.click());
        else{
          await page.locator(width===390?'.os-navigation [data-launcher=drawer]':'.activity-rail [data-launcher=drawer]').evaluate(button=>button.click());
          await page.locator('#app-drawer').waitFor({state:'visible'});
          await page.locator('#app-drawer [data-view="'+app.route+'"]').click();
        }
        await page.locator('#'+app.route+'.active').waitFor({timeout:15000});
        await page.waitForTimeout(200);
        const geometry=await page.evaluate(id=>{
          const view=document.getElementById(id),rect=view.getBoundingClientRect();
          return {documentWidth:document.documentElement.scrollWidth,width:innerWidth,viewWidth:rect.width,bodyFont:getComputedStyle(document.body).fontSize,unnamedIcons:[...view.querySelectorAll('button:has(svg)')].filter(button=>button.getBoundingClientRect().width&&!button.textContent.trim()&&!button.getAttribute('aria-label')&&!button.title).length};
        },app.route);
        assert(geometry.documentWidth<=width,'Document horizontal overflow');
        assert(geometry.viewWidth>0&&geometry.viewWidth<=width,'Invalid view width');
        assert.equal(geometry.unnamedIcons,0,'Unnamed icon control');
        screens.push({app:app.id,theme,width,...geometry});
        await page.screenshot({path:`artifacts/ui/${app.id}-${theme}-${width}.png`});
      }catch(error){failures.push({app:app.id,theme,width,error:redact(error.message)});await page.keyboard.press('Escape');}
    }
    console.log('CHECK',theme,width,'screens',screens.length,'failures',failures.length);
    for(const action of ['library','settings','browser-settings','tailscale-settings']){
      try{
        await page.locator('.activity-rail [data-launcher=drawer]').evaluate(button=>button.click());
        await page.locator('#app-drawer').waitFor({state:'visible'});
        if(action!=='library')await page.locator('#app-drawer [data-action="'+action+'"]').click();
        await page.waitForTimeout(250);
        const dialog=page.locator('dialog[open]').last();await dialog.waitFor({state:'visible'});
        const box=await dialog.boundingBox();assert(box.width<=width&&box.x>=0,'Dialog exceeds viewport');
        await page.screenshot({path:`artifacts/ui/${action}-${theme}-${width}.png`});
        await page.keyboard.press('Escape');
      }catch(error){failures.push({app:action,theme,width,error:redact(error.message)});await page.keyboard.press('Escape');}
    }
  }
  assert.deepEqual(failures,[]);assert.deepEqual(errors,[]);
  console.log('PASS UI navigation, geometry and named icons:',screens.length,'screens');
}catch(error){console.error(redact(error.message));process.exitCode=1;}
finally{
  await writeFile('artifacts/ui/report.json',JSON.stringify({base,screens,failures,errors,consoleErrors,httpErrors},null,2));
  await browser.close();
}
