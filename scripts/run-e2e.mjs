import { spawn, execFileSync } from 'node:child_process';
import { createServer, createConnection } from 'node:net';
import { mkdirSync, readFileSync, existsSync, statSync, writeFileSync, rmSync, readdirSync } from 'node:fs';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as pause } from 'node:timers/promises';
import { createHash } from 'node:crypto';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const front = join(root, 'frontend');
const report = join(root, '.tools/e2e-report.json');
const windows = process.platform === 'win32';
const children = [];
const clean = {};
// Deliberately do not inherit .env, JAVA_TOOL_OPTIONS, SPRING_*, DB or external keys.
for (const name of ['PATH', 'Path', 'SystemRoot', 'WINDIR', 'TEMP', 'TMP', 'HOME', 'USERPROFILE',
  'LOCALAPPDATA', 'APPDATA', 'JAVA_HOME', 'DOCKER_HOST', 'DOCKER_TLS_VERIFY', 'DOCKER_CERT_PATH',
  'PLAYWRIGHT_BROWSERS_PATH', 'GRADLE_USER_HOME']) if (process.env[name]) clean[name] = process.env[name];
clean.PATH = dirname(process.execPath) + (windows ? ';' : ':') + (clean.PATH ?? clean.Path ?? '');
clean.TESTCONTAINERS_CHECKS_DISABLE = 'true';
clean.TESTCONTAINERS_PULL_POLICY = 'kr.sedanji.E2eCachedImagePolicy';
clean.TESTCONTAINERS_RYUK_DISABLED = 'false';
clean.TESTCONTAINERS_RYUK_CONTAINER_IMAGE = 'testcontainers/ryuk:0.14.0';
let directory, ready, failure, phase = 'preflight';
const started = new Date().toISOString();
const abort = new AbortController();
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => abort.abort());
const deadline = setTimeout(() => abort.abort(), 20 * 60_000);
deadline.unref();
const aborted = new Promise((_, reject) => abort.signal.addEventListener('abort', () => reject(new Error('Run interrupted or deadline exceeded')), { once: true }));
// Keep the signal promise handled while startup performs synchronous preflights.
aborted.catch(() => {});
let cleanup = true;
let provenance;

function fingerprint() {
  const head = command('git', ['rev-parse', 'HEAD']).toString().trim();
  const paths = command('git', ['ls-files', '-z', '--cached', '--others', '--exclude-standard']).toString().split('\0')
    .filter(path => /^(backend\/|frontend\/|scripts\/|\.github\/)/.test(path)).sort();
  const digest = createHash('sha256');
  for (const path of paths) { digest.update(path + '\0'); if (existsSync(join(root, path))) digest.update(readFileSync(join(root, path))); }
  return { head, worktreeSha256: digest.digest('hex') };
}

