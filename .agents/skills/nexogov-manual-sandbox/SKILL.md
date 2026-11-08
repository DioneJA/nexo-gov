---
name: nexogov-manual-sandbox
description: Maintain the isolated manual NexoGov sandbox and its safe start, status, recovery, and stop lifecycle.
---

# Manual sandbox lifecycle

- Keep the manual sandbox isolated from `.env` and the persistent local Compose project. Its state directory and generated environment file are private to the current OS user.
- Persist each service PID immediately after launch, before waiting for process identity capture. A failed capture must leave enough state for safe recovery.
- Signal a process group only when PID, process start time, group ID, expected command, and assigned port all match the recorded sandbox service.
- If a process is unvalidated, refuse to signal it. The stop command may still stop the exact validated Compose project, but must preserve state for inspection while a process remains alive.
- Do not discover and adopt arbitrary processes during recovery. Legacy migration is allowed only for the known old state shape with both service PIDs and no start-time fields, followed by full identity validation.
- Cover partial-state recovery with `./dev sandbox-test`; use a fake Docker executable and disposable process groups so the test cannot remove a developer database or signal an unrelated service.
- Keep `README.md`, `AGENTS.md`, `docs/TESTAR_LOCAL.md`, the `./dev` help, and this skill synchronized when lifecycle behavior changes.
