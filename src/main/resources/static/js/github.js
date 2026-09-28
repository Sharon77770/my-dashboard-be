'use strict';
(() => {
  const root = document.querySelector('#github');
  const localDevice = window.workspaceInitial?.devices?.find(device => device.id === 'local');
  if (!root || !localDevice) return;
  const $ = selector => root.querySelector(selector);
  let ui;
  let repository = '', tab = 'pull-requests', jobId = null, busy = false;
  const setStatus = text => {$('#github-status').textContent = text;};
  const escape = value => window.WorkspaceUI.escape(value);
  const safeUrl = value => /^https:\/\/github\.com\//.test(value || '') ? value : null;
  async function load() {
    setStatus('GitHub 인증 확인 중…');
    const status = await ui.api('/github/status');
    $('#github-login').hidden = status.authenticated;
    if (!status.authenticated) {
      setStatus('서버 GitHub 로그인이 필요합니다.');
      $('#github-repositories').textContent = '로그인 후 저장소를 볼 수 있습니다.';
      $('#github-items').replaceChildren();
      return;
    }
    setStatus('서버 GitHub 계정에 연결됨');
    const repositories = await ui.api('/github/repositories');
    const list = $('#github-repositories');
    list.replaceChildren();
    if (!repositories.length) list.textContent = '표시할 저장소가 없습니다.';
    for (const item of repositories) {
      const button = document.createElement('button');
      button.className = 'github-row';
      button.dataset.repository = item.nameWithOwner;
      button.setAttribute('aria-current', String(item.nameWithOwner === repository));
      const title = document.createElement('b'); title.textContent = item.nameWithOwner;
      const detail = document.createElement('small'); detail.textContent = item.description || (item.isPrivate ? '비공개 저장소' : '공개 저장소');
      button.append(title, detail); list.append(button);
    }
    if (repository && repositories.some(item => item.nameWithOwner === repository)) await loadItems();
    else {repository = ''; $('#github-selected').textContent = '저장소를 선택하세요'; $('#github-items').replaceChildren();}
  }
  async function loadItems() {
    $('#github-selected').textContent = repository;
    const list = $('#github-items'); list.textContent = '불러오는 중…';
    const items = await ui.api('/github/' + tab + '?repository=' + encodeURIComponent(repository));
    list.replaceChildren();
    if (!items.length) list.textContent = tab === 'issues' ? '열린 이슈가 없습니다.' : '열린 PR이 없습니다.';
    for (const item of items) {
      const url = safeUrl(item.url);
      if (!url) continue;
      const link = document.createElement('a'); link.className = 'github-external'; link.href = url; link.target = '_blank'; link.rel = 'noopener noreferrer';
      const title = document.createElement('b'); title.textContent = '#' + item.number + ' ' + item.title;
      const detail = document.createElement('small'); detail.textContent = item.isDraft ? '초안' : item.state;
      link.append(title, detail); list.append(link);
    }
  }
  function showAuthEvents(events) {
    for (const event of events || []) {
      if (event.url === 'https://github.com/login/device') $('#github-auth-link').href = event.url;
      if (event.code) $('#github-auth-code').textContent = event.code;
    }
  }
  async function login() {
    if (busy) return;
    busy = true; $('#github-login').disabled = true; $('#github-cancel').hidden = false; $('#github-auth').hidden = false;
    $('#github-auth-code').textContent = '발급 대기 중'; setStatus('로그인 준비 중…');
    try {
      let job = await ui.api('/studio/jobs', 'POST', {deviceId:'local', root:localDevice.rootPath, action:'github-login', args:{}});
      jobId = job.id;
      while (job.state === 'RUNNING') {
        await new Promise(resolve => setTimeout(resolve, 700));
        job = await ui.api('/studio/jobs/' + encodeURIComponent(jobId));
        showAuthEvents(job.events);
      }
      showAuthEvents(job.events);
      if (job.state !== 'SUCCEEDED') throw new Error(job.error || 'GitHub 로그인이 취소되거나 실패했습니다.');
      $('#github-auth').hidden = true;
      await load();
    } catch (error) {setStatus(error.message);} finally {jobId = null; busy = false; $('#github-login').disabled = false; $('#github-cancel').hidden = true;}
  }
  root.addEventListener('click', async event => {
    try {
      if (event.target.closest('#github-login')) {await login(); return;}
      if (event.target.closest('#github-cancel')) {if (jobId) await ui.api('/studio/jobs/' + encodeURIComponent(jobId), 'DELETE'); return;}
      if (event.target.closest('#github-copy')) {await navigator.clipboard.writeText($('#github-auth-code').textContent); return;}
      if (event.target.closest('#github-refresh')) {await load(); return;}
      const selected = event.target.closest('[data-repository]');
      if (selected) {repository = selected.dataset.repository; root.querySelectorAll('[data-repository]').forEach(button => button.setAttribute('aria-current', String(button === selected))); await loadItems(); return;}
      const nextTab = event.target.closest('[data-github-tab]');
      if (nextTab) {tab = nextTab.dataset.githubTab; root.querySelectorAll('[data-github-tab]').forEach(button => button.setAttribute('aria-pressed', String(button === nextTab))); if (repository) await loadItems();}
    } catch (error) {setStatus(error.message);}
  });
  window.WorkspaceGithub = {init(runtime) {ui = runtime;}, open(id) {if (id === 'github') return load();}};
})();
