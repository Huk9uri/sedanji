import React from 'react';
import { act, cleanup, fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api';
import type { ApiOptions } from '../../api';
import { useExplore } from './useExplore';
import { useLocation } from './useLocation';
import { usePublicDetail } from './usePublicDetail';
import ExplorePage from './ExplorePage';
import ChefFilter from './ChefFilter';
import { initialBounds, defaultCenter, youtubeLink } from './contracts';
import type { Area, Bounds, Detail, Item } from './contracts';

const { mocked } = vi.hoisted(() => ({ mocked: vi.fn<(path: string, options?: ApiOptions) => Promise<unknown>>() }));
vi.mock('../../api', async original => ({ ...await original<typeof import('../../api')>(), api: mocked }));
vi.mock('./ExploreMap', () => ({ default: ({ items, onReady, onBounds, onSelect }: { items: Item[]; onReady: (b: Bounds, failed: boolean) => void; onBounds: (b: Bounds) => void; onSelect: (id: number) => void }) => {
  React.useEffect(() => onReady(initialBounds(defaultCenter), true), [onReady]);
  return <div><span>{items.length} markers</span><button onClick={() => onBounds({ west: 127, east: 127.02, south: 37, north: 37.02 })}>지도 이동</button>{items.map(r => <button key={r.id} onClick={() => onSelect(r.id)}>핀 {r.id}</button>)}</div>;
} }));
const bounds = initialBounds(defaultCenter);
const query = { ...bounds, chefId: null };
const item: Item = { id: 10, name: '공개 식당', latitude: 37.5665, longitude: 126.978, roadAddress: '도로', address: '지번', chefs: [{ id: 1, name: '셰프' }], chefCount: 1, hasMoreChefs: false };
const area: Area = { items: [item], query, returnedCount: 1, limit: 200, truncated: false, totalCount: null };
const detail: Detail = { restaurant: { id: item.id, name: item.name, latitude: item.latitude, longitude: item.longitude, roadAddress: item.roadAddress, address: item.address, kakaoPlaceId: '123', placeUrl: 'https://place.map.kakao.com/123' }, sources: { items: [{ mentionId: 1, chef: { id: 1, name: '셰프' }, mentionType: 'VISITED', video: { id: 'PUBVIDEO001', title: '공식 출처', channelTitle: '채널', publishedAt: '2026-11-01T00:00:00Z' }, timestampSeconds: 0 }], totalElements: 1, page: 0, size: 20, hasNext: false } };
function deferred<T>() { let resolve!: (value: T) => void; let reject!: (reason: Error) => void; const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; }
beforeEach(() => { mocked.mockReset(); });
afterEach(() => { cleanup(); vi.useRealTimers(); vi.unstubAllGlobals(); });

describe('request generations and previous results', () => {
  it('keeps successful results and conditions on failure; drops late success and failure', async () => {
    const first = deferred<Area>(); const second = deferred<Area>();
    mocked.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    const { result } = renderHook(useExplore);
    act(() => { void result.current.request(query); void result.current.request({ ...query, chefId: 1 }); });
    await act(async () => second.resolve({ ...area, query: { ...query, chefId: 1 } }));
    await act(async () => first.reject(new Error('오래된 실패')));
    expect(result.current.error).toBe(''); expect(result.current.lastSuccess?.query.chefId).toBe(1);
    mocked.mockRejectedValueOnce(new Error('최신 실패'));
    await act(async () => result.current.request(query));
    expect(result.current.error).toBe('최신 실패'); expect(result.current.lastSuccess?.items).toEqual([item]);
    const stale = deferred<Area>(); mocked.mockReturnValueOnce(stale.promise).mockResolvedValueOnce({ ...area, items: [], returnedCount: 0 });
    act(() => { void result.current.request(query); void result.current.request(query); });
    await waitFor(() => expect(result.current.lastSuccess?.returnedCount).toBe(0));
    await act(async () => stale.resolve(area)); expect(result.current.lastSuccess?.returnedCount).toBe(0);
  });
  it('invalidates memory and aborts active requests after an admin success', async () => {
    mocked.mockResolvedValue(area); const { result } = renderHook(useExplore);
    await act(async () => result.current.request(query));
    const pending = deferred<Area>(); mocked.mockReturnValueOnce(pending.promise).mockResolvedValueOnce({ ...area, items: [], returnedCount: 0 });
    act(() => { void result.current.request(query); });
    const signal = mocked.mock.calls.at(-1)?.[1]?.signal;
    await act(async () => result.current.invalidate()); expect(signal?.aborted).toBe(true);
    await act(async () => pending.resolve(area)); expect(result.current.lastSuccess?.items).toEqual([]);
  });
  it('does not let an older area snapshot restore a restaurant confirmed unavailable', async () => {
    mocked.mockResolvedValueOnce(area); const { result } = renderHook(useExplore);
    await act(async () => result.current.request(query));
    const older = deferred<Area>(); mocked.mockReturnValueOnce(older.promise);
    act(() => { void result.current.request(query); result.current.removeUnavailable(10); });
    await act(async () => older.resolve(area)); expect(result.current.lastSuccess?.items).toEqual([]);
    mocked.mockResolvedValueOnce(area); await act(async () => result.current.request(query)); expect(result.current.lastSuccess?.items).toEqual([item]);
  });
});

describe('location states', () => {
  it('distinguishes refusal, success, failure and timeout and rejects late coordinates', () => {
    vi.useFakeTimers(); let success!: PositionCallback; let failure!: PositionErrorCallback;
    vi.stubGlobal('navigator', { geolocation: { getCurrentPosition: vi.fn((yes: PositionCallback, no: PositionErrorCallback) => { success = yes; failure = no; }) } });
    const { result } = renderHook(useLocation);
    act(() => result.current.locate()); act(() => failure({ code: 1 } as GeolocationPositionError));
    expect(result.current.state).toBe('denied'); expect(result.current.center).toEqual(defaultCenter);
    act(() => result.current.locate()); act(() => success({ coords: { latitude: 35, longitude: 128 } } as GeolocationPosition));
    expect(result.current.state).toBe('located');
    act(() => result.current.locate()); act(() => failure({ code: 2 } as GeolocationPositionError)); expect(result.current.state).toBe('failed');
    act(() => result.current.locate()); act(() => vi.advanceTimersByTime(8000)); expect(result.current.state).toBe('timeout');
    act(() => success({ coords: { latitude: 35, longitude: 128 } } as GeolocationPosition)); expect(result.current.center).toBeNull();
    act(() => result.current.fallback()); expect(result.current.state).toBe('default');
  });
});

it('detail keeps server failure separate from confirmed unavailability and drops stale reads', async () => {
  const unavailable = vi.fn(); const old = deferred<Detail>(); mocked.mockReturnValueOnce(old.promise).mockResolvedValueOnce({ ...detail, restaurant: { ...detail.restaurant, id: 20 } });
  const { result } = renderHook(() => usePublicDetail(unavailable));
  act(() => { result.current.open(10); result.current.open(20); }); await waitFor(() => expect(result.current.detail?.restaurant.id).toBe(20));
  await act(async () => old.resolve(detail)); expect(result.current.detail?.restaurant.id).toBe(20);
  mocked.mockRejectedValueOnce(new ApiError(500, '서버 실패')); await act(async () => result.current.load(20, 0));
  expect(unavailable).not.toHaveBeenCalled(); expect(result.current.detail?.restaurant.id).toBe(20);
  mocked.mockRejectedValueOnce(new ApiError(404, '공개된 식당을 찾을 수 없습니다.', { code: 'RESOURCE_NOT_FOUND' })); await act(async () => result.current.load(20, 0));
  expect(result.current.detail).toBeNull(); expect(unavailable).toHaveBeenCalledWith(20);
});

it('preserves null, zero and positive official video timestamps', () => {
  expect(youtubeLink('PUBVIDEO001', null)).toBe('https://www.youtube.com/watch?v=PUBVIDEO001');
  expect(youtubeLink('PUBVIDEO001', 0)).toBe('https://www.youtube.com/watch?v=PUBVIDEO001&t=0s');
  expect(youtubeLink('PUBVIDEO001', 793)).toBe('https://www.youtube.com/watch?v=PUBVIDEO001&t=793s');
});

it('keeps a selected chef when the refreshed global list no longer contains it', async () => {
  mocked.mockResolvedValueOnce({ items: [{ id: 1, name: '셰프', restaurantCount: 1 }], page: 0, size: 50, totalElements: 1, hasNext: false });
  const change = vi.fn(); const view = render(<ChefFilter selected={1} onChange={change} revision={0}/>);
  await screen.findByRole('option', { name: '셰프 · 공개 식당 1곳' });
  mocked.mockResolvedValueOnce({ items: [], page: 0, size: 50, totalElements: 0, hasNext: false });
  view.rerender(<ChefFilter selected={1} onChange={change} revision={1}/>);
  await screen.findByRole('option', { name: /선택 유지/ }); expect(screen.getByLabelText('셰프 필터')).toHaveValue('1'); expect(change).not.toHaveBeenCalled();
});

it('queries only on initial bounds, explicit search and chef change; restores detail entry focus', async () => {
  const nextSource = { ...detail.sources.items[0], mentionId: 2, timestampSeconds: null,
    video: { ...detail.sources.items[0].video, title: '다음 영상' } };
  const firstPage = { ...detail, sources: { ...detail.sources, totalElements: 21, hasNext: true } };
  const nextPage = { ...detail, sources: { ...detail.sources, items: [nextSource], totalElements: 21, page: 1 } };
  mocked.mockImplementation(async path => path.startsWith('/public/chefs') ? { items: [{ id: 1, name: '셰프', restaurantCount: 1 }], totalElements: 1, page: 0, size: 50, hasNext: false } : path.startsWith('/public/restaurants/10?') ? (path.includes('sourcePage=1') ? nextPage : firstPage) : area);
  render(<ExplorePage active registerRefresh={() => {}}/>);
  fireEvent.click(screen.getByRole('button', { name: '서울 시청 기본 지역' }));
  await screen.findByText('1 markers');
  const areaCalls = () => mocked.mock.calls.filter(([p]) => p.startsWith('/public/restaurants?'));
  expect(areaCalls()).toHaveLength(1); fireEvent.click(screen.getByRole('button', { name: '지도 이동' })); expect(areaCalls()).toHaveLength(1);
  fireEvent.click(screen.getByRole('button', { name: '이 지역에서 검색' })); await waitFor(() => expect(areaCalls()).toHaveLength(2));
  expect(areaCalls()[1][0]).toContain('west=127');
  const entry = screen.getByRole('button', { name: '공개 식당' }); entry.focus(); fireEvent.click(entry);
  await screen.findByRole('link', { name: '공식 출처 ↗' }); expect(screen.getByRole('heading', { name: '공개 식당' })).toHaveFocus();
  const panel = screen.getByRole('region', { name: '식당 상세' });
  expect(panel).toHaveTextContent('도로');
  expect(panel).toHaveTextContent('지번');
  expect(screen.getByRole('link', { name: '공식 출처 ↗' })).toHaveAttribute('href', youtubeLink('PUBVIDEO001', 0));
  fireEvent.click(screen.getByRole('button', { name: '다음 출처' }));
  expect(await screen.findByRole('link', { name: '다음 영상 ↗' })).toHaveAttribute('href', youtubeLink('PUBVIDEO001', null));
  expect(mocked).toHaveBeenCalledWith('/public/restaurants/10?sourcePage=1&sourceSize=20', expect.anything());
  expect(screen.getByText('2페이지')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '다음 출처' })).toBeDisabled();
  fireEvent.keyDown(window, { key: 'Escape' }); await waitFor(() => expect(entry).toHaveFocus());
  fireEvent.change(screen.getByLabelText('셰프 필터'), { target: { value: '1' } }); await waitFor(() => expect(areaCalls()).toHaveLength(3)); expect(areaCalls()[2][0]).toContain('chefId=1');
  expect(screen.getByText(/초기 지역 목록입니다/)).toBeInTheDocument();
});
