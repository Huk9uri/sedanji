import { writeFileSync } from 'node:fs';
import { basename } from 'node:path';
import type { FullConfig, FullResult, Reporter, Suite, TestCase, TestResult, TestStep } from '@playwright/test/reporter';

// No raw errors, attachments, request headers/bodies or Playwright HTML report.
export default class SafeReporter implements Reporter {
  private expectedTests: { file: string; title: string }[] = [];
  private tests: { file: string; title: string; status: string; duration: number; failure?: string }[] = [];
  private publicLine = new Map<string, number>();
  onStepBegin(test: TestCase, _result: TestResult, step: TestStep) {
    if (step.location && basename(step.location.file) === 'public.spec.ts') this.publicLine.set(test.id, step.location.line);
  }
  onBegin(_config: FullConfig, suite: Suite) {
    this.expectedTests = suite.allTests().map(test => ({ file: basename(test.location.file), title: test.title }));
  }
  onTestEnd(test: TestCase, result: TestResult) {
    const title = test.title;
    const message = result.error?.message ?? '';
    const kind = /strict mode violation/.test(message) ? 'locator-ambiguity' : /[Tt]imeout|timed out/.test(message) ? 'timeout' : 'assertion-or-command';
    const file = basename(test.location.file);
    const line = result.error?.stack?.match(/(?:admin|public)\.spec\.ts:(\d+):\d+/)?.[1]
      ?? (file === 'public.spec.ts' ? this.publicLine.get(test.id) : undefined);
    const failure = result.error ? `${kind}${line ? ` at ${file}:${line}` : ''}` : undefined;
    this.tests.push({ file, title, status: result.status, duration: result.duration, failure });
    writeFileSync(process.env.SEDANJI_E2E_REPORT!, JSON.stringify({ started: process.env.SEDANJI_E2E_STARTED,
      status: 'running', expectedTests: this.expectedTests, tests: this.tests }, null, 2));
    console.log(`${result.status.toUpperCase()}: ${title}`);
  }
  onEnd(result: FullResult) {
    writeFileSync(process.env.SEDANJI_E2E_REPORT!, JSON.stringify({
      started: process.env.SEDANJI_E2E_STARTED, finished: new Date().toISOString(),
      status: result.status, expectedTests: this.expectedTests, tests: this.tests,
    }, null, 2));
  }
}
