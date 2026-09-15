'use strict';
/** IDE pane presentation: desktop resizable columns, mobile exclusive screens. */
window.StudioPanels = {
  attach(root){const workbench=root.querySelector('.studio-workbench');let widths={explorer:200,inspector:320};
    try{const saved=JSON.parse(localStorage.getItem('workspace-studio-panes-v1'));for(const key of Object.keys(widths))if(Number.isFinite(saved?.[key]))widths[key]=Math.max(160,Math.min(480,saved[key]));}catch{}
    const apply=()=>{for(const [key,value] of Object.entries(widths)){workbench.style.setProperty('--'+key+'-width',value+'px');root.querySelector(`[data-resize="${key}"]`).setAttribute('aria-valuenow',value);}try{localStorage.setItem('workspace-studio-panes-v1',JSON.stringify(widths));}catch{}};
    apply();
    root.addEventListener('click',event=>{const button=event.target.closest('[data-pane]');if(!button)return;const pane=button.dataset.pane;workbench.dataset.mobilePane=pane;root.querySelectorAll('[data-pane]').forEach(item=>item.setAttribute('aria-pressed',String(item===button)));if(pane==='explorer')workbench.classList.toggle('explorer-collapsed');if(pane==='inspector')workbench.classList.toggle('inspector-collapsed');});
    for(const handle of root.querySelectorAll('[data-resize]')){
      let origin;
      handle.addEventListener('pointerdown',event=>{origin={x:event.clientX,width:widths[handle.dataset.resize]};handle.setPointerCapture(event.pointerId);event.preventDefault();});
      handle.addEventListener('pointermove',event=>{if(!origin)return;const key=handle.dataset.resize;const available=Math.max(160,Math.min(480,workbench.clientWidth-480));widths[key]=Math.max(160,Math.min(available,origin.width+(event.clientX-origin.x)*(key==='inspector'?-1:1)));apply();});
      const end=()=>{origin=null;};handle.addEventListener('pointerup',end);handle.addEventListener('pointercancel',end);
      handle.addEventListener('keydown',event=>{if(!['ArrowLeft','ArrowRight'].includes(event.key))return;event.preventDefault();const key=handle.dataset.resize;widths[key]=Math.max(160,Math.min(480,widths[key]+(event.key==='ArrowRight'?16:-16)*(key==='inspector'?-1:1)));apply();});
    }
  }
};
