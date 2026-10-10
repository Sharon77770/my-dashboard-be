"""Run inside an isolated Linux image with TigerVNC and libX11; no extra packages required."""
import ctypes as c
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time

helper = Path(sys.argv[1] if len(sys.argv) > 1 else 'src/main/resources/remote-desktop/clipboard.py').resolve()
x = c.CDLL('libX11.so.6')
for name, arguments, result in [
    ('XOpenDisplay', [c.c_char_p], c.c_void_p),
    ('XDefaultRootWindow', [c.c_void_p], c.c_ulong),
    ('XCreateSimpleWindow', [c.c_void_p,c.c_ulong,c.c_int,c.c_int,c.c_uint,c.c_uint,c.c_uint,c.c_ulong,c.c_ulong], c.c_ulong),
    ('XInternAtom', [c.c_void_p,c.c_char_p,c.c_int], c.c_ulong),
    ('XConvertSelection', [c.c_void_p,c.c_ulong,c.c_ulong,c.c_ulong,c.c_ulong,c.c_ulong], c.c_int),
    ('XFlush', [c.c_void_p], c.c_int),
    ('XFree', [c.c_void_p], c.c_int),
    ('XCloseDisplay', [c.c_void_p], c.c_int),
    ('XGetWindowProperty', [c.c_void_p,c.c_ulong,c.c_ulong,c.c_long,c.c_long,c.c_int,c.c_ulong,c.POINTER(c.c_ulong),c.POINTER(c.c_int),c.POINTER(c.c_ulong),c.POINTER(c.c_ulong),c.POINTER(c.c_void_p)], c.c_int),
]:
    function = getattr(x, name)
    function.argtypes, function.restype = arguments, result

with tempfile.TemporaryDirectory(prefix='clipboard-test-') as home:
    root = Path(home) / '.personal-dashboard-desktop'
    root.mkdir(mode=0o700)
    (root/'passwd').touch()
    display_number = next(number for number in range(90,100) if not Path(f'/tmp/.X{number}-lock').exists() and not Path(f'/tmp/.X11-unix/X{number}').exists())
    port = 5900 + display_number
    process = subprocess.Popen(['Xvnc',f':{display_number}','-SecurityTypes','None','-PasswordFile',str(root/'passwd'),'-localhost','yes','-nolisten','tcp','-geometry','800x600'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    display = None
    try:
        (root/'state.json').write_text(json.dumps({'port':port,'pid':process.pid}))
        for attempt in range(30):
            display = x.XOpenDisplay(f':{display_number}'.encode())
            if display:
                break
            assert process.poll() is None, 'VNC failed to start'
            time.sleep(.1)
        assert display, 'Display not ready'
        window = x.XCreateSimpleWindow(display,x.XDefaultRootWindow(display),0,0,1,1,0,0,0)
        clip, utf, prop = [x.XInternAtom(display,name,0) for name in (b'CLIPBOARD',b'UTF8_STRING',b'PROBE')]
        def send(text, target_port=port):
            return subprocess.run([sys.executable,str(helper)],input=json.dumps({'port':target_port,'text':text}),text=True,capture_output=True,timeout=10,env=dict(os.environ,HOME=home))
        def read():
            x.XConvertSelection(display,clip,utf,prop,window,0)
            x.XFlush(display)
            time.sleep(.2)
            kind, fmt, count, rest, data = c.c_ulong(),c.c_int(),c.c_ulong(),c.c_ulong(),c.c_void_p()
            x.XGetWindowProperty(display,window,prop,0,100000,1,0,c.byref(kind),c.byref(fmt),c.byref(count),c.byref(rest),c.byref(data))
            assert kind.value == utf and rest.value == 0
            try:
                return c.string_at(data,count.value).decode('utf-8') if count.value else ''
            finally:
                if data.value:
                    x.XFree(data)
        for text in ('ASCII clipboard', '한글😀\n두 번째 줄', '한글😀'*6000, ''):
            result = send(text)
            assert result.returncode == 0 and result.stdout.strip() == 'READY'
            assert read() == text
        assert send('wrong target',port-1).returncode != 0
        assert send('x'*32001).returncode != 0
        assert send('NUL\0text').returncode != 0
        (root/'state.json').write_text(json.dumps({'port':port,'pid':os.getpid()}))
        assert send('wrong process').returncode != 0
        print('PASS: actual X11 ASCII, Unicode, emoji, multiline, large and empty clipboard; target, process and input rejection')
    finally:
        if display:
            x.XCloseDisplay(display)
        process.terminate()
        process.wait(timeout=5)
