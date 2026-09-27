#!/bin/sh
set -eu

case "${NAS_USERNAME:-}" in
  [A-Za-z0-9]* ) ;;
  *) echo 'NAS_USERNAME must start with a letter or digit.' >&2; exit 1 ;;
esac
case "$NAS_USERNAME" in
  *[!A-Za-z0-9_.-]*) echo 'NAS_USERNAME must use only letters, digits, dot, underscore, or hyphen.' >&2; exit 1 ;;
esac
if [ "${#NAS_USERNAME}" -gt 32 ] || [ -z "${NAS_PASSWORD:-}" ]; then
  echo 'NAS_USERNAME must be at most 32 characters and NAS_PASSWORD must be set.' >&2
  exit 1
fi

if ! getent group nas-storage >/dev/null; then
  groupadd --gid "$NAS_GID" nas-storage
fi
if ! getent passwd "$NAS_USERNAME" >/dev/null; then
  useradd --uid "$NAS_UID" --gid "$NAS_GID" --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin "$NAS_USERNAME"
fi
if pdbedit -L | awk -F: -v username="$NAS_USERNAME" '$1 == username { found = 1 } END { exit !found }'; then
  printf '%s\n%s\n' "$NAS_PASSWORD" "$NAS_PASSWORD" | smbpasswd -s "$NAS_USERNAME" >/dev/null
else
  printf '%s\n%s\n' "$NAS_PASSWORD" "$NAS_PASSWORD" | smbpasswd -s -a "$NAS_USERNAME" >/dev/null
fi
unset NAS_PASSWORD

sed "s/@NAS_USERNAME@/$NAS_USERNAME/g" /etc/samba/smb.conf > /run/smb.conf
testparm -s /run/smb.conf >/dev/null
exec smbd --foreground --no-process-group --configfile=/run/smb.conf --debug-stdout
