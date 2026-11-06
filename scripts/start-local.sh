#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
core_root="$(cd "$repo_root/../nexo-core" && pwd)"
if [[ ! -f "$repo_root/.env" ]]; then
  echo "Crie .env a partir de .env.example antes de iniciar o ambiente local."
  exit 2
fi
set -a
# shellcheck disable=SC1091
source "$repo_root/.env"
set +a
mvn -f "$core_root/pom.xml" install -DskipTests
docker compose --env-file "$repo_root/.env" -f "$repo_root/compose.yaml" up -d postgres
cd "$repo_root/backend"
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
