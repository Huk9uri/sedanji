import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import type { KakaoSdk } from '../src/kakao';
declare global { interface Window { kakao?: KakaoSdk } }

const item = { id: 10, name: '모의 공개 식당', latitude: 37.5665, longitude: 126.978, roadAddress: '모의 도로', address: '모의 지번', chefs: [{ id: 1, name: '모의 셰프' }], chefCount: 1, hasMoreChefs: false };
const sources = [null, 0, 793].map((seconds, index) => ({ mentionId: index + 1, chef: { id: index === 2 ? 2 : 1, name: `모의 셰프 ${index}` }, mentionType: 'VISITED', timestampSeconds: seconds,
  video: { id: 'PUBVIDEO001', title: `모의 영상 ${index}`, channelTitle: '모의 채널', publishedAt: '2026-11-01T00:00:00Z' } }));

async function prepare(page: Page, sdk = true, permission: 'success' | 'denied' | 'timeout' = 'success') {
  await page.addInitScript(({ sdk, permission }) => {
    Object.defineProperty(navigator, 'geolocation', { configurable: true, value: { getCurrentPosition: (yes: PositionCallback, no: PositionErrorCallback) => {
      if (permission === 'success') yes({ coords: { latitude: 37.5665, longitude: 126.978 } } as GeolocationPosition);
      if (permission === 'denied') no({ code: 1 } as GeolocationPositionError);
      if (permission === 'timeout') setTimeout(() => yes({ coords: { latitude: 35, longitude: 128 } } as GeolocationPosition), 12000);
    } } });
    if (!sdk) return;
    class Point { constructor(private lat: number, private lng: number) {} getLat() { return this.lat; } getLng() { return this.lng; } }
    class Bounds { extend() {} getSouthWest() { return new Point(37.55, 126.96); } getNorthEast() { return new Point(37.58, 126.99); } }
    class Map { setBounds() {} panTo() {} relayout() {} getBounds() { return new Bounds(); } }
    class Marker { setMap() {} }
    window.kakao = { maps: { load: callback => callback(), LatLng: Point, LatLngBounds: Bounds, Map, Marker,
      event: { addListener: (_target, name, callback) => { if (name === 'idle') window.addEventListener('mock-map-move', callback); },
        removeListener: (_target, name, callback) => { if (name === 'idle') window.removeEventListener('mock-map-move', callback); } } } };
  }, { sdk, permission });
  const requests: URL[] = []; let failList = false; let unavailable = false;
  await page.route('**/*', async route => {
    const url = new URL(route.request().url());
    if (url.origin !== 'http://127.0.0.1:18173') return route.abort();
    if (url.pathname === '/api/public/chefs') return route.fulfill({ json: { items: [{ id: 1, name: '모의 셰프', restaurantCount: 1 }], page: 0, size: 50, totalElements: 1, hasNext: false } });
    if (url.pathname === '/api/public/restaurants') {
      requests.push(url);
      return route.fulfill({ status: failList ? 500 : 200, json: failList ? { message: '모의 조회 실패' } : { items: [item], query: { west: Number(url.searchParams.get('west')), south: Number(url.searchParams.get('south')), east: Number(url.searchParams.get('east')), north: Number(url.searchParams.get('north')), chefId: url.searchParams.has('chefId') ? Number(url.searchParams.get('chefId')) : null }, returnedCount: 1, limit: 200, truncated: false, totalCount: null } });
    }
    if (url.pathname === '/api/public/restaurants/10') return route.fulfill({ status: unavailable ? 404 : 200, json: unavailable ? { code: 'RESOURCE_NOT_FOUND', message: '공개된 식당을 찾을 수 없습니다.' } : { restaurant: { id: item.id, name: item.name, latitude: item.latitude, longitude: item.longitude, roadAddress: item.roadAddress, address: item.address, kakaoPlaceId: '123', placeUrl: 'https://place.map.kakao.com/123' }, sources: { items: sources, page: 0, size: 20, hasNext: false, totalElements: 3 } } });
    return route.continue();
  });
  return { requests, fail: () => { failList = true; }, hide: () => { unavailable = true; } };
}

