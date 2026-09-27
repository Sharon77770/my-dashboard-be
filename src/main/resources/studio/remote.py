"""Fixed SSH command adapter. Only the JSON request arrives on stdin; never shell input."""
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import stat
import subprocess
import sys
import tarfile
import tempfile
import threading
import time
import urllib.request
import urllib.parse

LIMIT = 1024 * 1024
processes = set()
process_lock = threading.Lock()
env = dict(os.environ, PATH=str(Path.home() / '.local/bin') + ':/usr/local/bin:/usr/bin:/bin',
           GIT_TERMINAL_PROMPT='0', GIT_PAGER='cat', GIT_LITERAL_PATHSPECS='1',
           GIT_SSH_COMMAND='ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10',
           NO_COLOR='1', LC_ALL='C.UTF-8', GH_BROWSER='true')
# Do not let an inherited Git directory redirect operations outside the selected workspace.
for key in list(env):
    if key.startswith('GIT_') and key not in ('GIT_TERMINAL_PROMPT', 'GIT_PAGER', 'GIT_LITERAL_PATHSPECS', 'GIT_SSH_COMMAND'):
        del env[key]


class Failure(Exception):
    def __init__(self, message, status=400):
        self.message, self.status = message, status


def emit(**value):
    print(json.dumps(value, ensure_ascii=True), flush=True)


def clean(text):
    text = re.sub(r'\x1b\[[0-9;]*[A-Za-z]', '', text)
    text = re.sub(r'(https?://)[^\s/@]+:[^\s/@]+@', r'\1[redacted]@', text)
    return re.sub(r'\b(?:sk-[A-Za-z0-9_-]{8,}|gh[pousr]_[A-Za-z0-9_]{8,})', '[redacted]', text)


def stop(*unused):
    with process_lock:
        for pid in processes:
            try: os.killpg(pid, signal.SIGKILL)
            except ProcessLookupError: pass
    os._exit(130)


def disconnected():
    for line in sys.stdin:
        if len(line) > 262144: stop()
        try: controls.put_nowait(json.loads(line))
        except (ValueError, queue.Full): pass
    stop()


