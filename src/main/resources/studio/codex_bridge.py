"""Codex App Server stdio adapter; included in the fixed remote helper."""
import queue
import time
import uuid

controls = queue.Queue(maxsize=64)


def assistant_item(item):
    kind = item.get('type', '')
    text = item.get('text', '')
    if kind == 'userMessage': text = '\n'.join(x.get('text', x.get('path', '[이미지]')) for x in item.get('content', []))
    if kind == 'reasoning': text = '\n'.join(item.get('summary', []))
    if kind == 'webSearch': text = item.get('query', '')
    return dict(id=item.get('id', ''), type=kind, text=clean(text)[:64000],
                status=item.get('status', ''), command=clean(item.get('command', ''))[:4000],
                output=clean(item.get('aggregatedOutput', ''))[-32000:],
                files=[dict(path=x.get('path', ''), diff=clean(x.get('diff', ''))[:32000],
                            kind=str(x.get('kind', ''))) for x in item.get('changes', [])[:100]])


def assistant_thread(thread):
    status = thread.get('status', {})
    return dict(id=thread['id'], name=thread.get('name') or '', preview=clean(thread.get('preview', ''))[:500],
                cwd=thread.get('cwd', ''), createdAt=thread.get('createdAt', 0), updatedAt=thread.get('updatedAt', 0),
                status=status.get('type', '') if isinstance(status, dict) else str(status),
                turns=[dict(id=t['id'], status=t.get('status', ''),
                            error=clean((t.get('error') or {}).get('message', '')),
                            items=[assistant_item(i) for i in t.get('items', [])]) for t in thread.get('turns', [])[-50:]])


