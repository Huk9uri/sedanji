import { afterEach, expect, it, vi } from 'vitest';
import { api, ApiError } from './api';
afterEach(() => vi.unstubAllGlobals());

it('accepts an empty 201 response from candidate or relationship creation', async () => {
  const fetch = vi.fn()
    .mockResolvedValueOnce({ ok: true, json: async () => ({ headerName: 'X-CSRF-TOKEN', token: 'test-token' }) })
    .mockResolvedValueOnce({ ok: true, status: 201, text: async () => '' });
  vi.stubGlobal('fetch', fetch);
  expect(await api('/admin/candidates/1/mentions', { method: 'POST', body: {} })).toBeNull();
  expect(fetch.mock.calls[1][1].headers['X-CSRF-TOKEN']).toBe('test-token');
});

it('reads the candidate JSON on a successful read', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, status: 200, text: async () => '[{"id":1,"disposition":"OPEN"}]' }));
  expect(await api('/admin/candidates')).toEqual([{ id: 1, disposition: 'OPEN' }]);
});

it('preserves a server validation message for a failed approval', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 400, json: async () => ({ message: '언급 유형을 확인하세요.' }) }));
  await expect(api('/admin/candidates')).rejects.toThrow('언급 유형을 확인하세요.');
});

it('keeps form login encoding, CSRF headers, and empty 204 handling', async () => {
  const fetch = vi.fn()
    .mockResolvedValueOnce({ ok: true, json: async () => ({ headerName: 'X-CSRF-TOKEN', token: 'test-token' }) })
    .mockResolvedValueOnce({ ok: true, status: 204 });
  vi.stubGlobal('fetch', fetch);
  expect(await api('/login', { method: 'POST', form: true, body: { username: '관리자', password: 'test&value' } })).toBeNull();
  const [url, options] = fetch.mock.calls[1];
  expect(url).toBe('/api/login');
  expect(options.headers).toEqual({ 'X-CSRF-TOKEN': 'test-token', 'Content-Type': 'application/x-www-form-urlencoded' });
  expect(options.body).toBeInstanceOf(URLSearchParams);
  expect(options.body.get('username')).toBe('관리자');
  expect(options.body.get('password')).toBe('test&value');
});

it('does not submit a write when CSRF retrieval fails', async () => {
  const fetch = vi.fn().mockResolvedValueOnce({ ok: false });
  vi.stubGlobal('fetch', fetch);
  await expect(api('/logout', { method: 'POST' })).rejects.toThrow('보안 토큰을 가져오지 못했습니다.');
  expect(fetch).toHaveBeenCalledTimes(1);
});

it('preserves structured fields while validating unknown error JSON', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ok:false,status:409,json:async()=>({message:'충돌',code:'STALE_VERSION',currentVersion:7,existingMentionId:9,appliedMentionId:10,missingFields:['chefId']})}));
  await expect(api('/admin/mention-drafts/1')).rejects.toMatchObject({name:'ApiError',status:409,code:'STALE_VERSION',currentVersion:7,existingMentionId:9,appliedMentionId:10,missingFields:['chefId']});
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue({ok:false,status:403,json:async()=>({message:7,currentVersion:'bad',missingFields:[1]})}));
  await expect(api('/admin/mentions')).rejects.toMatchObject({status:403,message:expect.stringContaining('접근 권한')});
});
it('keeps idempotency headers but prevents case-insensitive CSRF override', async () => {
  const fetch=vi.fn().mockResolvedValueOnce({ok:true,json:async()=>({headerName:'X-CSRF-TOKEN',token:'fresh'})})
    .mockResolvedValueOnce({ok:true,status:201,text:async()=>'{}'});
  vi.stubGlobal('fetch',fetch);
  await api('/admin/mentions',{method:'POST',body:{},headers:{'Idempotency-Key':'stable','x-csrf-token':'override'}});
  expect(fetch.mock.calls[1][1].headers).toEqual({'Idempotency-Key':'stable','X-CSRF-TOKEN':'fresh','Content-Type':'application/json'});
});
it('keeps empty security errors and optional read cancellation', async () => {
  const fetch=vi.fn().mockResolvedValue({ok:false,status:401,json:async()=>{throw new Error();}});
  vi.stubGlobal('fetch',fetch); const controller=new AbortController();
  await expect(api('/admin/chefs',{signal:controller.signal})).rejects.toBeInstanceOf(ApiError);
  expect(fetch.mock.calls[0][1].signal).toBe(controller.signal);
});
