'use strict';
/** IDE pane presentation: desktop resizable columns, mobile exclusive screens. */
window.StudioPanels = {
  attach(root){const workbench=root.querySelector('.studio-workbench');let widths={explorer:200,inspector:480};
    try{const saved=JSON.parse(localStorage.getItem('workspace-studio-panes-v1'));for(const key of Object.keys(widths))if(Number.isFinite(saved?.[key]))widths[key]=Math.max(160,Math.min(key==='inspector'?960:480,key==='inspector'&&saved[key]===320?480:saved[key]));}catch{}
    const apply=()=>{for(const [key,value] of Object.entries(widths)){workbench.style.setProperty('--'+key+'-width',value+'px');root.querySelector(`[data-resize="${key}"]`).setAttribute('aria-valuenow',value);root.querySelector(`[data-resize="${key}"]`).setAttribute('aria-valuemax',key==='inspector'?960:480);}try{localStorage.setItem('workspace-studio-panes-v1',JSON.stringify(widths));}catch{}};
    apply();
    root.addEventListener('click',event=>{const button=event.target.closest('[data-pane]');if(!button)return;const pane=button.dataset.pane;if(pane!=='inspector'){workbench.classList.remove('codex-focused');const focus=root.querySelector('[data-cx=focus]');focus?.setAttribute('aria-pressed','false');if(focus){focus.textContent='대화 확대';focus.setAttribute('aria-label','Codex 대화 확대');}}workbench.dataset.mobilePane=pane;root.querySelectorAll('[data-pane]').forEach(item=>item.setAttribute('aria-pressed',String(item===button)));if(pane==='explorer')workbench.classList.toggle('explorer-collapsed');if(pane==='inspector')workbench.classList.toggle('inspector-collapsed');});
    for(const handle of root.querySelectorAll('[data-resize]')){
      let origin;
      handle.addEventListener('pointerdown',event=>{origin={x:event.clientX,width:widths[handle.dataset.resize]};handle.setPointerCapture(event.pointerId);event.preventDefault();});
      handle.addEventListener('pointermove',event=>{if(!origin)return;const key=handle.dataset.resize;const available=Math.max(160,Math.min(key==='inspector'?960:480,workbench.clientWidth-(key==='inspector'?(workbench.classList.contains('explorer-collapsed')?0:widths.explorer):widths.inspector)-248));widths[key]=Math.max(160,Math.min(available,origin.width+(event.clientX-origin.x)*(key==='inspector'?-1:1)));apply();});
      const end=()=>{origin=null;};handle.addEventListener('pointerup',end);handle.addEventListener('pointercancel',end);
      handle.addEventListener('keydown',event=>{if(!['ArrowLeft','ArrowRight'].includes(event.key))return;event.preventDefault();const key=handle.dataset.resize;widths[key]=Math.max(160,Math.min(key==='inspector'?960:480,widths[key]+(event.key==='ArrowRight'?16:-16)*(key==='inspector'?-1:1)));apply();});
    }
  }
};
