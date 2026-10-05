#!/bin/sh
set -eu
export PYTHONDONTWRITEBYTECODE=1
# Isolated local SSH fixture only; no production credentials or dashboard volume.
export LOCAL_SSH_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_hex(24))')"
printf 'codexcheck:%s\n' "$LOCAL_SSH_PASSWORD" | chpasswd
if [ "${RUN_LOCAL_SSH_INSTALL:-false}" = true ]; then
  useradd -m -s /bin/bash codexinstall
  printf 'codexinstall:%s\n' "$LOCAL_SSH_PASSWORD" | chpasswd
fi
printf '#!/bin/sh\nexec /usr/bin/docker --host tcp://dashboard-log-proxy:2375 "$@"\n' > /usr/local/bin/docker
chmod 755 /usr/local/bin/docker
/usr/sbin/sshd
export LOCAL_SSH_FINGERPRINT="$(ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub | awk '{print $2}')"
export RUN_LOCAL_SSH_TEST=true
mkdir -p /home/codexcheck/.local/bin
python3 - <<'PY'
import ast
from pathlib import Path
tree = ast.parse(Path('src/test/python/test_codex_bridge.py').read_text())
fixture = next(ast.literal_eval(n.value) for n in tree.body if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'FIXTURE' for t in n.targets))
path = Path('/home/codexcheck/.local/bin/codex')
path.write_text(fixture)
path.chmod(0o755)
PY
chown -R codexcheck:codexcheck /home/codexcheck/.local
python3 -m unittest discover -s src/test/python -v
if [ "$#" -eq 0 ]; then set -- verify; fi
mvn -B "$@"
