#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
core_root="${NEXO_CORE_DIR:-$repo_root/../nexo-core}"
with_e2e=0
if [[ "${1:-}" == "--with-e2e" ]]; then with_e2e=1; fi
frontend_build_dir="$(mktemp -d "${TMPDIR:-/tmp}/nexogov-verify-frontend.XXXXXX")"
trap 'rm -rf "$frontend_build_dir"' EXIT

if [[ ! -f "$core_root/pom.xml" ]]; then
  echo "NEXO Core não encontrado em $core_root. Defina NEXO_CORE_DIR." >&2
  exit 2
fi

python3 "$repo_root/scripts/check-core-version.py" "$core_root"
mvn -B -f "$core_root/pom.xml" test install
mvn -B -f "$repo_root/backend/pom.xml" test
npm --prefix "$repo_root/frontend" ci
npm --prefix "$repo_root/frontend" run build -- --outDir "$frontend_build_dir"
python3 "$repo_root/scripts/check-matrix.py"

if [[ "$with_e2e" == 1 ]]; then
  "$repo_root/scripts/run-e2e.sh"
  NEXOGOV_E2E_AUTH_MODE=oidc "$repo_root/scripts/run-e2e.sh" --grep @oidc
fi
