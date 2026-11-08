#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose_project="${NEXOGOV_E2E_COMPOSE_PROJECT:-}"
if [[ ! "$compose_project" =~ ^nexogov-e2e-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$ ]]; then
  echo "Nome de projeto E2E ausente/inválido; execute ./dev e2e para criar recursos isolados." >&2
  exit 2
fi
db_port="${NEXOGOV_E2E_DB_PORT:?porta de banco E2E ausente}"
api_port="${NEXOGOV_E2E_API_PORT:?porta de API E2E ausente}"
web_port="${NEXOGOV_E2E_WEB_PORT:?porta web E2E ausente}"
oidc_port="${NEXOGOV_E2E_OIDC_PORT:?porta OIDC E2E ausente}"
storage_port="${NEXOGOV_E2E_STORAGE_PORT:?porta de armazenamento E2E ausente}"
compose_args=(--project-name "$compose_project" -f "$repo_root/compose.yaml")

# Even with a fresh random project id, refuse to adopt any pre-existing
# container, volume or network carrying that id. Cleanup below is then limited
# to the isolated resources created after this check.
if docker ps -aq --filter "label=com.docker.compose.project=$compose_project" | grep -q . \
  || docker volume ls -q --filter "label=com.docker.compose.project=$compose_project" | grep -q . \
  || docker network ls -q --filter "label=com.docker.compose.project=$compose_project" | grep -q .; then
  echo "O projeto Compose E2E já possui recursos; recusando reutilização e limpeza." >&2
  exit 2
fi

export NEXOGOV_DB_PORT="$db_port"
export NEXOGOV_DB_NAME=nexogov_e2e
export NEXOGOV_DB_USERNAME=nexogov_e2e
export NEXOGOV_DB_PASSWORD=e2e-local-only
export NEXOGOV_DB_URL="jdbc:postgresql://127.0.0.1:${db_port}/nexogov_e2e"
export NEXOGOV_DEMO_USERNAME=servidor
export NEXOGOV_DEMO_PASSWORD=e2e-local-only-password
export NEXOGOV_DEMO_MEMBER_USERNAME=servidor.teste
export NEXOGOV_DEMO_MEMBER_PASSWORD=e2e-local-only-member-password
export NEXOGOV_PUBLIC_BASE_URL="http://localhost:${web_port}"
export NEXOGOV_ALLOWED_ORIGIN="http://localhost:${web_port}"
export NEXOGOV_API_PORT="$api_port"
export VITE_API_URL="http://127.0.0.1:${api_port}/api/v1"
export NEXOGOV_STORAGE_PORT="$storage_port"
export NEXOGOV_STORAGE_BUCKET=nexogov-e2e-private-documents
export NEXOGOV_STORAGE_REGION=us-east-1
export NEXOGOV_STORAGE_ENDPOINT_URI="http://127.0.0.1:${storage_port}"
export NEXOGOV_STORAGE_ACCESS_KEY=e2e-s3mock-access
export NEXOGOV_STORAGE_SECRET_KEY=e2e-s3mock-secret
if [[ "${NEXOGOV_E2E_AUTH_MODE:-local-basic}" == "oidc" ]]; then
  export VITE_STAFF_AUTH_MODE=oidc
  export VITE_OIDC_ISSUER="http://127.0.0.1:${oidc_port}/issuer"
  export VITE_OIDC_CLIENT_ID=nexogov-e2e-public-client
  export VITE_OIDC_REDIRECT_URI="http://localhost:${web_port}/servidor/"
  export VITE_OIDC_SCOPE="openid profile"
else
  export VITE_STAFF_AUTH_MODE=local-basic
fi
export SPRING_PROFILES_ACTIVE=local

api_pid=""
web_pid=""
cleanup() {
  local result=$?
  if [[ -n "$web_pid" ]]; then kill "$web_pid" 2>/dev/null || true; fi
  if [[ -n "$api_pid" ]]; then kill "$api_pid" 2>/dev/null || true; fi
  if [[ -n "$web_pid" ]]; then wait "$web_pid" 2>/dev/null || true; fi
  if [[ -n "$api_pid" ]]; then wait "$api_pid" 2>/dev/null || true; fi
  docker compose "${compose_args[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
  exit "$result"
}
trap cleanup EXIT INT TERM

docker compose "${compose_args[@]}" up --detach --wait --wait-timeout 90 postgres s3mock
(cd "$repo_root/backend" && mvn spring-boot:run) &
api_pid=$!

api_ready=0
for _ in $(seq 1 120); do
  if curl --fail --silent "http://127.0.0.1:${api_port}/actuator/health" >/dev/null; then
    api_ready=1
    break
  fi
  if ! kill -0 "$api_pid" 2>/dev/null; then
    wait "$api_pid"
    exit 1
  fi
  sleep 1
done
if [[ "$api_ready" != 1 ]]; then
  echo "A API NexoGov não iniciou para o teste de navegador." >&2
  exit 1
fi

npm --prefix "$repo_root/frontend" run dev -- --host 127.0.0.1 --port "$web_port" --strictPort &
web_pid=$!
wait "$web_pid"
