import { defineConfig } from '@playwright/test'
import { randomUUID } from 'node:crypto'

const webPort = process.env.NEXOGOV_E2E_WEB_PORT || '9000'
// Compose teardown is destructive within its project namespace. Generate the
// identity here for every Playwright process, ignoring inherited environment.
process.env.NEXOGOV_E2E_COMPOSE_PROJECT = `nexogov-e2e-${randomUUID()}`
process.env.NEXOGOV_E2E_DB_PORT ||= '55434'
process.env.NEXOGOV_E2E_API_PORT ||= '8080'
process.env.NEXOGOV_E2E_WEB_PORT ||= webPort
process.env.NEXOGOV_E2E_STORAGE_PORT ||= '55436'
const baseURL = `http://localhost:${webPort}`

export default defineConfig({
  testDir: './e2e',
  globalTeardown: './e2e/global-teardown.ts',
  fullyParallel: false,
  // Scenarios share the disposable database; some verify concurrent access revocation.
  workers: 1,
  timeout: 90_000,
  reporter: 'list',
  use: {
    baseURL,
    channel: 'chrome',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  webServer: {
    command: 'bash ../scripts/start-e2e.sh',
    url: baseURL,
    timeout: 180_000,
    reuseExistingServer: false,
    stdout: 'pipe',
    stderr: 'pipe'
  }
})
