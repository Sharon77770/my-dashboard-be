const {JSDOM}=require('jsdom');
const fs=require('node:fs'),assert=require('node:assert/strict');
const script=fs.readFileSync('src/main/resources/static/js/studio-codex.js','utf8');
const tick=()=>new Promise(resolve=>setTimeout(resolve,20));
async function verify(idPrefix){
  const dom=new JSDOM('<section id="chat"></section>',{url:'https://localhost',runScripts:'outside-only'});
  const w=dom.window,panel=w.document.querySelector('#chat');
  let project={deviceId:'one',root:'/srv'},busy=false,failQuota=false,emptyQuota=false;
  const calls=[];w.eval(script);
  const chat=w.StudioCodex(panel,{idPrefix,storagePrefix:idPrefix||'ide:',escape:String,project:()=>project,auth(){},toast(){},publish(){},dirty:()=>false,busy:()=>busy,setBusy:value=>{busy=value;},
    api:async(url,method,body)=>{
      calls.push(body);let assistant={};
      if(body.action==='codex-models')assistant={models:[{id:'model',name:'Model',defaultModel:true,defaultEffort:'medium',efforts:[{reasoningEffort:'medium'},{reasoningEffort:'high'}]}]};
      if(body.action==='codex-account')assistant={authenticated:true,email:'fixture@example.com'};
      if(body.action==='codex-rate-limits'){
        if(failQuota)throw Error('unavailable');
        assistant={rateLimits:emptyQuota?[]:[{name:'Codex',windowDurationMins:300,usedPercent:25,resetsAt:1800000000},{name:'Codex',windowDurationMins:10080,usedPercent:100}]};
      }
      if(body.action==='codex-run')return {id:'job',state:'SUCCEEDED',events:[
        {assistant:{sequence:1,kind:'started',threadId:'thread'}},
        {assistant:{sequence:2,kind:'usage',usage:{totalTokens:100,inputTokens:70,outputTokens:30,cachedInputTokens:0,contextWindow:100,contextTokens:100}}}
      ],result:{assistant:{status:'completed',thread:{id:'thread',turns:[]}}}};
      return {id:'job',state:'SUCCEEDED',result:{assistant}};
    }
  });
  const $=id=>panel.querySelector(`[data-cx-id="${id}"]`);
  const change=(id,value)=>{$(id).value=value;$(id).dispatchEvent(new w.Event('change'));};
  await chat.load();
  assert.equal($('studio-codex-mode').value,'read-only');assert.equal($('cx-approval').value,'on-request');
  panel.querySelector('[data-cx=settings]').click();assert.equal($('cx-preferences').hidden,false);
  assert.deepEqual([...$('cx-limits').querySelectorAll('meter')].map(m=>m.value),[75,0]);
  change('studio-codex-mode','danger-full-access');change('cx-approval','never');change('cx-effort','high');
  panel.querySelector('[data-cx=settings-close]').click();assert.equal($('cx-preferences').hidden,true);
  $('studio-prompt').value='Inspect';$('studio-prompt-form').dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));await tick();
  const args=calls.find(call=>call.action==='codex-run').args;
  assert.equal(args.mode,'danger-full-access');assert.equal(args.approval,'never');assert.equal(args.effort,'high');
  assert.match($('cx-token-detail').textContent,/100/);assert.match($('cx-token-detail').textContent,/0/);assert.doesNotMatch($('cx-token-detail').textContent,/—/);
  assert.ok(calls.filter(call=>call.action==='codex-rate-limits').length>=2);
  chat.reset();await chat.load();assert.equal($('cx-approval').value,'never');assert.equal($('studio-codex-mode').value,'danger-full-access');assert.equal($('cx-effort').value,'high');
  project={deviceId:'two',root:'/srv'};chat.reset();assert.equal($('cx-approval').value,'on-request');assert.equal($('studio-codex-mode').value,'read-only');
  project={deviceId:'one',root:'/other'};chat.reset();assert.equal($('cx-approval').value,'on-request');
  failQuota=true;await chat.load(true);assert.equal(busy,false);assert.equal($('cx-limits').querySelectorAll('meter').length,0);
  failQuota=false;emptyQuota=true;await chat.load(true);assert.equal($('cx-limits').querySelectorAll('meter').length,0);
  assert.ok(!JSON.stringify({...w.sessionStorage}).includes('fixture@example.com'),'account data is not persisted');
  dom.window.close();
}
(async()=>{await verify('');await verify('device-');console.log('PASS IDE/device settings: permissions, payloads, scoped preferences, quota windows, zero tokens and unavailable fallback');})().catch(error=>{console.error(error);process.exitCode=1;});
