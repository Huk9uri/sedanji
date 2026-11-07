import { writeFileSync } from 'node:fs';
import type { FullResult, Reporter, TestCase, TestResult } from '@playwright/test/reporter';

// No raw errors, attachments, request headers/bodies or Playwright HTML report.
export default class SafeReporter implements Reporter {
  private tests: { title: string; status: string; duration: number; failure?: string }[] = [];
  onTestEnd(test: TestCase, result: TestResult) {
    const title = test.title;
    const message = result.error?.message ?? '';
    const kind = /strict mode violation/.test(message) ? 'locator-ambiguity' : /[Tt]imeout|timed out/.test(message) ? 'timeout' : 'assertion-or-command';
    const line = result.error?.stack?.match(/admin\.spec\.ts:(\d+):\d+/)?.[1];
    const failure = result.error ? `${kind}${line ? ` at admin.spec.ts:${line}` : ''}` : undefined;
    this.tests.push({ title, status: result.status, duration: result.duration, failure });
    writeFileSync(process.env.SEDANJI_E2E_REPORT!, JSON.stringify({ started: process.env.SEDANJI_E2E_STARTED,
      status: 'running', tests: this.tests }, null, 2));
    console.log(`${result.status.toUpperCase()}: ${title}`);
  }
  onEnd(result: FullResult) {
    writeFileSync(process.env.SEDANJI_E2E_REPORT!, JSON.stringify({
      started: process.env.SEDANJI_E2E_STARTED, finished: new Date().toISOString(),
      status: result.status, tests: this.tests,
    }, null, 2));
  }
}
