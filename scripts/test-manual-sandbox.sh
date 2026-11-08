#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
test_root="$(mktemp -d)"
state_dir="$test_root/nexogov-manual-sandbox-${UID}"
docker_log="$test_root/docker.log"
dummy_pid=""

cleanup() {
  if [[ -n "$dummy_pid" ]] && kill -0 -- "-$dummy_pid" 2>/dev/null; then
    kill -TERM -- "-$dummy_pid" 2>/dev/null || true
    wait "$dummy_pid" 2>/dev/null || true
  fi
  rm -rf "$test_root"
}
trap cleanup EXIT

mkdir -p "$test_root/bin" "$state_dir"
chmod 700 "$state_dir"
cat > "$test_root/bin/docker" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$SANDBOX_DOCKER_LOG"
EOF
chmod +x "$test_root/bin/docker"
export PATH="$test_root/bin:$PATH"
export XDG_RUNTIME_DIR="$test_root"
export SANDBOX_DOCKER_LOG="$docker_log"
: > "$docker_log"

write_partial_state() {
  local api_pid="${1:-}"
  cat > "$state_dir/state" <<EOF
PROJECT=nexogov-manual-${UID}-deadbeef
DB_PORT=39001
API_PORT=39002
WEB_PORT=39003
STORAGE_PORT=39004
DB_NAME=nexogov_sandbox
DB_USERNAME=nexogov_sandbox
API_PID=$api_pid
EOF
  : > "$state_dir/compose.env"
  chmod 600 "$state_dir/state" "$state_dir/compose.env"
}

# A recorded PID that has already exited can be safely discarded without a start-time token.
write_partial_state 99999999
"$repo_root/scripts/manual-sandbox.sh" stop > "$test_root/dead.out"
[[ ! -e "$state_dir" ]]
rg -q 'down --volumes --remove-orphans' "$docker_log"

# A live process without a captured start time must never be signaled; Compose can still be stopped.
mkdir -m 700 "$state_dir"
setsid sleep 60 &
dummy_pid="$!"
write_partial_state "$dummy_pid"
: > "$docker_log"
if "$repo_root/scripts/manual-sandbox.sh" stop > "$test_root/live.out" 2> "$test_root/live.err"; then
  echo "A parada deveria recusar o processo ativo sem identidade validada." >&2
  exit 1
fi
kill -0 -- "-$dummy_pid"
rg -q 'identidade não foi validada; sinalização recusada' "$test_root/live.err"
rg -q 'down --volumes --remove-orphans' "$docker_log"
[[ -f "$state_dir/state" ]]

kill -TERM -- "-$dummy_pid"
wait "$dummy_pid" 2>/dev/null || true
dummy_pid=""
"$repo_root/scripts/manual-sandbox.sh" stop > "$test_root/retry.out"
[[ ! -e "$state_dir" ]]

echo "Sandbox incompleto: limpeza segura e recusa de sinalização validadas."
