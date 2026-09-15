const {JSDOM}=require('jsdom');const fs=require('fs'),assert=require('node:assert/strict');
const dom=new JSDOM('<div id="logs"></div>',{runScripts:'outside-only',url:'http://localhost'}),w=dom.window,d=w.document;
w.eval(fs.readFileSync('src/main/resources/static/js/log-presentation.js','utf8'));
w.eval(fs.readFileSync('src/main/resources/static/js/device-logs.js','utf8'));
const jobs=new Map(),deleted=[];let index=0,delayed=null;
const escape=s=>String(s).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('"','&quot;');
const api=async(path,method,body)=>{
 if(path==='/workspace')return {devices:[{id:'local',name:'Local',rootPath:'/tmp'}]};
 if(method==='POST'){const id=String(++index);jobs.set(id,{id,action:body.action,state:body.action==='logs-targets'?'SUCCEEDED':'RUNNING',result:{logTargets:[{id:'a'.repeat(64),name:'<img src=x>',status:'Up'}]},events:body.action==='logs-follow'?[{sequence:1,event:'log-append',text:'<script>hello</script>\n'}]:[]});if(delayed)await delayed.promise;return {id};}
 const id=path.split('/').pop();if(method==='DELETE'){deleted.push(id);return null;}return jobs.get(id);
};
const tick=()=>new Promise(r=>setTimeout(r,20));
(async()=>{
 w.WorkspaceLogs.init({api,escape});await w.WorkspaceLogs.open('logs');assert.equal(d.querySelectorAll('img').length,0);assert.equal(d.querySelector('[data-log-start]').disabled,false);
 d.querySelector('[data-log-start]').click();await tick();assert.equal(d.querySelector('[data-log-output]').textContent,'<script>hello</script>\n');assert.equal(d.querySelectorAll('script').length,0);
 await new Promise(r=>setTimeout(r,450));assert.equal(d.querySelector('[data-log-output]').textContent,'<script>hello</script>\n');
 const jobCount=index;
 const color=d.querySelector('[data-log-color]');color.value='levels';color.dispatchEvent(new w.Event('change'));
 const wrap=d.querySelector('[data-log-wrap]');wrap.checked=false;wrap.dispatchEvent(new w.Event('change'));
 assert.equal(index,jobCount,'presentation changes do not reconnect');assert.equal(deleted.includes('2'),false);
 assert.equal(d.querySelector('[data-log-output]').dataset.wrap,'false');assert.equal(JSON.parse(w.localStorage.getItem('workspace-log-view-v1:owner')).color,'levels');
 await w.WorkspaceLogs.open('home');assert.ok(deleted.includes('2'));
 await w.WorkspaceLogs.open('logs');let release;delayed={promise:new Promise(r=>release=r)};d.querySelector('[data-log-start]').click();await tick();await w.WorkspaceLogs.open('home');release();await tick();assert.ok(deleted.includes('4'),'late created job cancelled');
 console.log('PASS logs: target XSS, text-only output, sequence deduplication, navigation stop, late creation cancellation');dom.window.close();
})().catch(error=>{console.error(error);dom.window.close();process.exitCode=1;});
