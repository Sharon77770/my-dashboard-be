const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');

const dom = new JSDOM('<meta name="csrf-header" content="X-CSRF-TOKEN"><meta name="csrf-token" content="test"><section id="databases"></section>', {runScripts:'outside-only', url:'http://localhost'});
const {window} = dom;
const {document} = window;
let pollCount = 0;
window.WorkspaceUI = {escape:value=>String(value??''),icon:()=>''};
window.fetch = async (url, options = {}) => {
  const path = String(url).replace('/api/v1','');
  let data;
  if (path === '/databases') data = [{id:'database-1',name:'Local',type:'SQLITE',accessMode:'READ_ONLY'}];
  else if (path.endsWith('/schemas')) data = [{name:'main'}];
  else if (path.includes('/tables?') || path.includes('/functions?') || path.endsWith('/favorites')) data = [];
  else if (path === '/databases/database-1/query' && options.method === 'POST') data = {executionId:'query-1',state:'RUNNING',durationMs:0};
  else if (path.endsWith('/query/query-1')) {
    pollCount++;
    data = pollCount === 1 ? {executionId:'query-1',state:'RUNNING',durationMs:350} : {executionId:'query-1',state:'COMPLETED',durationMs:700,columns:['value'],rows:[{value:'done'}]};
  } else throw new Error(`Unexpected request: ${path}`);
  return {ok:true,status:200,json:async()=>data};
};
window.eval(fs.readFileSync('src/main/resources/static/js/databases.js','utf8'));
const wait = milliseconds => new Promise(resolve=>setTimeout(resolve,milliseconds));

(async()=>{
  window.WorkspaceDatabases.open('databases');
  await wait(20);
  document.querySelector('[data-db-connection="database-1"]').click();
  await wait(20);
  const editor = document.querySelector('#db-sql');
  editor.value = 'SELECT 1';
  editor.dispatchEvent(new window.Event('input',{bubbles:true}));
  editor.focus();
  document.querySelector('[data-db-action="run"]').click();
  await wait(430);
  assert.equal(pollCount,1);
  assert.equal(document.querySelector('#db-sql'),editor,'running poll must preserve the editor node');
  assert.equal(document.activeElement,editor,'running poll must preserve keyboard focus');
  assert.match(document.querySelector('.db-result-head').textContent,/RUNNING/);
  await wait(380);
  assert.match(document.querySelector('.db-result-head').textContent,/COMPLETED/);
  assert.equal(document.querySelector('#db-sql').value,'SELECT 1');
  console.log('PASS Database Studio polling preserves editor focus until completion');
  dom.window.close();
})().catch(error=>{console.error(error);dom.window.close();process.exitCode=1});
