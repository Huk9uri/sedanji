import { afterEach, expect, it, vi } from 'vitest';
import { api } from '../../../api';
import { correctionApi } from './correctionApi';
import type { CorrectionKind } from './types';
vi.mock('../../../api',()=>({api:vi.fn(async()=>({}))}));
afterEach(()=>vi.clearAllMocks());
it.each<[CorrectionKind,string,string,string]>([
  ['MENTION','mentions','corrections','mention-corrections'],
  ['RESTAURANT','restaurants','revisions','restaurant-revisions'],
  ['CHEF','chefs','revisions','chef-revisions'],
])('uses the dedicated %s routes and forwards version, impact hash and idempotency key',async(kind,resource,collection,revision)=>{
  const target={kind,id:1};const signal=new AbortController().signal;
  await correctionApi.list(target,2,signal);expect(api).toHaveBeenLastCalledWith(`/admin/${resource}/1/${collection}?page=2&size=20`,{signal});
  await correctionApi.get(target,10,3,signal);expect(api).toHaveBeenLastCalledWith(`/admin/${revision}/10?impactPage=3&impactSize=20`,{signal});
  await correctionApi.history(target,2,signal);expect(api).toHaveBeenLastCalledWith(`/admin/${resource}/1/history?page=2&size=20`,{signal});
  const body={expectedVersion:4,expectedImpactHash:'a'.repeat(64)};
  await correctionApi.write(target,10,'approve',body,'fixed-key');
  expect(api).toHaveBeenLastCalledWith(`/admin/${revision}/10/approve`,{method:'POST',body,headers:{'Idempotency-Key':'fixed-key'}});
  await correctionApi.write(target,10,'patch',{expectedVersion:4,changeReason:'사유'});
  expect(api).toHaveBeenLastCalledWith(`/admin/${revision}/10`,{method:'PATCH',body:{expectedVersion:4,changeReason:'사유'},headers:undefined});
});
