#!/usr/bin/env bash
set -euo pipefail
umask 077

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if (( $# > 0 )); then
  if [[ "$1" == "--help" || "$1" == "-h" ]]; then
    cat <<'HELP'
Uso: ./dev evidence

Executa somente pares exatos spec/cenário revisados para captura segura,
em ambiente Compose descartável. Exporta PNG/manifesto em Documentos; vídeo está desligado.
Não aceita filtros: isso evita incluir cenários com tokens/chaves bearer.
Requer Docker e Chrome.
HELP
    exit 0
  fi
  echo "./dev evidence não aceita filtros; a lista gravável é controlada no config Playwright." >&2
  exit 2
fi

python3 -m unittest discover -s "$repo_root/scripts/tests" -p 'test_export_e2e_evidence.py'

if ! command -v xdg-user-dir >/dev/null 2>&1; then
  echo "xdg-user-dir é necessário para localizar a pasta Documentos do usuário." >&2
  exit 2
fi
documents_dir="$(xdg-user-dir DOCUMENTS)"
if [[ -z "$documents_dir" || "$documents_dir" == "$HOME" ]]; then
  echo "Não foi possível localizar a pasta Documentos do usuário." >&2
  exit 2
fi

evidence_root="$(realpath -m "$documents_dir/NexoGov-Evidencias/PE-91-2025")"
repo_real="$(realpath "$repo_root")"
case "$evidence_root/" in
  "$repo_real/"*) echo "A pasta Documentos resolve dentro do checkout; exportação recusada." >&2; exit 2 ;;
esac
mkdir -p "$(dirname "$evidence_root")"
if [[ -e "$evidence_root" && "$(stat -c '%u' "$evidence_root")" != "$(id -u)" ]]; then
  echo "A pasta de evidências existente não pertence ao usuário atual; exportação recusada." >&2
  exit 2
fi
mkdir -p -m 700 "$evidence_root"
chmod 700 "$evidence_root"

run_id="$(date -u +%Y%m%dT%H%M%SZ)-$$"
destination="$evidence_root/$run_id"
if ! mkdir -m 700 "$destination"; then
  echo "A pasta desta execução já existe; exportação recusada sem sobrescrever dados." >&2
  exit 2
fi
staging="$(mktemp -d "${TMPDIR:-/tmp}/nexogov-evidence.XXXXXXXX")"
case "$staging" in
  "${TMPDIR:-/tmp}"/nexogov-evidence.*) ;;
  *) echo "Diretório temporário inesperado; encerrando." >&2; exit 2 ;;
esac
cleanup() {
  rm -rf -- "$staging"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

export NEXOGOV_EVIDENCE_ARTIFACTS_DIR="$staging/playwright-artifacts"
# Playwright's JSON reporter copies attachment buffers under the report's
# sibling `data/` directory. Keep the report there too, inside the private
# artifact root, so the exporter can validate every resolved path beneath a
# single staging boundary without rejecting the reporter's own copies.
export NEXOGOV_EVIDENCE_JSON_REPORT="$NEXOGOV_EVIDENCE_ARTIFACTS_DIR/evidence-report.json"
export NEXOGOV_EVIDENCE_MODE=1
mkdir -p "$NEXOGOV_EVIDENCE_ARTIFACTS_DIR"

set +e
"$repo_root/scripts/run-e2e.sh" --config=playwright.evidence.config.ts
runner_exit_code=$?
set -e

set +e
python3 "$repo_root/scripts/export-e2e-evidence.py" \
  --report "$NEXOGOV_EVIDENCE_JSON_REPORT" \
  --artifact-root "$NEXOGOV_EVIDENCE_ARTIFACTS_DIR" \
  --matrix "$repo_root/docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv" \
  --destination "$destination" \
  --run-id "$run_id" \
  --runner-exit-code "$runner_exit_code"
exporter_exit_code=$?
set -e

if (( exporter_exit_code != 0 )); then
  case "$destination" in
    "$evidence_root/$run_id") rm -rf -- "$destination" ;;
    *) echo "Destino de execução inesperado; não foi removido." >&2 ;;
  esac
  exit "$exporter_exit_code"
fi

exit "$runner_exit_code"
