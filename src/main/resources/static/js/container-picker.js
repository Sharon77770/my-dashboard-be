'use strict';
/** Enhances marked selects without changing their form values or change contracts. */
(() => {
  const states = new WeakMap();
  let sequence = 0;
  function enhance(select) {
    if (states.has(select)) return;
    const wrapper = document.createElement('div');
    wrapper.className = 'container-picker';
    const input = document.createElement('input');
    input.type = 'text'; input.autocomplete = 'off';
    input.setAttribute('role', 'combobox');
    input.setAttribute('aria-autocomplete', 'list');
    input.setAttribute('aria-label', '컨테이너 검색 및 선택');
    const list = document.createElement('div');
    list.id = 'container-options-' + (++sequence);
    list.className = 'container-picker-options'; list.setAttribute('role', 'listbox');
    input.setAttribute('aria-controls', list.id);
    const status = document.createElement('span');
    status.className = 'container-picker-status'; status.setAttribute('role', 'status');
    select.before(wrapper); wrapper.append(input, list, status, select);
    select.hidden = true; select.tabIndex = -1;
    let matches = [], active = -1, expanded = false;
    const label = () => select.value ? select.selectedOptions[0]?.textContent || '' : '';
    function close() {
      expanded = false; list.hidden = true; input.setAttribute('aria-expanded', 'false');
      input.removeAttribute('aria-activedescendant'); input.value = label(); status.textContent = '';
    }
    function highlight(index) {
      active = index;
      [...list.children].forEach((row, i) => row.setAttribute('aria-selected', String(i === active)));
      const row = list.children[active];
      if (row) { input.setAttribute('aria-activedescendant', row.id); row.scrollIntoView?.({block:'nearest'}); }
      else input.removeAttribute('aria-activedescendant');
    }
    function choose(option) {
      if (!option || select.disabled) return;
      select.value = option.value;
      close();
      select.dispatchEvent(new Event('input', {bubbles:true}));
      select.dispatchEvent(new Event('change', {bubbles:true}));
    }
    function render(query = '') {
      if (select.disabled) return close();
      const tokens = query.trim().toLocaleLowerCase().split(/\s+/).filter(Boolean);
      matches = [...select.options].filter(option => option.value && !option.disabled && tokens.every(token => (option.textContent + ' ' + option.value + ' ' + (option.dataset.search || '')).toLocaleLowerCase().includes(token)));
      list.replaceChildren();
      matches.forEach((option, index) => {
        const row = document.createElement('div'); row.id = list.id + '-' + index;
        row.setAttribute('role', 'option'); row.textContent = option.textContent;
        row.title = option.textContent + ' · ' + option.value;
        row.addEventListener('mousedown', event => event.preventDefault());
        row.addEventListener('click', event => { event.preventDefault(); event.stopPropagation(); choose(option); }); list.append(row);
      });
      status.textContent = matches.length ? matches.length + '개 · ↑↓ 선택 · Enter 확인' : '검색 결과 없음';
      list.hidden = !matches.length; expanded = true; input.setAttribute('aria-expanded', 'true');
      highlight(matches.length ? 0 : -1);
    }
    function sync() {
      input.disabled = select.disabled;
      input.placeholder = select.disabled ? select.options[0]?.textContent || '목록 불러오는 중' : '이름, 이미지 또는 ID 검색';
      input.setAttribute('aria-required', String(select.required));
      if (expanded) render(input.value); else { input.value = label(); status.textContent = ''; }
    }
    states.set(select, {sync}); close(); sync();
    input.addEventListener('focus', () => { input.select(); render(); });
    input.addEventListener('click', () => { if (!expanded) { input.select(); render(); } });
    input.addEventListener('input', () => render(input.value));
    input.addEventListener('blur', close);
    input.addEventListener('keydown', event => {
      if (event.isComposing) return;
      if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close(); }
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault(); if (!expanded) render();
        else if (matches.length) highlight((active + (event.key === 'ArrowDown' ? 1 : -1) + matches.length) % matches.length);
      }
      if (event.key === 'Enter' && expanded) { event.preventDefault(); choose(matches[active]); }
    });
    select.addEventListener('change', sync);
    select.addEventListener('invalid', event => { event.preventDefault(); input.focus(); status.textContent = '목록에서 컨테이너를 선택하세요.'; });
    select.form?.addEventListener('reset', () => queueMicrotask(sync));
  }
  const selector = 'select[data-container-search]';
  const observer = new MutationObserver(records => {
    const changed = new Set();
    for (const record of records) {
      const select = record.target.closest?.(selector);
      if (select) changed.add(select);
      for (const node of record.addedNodes) {
        if (node.nodeType !== 1) continue;
        if (node.matches(selector)) enhance(node);
        node.querySelectorAll(selector).forEach(enhance);
      }
    }
    changed.forEach(select => states.get(select)?.sync());
  });
  document.querySelectorAll(selector).forEach(enhance);
  observer.observe(document.body, {subtree:true, childList:true, attributes:true, attributeFilter:['disabled','required','selected'], characterData:true});
})();
