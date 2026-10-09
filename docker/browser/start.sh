#!/bin/sh
set -eu
mkdir -p /home/browser/profile
# Hold a volume-scoped lease before removing stale Chromium singleton metadata.
# The profile contents and saved logins are never removed.
exec 9>/home/browser/profile/.workspace-browser.lock
flock -n 9 || { echo 'Browser profile is already owned by another service.' >&2; exit 1; }
rm -f /home/browser/profile/SingletonLock /home/browser/profile/SingletonCookie /home/browser/profile/SingletonSocket
if [ -f /tmp/.X1-lock ]; then
    display_pid=$(tr -d ' ' </tmp/.X1-lock)
    if ! kill -0 "$display_pid" 2>/dev/null; then rm -f /tmp/.X1-lock /tmp/.X11-unix/X1; fi
fi
children=''
cleanup() { trap - EXIT INT TERM; for child in $children; do kill "$child" 2>/dev/null || true; done; wait 2>/dev/null || true; }
trap cleanup EXIT
trap 'exit 0' INT TERM
# Shared dashboard networking must not expose the unauthenticated desktop/CDP ports.
browser_bind=0.0.0.0
vnc_local_only=no
if [ "${BROWSER_LOCAL_ONLY:-false}" = "true" ]; then
    browser_bind=127.0.0.1
    vnc_local_only=yes
fi
Xvnc :1 -geometry 1600x900 -depth 24 -SecurityTypes None -localhost "$vnc_local_only" -nolisten tcp -AlwaysShared &
vnc_pid=$!
children="$vnc_pid"
sleep 1
openbox &
children="$children $!"
socat "TCP-LISTEN:9223,reuseaddr,fork,bind=$browser_bind" TCP:127.0.0.1:9222 &
children="$children $!"
chromium --no-sandbox --disable-dev-shm-usage --no-first-run --password-store=basic --user-data-dir=/home/browser/profile --remote-debugging-port=9222 --remote-allow-origins=http://browser:9223 about:blank &
browser_pid=$!
children="$children $browser_pid"
# A Chromium error dialog keeps the process alive without providing CDP.
# Fail startup rather than leaving a seemingly running but unusable browser.
attempt=0
until curl -fsS --max-time 1 http://127.0.0.1:9223/json/version >/dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 30 ] || ! kill -0 "$browser_pid" 2>/dev/null; then
        echo 'Chromium CDP did not become ready on loopback port 9223.' >&2
        exit 1
    fi
    sleep 1
done
wait "$browser_pid"
