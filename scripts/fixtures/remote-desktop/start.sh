#!/bin/sh
set -eu
printf 'tester:%s\n' "$REMOTE_TEST_PASSWORD" | chpasswd
unset REMOTE_TEST_PASSWORD
exec /usr/sbin/sshd -D -e
