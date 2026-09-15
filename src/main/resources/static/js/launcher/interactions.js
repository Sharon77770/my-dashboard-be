'use strict';
/** Pointer interaction is isolated from layout mutation. Touch long press opens actions outside edit mode. */
window.LauncherInteractions = {
  attach(host,options){let pending,holdTimer,swipeStart,suppressClick=false;
    const reset=()=>{clearTimeout(holdTimer);pending=null;host.classList.remove('is-dragging');document.querySelector('.launcher-drag-ghost')?.remove();document.querySelector('.drop-target')?.classList.remove('drop-target');};
    host.addEventListener('contextmenu',event=>{if(!event.target.closest('.launcher-grid,[data-home-item],[data-drawer-app]'))return;event.preventDefault();options.context(event.target.closest('[data-home-item],[data-drawer-app]'));});
    host.addEventListener('pointerdown',event=>{
      if(event.button!==0||event.target.closest('input,textarea,select'))return;
      const target=event.target.closest('[data-home-item],[data-drawer-app],[data-folder-app],[data-dock-app]');
      if(!target){if(event.target.closest('.launcher-grid')){swipeStart={x:event.clientX,y:event.clientY};if(event.pointerType==='touch')holdTimer=setTimeout(()=>{options.context(null);suppressClick=true;swipeStart=null;},550);}return;}
      const editable=options.editable();
      if(!editable&&target.closest('.launcher-grid'))swipeStart={x:event.clientX,y:event.clientY};
      pending={target,x:event.clientX,y:event.clientY,pointer:event.pointerId,dragging:false,resizing:Boolean(event.target.closest("[data-widget-resize]"))};
      if(event.pointerType==='touch'&&!editable)holdTimer=setTimeout(()=>{options.context(target);suppressClick=true;pending=null;},550);
    });
    host.addEventListener('pointermove',event=>{
      if(swipeStart&&Math.hypot(event.clientX-swipeStart.x,event.clientY-swipeStart.y)>8)clearTimeout(holdTimer);
      if(!pending||event.pointerId!==pending.pointer)return;
      const distance=Math.hypot(event.clientX-pending.x,event.clientY-pending.y);if(distance<8)return;
      clearTimeout(holdTimer);
      if(!options.editable()&&!pending.target.dataset.drawerApp){pending=null;return;}
      if(!pending.dragging){if(options.locked())return;pending.dragging=true;swipeStart=null;options.dragStart(pending.target);host.classList.add('is-dragging');const ghost=pending.target.cloneNode(true);ghost.className='launcher-drag-ghost';ghost.removeAttribute('id');ghost.querySelectorAll('[id]').forEach(node=>node.removeAttribute('id'));document.body.append(ghost);}
      event.preventDefault();options.dragMove?.(pending.target,event.clientX,event.clientY);const ghost=document.querySelector('.launcher-drag-ghost');if(ghost){ghost.style.left=event.clientX+12+'px';ghost.style.top=event.clientY+12+'px';}
      document.querySelector('.drop-target')?.classList.remove('drop-target');document.elementFromPoint(event.clientX,event.clientY)?.closest('[data-home-item],[data-page],[data-folder-app]')?.classList.add('drop-target');
    },{passive:false});
    document.addEventListener('pointerup',event=>{
      clearTimeout(holdTimer);
      if(pending?.dragging){suppressClick=true;setTimeout(()=>{suppressClick=false;},300);if(pending.resizing)options.resize(pending.target,event.clientX,event.clientY);else options.drop(pending.target,event.clientX,event.clientY);}
      else if(swipeStart){const dx=event.clientX-swipeStart.x,dy=event.clientY-swipeStart.y;if(Math.abs(dx)>70&&Math.abs(dx)>Math.abs(dy)*1.5)options.page(dx<0?1:-1);}
      swipeStart=null;reset();
    });
    document.addEventListener('pointercancel',()=>{swipeStart=null;reset();});
    host.addEventListener('click',event=>{if(suppressClick){event.preventDefault();event.stopImmediatePropagation();suppressClick=false;}},true);
    let wheelTime=0;
    host.addEventListener('wheel',event=>{if(!event.target.closest('.launcher-grid')||Math.abs(event.deltaX)<30||Math.abs(event.deltaX)<Math.abs(event.deltaY))return;event.preventDefault();if(Date.now()-wheelTime>400){options.page(event.deltaX>0?1:-1);wheelTime=Date.now();}},{passive:false});
  }
};
