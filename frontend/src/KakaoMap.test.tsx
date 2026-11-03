import React from 'react';
import { cleanup, render, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import KakaoMap from './KakaoMap';
afterEach(() => { cleanup(); vi.unstubAllEnvs(); delete window.kakao; });
it('passes latitude then longitude to Kakao markers', async () => {
  vi.stubEnv('VITE_KAKAO_JAVASCRIPT_KEY', 'test-browser-key');
  const LatLng = vi.fn(function() { return {}; });
  const Marker = vi.fn(function() { return { setMap: vi.fn() }; });
  const Map = vi.fn(function() { return { setBounds: vi.fn(), panTo: vi.fn() }; });
  window.kakao = { maps: { load: callback => callback(), Map, LatLng, Marker, LatLngBounds: class { extend = vi.fn(); }, event: { addListener: vi.fn() } } };
  render(<KakaoMap restaurants={[{ id: 1, kakaoPlaceId: '1', name: '식당', roadAddress: null, address: null, placeUrl: 'https://place.map.kakao.com/1', mentions: [], latitude: 37.554615, longitude: 126.90687 }]} selected={null} onSelect={vi.fn()}/>);
  await waitFor(() => expect(Marker).toHaveBeenCalledTimes(1));
  expect(LatLng).toHaveBeenCalledWith(37.554615, 126.90687);
});
