import { test, expect } from '@playwright/test';
import type { Page, BrowserContext } from '@playwright/test';
import { probe, runtime } from './probe';
import type { Row } from './probe';
import type { Restaurant } from '../src/types';
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

test.afterEach(async ({ page }, info) => {
  if (info.status === info.expectedStatus || page.isClosed()) return;
  const directory = resolve(process.env.SEDANJI_E2E_DIR!, '../../e2e-masked', process.env.SEDANJI_E2E_STARTED!.replace(/[:.]/g, '-'));
  mkdirSync(directory, { recursive: true });
  await page.screenshot({ path: resolve(directory, `${info.testId.replace(/[^a-zA-Z0-9-]/g, '')}.png`),
    mask: [page.locator('input, textarea, pre, [contenteditable], .login-panel, .admin-note, [role="alert"]')],
    fullPage: true, timeout: 5000 }).catch(() => {});
});

async function isolate(context: BrowserContext) {
  // Prevent accidental third-party resources/navigation; application /api is real.
  await context.route('**/*', route => {
    const url = new URL(route.request().url());
    return url.origin === 'http://127.0.0.1:18173' ? route.continue() : route.abort();
  });
}
test.beforeEach(async ({ context }) => { await isolate(context); });
async function login(page: Page, username = 'e2e-admin') {
  await page.goto('/');
  await page.getByRole('button', { name: '관리자', exact: true }).click();
  await page.getByLabel('아이디', { exact: true }).fill(username);
  await page.getByLabel('비밀번호', { exact: true }).fill(runtime().password);
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await expect(page.getByRole('button', { name: '로그아웃', exact: true })).toBeVisible();
}
async function register(page: Page, scenario: string) {
  await page.getByRole('button', { name: '식당 직접 등록 · 초안 이어쓰기' }).click();
  await page.getByLabel('식당 이름 검색').fill(scenario);
  await page.getByRole('button', { name: '장소 검색', exact: true }).click();
  await page.getByRole('button', { name: '이 지점 선택', exact: true }).click();
}
async function relationship(page: Page, zero = false) {
  const chef = (await probe()).chefs.find(c => c.name === 'A 셰프')!;
  await page.getByLabel('관계 셰프 (승인 필수)').selectOption(String(chef.id));
  await page.getByLabel('출처 영상 URL').fill('https://www.youtube.com/watch?v=E2EVIDEO006');
  await page.getByRole('button', { name: '메타데이터 확인', exact: true }).click();
  await expect(page.getByText('Video ID E2EVIDEO006', { exact: true })).toBeVisible();
  await page.getByLabel('관계 유형 (승인 필수)').selectOption('VISITED');
  if (zero) await page.getByLabel('확인된 시점 (선택)').fill('00:00');
}
async function publicData(page: Page): Promise<Restaurant[]> {
  const response = await page.request.get('/api/restaurants');
  expect(response.status()).toBe(200);
  const data: Restaurant[] = await response.json();
  for (const restaurant of data) {
    expect(Object.keys(restaurant).sort()).toEqual(['id', 'kakaoPlaceId', 'name', 'roadAddress', 'address', 'longitude', 'latitude', 'placeUrl', 'mentions'].sort());
    for (const source of restaurant.mentions) expect(Object.keys(source).sort()).toEqual(['mentionId', 'chefName', 'videoId', 'videoTitle', 'mentionType', 'timestampSeconds'].sort());
  }
  const text = JSON.stringify(data);
  for (const field of ['verificationNote', 'reviewNote', 'reviewedBy', 'actorId', 'candidateId', 'evidence', 'changeReason', 'proposedValues', '내부 메모']) expect(text).not.toContain(field);
  return data;
}
async function detail(page: Page, name: string) {
  await page.getByRole('button', { name: '식당 관리', exact: true }).click();
  await page.getByLabel('관리자 식당 검색', { exact: true }).fill(name);
  await page.getByRole('button', { name: '검색', exact: true }).click();
  await page.getByRole('button', { name: `상세 보기 · ${name}`, exact: true }).click();
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();
}
function own(rows: Row[], id: string | number | null) { return rows.find(row => row.id === id)!; }
async function fixture(name: string) {
  const data = await probe(); const restaurant = data.restaurants.find(r => r.name === name)!;
  return { data, restaurant, mentions: data.mentions.filter(m => m.restaurant_id === restaurant.id) };
}
async function approveCorrection(page: Page) {
  const response = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/approve'));
  await page.getByRole('button', { name: '영향 확인 · 재승인', exact: true }).click();
  expect((await response).status()).toBe(200);
  await expect(page.getByText('APPLIED · 적용 완료.', { exact: false })).toBeVisible();
}
async function saveCorrection(page: Page, reason: string) {
  await page.getByLabel('필수 정정 사유').fill(reason);
  await page.getByRole('button', { name: '정정안 저장', exact: true }).click();
  await expect(page.getByText('정정안을 저장했습니다. 공개본은 변경되지 않았습니다.', { exact: true })).toBeVisible();
}

