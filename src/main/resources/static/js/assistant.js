'use strict';
(() => {
  const panel = document.querySelector('#assistant');
  const messages = document.querySelector('#assistant-messages');
  const form = document.querySelector('#assistant-form');
  const prompt = document.querySelector('#assistant-prompt');
  const modelSelect = document.querySelector('#assistant-model');
  const effortSelect = document.querySelector('#assistant-effort');
  const composerModelSelect = document.querySelector('#assistant-composer-model');
  const composerEffortSelect = document.querySelector('#assistant-composer-effort');
  const statusLabel = document.querySelector('#assistant-status-text');
  const accountStatus = document.querySelector('#assistant-account-status');
  const limitsText = document.querySelector('#assistant-limits-text');
  const limitsRefresh = document.querySelector('#assistant-limits-refresh');
  limitsRefresh.disabled = true;
  const loginButton = document.querySelector('#assistant-login');
  const loginGuide = document.querySelector('#assistant-login-guide');
  const loginMessage = document.querySelector('#assistant-login-message');
  const loginLink = document.querySelector('#assistant-login-link');
  const loginCodeRow = document.querySelector('#assistant-login-code-row');
  const loginCode = document.querySelector('#assistant-login-code');
  const copyCodeButton = document.querySelector('#assistant-copy-code');
  const stopButton = document.querySelector('#assistant-stop');
  const sendButton = document.querySelector('#assistant-send');
  const shell = document.querySelector('.assistant-shell');
  const sessions = document.querySelector('#assistant-sessions');
  const search = document.querySelector('#assistant-search');
  const moreButton = document.querySelector('#assistant-more');
  const settings = document.querySelector('#assistant-settings');
  const deleteDialog = document.querySelector('#assistant-delete-dialog');
  const deleteList = document.querySelector('#assistant-delete-list');
  const deleteStatus = document.querySelector('#assistant-delete-status');
  const deleteMore = document.querySelector('#assistant-delete-more');
  const deleteSelected = document.querySelector('#assistant-delete-selected');
  const closeSettings = () => { if (typeof settings.close === 'function' && settings.open) settings.close(); else settings.removeAttribute('open'); };
  const settingsLogin = document.querySelector('#assistant-settings-login');
  const settingsLogout = document.querySelector('#assistant-settings-logout');
  const settingsAccount = document.querySelector('#assistant-settings-account');
  const fileInput = document.querySelector('#assistant-file');
  const attachmentList = document.querySelector('#assistant-attachments');
  const runtime = window.WorkspaceAssistantRuntime;
  const localDevice = window.workspaceInitial?.devices?.find(device => device.id === 'local');
  if (!panel || !runtime || !localDevice?.rootPath) return;

  const conversationKey = 'dashboard-assistant-conversation-v1';
  const threadKey = 'dashboard-assistant-thread-v1';
  const preferencesKey = 'dashboard-assistant-model-preferences-v1';
  const assistantRoot = localDevice.rootPath;
  let busy = false;
  let chatPending = false;
  let stopRequested = false;
  let ready = false;
  let mcpReady = false;
  let needsMcpRepair = false;
  let readyAt = 0;
  let preparing = null;
  let currentJob = null;
  let opened = false;
  let loading = null;
  let threadId = '';
  let serviceDraft = null;
  let serviceDraftRemoved = [];
  let serviceDraftReviewReady = true;
  let conversation = [];
  let models = [];
  let preferredModelId = '';
  let preferredEffort = '';
  try {
    const stored = JSON.parse(localStorage.getItem(preferencesKey) || '{}');
    if (typeof stored.model === 'string') preferredModelId = stored.model.slice(0, 100);
    if (typeof stored.effort === 'string') preferredEffort = stored.effort.slice(0, 100);
  } catch {}
  let threads = [];
  let threadCursor = null;
  let deleteThreads = [];
  let deleteCursor = null;
  let deletingSessions = false;
  const selectedDeleteIds = new Set();
  let currentThreadTitle = '';
  let attachments = [];
  let firstHistoryLoad = false;
  const renderedInteractionIds = new Set();

  function setStatus(text, kind = '') {
    statusLabel.textContent = text;
    statusLabel.dataset.kind = kind;
  }

  function saveConversation() {
    try {
      sessionStorage.setItem(conversationKey, JSON.stringify(conversation.slice(-60)));
      sessionStorage.setItem(threadKey, threadId);
    } catch {}
  }

  function restoreConversation() {
    try {
      const stored = JSON.parse(sessionStorage.getItem(conversationKey) || '[]');
      if (Array.isArray(stored)) {
        conversation = stored.filter(item => ['user', 'assistant'].includes(item.role) && typeof item.text === 'string')
          .slice(-60).map(item => item.pending
            ? { role: item.role, text: item.text || '이전 요청 상태를 확인할 수 없습니다. 다시 보내 주세요.', pending: false }
            : item);
      }
      threadId = sessionStorage.getItem(threadKey) || '';
    } catch {
      conversation = [];
      threadId = '';
    }
  }

  function recordMcpCall(calls, item) {
    if (item?.type !== 'mcpToolCall' || typeof item.server !== 'string' || typeof item.tool !== 'string'
        || !item.server || !item.tool) return;
    const existing = item.id ? calls.find(call => call.id === item.id) : null;
    if (existing) existing.status = item.status || existing.status;
    else if (calls.length < 30) calls.push({id: item.id || '', server: item.server.slice(0, 100),
      tool: item.tool.slice(0, 120), status: item.status || ''});
  }

  function mcpCallsFromItems(items) {
    const calls = [];
    for (const item of items || []) recordMcpCall(calls, item);
    return calls;
  }

  function mcpFunctionLabel(call) {
    const dashboardFunctions = {
      open_page: '대시보드 페이지 열기', list_apps: '등록된 앱 조회', open_app: '앱 열기',
      list_calendar_events: '일정 조회', create_calendar_event: '일정 만들기',
      update_calendar_event: '일정 수정', delete_calendar_event: '일정 삭제',
      list_notes: '메모 목록 조회', read_note: '메모 읽기', create_note_folder: '메모 폴더 만들기',
      create_note: '메모 만들기', append_note: '메모 내용 추가', update_note_metadata: '메모 정보 수정',
      replace_note_text: '메모 내용 교체', delete_note: '메모 삭제',
      search_memories: '저장된 기억 검색', list_memories: '저장된 기억 목록 조회',
      get_memory: '저장된 기억 읽기', compose_memory_context: '관련 기억 정리',
      create_memory: '기억 저장', update_memory: '기억 수정', archive_memory: '기억 보관',
      restore_memory: '기억 복원', delete_memory: '기억 삭제', pin_memory: '기억 고정',
      unpin_memory: '기억 고정 해제', reinforce_memory: '기억 확인',
      supersede_memory: '기억 갱신', promote_memory_to_calendar: '기억을 일정으로 등록',
      promote_memory_to_note: '기억을 메모로 등록', promote_memories_to_note: '여러 기억을 메모로 정리',
      discover_service_resources: '서비스에 연결할 리소스 찾기',
      create_service_draft: '서비스 초안 만들기', update_service_draft: '서비스 초안 수정',
      get_service_draft: '서비스 초안 확인', cancel_service_draft: '서비스 초안 취소',
      commit_service_draft: '서비스 등록',
      list_services: '서비스 목록 조회', get_service: '서비스 정보 조회',
      get_service_logs: '서비스 운영 로그 조회', get_service_runtime: '서비스 실행 상태 조회',
      get_service_context: '서비스 연결 현황 조회', get_service_health: '서비스 상태 조회',
      list_database_connections: '데이터베이스 연결 조회', get_database_metadata: '데이터베이스 정보 조회',
      list_database_tables: '데이터베이스 테이블 조회', describe_database_table: '테이블 구조 조회',
      github_status: 'GitHub 연결 상태 확인', list_github_repositories: 'GitHub 저장소 조회',
      list_github_pull_requests: 'GitHub PR 조회', list_github_issues: 'GitHub 이슈 조회'
    };
    if (call.server === 'personal-dashboard' && dashboardFunctions[call.tool]) return dashboardFunctions[call.tool];
    if (call.server === 'personal-dashboard' && call.tool?.startsWith('github.')) {
      const tool = call.tool.slice('github.'.length);
      const actions = { list: '목록 조회', get: '조회', search: '검색', find: '찾기',
        create: '만들기', update: '수정', delete: '삭제', request: '승인 요청',
        archive: '보관', comment: '댓글 작성', merge: '병합', run: '실행', analyze: '분석' };
      const subjects = { owner: '계정', owners: '계정', organization: '조직', organizations: '조직',
        repository: '저장소', repositories: '저장소', issue: '이슈', issues: '이슈',
        pull_request: 'PR', pull_requests: 'PR', branch: '브랜치', branches: '브랜치',
        tag: '태그', tags: '태그', contributor: '기여자', contributors: '기여자',
        languages: '사용 언어', workflow: '워크플로', workflows: '워크플로',
        workflow_run: '워크플로 실행', workflow_runs: '워크플로 실행',
        workflow_jobs: '워크플로 작업', workflow_artifacts: '워크플로 결과물',
        workflow_logs: '워크플로 로그', release: '릴리스', releases: '릴리스',
        file: '파일', tree: '파일 트리', commit: '커밋', commit_diff: '커밋 변경 내용',
        pull_request_diff: 'PR 변경 내용', pr_context: 'PR 관련 정보',
        development_context: '개발 현황', org_overview: '조직 현황',
        recent_activity: '최근 활동', my_work: '내 작업', repo_health: '저장소 상태',
        org_members: '조직 구성원', org_teams: '조직 팀' };
      if (tool === 'request_archive') return 'GitHub 저장소 보관 승인 요청';
      if (tool === 'archive_repository') return 'GitHub 저장소 보관';
      if (tool === 'analyze_failed_workflow') return 'GitHub 워크플로 실패 분석';
      if (tool === 'search_across_org') return 'GitHub 조직 전체 검색';
      if (tool === 'find_related_issues') return 'GitHub 관련 이슈 찾기';
      const [action, ...parts] = tool.split('_');
      const subject = subjects[parts.join('_')] || subjects[parts.slice(1).join('_')];
      if (subject && actions[action]) return `GitHub ${subject} ${actions[action]}`;
      return 'GitHub 기능 사용';
    }
    return '연결된 기능 사용';
  }

  function renderConversation() {
    const interactionCards = [...messages.querySelectorAll('.assistant-interaction')];
    messages.replaceChildren();
    if (!conversation.length) {
      const welcome = document.createElement('div');
      welcome.className = 'assistant-welcome';
      welcome.innerHTML = '<span class="assistant-welcome-mark">✦</span><h2>무엇을 함께 처리할까요?</h2><p>일정과 메모, GitHub 작업을 한 대화에서 조회·작성·수정·삭제할 수 있어요.</p><div class="assistant-suggestions"><button type="button" data-assistant-prompt="오늘 일정과 관련 메모를 함께 정리해 줘">오늘 할 일 정리</button><button type="button" data-assistant-prompt="내 GitHub 저장소와 열린 이슈를 요약해 줘">GitHub 작업 보기</button></div>';
      messages.append(welcome);
      messages.append(...interactionCards);
      renderServiceDraft();
      return;
    }

    for (const entry of conversation) {
      const article = document.createElement('article');
      article.className = 'assistant-message';
      article.dataset.role = entry.role;
      if (entry.pending) article.dataset.pending = 'true';
      const label = document.createElement('small');
      label.className = 'assistant-message-label';
      label.textContent = entry.role === 'user' ? '나' : 'AI 비서';
      const body = document.createElement('div');
      body.className = 'assistant-message-body';
      article.append(label);
      if (entry.role === 'assistant' && entry.notices?.length) {
        const notices = document.createElement('div');
        notices.className = 'assistant-message-notices';
        for (const notice of entry.notices) {
          const line = document.createElement('div');
          window.AssistantMarkdown.render(line, notice);
          notices.append(line);
        }
        article.append(notices);
      }
      if (entry.pending && !entry.text) {
        const progress = document.createElement('span');
        progress.className = 'assistant-progress';
        const label = document.createElement('span');
        label.textContent = {
          preparing: '요청을 준비하고 있어요',
          thinking: 'Codex가 생각하고 있어요',
          tools: '대시보드 정보를 확인하고 있어요',
          discovery: 'GitHub·장비·Docker·DB·Telemetry 후보를 찾고 있어요',
          writing: '답변을 작성하고 있어요'
        }[entry.phase] || '요청을 준비하고 있어요';
        const dots = document.createElement('span');
        dots.className = 'assistant-progress-dots';
        dots.setAttribute('aria-hidden', 'true');
        for (let index = 0; index < 3; index++) dots.append(document.createElement('span'));
        progress.append(label, dots);
        body.append(progress);
      } else {
        if (entry.role === 'assistant') window.AssistantMarkdown.render(body, entry.text);
        else body.textContent = entry.text;
      }
      article.append(body);
      if (entry.role === 'assistant' && entry.mcpCalls?.length) {
        const report = document.createElement('details');
        report.className = 'assistant-tool-report';
        const heading = document.createElement('summary');
        heading.textContent = `실제로 사용한 기능 · ${entry.mcpCalls.length}개${entry.mcpCalls.length === 30 ? ' (최대 30개 표시)' : ''}`;
        const list = document.createElement('ul');
        for (const call of entry.mcpCalls) {
          const row = document.createElement('li');
          const name = document.createElement('span');
          name.className = 'assistant-tool-name';
          name.textContent = mcpFunctionLabel(call);
          row.append(name);
          if (call.status === 'failed') {
            const state = document.createElement('span');
            state.textContent = ' · 실패';
            row.append(state);
          }
          list.append(row);
        }
        report.append(heading, list);
        article.append(report);
      }
      if (entry.role === 'user' && Array.isArray(entry.attachments) && entry.attachments.length) {
        const files = document.createElement('small');
        files.className = 'assistant-message-files';
        files.textContent = entry.attachments.join(' · ');
        article.append(files);
      }
      messages.append(article);
    }
    messages.append(...interactionCards);
    renderServiceDraft();
    messages.scrollTop = messages.scrollHeight;
  }

  function renderServiceDraft() {
    if (!serviceDraft || serviceDraft.threadId !== threadId || chatPending) return;
    const draft = serviceDraft;
    const preview = document.createElement('section');
    preview.className = 'assistant-draft-preview';
    preview.setAttribute('aria-label', '서비스 초안');
    const body = document.createElement('div'); body.className = 'assistant-draft-body';
    const labels = {GITHUB_REPOSITORY:'GitHub 저장소', GITHUB_ORGANIZATION:'GitHub 조직', DEVICE:'장비',
      DOCKER_CONTAINER:'컨테이너', DATABASE:'데이터베이스', TELEMETRY:'텔레메트리', ENDPOINT:'접속 주소', FILE:'파일'};
    const selected = draft.candidates.filter(item => item.selected);
    const addText = (parent, tag, className, value) => {
      const element = document.createElement(tag);
      element.className = className;
      element.textContent = value;
      parent.append(element);
      return element;
    };
    const addSection = (title, entries) => {
      const section = document.createElement('section'); section.className = 'assistant-draft-section';
      addText(section, 'h4', '', title);
      const list = document.createElement('ul');
      for (const entry of entries) addText(list, 'li', '', entry);
      section.append(list); body.append(section);
    };
    const heading = document.createElement('header'); heading.className = 'assistant-draft-heading';
    addText(heading, 'small', 'assistant-draft-state', draft.status === 'COMMITTED' ? '반영 완료' : '검토 중인 서비스 초안');
    addText(heading, 'h3', '', draft.name);
    addText(heading, 'span', 'assistant-draft-environment', draft.environment);
    body.append(heading);
    if (draft.description) {
      const description = document.createElement('div'); description.className = 'assistant-draft-description';
      window.AssistantMarkdown.render(description, draft.description);
      body.append(description);
    }
    const resources = document.createElement('section'); resources.className = 'assistant-draft-section';
    addText(resources, 'h4', '', `연결할 리소스 · ${selected.length}개`);
    if (selected.length) {
      const groups = new Map();
      for (const item of selected) {
        if (!groups.has(item.type)) groups.set(item.type, []);
        groups.get(item.type).push(item);
      }
      for (const [type, items] of groups) {
        const group = document.createElement('div'); group.className = 'assistant-draft-group';
        addText(group, 'strong', '', `${labels[type] || type} · ${items.length}`);
        const list = document.createElement('ul');
        for (const item of items) addText(list, 'li', '', item.displayName);
        group.append(list); resources.append(group);
      }
    } else addText(resources, 'p', 'assistant-draft-empty', '연결할 리소스가 없어요. 채팅으로 추가할 대상을 알려 주세요.');
    body.append(resources);
    if (draft.excludedResources?.length) addSection('제외한 컨테이너', draft.excludedResources.map(excluded =>
      draft.candidates.find(item => item.type === excluded.type && item.reference === excluded.reference
        && item.deviceId === excluded.deviceId)?.displayName || excluded.reference));
    if (serviceDraftRemoved.length && draft.status !== 'COMMITTED') addSection('제거할 기존 연결', serviceDraftRemoved);
    if (!serviceDraftReviewReady) addText(body, 'p', 'assistant-draft-warning', '기존 연결을 확인하지 못했어요. 다시 불러온 뒤 승인해 주세요.');
    if (draft.status !== 'COMMITTED') {
      const selectedProjects = new Set(selected.filter(item => item.type === 'DOCKER_CONTAINER').map(item => item.composeProject).filter(Boolean));
      const questions = (draft.questions || []).filter(question => {
        if (!question.startsWith("Compose project '")) return true;
        return [...selectedProjects].some(project => question.startsWith(`Compose project '${project}'`)
          && draft.candidates.some(item => item.type === 'DOCKER_CONTAINER' && item.composeProject === project && !item.selected));
      });
      if (questions.length) addSection('확인이 필요한 항목', questions);
    }
    const guidance = document.createElement('div'); guidance.className = 'assistant-draft-guidance';
    if (draft.status === 'COMMITTED') {
      addText(guidance, 'p', '', '서비스 화면을 열려면 “서비스 열기”라고 입력해 주세요.');
    } else {
      addText(guidance, 'p', '', `수정할 내용을 말하거나, “승인”${draft.serviceId ? ' 또는 “서비스 변경 승인”' : '이나 “이대로 만들어줘”'}로 확정하거나, “취소”로 초안을 버릴 수 있어요.`);
    }
    body.append(guidance);
    preview.append(body);
    const latestMessage = [...messages.querySelectorAll('.assistant-message')].at(-1);
    let message = latestMessage?.dataset.role === 'assistant' ? latestMessage : null;
    if (!message) {
      message = document.createElement('article');
      message.className = 'assistant-message';
      message.dataset.role = 'assistant';
      const label = document.createElement('small'); label.className = 'assistant-message-label'; label.textContent = 'AI 비서';
      message.append(label);
      messages.append(message);
    }
    const report = message.querySelector('.assistant-tool-report');
    if (report) message.insertBefore(preview, report);
    else message.append(preview);
  }

  async function refreshServiceDraft() {
    if (!threadId) { serviceDraft = null; serviceDraftRemoved = []; serviceDraftReviewReady = true; renderConversation(); return; }
    serviceDraft = await runtime.api('/assistant/service-drafts/thread/' + encodeURIComponent(threadId));
    serviceDraftRemoved = [];
    serviceDraftReviewReady = true;
    if (serviceDraft?.serviceId && serviceDraft.status !== 'COMMITTED') {
      try {
        const existing = await runtime.api('/services/' + encodeURIComponent(serviceDraft.serviceId) + '/resources');
        serviceDraftRemoved = existing.filter(resource => !serviceDraft.candidates.some(item => item.selected && item.type === resource.type
          && item.reference === resource.reference && item.deviceId === resource.deviceId))
          .map(resource => `${resource.type} · ${resource.label || resource.reference}`);
      } catch { serviceDraftReviewReady = false; }
    }
    renderConversation();
  }

  function serviceDraftAction(text) {
    if (!serviceDraft || serviceDraft.threadId !== threadId || attachments.length) return '';
    const draft = serviceDraft;
    const command = text.replace(/[.!。]+$/u, '').replace(/\s+/gu, ' ').trim();
    if (draft.status === 'COMMITTED') return command === '서비스 열기' ? 'open' : '';
    if (['취소', '초안 취소', '서비스 초안 취소', '서비스 생성 취소', '이 초안 취소해줘'].includes(command)) return 'cancel';
    if (isServiceDraftCommitCommand(command, draft)) return 'commit';
    return '';
  }

  function isServiceDraftCommitCommand(command, draft) {
    if (/[?？]/u.test(command)) return false;
    const compact = command.replace(/[\s,，]+/gu, '');
    const approval = draft.serviceId ? '(?:승인|최종승인|서비스변경승인)' : '(?:승인|최종승인|서비스생성승인)';
    if (new RegExp(`^${approval}(?:해줘|해주세요|해|합니다|할게)?$`, 'u').test(compact)) return true;
    if (draft.serviceId) return false;
    const prefix = '(?:(?:이대로|그대로|이초안대로|현재초안대로)(?:서비스(?:를)?)?|서비스(?:를)?)?';
    const action = '(?:생성|만들|등록|확정)';
    const ending = '(?:해줘|해주세요|해|해요|어줘|어주세요|어|어요|자|할게)?';
    return new RegExp(`^${prefix}${action}${ending}$`, 'u').test(compact)
      || new RegExp(`^승인(?:하고|해서|후)?(?:서비스(?:를)?)?${action}${ending}$`, 'u').test(compact)
      || /^(?:서비스)?생성확정(?:해줘|해주세요|해|합니다)?$/u.test(compact)
      || /^(?:이대로|그대로|이초안대로|현재초안대로)진행(?:해줘|해주세요|해|하자)$/u.test(compact);
  }

  async function handleServiceDraftReply(text, action) {
    if (!serviceDraft || serviceDraft.threadId !== threadId || attachments.length) return false;
    const draft = serviceDraft;
    if (!action) return false;
    chatPending = true; syncComposerAction();
    prompt.value = '';
    conversation.push({role:'user', text});
    conversation = conversation.slice(-60);
    renderConversation();
    try {
      if (action === 'cancel') {
        await runtime.api('/assistant/service-drafts/' + encodeURIComponent(draft.id), 'DELETE');
        serviceDraft = null; serviceDraftRemoved = []; serviceDraftReviewReady = true;
        conversation.push({role:'assistant', text:'서비스 초안을 취소했어요.'});
      } else if (action === 'commit') {
        const reviewedRemovals = [...serviceDraftRemoved];
        await refreshServiceDraft();
        if (!serviceDraftReviewReady) throw new Error('기존 연결을 확인하지 못해 승인할 수 없어요. 대화를 다시 열어 구성을 확인해 주세요.');
        const path = '/assistant/service-drafts/' + encodeURIComponent(draft.id);
        if (serviceDraft?.id !== draft.id || serviceDraft.threadId !== threadId || serviceDraft.revision !== draft.revision
            || serviceDraft.status !== draft.status || JSON.stringify(serviceDraftRemoved) !== JSON.stringify(reviewedRemovals))
          throw new Error('서비스 초안이 변경됐어요. 현재 구성을 다시 확인한 뒤 승인해 주세요.');
        await runtime.api(path + '/approve', 'POST', {revision:draft.revision});
        const created = await runtime.api(path + '/commit', 'POST', {revision:draft.revision});
        serviceDraft = await runtime.api(path);
        serviceDraftRemoved = []; serviceDraftReviewReady = true;
        conversation.push({role:'assistant', text:`${created.name} 서비스를 ${draft.serviceId ? '수정했어요' : '만들었어요'}.`});
      } else if (action === 'open' && draft.serviceId) {
        window.dispatchEvent(new CustomEvent('assistant:navigate', {detail:{route:'services'}}));
        await window.WorkspaceServices?.openService(draft.serviceId);
        conversation.push({role:'assistant', text:'서비스 화면을 열었어요.'});
      }
      setStatus('');
    } catch (error) {
      conversation.push({role:'assistant', text:`처리하지 못했어요. ${error.message}`});
      setStatus(error.message, 'error');
      await refreshServiceDraft().catch(() => {});
    } finally {
      chatPending = false; syncComposerAction(); saveConversation(); renderConversation();
    }
    return true;
  }

  function syncComposerAction() {
    sendButton.hidden = chatPending;
    sendButton.disabled = busy;
    stopButton.hidden = !chatPending;
    stopButton.disabled = !currentJob || stopRequested;
  }

  function setBusy(value) {
    busy = value;
    panel.toggleAttribute('aria-busy', value);
    syncComposerAction();
    fileInput.disabled = value;
    document.querySelectorAll('#assistant-sessions button, #assistant-new, #assistant-header-new, #assistant-more, #assistant-settings-logout, #assistant-archive, #assistant-rename, #assistant-delete-open, #assistant-delete-current').forEach(button => { button.disabled = value; });
    if (!value) renderThreads();
    renderDeleteThreads();
    if (value) setStatus('대시보드 기능을 확인하고 있어요…');
  }

  function renderAttachments() {
    attachmentList.replaceChildren();
    attachments.forEach((item, index) => {
      const chip = document.createElement('span'); chip.className = 'assistant-attachment';
      const name = document.createElement('span'); name.textContent = item.name;
      const remove = document.createElement('button'); remove.type = 'button'; remove.textContent = '×';
      remove.setAttribute('aria-label', item.name + ' 첨부 제거');
      remove.dataset.attachmentIndex = String(index);
      chip.append(name, remove); attachmentList.append(chip);
    });
  }

  function setSidebar(open) {
    shell.dataset.sidebarOpen = String(open);
    document.querySelector('#assistant-sidebar-backdrop').hidden = !open;
    document.querySelector('#assistant-sidebar-open').setAttribute('aria-expanded', String(open));
  }

  function renderThreads() {
    sessions.replaceChildren();
    const unique = new Map(threads.filter(item => item?.id).map(item => [item.id, item]));
    const visible = [...unique.values()];
    if (!visible.length) {
      const empty = document.createElement('p'); empty.className = 'assistant-session-empty';
      empty.textContent = '저장된 대화가 없습니다.'; sessions.append(empty);
    }
    for (const item of visible) {
      const row = document.createElement('button'); row.type = 'button'; row.className = 'assistant-session';
      row.dataset.threadId = item.id; row.setAttribute('aria-current', String(item.id === threadId));
      row.disabled = busy;
      const title = document.createElement('span'); title.textContent = item.name || item.preview || '제목 없는 대화';
      const date = document.createElement('small');
      date.textContent = item.updatedAt ? new Date(item.updatedAt * 1000).toLocaleDateString('ko-KR') : '저장된 대화';
      row.append(title, date); sessions.append(row);
    }
    moreButton.hidden = !threadCursor;
    const current = unique.get(threadId);
    document.querySelector('#assistant-chat-title').textContent = current?.name || current?.preview || currentThreadTitle || '새 채팅';
    document.querySelector('#assistant-rename').disabled = busy || !threadId;
    document.querySelector('#assistant-archive').disabled = busy || !threadId;
    document.querySelector('#assistant-delete-current').disabled = busy || !threadId;
  }

  function renderDeleteThreads() {
    deleteList.replaceChildren();
    if (!deleteThreads.length) {
      const empty = document.createElement('p');
      empty.textContent = '삭제할 대화가 없습니다.';
      deleteList.append(empty);
    }
    for (const item of deleteThreads) {
      const row = document.createElement('label');
      row.className = 'assistant-delete-row';
      const checkbox = document.createElement('input');
      checkbox.type = 'checkbox';
      checkbox.value = item.id;
      checkbox.checked = selectedDeleteIds.has(item.id);
      checkbox.disabled = busy || deletingSessions;
      const detail = document.createElement('span');
      const title = document.createElement('strong');
      title.textContent = item.name || item.preview || '제목 없는 대화';
      const date = document.createElement('small');
      date.textContent = item.updatedAt ? new Date(item.updatedAt * 1000).toLocaleDateString('ko-KR') : '저장된 대화';
      detail.append(title, date);
      row.append(checkbox, detail);
      deleteList.append(row);
    }
    document.querySelector('#assistant-delete-count').textContent = `${selectedDeleteIds.size}개 선택`;
    deleteSelected.disabled = busy || deletingSessions || !selectedDeleteIds.size;
    deleteMore.hidden = !deleteCursor;
    deleteMore.disabled = busy || deletingSessions;
    document.querySelector('#assistant-delete-close').disabled = busy || deletingSessions;
    document.querySelector('#assistant-delete-cancel').disabled = busy || deletingSessions;
  }

  async function loadDeleteThreads(more = false) {
    if (busy || deletingSessions) return;
    deleteStatus.textContent = '대화 목록을 불러오는 중…';
    try {
      const result = await runJob('codex-threads', { cursor: more ? deleteCursor : undefined });
      const page = result.assistant || {};
      deleteThreads = more ? [...deleteThreads, ...(page.threads || [])] : page.threads || [];
      deleteCursor = page.nextCursor || null;
      deleteStatus.textContent = '';
      renderDeleteThreads();
    } catch (error) {
      deleteStatus.textContent = error.message;
    }
  }

  async function deleteCurrentThread() {
    if (!threadId || busy || !window.confirm('현재 대화를 영구 삭제할까요? 삭제 후 복구할 수 없습니다.')) return;
    const id = threadId;
    try {
      await runJob('codex-thread-delete', { threadId: id });
      selectedDeleteIds.delete(id);
      newChat();
      await refreshThreads();
      setStatus('대화를 삭제했습니다.');
    } catch (error) { setStatus(error.message, 'error'); }
  }

  async function deleteSelectedThreads() {
    if (busy || deletingSessions || !selectedDeleteIds.size) return;
    deletingSessions = true;
    renderDeleteThreads();
    const ids = [...selectedDeleteIds];
    let removed = 0;
    const failed = [];
    for (const id of ids) {
      deleteStatus.textContent = `${removed + failed.length + 1}/${ids.length} 대화 삭제 중…`;
      try {
        await runJob('codex-thread-delete', { threadId: id });
        selectedDeleteIds.delete(id);
        deleteThreads = deleteThreads.filter(item => item.id !== id);
        if (threadId === id) newChat();
        removed++;
      } catch (error) {
        failed.push(error.message);
      }
    }
    deletingSessions = false;
    renderDeleteThreads();
    try { await refreshThreads(); } catch (error) { failed.push(error.message); }
    if (failed.length) {
      deleteStatus.textContent = `${removed}개 삭제됨 · ${failed.length}개 실패: ${failed[0]}`;
      setStatus(deleteStatus.textContent, 'error');
    } else {
      deleteDialog.close?.();
      deleteDialog.removeAttribute('open');
      setStatus(`${removed}개 대화를 삭제했습니다.`);
    }
  }

  async function refreshThreads(more = false) {
    if (busy) return;
    const result = await runJob('codex-threads', {
      query: search.value.trim() || undefined,
      cursor: more ? threadCursor || undefined : undefined
    });
    const page = result.assistant || {};
    threads = more ? [...threads, ...(page.threads || [])] : page.threads || [];
    threadCursor = page.nextCursor || null;
    renderThreads();
  }

  function conversationFromThread(thread) {
    const entries = [];
    for (const turn of thread.turns || []) {
      let lastAssistant = null;
      for (const item of turn.items || []) {
        if ((item.type === 'userMessage' || item.type === 'agentMessage') && item.text) {
          entries.push({ role: item.type === 'userMessage' ? 'user' : 'assistant', text: item.text });
          if (item.type === 'agentMessage') lastAssistant = entries[entries.length - 1];
        }
      }
      if (lastAssistant) lastAssistant.mcpCalls = mcpCallsFromItems(turn.items);
      if (turn.error) entries.push({ role: 'assistant', text: turn.error });
    }
    return entries.slice(-60);
  }

  async function selectThread(id) {
    if (busy || !id || id === threadId) return;
    const result = await runJob('codex-thread-read', { threadId: id });
    const thread = result.assistant?.thread;
    if (!thread || thread.id !== id) throw new Error('대화를 불러오지 못했습니다.');
    threadId = id;
    currentThreadTitle = thread.name || thread.preview || '제목 없는 대화';
    conversation = conversationFromThread(thread);
    attachments = []; renderAttachments();
    renderedInteractionIds.clear(); messages.querySelectorAll('.assistant-interaction').forEach(card => card.remove());
    saveConversation(); renderConversation(); renderThreads(); setSidebar(false);
    await refreshServiceDraft();
    setStatus('저장된 대화를 불러왔습니다.'); prompt.focus();
  }

  async function runJob(action, args = {}, onEvent = () => {}) {
    if (busy) throw new Error('현재 대시보드 요청이 끝난 뒤 다시 시도해 주세요.');
    setBusy(true);
    let jobId = null;
    try {
      let job = await runtime.api('/assistant/jobs', 'POST', {
        deviceId: 'local',
        root: assistantRoot,
        action,
        args
      });
      jobId = job.id;
      currentJob = jobId;
      syncComposerAction();
      const seenEvents = new Set();
      const deliverEvents = events => {
        for (const event of events || []) {
          const identity = JSON.stringify(event);
          if (seenEvents.has(identity)) continue;
          seenEvents.add(identity);
          onEvent(event, jobId);
        }
      };
      while (job.state === 'RUNNING') {
        deliverEvents(job.events);
        await (window.WorkspaceRealtime?.waitForJob(jobId,450)||new Promise(resolve => setTimeout(resolve, 450)));
        job = await runtime.api('/assistant/jobs/' + encodeURIComponent(jobId));
      }
      deliverEvents(job.events);
      if (job.state !== 'SUCCEEDED') {
        const error = new Error(job.error || '대시보드 요청을 완료하지 못했습니다.');
        error.status = job.errorStatus;
        throw error;
      }
      return job.result || {};
    } finally {
      if (currentJob === jobId) currentJob = null;
      setBusy(false);
    }
  }

  async function prepareServerCodex() {
    if (ready && Date.now() - readyAt < 600000) return;
    if (preparing) return preparing;
    const refreshOnly = ready;
    preparing = (async () => {
      accountStatus.textContent = '서버 Codex 준비 중';
      await runJob('setup', { refresh: refreshOnly });
      ready = true;
      readyAt = Date.now();
    })().finally(() => { preparing = null; });
    return preparing;
  }

  function renderEfforts(preferred = '') {
    const model = models.find(item => item.id === modelSelect.value);
    const efforts = model?.efforts || [];
    effortSelect.replaceChildren();
    composerEffortSelect.replaceChildren();
    if (!efforts.length) {
      for (const select of [effortSelect, composerEffortSelect]) {
        const option = document.createElement('option');
        option.value = '';
        option.textContent = '기본';
        select.append(option);
      }
      return;
    }
    for (const effort of efforts) {
      for (const select of [effortSelect, composerEffortSelect]) {
        const option = document.createElement('option');
        option.value = effort.reasoningEffort;
        option.textContent = effort.reasoningEffort;
        option.title = effort.description || effort.reasoningEffort;
        select.append(option);
      }
    }
    const selected = efforts.some(item => item.reasoningEffort === preferred) ? preferred
      : efforts.some(item => item.reasoningEffort === model.defaultEffort)
        ? model.defaultEffort : efforts[0].reasoningEffort;
    effortSelect.value = selected;
    composerEffortSelect.value = selected;
  }

  function renderModels(nextModels) {
    const previousModel = modelSelect.value;
    const previousEffort = effortSelect.value;
    models = Array.isArray(nextModels) ? nextModels : [];
    modelSelect.replaceChildren();
    composerModelSelect.replaceChildren();
    for (const model of models) {
      for (const select of [modelSelect, composerModelSelect]) {
        const option = document.createElement('option');
        option.value = model.id;
        option.textContent = model.name || model.id;
        option.title = model.description || model.id;
        select.append(option);
      }
    }
    if (!models.length) {
      for (const select of [modelSelect, composerModelSelect]) {
        const option = document.createElement('option');
        option.value = '';
        option.textContent = '기본 모델';
        select.append(option);
      }
    } else {
      const selected = models.find(model => model.id === preferredModelId)
        || models.find(model => model.id === previousModel)
        || models.find(model => model.defaultModel) || models[0];
      modelSelect.value = selected.id;
      composerModelSelect.value = selected.id;
    }
    renderEfforts(modelSelect.value === preferredModelId ? preferredEffort : previousEffort);
  }

  function saveModelPreferences() {
    preferredModelId = modelSelect.value;
    preferredEffort = effortSelect.value;
    try { localStorage.setItem(preferencesKey, JSON.stringify({ model: preferredModelId, effort: preferredEffort })); }
    catch { /* The current page keeps the selection when browser storage is unavailable. */ }
  }

  function changeModel(select) {
    modelSelect.value = select.value;
    composerModelSelect.value = select.value;
    renderEfforts();
    saveModelPreferences();
  }

  function changeEffort(select) {
    effortSelect.value = select.value;
    composerEffortSelect.value = select.value;
    saveModelPreferences();
  }

  function renderRateLimits(limits) {
    if (!Array.isArray(limits) || !limits.length) {
      limitsText.textContent = '잔여 사용량 정보 없음';
      return;
    }
    limitsText.replaceChildren();
    const list = document.createElement('div');
    list.className = 'assistant-limit-list';
    for (const limit of limits) {
      const duration = Number(limit.windowDurationMins);
      const windowLabel = Number.isFinite(duration) && duration > 0
        ? duration >= 1440 && duration % 1440 === 0 ? duration / 1440 + '일'
          : duration >= 60 && duration % 60 === 0 ? duration / 60 + '시간' : duration + '분'
        : '기간 미상';
      const used = Number(limit.usedPercent);
      const remaining = limit.usedPercent != null && Number.isFinite(used)
        ? Math.max(0, Math.min(100, 100 - used)) : null;
      const reset = Number(limit.resetsAt);
      const resetLabel = Number.isFinite(reset) && reset > 0
        ? new Date(reset * 1000).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }) + ' 초기화'
        : '';
      const row = document.createElement('div');row.className = 'assistant-limit-row';
      const label = document.createElement('span');label.textContent = `${limit.name || 'Codex'} · ${windowLabel}`;
      const value = document.createElement('strong');
      value.textContent = remaining == null ? '—' : remaining.toLocaleString('ko-KR', {maximumFractionDigits:1}) + '%';
      row.append(label,value);
      const bar = document.createElement('div');
      bar.innerHTML = window.WorkspaceUI.progress(remaining,`${limit.name || 'Codex'} 잔여 사용량`,remaining != null && remaining <= 10?'danger':remaining != null && remaining <= 25?'warning':'accent');
      row.append(bar.firstElementChild);
      if (resetLabel) {const time = document.createElement('small');time.textContent = resetLabel;row.append(time);}
      list.append(row);
    }
    limitsText.append(list);
  }

  async function refreshRateLimits() {
    if (busy) return;
    limitsRefresh.disabled = true;
    limitsText.innerHTML = window.WorkspaceUI.skeleton(2);
    try {
      const result = await runJob('codex-rate-limits');
      renderRateLimits(result.assistant?.rateLimits);
    } catch {
      limitsText.textContent = '잔여 사용량을 확인할 수 없습니다';
    } finally {
      limitsRefresh.disabled = false;
    }
  }

  async function load(refreshModels = false) {
    if (loading) return loading;
    if (busy) return;
    loading = (async () => {
      try {
        mcpReady = false;
        needsMcpRepair = false;
        limitsRefresh.disabled = true;
        limitsText.textContent = '잔여 사용량 확인 전';
        settingsAccount.textContent = '서버 Codex 계정 확인 중';
        document.querySelector('#assistant-sidebar-account').textContent = settingsAccount.textContent;
        await prepareServerCodex();
        const accountResult = await runJob('codex-account');
        const authenticated = Boolean(accountResult.assistant?.authenticated);
        const account = accountResult.assistant || {};
        const identity = account.email || (account.accountType === 'apiKey' ? 'API 키 인증 · 이메일 미제공' : '로그인됨 · 이메일 미제공');
        settingsAccount.textContent = authenticated ? '서버 Codex · ' + identity + (account.plan ? ' · ' + account.plan : '') : '서버 Codex 로그인이 필요합니다.';
        document.querySelector('#assistant-sidebar-account').textContent = settingsAccount.textContent;
        settingsLogin.hidden = authenticated;
        settingsLogout.hidden = !authenticated;
        limitsRefresh.disabled = !authenticated;
        loginButton.hidden = authenticated;
        loginButton.textContent = 'Codex 로그인';
        loginGuide.hidden = authenticated;
        if (!authenticated) {
          threadId = ''; currentThreadTitle = ''; conversation = []; attachments = [];
          threads = []; threadCursor = null;
          saveConversation(); renderConversation(); renderThreads(); renderAttachments();
          limitsText.textContent = '로그인 후 잔여 사용량을 확인할 수 있습니다';
          accountStatus.textContent = 'Codex 로그인이 필요합니다';
          loginMessage.textContent = 'Codex 로그인을 시작하면 인증 주소와 일회용 코드를 여기에 표시합니다.';
          setStatus('대시보드 Codex 계정에 로그인해 주세요.');
          return;
        }
        needsMcpRepair = true;
        const connectionResult = await runJob('codex-connections');
        const dashboardMcp = (connectionResult.assistant?.connections || []).find(item => item.name === 'personal-dashboard');
        // Threadless discovery can return null runtimeStatus even with a live tool catalog.
        const requiredTools = ['list_apps', 'list_calendar_events', 'create_calendar_event',
          'update_calendar_event', 'delete_calendar_event', 'list_notes', 'read_note', 'create_note',
          'append_note', 'update_note_metadata', 'replace_note_text', 'delete_note',
          'github.get_repository', 'github.update_repository', 'github.update_release',
          'github.delete_repository', 'github.delete_release', 'discover_service_resources',
          'create_service_draft', 'update_service_draft', 'get_service_draft', 'commit_service_draft',
          'search_memories', 'get_memory', 'create_memory', 'compose_memory_context', 'get_service_runtime', 'get_service_logs'];
        if (!dashboardMcp || dashboardMcp.error
            || (dashboardMcp.runtimeStatus != null && dashboardMcp.runtimeStatus !== 'connected')
            || !requiredTools.every(name => dashboardMcp.tools?.includes(name))) {
          const detail = dashboardMcp?.error ? ' (' + dashboardMcp.error + ')' : '';
          throw new Error('AI 비서 도구 연결에 실패했습니다. 도구 준비를 다시 실행해 주세요.' + detail);
        }
        needsMcpRepair = false;
        const modelResult = await runJob('codex-models');
        renderModels(modelResult.assistant?.models);
        mcpReady = true;
        await refreshRateLimits();
        try { await refreshThreads(); } catch { sessions.textContent = '대화 목록을 불러오지 못했습니다. 새로고침해 주세요.'; }
        accountStatus.textContent = '대시보드 기능에 연결됨';
        setStatus('개인 대시보드 기능 전용 · Codex 연결됨');
        if (!refreshModels && !conversation.length && !threadId) renderConversation();
      } catch (error) {
        mcpReady = false;
        if (needsMcpRepair) {
          loginButton.hidden = false;
          loginButton.textContent = 'MCP 연결 복구';
          accountStatus.textContent = '대시보드 MCP 연결 확인 필요';
        } else {
          accountStatus.textContent = '서버 Codex 연결을 확인해 주세요';
        }
        setStatus(error.message, 'error');
        throw error;
      }
    })().finally(() => { loading = null; });
    return loading;
  }

  function renderInteraction(interaction, jobId) {
    if (!interaction || interaction.kind !== 'answer' || renderedInteractionIds.has(interaction.id)) return;
    renderedInteractionIds.add(interaction.id);
    const card = document.createElement('section');
    card.className = 'assistant-interaction';
    card.dataset.interactionId = interaction.id;
    const title = document.createElement('strong');
    title.textContent = '대시보드 작업 확인';
    const reason = document.createElement('p');
    reason.textContent = interaction.reason || '대시보드 기능이 입력을 요청했습니다.';
    const form = document.createElement('form');
    const questions = Array.isArray(interaction.questions) ? interaction.questions : [];
    for (const question of questions) {
      const label = document.createElement('label');
      const caption = document.createElement('span');
      caption.textContent = [question.header, question.question].filter(Boolean).join(' · ') || '확인';
      const options = Array.isArray(question.options) ? question.options : [];
      let field;
      if (options.length) {
        field = document.createElement('select');
        for (const option of options) {
          const item = document.createElement('option');
          item.value = option.label || '';
          item.textContent = option.label || option.description || '';
          if (option.description) item.title = option.description;
          field.append(item);
        }
      } else {
        field = document.createElement(question.secret ? 'input' : 'textarea');
        if (question.secret) field.type = 'password';
        field.maxLength = 4000;
        field.placeholder = '답변을 입력하세요';
      }
      field.name = question.id;
      field.required = true;
      label.append(caption, field);
      form.append(label);
    }
    const submit = document.createElement('button');
    submit.type = 'submit';
    submit.textContent = '확인하고 계속';
    form.append(submit);
    form.addEventListener('submit', async event => {
      event.preventDefault();
      if (!jobId || !questions.length || submit.disabled) return;
      submit.disabled = true;
      try {
        const answers = Object.fromEntries([...new FormData(form)].map(([name, value]) => [name, [String(value).slice(0, 4000)]]));
        await runtime.api('/assistant/jobs/' + encodeURIComponent(jobId) + '/inputs', 'POST', {
          type: 'answer', requestId: interaction.id, answers
        });
        card.dataset.completed = 'true';
        submit.textContent = '확인 완료';
        for (const field of form.elements) field.disabled = true;
        setStatus('대시보드 확인을 반영하고 있어요…');
      } catch (error) {
        submit.disabled = false;
        setStatus(error.message, 'error');
      }
    });
    card.append(title, reason, form);
    messages.append(card);
    messages.scrollTop = messages.scrollHeight;
  }

  function setResponsePhase(responseIndex, phase) {
    const answer = conversation[responseIndex];
    if (!answer?.pending || answer.text || answer.phase === phase) return;
    answer.phase = phase;
    saveConversation();
    renderConversation();
  }

  function receiveEvent(event, responseIndex, jobId) {
    if (event.event === '인증 주소' && /^https:\/\/auth\.openai\.com\//.test(event.url || '')) {
      loginGuide.hidden = false;
      loginLink.href = event.url;
      loginLink.hidden = false;
      loginMessage.textContent = '인증 페이지를 열고 Codex 로그인을 완료하세요.';
      setStatus('인증 페이지에서 로그인을 진행해 주세요.');
    }
    if (event.event === '일회용 인증 코드' && event.code) {
      loginGuide.hidden = false;
      loginCode.textContent = event.code;
      loginCodeRow.hidden = false;
      loginMessage.textContent = '인증 페이지에서 아래 일회용 코드를 입력하세요.';
      setStatus('인증 페이지에 표시된 입력란에 일회용 코드를 입력해 주세요.');
    }
    const update = event.assistant;
    if (!update) return;
    if (update.threadId) {
      threadId = update.threadId;
      saveConversation();
    }
    if (update.kind === 'interaction') renderInteraction(update.interaction, jobId);
    if (update.kind === 'started') setResponsePhase(responseIndex, 'thinking');
    if (update.kind === 'item' && update.item?.type === 'reasoning') {
      setResponsePhase(responseIndex, 'thinking');
      setStatus('Codex가 생각하고 있어요…');
    }
    if (update.kind === 'item' && update.item?.type === 'mcpToolCall') {
      const answer = conversation[responseIndex];
      if (answer?.role === 'assistant') recordMcpCall(answer.mcpCalls ||= [], update.item);
      const discovery = update.item.tool === 'discover_service_resources';
      setResponsePhase(responseIndex, discovery ? 'discovery' : 'tools');
      setStatus(discovery ? '서비스 리소스를 탐색하고 있어요…' : '대시보드 정보를 확인하고 있어요…');
    }
    if (update.kind === 'item' && update.item?.type === 'agentMessage') {
      const answer = conversation[responseIndex];
      if (!answer || answer.role !== 'assistant') return;
      if (update.item.id && answer.activeMessageId && answer.activeMessageId !== update.item.id) {
        if (answer.text) (answer.notices ||= []).push(answer.text);
        answer.text = '';
      }
      if (update.item.id) answer.activeMessageId = update.item.id;
      if (!update.item.text) {
        setResponsePhase(responseIndex, 'writing');
        setStatus('답변을 작성하고 있어요…');
        return;
      }
      answer.text = update.item.text;
      answer.pending = false;
      saveConversation();
      renderConversation();
    }
  }

  async function sendMessage(value = prompt.value) {
    const text = String(value || '').trim();
    if ((!text && !attachments.length) || busy || chatPending) return;
    const draftAction = serviceDraftAction(text);
    if (draftAction && await handleServiceDraftReply(text, draftAction)) return;
    if (!mcpReady) {
      setStatus('대시보드 MCP 연결을 먼저 복구해 주세요.', 'error');
      return;
    }
    chatPending = true;
    stopRequested = false;
    syncComposerAction();
    prompt.value = '';
    const selectedAttachments = [...attachments];
    conversation.push({ role: 'user', text: text || '첨부 파일을 확인해 주세요.', attachments: selectedAttachments.map(item => item.name) }, { role: 'assistant', text: '', pending: true, phase: 'preparing' });
    conversation = conversation.slice(-60);
    const responseIndex = conversation.length - 1;
    saveConversation();
    renderConversation();
    try {
      await prepareServerCodex();
      const result = await runJob('codex-run', {
        prompt: text || '첨부 파일을 확인해 주세요.',
        context: selectedAttachments.map(item => item.context),
        threadId: threadId || undefined,
        model: modelSelect.value || undefined,
        effort: effortSelect.value || undefined,
        mode: 'read-only'
      }, (event, jobId) => receiveEvent(event, responseIndex, jobId));
      const answer = result.assistant || {};
      if (answer.thread?.id) {
        threadId = answer.thread.id;
        currentThreadTitle = answer.thread.name || answer.thread.preview || text.slice(0, 60) || '첨부 대화';
      }
      const message = conversation[responseIndex];
      message.pending = false;
      const turns = answer.thread?.turns || [];
      const items = turns.length ? turns[turns.length - 1].items || [] : [];
      const recordedCalls = mcpCallsFromItems(items);
      if (recordedCalls.length) message.mcpCalls = recordedCalls;
      if (!message.text) {
        const finalMessage = items.slice().reverse().find(item => item.type === 'agentMessage');
        message.text = finalMessage?.text || '';
      }
      if (!message.text) message.text = '요청을 처리했지만 표시할 답변이 없습니다.';
      attachments = []; renderAttachments();
      saveConversation();
      renderConversation();
      chatPending = false;
      syncComposerAction();
      await refreshServiceDraft().catch(() => {});
      try { await refreshThreads(); } catch { /* The answer remains usable when history refresh fails. */ }
      await refreshRateLimits();
      setStatus('');
    } catch (error) {
      prompt.value = text;
      const message = conversation[responseIndex];
      if (error.status === 401) {
        mcpReady = false;
        needsMcpRepair = false;
        loginButton.hidden = false;
        loginButton.textContent = 'Codex 로그인';
        loginGuide.hidden = false;
        loginMessage.textContent = error.message;
        accountStatus.textContent = 'Codex 재로그인이 필요합니다';
      }
      if (message) {
        message.pending = false;
        if (!message.text) message.text = error.message;
      }
      saveConversation();
      renderConversation();
      setStatus(error.message, 'error');
    } finally {
      chatPending = false;
      stopRequested = false;
      syncComposerAction();
    }
  }

  async function login() {
    if (busy || loading) return;
    loginButton.disabled = true;
    if (needsMcpRepair) {
      setStatus('대시보드 MCP 연결을 복구하고 있어요…');
      try {
        await runJob('setup', { refresh: true });
        ready = true;
        readyAt = Date.now();
        await load(true);
      } catch (error) {
        setStatus(error.message, 'error');
      } finally {
        loginButton.disabled = false;
      }
      return;
    }
    loginButton.textContent = 'Codex 로그인';
    loginGuide.hidden = false;
    loginLink.hidden = true;
    loginCodeRow.hidden = true;
    loginMessage.textContent = 'Codex 인증 정보를 기다리고 있어요…';
    setStatus('Codex 로그인을 준비하고 있어요…');
    try {
      await runJob('codex-login', {}, event => receiveEvent(event, -1));
      ready = false;
      await load(true);
    } catch (error) {
      setStatus(error.message, 'error');
    } finally {
      loginButton.disabled = false;
    }
  }

  async function logout() {
    if (busy || loading) return;
    settingsLogout.disabled = true;
    try {
      await runJob('codex-logout');
      threadId = ''; currentThreadTitle = ''; conversation = []; threads = []; threadCursor = null; attachments = [];
      saveConversation(); renderConversation(); renderThreads(); renderAttachments();
      mcpReady = false; ready = false;
      closeSettings();
      await load(true);
      setStatus('서버 Codex 계정에서 로그아웃했습니다.');
    } catch (error) { setStatus(error.message, 'error'); }
    finally { settingsLogout.disabled = false; }
  }

  async function addFiles(files) {
    if (busy) return;
    for (const file of files) {
      if (attachments.length >= 16) throw new Error('첨부 파일은 최대 16개입니다.');
      if (file.name.length > 200) throw new Error('파일 이름은 200자 이하여야 합니다.');
      let context;
      if (['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) {
        if (file.size > 2000000) throw new Error('이미지는 파일당 2 MB 이하여야 합니다.');
        const dataUrl = await new Promise((resolve, reject) => {
          const reader = new FileReader(); reader.onload = () => resolve(reader.result); reader.onerror = reject;
          reader.readAsDataURL(file);
        });
        context = { kind: 'image', name: file.name, dataUrl };
      } else {
        if (!/\.(txt|md|markdown|json|csv|tsv|js|ts|jsx|tsx|py|java|html|css|xml|yaml|yml|sql|sh|log)$/i.test(file.name))
          throw new Error('텍스트·코드 파일 또는 PNG, JPEG, WebP 이미지만 첨부할 수 있습니다.');
        if (file.size > 64000) throw new Error('텍스트 파일은 파일당 64 KB 이하여야 합니다.');
        const content = await file.text();
        if (content.includes('\0') || content.includes('\uFFFD')) throw new Error('UTF-8 텍스트 파일만 첨부할 수 있습니다.');
        const textSize = attachments.filter(item => item.context.kind === 'upload').reduce((size, item) => size + item.context.content.length, 0) + content.length;
        if (textSize > 128000) throw new Error('첨부 텍스트 전체는 128,000자 이하여야 합니다.');
        context = { kind: 'upload', name: file.name, content };
      }
      const total = attachments.reduce((size, item) => size + JSON.stringify(item.context).length, 0) + JSON.stringify(context).length;
      if (total > 3500000) throw new Error('첨부 파일 전체 크기는 3.5 MB 이하여야 합니다.');
      attachments.push({ name: file.name, context });
    }
    renderAttachments();
  }

  function newChat() {
    if (busy) return;
    threadId = ''; currentThreadTitle = ''; conversation = []; attachments = [];
    serviceDraft = null; serviceDraftRemoved = []; serviceDraftReviewReady = true;
    messages.querySelectorAll('.assistant-interaction').forEach(card => card.remove());
    renderedInteractionIds.clear();
    saveConversation(); renderConversation(); renderThreads(); renderAttachments();
    setSidebar(false); closeSettings(); prompt.focus();
    setStatus('새 대화를 시작합니다.');
  }

  copyCodeButton.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(loginCode.textContent);
      copyCodeButton.textContent = '복사됨';
      setTimeout(() => { copyCodeButton.textContent = '코드 복사'; }, 1600);
    } catch {
      setStatus('코드를 선택해 직접 복사해 주세요.', 'error');
    }
  });

  async function open() {
    const firstOpen = !opened;
    opened = true;
    if (firstOpen) restoreConversation();
    renderConversation();
    await load(!firstOpen);
    if (!firstHistoryLoad && mcpReady && threadId) {
      firstHistoryLoad = true;
      const savedId = threadId;
      try {
        threadId = '';
        await selectThread(savedId);
      } catch (error) {
        threadId = '';
        conversation = [];
        saveConversation(); renderConversation(); renderThreads();
        setStatus('이전 대화를 불러오지 못했습니다. 목록에서 다시 선택해 주세요.', 'error');
      }
    }
    prompt.focus();
  }

  async function stopCurrentJob() {
    if (!chatPending || !currentJob || stopRequested) return;
    stopRequested = true;
    syncComposerAction();
    try {
      await runtime.api('/assistant/jobs/' + encodeURIComponent(currentJob), 'DELETE');
      setStatus('요청을 중지했습니다.');
    } catch (error) {
      stopRequested = false;
      setStatus(error.message, 'error');
      syncComposerAction();
    }
  }

  window.addEventListener('workspace:view', event => {
    if (event.detail?.id === 'assistant') open().catch(error => runtime.toast(error.message));
  });
  document.querySelector('#assistant-new').addEventListener('click', newChat);
  document.querySelector('#assistant-header-new').addEventListener('click', newChat);
  document.querySelector('#assistant-sidebar-open').addEventListener('click', () => setSidebar(true));
  document.querySelector('#assistant-sidebar-close').addEventListener('click', () => setSidebar(false));
  document.querySelector('#assistant-sidebar-backdrop').addEventListener('click', () => setSidebar(false));
  const openSettings = () => { setSidebar(false); if (typeof settings.showModal === 'function') settings.showModal(); else settings.setAttribute('open', ''); };
  document.querySelector('#assistant-settings-open').addEventListener('click', openSettings);
  document.querySelector('#assistant-header-settings').addEventListener('click', openSettings);
  document.querySelector('#assistant-settings-close').addEventListener('click', closeSettings);
  settingsLogin.addEventListener('click', () => { closeSettings(); login(); });
  settingsLogout.addEventListener('click', logout);
  document.querySelector('#assistant-history-refresh').addEventListener('click', () => refreshThreads().catch(error => setStatus(error.message, 'error')));
  document.querySelector('#assistant-delete-open').addEventListener('click', () => {
    if (busy) return;
    setSidebar(false);
    selectedDeleteIds.clear();
    deleteThreads = [];
    deleteCursor = null;
    renderDeleteThreads();
    if (typeof deleteDialog.showModal === 'function') deleteDialog.showModal();
    else deleteDialog.setAttribute('open', '');
    loadDeleteThreads();
  });
  const closeDeleteDialog = () => {
    if (busy || deletingSessions) return;
    if (typeof deleteDialog.close === 'function') deleteDialog.close();
    else deleteDialog.removeAttribute('open');
  };
  document.querySelector('#assistant-delete-close').addEventListener('click', closeDeleteDialog);
  document.querySelector('#assistant-delete-cancel').addEventListener('click', closeDeleteDialog);
  deleteDialog.addEventListener('cancel', event => { if (busy || deletingSessions) event.preventDefault(); });
  deleteMore.addEventListener('click', () => loadDeleteThreads(true));
  deleteList.addEventListener('change', event => {
    const checkbox = event.target.closest('input[type="checkbox"]');
    if (!checkbox || busy || deletingSessions) return;
    if (checkbox.checked) selectedDeleteIds.add(checkbox.value);
    else selectedDeleteIds.delete(checkbox.value);
    renderDeleteThreads();
  });
  deleteSelected.addEventListener('click', deleteSelectedThreads);
  document.querySelector('#assistant-delete-current').addEventListener('click', deleteCurrentThread);
  moreButton.addEventListener('click', () => refreshThreads(true).catch(error => setStatus(error.message, 'error')));
  let searchTimer;
  search.addEventListener('input', () => { clearTimeout(searchTimer); searchTimer = setTimeout(() => refreshThreads().catch(error => setStatus(error.message, 'error')), 280); });
  sessions.addEventListener('click', event => {
    const selected = event.target.closest('[data-thread-id]');
    if (selected) selectThread(selected.dataset.threadId).catch(error => setStatus(error.message, 'error'));
  });
  document.querySelector('#assistant-rename').addEventListener('click', async () => {
    if (!threadId || busy) return;
    const name = window.prompt('대화 이름', document.querySelector('#assistant-chat-title').textContent)?.trim();
    if (!name) return;
    if (name.length > 200) { setStatus('대화 이름은 200자 이하여야 합니다.', 'error'); return; }
    try { await runJob('codex-thread-rename', { threadId, name }); await refreshThreads(); closeSettings(); }
    catch (error) { setStatus(error.message, 'error'); }
  });
  document.querySelector('#assistant-archive').addEventListener('click', async () => {
    if (!threadId || busy || !window.confirm('이 대화를 보관할까요?')) return;
    try { await runJob('codex-thread-archive', { threadId }); newChat(); await refreshThreads(); }
    catch (error) { setStatus(error.message, 'error'); }
  });
  document.querySelector('#assistant-attach').addEventListener('click', () => fileInput.click());
  fileInput.addEventListener('change', () => {
    addFiles([...fileInput.files]).catch(error => setStatus(error.message, 'error')).finally(() => { fileInput.value = ''; });
  });
  attachmentList.addEventListener('click', event => {
    const button = event.target.closest('[data-attachment-index]');
    if (!button || busy) return;
    attachments.splice(Number(button.dataset.attachmentIndex), 1); renderAttachments();
  });
  form.addEventListener('dragover', event => { if (event.dataTransfer?.types.includes('Files')) event.preventDefault(); });
  form.addEventListener('drop', event => {
    if (!event.dataTransfer?.files.length) return;
    event.preventDefault(); addFiles([...event.dataTransfer.files]).catch(error => setStatus(error.message, 'error'));
  });
  loginButton.addEventListener('click', login);
  limitsRefresh.addEventListener('click', () => refreshRateLimits());
  stopButton.addEventListener('click', stopCurrentJob);
  modelSelect.addEventListener('change', () => changeModel(modelSelect));
  composerModelSelect.addEventListener('change', () => changeModel(composerModelSelect));
  effortSelect.addEventListener('change', () => changeEffort(effortSelect));
  composerEffortSelect.addEventListener('change', () => changeEffort(composerEffortSelect));
  form.addEventListener('submit', event => {
    event.preventDefault();
    sendMessage().catch(error => runtime.toast(error.message));
  });
  prompt.addEventListener('keydown', event => {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      sendMessage().catch(error => runtime.toast(error.message));
    }
  });
  messages.addEventListener('click', event => {
    const suggestion = event.target.closest('[data-assistant-prompt]');
    if (suggestion) sendMessage(suggestion.dataset.assistantPrompt).catch(error => runtime.toast(error.message));
  });
  setInterval(() => {
    if (opened && panel.classList.contains('active') && !document.hidden && !busy) load(true).catch(() => {});
  }, 600000);

  let eventCursor = 0;
  try { eventCursor = Number(sessionStorage.getItem('assistant-event-cursor') || 0) || 0; } catch {}
  let pollingEvents=false;
  async function pollEvents() {
    if(pollingEvents)return;pollingEvents=true;
    try {
      const events = await runtime.api('/assistant/events?after=' + eventCursor);
      for (const event of events) {
        eventCursor = Math.max(eventCursor, event.sequence);
        try { sessionStorage.setItem('assistant-event-cursor', String(eventCursor)); } catch {}
        window.dispatchEvent(new CustomEvent('assistant:navigate', { detail: { route: event.route, applicationId: event.applicationId } }));
      }
    } catch {} finally {pollingEvents=false;}
  }
  let lastEventPoll=0;
  setInterval(() => { if (!document.hidden&&(!window.WorkspaceRealtime?.connected()||Date.now()-lastEventPoll>10000)){lastEventPoll=Date.now();pollEvents();} }, 1000);
  window.addEventListener('workspace:invalidate',event=>{if(event.detail.topics.some(topic=>['all','workspace','assistant'].includes(topic)))pollEvents();});
  pollEvents();
})();
