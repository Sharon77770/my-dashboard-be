'use strict';
(() => {
  const root = document.querySelector('#github');
  const localDevice = window.workspaceInitial?.devices?.find(device => device.id === 'local');
  if (!root || !localDevice) return;
  const $ = selector => root.querySelector(selector);
  const safeUrl = value => /^https:\/\/github\.com\//.test(value || '') ? value : null;
  const option = (value, label) => {const item = document.createElement('option'); item.value = value; item.textContent = label; return item;};
  const line = (title, detail, href) => {
    const element = document.createElement(href ? 'a' : 'div');
    element.className = 'github-external';
    if (href && safeUrl(href)) {element.href = href; element.target = '_blank'; element.rel = 'noopener noreferrer';}
    const name = document.createElement('b'); name.textContent = title;
    const meta = document.createElement('small'); meta.textContent = detail || '';
    element.append(name, meta); return element;
  };
  let ui, owner = '', repository = '', tab = 'overview', jobId = null, busy = false, fileRef = '', pendingRepository = '';
  let workState = 'open', issueRole = 'all', issueLabel = '';
  let failedRunsOnly = false;
  let owners = [], repositories = [], viewToken = 0;
  let approvalSignature = null;
  const setStatus = (message, tone = 'info') => {$('#github-status').textContent = message; $('#github-status').dataset.state = tone;};
  $('#github-status').classList.add('ui-status');
  const layout = $('.github-layout');
  layout.innerHTML = '<section class="panel github-scope"><label>Scope <select id="github-owner"></select></label><nav id="github-nav" aria-label="GitHub 탐색"></nav><label>저장소 검색 <input id="github-search" type="search" placeholder="이름 또는 설명"></label><label>필터 <select id="github-filter"><option value="all">전체</option><option value="public">Public</option><option value="private">Private</option><option value="archived">Archived</option><option value="fork">Fork</option></select></label><label>정렬 <select id="github-sort"><option value="updated">최근 업데이트</option><option value="name">이름</option></select></label><div id="github-repositories"></div></section><section class="panel github-content"><div class="github-content-head"><h2 id="github-selected">Owner를 선택하세요</h2><span id="github-context"></span></div><div id="github-items"></div><div id="github-detail" hidden></div></section>';
  $('.github-content-head').insertAdjacentHTML('afterbegin',`<button type="button" class="github-scope-trigger" data-drawer-target=".github-scope" data-drawer-title="GitHub 탐색" aria-label="GitHub 탐색 열기" aria-haspopup="dialog" aria-expanded="false" title="GitHub 탐색 열기">${window.WorkspaceUI.icon('menu')}</button>`);
  const actionBar = document.createElement('div'); actionBar.id = 'github-actions';
  const formHost = document.createElement('div'); formHost.id = 'github-form'; formHost.hidden = true;
  $('#github-items').before(actionBar, formHost);
  const approvalPanel = document.createElement('section'); approvalPanel.className = 'panel github-approvals';
  approvalPanel.innerHTML = '<h2>GitHub 승인 대기</h2><div id="github-approvals"></div>';
  approvalPanel.hidden = true;
  layout.after(approvalPanel);
  const tabs = [['overview','Overview'],['repositories','Repositories'],['issues','Issues'],['pull-requests','Pull Requests'],['actions','Actions'],['releases','Releases'],['files','Files'],['commits','Commits'],['members','Members']];
  const tabIcons = {overview:'activity',repositories:'folder',issues:'issue','pull-requests':'pull',actions:'refresh',releases:'check',files:'files',commits:'git',members:'devices'};
  function renderNav() {
    const nav = $('#github-nav'); nav.replaceChildren();
    for (const [id, label] of tabs) {
      if ((id === 'members' && !owners.some(item => item.login === owner && item.type === 'ORGANIZATION')) ||
          (['actions','releases','files','commits'].includes(id) && !repository)) continue;
      const button = document.createElement('button'); button.type = 'button'; button.dataset.tab = id;
      button.innerHTML = window.WorkspaceUI.icon(tabIcons[id]) + '<span>' + label + '</span>';
      button.dataset.tooltip = label; button.setAttribute('aria-current', String(tab === id)); nav.append(button);
    }
  }
  function renderActions() {
    actionBar.replaceChildren(); formHost.hidden = true; formHost.replaceChildren();
    if (tab === 'actions') {
      const failure = document.createElement('button'); failure.type = 'button';
      failure.dataset.failedRuns = 'toggle';
      failure.textContent = failedRunsOnly ? '모든 실행 보기' : '실패한 실행만';
      actionBar.append(failure); return;
    }
    if (!['issues','pull-requests'].includes(tab)) return;
    const state = document.createElement('select'); state.dataset.workFilter = 'state';
    for (const value of ['open','closed','all']) state.append(option(value, value)); state.value = workState;
    state.setAttribute('aria-label', '상태'); actionBar.append(state);
    if (tab === 'issues') {
      const role = document.createElement('select'); role.dataset.workFilter = 'role';
      for (const value of ['all','assigned','created','mentioned']) role.append(option(value, value));
      role.value = issueRole; role.setAttribute('aria-label', '사용자 필터'); actionBar.append(role);
      const label = document.createElement('input'); label.dataset.workFilter = 'label';
      label.value = issueLabel; label.placeholder = 'Label'; label.setAttribute('aria-label', 'Label');
      actionBar.append(label);
    }
    if (!repository) return;
    const button = document.createElement('button'); button.type = 'button';
    button.dataset.createKind = tab === 'issues' ? 'issue' : 'pr';
    button.textContent = tab === 'issues' ? '이슈 만들기' : 'PR 만들기';
    actionBar.append(button);
  }
  function openCreateForm(kind) {
    formHost.hidden = false;
    formHost.innerHTML = kind === 'issue'
      ? '<form data-github-create="issue"><label>제목<input name="title" maxlength="256" required></label><label>본문<textarea name="body" maxlength="60000" rows="5"></textarea></label><button class="primary">이슈 생성</button></form>'
      : '<form data-github-create="pr"><label>제목<input name="title" maxlength="256" required></label><label>Base branch<input name="base" maxlength="200" required></label><label>Head branch<input name="head" maxlength="200" required></label><label>본문<textarea name="body" maxlength="60000" rows="5"></textarea></label><label class="github-checkbox"><input name="draft" type="checkbox"> Draft</label><button class="primary">PR 생성</button></form>';
    formHost.querySelector('input[name="title"]').focus();
  }
  function openWorkflowForm(workflowId) {
    formHost.hidden = false;
    formHost.innerHTML = '<form data-github-dispatch><label>Branch 또는 tag<input name="ref" maxlength="200" required></label><button class="primary">Workflow 실행</button></form>';
    formHost.querySelector('form').dataset.workflowId = workflowId;
    formHost.querySelector('input').focus();
  }
  function renderRepositories() {
    const list = $('#github-repositories'); list.replaceChildren();
    const query = $('#github-search').value.trim().toLowerCase();
    const filter = $('#github-filter').value;
    const sorted = repositories.filter(item => (item.nameWithOwner + ' ' + (item.description || '')).toLowerCase().includes(query)
      && (filter === 'all' || (filter === 'public' && !item.isPrivate) || (filter === 'private' && item.isPrivate)
        || (filter === 'archived' && item.isArchived) || (filter === 'fork' && item.isFork)));
    sorted.sort((a,b) => $('#github-sort').value === 'name' ? a.nameWithOwner.localeCompare(b.nameWithOwner) : b.updatedAt.localeCompare(a.updatedAt));
    if (!sorted.length) list.textContent = '저장소가 없습니다.';
    for (const item of sorted) {
      const button = document.createElement('button'); button.type = 'button'; button.className = 'github-row';
      button.dataset.repository = item.nameWithOwner;
      button.setAttribute('aria-current', String(repository === item.nameWithOwner));
      const name = document.createElement('b'); name.textContent = item.nameWithOwner.split('/')[1];
      const detail = document.createElement('small'); detail.textContent = (item.isPrivate ? 'Private · ' : 'Public · ')
        + (item.isArchived ? 'Archived · ' : '') + (item.isFork ? 'Fork · ' : '') + (item.description || '설명 없음');
      button.append(name, detail); list.append(button);
    }
  }
  function detailRow(title, detail, kind, id, url, targetRepository = repository) {
    const row = document.createElement('div'); row.className = 'github-detail-row';row.dataset.liveKey=kind+':'+targetRepository+':'+id;
    const button = document.createElement('button'); button.type = 'button'; button.className = 'github-row';
    button.dataset.detailKind = kind; button.dataset.detailId = String(id);
    button.dataset.detailRepository = targetRepository;
    const name = document.createElement('b'); name.textContent = title;
    const meta = document.createElement('small'); meta.textContent = detail || '';
    button.append(name, meta); row.append(button);
    if (safeUrl(url)) {const external = document.createElement('a'); external.href = url;
      external.target = '_blank'; external.rel = 'noopener noreferrer'; external.textContent = '↗';
      external.setAttribute('aria-label', 'GitHub에서 열기'); row.append(external);}
    return row;
  }
  const repositoryFromUrl = value => {
    const match = /^https:\/\/github\.com\/([A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+)\//.exec(value || '');
    return match?.[1] || repository;
  };
  async function load() {
    setStatus('GitHub 인증 확인 중…');
    const status = await ui.api('/github/status');
    $('#github-login').hidden = status.authenticated;
    if (!status.authenticated) {
      setStatus('서버 GitHub 로그인 필요', 'warning');
      $('#github-items').textContent = '로그인 후 Owner와 저장소를 볼 수 있습니다.';
      return;
    }
    owners = await ui.api('/github/owners');
    const selector = $('#github-owner'); selector.replaceChildren();
    for (const item of owners) selector.append(option(item.login, (item.type === 'ORGANIZATION' ? '🏢 ' : '👤 ') + item.login));
    if (!owners.some(item => item.login === owner)) owner = owners[0]?.login || '';
    selector.value = owner;
    setStatus('서버 GitHub 연결됨', 'success');
    await loadApprovals();
    await loadOwner();
  }
  async function loadApprovals() {
    const approvals = await ui.api('/github/approvals', 'GET', undefined, {quiet:true});
    const signature = JSON.stringify(approvals.map(item => [item.id,item.operation,item.repository,item.number]));
    if (signature === approvalSignature) return;
    approvalSignature = signature;
    const list = $('#github-approvals'); list.replaceChildren();
    approvalPanel.hidden = !approvals.length;
    for (const item of approvals) {
      const row = document.createElement('div'); row.className = 'github-approval';
      const actions = {
        ARCHIVE_REPOSITORY: ['저장소 보관', '보관 승인'],
        DELETE_REPOSITORY: ['저장소 영구 삭제', '삭제 승인'],
        DELETE_RELEASE: ['릴리스 삭제', '삭제 승인'],
        MERGE_PULL_REQUEST: ['PR 병합', '병합 승인']
      };
      const action = actions[item.operation] || ['GitHub 작업', '승인'];
      const target = item.repository + (item.number ? (item.operation === 'DELETE_RELEASE' ? ' · 릴리스 #' : '#') + item.number : '');
      const label = document.createElement('span'); label.textContent = action[0] + ': ' + target;
      const button = document.createElement('button'); button.type = 'button'; button.dataset.approval = item.id;
      button.dataset.operation = item.operation;
      button.dataset.target = target;
      button.textContent = action[1];
      row.append(label, button); list.append(row);
    }
  }
  async function loadOwner() {
    if (!owner) return;
    const token = ++viewToken;
    repository = ''; tab = 'overview';
    $('#github-selected').textContent = owner;
    $('#github-items').innerHTML = window.WorkspaceUI.skeleton(3);
    renderNav();
    repositories = await ui.api('/github/owners/' + encodeURIComponent(owner) + '/repositories');
    if (token !== viewToken) return;
    renderRepositories(); await renderContent();
  }
  async function renderContent(options={}) {
    if(options.quiet&&(!formHost.hidden||!$('#github-detail').hidden||busy))return;
    const get=path=>ui.api(path,'GET',undefined,options);
    const token = ++viewToken;
    const list = options.quiet?document.createElement('div'):$('#github-items');
    if(!options.quiet){list.innerHTML = window.WorkspaceUI.skeleton(3);
    $('#github-detail').hidden = true; $('#github-detail').replaceChildren();
    $('#github-selected').textContent = repository || owner;
    $('#github-context').textContent = repository ? 'Repository' : owners.find(item => item.login === owner)?.type || '';
    renderNav(); renderRepositories(); renderActions();}
    try {
      let data;
      const repoQuery = '?repository=' + encodeURIComponent(repository);
      if (tab === 'overview') data = repository
        ? await get('/github/repositories/context' + repoQuery)
        : await get('/github/owners/' + encodeURIComponent(owner) + '/overview');
      else if (tab === 'repositories') data = repositories;
      else if (tab === 'issues') data = await get('/github/owners/' + encodeURIComponent(owner)
        + '/issues?state=' + encodeURIComponent(workState) + '&role=' + encodeURIComponent(issueRole)
        + (repository ? '&repository=' + encodeURIComponent(repository) : '')
        + (issueLabel ? '&label=' + encodeURIComponent(issueLabel) : ''));
      else if (tab === 'pull-requests') data = await get('/github/owners/' + encodeURIComponent(owner)
        + '/pull-requests?state=' + encodeURIComponent(workState)
        + (repository ? '&repository=' + encodeURIComponent(repository) : ''));
      else if (tab === 'actions') data = {
        runs: await get('/github/actions/runs' + repoQuery),
        workflows: await get('/github/actions/workflows' + repoQuery)
      };
      else if (tab === 'releases') data = await get('/github/releases' + repoQuery);
      else if (tab === 'files') {const detail = await get('/github/repositories/detail' + repoQuery); fileRef = detail.defaultBranch;
        data = await get('/github/repositories/tree' + repoQuery + '&ref=' + encodeURIComponent(fileRef));}
      else if (tab === 'commits') data = await get('/github/repositories/commits' + repoQuery);
      else if (tab === 'members') data = await get('/github/organizations/' + encodeURIComponent(owner) + '/members');
      if (token !== viewToken) return;
      list.replaceChildren();
      if (tab === 'overview') {
        const summary = document.createElement('div'); summary.className = 'github-summary';
        const metrics = repository
          ? [['Open Issues',data.openIssues.length,'issue'],['Open PRs',data.openPullRequests.length,'pull'],['Workflow Runs',data.recentWorkflowRuns.length,'activity']]
          : [['Repositories',data.repositories.length,'folder'],['Open Issues',data.openIssues.length,'issue'],['Open PRs',data.openPullRequests.length,'pull']];
        if (!repository && data.memberCount != null) metrics.push(['Members', data.memberCount,'devices']);
        for (const [label, count, symbol] of metrics) {
          const card = document.createElement('div'); card.className = 'ui-kpi';
          card.innerHTML = window.WorkspaceUI.icon(symbol) + '<strong></strong><small></small>';
          card.querySelector('small').textContent = label; card.querySelector('strong').textContent = count;
          summary.append(card);
        }
        list.append(summary);
        const heading = document.createElement('h3'); heading.textContent = '작업 항목'; list.append(heading);
        for (const item of [...data.openPullRequests.slice(0,5), ...data.openIssues.slice(0,5)])
          list.append(detailRow('#' + item.number + ' ' + item.title, item.updatedAt,
            data.openPullRequests.includes(item) ? 'pr' : 'issue', item.number, item.url,
            repositoryFromUrl(item.url)));
        if (repository) for (const run of data.recentWorkflowRuns.filter(item => item.conclusion === 'failure').slice(0,5))
          list.append(line(run.name || 'Workflow 실패', run.branch + ' · ' + run.createdAt, run.url));
        if (!repository && data.recentActivity?.length) {
          const activityTitle = document.createElement('h3'); activityTitle.textContent = 'Recent Activity'; list.append(activityTitle);
          for (const item of data.recentActivity.slice(0,10))
            list.append(line(item.repository + ' · ' + item.type, (item.actor || '') + ' · '
              + (item.action || '') + ' · ' + item.createdAt, item.url));
        }
      } else if (tab === 'actions') {
        const workflows = document.createElement('h3'); workflows.textContent = 'Workflows'; list.append(workflows);
        for (const workflow of data.workflows || []) {
          const row = document.createElement('div'); row.className = 'github-detail-row';
          row.append(line(workflow.name, workflow.state, workflow.url));
          const dispatch = document.createElement('button'); dispatch.type = 'button';
          dispatch.dataset.dispatchWorkflow = workflow.id; dispatch.textContent = '수동 실행';
          row.append(dispatch); list.append(row);
        }
        const runs = document.createElement('h3'); runs.textContent = 'Recent Runs'; list.append(runs);
        const visibleRuns = failedRunsOnly ? data.runs.filter(item => item.conclusion === 'failure') : data.runs;
        if (!visibleRuns.length) list.append(line('표시할 실행이 없습니다.', ''));
        for (const item of visibleRuns) list.append(detailRow(item.name || 'Workflow',
          (item.conclusion || item.status) + ' · ' + item.branch + ' · ' + item.createdAt,
          'run', item.id, item.url));
      } else {
        if (!data?.length) list.innerHTML = window.WorkspaceUI.emptyState('표시할 항목 없음','','git');
        for (const item of data || []) {
          if (tab === 'repositories') list.append(line(item.nameWithOwner, item.description, item.url));
          else if (tab === 'issues' || tab === 'pull-requests') list.append(detailRow('#' + item.number + ' ' + item.title,
            item.state + ' · ' + item.updatedAt, tab === 'issues' ? 'issue' : 'pr', item.number, item.url,
            repositoryFromUrl(item.url)));
          else if (tab === 'releases') list.append(detailRow(item.name || item.tag,
            item.tag + ' · ' + item.publishedAt, 'release', item.id, item.url));
          else if (tab === 'files') list.append(detailRow(item.path, item.type + (item.size ? ' · ' + item.size + ' bytes' : ''),
            item.type === 'blob' ? 'file' : 'tree', item.path, null));
          else if (tab === 'commits') list.append(detailRow(item.message?.split('\n')[0] || item.sha,
            item.sha?.slice(0,7) + ' · ' + item.author, 'commit', item.sha, item.url));
          else if (tab === 'members') list.append(line(item.login, '', item.url));
        }
      }
      if(options.quiet&&token===viewToken&&formHost.hidden&&$('#github-detail').hidden)window.WorkspaceLiveDOM?.patch($('#github-items'),list.innerHTML);
    } catch (error) {if(options.quiet)throw error;if (token === viewToken) list.innerHTML = window.WorkspaceUI.emptyState('조회 실패',error.message,'warning');}
  }
  async function showDetail(button) {
    if (button.dataset.detailKind === 'tree') return;
    const detail = $('#github-detail'); detail.hidden = false; detail.innerHTML = window.WorkspaceUI.skeleton(2);
    const target = button.dataset.detailRepository;
    const id = button.dataset.detailId;
    const kind = button.dataset.detailKind;
    const query = '?repository=' + encodeURIComponent(target);
    try {
      let item, files = [];
      if (kind === 'issue') item = await ui.api('/github/issues/detail' + query + '&number=' + encodeURIComponent(id));
      else if (kind === 'pr') {const context = await ui.api('/github/pull-requests/context' + query + '&number=' + encodeURIComponent(id));
        item = context.pullRequest; files = context.files; item.conversation = context.conversation;
        item.reviewComments = context.reviewComments; item.checks = context.checks; item.commits = context.commits;}
      else if (kind === 'run') {item = await ui.api('/github/actions/runs/' + encodeURIComponent(id) + '/analysis' + query);
        item.artifacts = await ui.api('/github/actions/runs/' + encodeURIComponent(id) + '/artifacts' + query).catch(() => []);}
      else if (kind === 'commit') {item = await ui.api('/github/repositories/commits/' + encodeURIComponent(id) + query);
        files = await ui.api('/github/repositories/commits/' + encodeURIComponent(id) + '/files' + query);}
      else if (kind === 'release') item = await ui.api('/github/releases/' + encodeURIComponent(id) + query);
      else if (kind === 'file') item = await ui.api('/github/repositories/file' + query + '&path=' + encodeURIComponent(id)
        + '&ref=' + encodeURIComponent(fileRef));
      else return;
      detail.replaceChildren();
      const heading = document.createElement('h3'); heading.textContent = kind === 'run' ? item.run.name :
        (item.title || item.name || item.path || item.message?.split('\n')[0] || id); detail.append(heading);
      const meta = document.createElement('p'); meta.className = 'github-detail-meta';
      meta.textContent = kind === 'run' ? (item.run.conclusion + ' · ' + item.run.branch) :
        kind === 'pr' ? (item.base + ' ← ' + item.head + ' · ' + item.state + ' · mergeable: ' + item.mergeable) :
        kind === 'issue' ? (item.state + ' · ' + (item.assignees || []).join(', ') + ' · ' + (item.labels || []).join(', ')) :
        kind === 'release' ? (item.tag + ' · ' + item.publishedAt) :
        kind === 'commit' ? (item.sha + ' · ' + item.author) : (item.size + ' bytes');
      detail.append(meta);
      const body = document.createElement('pre'); body.className = 'github-detail-body';
      body.textContent = kind === 'run' ? (item.failedLogs || '실패 로그가 없습니다.') :
        kind === 'file' ? item.content : (item.body || item.message || '본문이 없습니다.');
      detail.append(body);
      if (kind === 'release' && item.assets?.length) {
        const title = document.createElement('h4'); title.textContent = 'Assets'; detail.append(title);
        for (const asset of item.assets) detail.append(line(asset.name,
          asset.size + ' bytes · ' + asset.contentType, asset.downloadUrl));
      }
      if (kind === 'issue' || kind === 'pr') {
        const controls = document.createElement('div'); controls.className = 'github-run-controls';
        const stateButton = document.createElement('button'); stateButton.type = 'button';
        stateButton.dataset.workAction = item.state === 'open' ? 'closed' : 'open';
        stateButton.dataset.workKind = kind; stateButton.dataset.workRepository = target;
        stateButton.dataset.workNumber = id;
        stateButton.textContent = item.state === 'open' ? '닫기' : '다시 열기';
        controls.append(stateButton); detail.append(controls);
        const form = document.createElement('form'); form.dataset.githubResponse = kind;
        form.dataset.repository = target; form.dataset.number = id;
        const message = document.createElement('textarea'); message.name = 'body'; message.maxLength = 60000;
        message.rows = 3; message.placeholder = kind === 'issue' ? '댓글 작성' : '리뷰 의견 작성';
        message.setAttribute('aria-label', message.placeholder); form.append(message);
        if (kind === 'pr') {
          const review = document.createElement('select'); review.name = 'event';
          review.setAttribute('aria-label', '리뷰 결정');
          for (const [value, label] of [['COMMENT','의견 남기기'],['APPROVE','승인'],['REQUEST_CHANGES','수정 요청']])
            review.append(option(value, label));
          form.append(review);
        }
        const send = document.createElement('button'); send.className = 'primary';
        send.textContent = kind === 'issue' ? '댓글 등록' : '리뷰 제출'; form.append(send);
        detail.append(form);
      }
      if (kind === 'run') for (const job of item.failedJobs || []) {
        detail.append(line(job.name, (job.steps || []).filter(step => step.conclusion === 'failure')
          .map(step => step.name).join(', '), job.url));
      }
      if (kind === 'run' && item.artifacts?.length) {
        const title = document.createElement('h4'); title.textContent = 'Artifacts'; detail.append(title);
        for (const artifact of item.artifacts)
          detail.append(line(artifact.name, artifact.size + ' bytes · '
            + (artifact.expired ? '만료됨' : '사용 가능'), item.run.url));
      }
      if (kind === 'run') {
        const controls = document.createElement('div'); controls.className = 'github-run-controls';
        if (item.run.status === 'completed') {const rerun = document.createElement('button');
          rerun.textContent = '다시 실행'; rerun.dataset.runAction = 'rerun'; rerun.dataset.runId = id;
          rerun.dataset.runRepository = target; controls.append(rerun);}
        else {const cancel = document.createElement('button'); cancel.textContent = '실행 취소';
          cancel.dataset.runAction = 'cancel'; cancel.dataset.runId = id;
          cancel.dataset.runRepository = target; controls.append(cancel);}
        detail.append(controls);
      }
      if (kind === 'pr') {
        const section = document.createElement('h4'); section.textContent = 'Checks'; detail.append(section);
        for (const check of item.checks || []) detail.append(line(check.name, check.conclusion || check.status, check.url));
        const commits = document.createElement('p'); commits.textContent = 'Commits: ' + (item.commits || []).length;
        detail.append(commits);
        const discussion = document.createElement('h4'); discussion.textContent = 'Conversation'; detail.append(discussion);
        for (const comment of [...(item.conversation || []), ...(item.reviewComments || [])])
          detail.append(line(comment.author + (comment.path ? ' · ' + comment.path : ''), comment.body, comment.url));
      }
      for (const file of files) {
        const section = document.createElement('details');
        const summary = document.createElement('summary'); summary.textContent = file.filename + ' · +' + file.additions + ' -' + file.deletions;
        const patch = document.createElement('pre'); patch.textContent = file.patch || 'Patch를 제공하지 않는 변경 파일입니다.';
        section.append(summary, patch); detail.append(section);
      }
      if (window.innerWidth < 720) detail.scrollIntoView({block:'start', behavior:'smooth'});
    } catch (error) {detail.innerHTML = window.WorkspaceUI.emptyState('상세 조회 실패',error.message,'warning');}
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
        await (window.WorkspaceRealtime?.waitForJob(jobId,700)||new Promise(resolve => setTimeout(resolve, 700)));
        job = await ui.api('/studio/jobs/' + encodeURIComponent(jobId)); showAuthEvents(job.events);
      }
      showAuthEvents(job.events);
      if (job.state !== 'SUCCEEDED') throw new Error(job.error || 'GitHub 로그인이 취소되거나 실패했습니다.');
      $('#github-auth').hidden = true; await load();
    } catch (error) {setStatus(error.message);} finally {jobId = null; busy = false; $('#github-login').disabled = false; $('#github-cancel').hidden = true;}
  }
  root.addEventListener('click', async event => {
    try {
      if (event.target.closest('#github-login')) return await login();
      if (event.target.closest('#github-cancel')) {if (jobId) await ui.api('/studio/jobs/' + encodeURIComponent(jobId), 'DELETE'); return;}
      if (event.target.closest('#github-copy')) return await navigator.clipboard.writeText($('#github-auth-code').textContent);
      if (event.target.closest('#github-refresh')) return await load();
      const approve = event.target.closest('[data-approval]');
      if (approve) {
        if (approve.dataset.operation?.startsWith('DELETE_')
            && !window.confirm(approve.dataset.target + ' 삭제를 승인할까요? 이 작업은 복구할 수 없습니다.')) return;
        await ui.api('/github/approvals/' + encodeURIComponent(approve.dataset.approval), 'POST');
        return await loadApprovals();
      }
      const selected = event.target.closest('[data-repository]');
      if (selected) {repository = selected.dataset.repository; tab = 'overview';if(selected.closest('.ui-side-drawer'))window.WorkspaceDrawers?.close();return await renderContent();}
      const detailButton = event.target.closest('[data-detail-kind]');
      if (detailButton) return await showDetail(detailButton);
      const create = event.target.closest('[data-create-kind]');
      if (create) return openCreateForm(create.dataset.createKind);
      const failedRuns = event.target.closest('[data-failed-runs]');
      if (failedRuns) {failedRunsOnly = !failedRunsOnly; return await renderContent();}
      const dispatch = event.target.closest('[data-dispatch-workflow]');
      if (dispatch) return openWorkflowForm(dispatch.dataset.dispatchWorkflow);
      const runAction = event.target.closest('[data-run-action]');
      if (runAction) {await ui.api('/github/actions/runs/' + encodeURIComponent(runAction.dataset.runId)
        + '/' + runAction.dataset.runAction + '?repository=' + encodeURIComponent(runAction.dataset.runRepository), 'POST');
        setStatus(runAction.dataset.runAction === 'rerun' ? 'Workflow 재실행을 요청했습니다.' : 'Workflow 취소를 요청했습니다.');
        return await renderContent();}
      const workAction = event.target.closest('[data-work-action]');
      if (workAction) {
        const kind = workAction.dataset.workKind === 'issue' ? 'issues' : 'pull-requests';
        await ui.api('/github/' + kind + '/' + encodeURIComponent(workAction.dataset.workNumber)
          + '?repository=' + encodeURIComponent(workAction.dataset.workRepository), 'PATCH',
        {state:workAction.dataset.workAction});
        setStatus(workAction.dataset.workAction === 'closed' ? '항목을 닫았습니다.' : '항목을 다시 열었습니다.');
        await renderContent(); return;
      }
      const nextTab = event.target.closest('[data-tab]');
      if (nextTab) {tab = nextTab.dataset.tab;if(nextTab.closest('.ui-side-drawer'))window.WorkspaceDrawers?.close();return await renderContent();}
    } catch (error) {setStatus(error.message);}
  });
  root.addEventListener('submit', async event => {
    const dispatch = event.target.closest('[data-github-dispatch]');
    if (dispatch) {
      event.preventDefault();
      const ref = new FormData(dispatch).get('ref');
      try {
        await ui.api('/github/actions/workflows/' + encodeURIComponent(dispatch.dataset.workflowId)
          + '/dispatches?repository=' + encodeURIComponent(repository), 'POST', {ref});
        setStatus('Workflow 실행을 요청했습니다.'); await renderContent();
      } catch (error) {setStatus(error.message);}
      return;
    }
    const response = event.target.closest('[data-github-response]');
    if (response) {
      event.preventDefault();
      const values = new FormData(response);
      const query = '?repository=' + encodeURIComponent(response.dataset.repository);
      const base = '/github/' + (response.dataset.githubResponse === 'issue' ? 'issues' : 'pull-requests')
        + '/' + encodeURIComponent(response.dataset.number);
      try {
        if (response.dataset.githubResponse === 'issue') {
          if (!String(values.get('body') || '').trim()) throw new Error('댓글을 입력해 주세요.');
          await ui.api(base + '/comments' + query, 'POST', {body:values.get('body')});
        } else {
          await ui.api(base + '/reviews' + query, 'POST', {event:values.get('event'), body:values.get('body')});
        }
        setStatus(response.dataset.githubResponse === 'issue' ? '댓글을 등록했습니다.' : '리뷰를 제출했습니다.');
        await renderContent();
      } catch (error) {setStatus(error.message);}
      return;
    }
    const form = event.target.closest('[data-github-create]'); if (!form) return;
    event.preventDefault();
    const values = new FormData(form); const kind = form.dataset.githubCreate;
    const body = kind === 'issue' ? {title:values.get('title'), body:values.get('body')}
      : {title:values.get('title'), base:values.get('base'), head:values.get('head'),
        body:values.get('body'), draft:values.has('draft')};
    try {await ui.api('/github/' + (kind === 'issue' ? 'issues' : 'pull-requests') + '?repository='
      + encodeURIComponent(repository), 'POST', body);
      setStatus(kind === 'issue' ? '이슈를 생성했습니다.' : 'PR을 생성했습니다.'); await renderContent();}
    catch (error) {setStatus(error.message);}
  });
  $('#github-owner').addEventListener('change', async event => {owner = event.target.value; try {await loadOwner();} catch (error) {setStatus(error.message);}});
  $('#github-search').addEventListener('input', renderRepositories);
  $('#github-filter').addEventListener('change', renderRepositories);
  $('#github-sort').addEventListener('change', renderRepositories);
  root.addEventListener('change', event => {
    if (!event.target.matches('[data-work-filter]')) return;
    if (event.target.dataset.workFilter === 'state') workState = event.target.value;
    if (event.target.dataset.workFilter === 'role') issueRole = event.target.value;
    if (event.target.dataset.workFilter === 'label') issueLabel = event.target.value.trim();
    renderContent();
  });
  window.setInterval(() => {if (ui && root.getClientRects().length && !$('#github-login').hidden) return;
    if (ui && root.getClientRects().length&&!window.WorkspaceRealtime?.connected()) loadApprovals().catch(() => {});}, 10000);
  window.WorkspaceGithub = {async refresh(){if(!ui||!owner||!$('#github-login').hidden)return;await Promise.all([loadApprovals(),renderContent({quiet:true})]);},init(runtime) {ui = runtime;}, queueRepository(name) {pendingRepository = name;}, async open(id) {if (id !== 'github') return; await load();if(pendingRepository){const requested=pendingRepository;pendingRepository='';const requestedOwner=requested.split('/')[0];if(owner!==requestedOwner){owner=requestedOwner;$('#github-owner').value=owner;await loadOwner()}repository=requested;tab='overview';await renderContent()}}};
})();
