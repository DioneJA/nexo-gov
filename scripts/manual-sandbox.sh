#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
runtime_root="${XDG_RUNTIME_DIR:-/tmp}"
state_dir="$runtime_root/nexogov-manual-sandbox-${UID}"
action="${1:-status}"

compose() {
  docker compose --project-name "$project" --env-file "$state_dir/compose.env" -f "$repo_root/compose.yaml" "$@"
}

read_state() {
  if [[ ! -f "$state_dir/state" ]]; then
    echo "Não há sandbox manual ativo. Inicie com ./dev sandbox." >&2
    return 1
  fi
  while IFS='=' read -r key value; do
    case "$key" in
      PROJECT) project="$value" ;;
      DB_PORT) db_port="$value" ;;
      API_PORT) api_port="$value" ;;
      WEB_PORT) web_port="$value" ;;
      STORAGE_PORT) storage_port="$value" ;;
      API_PID) api_pid="$value" ;;
      WEB_PID) web_pid="$value" ;;
      API_START_TICKS) api_start_ticks="$value" ;;
      WEB_START_TICKS) web_start_ticks="$value" ;;
      DB_NAME) db_name="$value" ;;
      DB_USERNAME) db_username="$value" ;;
    esac
  done < "$state_dir/state"
  [[ "${project:-}" =~ ^nexogov-manual-${UID}-[a-f0-9]{8}$ ]] || { echo "Identidade do sandbox inválida; parada recusada." >&2; return 1; }
  [[ "${db_port:-}" =~ ^[0-9]+$ && "${api_port:-}" =~ ^[0-9]+$ && "${web_port:-}" =~ ^[0-9]+$ && "${storage_port:-}" =~ ^[0-9]+$ ]] || { echo "Portas do sandbox inválidas; parada recusada." >&2; return 1; }
  [[ "${api_pid:-}" =~ ^[0-9]+$ && "${web_pid:-}" =~ ^[0-9]+$ ]] || { echo "Identidade dos processos do sandbox inválida; parada recusada." >&2; return 1; }
  if [[ -z "${api_start_ticks:-}" || -z "${web_start_ticks:-}" ]]; then
    api_start_ticks="$(process_start_ticks "$api_pid" 2>/dev/null || true)"
    web_start_ticks="$(process_start_ticks "$web_pid" 2>/dev/null || true)"
    if [[ ! "$api_start_ticks" =~ ^[0-9]+$ || ! "$web_start_ticks" =~ ^[0-9]+$ ]] \
      || ! process_group_matches_sandbox "$api_pid" api "$api_port" "$api_start_ticks" \
      || ! process_group_matches_sandbox "$web_pid" web "$web_port" "$web_start_ticks"; then
      echo "O estado antigo não corresponde aos processos esperados; parada recusada." >&2
      return 1
    fi
    printf 'API_START_TICKS=%s\nWEB_START_TICKS=%s\n' "$api_start_ticks" "$web_start_ticks" >> "$state_dir/state"
  fi
  [[ "$api_start_ticks" =~ ^[0-9]+$ && "$web_start_ticks" =~ ^[0-9]+$ ]] || { echo "Identidade dos processos do sandbox inválida; parada recusada." >&2; return 1; }
}

wait_for_http() {
  local url="$1" label="$2"
  for _ in $(seq 1 90); do
    if curl --fail --silent --show-error --output /dev/null "$url" 2>/dev/null; then return 0; fi
    sleep 1
  done
  echo "Tempo esgotado ao iniciar $label. Consulte $state_dir/${label}.log." >&2
  return 1
}

process_start_ticks() {
  local pid="$1"
  [[ -r "/proc/$pid/stat" ]] || return 1
  awk '{sub(/^.*\) /, ""); print $20}' "/proc/$pid/stat"
}

process_group_matches_sandbox() {
  local pid="$1" service="$2" port="$3" expected_start_ticks="$4"
  local current_start_ticks current_pgid args group_members=0
  if [[ -r "/proc/$pid/stat" ]]; then
    current_start_ticks="$(process_start_ticks "$pid")" || return 1
    [[ "$current_start_ticks" == "$expected_start_ticks" ]] || return 1
    current_pgid="$(ps -o pgid= -p "$pid" | tr -d ' ')"
    [[ "$current_pgid" == "$pid" ]] || return 1
  fi

  while read -r _ candidate_pgid args; do
    [[ "$candidate_pgid" == "$pid" ]] || continue
    group_members=1
    case "$service" in
      api)
        [[ ("$args" == *"org.codehaus.plexus.classworlds.launcher.Launcher"* \
            && "$args" == *"-Dmaven.multiModuleProjectDirectory=$repo_root/backend"* \
            && "$args" == *"spring-boot:run"*) \
          || ("$args" == *"/backend/target/classes"* && "$args" == *"br.com.nexo.gov.NexoGovApplication"*) ]] || return 1
        ;;
      web) [[ "$args" == *"--port $port"* && ("$args" == *"npm run dev"* || "$args" == *vite* || "$args" == *node*) ]] || return 1 ;;
      *) return 1 ;;
    esac
  done < <(ps -eo pid=,pgid=,args=)
  [[ "$group_members" == 1 ]]
}

