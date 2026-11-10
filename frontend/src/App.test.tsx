import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import App from './App';
import type { ApiOptions } from './api';
import type { Restaurant } from './types';
const { api } = vi.hoisted(() => ({ api: vi.fn<(path: string, options?: ApiOptions) => Promise<unknown>>() }));
vi.mock('./api', async importOriginal => ({ ...await importOriginal<typeof import('./api')>(), api }));
vi.mock('./features/explore/ExploreMap', () => ({ default: ({ onReady }: { onReady: (b: { west: number; east: number; south: number; north: number }, failed: boolean) => void }) => { React.useEffect(() => onReady({ west: 126.96, east: 126.99, south: 37.55, north: 37.58 }, true), [onReady]); return <div data-testid="map"/>; } }));
afterEach(cleanup);
beforeEach(() => { vi.resetAllMocks(); window.history.replaceState(null, '', '/'); });
const restaurant: Restaurant = { id: 1, kakaoPlaceId: '1158460117', address: null, longitude: 126.90687, latitude: 37.554615, name: '뽀요베리', roadAddress: '서울 마포구 월드컵로13길 55-15', placeUrl: 'http://place.map.kakao.com/1158460117', mentions: [{ mentionId: 1, chefName: '윤남노', videoId: 'RMTJPFKRbEk', videoTitle: '원본 영상', mentionType: 'VISITED', timestampSeconds: null }] };

describe('public and approval workflow', () => {
  it('guards map navigation and logout from the unsaved direct registration form', async () => {
    api.mockImplementation(async(path)=>{
      if(path.startsWith('/public/chefs')) return {items:[],page:0,size:50,totalElements:0,hasNext:false};
      if(path==='/session') return {authenticated:true,username:'admin'};
      if(path.startsWith('/admin/places?')) return {candidates:[{placeSelectionToken:'token',place:{id:'1',name:'등록 장소',roadAddress:'도로',address:null,longitude:127,latitude:37,placeUrl:'https://place.map.kakao.com/1'}}],totalCount:1,complete:true};
      return [];
    });
    render(<App/>);fireEvent.click(screen.getByRole('button',{name:'관리자'}));
    fireEvent.click(await screen.findByRole('button',{name:'식당 직접 등록 · 초안 이어쓰기'}));
    fireEvent.change(screen.getByLabelText('식당 이름 검색'),{target:{value:'장소'}});fireEvent.click(screen.getByRole('button',{name:'장소 검색'}));
    fireEvent.click(await screen.findByRole('button',{name:'이 지점 선택'}));
    fireEvent.click(screen.getByRole('button',{name:'맛집 지도'}));expect(screen.getByRole('dialog')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button',{name:'계속 작성'}));expect(screen.getByLabelText('검증 메모 (선택)')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button',{name:'로그아웃'}));expect(api.mock.calls.filter(([path])=>path==='/logout')).toHaveLength(0);
    fireEvent.click(screen.getByRole('button',{name:'계속 작성'}));fireEvent.click(screen.getByRole('button',{name:'맛집 지도'}));
    fireEvent.click(screen.getByRole('button',{name:'저장하지 않고 나가기'}));expect(await screen.findByText('위치 또는 기본 지역을 선택하세요.')).toBeInTheDocument();
  });
  it('waits for a location choice and never invents places', async () => {
    api.mockImplementation(async path => path.startsWith('/public/chefs') ? { items: [], page: 0, size: 50, totalElements: 0, hasNext: false } : []);
    render(<App/>);
    expect(screen.getByText('위치 또는 기본 지역을 선택하세요.')).toBeInTheDocument();
    expect(api.mock.calls.some(([path]) => path.startsWith('/public/restaurants'))).toBe(false);
  });
  it('matched place still requires explicit chef type evidence and note before approval', async () => {
    const mention = { id: 9, videoId: 'RMTJPFKRbEk', videoTitle: '영상', extractedName: '뽀요베리', extractedAddress: '', sourceLine: 1, evidence: '원문', disposition: 'OPEN', matchStatus: 'MATCHED' };
    api.mockImplementation(async (path, options) => {
      if (path.startsWith('/public/chefs')) return {items:[],page:0,size:50,totalElements:0,hasNext:false};
      if (path === '/session') return { authenticated: true, username: 'admin' };
      if (path === '/admin/videos') return [];
      if (path === '/admin/mentions') return [];
      if (path === '/admin/candidates') return [mention];
      if (path === '/admin/chefs') return [{ id: 1, name: '윤남노' }];
      if (path.endsWith('/places') && !options) return { candidates: [], complete: true };
      if (path.endsWith('/places')) return { complete: true, candidates: [{ id: '1158460117', name: '뽀요베리', roadAddress: '서울 마포구 월드컵로13길 55-15', placeUrl: restaurant.placeUrl }] };
    });
    render(<App/>); fireEvent.click(screen.getByRole('button', { name: '관리자' }));
    fireEvent.click(await screen.findByRole('button', { name: '기존 후보 검토' }));
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