def run(args, cwd=None, data=None, stream=False, auth=False, check=True):
    with process_lock:
        p = subprocess.Popen(args, cwd=cwd, env=env, stdin=subprocess.PIPE,
                             stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
        processes.add(p.pid)
    try:
        if data is not None: p.stdin.write(data.encode('utf-8'))
        p.stdin.close()
        chunks, size = [], 0
        for raw in iter(lambda: p.stdout.readline(LIMIT + 1), b''):
            size += len(raw)
            if size > 4 * LIMIT: raise Failure('명령 출력이 4 MiB를 초과했습니다.', 413)
            text = raw.decode('utf-8', errors='replace')
            if stream:
                if auth:
                    # Only the intentionally requested device authorization URL/code is returned.
                    url = re.search(r'https://(?:auth\.openai\.com/[^\s\x1b]+|github\.com/login/device)', clean(text))
                    code = re.search(r'^\s*([A-Za-z0-9]{4,5}-[A-Za-z0-9]{4,5})\s*$', clean(text)) or re.search(r'one-time code:\s*([A-Za-z0-9]{4,5}-[A-Za-z0-9]{4,5})', clean(text))
                    if url: emit(event='인증 주소', url=url.group(0))
                    if code: emit(event='일회용 인증 코드', code=code.group(1))
                else:
                    try:
                        event = json.loads(text)
                        item = event.get('item', {})
                        if item.get('type') == 'agent_message': emit(event='Codex', text=clean(item.get('text', ''))[:30000])
                        elif item.get('type') == 'command_execution': emit(event='명령 실행', text=clean(item.get('command', ''))[:2000], state=item.get('status', ''))
                        elif item.get('type') == 'file_change': emit(event='파일 변경', text=clean(json.dumps(item.get('changes', [])))[:12000])
                        elif event.get('type') in ('turn.failed', 'error'): emit(event='Codex 오류', text='인증·모델 접근·서버 네트워크 또는 샌드박스 설정을 확인해 주세요.')
                        elif event.get('type') == 'turn.completed': emit(event='Codex 응답 완료')
                    except (ValueError, TypeError): pass
            else: chunks.append(raw)
        result = b''.join(chunks)
        rc = p.wait()
        if check and rc:
            if stream: raise Failure('CLI 실행 실패: 원격 터미널에서 인증 상태와 실행 환경을 확인해 주세요.', 502)
            raise Failure(clean(result.decode('utf-8', errors='replace'))[-3000:] or 'CLI 명령이 실패했습니다.', 409)
        return rc, result
    finally:
        with process_lock:
            try: os.killpg(p.pid, signal.SIGKILL)
            except ProcessLookupError: pass
            processes.discard(p.pid)
        p.wait()
        p.stdout.close()


def textarg(args, key, maximum=1024, required=True):
    value = args.get(key, '')
    if not isinstance(value, str) or len(value) > maximum or '\0' in value or (required and not value.strip()):
        raise Failure('입력값을 확인해 주세요: ' + key)
    return value


def within(base, value):
    if not isinstance(value, str) or len(value) > 4096 or '\0' in value: raise Failure('잘못된 경로입니다.')
    candidate = Path(value) if value.startswith('/') else base / value
    try:
        relative = candidate.relative_to(base)
        if '..' in relative.parts: raise ValueError()
        current = base
        for part in relative.parts:
            current = current / part
            if current.is_symlink(): raise ValueError()
        current.resolve().relative_to(base)
    except (ValueError, OSError): raise Failure('작업 폴더 밖의 경로 또는 심볼릭 링크에는 접근할 수 없습니다.', 403)
    return candidate


def file_path(root, args, key='path'):
    path = within(root, textarg(args, key, 4096))
    if '.git' in path.relative_to(root).parts: raise Failure('.git 내부는 Git 작업으로 관리해 주세요.', 403)
    return path


def read_file(path):
    if not path.is_file() or not stat.S_ISREG(path.stat().st_mode): raise Failure('일반 파일만 편집할 수 있습니다.')
    with path.open('rb') as f: data = f.read(LIMIT + 1)
    if len(data) > LIMIT: raise Failure('1 MiB 이하 파일만 편집할 수 있습니다.', 413)
    try:
        text = data.decode('utf-8')
        if '\0' in text: raise ValueError()
    except (UnicodeError, ValueError): raise Failure('UTF-8 텍스트 파일만 편집할 수 있습니다.', 415)
    return dict(content=text, revision=hashlib.sha256(data).hexdigest())


def git(root, *args, check=True):
    return run(['git', '-c', 'color.ui=false', '-c', 'core.quotepath=false', *args], cwd=root, check=check)


def repository_url(args):
    url = textarg(args, 'url', 2048)
    if not (re.fullmatch(r'https://[^\s]+', url) or re.fullmatch(r'[A-Za-z0-9_.-]+@[A-Za-z0-9.-]+:[^\s]+', url)):
        raise Failure('HTTPS 또는 git@host:path 형식의 SSH 주소를 입력해 주세요.')
    if url.startswith('https://') and (urllib.parse.urlsplit(url).username or urllib.parse.urlsplit(url).query):
        raise Failure('URL에 인증 정보를 넣지 마세요. 원격 서버의 Git 인증을 사용합니다.')
    return url


def repository(root):
    rc, top = git(root, 'rev-parse', '--show-toplevel', check=False)
    if rc: raise Failure('먼저 Git 저장소를 초기화하거나 복제해 주세요.', 409)
    if Path(top.decode().strip()).resolve() != root: raise Failure('Git 저장소 최상위 폴더를 작업 공간으로 열어 주세요.', 409)
    rc, gitdir = git(root, 'rev-parse', '--absolute-git-dir')
    within(root, gitdir.decode().strip())


def install_github_cli():
    """Install the GitHub credential CLI to the same user prefix, verifying the official artifact."""
    destination = Path.home() / '.local/bin/gh'
    if destination.exists(): return
    arch = 'amd64' if os.uname().machine == 'x86_64' else 'arm64'
    digest = {'amd64':'e4d4bb4498e8d007abe545b6568926793ace1b6447da598294a610018cb164be',
              'arm64':'ea4e7a581a32ccad6cc7923cb1576ac5859ba4b9a16ab22eb8f8a96e78e2e961'}[arch]
    name = 'gh_2.100.0_linux_' + arch
    emit(event='GitHub CLI 2.100.0 다운로드 및 SHA256 검증 중')
    with urllib.request.urlopen('https://github.com/cli/cli/releases/download/v2.100.0/' + name + '.tar.gz', timeout=60) as response:
        data = response.read(100 * LIMIT + 1)
    if len(data) > 100 * LIMIT or hashlib.sha256(data).hexdigest() != digest:
        raise Failure('GitHub CLI 설치 파일 무결성 검증에 실패했습니다.', 502)
    import io
    with tarfile.open(fileobj=io.BytesIO(data), mode='r:gz') as archive:
        member = archive.getmember(name + '/bin/gh')
        if not member.isfile() or member.size > 200 * LIMIT: raise Failure('잘못된 GitHub CLI 설치 파일입니다.', 502)
        with tempfile.NamedTemporaryFile(dir=destination.parent, delete=False) as output:
            tmp = Path(output.name)
            try:
                output.write(archive.extractfile(member).read()); output.flush()
                os.fchmod(output.fileno(), 0o755); os.replace(tmp, destination)
            finally: tmp.unlink(missing_ok=True)


def install_codex_artifact(destination, arch, url, expected_digest):
    name = 'codex-' + arch + '-unknown-linux-musl'
    if not url.startswith('https://github.com/openai/codex/releases/download/'):
        raise Failure('Codex 릴리스 다운로드 주소가 올바르지 않습니다.', 502)
    digest_match = re.fullmatch(r'sha256:([a-f0-9]{64})', expected_digest)
    if not digest_match: raise Failure('Codex 릴리스 체크섬을 확인할 수 없습니다.', 502)
    digest = hashlib.sha256()
    with urllib.request.urlopen(url, timeout=60) as response, tempfile.TemporaryFile() as archive:
        total = 0
        while True:
            chunk = response.read(65536)
            if not chunk: break
            total += len(chunk)
            if total > 300 * LIMIT: raise Failure('Codex 설치 파일 크기 제한을 초과했습니다.', 502)
            archive.write(chunk)
            digest.update(chunk)
        if digest.hexdigest() != digest_match.group(1): raise Failure('Codex 설치 파일 무결성 검증에 실패했습니다.', 502)
        archive.seek(0)
        with tarfile.open(fileobj=archive, mode='r:gz') as tar:
            member = tar.getmember(name)
            if not member.isfile() or member.size > 500 * LIMIT: raise Failure('잘못된 Codex 설치 파일입니다.', 502)
            with tempfile.NamedTemporaryFile(dir=destination.parent, delete=False) as out:
                tmp = Path(out.name)
                try:
                    source = tar.extractfile(member)
                    while True:
                        chunk = source.read(65536)
                        if not chunk: break
                        out.write(chunk)
                    out.flush()
                    os.fchmod(out.fileno(), 0o755)
                    os.replace(tmp, destination)
                finally: tmp.unlink(missing_ok=True)


def latest_codex_release(cache, arch):
    now = time.time()
    try:
        cached = json.loads(cache.read_text())
        cached_age = now - float(cached['checkedAt'])
        if (isinstance(cached, dict) and 0 <= cached_age < 600
                and re.fullmatch(r'\d+\.\d+\.\d+', cached.get('version', ''))
                and cached.get('url', '').startswith('https://github.com/openai/codex/releases/download/')
                and re.fullmatch(r'sha256:[a-f0-9]{64}', cached.get('digest', ''))): return cached
    except (OSError, ValueError, KeyError, TypeError, AttributeError): pass
    request = urllib.request.Request(
        'https://api.github.com/repos/openai/codex/releases/latest',
        headers={'Accept': 'application/vnd.github+json', 'User-Agent': 'personal-dashboard-codex-updater'})
    with urllib.request.urlopen(request, timeout=15) as response:
        payload = response.read(LIMIT + 1)
    if len(payload) > LIMIT: raise Failure('Codex 릴리스 정보가 너무 큽니다.', 502)
    release = json.loads(payload)
    tag = release.get('tag_name', '')
    version = re.fullmatch(r'rust-v(\d+\.\d+\.\d+)', tag)
    asset_name = 'codex-' + arch + '-unknown-linux-musl.tar.gz'
    asset = next((item for item in release.get('assets', []) if item.get('name') == asset_name), None)
    if not version or not asset: raise Failure('최신 Codex stable 릴리스에 지원되는 Linux 바이너리가 없습니다.', 502)
    result = dict(version=version.group(1), url=asset.get('browser_download_url', ''),
                  digest=asset.get('digest', ''), checkedAt=now)
    if not result['url'].startswith('https://github.com/openai/codex/releases/download/'):
        raise Failure('Codex 릴리스 다운로드 주소가 올바르지 않습니다.', 502)
    if not re.fullmatch(r'sha256:[a-f0-9]{64}', result['digest']):
        raise Failure('최신 Codex 릴리스 체크섬을 확인할 수 없습니다.', 502)
    cache.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=cache.parent, delete=False) as output:
        tmp = Path(output.name)
        try:
            os.fchmod(output.fileno(), 0o600)
            json.dump(result, output)
            output.flush()
            os.replace(tmp, cache)
        finally: tmp.unlink(missing_ok=True)
    return result


