'use strict';
window.StudioBrowser=(bench,host)=>{
  const tab=bench.page('Browser');
  const dialog=document.createElement('dialog');dialog.className='studio-browser-dialog';dialog.setAttribute('aria-label','브라우저 테스트');
  const heading=document.createElement('header');heading.className='studio-browser-dialog-head';
  heading.innerHTML='<strong>Browser Preview</strong><button type="button" data-browser-dismiss aria-label="미리보기 창 닫기">✕</button>';
  const page=document.createElement('div');page.className='studio-browser-dialog-body';dialog.append(heading,page);
  (tab.closest('#studio')||document.body).append(dialog);
  tab.innerHTML='<div class="studio-browser-launcher"><button type="button" data-browser-launch>브라우저 열기 ↗</button><span>큰 창에서 테스트합니다. 창을 닫아도 세션은 유지됩니다.</span></div>';
  let returnFocus;
  function present(){if(!dialog.open){returnFocus=document.activeElement;dialog.showModal();}page.querySelector('[name=url]').focus();}
  heading.querySelector('button').onclick=()=>dialog.close();
  dialog.addEventListener('close',()=>{if(returnFocus?.isConnected)returnFocus.focus();});
  tab.querySelector('[data-browser-launch]').onclick=present;
  page.innerHTML='<form class="studio-tool-actions"><button type="button" data-browser="back" aria-label="뒤로">←</button><button type="button" data-browser="forward" aria-label="앞으로">→</button><button type="button" data-browser="reload">Reload</button><input name="url" type="url" placeholder="http://127.0.0.1:3000" required aria-label="미리보기 URL"><button>열기</button><button type="button" data-browser="close">세션 종료</button></form><div class="studio-browser-status" role="status">기존 서버 Chromium에서 실행합니다.</div><img class="studio-browser-screen" alt="Chromium 미리보기" tabindex="0" hidden><details><summary>Console / Network</summary><pre data-browser-errors></pre></details>';
  const image=page.querySelector('img'),url=page.querySelector('[name=url]'),status=page.querySelector('[role=status]');let state=null,opened=false,busy=false,epoch=0;
  const textForm=document.createElement('form');textForm.className='studio-browser-text studio-tool-actions';
  textForm.innerHTML='<input name="text" maxlength="4000" aria-label="미리보기 텍스트 입력" placeholder="화면의 입력칸 선택 후 한글·텍스트 입력"><button type="submit">입력</button>';
  image.before(textForm);
  const guard=action=>Promise.resolve().then(action).catch(error=>{status.textContent=error.message;host.toast(error.message);});
  async function action(action,extra={}) {
    const project=host.project();if(!project)throw Error('프로젝트를 먼저 여세요.');
    if(action==='open'||action==='close'){epoch++;opened=false;}
    const version=epoch;const result=await host.api('/studio/browser','POST',{...project,action,...extra},{quiet:true});if(version!==epoch)return result;
    if(action==='close'){opened=false;state=null;image.hidden=true;image.removeAttribute('src');status.textContent='세션을 종료했습니다. URL을 열면 다시 시작합니다.';return result;}
    opened=true;state=result;status.textContent=result.title||result.url;image.src='data:image/jpeg;base64,'+result.image;image.hidden=false;
    if(document.activeElement!==url)url.value=result.url;
    page.querySelector('[data-browser-errors]').textContent=[...(result.consoleErrors||[]),...(result.networkFailures||[])].join('\n')||'수집된 오류가 없습니다.';return result;
  }
  async function open(value){bench.show('Browser');url.value=value;present();status.textContent='브라우저 연결 중…';return action('open',{url:value});}
  page.querySelector('form').onsubmit=event=>{event.preventDefault();guard(()=>open(url.value));};
  let composing=false;
  textForm.elements.text.addEventListener('compositionstart',()=>{composing=true;});
  textForm.elements.text.addEventListener('compositionend',()=>{composing=false;});
  textForm.onsubmit=event=>{event.preventDefault();const input=textForm.elements.text;if(composing||!input.value)return;const value=input.value;guard(async()=>{await action('text',{text:value});if(input.value===value)input.value='';});};
  textForm.elements.text.addEventListener('keydown',event=>{if(event.isComposing&&event.key==='Enter')event.preventDefault();});
  page.querySelectorAll('[data-browser]').forEach(button=>button.onclick=()=>guard(()=>action(button.dataset.browser)));
  image.onclick=event=>guard(()=>{const box=image.getBoundingClientRect();return action('click',{x:Math.round((event.clientX-box.left)*1200/box.width),y:Math.round((event.clientY-box.top)*720/box.height)});});
  image.onkeydown=event=>{if(event.ctrlKey||event.metaKey||event.altKey)return;if(event.key.length===1){event.preventDefault();guard(()=>action('text',{text:event.key}));}else if(['Enter','Tab','Backspace','Escape','ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(event.key)){event.preventDefault();guard(()=>action('key',{text:event.key}));}};
  image.onpaste=event=>{event.preventDefault();guard(()=>action('text',{text:event.clipboardData.getData('text').slice(0,4000)}));};
  image.onwheel=event=>{event.preventDefault();guard(()=>action('scroll',{delta:Math.max(-3000,Math.min(3000,Math.round(event.deltaY)))}));};
  const timer=setInterval(async()=>{if(!opened||busy||!dialog.open||page.offsetHeight===0)return;busy=true;try{await action('snapshot');}catch(error){status.textContent=error.message;}finally{busy=false;}},1500);
  window.addEventListener('beforeunload',()=>clearInterval(timer));
  return {open,present,projectChanged(){if(dialog.open)dialog.close();epoch++;opened=false;state=null;image.hidden=true;url.value='';page.querySelector('[data-browser-errors]').textContent='';status.textContent='프로젝트 URL을 여세요.';},context:()=>state?{url:state.url,title:state.title,text:state.text,consoleErrors:state.consoleErrors,networkFailures:state.networkFailures}:null};
};
