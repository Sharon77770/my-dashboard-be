const assert=require('node:assert/strict');
const fs=require('node:fs');
const {JSDOM}=require('jsdom');

const dom=new JSDOM('<meta name="csrf-header" content="X-CSRF-TOKEN"><meta name="csrf-token" content="fixture"><section id="services"><div class="page-head"><button data-services="create">새 서비스</button></div><div id="services-content"></div></section><section id="databases"></section><section id="telemetry"><div class="page-head"><button data-telemetry="create">서비스 등록</button></div><div id="telemetry-content"></div></section>',{runScripts:'outside-only',url:'http://localhost'});
const window=dom.window,document=window.document;
let nextId=0;
Object.defineProperty(window.crypto,'randomUUID',{value:()=>`test-${++nextId}`});
window.fetch=async url=>{
 const path=String(url).replace('/api/v1','');
 const data={
  '/services':[{id:'service-1',name:'PFM API',icon:'server',environment:'Production'}],
  '/services/service-1/resources':[{id:'device-binding',type:'DEVICE',reference:'device-1',label:'Spark',orphaned:false}],
  '/services/service-1/health':{state:'HEALTHY',signals:[]},
  '/services/service-1/activity':[],
  '/services/service-1/context':{service:{id:'service-1',name:'PFM API',icon:'server',environment:'Production'},resources:[{id:'device-binding',type:'DEVICE',reference:'device-1',label:'Spark',orphaned:false}],health:{state:'HEALTHY',signals:[]},runtime:{'device-1':{device:{remoteProtocol:'NONE'},status:null}},telemetry:{},github:{},activity:[]},
  '/services/service-1/runtime':[{resourceId:'device-binding',type:'DEVICE',name:'Spark',deviceId:'device-1',state:'ONLINE',cpu:12,memory:24,disk:35,image:'',detail:'SSH Linux 계측',checkedAt:1}],
  '/databases':[],
  '/telemetry/services':[{serviceId:'telemetry-1',serviceName:'PFM API',serviceType:'Backend API',status:'Receiving data',requestsToday:12,dau:3,errorRate:0,lastUsedAt:'2026-09-30T08:00:00Z'}],
  '/telemetry/services/telemetry-1/analytics?range=24h':{summary:{serviceName:'PFM API',serviceType:'Backend API',status:'Receiving data',enabled:true,lastUsedAt:'2026-09-30T08:00:00Z',requests:12,uniqueUsers:3,errorRate:0,p95LatencyMs:20,errors:0,gauges:{}},timeline:[],endpoints:{},endpointErrorRates:{},statuses:{},methods:{}}
 }[path];
 assert.notEqual(data,undefined,`unexpected API call ${path}`);
 return {ok:true,status:200,json:async()=>data};
};
window.eval(fs.readFileSync('src/main/resources/static/js/ui.js','utf8'));
window.eval(fs.readFileSync('src/main/resources/static/js/services.js','utf8'));
window.eval(fs.readFileSync('src/main/resources/static/js/databases.js','utf8'));
window.eval(fs.readFileSync('src/main/resources/static/js/telemetry.js','utf8'));
const tick=()=>new Promise(resolve=>setTimeout(resolve,20));

(async()=>{
 window.WorkspaceServices.open('services');
 await tick();
 document.querySelector('[data-service-open="service-1"]').click();
 await tick();
 assert.ok(document.querySelector('#services').classList.contains('service-detail-open'));
 assert.ok(document.querySelector('[data-services="back"] svg'));
 assert.equal(document.querySelector('[data-services="refresh"]').getAttribute('aria-label'),'서비스 새로고침');
 document.querySelector('[data-service-tab="Runtime"]').click();
  await tick();
  assert.ok(document.querySelector('[data-open="TERMINAL"]'));
  assert.ok(document.querySelector('[data-open="FILES"]'));
 document.querySelector('[data-services="back"]').click();
 await tick();
 assert.equal(document.querySelector('#services').classList.contains('service-detail-open'),false);

 window.WorkspaceDatabases.open('databases');
 await tick();
 assert.equal(document.querySelector('#databases .eyebrow'),null);
 assert.ok(document.querySelector('#databases [data-db-action="toggle-sidebar"] svg'));
 assert.ok(document.querySelector('[data-db-action="new-connection"][aria-label] svg'));
 assert.ok(document.querySelector('[data-db-action="run"][aria-label] svg'));
 assert.ok(document.querySelector('[data-db-action="cancel"][aria-label] svg'));
 assert.ok(document.querySelector('.db-more summary[aria-label] svg'));
 document.querySelector('[data-db-action="new-tab"]').click();
 assert.equal(document.querySelectorAll('.db-tab').length,2);
 document.querySelectorAll('[data-db-close]')[1].click();
 assert.equal(document.querySelectorAll('.db-tab').length,1);

 window.WorkspaceTelemetry.open('telemetry');
 await tick();
 assert.equal(document.querySelector('#telemetry-content [data-telemetry="create"]'),null,'Telemetry list avoids a second create action');
 assert.ok(document.querySelector('.telemetry-card footer [data-telemetry="guide"][aria-label] svg'));
 document.querySelector('.telemetry-card-open').click();
 await tick();
 assert.ok(document.querySelector('#telemetry').classList.contains('telemetry-detail-open'));
 assert.ok(document.querySelector('.telemetry-back[aria-label] svg'));
 assert.ok(document.querySelector('.telemetry-menu summary[aria-label] svg'));
 document.querySelector('.telemetry-back').click();
 await tick();
 assert.equal(document.querySelector('#telemetry').classList.contains('telemetry-detail-open'),false);
 document.querySelector('.telemetry-card footer [data-telemetry="guide"]').click();
 await tick();
 assert.ok(document.querySelector('#telemetry').classList.contains('telemetry-detail-open'));
 assert.ok(document.querySelector('.telemetry-guide'));
 document.querySelector('#telemetry-content [data-telemetry="back"]').click();
 await tick();
 document.querySelector('#telemetry>.page-head [data-telemetry="create"]').click();
 await tick();
 assert.ok(document.querySelector('#telemetry').classList.contains('telemetry-detail-open'));
 assert.ok(document.querySelector('#telemetry-create-form'));
 document.querySelector('#telemetry-content [data-telemetry="back"]').click();
 await tick();
 console.log('PASS workstation apps: Service navigation, Database icon actions, accessible SQL tabs, compact Telemetry list/detail');
 dom.window.close();
})().catch(error=>{console.error(error);dom.window.close();process.exitCode=1;});