class CodexBridge:
    def __init__(self, root):
        self.root, self.serial, self.sequence = root, 0, 0
        self.frames, self.replies, self.pending = queue.Queue(maxsize=512), {}, {}
        self.control_replies = set()
        self.thread_id, self.turn_id, self.finished = None, None, None
        self.items = {}
        with process_lock:
            self.process = subprocess.Popen(['codex', 'app-server'], cwd=root, env=env, stdin=subprocess.PIPE,
                                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, start_new_session=True)
            processes.add(self.process.pid)
        threading.Thread(target=self.read, daemon=True).start()

    def read(self):
        try:
            while True:
                line = self.process.stdout.readline(8 * LIMIT + 1)
                if not line or len(line) > 8 * LIMIT: break
                self.frames.put(json.loads(line))
        finally: self.frames.put(None)

    def close(self):
        self.process.stdin.close()
        try: self.process.wait(timeout=2)
        except subprocess.TimeoutExpired:
            os.killpg(self.process.pid, signal.SIGKILL); self.process.wait()
        self.process.stdout.close()
        with process_lock: processes.discard(self.process.pid)

    def write(self, frame):
        self.process.stdin.write((json.dumps(frame) + '\n').encode()); self.process.stdin.flush()

    def event(self, kind, **values):
        self.sequence += 1
        emit(assistant=dict(sequence=self.sequence, kind=kind, threadId=self.thread_id, turnId=self.turn_id, **values))

    def call(self, method, params):
        self.serial += 1
        ident = self.serial
        self.write(dict(id=ident, method=method, params=params))
        deadline = time.monotonic() + 60
        while ident not in self.replies:
            if time.monotonic() > deadline: raise Failure('Codex 응답 시간이 초과되었습니다.', 504)
            self.pump()
        reply = self.replies.pop(ident)
        if 'error' in reply: raise Failure(clean(reply['error'].get('message', 'Codex 요청 실패'))[:2000], 502)
        return reply.get('result') or {}

    def pump(self):
        try: control = controls.get_nowait()
        except queue.Empty: control = None
        if control: self.control(control)
        try: frame = self.frames.get(timeout=.1)
        except queue.Empty: return
        if frame is None: raise Failure('Codex App Server 연결이 종료되었습니다.', 502)
        method, params = frame.get('method', ''), frame.get('params') or {}
        if 'id' in frame and not method:
            if frame['id'] in self.control_replies:
                self.control_replies.remove(frame['id'])
                if 'error' in frame: self.event('notice', text=clean(frame['error'].get('message', '추가 입력 실패'))[:2000])
                return
            self.replies[frame['id']] = frame; return
        if 'id' in frame:
            ident = str(uuid.uuid4())
            if method in ('item/commandExecution/requestApproval', 'item/fileChange/requestApproval', 'item/tool/requestUserInput'):
                self.pending[ident] = (frame['id'], method, params)
                self.event('interaction', interaction=dict(id=ident, kind='answer' if method.endswith('requestUserInput') else 'approval',
                    reason=clean(params.get('reason') or 'Codex가 승인을 요청했습니다.'), command=clean(params.get('command') or ''),
                    questions=[dict(id=q['id'], header=q.get('header', ''), question=q.get('question', ''), secret=q.get('isSecret', False),
                                    options=q.get('options') or []) for q in params.get('questions', [])]))
            else:
                self.write(dict(id=frame['id'], error=dict(code=-32601, message='Unsupported interactive request')))
                self.event('notice', text='지원하지 않는 승인 요청을 거절했습니다: ' + method)
            return
        if method == 'thread/started': self.thread_id = params['thread']['id']
        if method == 'turn/started': self.turn_id = params['turn']['id']; self.event('started')
        if method in ('item/started', 'item/completed'):
            item = assistant_item(params['item']); self.items[item['id']] = item; self.event('item', item=item)
        if method == 'item/agentMessage/delta':
            ident = params['itemId']
            item = self.items.setdefault(ident, dict(id=ident, type='agentMessage', text=''))
            item['text'] = (item['text'] + clean(params.get('delta', '')))[-64000:]
            self.event('item', item=item)
        if method == 'turn/diff/updated': self.event('diff', text=clean(params.get('diff', ''))[:64000])
        if method == 'turn/plan/updated':
            self.event('plan', text='\n'.join(x.get('status', '') + ' · ' + x.get('step', '') for x in params.get('plan', [])))
        if method == 'thread/tokenUsage/updated':
            usage = params.get('tokenUsage') or {}; total = usage.get('total') or {}; last = usage.get('last') or {}
            self.event('usage', usage=dict(totalTokens=total.get('totalTokens'), inputTokens=total.get('inputTokens'),
                outputTokens=total.get('outputTokens'), cachedInputTokens=total.get('cachedInputTokens'),
                contextWindow=usage.get('modelContextWindow'), contextTokens=last.get('totalTokens')))
        if method == 'turn/completed': self.finished = params['turn']; self.event('completed')
        if method == 'thread/compacted': self.finished = dict(status='completed'); self.event('completed')

    def control(self, value):
        kind = value.get('type')
        if kind in ('approval', 'answer'):
            ident = value.get('requestId'); pending = self.pending.get(ident)
            if not pending: return
            rpc_id, method, params = pending
            if kind == 'approval' and method.endswith('requestApproval'):
                decision = value.get('decision')
                if decision not in ('accept', 'acceptForSession', 'decline', 'cancel'): return
                result = dict(decision=decision)
            elif kind == 'answer' and method.endswith('requestUserInput'):
                answers = value.get('answers') or {}
                if set(answers) != {q['id'] for q in params.get('questions', [])}: return
                result = dict(answers={key:dict(answers=answer) for key, answer in answers.items()})
            else: return
            self.write(dict(id=rpc_id, result=result)); del self.pending[ident]
            self.event('answered', requestId=ident)
        elif kind in ('interrupt', 'steer') and self.thread_id and self.turn_id:
            self.serial += 1
            self.control_replies.add(self.serial)
            params = dict(threadId=self.thread_id, turnId=self.turn_id)
            if kind == 'steer':
                params = dict(threadId=self.thread_id, expectedTurnId=self.turn_id,
                              input=[dict(type='text', text=textarg(value, 'text', 32000))])
            self.write(dict(id=self.serial, method='turn/' + kind, params=params))
        elif kind in ('interrupt', 'steer'):
            self.event('notice', text='Codex 작업이 아직 시작되지 않았습니다. 잠시 후 다시 시도하거나 하단 실행 중지를 사용하세요.')

    def owned(self, ident):
        thread = self.call('thread/read', dict(threadId=ident, includeTurns=True))['thread']
        if Path(thread['cwd']).resolve() != self.root: raise Failure('다른 작업 폴더의 세션에는 접근할 수 없습니다.', 403)
        return thread


def codex_input(root, args):
    inputs = [dict(type='text', text=textarg(args, 'prompt', 32000))]
    size = 0
    for context in args.get('context') or []:
        kind = context.get('kind')
        if kind in ('file', 'selection'):
            path = file_path(root, context)
            content = textarg(context, 'content', 32000) if kind == 'selection' else read_file(path)['content']
            size += len(content)
            if size > 128000: raise Failure('첨부 컨텍스트는 총 128,000자 이하로 선택해 주세요.', 413)
            inputs.append(dict(type='text', text='File context: ' + str(path.relative_to(root)) + '\n' + content))
        elif kind == 'image':
            value = context.get('dataUrl', '')
            if len(value) > 3000000 or not re.fullmatch(r'data:image/(?:png|jpeg|webp);base64,[A-Za-z0-9+/=]+', value):
                raise Failure('PNG, JPEG, WebP 이미지 파일만 첨부할 수 있습니다.')
            inputs.append(dict(type='image', url=value))
        elif kind != 'skill': raise Failure('지원하지 않는 컨텍스트입니다.')
    return inputs


