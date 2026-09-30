const { JSDOM } = require('jsdom');
const fs = require('node:fs');
const assert = require('node:assert/strict');
const html = fs.readFileSync('src/main/resources/templates/home.html', 'utf8');
const script = fs.readFileSync('src/main/resources/static/js/assistant-memory.js', 'utf8');
const css = fs.readFileSync('src/main/resources/static/css/assistant.css', 'utf8');
const tick = () => new Promise(resolve => setTimeout(resolve, 25));

(async () => {
  const dom = new JSDOM(html, {runScripts:'outside-only',url:'http://localhost'});
  const {window:w} = dom, d = w.document;
  const items = [];
  let preferences = {autoArchive:true,autoDelete:true,protectManual:true,
    tentativeDays:30,possibilityDays:60,followUpDays:14,archivedDays:90,lastCleanupAt:0};
  w.confirm = () => true;
  w.WorkspaceAssistantRuntime = {async api(path,method='GET',body) {
    if(path.includes('/preferences')) {
      if(method === 'PUT') preferences = {...preferences,...body};
      return preferences;
    }
    if(path.includes('?')) {
      const params = new URL(path,'http://localhost').searchParams;
      const found = items.filter(item => item.content.includes(params.get('query'))
        && (!params.get('status') || item.status === params.get('status'))
        && (!params.get('type') || item.type === params.get('type'))
        && (!params.get('confidence') || item.confidence === params.get('confidence'))
        && (!params.get('scope') || item.scope === params.get('scope')));
      const offset = Number(params.get('offset'));
      return {items:found.slice(offset,offset+25),nextOffset:offset+Math.min(25,found.length-offset),hasMore:found.length>offset+25};
    }
    if(method === 'POST' && path.endsWith('/memories')) {
      const item = {...body,id:'memory-1',status:'ACTIVE'};items.push(item);return item;
    }
    const id = path.split('/').at(-1);
    const item = items.find(entry => entry.id === id || path.includes('/'+entry.id+'/'));
    if(method === 'DELETE' && path.endsWith('/pin')) {item.pinned=false;return item;}
    if(method === 'DELETE') {items.splice(items.indexOf(item),1);return null;}
    if(method === 'PUT') {Object.assign(item,body);return item;}
    if(path.endsWith('/pin')) item.pinned=true;
    if(path.endsWith('/archive')) item.status='ARCHIVED';
    if(path.endsWith('/restore')) item.status='ACTIVE';
    return item;
  }};
  w.eval(script);
  assert.ok(d.querySelector('#assistant-settings .assistant-settings-content button').textContent.includes('Memory'));
  d.querySelector('#assistant-settings .assistant-settings-content button').click();
  await tick();
  d.querySelector('#assistant-memory-new').click();
  const form = d.querySelector('#assistant-memory-form');
  form.elements.content.value = '<script>alert(1)</script> 기억';
  form.dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));
  await tick();
  assert.equal(items.length,1);
  assert.equal(d.querySelector('.assistant-memory-row strong').textContent,items[0].content);
  assert.equal(d.querySelector('.assistant-memory-row script'),null);
  d.querySelector('[data-memory-action="pin"]').click();await tick();
  assert.equal(items[0].pinned,true);
  d.querySelector('[data-memory-action="archive"]').click();await tick();
  assert.equal(items[0].status,'ARCHIVED');
  d.querySelector('#assistant-memory-filter').value='ACTIVE';
  d.querySelector('#assistant-memory-filter').dispatchEvent(new w.Event('change'));await tick();
  assert.equal(d.querySelector('.assistant-memory-row'),null);
  assert.match(css,/@media\(max-width:700px\).*assistant-memory-dialog\{width:100vw/);
  assert.match(css,/assistant-memory-row\{padding:10px 0\}/);
  dom.window.close();
  console.log('PASS assistant memory: settings CRUD, filtering, safe text rendering, mobile layout rules');
})().catch(error => {console.error(error);process.exitCode=1;});