test('A1 A2 최소 초안 → 서버 ID 재개 → null 시점 승인', async ({ page }) => {
  await login(page); await register(page, 'A1');
  const response = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/mention-drafts'));
  await page.getByRole('button', { name: '임시 저장', exact: true }).click();
  expect((await response).status()).toBe(201);
  const draft = await (await response).json();
  const state = await probe(); const saved = own(state.drafts, draft.id);
  expect(saved).toMatchObject({ chef_id: null, video_id: null, mention_type: null, timestamp_seconds: null, status: 'DRAFT', version: 0 });
  expect(state.mentions.filter(m => m.restaurant_id === saved.restaurant_id)).toHaveLength(0);
  expect((await publicData(page)).some(r => r.id === saved.restaurant_id)).toBe(false);
  await page.reload();
  await expect(page.getByText(`초안 #${draft.id}`, { exact: false }).first()).toBeVisible();
  await relationship(page);
  const patch = page.waitForResponse(r => r.request().method() === 'PATCH' && r.url().endsWith(`/mention-drafts/${draft.id}`));
  await page.getByRole('button', { name: '수정 저장', exact: true }).click();
  expect((await patch).status()).toBe(200);
  expect(own((await probe()).drafts, draft.id).version).toBe(1);
  await page.getByRole('button', { name: '승인 전 검토', exact: true }).click();
  const approval = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith(`/mention-drafts/${draft.id}/approve`));
  await page.getByRole('button', { name: '저장된 초안 승인·공개', exact: true }).click();
  expect((await approval).status()).toBe(200);
  const result = await (await approval).json(); const after = await probe();
  expect(own(after.drafts, draft.id)).toMatchObject({ status: 'APPLIED', applied_mention_id: result.mentionId });
  expect(own(after.mentions, result.mentionId)).toMatchObject({ timestamp_seconds: null, verification_status: 'APPROVED', publication_status: 'PUBLISHED' });
  expect(after.audits.filter(a => a.draft_id === draft.id).length).toBeGreaterThanOrEqual(3);
  expect(after.receipts.length - state.receipts.length).toBe(1);
  const published = (await publicData(page)).find(r => r.id === result.restaurantId)!;
  expect(published.mentions).toHaveLength(1); expect(published.mentions[0].timestampSeconds).toBeNull();
  await expect(page.getByRole('button', { name: '저장한 식당 상세로 이동', exact: true })).toBeEnabled();
  await detail(page, 'A1 식당');
  await expect(page.getByText('시점 미확인', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: /영상 확인 ·/ })).toHaveAttribute('href', 'https://www.youtube.com/watch?v=E2EVIDEO006');
});

test('A3 초안 없는 직접 승인 · 0초 표시와 출처 URL', async ({ page }) => {
  await login(page); const before = await probe(); await register(page, 'A3'); await relationship(page, true);
  await page.getByRole('button', { name: '승인 전 검토', exact: true }).click();
  const response = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/api/admin/mentions'));
  await page.getByRole('button', { name: '직접 승인·공개', exact: true }).click();
  expect((await response).status()).toBe(201); const result = await (await response).json();
  const after = await probe(); expect(after.drafts).toEqual(before.drafts);
  expect(after.mentions.filter(m => m.restaurant_id === result.restaurantId)).toHaveLength(1);
  expect(own(after.mentions, result.mentionId).timestamp_seconds).toBe(0);
  expect(after.receipts.length - before.receipts.length).toBe(1);
  // The write response precedes the public refresh; wait for the UI leave guard.
  await expect(page.getByRole('button', { name: '저장한 식당 상세로 이동', exact: true })).toBeEnabled();
  await detail(page, 'A3 식당'); await expect(page.getByText('00:00', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: /영상 확인 ·/ })).toHaveAttribute('href', /&t=0s$/);
  expect((await publicData(page)).find(r => r.id === result.restaurantId)!.mentions[0].timestampSeconds).toBe(0);
});

