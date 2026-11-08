#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
core_root="${NEXO_CORE_DIR:-$repo_root/../nexo-core}"
failed=0

check_command() {
  local label="$1" command="$2" required="$3"
  if command -v "$command" >/dev/null 2>&1; then
    printf 'OK    %-22s %s\n' "$label" "$(command -v "$command")"
  elif [[ "$required" == yes ]]; then
    printf 'FALTA %-22s instale ou adicione ao PATH\n' "$label"; failed=1
  else
    printf 'OPC.  %-22s ausente\n' "$label"
  fi
}

echo 'Ferramentas de desenvolvimento do NexoGov'
check_command 'Java 21+' java yes
check_command 'Maven' mvn yes
check_command 'Node.js' node yes
check_command 'npm' npm yes
check_command 'Python 3' python3 yes
check_command 'Docker' docker yes
check_command 'Git' git yes

if command -v java >/dev/null 2>&1; then
  java_version="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
  if [[ -n "$java_version" && "$java_version" -ge 21 ]]; then printf 'OK    %-22s %s\n' 'Java alvo' "$java_version"; else printf 'FALTA %-22s encontrado %s, necessário 21+\n' 'Java alvo' "${java_version:-desconhecido}"; failed=1; fi
fi
if command -v docker >/dev/null 2>&1; then
  if docker compose version >/dev/null 2>&1; then printf 'OK    %-22s disponível\n' 'Docker Compose'; else printf 'FALTA %-22s plugin Compose não encontrado\n' 'Docker Compose'; failed=1; fi
fi
if [[ -f "$core_root/pom.xml" ]]; then printf 'OK    %-22s %s\n' 'NEXO Core' "$(cd "$core_root" && pwd)"; else printf 'FALTA %-22s defina NEXO_CORE_DIR\n' 'NEXO Core'; failed=1; fi
if [[ -f "$repo_root/.env" ]]; then printf 'OK    %-22s presente (valores não exibidos)\n' '.env local'; else printf 'INFO  %-22s será criado com ./dev setup\n' '.env local'; fi
if command -v google-chrome >/dev/null 2>&1 || command -v chromium >/dev/null 2>&1 || command -v chromium-browser >/dev/null 2>&1; then printf 'OK    %-22s navegador disponível\n' 'Chrome/Chromium'; else printf 'OPC.  %-22s necessário apenas para ./dev e2e\n' 'Chrome/Chromium'; fi

exit "$failed"
