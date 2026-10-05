const {JSDOM}=require('jsdom'),fs=require('node:fs'),assert=require('node:assert/strict');
const dom=new JSDOM('<meta name="csrf-header" content="X-CSRF-TOKEN"><meta name="csrf-token" content="fixture"><section id="telemetry"><div id="telemetry-content"></div></section>',{url:'http://localhost',runScripts:'outside-only',pretendToBeVisual:true});
const w=dom.window,d=w.document;let requests=1,hasData=false,pending=null,foreground=0;
w.WorkspaceUI={escape:value=>String(value??'').replaceAll('<','&lt;'),icon:()=>'',skeleton:()=>'',emptyState:()=>'',beginTask:()=>{foreground++;return()=>{};}};
w.fetch=async url=>{
 if(pending){const next=pending;pending=null;await next;}
 const summary={serviceId:'one',serviceName:'Example',serviceType:'API',enabled:true,requests,requestsToday:requests,errors:0,errorRate:0,lastUsedAt:hasData?'2026-10-05T00:00:00Z':null};
 return {ok:true,status:200,json:async()=>String(url).includes('/analytics?')?{summary,timeline:[{timestamp:Date.now(),requests,errors:0}],endpoints:{},endpointErrorRates:{},statuses:{},methods:{}}:[summary]};
};
for(const file of ['live-dom','telemetry'])w.eval(fs.readFileSync('src/main/resources/static/js/'+file+'.js','utf8'));
const tick=()=>new Promise(resolve=>setTimeout(resolve,20));
(async()=>{
 w.WorkspaceTelemetry.open('telemetry');await tick();const card=d.querySelector('.telemetry-card');requests=2;const count=foreground;await w.WorkspaceTelemetry.refresh();assert.equal(foreground,count);assert.equal(d.querySelector('.telemetry-card'),card);assert.match(card.textContent,/2/);
 d.querySelector('[data-telemetry=open]').click();await tick();assert.ok(d.querySelector('.telemetry-onboarding'));hasData=true;await w.WorkspaceTelemetry.refresh();assert.ok(d.querySelector('.telemetry-chart'));assert.equal(d.querySelector('.telemetry-onboarding'),null);
 const chart=d.querySelector('.telemetry-chart'),menu=d.querySelector('details');menu.open=true;requests=3;await w.WorkspaceTelemetry.refresh();assert.equal(d.querySelector('.telemetry-chart'),chart);assert.equal(d.querySelector('details'),menu);assert.equal(menu.open,true);assert.match(d.querySelector('.telemetry-kpi').textContent,/3/);
 let release;pending=new Promise(resolve=>release=resolve);const refresh=w.WorkspaceTelemetry.refresh();d.querySelector('[data-telemetry=guide]').click();assert.ok(d.querySelector('#telemetry-snippet'));release();await refresh;assert.ok(d.querySelector('#telemetry-snippet'),'late analytics cannot replace integration guide');
 const create=d.createElement('button');create.dataset.telemetry='create';d.body.append(create);create.click();const input=d.querySelector('[name=name]');input.value='draft';input.focus();await w.WorkspaceTelemetry.refresh();assert.equal(d.activeElement,input);assert.equal(input.value,'draft');
 console.log('PASS Telemetry live updates: list identity, quiet requests, first ingestion, chart/menu preservation, late response and form draft');w.close();
})().catch(error=>{console.error(error);w.close();process.exitCode=1;});
