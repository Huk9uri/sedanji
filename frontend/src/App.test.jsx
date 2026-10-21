import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import App from './App';
import { api } from './api';
vi.mock('./api', () => ({ api: vi.fn() }));
vi.mock('./KakaoMap', () => ({ default: ({ restaurants }) => <div data-testid="map">{restaurants.length} markers</div> }));
afterEach(cleanup);
beforeEach(() => vi.resetAllMocks());
const restaurant = { id: 1, name: '뽀요베리', roadAddress: '서울 마포구 월드컵로13길 55-15', placeUrl: 'http://place.map.kakao.com/1158460117', mentions: [{ mentionId: 1, chefName: '윤남노', videoId: 'RMTJPFKRbEk', videoTitle: '원본 영상', mentionType: 'VISITED', timestampSeconds: null }] };

describe('public and approval workflow', () => {
  it('shows empty approved list without invented restaurants', async () => {
    api.mockResolvedValue([]);
    render(<App/>);
    expect(await screen.findByText('첫 단골집을 기다리고 있어요')).toBeInTheDocument();
    expect(screen.getByTestId('map')).toHaveTextContent('0 markers');
  });
  it('filters published restaurants by chef and keeps source links', async () => {
    api.mockResolvedValue([restaurant]); render(<App/>);
    expect(await screen.findByRole('heading', { name: '뽀요베리' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /원본 영상/ })).toHaveAttribute('href', 'https://www.youtube.com/watch?v=RMTJPFKRbEk');
    fireEvent.change(screen.getByLabelText('식당, 지역, 셰프 검색'), { target: { value: '윤남노' } });
    expect(screen.getByTestId('map')).toHaveTextContent('1 markers');
    fireEvent.change(screen.getByLabelText('식당, 지역, 셰프 검색'), { target: { value: '없는 셰프' } });
    expect(screen.getByText('검색 결과가 없습니다')).toBeInTheDocument();
  });
  it('handles public API failure visibly', async () => {
    api.mockRejectedValue(new Error('DB 연결 실패')); render(<App/>);
    expect(await screen.findByRole('alert')).toHaveTextContent('DB 연결 실패');
  });
  it('shows verified relationship type and preserves a confirmed zero-second timestamp', async () => {
    api.mockResolvedValue([{ ...restaurant, mentions: [{ ...restaurant.mentions[0], mentionType: 'MENTIONED', timestampSeconds: 0 }] }]);
    render(<App/>);
    expect(await screen.findByText('윤남노 셰프 · 단순 언급')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /원본 영상/ })).toHaveAttribute('href', 'https://www.youtube.com/watch?v=RMTJPFKRbEk&t=0s');
  });
  it('matched place still requires explicit chef type evidence and note before approval', async () => {
    const mention = { id: 9, videoId: 'RMTJPFKRbEk', videoTitle: '영상', extractedName: '뽀요베리', extractedAddress: '', sourceLine: 1, evidence: '원문', disposition: 'OPEN', matchStatus: 'MATCHED' };
    api.mockImplementation(async (path, options) => {
      if (path === '/restaurants') return [];
      if (path === '/session') return { authenticated: true };
      if (path === '/admin/videos') return [];
      if (path === '/admin/mentions') return [];
      if (path === '/admin/candidates') return [mention];
      if (path === '/admin/chefs') return [{ id: 1, name: '윤남노' }];
      if (path.endsWith('/places') && !options) return { candidates: [], complete: true };
      if (path.endsWith('/places')) return { complete: true, candidates: [{ id: '1158460117', name: '뽀요베리', roadAddress: '서울 마포구 월드컵로13길 55-15', placeUrl: restaurant.placeUrl }] };
    });
    render(<App/>); fireEvent.click(screen.getByRole('button', { name: '관리자', exact: true }));
    const approve = await screen.findByRole('button', { name: '관계 승인하고 공개' });
    expect(approve).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: 'Kakao 검색' }));
    fireEvent.click(await screen.findByRole('radio'));
    expect(approve).toBeDisabled();
    fireEvent.change(screen.getByLabelText('관계 셰프'), { target: { value: '1' } });
    fireEvent.change(screen.getByLabelText('검토 메모'), { target: { value: '셰프와 지점 근거 확인' } });
    expect(approve).toBeDisabled();
    fireEvent.change(screen.getByLabelText('언급 유형'), { target: { value: 'VISITED' } });
    expect(approve).toBeDisabled();
    fireEvent.change(screen.getByLabelText('관계 확인 근거'), { target: { value: '해당 셰프의 방문 확인' } });
    await waitFor(() => expect(approve).toBeEnabled());
    fireEvent.click(approve);
    await waitFor(() => expect(api).toHaveBeenCalledWith('/admin/candidates/9/mentions', { method: 'POST', body: { chefId: 1, placeId: '1158460117', mentionType: 'VISITED', timestampSeconds: null, evidence: '해당 셰프의 방문 확인', note: '셰프와 지점 근거 확인' } }));
  });
});
