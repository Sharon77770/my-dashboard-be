"""Fixed Kakao executable in a profile-owned prefix; no caller-supplied commands."""
import os
from pathlib import Path
import subprocess

profile = Path(os.environ['COMMUNICATION_PROFILE'])
prefix = Path(os.environ['WINEPREFIX'])
state = profile / '.runtime-state'

def run(arguments, timeout=None):
    return subprocess.run(arguments, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=timeout, check=True)

def executable():
    return next((prefix / 'drive_c' / folder / 'Kakao/KakaoTalk/KakaoTalk.exe'
                 for folder in ('Program Files (x86)', 'Program Files')
                 if (prefix / 'drive_c' / folder / 'Kakao/KakaoTalk/KakaoTalk.exe').is_file()), None)

try:
    state.write_text('STARTING')
    os.environ['WINEDLLOVERRIDES'] = 'mscoree,mshtml='
    if not (prefix / 'system.reg').exists():
        run(['wineboot', '-u'], 120)
        run(['wineserver', '-w'], 120)
        run(['wine', 'reg', 'add', r'HKCU\Software\Wine\Fonts\Replacements', '/v', 'Malgun Gothic', '/d', 'NanumGothic', '/f'], 20)
    if not executable():
        state.write_text('INSTALLING')
        # Installation is initiated by the owner's explicit open-profile request.
        run(['wine', '/opt/kakaotalk/setup.exe', '/S'], 180)
    app = executable()
    if not app:
        raise RuntimeError('Application missing')
    state.write_text('RUNNING')
    run(['wine', str(app)])
    run(['wineserver', '-w'])
except Exception:
    state.write_text('ERROR')