test('B1 B4 관계 정정은 재승인 후 공개 · 원본 근거와 이력 보존', async ({ page }) => {
  await login(page); const { data, mentions } = await fixture('B1 식당'); const original = mentions[0];
  const beforePublic = await publicData(page); await detail(page, 'B1 식당');
  await page.getByRole('button', { name: `관계 #${original.id} 정정 · 이력`, exact: true }).click();
  await page.getByLabel('정정 유형').selectOption('RECOMMENDED');
  await saveCorrection(page, 'B1 근거 재확인');
  expect(own((await probe()).mentions, original.id)).toEqual(original);
  expect(await publicData(page)).toEqual(beforePublic);
  await approveCorrection(page);
  const after = await probe(); const changed = own(after.mentions, original.id);
  expect(changed.mention_type).toBe('RECOMMENDED'); expect(changed.version).toBe(Number(original.version) + 1);
  for (const field of ['evidence', 'restaurant_candidate_id', 'chef_id', 'restaurant_id', 'video_id', 'timestamp_seconds', 'review_note']) expect(changed[field]).toEqual(original[field]);
  expect(after.candidates).toEqual(data.candidates);
  const event = page.locator('details').filter({ has: page.locator('summary', { hasText: 'B1 근거 재확인' }) }).first();
  await event.locator(':scope > summary').click();
  await expect(event.getByText('변경 전', { exact: true })).toBeVisible();
  await expect(event.getByText('변경 후', { exact: true })).toBeVisible();
  await expect(event.getByRole('heading', { name: '승인 당시 영향 확인' })).toBeVisible();
  await expect(event.locator(':scope > summary')).toContainText('e2e-admin');
  expect((await publicData(page)).find(r => r.id === original.restaurant_id)!.mentions[0].mentionType).toBe('RECOMMENDED');
});

test('B2 공유 셰프 영향 두 관계 확인 후 이름 재승인', async ({ page }) => {
  await login(page); const before = await probe(); const chef = before.chefs.find(c => c.name === 'B2 공유 셰프')!;
  const unrelated = before.chefs.find(c => c.name === '무관 셰프')!; const published = await publicData(page);
  await detail(page, 'B2 식당');
  await page.getByRole('button', { name: `셰프 #${chef.id} 이름 정정 · 이력`, exact: true }).click();
  await page.getByLabel('같은 사람의 이름 표기').fill('B2 변경 셰프'); await saveCorrection(page, 'B2 이름 확인');
  await expect(page.getByText('연결 관계 2개 · 공개 영향 2개', { exact: true })).toBeVisible();
  expect(await publicData(page)).toEqual(published); await approveCorrection(page);
  const after = await probe(); expect(own(after.chefs, chef.id).name).toBe('B2 변경 셰프');
  expect(own(after.chefs, unrelated.id)).toEqual(unrelated); expect(after.mentions).toEqual(before.mentions);
  const sources = (await publicData(page)).flatMap(r => r.mentions);
  expect(sources.filter(s => s.chefName === 'B2 변경 셰프')).toHaveLength(2);
  expect(sources.filter(s => s.chefName === '무관 셰프')).toHaveLength(1);
});

