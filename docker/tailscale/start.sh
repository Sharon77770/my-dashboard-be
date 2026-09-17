#!/bin/sh
set -eu
umask 077
mkdir -p /var/run/tailscale "${TS_STATE_DIR:-/var/lib/tailscale}"
# Authentication begins only through the user's explicit login request.
unset TS_AUTHKEY
tailscaled --state="${TS_STATE_DIR:-/var/lib/tailscale}/tailscaled.state" \
    --socket=/var/run/tailscale/tailscaled.sock --tun=tailscale0 &
daemon_pid=$!
bridge_pid=
cleanup() {
    if [ -n "$bridge_pid" ]; then kill "$bridge_pid" 2>/dev/null || true; fi
    kill "$daemon_pid" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 0' INT TERM
# tailscaled restores existing state; never start unattended authentication here.
python3 /opt/dashboard-tailscale/bridge.py &
bridge_pid=$!
wait "$daemon_pid"
