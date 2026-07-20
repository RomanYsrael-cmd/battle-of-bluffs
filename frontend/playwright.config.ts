import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    actionTimeout: 15_000,
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? 'http://127.0.0.1:5173',
    navigationTimeout: 30_000,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    launchOptions: process.env.MEDIA_E2E === 'true' ? {
      args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'],
    } : undefined,
    ...devices['Desktop Chrome'],
  },
  outputDir: 'test-results/artifacts',
})
