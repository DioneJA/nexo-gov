#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -z "${NEXOGOV_E2E_DB_PORT:-}" || -z "${NEXOGOV_E2E_API_PORT:-}" || -z "${NEXOGOV_E2E_WEB_PORT:-}" || -z "${NEXOGOV_E2E_OIDC_PORT:-}" || -z "${NEXOGOV_E2E_STORAGE_PORT:-}" ]]; then
  read -r db_port api_port web_port oidc_port storage_port < <(python3 "$repo_root/scripts/find-free-ports.py")
  export NEXOGOV_E2E_DB_PORT="$db_port"
  export NEXOGOV_E2E_API_PORT="$api_port"
  export NEXOGOV_E2E_WEB_PORT="$web_port"
  export NEXOGOV_E2E_OIDC_PORT="$oidc_port"
  export NEXOGOV_E2E_STORAGE_PORT="$storage_port"
fi
# The Playwright config owns the per-run Compose identity. Never let a caller
# supply a project name that teardown could later remove.
unset NEXOGOV_E2E_COMPOSE_PROJECT
if [[ "${NEXOGOV_E2E_AUTH_MODE:-local-basic}" == "oidc" ]]; then
  export VITE_STAFF_AUTH_MODE=oidc
else
  export VITE_STAFF_AUTH_MODE=local-basic
fi

npm --prefix "$repo_root/frontend" run e2e -- "$@"
