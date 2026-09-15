#!/bin/sh
# Noninteractive prerequisites; package manager output can contain private mirror URLs.
set -eu
export PATH="$HOME/.local/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"
[ "$(uname -s)" = Linux ] || { printf '%s\n' '{"error":"Linux SSH 서버만 지원합니다.","status":400}'; exit 1; }
if ! command -v python3 >/dev/null || ! command -v git >/dev/null; then
  printf '%s\n' '{"event":"Git · Python 설치 중"}'
  elevate=''
  if [ "$(id -u)" != 0 ]; then
    if command -v sudo >/dev/null && sudo -n true 2>/dev/null; then elevate='sudo -n';
    else printf '%s\n' '{"error":"Git/Python 자동 설치에는 root 또는 비밀번호 없는 sudo가 필요합니다. 서버에서 git python3 ca-certificates를 설치한 뒤 다시 여세요.","status":409}'; exit 1; fi
  fi
  if command -v apt-get >/dev/null; then
    $elevate env DEBIAN_FRONTEND=noninteractive apt-get update >/dev/null 2>&1 && $elevate env DEBIAN_FRONTEND=noninteractive apt-get install -y git python3 ca-certificates >/dev/null 2>&1
  elif command -v dnf >/dev/null; then $elevate dnf install -y git python3 ca-certificates >/dev/null 2>&1
  elif command -v apk >/dev/null; then $elevate apk add git python3 ca-certificates >/dev/null 2>&1
  else printf '%s\n' '{"error":"지원하지 않는 패키지 관리자입니다. git python3 ca-certificates를 설치해 주세요.","status":409}'; exit 1; fi
fi
