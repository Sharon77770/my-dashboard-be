'use strict';
/** One read-only socket per page; REST remains the authorized source of resource data. */
window.WorkspaceRealtime = (() => {
  let socket, reconnectTimer, watchdog, attempt=0, stopped=false, epoch=null, revision=0, receivedAt=0;
  const listeners=new Map(), pending=new Set(), jobVersions=new Map(), seenJobs=new Map(), waiters=new Map();
  let drainTimer;
  function status(value){
    document.documentElement.dataset.realtime=value;
    const label=document.querySelector('#workspace-live');
    if(label){label.dataset.state=value;label.textContent=value==='connected'?'실시간 연결':value==='expired'?'로그인 확인 필요':'다시 연결 중';}
  }
  function invalidate(topics){
    topics.forEach(topic=>pending.add(topic));
    if(!pending.size)return;
    clearTimeout(drainTimer);
    drainTimer=setTimeout(()=>{
      if(document.hidden)return;
      const topics=[...pending];pending.clear();
      if(!topics.length)return;
      for(const topic of topics)for(const callback of listeners.get(topic)||[])callback();
      window.dispatchEvent(new CustomEvent('workspace:invalidate',{detail:{topics}}));
    },120);
  }
  function connected(){return socket?.readyState===1&&receivedAt>0;}
  function connect(){
    if(stopped||socket?.readyState===0||socket?.readyState===1||typeof WebSocket!=='function')return;
    clearTimeout(reconnectTimer);
    const url=new URL('ws/workspace',document.baseURI);url.protocol=location.protocol==='https:'?'wss:':'ws:';
    const current=new WebSocket(url);socket=current;receivedAt=0;status('connecting');
    current.onmessage=event=>{
      if(socket!==current)return;
      let frame;try{frame=JSON.parse(event.data);}catch{return;}
      if(!['ready','changed','heartbeat'].includes(frame.type)||!Number.isSafeInteger(frame.revision)||typeof frame.epoch!=='string')return;
      receivedAt=Date.now();attempt=0;status('connected');
      const reset=frame.type==='ready'||frame.epoch!==epoch||frame.revision>revision+1;
      if(epoch!==frame.epoch){revision=0;jobVersions.clear();seenJobs.clear();}
      epoch=frame.epoch;revision=Math.max(revision,frame.revision);
      if(reset)invalidate(['all']);
      if(Array.isArray(frame.topics))invalidate(frame.topics.filter(topic=>typeof topic==='string'));
      for(const id of (Array.isArray(frame.jobs)?frame.jobs:[]).filter(id=>typeof id==='string')){
        jobVersions.set(id,frame.revision);
        for(const resolve of waiters.get(id)||[])resolve();
      }
      while(jobVersions.size>64){const id=jobVersions.keys().next().value;jobVersions.delete(id);seenJobs.delete(id);}
      if(frame.type==='heartbeat')window.dispatchEvent(new CustomEvent('workspace:heartbeat'));
    };
    current.onerror=()=>current.close();
    current.onclose=event=>{
      if(socket!==current)return;socket=null;receivedAt=0;
      if(event.code===1008){stopped=true;status('expired');return;}
      status('reconnecting');
      if(!stopped)reconnectTimer=setTimeout(connect,Math.min(30000,500*2**Math.min(attempt++,6))+Math.random()*250);
    };
  }
  function waitForJob(id,fallback=500){
    const version=jobVersions.get(id)||0;
    if(version>(seenJobs.get(id)||0)){seenJobs.set(id,version);return Promise.resolve();}
    return new Promise(resolve=>{
      let timer;
      const finish=()=>{clearTimeout(timer);waiters.get(id)?.delete(finish);if(!waiters.get(id)?.size)waiters.delete(id);seenJobs.set(id,jobVersions.get(id)||0);resolve();};
      if(!waiters.has(id))waiters.set(id,new Set());waiters.get(id).add(finish);
      timer=setTimeout(finish,connected()?3000:fallback);
    });
  }
  function stop(){stopped=true;clearTimeout(reconnectTimer);clearInterval(watchdog);clearTimeout(drainTimer);socket?.close();for(const group of waiters.values())for(const finish of [...group])finish();}
  function start(){
    stopped=false;connect();clearInterval(watchdog);
    watchdog=setInterval(()=>{if(connected()&&Date.now()-receivedAt>45000)socket.close();},10000);
  }
  window.addEventListener('pagehide',stop);
  window.addEventListener('pageshow',event=>{if(event.persisted)start();});
  window.addEventListener('online',()=>{attempt=0;connect();});
  document.addEventListener('visibilitychange',()=>{if(!document.hidden){invalidate(['all']);connect();}});
  return {start,connected,waitForJob,on(topic,callback){if(!listeners.has(topic))listeners.set(topic,new Set());listeners.get(topic).add(callback);return()=>listeners.get(topic)?.delete(callback);}};
})();
