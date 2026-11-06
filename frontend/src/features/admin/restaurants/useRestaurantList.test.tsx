import { useState } from 'react';
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { useRestaurantList } from './useRestaurantList';
import { restaurantApi } from './restaurantApi';
import { initialConditions } from './types';
import type { ListConditions, Page, RestaurantItem } from './types';
import { ApiError } from '../../../api';
vi.mock('./restaurantApi',()=>({restaurantApi:{list:vi.fn()}}));
beforeEach(()=>vi.resetAllMocks());afterEach(cleanup);
const empty=(page=0,totalElements=0):Page<RestaurantItem>=>({items:[],totalElements,page,size:20,hasNext:false});
it('corrects an out-of-range page once and never loops over inconsistent totals',async()=>{
  vi.mocked(restaurantApi.list).mockResolvedValueOnce(empty(4,21)).mockResolvedValueOnce(empty(1,0));
  const {result}=renderHook(()=>{const[c,set]=useState({...initialConditions(),page:4});return useRestaurantList(c,set,true,1,0,vi.fn());});
  await waitFor(()=>expect(result.current.data?.page).toBe(1));
  expect(restaurantApi.list).toHaveBeenCalledTimes(2);expect(vi.mocked(restaurantApi.list).mock.calls[1][0].page).toBe(1);
});
it('discards late search responses and late errors instead of replacing the executed query',async()=>{
  let resolve!:(value:Page<RestaurantItem>)=>void;
  vi.mocked(restaurantApi.list).mockImplementationOnce(()=>new Promise(r=>{resolve=r;})).mockResolvedValueOnce(empty(0,7));
  const {result,rerender}=renderHook(({c}:{c:ListConditions})=>useRestaurantList(c,vi.fn(),true,1,0,vi.fn()),{initialProps:{c:initialConditions()}});
  rerender({c:{...initialConditions(),query:'new'}});await waitFor(()=>expect(result.current.data?.totalElements).toBe(7));
  await act(async()=>{resolve(empty(0,99));});expect(result.current.data?.totalElements).toBe(7);
  expect(vi.mocked(restaurantApi.list).mock.calls[0][1]?.aborted).toBe(true);
});
it('clears data on auth generation changes and reports 401 separately from 403',async()=>{
  const fail=vi.fn();vi.mocked(restaurantApi.list).mockResolvedValueOnce(empty(0,5)).mockRejectedValueOnce(new ApiError(401,'login'))
    .mockRejectedValueOnce(new ApiError(403,'forbidden'));
  const {result,rerender}=renderHook(({generation,enabled})=>useRestaurantList(initialConditions(),vi.fn(),enabled,generation,0,fail),{initialProps:{generation:1,enabled:true}});
  await waitFor(()=>expect(result.current.data?.totalElements).toBe(5));rerender({generation:2,enabled:true});
  expect(result.current.data).toBeUndefined();await waitFor(()=>expect(fail).toHaveBeenCalledWith(expect.objectContaining({status:401})));
  rerender({generation:3,enabled:true});await waitFor(()=>expect(fail).toHaveBeenCalledWith(expect.objectContaining({status:403})));
  rerender({generation:4,enabled:false});expect(result.current.data).toBeUndefined();expect(result.current.error).toBeUndefined();
});
