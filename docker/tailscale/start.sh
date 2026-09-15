#!/bin/sh
set -eu
umask 077
mkdir -p /var/run/tailscale "${TS_STATE_DIR:-/var/lib/tailscale}"
key_file=/var/run/tailscale/dashboard-auth.key
rm -f "$key_file"
if [ -n "${TS_AUTHKEY:-}" ]; then
    printf '%s' "$TS_AUTHKEY" > "$key_file"
fi
unset TS_AUTHKEY
tailscaled --state="${TS_STATE_DIR:-/var/lib/tailscale}/tailscaled.state" \
    --socket=/var/run/tailscale/tailscaled.sock --tun=tailscale0 &
daemon_pid=$!
bridge_pid=
cleanup() {
    rm -f "$key_file"
    if [ -n "$bridge_pid" ]; then kill "$bridge_pid" 2>/dev/null || true; fi
    kill "$daemon_pid" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 0' INT TERM
(
    for attempt in $(seq 1 30); do
        state=$(tailscale status --json 2>/dev/null | sed -n 's/.*"BackendState": "\([^"]*\)".*/\1/p')
        case "$state" in
            NeedsLogin|Running|Stopped|NeedsMachineAuth) break ;;
        esac
        sleep 1
    done
    set -- --hostname="${TS_HOSTNAME:-personal-dashboard}" \
        --accept-dns="${TS_ACCEPT_DNS:-true}" --accept-routes="${TS_ACCEPT_ROUTES:-false}" --timeout=30s
    if [ -f "$key_file" ] && [ "$state" = NeedsLogin ]; then
        set -- "$@" --auth-key="file:$key_file"
    fi
    # Login URLs and external authentication details must not enter container logs.
    if ! tailscale up "$@" >/dev/null 2>&1; then
        echo 'Tailscale authentication is pending or failed; local services remain available.'
    fi
    rm -f "$key_file"
)
python3 /opt/dashboard-tailscale/bridge.py &
bridge_pid=$!
wait "$daemon_pid"
