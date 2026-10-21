import React from 'react';
import { cleanup, render, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import KakaoMap from './KakaoMap';
afterEach(() => { cleanup(); vi.unstubAllEnvs(); delete window.kakao; });
it('passes latitude then longitude to Kakao markers', async () => {
  vi.stubEnv('VITE_KAKAO_JAVASCRIPT_KEY', 'test-browser-key');
  const LatLng = vi.fn(function() {});
  const Marker = vi.fn(function() { this.setMap = vi.fn(); });
  const Map = vi.fn(function() { this.setBounds = vi.fn(); this.panTo = vi.fn(); });
  window.kakao = { maps: { Map, LatLng, Marker, LatLngBounds: function() { this.extend = vi.fn(); }, event: { addListener: vi.fn() } } };
  render(<KakaoMap restaurants={[{ id: 1, name: '식당', latitude: 37.554615, longitude: 126.90687 }]} onSelect={vi.fn()}/>);
  await waitFor(() => expect(Marker).toHaveBeenCalledTimes(1));
  expect(LatLng).toHaveBeenCalledWith(37.554615, 126.90687);
});