def codex_action(root, action, args):
    # Empty App Server threads are not persisted until their first turn.
    if action == 'codex-thread-new':
        return dict(assistant=dict(thread=assistant_thread(dict(id='', cwd=str(root), turns=[]))))
    bridge = CodexBridge(root)
    try:
        bridge.call('initialize', dict(clientInfo=dict(name='personal_workspace', version='1.0.0'), capabilities=dict(experimentalApi=True)))
        bridge.write(dict(method='initialized'))
        if action == 'codex-models':
            result = bridge.call('model/list', dict(limit=100))
            return dict(assistant=dict(models=[dict(id=m['model'], name=m['displayName'], description=m.get('description', ''),
                defaultModel=m.get('isDefault', False), defaultEffort=m.get('defaultReasoningEffort'),
                efforts=m.get('supportedReasoningEfforts', []), inputModalities=m.get('inputModalities', [])) for m in result.get('data', [])]))
        if action == 'codex-threads':
            result = bridge.call('thread/list', dict(cwd=str(root), limit=25, cursor=args.get('cursor'),
                archived=bool(args.get('archived')), searchTerm=args.get('query'), sortKey='updated_at',
                sourceKinds=['cli', 'vscode', 'exec', 'appServer']))
            return dict(assistant=dict(threads=[assistant_thread(t) for t in result.get('data', []) if Path(t['cwd']).resolve() == root], nextCursor=result.get('nextCursor')))
        if action == 'codex-account':
            result = bridge.call('account/read', {})
            account = result.get('account') or {}
            return dict(assistant=dict(authenticated=bool(account), plan=account.get('planType', account.get('type', ''))))
        if action == 'codex-skills':
            result = bridge.call('skills/list', dict(cwds=[str(root)]))
            return dict(assistant=dict(skills=[dict(name=s['name'], description=clean(s['description'])[:2000], path=s['path'], enabled=s['enabled'])
                for entry in result.get('data', []) for s in entry.get('skills', [])]))
        if action == 'codex-connections':
            result = bridge.call('mcpServerStatus/list', dict(limit=100))
            return dict(assistant=dict(connections=[dict(name=s['name'], status=s.get('authStatus', 'unknown')) for s in result.get('data', [])]))
        ident = args.get('threadId')
        thread = bridge.owned(ident) if ident else None
        bridge.thread_id = ident
        if action == 'codex-thread-read': return dict(assistant=dict(thread=assistant_thread(thread)))
        methods = {'codex-thread-rename':'thread/name/set', 'codex-thread-archive':'thread/archive',
                   'codex-thread-unarchive':'thread/unarchive', 'codex-thread-fork':'thread/fork',
                   'codex-thread-compact':'thread/compact/start', 'codex-thread-rollback':'thread/rollback'}
        if action in methods:
            if not ident: raise Failure('세션을 선택해 주세요.')
            params = dict(threadId=ident)
            if action.endswith('rename'): params['name'] = textarg(args, 'name', 200)
            if action.endswith('rollback'): params['numTurns'] = 1
            if action.endswith(('compact', 'rollback')): bridge.call('thread/resume', dict(threadId=ident))
            result = bridge.call(methods[action], params)
            if action.endswith('compact'):
                while bridge.finished is None: bridge.pump()
            if result.get('thread'): return dict(assistant=dict(thread=assistant_thread(result['thread'])))
            return dict(ok=True)
        if action not in ('codex-run', 'codex-review'): raise Failure('지원하지 않는 Codex 작업입니다.')
        mode = args.get('mode') or 'read-only'
        if mode not in ('read-only', 'workspace-write'): raise Failure('지원하지 않는 실행 권한입니다.')
        params = dict(cwd=str(root), sandbox=mode, approvalPolicy='on-request')
        if args.get('model'): params['model'] = textarg(args, 'model', 100)
        if ident: params['threadId'] = ident
        result = bridge.call('thread/resume' if ident else 'thread/start', params)
        bridge.thread_id = result['thread']['id']
        if action == 'codex-thread-new': return dict(assistant=dict(thread=assistant_thread(result['thread']), model=result.get('model')))
        inputs = codex_input(root, args) if action == 'codex-run' else []
        for context in args.get('context') or []:
            if context.get('kind') == 'skill':
                skills = bridge.call('skills/list', dict(cwds=[str(root)]))
                matches = [s for entry in skills.get('data', []) for s in entry.get('skills', [])
                           if s['path'] == context.get('path') and s['name'] == context.get('name') and s['enabled']]
                if not matches: raise Failure('활성화된 CLI 스킬만 첨부할 수 있습니다.')
                inputs.append(dict(type='skill', name=matches[0]['name'], path=matches[0]['path']))
        turn_params = dict(threadId=bridge.thread_id, input=inputs)
        if args.get('model'): turn_params['model'] = args['model']
        if args.get('effort'): turn_params['effort'] = textarg(args, 'effort', 100)
        result = bridge.call('review/start', dict(threadId=bridge.thread_id, target=dict(type='uncommittedChanges'), delivery='inline')) if action == 'codex-review' else bridge.call('turn/start', turn_params)
        bridge.turn_id = result['turn']['id']; bridge.event('started')
        while bridge.finished is None: bridge.pump()
        thread = bridge.owned(bridge.thread_id)
        return dict(assistant=dict(thread=assistant_thread(thread), status=bridge.finished.get('status'), turnId=bridge.turn_id))
    finally: bridge.close()