test('모의 공개 지도: 초기 조회·명시적 재검색·즉시 셰프 필터·실패 보존', async ({ page }) => {
  const state = await prepare(page); await page.goto('/');
  await page.getByRole('button', { name: '내 위치로 시작', exact: true }).click();
  await expect(page.getByRole('button', { name: item.name, exact: true })).toBeVisible(); expect(state.requests).toHaveLength(1);
  await page.evaluate(() => window.dispatchEvent(new Event('mock-map-move')));
  await expect(page.getByText('식당 조회 중', { exact: true })).toHaveCount(0); expect(state.requests).toHaveLength(1);
  await page.getByRole('button', { name: '이 지역에서 검색', exact: true }).click(); await expect.poll(() => state.requests.length).toBe(2);
  const filter = page.getByRole('combobox', { name: '셰프 필터', exact: true });
  await expect(filter).toBeVisible();
  await expect(filter.locator('option[value="1"]')).toHaveCount(1);
  await filter.selectOption('1', { timeout: 10000 }); await expect.poll(() => state.requests.length).toBe(3);
  expect(state.requests[2].searchParams.get('chefId')).toBe('1'); state.fail();
  await page.getByRole('button', { name: '이 지역에서 검색', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('모의 조회 실패'); await expect(page.getByRole('button', { name: item.name, exact: true })).toBeVisible();
  await expect(page.getByText(/이전 성공 결과 조건/)).toBeVisible();
});

test('모의 공개 상세: 모든 출처·null/0/793 공식 링크·포커스·404 제거', async ({ page }) => {
  const state = await prepare(page); await page.goto('/'); await page.getByRole('button', { name: '서울 시청 기본 지역', exact: true }).click();
  const entry = page.getByRole('button', { name: item.name, exact: true }); await entry.click();
  await expect(page.getByRole('heading', { name: item.name, exact: true })).toBeFocused();
  for (const [index, suffix] of ['', '&t=0s', '&t=793s'].entries()) await expect(page.getByRole('link', { name: `모의 영상 ${index} ↗`, exact: true })).toHaveAttribute('href', 'https://www.youtube.com/watch?v=PUBVIDEO001' + suffix);
  await page.keyboard.press('Escape'); await expect(entry).toBeFocused(); state.hide(); await entry.click();
  await expect(page.getByRole('alert')).toContainText('공개된 식당을 찾을 수 없습니다.');
  await expect(page.getByRole('link', { name: /모의 영상/ })).toHaveCount(0);
  await page.getByRole('button', { name: '상세 닫기', exact: true }).click(); await expect(entry).toHaveCount(0);
});

test('모의 SDK 실패·위치 거부: 기본 지역 목록과 공식 링크 유지', async ({ page }) => {
  await prepare(page, false, 'denied'); await page.goto('/'); await page.getByRole('button', { name: '내 위치로 시작', exact: true }).click();
  await expect(page.getByText('위치 권한 거부 · 서울 시청 기본 지역', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: item.name, exact: true }).click();
  await expect(page.getByRole('link', { name: '카카오맵에서 보기 ↗', exact: true })).toHaveAttribute('href', 'https://place.map.kakao.com/123');
  await expect(page.getByText(/초기 지역 목록입니다/)).toBeVisible();
});

test('모의 위치 8초 timeout: 늦은 성공 무시·기본 지역 선택', async ({ page }) => {
  await page.clock.install(); const state = await prepare(page, false, 'timeout'); await page.goto('/');
  await page.getByRole('button', { name: '내 위치로 시작', exact: true }).click(); await page.clock.fastForward(8001);
  await expect(page.getByText(/위치 요청 시간 초과/)).toBeVisible(); await page.clock.fastForward(5000); expect(state.requests).toHaveLength(0);
  await page.getByRole('button', { name: '서울 시청 기본 지역', exact: true }).click(); await expect.poll(() => state.requests.length).toBe(1);
});

test('모의 반응형: 900px 분기와 단일 결과·상세 패널', async ({ page }) => {
  await prepare(page); await page.setViewportSize({ width: 390, height: 844 }); await page.goto('/');
  await page.getByRole('button', { name: '서울 시청 기본 지역', exact: true }).click();
  await expect(page.getByRole('button', { name: item.name, exact: true })).toBeVisible();
  expect(await page.locator('.public-layout').evaluate(e => getComputedStyle(e).display)).toBe('flex');
  await page.setViewportSize({ width: 1200, height: 900 });
  expect(await page.locator('.public-layout').evaluate(e => getComputedStyle(e).display)).toBe('grid');
  await page.getByRole('button', { name: item.name, exact: true }).click(); await expect(page.getByRole('region', { name: '식당 상세' })).toHaveCount(1);
});
