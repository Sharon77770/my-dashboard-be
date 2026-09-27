'use strict';
(() => {
  const shell = document.querySelector('#assistant-float');
  const launcher = document.querySelector('#assistant-launcher');
  const panel = document.querySelector('#assistant-window');
  const messages = document.querySelector('#assistant-messages');
  const form = document.querySelector('#assistant-form');
  const prompt = document.querySelector('#assistant-prompt');
  const modelSelect = document.querySelector('#assistant-model');
  const effortSelect = document.querySelector('#assistant-effort');
  const statusLabel = document.querySelector('#assistant-status-text');
  const accountStatus = document.querySelector('#assistant-account-status');
  const loginButton = document.querySelector('#assistant-login');
  const stopButton = document.querySelector('#assistant-stop');
  const sendButton = document.querySelector('#assistant-send');
  const runtime = window.WorkspaceAssistantRuntime;
  const localDevice = window.workspaceInitial?.devices?.find(device => device.id === 'local');
  if (!shell || !launcher || !panel || !runtime || !localDevice?.rootPath) return;

  const conversationKey = 'dashboard-assistant-conversation-v1';
  const threadKey = 'dashboard-assistant-thread-v1';
  const assistantRoot = localDevice.rootPath;
  let busy = false;
  let ready = false;
  let readyAt = 0;
  let preparing = null;
  let currentJob = null;
  let opened = false;
  let loading = null;
  let drag = null;
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
        conversation = stored.filter(item => ['user', 'assistant'].includes(item.role) && typeof item.text === 'string').slice(-60);
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
      body.textContent = entry.text || (entry.pending ? '대시보드 기능을 확인하고 있어요…' : '');
      article.append(label, body);
      messages.append(article);
    }
    messages.append(...interactionCards);
    messages.scrollTop = messages.scrollHeight;
  }

  function setBusy(value) {
    busy = value;
    launcher.toggleAttribute('aria-busy', value);
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
      if (job.state !== 'SUCCEEDED') throw new Error(job.error || '대시보드 요청을 완료하지 못했습니다.');
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

  async function load(refreshModels = false) {
    if (loading) return loading;
    loading = (async () => {
      try {
        await prepareServerCodex();
        const modelResult = await runJob('codex-models');
        renderModels(modelResult.assistant?.models);
        const accountResult = await runJob('codex-account');
        const authenticated = Boolean(accountResult.assistant?.authenticated);
        accountStatus.textContent = authenticated ? '대시보드 기능에 연결됨' : 'Codex 로그인이 필요합니다';
        loginButton.hidden = authenticated;
        setStatus(authenticated ? '개인 대시보드 기능 전용 · Codex 연결됨' : '대시보드 Codex 계정에 로그인해 주세요.');
        if (!refreshModels && !conversation.length && !threadId) renderConversation();
      } catch (error) {
        accountStatus.textContent = '서버 Codex 연결을 확인해 주세요';
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

  function receiveEvent(event, responseIndex, jobId) {
    if (event.event === '인증 주소' && /^https:\/\/auth\.openai\.com\//.test(event.url || '')) {
      const link = document.createElement('a');
      link.href = event.url;
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      link.textContent = '인증 페이지 열기';
      setStatus('새 창에서 Codex 로그인을 완료한 뒤 돌아오세요.');
      statusLabel.append(' ', link);
    }
    if (event.event === '일회용 인증 코드' && event.code) {
      setStatus('Codex 인증 코드: ' + event.code);
    }
    const update = event.assistant;
    if (!update) return;
    if (update.threadId) {
      threadId = update.threadId;
      saveConversation();
    }
    if (update.kind === 'interaction') renderInteraction(update.interaction, jobId);
    if (update.kind === 'started' || update.kind === 'item' && update.item?.type === 'mcpToolCall') {
      setStatus('대시보드 기능을 확인하고 있어요…');
    }
    if (update.kind === 'item' && update.item?.type === 'agentMessage') {
      const answer = conversation[responseIndex];
      if (!answer || answer.role !== 'assistant') return;
      answer.text = update.item.text || '';
      answer.pending = false;
      saveConversation();
      renderConversation();
    }
  }

  async function sendMessage(value = prompt.value) {
    const text = String(value || '').trim();
    if (!text || busy) return;
    prompt.value = '';
    conversation.push({ role: 'user', text }, { role: 'assistant', text: '', pending: true });
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
      setStatus('대시보드 요청을 완료했습니다.');
    } catch (error) {
      const message = conversation[responseIndex];
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
    loginButton.disabled = true;
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

  function placePanel() {
    if (panel.hidden) return;
    const anchor = shell.getBoundingClientRect();
    const bounds = panel.getBoundingClientRect();
    const left = anchor.left > innerWidth / 2 ? anchor.right - bounds.width : anchor.left;
    const top = anchor.top > innerHeight / 2 ? anchor.top - bounds.height - 10 : anchor.bottom + 10;
    panel.style.left = Math.max(8, Math.min(innerWidth - bounds.width - 8, left)) + 'px';
    panel.style.top = Math.max(36, Math.min(innerHeight - bounds.height - 8, top)) + 'px';
    panel.style.right = 'auto';
    panel.style.bottom = 'auto';
  }

  async function open() {
    panel.hidden = false;
    launcher.setAttribute('aria-expanded', 'true');
    placePanel();
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

  launcher.addEventListener('click', () => {
    if (drag?.moved) return;
    if (panel.hidden) open().catch(error => runtime.toast(error.message));
    else {
      panel.hidden = true;
      launcher.setAttribute('aria-expanded', 'false');
    }
  });
  document.querySelector('#assistant-close').addEventListener('click', () => {
    panel.hidden = true;
    launcher.setAttribute('aria-expanded', 'false');
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
  window.addEventListener('resize', placePanel);
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && !panel.hidden) {
      panel.hidden = true;
      launcher.setAttribute('aria-expanded', 'false');
    }
  });
  setInterval(() => {
    if (opened && !panel.hidden && !document.hidden && !busy) load(true).catch(() => {});
  }, 600000);

  launcher.addEventListener('pointerdown', event => {
    if (event.button !== 0) return;
    drag = { id: event.pointerId, x: event.clientX, y: event.clientY, left: shell.offsetLeft, top: shell.offsetTop, moved: false };
    launcher.setPointerCapture(event.pointerId);
  });
  launcher.addEventListener('pointermove', event => {
    if (!drag || drag.id !== event.pointerId) return;
    const dx = event.clientX - drag.x, dy = event.clientY - drag.y;
    if (Math.abs(dx) + Math.abs(dy) > 6) drag.moved = true;
    if (!drag.moved) return;
    shell.style.left = Math.max(8, Math.min(innerWidth - shell.offsetWidth - 8, drag.left + dx)) + 'px';
    shell.style.top = Math.max(38, Math.min(innerHeight - shell.offsetHeight - 52, drag.top + dy)) + 'px';
    shell.style.right = 'auto';
    shell.style.bottom = 'auto';
    try { localStorage.setItem('assistant-position-v1', JSON.stringify({ left: shell.offsetLeft, top: shell.offsetTop })); } catch {}
  });
  launcher.addEventListener('pointerup', () => { if (drag) setTimeout(() => { drag = null; }, 0); });
  try {
    const position = JSON.parse(localStorage.getItem('assistant-position-v1'));
    if (Number.isFinite(position?.left) && Number.isFinite(position?.top)) {
      shell.style.left = Math.max(8, Math.min(innerWidth - 64, position.left)) + 'px';
      shell.style.top = Math.max(38, Math.min(innerHeight - 64, position.top)) + 'px';
      shell.style.right = 'auto';
      shell.style.bottom = 'auto';
    }
  } catch {}

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