def ensure_server_codex(destination, arch, cache):
    if arch not in ('x86_64', 'aarch64'): raise Failure('Codex 자동 설치는 Linux x86_64 / aarch64를 지원합니다.')
    release = latest_codex_release(cache, arch)
    installed = ''
    if destination.exists():
        try:
            output = run([str(destination), '--version'])[1].decode('utf-8', errors='replace')
            match = re.search(r'\b(\d+\.\d+\.\d+)\b', output)
            if match: installed = match.group(1)
        except Exception: pass
    def version_tuple(value): return tuple(int(part) for part in value.split('.'))
    if installed and version_tuple(installed) >= version_tuple(release['version']): return
    emit(event='Codex 최신 버전 ' + release['version'] + ' 확인, 다운로드 및 SHA256 검증 중')
    install_codex_artifact(destination, arch, release['url'], release['digest'])


def setup(device_id=None, refresh=False):
    emit(event='Codex CLI 확인 중')
    arch = os.uname().machine
    hashes = {'x86_64': 'd7e18b2597ae8f242f5f31ee9e90deef48dbc9edd634d9868fb6435d08c07f02',
              'aarch64': '583b48df32804213bdcd338c2e5adb06b34340821fa757a726cc0a524fa33c27'}
    if arch not in hashes: raise Failure('Codex 자동 설치는 Linux x86_64 / aarch64를 지원합니다.')
    destination = Path.home() / '.local/bin/codex'
    destination.parent.mkdir(parents=True, exist_ok=True)
    lockdir = Path.home() / '.cache/personal-workspace'
    lockdir.mkdir(parents=True, exist_ok=True, mode=0o700)
    with (lockdir / 'install.lock').open('w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        if device_id == 'local':
            try:
                ensure_server_codex(destination, arch, lockdir / 'codex-release.json')
            except Exception:
                if not destination.exists():
                    emit(event='최신 Codex 확인에 실패해 검증된 기본 버전을 설치합니다')
                    name = 'codex-' + arch + '-unknown-linux-musl'
                    install_codex_artifact(
                        destination, arch,
                        'https://github.com/openai/codex/releases/download/rust-v0.154.0/' + name + '.tar.gz',
                        'sha256:' + hashes[arch])
                else:
                    emit(event='최신 Codex를 확인할 수 없어 설치된 버전으로 계속합니다')
        elif not destination.exists():
            emit(event='공식 Codex 0.154.0 다운로드 및 SHA256 검증 중')
            name = 'codex-' + arch + '-unknown-linux-musl'
            install_codex_artifact(
                destination, arch,
                'https://github.com/openai/codex/releases/download/rust-v0.154.0/' + name + '.tar.gz',
                'sha256:' + hashes[arch])
        install_github_cli()
    if device_id == 'local':
        url = os.environ.get('DASHBOARD_MCP_URL', '')
        token = os.environ.get('DASHBOARD_MCP_TOKEN', '')
        if not url or len(token) < 32: raise Failure('대시보드 MCP 설정을 확인해 주세요.', 500)
        # Reconcile the MCP entry on every setup, including refreshes. Existing entries are
        # updated by the CLI, and failures must fail setup rather than silently disable tools.
        run([str(destination), 'mcp', 'remove', 'personal-dashboard'], check=False)
        run([str(destination), 'mcp', 'add', 'personal-dashboard', '--url', url,
             '--bearer-token-env-var', 'DASHBOARD_MCP_TOKEN'])
    return dict(git=git(Path.home(), '--version')[1].decode().strip(),
                codex=run([str(destination), '--version'])[1].decode().strip())


# CODEX_BRIDGE
# LOGS_BRIDGE


def handle(request):
    action, args = request['action'], request.get('args', {})
    if action == 'setup': return setup(request.get('deviceId'), args.get('refresh') is True)
    base = Path(request['base']).resolve(strict=True)
    root = within(base, request['root']).resolve(strict=True)
    if not root.is_dir(): raise Failure('작업 폴더가 아닙니다.')
    if action in ('logs-targets', 'logs-follow'): return device_logs(action, args, root)
    # Serialize editor/Git/Codex mutations across HTTP sessions on this SSH account.
    lockdir = Path.home() / '.cache/personal-workspace'
    lockdir.mkdir(parents=True, exist_ok=True, mode=0o700)
    lock = (lockdir / (hashlib.sha256(str(root).encode()).hexdigest() + '.lock')).open('w')
    try: fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError: raise Failure('이 작업 폴더에서 다른 작업이 진행 중입니다.', 409)
    with lock:
        if action == 'github-status':
            rc, unused = run(['gh', 'auth', 'status', '--hostname', 'github.com'], check=False)
            return dict(authenticated=rc == 0)
        if action == 'github-login':
            run(['gh', 'auth', 'login', '--hostname', 'github.com', '--git-protocol', 'https', '--web'], data='\n', stream=True, auth=True)
            run(['gh', 'auth', 'setup-git', '--hostname', 'github.com'])
            return dict(authenticated=True)
        if action == 'list':
            directory = file_path(root, dict(path=args.get('path', '.')))
            entries = []
            with os.scandir(directory) as listing:
                for entry in listing:
                    if entry.name == '.git' or entry.is_symlink(): continue
                    if len(entries) >= 2000: raise Failure('폴더 항목이 2,000개를 초과했습니다.', 413)
                    if not entry.is_dir() and not entry.is_file(): continue
                    entries.append(dict(name=entry.name, path=str(Path(entry.path).relative_to(root)), directory=entry.is_dir()))
            return dict(root=str(root), path=str(directory.relative_to(root)), entries=sorted(entries, key=lambda x: (not x['directory'], x['name'].lower())))
        if action == 'read': return read_file(file_path(root, args))
        if action == 'save':
            path = file_path(root, args)
            before = read_file(path)
            if before['revision'] != textarg(args, 'revision', 64): raise Failure('서버 파일이 변경되었습니다. 다시 열어 내용을 비교한 뒤 저장해 주세요.', 409)
            data = textarg(args, 'content', LIMIT, False).encode('utf-8')
            if len(data) > LIMIT: raise Failure('1 MiB를 초과했습니다.', 413)
            with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as out:
                tmp = Path(out.name)
                try:
                    out.write(data); out.flush(); os.fsync(out.fileno())
                    os.fchmod(out.fileno(), stat.S_IMODE(path.stat().st_mode))
                    if read_file(path)['revision'] != before['revision']: raise Failure('파일 저장 충돌이 발생했습니다.', 409)
                    os.replace(tmp, path)
                finally: tmp.unlink(missing_ok=True)
            return dict(revision=hashlib.sha256(data).hexdigest())
        if action in ('create', 'mkdir', 'rename', 'delete'):
            path = file_path(root, args)
            if path == root: raise Failure('작업 폴더 자체는 변경할 수 없습니다.', 403)
            if action == 'create':
                with path.open('x', encoding='utf-8'): pass
            elif action == 'mkdir': path.mkdir()
            elif action == 'rename':
                target = file_path(root, args, 'target')
                if target.exists(): raise Failure('대상 이름이 이미 존재합니다.', 409)
                path.rename(target)
            elif path.is_dir(): path.rmdir()
            else: path.unlink()
            return dict(ok=True)
        if action == 'git-init':
            rc, unused = git(root, 'rev-parse', '--show-toplevel', check=False)
            if rc == 0: raise Failure('이미 저장소 안에 있습니다.', 409)
            git(root, 'init'); return dict(ok=True)
        if action == 'git-clone':
            url = repository_url(args)
            target = file_path(root, args, 'target')
            if target.exists(): raise Failure('복제 대상은 존재하지 않는 새 폴더여야 합니다.', 409)
            git(root, 'clone', '--', url, str(target)); return dict(path=str(target))
        if action.startswith('git-'):
            repository(root)
            if action == 'git-status':
                raw = git(root, 'status', '--porcelain=v1', '-z', '--untracked-files=normal')[1].decode('utf-8', errors='replace').split('\0')
                changes, i = [], 0
                while i < len(raw) and raw[i]:
                    entry = raw[i]; i += 1
                    old = ''
                    if 'R' in entry[:2] or 'C' in entry[:2]: old = raw[i]; i += 1
                    changes.append(dict(index=entry[0], worktree=entry[1], path=entry[3:], oldPath=old))
                branches = git(root, 'for-each-ref', '--format=%(refname:short)', 'refs/heads')[1].decode().splitlines()
                branch = git(root, 'symbolic-ref', '--short', '-q', 'HEAD', check=False)[1].decode().strip() or '(detached)'
                history = git(root, 'log', '-15', '--format=%h %s', check=False)[1].decode(errors='replace')
                return dict(branch=branch, branches=branches, changes=changes, history=clean(history))
            if action in ('git-stage', 'git-unstage', 'git-diff'):
                path = file_path(root, args)
                relative = str(path.relative_to(root))
                if action == 'git-stage': git(root, 'add', '--', relative)
                elif action == 'git-unstage':
                    has_head = git(root, 'rev-parse', '--verify', 'HEAD', check=False)[0] == 0
                    git(root, *(['restore', '--staged'] if has_head else ['rm', '--cached', '--ignore-unmatch']), '--', relative)
                else:
                    if args.get('untracked'):
                        return dict(diff=clean(read_file(path)['content']))
                    return dict(diff=clean(git(root, 'diff', '--no-ext-diff', '--no-textconv', *(['--cached'] if args.get('staged') else []), '--', relative)[1].decode(errors='replace')))
            elif action == 'git-commit': git(root, 'commit', '-m', textarg(args, 'message', 4000))
            elif action in ('git-branch', 'git-switch'):
                branch = textarg(args, 'branch', 200)
                git(root, 'check-ref-format', '--branch', branch)
                git(root, 'switch', *(['-c', branch] if action == 'git-branch' else ['--', branch]))
            elif action == 'git-identity':
                git(root, 'config', '--local', 'user.name', textarg(args, 'name', 200))
                git(root, 'config', '--local', 'user.email', textarg(args, 'email', 200))
            elif action == 'git-remote':
                url = repository_url(args)
                exists = git(root, 'remote', 'get-url', 'origin', check=False)[0] == 0
                git(root, 'remote', 'set-url' if exists else 'add', 'origin', url)
            elif action == 'git-fetch': git(root, 'fetch', '--all')
            elif action == 'git-pull': git(root, 'pull', '--ff-only')
            elif action == 'git-push':
                upstream = git(root, 'rev-parse', '--abbrev-ref', '@{upstream}', check=False)[0] == 0
                git(root, 'push', *([] if upstream else ['--set-upstream', 'origin', 'HEAD']))
            else: raise Failure('지원하지 않는 Git 작업입니다.')
            return dict(ok=True)
        if action == 'codex-status':
            rc, unused = run(['codex', 'login', 'status'], check=False)
            return dict(authenticated=rc == 0, version=run(['codex', '--version'])[1].decode().strip())
        if action == 'codex-login':
            run(['codex', 'login', '--device-auth'], stream=True, auth=True)
            return dict(authenticated=True)
        if action == 'codex-logout':
            run(['codex', 'logout']); return dict(authenticated=False)
        if action.startswith('codex-'): return codex_action(root, action, args)
        raise Failure('지원하지 않는 작업입니다.')


def main():
    try:
        request = json.loads(sys.stdin.readline(8 * LIMIT + 1))
        signal.signal(signal.SIGTERM, stop)
        threading.Thread(target=disconnected, daemon=True).start()
        timer = threading.Timer(900, stop); timer.daemon = True; timer.start()
        emit(result=handle(request))
    except Failure as exc: emit(error=exc.message, status=exc.status)
    except FileNotFoundError: emit(error='파일 또는 CLI가 없습니다. 경로를 확인하거나 도구 준비를 다시 실행해 주세요.', status=404)
    except FileExistsError: emit(error='같은 이름이 이미 존재합니다.', status=409)
    except PermissionError: emit(error='원격 SSH 계정에 접근 권한이 없습니다.', status=403)
    except Exception: emit(error='원격 작업이 실패했습니다. 폴더 상태·설치 권한·외부 네트워크를 확인해 주세요.', status=502)


if __name__ == '__main__': main()
