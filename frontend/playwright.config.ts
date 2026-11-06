import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  globalTeardown: './e2e/global-teardown.ts',
  fullyParallel: false,
  timeout: 90_000,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:9000',
    channel: 'chrome',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  webServer: {
    command: 'bash ../scripts/start-e2e.sh',
    url: 'http://localhost:9000',
    timeout: 180_000,
    reuseExistingServer: false,
    stdout: 'pipe',
    stderr: 'pipe'
  }
})
