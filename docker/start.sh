#!/bin/sh
set -eu
# Initialize the persistent shell home even when reusing an existing data volume.
mkdir -p "$HOME/.ssh"
chmod 700 "$HOME" "$HOME/.ssh"
exec java -jar /app/app.jar
