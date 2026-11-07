import { afterEach, expect, it, vi } from 'vitest';
import { api } from '../../../api';
import { publicationApi } from './publicationApi';
vi.mock('../../../api',()=>({api:vi.fn(async()=>({}))}));
afterEach(()=>vi.clearAllMocks());
it('uses dedicated confirmation and write paths with the original key and body',async()=>{
  const signal=new AbortController().signal;
  await publicationApi.get(12,signal);expect(api).toHaveBeenLastCalledWith('/admin/mentions/12/publication',{signal});
  await publicationApi.history(12,2,signal);expect(api).toHaveBeenLastCalledWith('/admin/mentions/12/history?page=2&size=20',{signal});
  const body={expectedVersion:3,reason:'재확인',expectedContextHash:'a'.repeat(64),evidenceRechecked:true as const,recheckNote:'근거 확인'};
  await publicationApi.write(12,'restore',body,'same-key');
  expect(api).toHaveBeenLastCalledWith('/admin/mentions/12/restore',{method:'POST',body,headers:{'Idempotency-Key':'same-key'}});
  await publicationApi.write(12,'suspend',{expectedVersion:4,reason:'중단'},'suspend-key');
  expect(api).toHaveBeenLastCalledWith('/admin/mentions/12/suspend',{method:'POST',body:{expectedVersion:4,reason:'중단'},headers:{'Idempotency-Key':'suspend-key'}});
});
