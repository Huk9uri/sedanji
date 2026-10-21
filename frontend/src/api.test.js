import { afterEach, expect, it, vi } from 'vitest';
import { api } from './api';
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
