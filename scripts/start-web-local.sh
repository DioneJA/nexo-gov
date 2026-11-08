#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ ! -f "$repo_root/.env" ]]; then
  echo "Execute ./dev setup para criar o ambiente local primeiro." >&2
  exit 2
fi

set -a
# shellcheck disable=SC1091
source "$repo_root/.env"
set +a

export VITE_API_URL="${VITE_API_URL:-http://localhost:${NEXOGOV_API_PORT:-8080}/api/v1}"
export VITE_STAFF_AUTH_MODE=local-basic
exec npm --prefix "$repo_root/frontend" run dev -- --host 127.0.0.1 --port "${NEXOGOV_WEB_PORT:-9000}" --strictPort
