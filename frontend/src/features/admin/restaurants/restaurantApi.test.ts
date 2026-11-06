import { afterEach, expect, it, vi } from 'vitest';
import { restaurantApi } from './restaurantApi';
import { initialConditions } from './types';
afterEach(()=>vi.unstubAllGlobals());
it('encodes executed filters and independent detail/draft pages with AbortSignal',async()=>{
  const fetch=vi.fn().mockResolvedValue({ok:true,status:200,text:async()=>'{}'});vi.stubGlobal('fetch',fetch);
  const controller=new AbortController();
  await restaurantApi.list({...initialConditions(),query:'국수 & 셰프',publication:'NO_PUBLIC_RELATION',needsSourceReview:false,page:2},controller.signal);
  await restaurantApi.detail(10,3,20,controller.signal);await restaurantApi.drafts(10,1,20,controller.signal);
  const first=new URL(fetch.mock.calls[0][0],'https://local.test');
  expect(first.pathname).toBe('/api/admin/restaurants');
  expect(Object.fromEntries(first.searchParams)).toEqual({query:'국수 & 셰프',publication:'NO_PUBLIC_RELATION',needsSourceReview:'false',page:'2',size:'20'});
  expect(fetch.mock.calls[0][1].signal).toBe(controller.signal);
  expect(fetch.mock.calls[1][0]).toBe('/api/admin/restaurants/10?mentionPage=3&mentionSize=20');
  expect(fetch.mock.calls[2][0]).toBe('/api/admin/restaurants/10/mention-drafts?status=DRAFT&page=1&size=20');
  expect(fetch.mock.calls.every(([,options])=>options.method==='GET')).toBe(true);
});
