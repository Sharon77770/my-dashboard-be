'use strict';
/** Shared local zoom/pan for Guacamole screens; moving the view never sends remote input. */
window.WorkspaceRemoteViewport = (area, display, changed = () => {}) => {
  let fitted = true, scale = 1, moving = false, drag = null;
  const element = display.getElement(), listeners = [];
  area.classList.add('remote-viewport');
  area.dataset.viewportFit='true';area.dataset.viewportMoving='false';
  // Guacamole scales its inner layers; clip their unscaled overflow at its sized outer element.
  element.style.overflow = 'hidden';element.style.flexShrink = '0';
  const toolbar = document.createElement('div');toolbar.className = 'remote-viewport-controls';
  toolbar.setAttribute('role','group');toolbar.setAttribute('aria-label','원격 화면 크기와 이동');
  toolbar.innerHTML = '<button type="button" data-viewport="fit">화면 맞춤</button><button type="button" data-viewport="out" aria-label="원격 화면 축소">−</button><output aria-label="원격 화면 배율">100%</output><button type="button" data-viewport="in" aria-label="원격 화면 확대">＋</button><button type="button" data-viewport="original">100%</button><button type="button" data-viewport="move" aria-pressed="false">화면 이동</button>';
  area.before(toolbar);
  const listen = (target, type, handler, options) => {target.addEventListener(type,handler,options);listeners.push(()=>target.removeEventListener(type,handler,options));};
  const bounds = () => {
    const style = getComputedStyle(area);
    return {width:Math.max(0,area.clientWidth-(parseFloat(style.paddingLeft)||0)-(parseFloat(style.paddingRight)||0)),
      height:Math.max(0,area.clientHeight-(parseFloat(style.paddingTop)||0)-(parseFloat(style.paddingBottom)||0))};
  };
  const update = () => {
    const size = bounds();
    if (!size.width || !size.height || !display.getWidth() || !display.getHeight()) return;
    if (fitted) scale = Math.min((size.width-1)/display.getWidth(),(size.height-1)/display.getHeight(),1);
    scale = Math.max(.01,scale);display.scale(scale);
    area.dataset.viewportFit = String(fitted);
    if (fitted) {area.scrollLeft=0;area.scrollTop=0;}
    toolbar.querySelector('output').textContent = Math.round(scale*100)+'%';
    toolbar.querySelector('[data-viewport=fit]').setAttribute('aria-pressed',String(fitted));
  };
  const zoom = value => {
    const size=bounds(),previous=scale;
    const x=(area.scrollLeft+size.width/2)/previous,y=(area.scrollTop+size.height/2)/previous;
    fitted=false;scale=Math.max(.1,Math.min(3,value));update();
    area.scrollLeft=x*scale-size.width/2;area.scrollTop=y*scale-size.height/2;changed();
  };
  const fit = () => {fitted=true;update();changed();};
  toolbar.onclick = event => {
    const action=event.target.closest('[data-viewport]')?.dataset.viewport;
    if(action==='fit')fit();
    if(action==='in')zoom(scale*1.25);
    if(action==='out')zoom(scale/1.25);
    if(action==='original')zoom(1);
    if(action==='move') {
      moving=!moving;drag=null;area.dataset.viewportMoving=String(moving);
      event.target.setAttribute('aria-pressed',String(moving));
      event.target.textContent=moving?'원격 조작':'화면 이동';
    }
  };
  const block = event => {event.preventDefault();event.stopImmediatePropagation();};
  listen(area,'pointerdown',event=>{
    if(!moving)return;block(event);
    if(drag)return;
    drag={id:event.pointerId,x:event.clientX,y:event.clientY,left:area.scrollLeft,top:area.scrollTop};
    area.setPointerCapture?.(event.pointerId);
  },true);
  listen(area,'pointermove',event=>{
    if(!moving)return;block(event);
    if(drag?.id===event.pointerId){area.scrollLeft=drag.left+drag.x-event.clientX;area.scrollTop=drag.top+drag.y-event.clientY;}
  },true);
  for(const type of ['pointerup','pointercancel','lostpointercapture'])listen(area,type,event=>{
    if(!moving)return;block(event);
    if(drag?.id===event.pointerId){drag=null;if(area.hasPointerCapture?.(event.pointerId))area.releasePointerCapture(event.pointerId);}
  },true);
  // Guacamole listens to legacy mouse/touch events too; suppress them only in local move mode.
  for(const type of ['touchstart','touchmove','touchend','touchcancel','mousedown','mousemove','mouseup','click','contextmenu'])
    listen(area,type,event=>{if(moving)block(event);},{capture:true,passive:false});
  listen(area,'wheel',event=>{
    if(!moving)return;block(event);
    const unit=event.deltaMode===1?16:event.deltaMode===2?area.clientHeight:1;
    area.scrollLeft+=event.deltaX*unit;area.scrollTop+=event.deltaY*unit;
  },{capture:true,passive:false});
  const observer = new ResizeObserver(update);observer.observe(area);
  update();
  return {update,bounds,fit,original:()=>zoom(1),isFitted:()=>fitted,
    dispose(){observer.disconnect();listeners.forEach(remove=>remove());toolbar.remove();drag=null;area.classList.remove('remote-viewport');delete area.dataset.viewportFit;delete area.dataset.viewportMoving;}};
};
