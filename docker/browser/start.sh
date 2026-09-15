#!/bin/sh
set -eu
mkdir -p /home/browser/profile
# Shared dashboard networking must not expose the unauthenticated desktop/CDP ports.
browser_bind=0.0.0.0
vnc_local_only=no
if [ "${BROWSER_LOCAL_ONLY:-false}" = "true" ]; then
    browser_bind=127.0.0.1
    vnc_local_only=yes
fi
Xvnc :1 -geometry 1600x900 -depth 24 -SecurityTypes None -localhost "$vnc_local_only" -nolisten tcp -AlwaysShared &
vnc_pid=$!
trap 'kill "$vnc_pid" 2>/dev/null || true' EXIT INT TERM
sleep 1
openbox &
socat "TCP-LISTEN:9223,reuseaddr,fork,bind=$browser_bind" TCP:127.0.0.1:9222 &
chromium --no-sandbox --disable-dev-shm-usage --no-first-run --password-store=basic --user-data-dir=/home/browser/profile --remote-debugging-port=9222 --remote-allow-origins=http://browser:9223 about:blank &
browser_pid=$!
wait "$browser_pid"
