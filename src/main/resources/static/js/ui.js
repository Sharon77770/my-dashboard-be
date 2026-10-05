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
    git:'M6 3v12a4 4 0 0 0 8 0V9m0 0 4-4m-4 4-4-4', codex:'m12 2 3 7 7 3-7 3-3 7-3-7-7-3 7-3Z', clip:'M8 12v5a4 4 0 0 0 8 0V6a3 3 0 0 0-6 0v11', more:'M5 12h.01M12 12h.01M19 12h.01',
    back:'m15 18-6-6 6-6', refresh:'M20 7v5h-5M4 17v-5h5M5.5 9a7 7 0 0 1 12-2l2.5 5M4 12l2.5 5a7 7 0 0 0 12-2',
    edit:'M4 20h4l11-11-4-4L4 16v4Zm9-13 4 4', menu:'M4 6h16M4 12h16M4 18h16', maximize:'M4 9V4h5m6 0h5v5m0 6v5h-5m-6 0H4v-5',
    chart:'M4 19V9m5 10V5m5 14v-7m5 7V3', activity:'M3 12h4l3-7 4 14 3-7h4', server:'M4 4h16v7H4zM4 15h16v5H4zM7 7h.01M7 18h.01',
    cpu:'M7 7h10v10H7zM9 2v5m6-5v5M9 17v5m6-5v5M2 9h5m-5 6h5m10-6h5m-5 6h5', memory:'M4 7h16v10H4zM8 7v10m4-10v10m4-10v10M7 20h10', disk:'M4 5h16v14H4zM7 15h10M7 9h.01M11 9h.01',
    issue:'M12 8v5m0 4h.01M4 4h16v16H4z', pull:'M7 4v12a3 3 0 0 0 6 0V9h4m-4 0 3-3m-3 3 3 3M7 4a2 2 0 1 0 0 4 2 2 0 0 0 0-4Zm10 12a2 2 0 1 0 0 4 2 2 0 0 0 0-4Z',
    warning:'M12 3 2 21h20L12 3Zm0 7v4m0 3h.01', check:'m4 12 5 5L20 6', wifi:'M2 9a16 16 0 0 1 20 0M5 12a11 11 0 0 1 14 0m-11 3a6 6 0 0 1 8 0m-4 4h.01',
    up:'M12 19V5m-6 6 6-6 6 6', play:'M8 5v14l11-7z', stop:'M6 6h12v12H6z',
    file:'M6 3h8l4 4v14H6zM14 3v5h4M9 12h6m-6 4h6', star:'m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-2.9-5.6 2.9 1.1-6.2L3 9.6l6.2-.9L12 3Z',
    upload:'M12 16V3m-5 5 5-5 5 5M4 16v5h16v-5',
    uploadFolder:'M3 8V5h7l2 3h9v12H3zM12 18v-7m-3 3 3-3 3 3',
    folderPlus:'M3 8V5h7l2 3h9v12H3zM12 12v6m-3-3h6',
    filePlus:'M6 3h8l4 4v14H6zM14 3v5h4M12 12v6m-3-3h6',
    select:'M4 4h16v16H4zM8 12l3 3 5-6',
    download:'M12 3v13m-5-5 5 5 5-5M4 18v3h16v-3',
    trash:'M4 7h16M9 7V4h6v3m-9 0 1 14h10l1-14M10 11v6m4-6v6',
    restore:'M4 11V5m0 6h6M4 11a8 8 0 1 1 2 8',
    info:'M12 10v7m0-10h.01M3 12a9 9 0 1 0 18 0 9 9 0 0 0-18 0Z',
    save:'M4 4h14l2 2v14H4zM8 4v6h8V4M8 20v-7h8v7',
    workspace:'M12 2 22 12 12 22 2 12zM12 7l5 5-5 5-5-5z',
    external:'M13 4h7v7M20 4l-9 9M20 14v6H4V4h6',
    chevron:'m6 9 6 6 6-6', arrowRight:'M4 12h16m-6-6 6 6-6 6'
  };
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const icon = name => `<svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${paths[name] || paths.apps}"/></svg>`;
  const token = name => getComputedStyle(document.documentElement).getPropertyValue(`--${name}`).trim();
  const terminalTheme = () => ({background:token('bg-app'),foreground:token('text-primary'),cursor:token('accent'),selectionBackground:token('accent-subtle'),black:token('bg-sidebar'),red:token('danger'),green:token('success'),yellow:token('warning'),blue:token('accent'),magenta:token('studio-keyword'),cyan:token('studio-function'),white:token('text-primary'),brightBlack:token('text-muted'),brightRed:token('danger'),brightGreen:token('success'),brightYellow:token('warning'),brightBlue:token('accent'),brightMagenta:token('studio-keyword'),brightCyan:token('studio-function'),brightWhite:token('text-primary')});
  const progress = (value, label, tone = 'accent') => {
    const percent = value != null && value !== '' && Number.isFinite(Number(value)) ? Math.max(0, Math.min(100, Number(value))) : null;
    if (percent == null) return `<span class="ui-progress ui-progress-unknown" aria-label="${escape(label)} 미확인"><span></span></span>`;
    return `<span class="ui-progress" data-tone="${escape(tone)}" role="progressbar" aria-label="${escape(label)}" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${percent}" style="--progress:${percent}%"><span></span></span>`;
  };
  const ring = (value, label, tone = 'accent') => {
    const percent = value != null && value !== '' && Number.isFinite(Number(value)) ? Math.max(0, Math.min(100, Number(value))) : 0;
    return `<span class="ui-ring" data-tone="${escape(tone)}" role="img" aria-label="${escape(label)} ${Math.round(percent)}%" style="--progress:${percent}%"><span aria-hidden="true">${Math.round(percent)}%</span></span>`;
  };
  const emptyState = (title, detail = '', symbol = 'apps') => `<div class="ui-empty">${icon(symbol)}<strong>${escape(title)}</strong>${detail ? `<small>${escape(detail)}</small>` : ''}</div>`;
  const skeleton = (count = 2) => Array.from({length:Math.max(1,Math.min(5,count))}, () => '<div class="ui-skeleton-row"><span class="ui-skeleton"></span><span class="ui-skeleton"></span></div>').join('');
  const pendingTasks = new Map();
  const inertElements = new Map();
  let activityHideTimer;
  let activityFocus;
  let activityObserver;
  let activityLocked = false;

  function lockBackground(indicator) {
    if (typeof document === 'undefined' || !document.body) return;
    for (const element of document.body.children) {
      if (element === indicator || inertElements.has(element)) continue;
      inertElements.set(element, element.hasAttribute('inert'));
      element.setAttribute('inert', '');
    }
  }

  // Capture at window level so app shortcuts and dialogs cannot bypass the loading lock.
  function blockLoadingInput(event) {
    if (!activityLocked) return;
    event.preventDefault();
    event.stopImmediatePropagation();
  }
  for (const type of ['click', 'dblclick', 'contextmenu', 'pointerdown', 'pointermove', 'pointerup', 'keydown', 'keyup', 'beforeinput', 'submit', 'dragstart', 'drop', 'wheel', 'touchmove']) {
    window.addEventListener(type, blockLoadingInput, {capture:true, passive:false});
  }

  /** Locks immediately and releases only after all foreground requests and their render continuations settle. */
  function beginTask(message = '불러오는 중…') {
    const task = Symbol('loading');
    pendingTasks.set(task, message);
    let indicator = document.querySelector('#workspace-activity');
    if (!indicator) {
      indicator = document.createElement('dialog');
      indicator.id = 'workspace-activity';
      indicator.className = 'workspace-activity';
      indicator.setAttribute('aria-modal', 'true');
      indicator.setAttribute('aria-labelledby', 'workspace-loading-title');
      indicator.setAttribute('aria-describedby', 'workspace-loading-detail');
      indicator.tabIndex = -1;
      indicator.innerHTML = '<div class="workspace-loading-content" role="status" aria-live="polite" aria-atomic="true"><span class="workspace-loading-spinner" aria-hidden="true"></span><strong id="workspace-loading-title" class="workspace-activity-message"></strong><span id="workspace-loading-detail">잠시만 기다려 주세요.</span></div>';
      indicator.addEventListener('cancel', event => event.preventDefault());
      document.body.append(indicator);
    }
    indicator.querySelector('.workspace-activity-message').textContent = message;
    clearTimeout(activityHideTimer);
    if (!activityLocked) {
      activityFocus = document.activeElement;
      activityLocked = true;
      document.documentElement.dataset.loading = 'true';
      lockBackground(indicator);
      activityObserver = new MutationObserver(() => lockBackground(indicator));
      activityObserver.observe(document.body, {childList:true});
      if (typeof indicator.showModal === 'function') indicator.showModal();
      else indicator.setAttribute('open', '');
      indicator.focus({preventScroll:true});
    }
    let ended = false;
    return () => {
      if (ended) return;
      ended = true;
      pendingTasks.delete(task);
      if (pendingTasks.size) {
        indicator.querySelector('.workspace-activity-message').textContent = [...pendingTasks.values()].at(-1);
      } else {
        // The API's awaiting caller renders in a microtask before this unlock.
        activityHideTimer = setTimeout(() => {
          if (pendingTasks.size) return;
          activityObserver.disconnect();
          for (const [element, wasInert] of inertElements) {
            if (!wasInert) element.removeAttribute('inert');
          }
          inertElements.clear();
          activityLocked = false;
          delete document.documentElement.dataset.loading;
          if (typeof indicator.close === 'function') indicator.close();
          else indicator.removeAttribute('open');
          if (activityFocus?.isConnected && !activityFocus.closest('[inert]') && !activityFocus.disabled) activityFocus.focus({preventScroll:true});
          activityFocus = null;
        }, 0);
      }
    };
  }
  function hydrateIcons() {
    const targets = [
      ['.activity-rail [data-view=home]','home'],['.activity-rail [data-action=palette]','search'],
      ['.activity-rail [data-view=services]','server'],['.activity-rail [data-view=studio]','studio'],
      ['.activity-rail [data-view=devices]','devices'],['.activity-rail [data-view=databases]','disk'],
      ['.activity-rail [data-launcher=drawer]','apps'],['.activity-rail [data-action=app-switcher]','recent'],
      ['.workspace-mark','workspace'],['.mobile-search-button','search'],
      ['.workspace-search>span:first-child','search'],['.home-command>span:first-child','search'],
      ['#home .launcher-head .ui-menu>summary','more'],['.launcher-dock .launcher-shortcut .launcher-icon','apps'],
      ['.os-navigation [data-view=home]>span:first-child','home'],
      ['.os-navigation [data-action=palette]>span:first-child','search'],
      ['.os-navigation [data-launcher=drawer]>span:first-child','apps'],
      ['.os-navigation [data-action=app-switcher]>span:first-child','recent'],
      ['.workspace-back','back'],['.workspace-bar .ui-menu>summary','more'],['#assistant-sidebar-open','menu'],
      ['#assistant-sidebar-close','close'],['#assistant-header-new','plus'],['#assistant-header-settings','settings'],
      ['#assistant-history-refresh','refresh'],['#assistant-limits-refresh','refresh'],
      ['#assistant-attach','plus'],['#assistant-send','up'],['#assistant-stop','stop'],
      ['#assistant-settings-close','close'],['#github-refresh','refresh']
    ];
    for (const [selector, name] of targets) {
      const target = document.querySelector(selector);
      if (target) {
        if (target.matches('button,summary')) {
          const label=target.getAttribute('aria-label')||target.textContent.trim();
          if (label) {target.setAttribute('aria-label',label);target.dataset.tooltip=label;}
        }
        target.innerHTML = icon(name);
      }
    }
    const actions = [
      ['#devices [data-action=refresh-status]','refresh'],['#devices [data-action=device-add]','plus'],
      ['#devices [data-action=device-manual]','more'],['#terminal .page-head [data-open=TERMINAL]','terminal'],
      ['#apps [data-action=tailscale-settings]','wifi'],['#apps [data-action=browser-settings]','browser'],
      ['#apps [data-action=app-add]','plus'],['#github-login','external'],['#github-cancel','close'],
      ['#services .page-head [data-services=create]','plus'],
      ['#telemetry .page-head [data-telemetry=create]','plus'],['#clipboard .page-head [data-action=clip-add]','plus']
    ];
    for (const [selector, name] of actions) {
      const target=document.querySelector(selector);
      if (!target) continue;
      const label=target.getAttribute('aria-label')||target.textContent.trim();
      target.setAttribute('aria-label',label);
      target.title=label;
      target.innerHTML=icon(name)+`<span class="ui-action-label">${escape(label)}</span>`;
    }
    const edit = document.querySelector('#home-edit');
    if (edit) {edit.innerHTML = `${icon('edit')}<span>홈 편집</span>`; edit.dataset.tooltip = '홈 편집';}
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', hydrateIcons, {once:true});
  else hydrateIcons();
  document.addEventListener('click', event => document.querySelectorAll('.ui-menu[open]').forEach(menu => {if (!menu.contains(event.target)) menu.open=false;}));
  document.addEventListener('keydown', event => {if(event.key==='Escape')document.querySelectorAll('.ui-menu[open]').forEach(menu=>{menu.open=false;menu.querySelector('summary').focus();});});
  let tooltip;
  function hideTooltip(){tooltip?.remove();tooltip=null;}
  function showTooltip(event){const target=event.target.closest('[data-tooltip]');if(!target)return;hideTooltip();tooltip=document.createElement('div');tooltip.className='ui-tooltip';tooltip.role='tooltip';tooltip.textContent=target.dataset.tooltip;document.body.append(tooltip);const box=target.getBoundingClientRect();tooltip.style.left=Math.max(8,Math.min(box.left,innerWidth-tooltip.offsetWidth-8))+'px';tooltip.style.top=Math.min(box.bottom+6,innerHeight-tooltip.offsetHeight-8)+'px';}
  document.addEventListener('pointerover',showTooltip);document.addEventListener('focusin',showTooltip);document.addEventListener('pointerout',hideTooltip);document.addEventListener('focusout',hideTooltip);document.addEventListener('pointerdown',hideTooltip);
  function uuid(){if(typeof crypto.randomUUID==='function')return crypto.randomUUID();const bytes=crypto.getRandomValues(new Uint8Array(16));bytes[6]=(bytes[6]&15)|64;bytes[8]=(bytes[8]&63)|128;const hex=Array.from(bytes,value=>value.toString(16).padStart(2,'0')).join('');return hex.slice(0,8)+'-'+hex.slice(8,12)+'-'+hex.slice(12,16)+'-'+hex.slice(16,20)+'-'+hex.slice(20);}
  return {escape,icon,token,terminalTheme,progress,ring,emptyState,skeleton,beginTask,uuid};
})();