capture_process_identity() {
  local pid="$1" service="$2" port="$3"
  for _ in $(seq 1 50); do
    local start_ticks
    start_ticks="$(process_start_ticks "$pid" 2>/dev/null || true)"
    if [[ "$start_ticks" =~ ^[0-9]+$ ]] && process_group_matches_sandbox "$pid" "$service" "$port" "$start_ticks"; then
      printf '%s' "$start_ticks"
      return 0
    fi
    sleep 0.1
  done
  echo "Não foi possível validar a identidade do processo $service; sandbox preservado para inspeção." >&2
  return 1
}

stop_process_group() {
  local pid="$1" service="$2" port="$3" expected_start_ticks="$4"
  [[ "$pid" =~ ^[0-9]+$ && "$expected_start_ticks" =~ ^[0-9]+$ ]] || return 1
  if ! kill -0 -- "-$pid" 2>/dev/null; then return 0; fi
  if ! process_group_matches_sandbox "$pid" "$service" "$port" "$expected_start_ticks"; then
    echo "O grupo de processos $service não corresponde ao sandbox; parada recusada para proteger processos alheios." >&2
    return 1
  fi
  kill -TERM -- "-$pid"
  for _ in $(seq 1 30); do
    kill -0 -- "-$pid" 2>/dev/null || return 0
    sleep 0.1
  done
  if kill -0 -- "-$pid" 2>/dev/null; then
    process_group_matches_sandbox "$pid" "$service" "$port" "$expected_start_ticks" || {
      echo "A identidade do grupo $service mudou durante a parada; encerramento forçado recusado." >&2
      return 1
    }
    kill -KILL -- "-$pid"
  fi
}

case "$action" in
  start)
    if [[ -e "$state_dir" ]]; then
      if [[ -f "$state_dir/state" ]]; then
        echo "Já existe um sandbox manual. Use ./dev sandbox-status ou ./dev sandbox-stop." >&2
      else
        echo "Estado temporário incompleto em $state_dir; revise-o antes de iniciar outro sandbox." >&2
      fi
      exit 2
    fi
    mkdir -m 700 "$state_dir"
    umask 077
    read -r db_port api_port web_port _ storage_port < <(python3 "$repo_root/scripts/find-free-ports.py")
    suffix="$(python3 -c 'import secrets; print(secrets.token_hex(4))')"
    project="nexogov-manual-${UID}-${suffix}"
    db_name="nexogov_sandbox"
    db_username="nexogov_sandbox"
    cat > "$state_dir/compose.env" <<EOF