test('B3 공식 장소 전체 스냅샷 · null 포함 재승인', async ({ page }) => {
  await login(page); const before = await fixture('B3 식당'); const published = await publicData(page);
  await detail(page, 'B3 식당'); await page.getByRole('button', { name: '식당 공식 정보 정정 · 이력', exact: true }).click();
  await page.getByLabel('같은 Kakao 장소 검색').fill('B3-updated');
  await page.getByRole('button', { name: '정정 장소 찾기', exact: true }).click();
  await page.getByRole('button', { name: /B3 공식 변경 ·.*Kakao ID 21003/ }).click();
  await saveCorrection(page, 'B3 공식 전체 스냅샷');
  expect(own((await probe()).restaurants, before.restaurant.id)).toEqual(before.restaurant);
  expect(await publicData(page)).toEqual(published); await approveCorrection(page);
  expect(own((await probe()).restaurants, before.restaurant.id)).toMatchObject({ kakao_place_id: '21003', name: 'B3 공식 변경', road_address: null, address: null, longitude: 128, latitude: 38, place_url: 'https://place.map.kakao.com/21003', version: 1 });
  expect((await publicData(page)).find(r => r.id === before.restaurant.id)).toMatchObject({ name: 'B3 공식 변경', roadAddress: null, address: null, longitude: 128, latitude: 38 });
});

test('C1 C2 C3 일부·마지막 중단 → 명시적 재확인 복구', async ({ page }) => {
  await login(page); const original = await fixture('C 식당'); const other = await fixture('C-other 식당');
  await detail(page, 'C 식당');
  for (const [index, mention] of original.mentions.entries()) {
    await page.getByRole('button', { name: `관계 #${mention.id} 공개 중단`, exact: true }).click();
    const dialog = page.getByRole('dialog', { name: '관계 공개 제어' });
    await dialog.getByLabel('처리 사유', { exact: true }).fill(`C 중단 ${index}`);
    await expect(dialog.getByText(/현재 APPROVED \/ PUBLISHED/)).toBeVisible();
    const response = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith(`/${mention.id}/suspend`));
    await dialog.getByRole('button', { name: '이 관계만 공개 중단', exact: true }).click();
    expect((await response).status()).toBe(200);
    await expect(dialog.getByText('공개 제어 요청이 완료됐습니다.', { exact: false })).toBeVisible();
    const visible = (await publicData(page)).find(r => r.id === original.restaurant.id);
    if (index === 0) { expect(visible!.mentions).toHaveLength(1); expect(visible!.mentions[0].mentionId).toBe(original.mentions[1].id); }
    else expect(visible).toBeUndefined();
    expect((await publicData(page)).find(r => r.id === other.restaurant.id)!.mentions).toHaveLength(1);
    await dialog.getByRole('button', { name: '닫기', exact: true }).click();
  }
  const id = original.mentions[1].id;
  await expect(page.getByText('공개 관계 0 / 전체 관계 2', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '맛집 지도', exact: true }).click();
  await page.getByLabel('식당, 지역, 셰프 검색').fill('C 식당');
  await expect(page.locator('.restaurants .restaurant')).toHaveCount(0);
  await page.getByRole('button', { name: '관리자', exact: true }).click(); await detail(page, 'C 식당');
  await page.getByRole('button', { name: `관계 #${id} 공개 복구`, exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '관계 공개 제어' });
  await expect(dialog.getByText(/현재 APPROVED \/ SUSPENDED/)).toBeVisible();
  await dialog.getByLabel('처리 사유', { exact: true }).fill('C 명시적 복구');
  await dialog.getByRole('button', { name: '명시적 재승인 후 복구', exact: true }).click();
  await expect(dialog.getByRole('alert')).toContainText('필수 재확인');
  expect(own((await probe()).mentions, id).publication_status).toBe('SUSPENDED');
  await dialog.getByLabel('현재 셰프·지점·영상·유형·시점의 근거를 다시 확인했습니다.').check();
  await dialog.getByLabel('재확인 내용', { exact: true }).fill('C 현재 근거 확인');
  const response = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith(`/${id}/restore`));
  await dialog.getByRole('button', { name: '명시적 재승인 후 복구', exact: true }).click();
  expect((await response).status()).toBe(200); const result = await (await response).json(); const state = await probe();
  expect(own(state.mentions, id)).toMatchObject({ publication_status: 'PUBLISHED', version: 2 });
  expect(state.audits.some(a => a.id === result.auditEventId && a.reason === 'C 명시적 복구')).toBe(true);
  expect(state.receipts.length - original.data.receipts.length).toBe(3);
  for (const field of ['evidence', 'restaurant_candidate_id', 'chef_id', 'restaurant_id', 'video_id', 'timestamp_seconds', 'mention_type', 'review_note']) expect(own(state.mentions, id)[field]).toEqual(original.mentions[1][field]);
  expect((await publicData(page)).find(r => r.id === original.restaurant.id)!.mentions.map(s => s.mentionId)).toEqual([id]);
  await expect(dialog.getByText('C 명시적 복구', { exact: true }).first()).toBeVisible();
  await dialog.getByRole('button', { name: '닫기', exact: true }).click();
  await page.getByRole('button', { name: '맛집 지도', exact: true }).click();
  await page.getByLabel('식당, 지역, 셰프 검색').fill('C 식당');
  await expect(page.locator('.restaurants .restaurant')).toHaveCount(1);
});