function command(file, args, options = {}) {
  return execFileSync(file, args, { cwd: root, env: clean, stdio: 'pipe', timeout: 30_000, ...options });
}
function start(file, args, cwd) {
  const child = spawn(file, args, { cwd, env: clean, stdio: ['ignore', 'pipe', 'pipe'],
    detached: !windows, windowsHide: true });
  child.done = new Promise(resolveDone => { child.on('error', () => resolveDone(1)); child.on('exit', code => resolveDone(code ?? 1)); });
  // Raw application/runner logs may contain tokens. Never persist or forward them.
  child.stdout.on('data', () => {}); child.stderr.on('data', () => {});
  children.push(child); return child;
}
async function free(port) {
  await new Promise((yes, no) => {
    const socket = createServer(); socket.once('error', no);
    socket.listen(port, '127.0.0.1', () => socket.close(yes));
  });
}
async function probe(operation) {
  return new Promise((yes, no) => {
    const socket = createConnection({ host: '127.0.0.1', port: ready.probePort }); let result = '';
    socket.setTimeout(5000, () => socket.destroy());
    socket.on('connect', () => socket.write(JSON.stringify({ operation, capability: ready.capability }) + '\n'));
    socket.on('data', data => { result += data; });
    socket.on('end', () => { try { yes(JSON.parse(result)); } catch { no(new Error('probe')); } });
    socket.on('error', no); socket.on('timeout', no);
  });
}
async function until(check, timeout = 180_000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) {
    if (abort.signal.aborted) throw new Error('Run interrupted');
    if (children.some(c => c.exitCode !== null)) throw new Error('Owned process exited before readiness');
    if (await check()) return;
    await pause(200);
  }
  throw new Error('Readiness deadline exceeded');
}
function completeReport(value) {
  const expected = value.expectedTests;
  const tests = value.tests;
  if (!Array.isArray(expected) || !Array.isArray(tests) || expected.length !== tests.length ||
      expected.filter(t => t.file === 'admin.spec.ts').length !== 8 ||
      !expected.some(t => t.file === 'public.spec.ts')) return false;
  const identity = t => JSON.stringify([t.file, t.title]);
  const wanted = new Set(expected.map(identity));
  return wanted.size === expected.length && new Set(tests.map(identity)).size === tests.length &&
    tests.every(t => t.status === 'passed' && wanted.has(identity(t)));
}
function validate() {
  const value = JSON.parse(readFileSync(report, 'utf8'));
  const startedAt = Date.parse(value.started), finishedAt = Date.parse(value.finished);
  if (!Number.isFinite(startedAt) || !Number.isFinite(finishedAt) || finishedAt < startedAt ||
      statSync(report).mtimeMs < startedAt || value.status !== 'passed' ||
      !completeReport(value) ||
      value.exitCode !== 0 || value.cleanup !== true) throw new Error('Fresh complete E2E report required');
  if ('SEDANJI_E2E_AFTER' in process.env) {
    const after = Number(process.env.SEDANJI_E2E_AFTER);
    if (!Number.isFinite(after) || after <= 0 || startedAt < after) throw new Error('Stale report or missing CI start marker');
  }
  console.log(`PASS: E2E ${value.tests.length} tests; existing 8 admin tests preserved; no failures/skips; cleanup confirmed`);
}
if (process.argv.includes('--validate-report')) {
  try { validate(); process.exit(0); } catch {
    // Only safe counts/flags: no raw assertion messages, request data or tokens.
    try {
      const value = JSON.parse(readFileSync(report, 'utf8'));
      const tests = Array.isArray(value.tests) ? value.tests : [];
      const passed = tests.filter(test => test?.status === 'passed').length;
      const skipped = tests.filter(test => test?.status === 'skipped').length;
      const exit = Number.isInteger(value.exitCode) ? value.exitCode : 'missing';
      console.error(`FAIL: E2E report rejected; passed=${passed}/${tests.length}; skipped=${skipped}; exit=${exit}; cleanup=${value.cleanup === true}. Inspect sanitized-e2e-report artifact.`);
    } catch { console.error('FAIL: E2E report is missing or invalid.'); }
    process.exit(1);
  }
}
try {
  mkdirSync(dirname(report), { recursive: true });
  rmSync(report, { force: true });
  const major = Number(process.versions.node.split('.')[0]);
  if (![22, 24, 26].includes(major)) throw new Error('Supported Node 22/24/26 is required');
  command('java', ['-version']);
  const gradleHome = windows ? join(root, '.tools/gradle-home') : clean.GRADLE_USER_HOME ?? join(clean.HOME ?? '', '.gradle');
  const wrapperVersion = readFileSync(join(root, 'backend/gradle/wrapper/gradle-wrapper.properties'), 'utf8').match(/gradle-([\d.]+)-bin\.zip/)?.[1];
  const distribution = join(gradleHome, `wrapper/dists/gradle-${wrapperVersion}-bin`);
  if (!wrapperVersion || !existsSync(distribution) || !readdirSync(distribution).some(hash =>
    existsSync(join(distribution, hash, `gradle-${wrapperVersion}-bin.zip.ok`)))) throw new Error('Gradle Wrapper cache not prepared');
  if (command('docker', ['info', '--format', '{{.OSType}}']).toString().trim() !== 'linux') throw new Error('Linux Docker Engine required');
  for (const image of ['postgres:17-alpine', 'testcontainers/ryuk:0.14.0']) command('docker', ['image', 'inspect', image]);
  const locked = JSON.parse(readFileSync(join(front, 'package.json'), 'utf8')).devDependencies['@playwright/test'];
  for (const name of ['@playwright/test', 'playwright', 'playwright-core']) {
    if (JSON.parse(readFileSync(join(front, 'node_modules', name, 'package.json'), 'utf8')).version !== locked) throw new Error('Playwright installation does not match the pinned version');
  }
  const { chromium } = await import('../frontend/node_modules/playwright/index.mjs');
  if (!existsSync(chromium.executablePath())) throw new Error('Chromium not installed; run explicit preparation');
  await free(18090); await free(18173);
  if (process.argv.includes('--preflight')) { console.log('E2E preflight PASS'); process.exit(0); }
  provenance = fingerprint();
  directory = join(root, '.tools/e2e', `${Date.now()}-${process.pid}`);
  mkdirSync(directory, { recursive: true, mode: 0o700 });
  Object.assign(clean, { SEDANJI_E2E_DIR: directory, SEDANJI_E2E_REPORT: report, SEDANJI_E2E_STARTED: started });
  phase = 'typecheck';
  command(process.execPath, ['node_modules/typescript/bin/tsc', '-p', 'tsconfig.e2e.json'], { cwd: front });
  phase = 'startup';
  const gradleArgs = ['--offline', '--no-daemon', '-Porg.gradle.java.installations.auto-download=false', 'e2eBackend'];
  if (windows) gradleArgs.unshift('--gradle-user-home', join(root, '.tools/gradle-home'));
  if (windows) start(process.env.ComSpec ?? 'C:\\Windows\\System32\\cmd.exe', ['/d', '/c', 'gradlew.bat', ...gradleArgs], join(root, 'backend'));
  else start('./gradlew', gradleArgs, join(root, 'backend'));
  const startupDeadline = Date.now() + 180_000;
  await until(() => existsSync(join(directory, 'ready.json')));
  ready = JSON.parse(readFileSync(join(directory, 'ready.json'), 'utf8'));
  start(process.execPath, ['node_modules/vite/bin/vite.js', '--config', 'vite.e2e.config.ts'], front);
  await until(async () => { try { return (await fetch('http://127.0.0.1:18173/api/session', { signal: AbortSignal.timeout(1000) })).ok; } catch { return false; } }, Math.max(1, startupDeadline - Date.now()));
  phase = 'tests';
  const tests = start(process.execPath, ['node_modules/@playwright/test/cli.js', 'test'], front);
  const code = await Promise.race([tests.done, aborted]);
  if (code !== 0) throw new Error('Browser tests failed; see sanitized per-test status');
} catch (error) {
  failure = { phase, kind: phase === 'preflight' ? 'NOT RUN' : 'FAIL' };
  console.log(`${failure.kind}: E2E ${phase}; raw logs suppressed to protect credentials`);
} finally {
  if (directory && !ready && existsSync(join(directory, 'ready.json'))) {
    try { ready = JSON.parse(readFileSync(join(directory, 'ready.json'), 'utf8')); } catch { cleanup = false; }
  }
  if (ready) try { await probe('stop'); } catch { cleanup = false; }
  if (children[0] && ready) await Promise.race([children[0].done, pause(15_000)]);
  for (const child of children.filter(c => c.exitCode === null)) {
    try {
      if (windows) command('taskkill.exe', ['/pid', String(child.pid), '/t', '/f']);
      else process.kill(-child.pid, 'SIGTERM');
      await Promise.race([child.done, pause(5000)]);
      if (child.exitCode === null) {
        cleanup = false;
        if (!windows) { process.kill(-child.pid, 'SIGKILL'); await Promise.race([child.done, pause(5000)]); }
      }
    } catch { if (child.exitCode === null) cleanup = false; }
  }
  // Only inspect/remove this run's labeled container, never prune or touch development data.
  try {
    if (directory) {
      const ids = command('docker', ['ps', '-aq', '--filter', `label=sedanji.e2e=${directory.split(/[\\/]/).at(-1)}`]).toString().trim().split(/\s+/).filter(Boolean);
      for (const id of ids) command('docker', ['rm', '-f', id]);
      if (ids.length) cleanup = false; // Fallback cleanup remains a reportable abnormal termination.
    }
  } catch { cleanup = false; }
  try { await free(18090); await free(18173); } catch { cleanup = false; }
  if (directory) rmSync(directory, { recursive: true, force: true });
  let result = { started, finished: new Date().toISOString(), status: 'not-run', tests: [] };
  if (existsSync(report)) try { result = JSON.parse(readFileSync(report, 'utf8')); } catch { /* fail guard below */ }
  const complete = result.status === 'passed' && completeReport(result);
  Object.assign(result, { cleanup, exitCode: failure || !cleanup || !complete ? 1 : 0, failure });
  try {
    const current = fingerprint();
    Object.assign(result, provenance ?? current);
    if (provenance && JSON.stringify(current) !== JSON.stringify(provenance)) { result.sourceChangedDuringRun = true; result.exitCode = 1; }
  } catch { result.provenance = 'unavailable'; result.exitCode = 1; }
  writeFileSync(report, JSON.stringify(result, null, 2));
  const history = join(root, '.tools/e2e-reports'); mkdirSync(history, { recursive: true });
  writeFileSync(join(history, started.replace(/[:.]/g, '-') + '.json'), JSON.stringify(result, null, 2));
  clearTimeout(deadline);
}
try { validate(); process.exit(0); } catch { process.exit(1); }
