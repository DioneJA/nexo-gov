import { execFileSync } from 'node:child_process'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..')

export default async function globalTeardown() {
  const project = process.env.NEXOGOV_E2E_COMPOSE_PROJECT
  if (!project || !/^nexogov-e2e-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(project)) {
    throw new Error('Refusing to stop a Compose project that was not created for this NexoGov E2E run.')
  }
  execFileSync('docker', [
    'compose', '--project-name', project, '-f', resolve(repoRoot, 'compose.yaml'),
    'down', '--volumes', '--remove-orphans'
  ], {
    cwd: repoRoot,
    env: { ...process.env, NEXOGOV_DB_PORT: process.env.NEXOGOV_E2E_DB_PORT || '55434' },
    stdio: 'inherit'
  })
}