test('보안 실제 세션 로그인·로그아웃·서버 만료·CSRF 403', async ({ page }) => {
  expect((await page.request.get('/api/admin/chefs')).status()).toBe(401);
  await login(page);
  expect((await page.request.post('/api/admin/chefs', { data: { name: 'CSRF 없이 생성 불가' } })).status()).toBe(403);
  expect((await probe()).chefs.some(c => c.name === 'CSRF 없이 생성 불가')).toBe(false);
  await page.getByRole('button', { name: '로그아웃', exact: true }).click();
  await expect(page.getByRole('heading', { name: '관리자 로그인', exact: true })).toBeVisible();
  expect((await page.request.get('/api/admin/chefs')).status()).toBe(401);
  await page.getByLabel('아이디', { exact: true }).fill('e2e-admin');
  await page.getByLabel('비밀번호', { exact: true }).fill(runtime().password);
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible();
  await register(page, 'SECURITY'); await page.getByLabel('검증 메모 (선택)').fill('만료 후 보존 입력');
  await probe('expire');
  await page.getByRole('button', { name: '임시 저장', exact: true }).click();
  await expect(page.getByRole('heading', { name: '관리자 로그인', exact: true })).toBeVisible();
  await page.getByLabel('아이디', { exact: true }).fill('e2e-admin');
  await page.getByLabel('비밀번호', { exact: true }).fill(runtime().password);
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  // Session-bound place selections are deliberately cleared on expiry.
  await page.getByLabel('식당 이름 검색').fill('SECURITY');
  await page.getByRole('button', { name: '장소 검색', exact: true }).click();
  await page.getByRole('button', { name: '이 지점 선택', exact: true }).click();
  await expect(page.getByLabel('검증 메모 (선택)')).toHaveValue('만료 후 보존 입력');
});

test('충돌 두 실제 로그인 context · stale version과 입력 보존', async ({ page, browser }) => {
  await login(page); await register(page, 'CONFLICT');
  const response = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/mention-drafts'));
  await page.getByRole('button', { name: '임시 저장', exact: true }).click(); const draft = await (await response).json();
  const second: BrowserContext = await browser.newContext({ baseURL: 'http://127.0.0.1:18173' });
  try {
    await isolate(second);
    const other = await second.newPage(); await login(other, 'e2e-other');
    await other.goto(`/?draftId=${draft.id}`);
    await expect(other.getByRole('button', { name: '수정 저장', exact: true })).toBeVisible();
    await page.getByLabel('검증 메모 (선택)').fill('첫 관리자 저장');
    const firstPatch = page.waitForResponse(r => r.request().method() === 'PATCH');
    await page.getByRole('button', { name: '수정 저장', exact: true }).click(); expect((await firstPatch).status()).toBe(200);
    await other.getByLabel('검증 메모 (선택)').fill('둘째 관리자 보존');
    const conflict = other.waitForResponse(r => r.request().method() === 'PATCH');
    await other.getByRole('button', { name: '수정 저장', exact: true }).click(); expect((await conflict).status()).toBe(409);
    await expect(other.getByLabel('검증 메모 (선택)')).toHaveValue('둘째 관리자 보존');
    expect(own((await probe()).drafts, draft.id)).toMatchObject({ version: 1, verification_note: '첫 관리자 저장' });
    await expect(other.getByRole('alert').first()).toBeVisible();
  } finally { await second.close(); }
});
