'use strict';
(() => {
  const panel = document.querySelector('#assistant');
  const messages = document.querySelector('#assistant-messages');
  const form = document.querySelector('#assistant-form');
  const prompt = document.querySelector('#assistant-prompt');
  const modelSelect = document.querySelector('#assistant-model');
  const effortSelect = document.querySelector('#assistant-effort');
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
  const runtime = window.WorkspaceAssistantRuntime;
  const localDevice = window.workspaceInitial?.devices?.find(device => device.id === 'local');
  if (!panel || !runtime || !localDevice?.rootPath) return;

  const conversationKey = 'dashboard-assistant-conversation-v1';
  const threadKey = 'dashboard-assistant-thread-v1';
  const assistantRoot = localDevice.rootPath;
  let busy = false;
  let ready = false;
  let mcpReady = false;
  let needsMcpRepair = false;
  let readyAt = 0;
  let preparing = null;
  let currentJob = null;
  let opened = false;
  let loading = null;
  let threadId = '';
  let conversation = [];
  let models = [];
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

  function renderConversation() {
    const interactionCards = [...messages.querySelectorAll('.assistant-interaction')];
    messages.replaceChildren();
    if (!conversation.length) {
      const welcome = document.createElement('div');
      welcome.className = 'assistant-welcome';
      welcome.innerHTML = '<span class="assistant-welcome-mark">✦</span><h2>대시보드에서 무엇을 도와드릴까요?</h2><p>일정과 노트를 찾고, 대시보드 화면이나 등록한 앱을 열 수 있어요.</p><div class="assistant-suggestions"><button type="button" data-assistant-prompt="오늘 일정 보여줘">오늘 일정 보기</button><button type="button" data-assistant-prompt="등록된 앱을 보여줘">등록 앱 확인</button><button type="button" data-assistant-prompt="최근 노트를 찾아줘">노트 찾기</button></div>';
      messages.append(welcome);
      messages.append(...interactionCards);
      return;
    }

    for (const entry of conversation) {
      const article = document.createElement('article');
      article.className = 'assistant-message';
      article.dataset.role = entry.role;
      if (entry.pending) article.dataset.pending = 'true';
      const label = document.createElement('small');
      label.className = 'assistant-message-label';
      label.textContent = entry.role === 'user' ? '나' : '대시보드 도우미';
      const body = document.createElement('div');
      body.className = 'assistant-message-body';
      if (entry.pending && !entry.text) {
        const progress = document.createElement('span');
        progress.className = 'assistant-progress';
        const label = document.createElement('span');
        label.textContent = {
          preparing: '요청을 준비하고 있어요',
          thinking: 'Codex가 생각하고 있어요',
          tools: '대시보드 정보를 확인하고 있어요',
          writing: '답변을 작성하고 있어요'
        }[entry.phase] || '요청을 준비하고 있어요';
        const dots = document.createElement('span');
        dots.className = 'assistant-progress-dots';
        dots.setAttribute('aria-hidden', 'true');
        for (let index = 0; index < 3; index++) dots.append(document.createElement('span'));
        progress.append(label, dots);
        body.append(progress);
      } else {
        body.textContent = entry.text;
      }
      article.append(label, body);
      messages.append(article);
    }
    messages.append(...interactionCards);
    messages.scrollTop = messages.scrollHeight;
  }

  function setBusy(value) {
    busy = value;
    panel.toggleAttribute('aria-busy', value);
    sendButton.disabled = value;
    stopButton.hidden = !value;
    if (value) setStatus('대시보드 기능을 확인하고 있어요…');
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
        await new Promise(resolve => setTimeout(resolve, 450));
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
    if (!efforts.length) {
      const option = document.createElement('option');
      option.value = '';
      option.textContent = '기본';
      effortSelect.append(option);
      return;
    }
    for (const effort of efforts) {
      const option = document.createElement('option');
      option.value = effort.reasoningEffort;
      option.textContent = effort.reasoningEffort;
      option.title = effort.description || effort.reasoningEffort;
      effortSelect.append(option);
    }
    effortSelect.value = efforts.some(item => item.reasoningEffort === preferred)
      ? preferred
      : model.defaultEffort || efforts[0].reasoningEffort;
  }

  function renderModels(nextModels) {
    const previousModel = modelSelect.value;
    const previousEffort = effortSelect.value;
    models = Array.isArray(nextModels) ? nextModels : [];
    modelSelect.replaceChildren();
    for (const model of models) {
      const option = document.createElement('option');
      option.value = model.id;
      option.textContent = model.name || model.id;
      option.title = model.description || model.id;
      modelSelect.append(option);
    }
    if (!models.length) {
      const option = document.createElement('option');
      option.value = '';
      option.textContent = '기본 모델';
      modelSelect.append(option);
    } else {
      const selected = models.find(model => model.id === previousModel) || models.find(model => model.defaultModel) || models[0];
      modelSelect.value = selected.id;
    }
    renderEfforts(previousEffort);
  }

  function renderRateLimits(limits) {
    if (!Array.isArray(limits) || !limits.length) {
      limitsText.textContent = '잔여 사용량 정보 없음';
      return;
    }
    limitsText.textContent = limits.map(limit => {
      const duration = Number(limit.windowDurationMins);
      const windowLabel = Number.isFinite(duration) && duration > 0
        ? duration >= 1440 && duration % 1440 === 0 ? duration / 1440 + '일'
          : duration >= 60 && duration % 60 === 0 ? duration / 60 + '시간' : duration + '분'
        : '기간 미상';
      const used = Number(limit.usedPercent);
      const remaining = limit.usedPercent != null && Number.isFinite(used)
        ? Math.max(0, Math.min(100, 100 - used)).toLocaleString('ko-KR', { maximumFractionDigits: 1 }) + '%'
        : '정보 없음';
      const reset = Number(limit.resetsAt);
      const resetLabel = Number.isFinite(reset) && reset > 0
        ? ' · ' + new Date(reset * 1000).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }) + ' 초기화'
        : '';
      return `${limit.name || 'Codex'} ${windowLabel} 잔여 ${remaining}${resetLabel}`;
    }).join('  |  ');
  }

  async function refreshRateLimits() {
    if (busy) return;
    limitsRefresh.disabled = true;
    limitsText.textContent = '잔여 사용량 확인 중…';
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
        await prepareServerCodex();
        const accountResult = await runJob('codex-account');
        const authenticated = Boolean(accountResult.assistant?.authenticated);
        limitsRefresh.disabled = !authenticated;
        loginButton.hidden = authenticated;
        loginButton.textContent = 'Codex 로그인';
        loginGuide.hidden = authenticated;
        if (!authenticated) {
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
        if (!dashboardMcp || dashboardMcp.error
            || (dashboardMcp.runtimeStatus != null && dashboardMcp.runtimeStatus !== 'connected')
            || !dashboardMcp.tools?.includes('list_calendar_events')) {
          const detail = dashboardMcp?.error ? ' (' + dashboardMcp.error + ')' : '';
          throw new Error('대시보드 MCP 일정 도구 연결에 실패했습니다. 도구 준비를 다시 실행해 주세요.' + detail);
        }
        needsMcpRepair = false;
        const modelResult = await runJob('codex-models');
        renderModels(modelResult.assistant?.models);
        mcpReady = true;
        await refreshRateLimits();
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
      setResponsePhase(responseIndex, 'tools');
      setStatus('대시보드 정보를 확인하고 있어요…');
    }
    if (update.kind === 'item' && update.item?.type === 'agentMessage') {
      const answer = conversation[responseIndex];
      if (!answer || answer.role !== 'assistant') return;
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
    if (!text || busy) return;
    if (!mcpReady) {
      setStatus('대시보드 MCP 연결을 먼저 복구해 주세요.', 'error');
      return;
    }
    prompt.value = '';
    conversation.push({ role: 'user', text }, { role: 'assistant', text: '', pending: true, phase: 'preparing' });
    conversation = conversation.slice(-60);
    const responseIndex = conversation.length - 1;
    saveConversation();
    renderConversation();
    try {
      await prepareServerCodex();
      const result = await runJob('codex-run', {
        prompt: text,
        threadId: threadId || undefined,
        model: modelSelect.value || undefined,
        effort: effortSelect.value || undefined,
        mode: 'read-only'
      }, (event, jobId) => receiveEvent(event, responseIndex, jobId));
      const answer = result.assistant || {};
      if (answer.thread?.id) threadId = answer.thread.id;
      const message = conversation[responseIndex];
      message.pending = false;
      if (!message.text) {
        const turns = answer.thread?.turns || [];
        const items = turns.length ? turns[turns.length - 1].items || [] : [];
        const finalMessage = items.slice().reverse().find(item => item.type === 'agentMessage');
        message.text = finalMessage?.text || '';
      }
      if (!message.text) message.text = '요청을 처리했지만 표시할 답변이 없습니다.';
      saveConversation();
      renderConversation();
      await refreshRateLimits();
      setStatus('대시보드 요청을 완료했습니다.');
    } catch (error) {
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
    prompt.focus();
  }

  async function stopCurrentJob() {
    if (!currentJob) return;
    stopButton.disabled = true;
    try {
      await runtime.api('/assistant/jobs/' + encodeURIComponent(currentJob), 'DELETE');
      setStatus('요청을 중지했습니다.');
    } catch (error) {
      setStatus(error.message, 'error');
    } finally {
      stopButton.disabled = false;
    }
  }

  window.addEventListener('workspace:view', event => {
    if (event.detail?.id === 'assistant') open().catch(error => runtime.toast(error.message));
  });
  document.querySelector('#assistant-new').addEventListener('click', () => {
    if (busy) return;
    threadId = '';
    conversation = [];
    messages.querySelectorAll('.assistant-interaction').forEach(card => card.remove());
    renderedInteractionIds.clear();
    saveConversation();
    renderConversation();
    prompt.focus();
    setStatus('새 대화를 시작합니다.');
  });
  loginButton.addEventListener('click', login);
  limitsRefresh.addEventListener('click', () => refreshRateLimits());
  stopButton.addEventListener('click', stopCurrentJob);
  modelSelect.addEventListener('change', () => renderEfforts());
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
  async function pollEvents() {
    try {
      const events = await runtime.api('/assistant/events?after=' + eventCursor);
      for (const event of events) {
        eventCursor = Math.max(eventCursor, event.sequence);
        try { sessionStorage.setItem('assistant-event-cursor', String(eventCursor)); } catch {}
        window.dispatchEvent(new CustomEvent('assistant:navigate', { detail: { route: event.route, applicationId: event.applicationId } }));
      }
    } catch {}
  }
  setInterval(() => { if (!document.hidden) pollEvents(); }, 1000);
  pollEvents();
})();
