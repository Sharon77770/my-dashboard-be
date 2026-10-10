const {JSDOM}=require('jsdom');
const fs=require('node:fs'),assert=require('node:assert/strict');
const script=fs.readFileSync('src/main/resources/static/js/studio-codex.js','utf8');
const tick=()=>new Promise(resolve=>setTimeout(resolve,0));
async function verify(idPrefix){
  const dom=new JSDOM('<section id="chat"></section>',{url:'https://localhost',runScripts:'outside-only'});
  const w=dom.window,d=w.document,panel=d.querySelector('#chat');
  let busy=false,releasePrepare,releasePoll,releaseControl,posts=0,controls=0,poll=0,fail=false;
  const prepareGate=new Promise(resolve=>{releasePrepare=resolve;});
  const user={id:'user-1',type:'userMessage',text:'Inspect service\n[첨부: config.txt]'};
  const event=(sequence,item)=>({assistant:{sequence,kind:'item',item}});
  w.WorkspaceRealtime={waitForJob:()=>new Promise(resolve=>{releasePoll=resolve;})};
  w.eval(script);
  w.StudioCodex(panel,{idPrefix,escape:String,project:()=>({deviceId:'one',root:'/srv'}),auth(){},toast(){},publish(){},dirty:()=>false,busy:()=>busy,setBusy:value=>{busy=value;},prepare:()=>prepareGate,runtimeContext:()=>({browser:{title:'fixture'},api:{response:{status:500}}}),
    api:async(url,method,body)=>{
      if(url.endsWith('/inputs')){controls++;await new Promise(resolve=>{releaseControl=resolve;});return {};}
      if(method==='POST'){
        assert.equal(body.args.context.at(-1).name,'studio-runtime-observations.txt');assert.match(body.args.context.at(-1).content,/Untrusted tool output/);
        posts++;if(fail)throw Error('Connection failed');
        return {id:'job',state:'RUNNING',events:[event(1,user)]};
      }
      poll++;
      if(poll===1)return {id:'job',state:'RUNNING',events:[event(1,user),event(2,user),event(3,{id:'reply-1',type:'agentMessage',text:'Checking'})]};
      return {id:'job',state:'SUCCEEDED',result:{assistant:{thread:{id:'thread',turns:[
        {id:'turn-1',items:[user,user,{id:'reply-1',type:'agentMessage',text:'Done'}]},
        {id:'turn-2',items:[{...user,id:'user-2'},{id:'reply-2',type:'agentMessage',text:'Done again'}]}
      ]}}}};
    }
  });
  const prompt=panel.querySelector('[data-cx-id=studio-prompt]'),form=panel.querySelector('form[data-cx-id=studio-prompt-form]');
  const send=()=>form.dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));
  prompt.value='Inspect service';send();send();assert.equal(posts,0);
  releasePrepare();await tick();assert.equal(posts,1,'rapid submit during preparation creates one request');
  const rows=()=>panel.querySelectorAll('[data-kind=userMessage]');
  assert.equal(rows().length,1,'server echo replaces optimistic row even with attached context');
  const acknowledged=rows()[0];assert.match(acknowledged.textContent,/첨부: config.txt/);assert.doesNotMatch(acknowledged.textContent,/Untrusted tool output|studio-runtime-observations/);
  prompt.value='Additional instruction';send();send();await tick();assert.equal(controls,1,'steering is also single flight');releaseControl();await tick();
  releasePoll();await tick();assert.equal(rows().length,1,'replayed user items do not append');assert.equal(rows()[0],acknowledged);
  releasePoll();await tick();assert.equal(rows().length,2,'identical text intentionally sent in a later turn is preserved');assert.equal(busy,false);
  fail=true;prompt.value='Retryable request';send();await tick();assert.equal(rows().length,2,'unacknowledged failed optimistic row is removed');assert.equal(prompt.value,'Retryable request');
  send();await tick();assert.equal(rows().length,2,'retries do not accumulate phantom messages');
  dom.window.close();
}
(async()=>{await verify('');await verify('device-');console.log('PASS IDE/device Codex: optimistic acknowledgement, replay, intentional repeats, preparation/steering concurrency and failure retries');})().catch(error=>{console.error(error);process.exitCode=1;});
