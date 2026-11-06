#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
core_root="$(cd "$repo_root/../nexo-core" && pwd)"

if [[ ! -f "$repo_root/.env" ]]; then
  echo "Crie .env a partir de .env.example e informe uma senha local de demonstração."
  exit 2
fi

set -a
# shellcheck disable=SC1091
source "$repo_root/.env"
set +a

mvn -f "$core_root/pom.xml" test install
mvn -f "$repo_root/backend/pom.xml" test
npm --prefix "$repo_root/frontend" ci
npm --prefix "$repo_root/frontend" run build
