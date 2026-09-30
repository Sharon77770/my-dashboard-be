'use strict';
(() => {
  const settings = document.querySelector('#assistant-settings');
  const dialog = document.querySelector('#assistant-memory-dialog');
  const runtime = window.WorkspaceAssistantRuntime;
  if (!settings || !dialog || !runtime) return;
  const section = document.createElement('section');
  const title = document.createElement('h3'); title.textContent = 'Memory';
  const description = document.createElement('p'); description.textContent = '대화 간 기억을 검색하고 직접 관리합니다.';
  const openButton = document.createElement('button'); openButton.type = 'button'; openButton.textContent = 'Memory 관리';
  section.append(title, description, openButton);
  settings.querySelector('.assistant-settings-content').insertBefore(section,
    settings.querySelector('.assistant-settings-content section:nth-child(2)'));
  const list = dialog.querySelector('#assistant-memory-list');
  const query = dialog.querySelector('#assistant-memory-query');
  const filter = dialog.querySelector('#assistant-memory-filter');
  function filterSelect(label, options) {
    const select = document.createElement('select'); select.setAttribute('aria-label', label);
    for (const [value, text] of options) {
      const option = document.createElement('option'); option.value = value; option.textContent = text;
      select.append(option);
    }
    filter.after(select); return select;
  }
  const typeFilter = filterSelect('기억 유형', [['', '모든 유형'], ['FACT', '사실'],
    ['POSSIBILITY', '가능성'], ['INTENTION', '계획'], ['FOLLOW_UP', '후속 작업'],
    ['DECISION', '결정'], ['PREFERENCE', '선호'], ['CONTEXT', '맥락']]);
  const confidenceFilter = filterSelect('확정도', [['', '모든 확정도'], ['CONFIRMED', '확정'],
    ['LIKELY', '가능성 높음'], ['TENTATIVE', '미확정']]);
  const scopeFilter = filterSelect('범위', [['', '모든 범위'], ['GLOBAL', '전체'],
    ['PERSONAL', '개인'], ['SERVICE', '서비스'], ['PROJECT', '프로젝트']]);
  const more = dialog.querySelector('#assistant-memory-more');
  const form = dialog.querySelector('#assistant-memory-form');
  const preferences = dialog.querySelector('#assistant-memory-preferences');
  const notice = dialog.querySelector('#assistant-memory-status');
  const relatedInput = form.elements.related;
  const serviceSelect = document.createElement('select');
  serviceSelect.name = 'relatedService'; serviceSelect.hidden = true;
  relatedInput.after(serviceSelect);
  let items = [];
  let selected = null;
  let offset = 0;
  let next = false;
  let timer;
  const path = '/assistant/memories';

  function status(message) { notice.textContent = message; }
  function action(label, type, id) {
    const button = document.createElement('button'); button.type = 'button'; button.textContent = label;
    button.dataset.memoryAction = type; button.dataset.memoryId = id; return button;
  }
  function render() {
    list.replaceChildren();
    if (!items.length) { const empty = document.createElement('p'); empty.textContent = '조건에 맞는 기억이 없습니다.'; list.append(empty); }
    for (const memory of items) {
      const row = document.createElement('article'); row.className = 'assistant-memory-row';
      const content = document.createElement('strong'); content.textContent = memory.content;
      const meta = document.createElement('small');
      meta.textContent = [memory.type, memory.confidence, memory.scope, memory.status,
        memory.relatedProject || memory.timeHint].filter(Boolean).join(' · ');
      const actions = document.createElement('div'); actions.className = 'assistant-memory-row-actions';
      actions.append(action('수정', 'edit', memory.id), action(memory.pinned ? '보호 해제' : '보호', 'pin', memory.id));
      if (memory.status === 'ARCHIVED') actions.append(action('복원', 'restore', memory.id));
      else if (memory.status !== 'PROMOTED') actions.append(action('보관', 'archive', memory.id));
      actions.append(action('삭제', 'delete', memory.id));
      row.append(content, meta, actions); list.append(row);
    }
    more.hidden = !next;
  }
  async function load(append = false) {
    const current = append ? offset : 0;
    const result = await runtime.api(path + '?' + new URLSearchParams({query:query.value,
      status:filter.value,type:typeFilter.value,confidence:confidenceFilter.value,
      scope:scopeFilter.value,offset:current,limit:25}));
    items = append ? items.concat(result.items) : result.items;
    offset = result.nextOffset; next = result.hasMore; render();
  }
  async function loadPreferences() {
    const value = await runtime.api(path + '/preferences');
    for (const name of ['autoArchive','autoDelete','protectManual']) preferences.elements[name].checked = value[name];
    for (const name of ['tentativeDays','possibilityDays','followUpDays','archivedDays']) preferences.elements[name].value = value[name];
    dialog.querySelector('#assistant-memory-cleanup').textContent = value.lastCleanupAt
      ? '최근 정리: ' + new Date(value.lastCleanupAt).toLocaleString() : '아직 정리 기록이 없습니다.';
  }
  function edit(memory) {
    selected = memory || null; form.reset();
    form.hidden = false; dialog.querySelector('#assistant-memory-form-title').textContent = memory ? '기억 수정' : '기억 추가';
    if (memory) {
      for (const name of ['content','type','confidence','scope','importance','timeHint','tags'])
        form.elements[name].value = memory[name] || '';
      form.elements.related.value = memory.relatedProject || '';
      serviceSelect.dataset.selected = memory.relatedServiceId || '';
      form.elements.pinned.checked = memory.pinned;
    }
    updateRelated();
    form.elements.content.focus();
  }
  async function updateRelated() {
    const scope = form.elements.scope.value;
    const usingService = scope === 'SERVICE';
    relatedInput.hidden = usingService;
    serviceSelect.hidden = !usingService;
    relatedInput.required = scope === 'PROJECT';
    serviceSelect.required = usingService;
    if (usingService) {
      try {
        const services = await runtime.api('/services');
        serviceSelect.replaceChildren();
        const empty = document.createElement('option'); empty.value = ''; empty.textContent = '서비스 선택'; serviceSelect.append(empty);
        for (const service of services) {
          const option = document.createElement('option'); option.value = service.id; option.textContent = service.name;
          serviceSelect.append(option);
        }
        serviceSelect.value = serviceSelect.dataset.selected || '';
      } catch (error) { status(error.message); }
    }
  }
  openButton.addEventListener('click', async () => {
    if (typeof settings.close === 'function') settings.close(); else settings.removeAttribute('open');
    if (typeof dialog.showModal === 'function') dialog.showModal(); else dialog.setAttribute('open','');
    try { await Promise.all([load(),loadPreferences()]); } catch (error) { status(error.message); }
  });
  dialog.querySelector('#assistant-memory-close').addEventListener('click', () => {
    if (typeof dialog.close === 'function') dialog.close(); else dialog.removeAttribute('open');
  });
  dialog.querySelector('#assistant-memory-new').addEventListener('click', () => edit(null));
  dialog.querySelector('#assistant-memory-form-cancel').addEventListener('click', () => { form.hidden = true; selected = null; });
  form.elements.scope.addEventListener('change', updateRelated);
  query.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(() => load().catch(error => status(error.message)), 250); });
  filter.addEventListener('change', () => load().catch(error => status(error.message)));
  for (const select of [typeFilter, confidenceFilter, scopeFilter])
    select.addEventListener('change', () => load().catch(error => status(error.message)));
  more.addEventListener('click', () => load(true).catch(error => status(error.message)));
  form.addEventListener('submit', async event => {
    event.preventDefault();
    const scope = form.elements.scope.value;
    const input = {
      content:form.elements.content.value,type:form.elements.type.value,
      confidence:form.elements.confidence.value,scope,importance:form.elements.importance.value,
      tags:form.elements.tags.value,timeHint:form.elements.timeHint.value,
      relatedServiceId:scope === 'SERVICE' ? serviceSelect.value : null,
      relatedProject:scope === 'PROJECT' ? form.elements.related.value : '',
      pinned:form.elements.pinned.checked
    };
    try {
      await runtime.api(path + (selected ? '/' + encodeURIComponent(selected.id) : ''), selected ? 'PUT' : 'POST', input);
      form.hidden = true; selected = null; status('기억을 저장했습니다.'); await load();
    } catch (error) { status(error.message); }
  });
  list.addEventListener('click', async event => {
    const button = event.target.closest('[data-memory-action]'); if (!button) return;
    const memory = items.find(item => item.id === button.dataset.memoryId); if (!memory) return;
    const actionName = button.dataset.memoryAction;
    if (actionName === 'edit') { edit(memory); return; }
    if (actionName === 'delete' && !window.confirm('이 기억을 삭제할까요? 대화 기록은 그대로 유지됩니다.')) return;
    try {
      const target = path + '/' + encodeURIComponent(memory.id);
      if (actionName === 'delete') await runtime.api(target,'DELETE');
      else if (actionName === 'pin') await runtime.api(target + '/pin',memory.pinned ? 'DELETE' : 'POST');
      else await runtime.api(target + '/' + actionName,'POST');
      status('기억을 변경했습니다.'); await load();
    } catch (error) { status(error.message); }
  });
  preferences.addEventListener('submit', async event => {
    event.preventDefault();
    const input = {};
    for (const name of ['autoArchive','autoDelete','protectManual']) input[name] = preferences.elements[name].checked;
    for (const name of ['tentativeDays','possibilityDays','followUpDays','archivedDays'])
      input[name] = Number(preferences.elements[name].value);
    try { await runtime.api(path + '/preferences','PUT',input); status('정리 설정을 저장했습니다.'); }
    catch (error) { status(error.message); }
  });
})();
