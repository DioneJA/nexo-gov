import { execFileSync } from 'node:child_process'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..')

export default async function globalTeardown() {
  execFileSync('docker', [
    'compose', '--project-name', 'nexogov-e2e', '-f', resolve(repoRoot, 'compose.yaml'),
    'down', '--volumes', '--remove-orphans'
  ], {
    cwd: repoRoot,
    env: { ...process.env, NEXOGOV_DB_PORT: '55434' },
    stdio: 'inherit'
  })
}
