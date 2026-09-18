'use strict';
/** Shared presentation primitives. No application requests or credentials live here. */
window.WorkspaceUI = (() => {
  const paths = {
    home:'m3 10 9-7 9 7M5 9v12h5v-7h4v7h5V9', devices:'M3 4h18v12H3zM8 21h8m-4-5v5', files:'M3 5h7l2 3h9v12H3z',
    terminal:'m5 6 5 6-5 6m8 0h6', remote:'M3 3h18v14H3zM8 21h8m-4-4v4m2-12 3 3-3 3m-4-6-3 3 3 3', apps:'M3 3h7v7H3zm11 0h7v7h-7zM3 14h7v7H3zm11 0h7v7h-7z',
    calendar:'M3 5h18v16H3zM7 2v6m10-6v6M3 10h18m-14 4h3m4 0h3m-10 4h3', timetable:'M3 4h18v17H3zM3 9h18M8 9v12m7-12v12M3 15h18',
    studio:'m8 5-6 7 6 7m8-14 6 7-6 7m-3-16-2 18',
    notes:'M5 3h10l4 4v14H5zM15 3v5h4M8 12h8M8 16h6',
    settings:'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8ZM12 2v3m0 14v3M2 12h3m14 0h3M5 5l2 2m10 10 2 2M5 19l2-2M17 7l2-2',
    browser:'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM3 12h18M12 3c5 5 5 13 0 18-5-5-5-13 0-18Z', recent:'M4 7V2m0 5h5M4 7a9 9 0 1 1-1 9m9-10v6l4 2',
    search:'M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0Zm-2 5 6 6', folder:'M3 5h7l2 3h9v12H3z', plus:'M12 4v16M4 12h16', close:'m5 5 14 14M5 19 19 5',
    git:'M6 3v12a4 4 0 0 0 8 0V9m0 0 4-4m-4 4-4-4', codex:'m12 2 3 7 7 3-7 3-3 7-3-7-7-3 7-3Z', clip:'M8 12v5a4 4 0 0 0 8 0V6a3 3 0 0 0-6 0v11', more:'M5 12h.01M12 12h.01M19 12h.01'
  };
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const icon = name => `<svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${paths[name] || paths.apps}"/></svg>`;
  const token = name => getComputedStyle(document.documentElement).getPropertyValue(`--${name}`).trim();
  const terminalTheme = () => ({background:token('bg-app'),foreground:token('text-primary'),cursor:token('accent'),selectionBackground:token('accent-subtle'),black:token('bg-sidebar'),red:token('danger'),green:token('success'),yellow:token('warning'),blue:token('accent'),magenta:token('studio-keyword'),cyan:token('studio-function'),white:token('text-primary'),brightBlack:token('text-muted'),brightRed:token('danger'),brightGreen:token('success'),brightYellow:token('warning'),brightBlue:token('accent'),brightMagenta:token('studio-keyword'),brightCyan:token('studio-function'),brightWhite:token('text-primary')});
  document.addEventListener('click', event => document.querySelectorAll('.ui-menu[open]').forEach(menu => {if (!menu.contains(event.target)) menu.open=false;}));
  document.addEventListener('keydown', event => {if(event.key==='Escape')document.querySelectorAll('.ui-menu[open]').forEach(menu=>{menu.open=false;menu.querySelector('summary').focus();});});
  let tooltip;
  function hideTooltip(){tooltip?.remove();tooltip=null;}
  function showTooltip(event){const target=event.target.closest('[data-tooltip]');if(!target)return;hideTooltip();tooltip=document.createElement('div');tooltip.className='ui-tooltip';tooltip.role='tooltip';tooltip.textContent=target.dataset.tooltip;document.body.append(tooltip);const box=target.getBoundingClientRect();tooltip.style.left=Math.max(8,Math.min(box.left,innerWidth-tooltip.offsetWidth-8))+'px';tooltip.style.top=Math.min(box.bottom+6,innerHeight-tooltip.offsetHeight-8)+'px';}
  document.addEventListener('pointerover',showTooltip);document.addEventListener('focusin',showTooltip);document.addEventListener('pointerout',hideTooltip);document.addEventListener('focusout',hideTooltip);document.addEventListener('pointerdown',hideTooltip);
  function uuid(){if(typeof crypto.randomUUID==='function')return crypto.randomUUID();const bytes=crypto.getRandomValues(new Uint8Array(16));bytes[6]=(bytes[6]&15)|64;bytes[8]=(bytes[8]&63)|128;const hex=Array.from(bytes,value=>value.toString(16).padStart(2,'0')).join('');return hex.slice(0,8)+'-'+hex.slice(8,12)+'-'+hex.slice(12,16)+'-'+hex.slice(16,20)+'-'+hex.slice(20);}
  return {escape,icon,token,terminalTheme,uuid};
})();
