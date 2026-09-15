#!/bin/bash
set -eu
umask 077
mkdir -p "$XDG_RUNTIME_DIR" "$HOME/.config/openbox" "$HOME/.config/fcitx5" "$HOME/Downloads"
for item in openbox/menu.xml fcitx5/profile fcitx5/config; do
    if [ ! -f "$HOME/.config/$item" ]; then cp "/opt/wine-config/$item" "$HOME/.config/$item"; fi
done
Xvfb :2 -screen 0 1280x900x24 -nolisten tcp &
desktop_pid=$!
trap 'wineserver -k 2>/dev/null || true; kill "$desktop_pid" 2>/dev/null || true' EXIT INT TERM
for attempt in $(seq 1 30); do
    if xdpyinfo >/dev/null 2>&1; then break; fi
    sleep 1
done
openbox &
xcompmgr -n &
x11vnc -display "$DISPLAY" -rfbport 5902 -localhost -forever -shared -nopw -noxdamage -quiet &
fcitx5 -d >/dev/null 2>&1
if [ ! -f "$WINEPREFIX/system.reg" ]; then
    WINEDLLOVERRIDES='mscoree,mshtml=' wineboot -u >"$HOME/provision.log" 2>&1
    wineserver -w
    wine reg add 'HKCU\Software\Wine\Fonts\Replacements' /v 'Malgun Gothic' /d 'NanumGothic' /f >>"$HOME/provision.log" 2>&1
fi
/usr/local/bin/launch-kakao &
wait "$desktop_pid"
