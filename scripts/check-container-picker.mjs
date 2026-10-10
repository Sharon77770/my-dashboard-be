import {chromium} from 'playwright';
import {readFile,writeFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
const browser=await chromium.launch({headless:true});
const page=await browser.newPage({viewport:{width:1100,height:800}});
const errors=[];page.on('pageerror',error=>errors.push(error.message));
try {
  await page.setContent('<form><label>Container<select name="container" required data-container-search><option value="">Choose</option></select></label><button>Submit</button></form>');
  await page.addStyleTag({path:'src/main/resources/static/vendor/workspace-ui.css'});
  await page.addScriptTag({path:'src/main/resources/static/js/container-picker.js'});
  await page.evaluate(()=>{const select=document.querySelector('select');for(let i=0;i<300;i++)select.add(new Option('worker-'+i+' · redis:7','id-'+i));select.add(new Option('한글 API · postgres:17','special-db'));window.changes=0;select.addEventListener('change',()=>window.changes++);});
  const input=page.getByRole('combobox');
  await input.fill('postgres');await page.getByRole('option').waitFor();assert.equal(await page.getByRole('option').count(),1);
  await input.press('Enter');assert.equal(await page.locator('select').inputValue(),'special-db');
  assert.equal(await page.evaluate(()=>new FormData(document.querySelector('form')).get('container')),'special-db');
  assert.equal(await page.evaluate(()=>window.changes),1);
  await input.fill('id-299');await input.press('Enter');assert.equal(await page.locator('select').inputValue(),'id-299');
  await input.fill('no-matches');assert.equal(await page.getByRole('option').count(),0);await input.press('Escape');assert.match(await input.inputValue(),/worker-299/);
  await input.fill('한글');await page.getByRole('option').click();assert.equal(await page.locator('select').inputValue(),'special-db');
  await page.evaluate(()=>document.querySelector('select').disabled=true);assert(await input.isDisabled());
  await page.evaluate(()=>{const s=document.querySelector('select');s.replaceChildren(new Option('Choose',''),new Option('new-container','new'));s.disabled=false;});
  await input.fill('new');await input.press('ArrowDown');await input.press('Enter');assert.equal(await page.locator('select').inputValue(),'new');
  await page.setViewportSize({width:390,height:700});await input.fill('new');await page.screenshot({path:'artifacts/container-picker-mobile.png'});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  assert.deepEqual(errors,[]);
  console.log('PASS 301 options, name/image/ID/Korean search, keyboard/mouse, form value, change event, empty result, disabled and refresh, mobile overflow');
} finally {await browser.close();}