NEXOGOV_DB_NAME=$db_name
NEXOGOV_DB_USERNAME=$db_username
NEXOGOV_DB_PASSWORD=nexogov-sandbox-only
NEXOGOV_DB_PORT=$db_port
NEXOGOV_STORAGE_BUCKET=nexogov-sandbox-private-documents
NEXOGOV_STORAGE_REGION=us-east-1
NEXOGOV_STORAGE_PORT=$storage_port
NEXOGOV_STORAGE_ENDPOINT_URI=http://127.0.0.1:$storage_port
NEXOGOV_STORAGE_ACCESS_KEY=local-s3mock-access
NEXOGOV_STORAGE_SECRET_KEY=local-s3mock-secret
NEXOGOV_ATTACHMENT_MAX_FILES=10
NEXOGOV_ATTACHMENT_MAX_FILE_SIZE_BYTES=10485760
NEXOGOV_ATTACHMENT_MAX_REQUEST_SIZE=105MB
NEXOGOV_API_PORT=$api_port
NEXOGOV_WEB_PORT=$web_port
NEXOGOV_DEMO_USERNAME=servidor
NEXOGOV_DEMO_PASSWORD=e2e-local-only-password
NEXOGOV_DEMO_MEMBER_USERNAME=servidor.teste
NEXOGOV_DEMO_MEMBER_PASSWORD=e2e-local-only-member-password
NEXOGOV_PUBLIC_BASE_URL=http://127.0.0.1:$web_port
NEXOGOV_ALLOWED_ORIGIN=http://127.0.0.1:$web_port
VITE_API_URL=http://127.0.0.1:$api_port/api/v1
VITE_STAFF_AUTH_MODE=local-basic
NEXOGOV_IDENTITY_ISSUER_URI=
NEXOGOV_IDENTITY_JWK_SET_URI=
NEXOGOV_IDENTITY_AUDIENCE=
EOF
    : > "$state_dir/api.log"
    : > "$state_dir/web.log"
    printf 'PROJECT=%s\nDB_PORT=%s\nAPI_PORT=%s\nWEB_PORT=%s\nSTORAGE_PORT=%s\nDB_NAME=%s\nDB_USERNAME=%s\n' \
      "$project" "$db_port" "$api_port" "$web_port" "$storage_port" "$db_name" "$db_username" > "$state_dir/state"
    chmod 600 "$state_dir/compose.env" "$state_dir/state" "$state_dir/api.log" "$state_dir/web.log"
    if ! compose up -d --wait --wait-timeout 90 postgres s3mock; then
      compose down --volumes --remove-orphans || true
      rm -f "$state_dir/compose.env" "$state_dir/state" "$state_dir/api.log" "$state_dir/web.log"
      rmdir "$state_dir"
      exit 1
    fi
    (
      cd "$repo_root/backend"
      exec setsid env SPRING_PROFILES_ACTIVE=local \
        NEXOGOV_DB_URL="jdbc:postgresql://127.0.0.1:$db_port/$db_name" \
        NEXOGOV_DB_USERNAME="$db_username" NEXOGOV_DB_PASSWORD=nexogov-sandbox-only \
        NEXOGOV_API_PORT="$api_port" \
        NEXOGOV_STORAGE_BUCKET=nexogov-sandbox-private-documents \
        NEXOGOV_STORAGE_REGION=us-east-1 \
        NEXOGOV_STORAGE_ENDPOINT_URI="http://127.0.0.1:$storage_port" \
        NEXOGOV_STORAGE_ACCESS_KEY=local-s3mock-access NEXOGOV_STORAGE_SECRET_KEY=local-s3mock-secret \
        NEXOGOV_DEMO_USERNAME=servidor NEXOGOV_DEMO_PASSWORD=e2e-local-only-password \
        NEXOGOV_DEMO_MEMBER_USERNAME=servidor.teste NEXOGOV_DEMO_MEMBER_PASSWORD=e2e-local-only-member-password \
        NEXOGOV_PUBLIC_BASE_URL="http://127.0.0.1:$web_port" NEXOGOV_ALLOWED_ORIGIN="http://127.0.0.1:$web_port" \
        mvn -B spring-boot:run
    ) > "$state_dir/api.log" 2>&1 < /dev/null &
    api_pid="$!"
    if ! api_start_ticks="$(capture_process_identity "$api_pid" api "$api_port")"; then exit 1; fi
    printf 'API_PID=%s\n' "$api_pid" >> "$state_dir/state"
    printf 'API_START_TICKS=%s\n' "$api_start_ticks" >> "$state_dir/state"
    if ! wait_for_http "http://127.0.0.1:$api_port/actuator/health" api; then
      stop_process_group "$api_pid" api "$api_port" "$api_start_ticks"
      compose down --volumes --remove-orphans || true
      rm -f "$state_dir/compose.env" "$state_dir/state" "$state_dir/api.log" "$state_dir/web.log"
      rmdir "$state_dir"
      exit 1
    fi
    (
      cd "$repo_root/frontend"
      exec setsid env VITE_API_URL="http://127.0.0.1:$api_port/api/v1" VITE_STAFF_AUTH_MODE=local-basic \
        npm run dev -- --host 127.0.0.1 --port "$web_port" --strictPort
    ) > "$state_dir/web.log" 2>&1 < /dev/null &
    web_pid="$!"
    if ! web_start_ticks="$(capture_process_identity "$web_pid" web "$web_port")"; then exit 1; fi
    printf 'WEB_PID=%s\n' "$web_pid" >> "$state_dir/state"
    printf 'WEB_START_TICKS=%s\n' "$web_start_ticks" >> "$state_dir/state"
    if ! wait_for_http "http://127.0.0.1:$web_port/servidor/" web; then
      stop_process_group "$web_pid" web "$web_port" "$web_start_ticks"
      stop_process_group "$api_pid" api "$api_port" "$api_start_ticks"
      compose down --volumes --remove-orphans || true
      rm -f "$state_dir/compose.env" "$state_dir/state" "$state_dir/api.log" "$state_dir/web.log"
      rmdir "$state_dir"
      exit 1
    fi
    cat <<EOF
Sandbox manual pronto, isolado em Compose: $project
Área do servidor: http://127.0.0.1:$web_port/servidor/
Portal do cidadão: http://127.0.0.1:$web_port/
Conta administradora: servidor / e2e-local-only-password
Conta de setor: servidor.teste / e2e-local-only-member-password
Organizações fictícias: Município Alfa e Município Beta
Dados e arquivos são descartáveis; a parada remove somente este sandbox: ./dev sandbox-stop
EOF
    ;;
  status)
    read_state
    printf 'Sandbox: %s\nServidor: http://127.0.0.1:%s/servidor/\nCidadão: http://127.0.0.1:%s/\n' "$project" "$web_port" "$web_port"
    curl --fail --silent --show-error "http://127.0.0.1:$api_port/actuator/health" || true
    printf '\n'
    ;;
  stop)
    read_state
    stop_process_group "$web_pid" web "$web_port" "$web_start_ticks"
    stop_process_group "$api_pid" api "$api_port" "$api_start_ticks"
    compose down --volumes --remove-orphans
    rm -f "$state_dir/compose.env" "$state_dir/state" "$state_dir/api.log" "$state_dir/web.log"
    rmdir "$state_dir"
    echo "Sandbox $project encerrado; seus dados fictícios foram removidos."
    ;;
  *)
    echo "Uso: $0 start|status|stop" >&2
    exit 2
    ;;
esac
