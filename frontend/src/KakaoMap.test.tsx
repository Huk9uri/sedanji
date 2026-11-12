import React from 'react';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import KakaoMap from './KakaoMap';
import type { Item } from './features/explore/contracts';
import type { KakaoMap as MapInstance, KakaoMarker } from './kakao';
afterEach(() => { cleanup(); vi.unstubAllEnvs(); vi.unstubAllGlobals(); delete window.kakao; });
const center = { latitude: 37.5665, longitude: 126.978 };
const item: Item = { id: 1, name: '식당', roadAddress: null, address: null, latitude: 37.554615, longitude: 126.90687, chefs: [], chefCount: 0, hasMoreChefs: false };
function sdk() {
  vi.stubEnv('VITE_KAKAO_JAVASCRIPT_KEY', 'test-browser-key');
  const LatLng = vi.fn(function(latitude: number, longitude: number) { return { getLat: () => latitude, getLng: () => longitude }; });
  class Bounds { extend = vi.fn(); getSouthWest() { return new LatLng(37.55,126.96); } getNorthEast() { return new LatLng(37.58,126.99); } }
  const instance = { setBounds: vi.fn(), panTo: vi.fn(), getBounds: vi.fn(() => new Bounds()), relayout: vi.fn() };
  const setMap = vi.fn();
  const Marker = vi.fn(function() { return { setMap }; });
  const Map = vi.fn(function() { return instance; });
  const listeners: { target: KakaoMarker | MapInstance; name: string; callback: () => void }[] = [];
  const removeListener = vi.fn();
  let resized = () => {};
  const disconnect = vi.fn();
  vi.stubGlobal('ResizeObserver', class { constructor(callback: () => void) { resized = callback; } observe() {} disconnect = disconnect; });
  window.kakao = { maps: { load: callback => callback(), Map, LatLng, Marker, LatLngBounds: Bounds,
    event: { addListener: (target,name,callback) => { listeners.push({target,name,callback}); }, removeListener } } };
  return { LatLng, Marker, setMap, instance, listeners, removeListener, disconnect, resize: () => resized() };
}
it('uses latitude/longitude, initializes camera once, and never fits results or selection', async () => {
  const mock = sdk(); const onBounds = vi.fn(); const onReady = vi.fn();
  const props = { center, items: [item], onBounds, onReady, onFailure: vi.fn(), onSelect: vi.fn() };
  const view = render(<KakaoMap {...props}/>);
  await waitFor(() => expect(mock.Marker).toHaveBeenCalledTimes(1));
  expect(mock.LatLng).toHaveBeenCalledWith(37.554615, 126.90687); expect(onReady).toHaveBeenCalledTimes(1);
  view.rerender(<KakaoMap {...props} items={[{ ...item, id: 2 }]}/>);
  await waitFor(() => expect(mock.Marker).toHaveBeenCalledTimes(2)); expect(mock.instance.setBounds).toHaveBeenCalledTimes(1);
  mock.listeners.find(l => l.name === 'idle')!.callback(); mock.resize();
  expect(onBounds).toHaveBeenCalledTimes(2); expect(onReady).toHaveBeenCalledTimes(1); expect(mock.instance.relayout).toHaveBeenCalledTimes(1);
  view.unmount(); expect(mock.removeListener).toHaveBeenCalled(); expect(mock.disconnect).toHaveBeenCalled();
  expect(mock.instance.panTo).not.toHaveBeenCalled();
});
it('offers a selection list for coincident pins and removes marker listeners', async () => {
  const mock = sdk(); const onSelect = vi.fn();
  const view = render(<KakaoMap center={center} items={[item,{...item,id:2,name:'두 번째 식당'}]} onBounds={vi.fn()} onReady={vi.fn()} onFailure={vi.fn()} onSelect={onSelect}/>);
  await waitFor(() => expect(mock.Marker).toHaveBeenCalledTimes(1));
  const click = mock.listeners.find(l => l.name === 'click')!;
  // A real SDK click is delivered outside React's event system.
  act(() => click.callback());
  fireEvent.click(screen.getByRole('button',{name:'두 번째 식당'})); expect(onSelect).toHaveBeenCalledWith(2);
  view.unmount(); expect(mock.removeListener).toHaveBeenCalledWith(click.target,'click',click.callback);
});
it('returns an explicit initial rectangle on first SDK failure', async () => {
  vi.stubEnv('VITE_KAKAO_JAVASCRIPT_KEY',''); const onReady=vi.fn();
  render(<KakaoMap center={center} items={[]} onBounds={vi.fn()} onReady={onReady} onFailure={vi.fn()} onSelect={vi.fn()}/>);
  await waitFor(()=>expect(onReady).toHaveBeenCalledTimes(1));
  expect(onReady.mock.calls[0][0].south).toBeCloseTo(37.5575); expect(onReady.mock.calls[0][0].north).toBeCloseTo(37.5755);
  expect(onReady.mock.calls[0][1]).toBe(true);
  expect(screen.getByRole('status')).toHaveTextContent('지도 연결 실패 · 초기 지역 목록');
});

it('disposes the idle listener and observer when initial bounds cannot be read', async () => {
  const mock = sdk();
  mock.instance.getBounds.mockImplementation(() => { throw new Error('invalid bounds'); });
  const onReady = vi.fn();
  const onFailure = vi.fn();
  render(<KakaoMap center={center} items={[item]} onBounds={vi.fn()} onReady={onReady} onFailure={onFailure} onSelect={vi.fn()}/>);
  await waitFor(() => expect(onReady).toHaveBeenCalledTimes(1));
  expect(onReady.mock.calls[0][1]).toBe(true);
  expect(onFailure).not.toHaveBeenCalled();
  expect(mock.disconnect).toHaveBeenCalledTimes(1);
  expect(mock.removeListener).toHaveBeenCalledWith(mock.instance, 'idle', mock.listeners[0].callback);
  expect(mock.Marker).not.toHaveBeenCalled();
});

it('cleans up partially created markers and invalidates the camera after a marker failure', async () => {
  const mock = sdk();
  mock.Marker.mockImplementationOnce(function () { return { setMap: mock.setMap }; });
  mock.Marker.mockImplementationOnce(function () { throw new Error('marker failed'); });
  const onFailure = vi.fn();
  const onBounds = vi.fn();
  render(<KakaoMap center={center} items={[item, { ...item, id: 2, latitude: 37.57 }]} onBounds={onBounds} onReady={vi.fn()} onFailure={onFailure} onSelect={vi.fn()}/>);
  await waitFor(() => expect(onFailure).toHaveBeenCalledTimes(1));
  const idle = mock.listeners.find(listener => listener.name === 'idle')!;
  const click = mock.listeners.find(listener => listener.name === 'click')!;
  expect(mock.removeListener).toHaveBeenCalledWith(idle.target, 'idle', idle.callback);
  expect(mock.removeListener).toHaveBeenCalledWith(click.target, 'click', click.callback);
  expect(mock.setMap).toHaveBeenCalledWith(null);
  expect(mock.disconnect).toHaveBeenCalledTimes(1);
  act(() => { idle.callback(); mock.resize(); });
  expect(onBounds).not.toHaveBeenCalled();
});
