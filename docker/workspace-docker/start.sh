#!/bin/sh
set -eu
# Official entrypoint generates TLS certificates before starting dockerd.
dockerd-entrypoint.sh "$@" &
daemon_pid=$!
trap 'kill -TERM "$daemon_pid" 2>/dev/null || true' TERM INT
attempt=0
until [ -s /certs/client/key.pem ]; do
  kill -0 "$daemon_pid" 2>/dev/null || { wait "$daemon_pid"; exit 1; }
  attempt=$((attempt + 1))
  [ "$attempt" -lt 60 ] || { kill "$daemon_pid"; echo 'Docker TLS initialization timed out' >&2; exit 1; }
  sleep 1
done
chown -R 10001:10001 /certs/client
chmod 0700 /certs/client
chmod 0600 /certs/client/key.pem
wait "$daemon_pid"