"""Read-only device log commands, embedded in the fixed remote helper."""
import codecs
import selectors
import shutil
import time


def log_process(command, root):
    with process_lock:
        process = subprocess.Popen(command, cwd=root, env=env, stdin=subprocess.DEVNULL,
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   start_new_session=True)
        processes.add(process.pid)
    return process


def log_close(process):
    with process_lock:
        try: os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError: pass
        processes.discard(process.pid)
    process.wait()
    process.stdout.close()


def log_collect(command, root):
    process = log_process(command, root)
    chunks, size, deadline = [], 0, time.monotonic() + 10
    try:
        with selectors.DefaultSelector() as selector:
            selector.register(process.stdout, selectors.EVENT_READ)
            while True:
                if time.monotonic() >= deadline: raise Failure('로그 도구 응답 시간이 초과되었습니다.', 504)
                if not selector.select(.2): continue
                chunk = os.read(process.stdout.fileno(), 16384)
                if not chunk: break
                size += len(chunk)
                if size > 2 * LIMIT: raise Failure('로그 목록 또는 화면이 너무 큽니다.', 413)
                chunks.append(chunk)
        return process.wait(timeout=1), b''.join(chunks).decode('utf-8', errors='replace')
    finally:
        log_close(process)


def log_targets(mode, root):
    if mode == 'docker':
        rc, output = log_collect(['docker', 'ps', '-a', '--no-trunc', '--format', '{{json .}}'], root)
        if rc: raise Failure('Docker 목록을 읽지 못했습니다. 데몬 연결과 계정 권한을 확인하세요.', 502)
        targets = []
        for line in output.splitlines():
            row = json.loads(line)
            if re.fullmatch(r'[a-f0-9]{64}', row.get('ID', '')):
                targets.append(dict(id=row['ID'], name=row.get('Names', row['ID'][:12]), status=row.get('Status', '')))
        return targets
    rc, output = log_collect(['tmux', 'list-panes', '-a', '-F', '#{pane_id}\t#{session_name}\t#{window_index}\t#{pane_index}\t#{pane_current_command}'], root)
    if rc:
        if 'no server running' in output or 'no sessions' in output or 'No such file or directory' in output: return []
        raise Failure('tmux 목록을 읽지 못했습니다. SSH 계정과 기본 tmux 소켓을 확인하세요.', 502)
    targets = []
    for line in output.splitlines():
        parts = line.split('\t', 4)
        if len(parts) == 5 and re.fullmatch(r'%\d+', parts[0]):
            targets.append(dict(id=parts[0], name=f'{parts[1]} · {parts[2]}.{parts[3]}', status=parts[4]))
    return targets


def log_docker_follow(target, root):
    process = log_process(['docker', 'logs', '--follow', '--tail', '200', '--timestamps', target], root)
    decoder = codecs.getincrementaldecoder('utf-8')(errors='replace')
    sequence = 0
    try:
        with selectors.DefaultSelector() as selector:
            selector.register(process.stdout, selectors.EVENT_READ)
            while True:
                if not selector.select(.2): continue
                chunk = os.read(process.stdout.fileno(), 16384)
                text = decoder.decode(chunk, final=not chunk)
                if text:
                    sequence += 1
                    emit(event='log-append', text=text, sequence=sequence)
                if not chunk: break
                time.sleep(.1)
        if process.wait(timeout=1):
            raise Failure('Docker 로그가 종료되었습니다. 컨테이너와 로그 드라이버·접근 권한을 확인하세요.', 502)
    finally:
        log_close(process)


def device_logs(action, args, root):
    mode = args.get('mode')
    if mode not in ('docker', 'tmux'): raise Failure('로그 종류는 docker 또는 tmux여야 합니다.')
    if not shutil.which(mode, path=env['PATH']): raise Failure(f'선택한 장비에 {mode} 도구가 없습니다.', 409)
    if action == 'logs-targets': return dict(logTargets=log_targets(mode, root))
    target = args.get('target', '')
    if not isinstance(target, str) or not re.fullmatch(r'[a-f0-9]{64}' if mode == 'docker' else r'%\d+', target):
        raise Failure('로그 목록에서 유효한 대상을 선택하세요.')
    if mode == 'docker':
        log_docker_follow(target, root)
    else:
        previous, sequence = None, 0
        while True:
            rc, output = log_collect(['tmux', 'capture-pane', '-p', '-J', '-S', '-500', '-t', target], root)
            if rc: raise Failure('tmux 화면을 읽을 수 없습니다. 세션이 종료되었는지 확인하세요.', 409)
            output = output[-100000:]
            if output != previous:
                sequence += 1
                emit(event='log-snapshot', text=output, sequence=sequence)
                previous = output
            time.sleep(1)
    return dict(ok=True)
