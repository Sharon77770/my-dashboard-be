const {JSDOM}=require('jsdom'),fs=require('node:fs'),assert=require('node:assert/strict');
const dom=new JSDOM('<body><div id="communications"></div></body>',{runScripts:'outside-only',url:'http://localhost'});
const w=dom.window,d=w.document,profiles=[],calls=[],opened=[];
w.eval(fs.readFileSync('src/main/resources/static/js/ui.js','utf8'));
w.eval(fs.readFileSync('src/main/resources/static/js/communications.js','utf8'));
const api=async(path,method='GET',body)=>{
 calls.push({path,method,body});
 if(path==='/communications/bridge/profiles'){
  if(method==='POST'){const item={id:'profile-'+profiles.length,...body};profiles.push(item);return item;}
  return profiles;
 }
 if(path.endsWith('/sessions'))return {id:'session'};
 if(['/communications/accounts','/communications/providers','/communications/actions'].includes(path))return [];
 throw Error(path);
};
w.WorkspaceAuthenticationBrowser={open:options=>{opened.push(options);}};
w.WorkspaceCommunications.init({api,escape:w.WorkspaceUI.escape,toast:message=>{throw Error(message);}});
const tick=()=>new Promise(resolve=>setTimeout(resolve,20));
(async()=>{
 await w.WorkspaceCommunications.open('communications');
 for(const provider of ['KAKAOTALK','SLACK','DISCORD']){
  d.querySelector(`[data-comm-remote="${provider}"]`).click();await tick();
  assert.equal(profiles.at(-1).provider,provider);
  await opened.at(-1).sessionFactory();
 }
 assert.equal(profiles.length,3);
 d.querySelector('[data-comm-remote="SLACK"]').click();await tick();
 assert.equal(profiles.length,3);assert.equal(opened.length,4);
 assert.equal(calls.filter(call=>call.path.includes('/oauth')||call.path==='/communications/accounts'&&call.method==='POST').length,0);
 console.log('PASS remote messenger launch/reuse: Kakao Wine, Slack and Discord; no OAuth or Bot token required.');
 dom.window.close();
})().catch(error=>{console.error(error);dom.window.close();process.exitCode=1;});
