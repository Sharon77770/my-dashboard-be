const fs = require('node:fs');
const path = require('node:path');
const {Worker} = require('node:worker_threads');

/** DOM tests use actual bundled workers on Node threads; this is not browser QA. */
module.exports = function prepareMonaco(window, vendor) {
  const workers = new Set();
  window.TextDecoder=TextDecoder;window.TextEncoder=TextEncoder;
  window.matchMedia=()=>({matches:false,addEventListener(){},removeEventListener(){},addListener(){},removeListener(){}});
  window.ResizeObserver=class {observe(){} disconnect(){}};
  window.document.queryCommandSupported=()=>false;
  window.document.fonts={addEventListener(){},removeEventListener(){},ready:Promise.resolve()};
  // jsdom has no drawing surface. Geometry is intentionally synthetic; no visual assertions.
  window.HTMLCanvasElement.prototype.getContext=()=>({measureText:text=>({width:text.length*8}),fillRect(){},clearRect(){},beginPath(){},moveTo(){},lineTo(){},stroke(){},fill(){},rect(){},closePath(){},save(){},restore(){},setTransform(){},getImageData:()=>({data:[]})});
  window.Worker=class {
    constructor(url) {
      const name=new URL(url).pathname.split('/').pop();
      if(!/^studio-(editor|ts|json|html|css)\.worker\.js$/.test(name))throw new Error('Unexpected worker URL');
      const source=fs.readFileSync(path.join(vendor,name),'utf8');
      this.worker=new Worker(`const {parentPort}=require('node:worker_threads');
        globalThis.self=globalThis;globalThis.postMessage=value=>parentPort.postMessage(value);
        globalThis.addEventListener=(type,callback)=>{if(type==='message')parentPort.on('message',data=>callback({data}));};
        parentPort.on('message',data=>globalThis.onmessage?.({data}));
        ${source}`,{eval:true});
      workers.add(this.worker);
      this.worker.on('message',data=>this.onmessage?.({data}));
      this.worker.on('error',error=>{throw error;});
    }
    postMessage(data){this.worker.postMessage(data);}
    addEventListener(type,callback){this.listeners??=new Map();const listener=data=>callback({data});this.listeners.set(callback,listener);this.worker.on(type,listener);}
    removeEventListener(type,callback){const listener=this.listeners?.get(callback);if(listener)this.worker.off(type,listener);this.listeners?.delete(callback);}
    terminate(){workers.delete(this.worker);this.worker.terminate();}
  };
  return async()=>{await Promise.all([...workers].map(worker=>worker.terminate()));};
};
