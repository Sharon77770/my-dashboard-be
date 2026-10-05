'use strict';
/** Keyed DOM reconciliation for trusted application templates, preserving user-owned editor state. */
window.WorkspaceLiveDOM = (() => {
  const keyAttributes=['data-live-key','id','data-home-item','data-note-open','data-db-connection','data-repository','data-service-open','data-id','data-key','data-view','data-day'];
  function key(node){
    if(node.nodeType!==1)return null;
    for(const attribute of keyAttributes)if(node.hasAttribute(attribute))return node.tagName+':'+attribute+':'+node.getAttribute(attribute)+':'+(node.getAttribute('data-action')||node.getAttribute('data-telemetry')||'');
    return null;
  }
  function compatible(before,after){return before.nodeType===after.nodeType&&(before.nodeType!==1||before.tagName===after.tagName)&&key(before)===key(after);}
  function animate(element){
    if(element?.nodeType!==1||!element.animate||window.matchMedia?.('(prefers-reduced-motion: reduce)').matches)return;
    element.animate([{opacity:.55},{opacity:1}],{duration:180,easing:'ease-out'});
  }
  function reconcile(target,source){
    const existing=[...target.childNodes],used=new Set();let cursor=target.firstChild;
    for(const desired of source.childNodes){
      const identity=key(desired);
      let current=identity?existing.find(node=>!used.has(node)&&key(node)===identity):existing.find(node=>!used.has(node)&&!key(node)&&compatible(node,desired));
      if(!current){current=desired.cloneNode(true);target.insertBefore(current,cursor);animate(current);}
      else{
        used.add(current);if(current!==cursor)target.insertBefore(current,cursor);
        if(current.nodeType===3){if(current.nodeValue!==desired.nodeValue){current.nodeValue=desired.nodeValue;animate(current.parentElement);}}
        else if(current.nodeType===1&&!current.matches('form,input,textarea,select,[contenteditable],[data-live-preserve],canvas,iframe')){
          const open=current.tagName==='DETAILS'?current.open:null;
          for(const attribute of [...current.attributes])if(!desired.hasAttribute(attribute.name)&&attribute.name!=='inert')current.removeAttribute(attribute.name);
          for(const attribute of desired.attributes)if(current.getAttribute(attribute.name)!==attribute.value)current.setAttribute(attribute.name,attribute.value);
          if(open!==null)current.open=open;
          reconcile(current,desired);
        }
      }
      cursor=current.nextSibling;
    }
    for(const node of existing)if(!used.has(node)&&node.parentNode===target)node.remove();
  }
  function patch(target,html){
    if(!target)return;
    const template=document.createElement('template');template.innerHTML=html;
    const focus=target.contains(document.activeElement)?document.activeElement:null;
    const scroll=[target,...target.querySelectorAll('*')].filter(node=>node.scrollTop||node.scrollLeft).map(node=>[node,node.scrollTop,node.scrollLeft]);
    reconcile(target,template.content);
    if(focus?.isConnected&&document.activeElement!==focus)focus.focus({preventScroll:true});
    for(const [node,top,left] of scroll)if(node.isConnected){node.scrollTop=top;node.scrollLeft=left;}
  }
  return {patch};
})();
