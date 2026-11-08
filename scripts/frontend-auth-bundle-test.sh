#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
temporary_root="$(mktemp -d "${TMPDIR:-/tmp}/nexogov-auth-bundle.XXXXXX")"
trap 'rm -rf "$temporary_root"' EXIT

npm --prefix "$repo_root/frontend" run build -- --outDir "$temporary_root/build"
python3 "$repo_root/scripts/check-frontend-auth-bundle.py" "$temporary_root/build"
