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
  echo "Crie .env a partir de .env.example antes de iniciar o ambiente local."
  exit 2
fi
set -a
# shellcheck disable=SC1091
source "$repo_root/.env"
set +a
mvn -f "$core_root/pom.xml" install -DskipTests
docker compose --env-file "$repo_root/.env" -f "$repo_root/compose.yaml" up -d --wait --wait-timeout 90 postgres s3mock
cd "$repo_root/backend"
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
