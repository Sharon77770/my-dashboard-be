"""Bounded, read-only Docker history collection; stdout is a single JSON envelope."""
import codecs
from collections import deque
import json
import os
import re
import selectors
import subprocess
import sys
import time

ERROR = re.compile(r'\b(?:error|exception|traceback|fatal|panic|critical|5\d\d)\b', re.I)


def redact(text):
    """Mask common credentials before returning application-controlled log text."""
    text = re.sub(r'\x1b\[[0-9;]*[A-Za-z]', '', text)
    text = re.sub(r'(https?://)[^\s/@]+:[^\s/@]+@', r'\1[redacted]@', text)
    text = re.sub(r'(?i)\b(?:Bearer|Basic)\s+[^\s,"\'}]+', '[redacted]', text)
    text = re.sub(r'(?i)(["\']?(?:password|passwd|secret|token|access_token|refresh_token|api[_-]?key|authorization|cookie|set-cookie)["\']?\s*[:=]\s*)(?:"[^"]*"|\'[^\']*\'|[^\s,;]+)', r'\1[redacted]', text)
    return re.sub(r'\b(?:sk-[A-Za-z0-9_-]{8,}|gh[pousr]_[A-Za-z0-9_]{8,})', '[redacted]', text)


def collect(container, since, until, errors_only):
    """Scan history before filtering so old failures survive a busy successful log tail."""
    command = ['docker', 'logs', '--timestamps', '--since', since, '--until', until, container]
    rows, characters, scanned, matched, size = deque(), 0, 0, 0, 0
    truncated, scan_complete, first, last = False, False, '', ''
    decoder = codecs.getincrementaldecoder('utf-8')(errors='replace')
    pending, context = '', 0

    def retain(line):
        nonlocal characters, scanned, matched, truncated, first, last, context
        scanned += 1
        stamp = line.split(' ', 1)[0]
        if re.fullmatch(r'\d{4}-\d\d-\d\dT[\d:.]+Z', stamp):
            if not first: first = stamp
            last = stamp
        is_error = bool(ERROR.search(line))
        if is_error: matched += 1
        if not errors_only or is_error or context:
            safe = redact(line)
            if len(safe) > 12000: safe = safe[:12000]; truncated = True
            rows.append(safe)
            characters += len(safe) + 1
            while characters > 24000 or len(rows) > 500:
                characters -= len(rows.popleft()) + 1
                truncated = True
        context = 8 if is_error else max(0, context - 1)

    try:
        process = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, start_new_session=True)
    except OSError:
        return dict(error='Docker 로그 도구를 실행하지 못했습니다. 설치와 SSH 계정을 확인하세요.')
    try:
        deadline = time.monotonic() + 7
        with selectors.DefaultSelector() as selector:
            selector.register(process.stdout, selectors.EVENT_READ)
            while time.monotonic() < deadline and size < 8 * 1024 * 1024:
                if not selector.select(.1): continue
                chunk = os.read(process.stdout.fileno(), 16384)
                size += len(chunk)
                pending += decoder.decode(chunk, final=not chunk)
                lines = pending.split('\n'); pending = lines.pop()
                for line in lines: retain(line)
                if len(pending) > 24000:
                    retain(pending[:12000]); pending = ''; truncated = True
                if not chunk:
                    if pending: retain(pending)
                    scan_complete = True
                    break
        if scan_complete and process.wait(timeout=.5) != 0:
            return dict(error='Docker 로그 조회에 실패했습니다. 컨테이너 존재 여부, 데몬 연결, 로그 드라이버와 SSH 계정 권한을 확인하세요.')
        return dict(output='\n'.join(rows), scannedLines=scanned, matchedLines=matched,
                    truncated=truncated or not scan_complete, scanComplete=scan_complete,
                    firstTimestamp=first, lastTimestamp=last)
    finally:
        if process.poll() is None: process.kill()
        process.wait()
        process.stdout.close()


if __name__ == '__main__':
    print(json.dumps(collect(*sys.argv[1:4], sys.argv[4] == 'errors'), ensure_ascii=True))
