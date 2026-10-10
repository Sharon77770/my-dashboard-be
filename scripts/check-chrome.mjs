import { chromium } from 'playwright';
import { readFile, mkdir } from 'node:fs/promises';
import assert from 'node:assert/strict';

// Real layout/interaction with a fake desktop transport; does not claim VNC or media playback proof.
const template = await readFile('src/main/resources/templates/home.html', 'utf8');
const styles = [...template.matchAll(/<link[^>]+th:href="@\{([^}]+\.css)\}"/g)].map(match => match[1]);
let html = template.replace(/<script\b[^>]*>[\s\S]*?<\/script>/g, '').replace(/th:href="@\{([^}]+)\}"/g, 'href="$1"');
const browser = await chromium.launch({headless:true});
const page = await browser.newPage();
const errors = [];
page.on('pageerror', error => errors.push(error.message));
await page.route('http://chrome.test/**', async route => {
  const path = new URL(route.request().url()).pathname;
  if (path === '/') return route.fulfill({contentType:'text/html',body:html});
  if (styles.includes(path)) return route.fulfill({contentType:'text/css',body:await readFile(`src/main/resources/static${path}`, 'utf8')});
  return route.fulfill({status:404,body:''});
});
await page.goto('http://chrome.test/');
await page.evaluate(() => {
  window.proof = {created:0, deleted:[], sizes:[], keys:[], fail:false};
  window.WorkspaceAssistantRuntime = {api:async (path, method, body) => {
    if (method === 'DELETE') {proof.deleted.push(path);return;}
    proof.created++; if(proof.fail) throw new Error('테스트 연결 실패');
    return {id:`test-${proof.created}`,kind:'APP',label:'Chrome',url:''};
  }};
  window.Guacamole = {
    WebSocketTunnel: class {},
    Client: class {
      constructor() {
        const element = document.createElement('div'), canvas = document.createElement('canvas');
        canvas.width=1600;canvas.height=900;element.append(canvas);
        this.display = {getElement:()=>element,getWidth:()=>canvas.width,getHeight:()=>canvas.height,scale:value=>{
          element.style.width=`${canvas.width*value}px`;element.style.height=`${canvas.height*value}px`;
          canvas.style.transformOrigin='0 0';canvas.style.transform=`scale(${value})`;canvas.style.position='absolute';
        }};
      }
      getDisplay(){return this.display;}
      connect(){this.onstatechange(3);}
      disconnect(){this.onstatechange?.(5);}
      sendSize(w,h){proof.sizes.push([w,h]);const c=this.display.getElement().firstChild;c.width=w;c.height=h;this.display.onresize?.();}
      sendKeyEvent(d,k){proof.keys.push([d,k]);}
      sendMouseState(){}
    },
    Mouse:class {}, Keyboard:class {reset(){}}, AudioContextFactory:{getAudioContext:()=>null}
  };
  Guacamole.Mouse.Touchpad = class {};
  window.showChrome = () => {
    document.querySelectorAll('.view').forEach(v=>v.classList.toggle('active',v.id==='chrome'));
    dispatchEvent(new CustomEvent('workspace:view',{detail:{id:'chrome'}}));
  };
  document.addEventListener('click', event=>{if(event.target.closest('[data-view="home"]'))document.getElementById('chrome').classList.remove('active');});
});
await page.addScriptTag({path:'src/main/resources/static/js/chrome.js'});
await mkdir('artifacts/chrome', {recursive:true});
const measurements=[];
for(const [width,height] of [[1440,900],[390,844],[844,390],[320,568]]) {
  await page.setViewportSize({width,height});
  await page.evaluate(()=>showChrome());
  await page.waitForTimeout(500);
  const bounds = await page.evaluate(()=>{
    const r=document.getElementById('chrome').getBoundingClientRect(), s=document.querySelector('.chrome-screen').getBoundingClientRect();
    const controls=[...document.querySelectorAll('.chrome-toolbar > button, .chrome-menu > summary')].map(e=>{const r=e.getBoundingClientRect();return {left:r.left,right:r.right,top:r.top,bottom:r.bottom};});
    return {left:r.left,top:r.top,width:r.width,height:r.height,screenHeight:s.height,controls};
  });
  assert.equal(bounds.left,0);assert.equal(bounds.top,0);assert.equal(bounds.width,width);assert.equal(bounds.height,height);
  assert(bounds.screenHeight>150);
  assert(bounds.controls.every(c=>c.left>=0&&c.right<=width&&c.top>=0&&c.bottom<=height));
  measurements.push({width,height,...bounds});
  await page.screenshot({path:`artifacts/chrome/${width}x${height}.png`});
}
await page.getByRole('button',{name:'키보드',exact:true}).click();
await page.getByRole('textbox',{name:'Chrome에 보낼 텍스트'}).fill('한글 test');
await page.getByRole('button',{name:'전송',exact:true}).click();
assert.equal(await page.getByRole('textbox',{name:'Chrome에 보낼 텍스트'}).inputValue(),'');
assert((await page.evaluate(()=>proof.keys)).some(([d,k])=>d===1&&k===0x01000000+'한'.codePointAt(0)));
await page.getByRole('button',{name:'Chrome에서 대시보드로 돌아가기'}).click();
await page.waitForTimeout(50);
assert.equal(await page.evaluate(()=>proof.deleted.length),1);
await page.evaluate(()=>{proof.fail=true;showChrome();});
await page.getByRole('status').filter({hasText:'테스트 연결 실패'}).waitFor();
await page.evaluate(()=>proof.fail=false);
await page.locator('.chrome-menu summary').click();
await page.getByRole('button',{name:'다시 연결',exact:true}).click();
await page.waitForTimeout(300);
assert.equal(await page.locator('.chrome-status').isVisible(),false);
assert.deepEqual(errors,[]);
console.log(JSON.stringify({measurements,created:await page.evaluate(()=>proof.created),result:'PASS: layout, Korean input, release, failed connection and retry'},null,2));
await browser.close();
