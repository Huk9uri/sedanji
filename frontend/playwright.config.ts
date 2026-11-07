import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  timeout: 90_000,
  globalTimeout: 20 * 60_000,
  expect: { timeout: 10_000 },
  workers: 1,
  retries: 0,
  forbidOnly: true,
  reporter: [['./e2e/safeReporter.ts']],
  outputDir: '../.tools/e2e-raw-output',
  preserveOutput: 'never',
  use: { baseURL: 'http://127.0.0.1:18173', browserName: 'chromium',
    trace: 'off', video: 'off', screenshot: 'off', serviceWorkers: 'block' },
  // The explicit runner owns both servers; existing listeners are rejected.
});
