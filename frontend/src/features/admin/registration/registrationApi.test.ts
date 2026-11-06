import { afterEach, expect, it, vi } from 'vitest';
import { registrationApi as api } from './registrationApi';
afterEach(()=>vi.unstubAllGlobals());
it('uses metadata endpoint and exact draft create/patch/approve contracts', async () => {
  const fetch=vi.fn(async (url:string) => url==='/api/csrf' ? {ok:true,json:async()=>({headerName:'X-CSRF-TOKEN',token:'csrf'})}
    : {ok:true,status:200,text:async()=>'{}'});
  vi.stubGlobal('fetch',fetch);
  await api.metadata('https://youtu.be/12345678901');
  const body={placeSelectionToken:'token',chefId:null,videoId:null,mentionType:null,timestampSeconds:null,verificationNote:null};
  await api.createDraft(body,'key'); await api.patch(3,{expectedVersion:4,chefId:null}); await api.approve(3,5,'approve-key');
  const writes=fetch.mock.calls.filter(([url])=>url!=='/api/csrf') as unknown as [string,RequestInit][];
  expect(writes.map(([url])=>url)).toEqual(['/api/admin/videos/metadata','/api/admin/mention-drafts','/api/admin/mention-drafts/3','/api/admin/mention-drafts/3/approve']);
  expect(JSON.parse(writes[1][1].body as string)).toEqual(body);
  expect(writes[1][1].headers).toMatchObject({'Idempotency-Key':'key'});
  expect(JSON.parse(writes[2][1].body as string)).toEqual({expectedVersion:4,chefId:null});
  expect(JSON.parse(writes[3][1].body as string)).toEqual({expectedVersion:5});
});
