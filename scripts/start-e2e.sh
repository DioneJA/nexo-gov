#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose_project="nexogov-e2e"
compose_args=(--project-name "$compose_project" -f "$repo_root/compose.yaml")

export NEXOGOV_DB_PORT=55434
export NEXOGOV_DB_NAME=nexogov_e2e
export NEXOGOV_DB_USERNAME=nexogov_e2e
export NEXOGOV_DB_PASSWORD=e2e-local-only
export NEXOGOV_DB_URL=jdbc:postgresql://127.0.0.1:55434/nexogov_e2e
export NEXOGOV_DEMO_USERNAME=servidor
export NEXOGOV_DEMO_PASSWORD=e2e-local-only-password
export NEXOGOV_PUBLIC_BASE_URL=http://localhost:9000
export NEXOGOV_ALLOWED_ORIGIN=http://localhost:9000
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

docker compose "${compose_args[@]}" up --detach --wait --wait-timeout 90 postgres
(cd "$repo_root/backend" && mvn spring-boot:run) &
api_pid=$!

api_ready=0
for _ in $(seq 1 120); do
  if curl --fail --silent http://localhost:8080/actuator/health >/dev/null; then
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

npm --prefix "$repo_root/frontend" run dev -- --host 127.0.0.1 --port 9000 --strictPort &
web_pid=$!
wait "$web_pid"
