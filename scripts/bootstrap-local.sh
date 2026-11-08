#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
core_root="${NEXO_CORE_DIR:-$repo_root/../nexo-core}"
if [[ ! -f "$core_root/pom.xml" ]]; then
  echo "NEXO Core não encontrado em $core_root. Defina NEXO_CORE_DIR com o caminho do repositório nexo-core." >&2
  exit 2
fi
core_root="$(cd "$core_root" && pwd)"

if [[ ! -f "$repo_root/.env" ]]; then
  cp "$repo_root/.env.example" "$repo_root/.env"
  chmod 600 "$repo_root/.env"
  python3 - "$repo_root/.env" <<'PY'
from pathlib import Path
import secrets
import sys

env_file = Path(sys.argv[1])
content = env_file.read_text()
content = content.replace("NEXOGOV_DEMO_PASSWORD=troque-esta-senha-local", f"NEXOGOV_DEMO_PASSWORD={secrets.token_urlsafe(24)}")
content = content.replace("NEXOGOV_DEMO_MEMBER_PASSWORD=troque-esta-senha-local", f"NEXOGOV_DEMO_MEMBER_PASSWORD={secrets.token_urlsafe(24)}")
env_file.write_text(content)
PY
  echo "Criado .env local ignorado pelo Git, com senhas aleatórias para as contas fictícias de demonstração."
fi

set -a
# shellcheck disable=SC1091
source "$repo_root/.env"
set +a

mvn -f "$core_root/pom.xml" test install
mvn -f "$repo_root/backend/pom.xml" test
npm --prefix "$repo_root/frontend" ci
npm --prefix "$repo_root/frontend" run build
