import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import AdminWorkspace from '../AdminWorkspace';
import { ApiError } from '../../../api';
import type { ApiOptions } from '../../../api';
import type { Draft, RestaurantDetail, RestaurantItem } from './types';
const { api }=vi.hoisted(()=>({api:vi.fn<(path:string,options?:ApiOptions)=>Promise<unknown>>()}));
vi.mock('../../../api',async original=>({...await original<typeof import('../../../api')>(),api}));
const place={id:10,kakaoPlaceId:'123',name:'한빛국수',roadAddress:'서울 마포구 10',address:null,longitude:127,latitude:37,placeUrl:'https://place.map.kakao.com/123',version:0,createdAt:'2026-01-01T00:00:00Z'};
const item:RestaurantItem={id:10,name:place.name,roadAddress:place.roadAddress,address:null,createdAt:place.createdAt,relationCount:2,publicRelationCount:1,hasPublicRelation:true,chefCount:2,chefs:[{id:1,name:'김셰프',relationCount:1,publicRelationCount:1},{id:2,name:'이셰프',relationCount:1,publicRelationCount:0}],hasMoreChefs:false,needsSourceReview:null,sourceCount:2,uncheckedSourceCount:1,reviewRequiredSourceCount:0};
const secondItem:RestaurantItem={...item,id:20,name:'골목밥',roadAddress:'부산 해운대 20',relationCount:0,publicRelationCount:0,hasPublicRelation:false,chefCount:0,chefs:[],sourceCount:0,uncheckedSourceCount:0};
const detail:RestaurantDetail={restaurant:place,summary:item,mentions:{items:[
  {id:102,version:4,chef:{id:2,name:'이셰프'},video:{id:'22222222222',title:'영상 B',channelTitle:'채널 B',publishedAt:'2026-01-02T00:00:00Z',sourceCheckStatus:'UNCHECKED',sourceCheckedAt:null,sourceCheckReason:null},mentionType:'MENTIONED',timestampSeconds:null,verificationStatus:'REJECTED',publicationStatus:'SUSPENDED',verificationNote:'메모 B',reviewedAt:null},
  {id:101,version:3,chef:{id:1,name:'김셰프'},video:{id:'11111111111',title:'영상 A',channelTitle:'채널 A',publishedAt:'2026-01-01T00:00:00Z',sourceCheckStatus:'AVAILABLE',sourceCheckedAt:'2026-01-03T00:00:00Z',sourceCheckReason:null},mentionType:'REGULAR',timestampSeconds:0,verificationStatus:'APPROVED',publicationStatus:'PUBLISHED',verificationNote:'메모 A',reviewedAt:'2026-01-04T00:00:00Z'},
],totalElements:2,page:0,size:20,hasNext:false}};
const draft:Draft={id:201,kind:'CREATE',status:'DRAFT',version:7,restaurant:place,chefId:1,videoId:'11111111111',mentionType:'VISITED',timestampSeconds:0,verificationNote:'초안 메모',candidateId:null,appliedMentionId:null,createdBy:1,updatedBy:1,createdAt:'2026-01-01T00:00:00Z',updatedAt:'2026-01-01T00:00:00Z'};
function page<T>(items:T[],totalElements=items.length,page=0,hasNext=false){return {items,totalElements,page,size:20,hasNext};}
async function defaults(path:string){
  if(path.startsWith('/admin/restaurants?'))return page([item,secondItem]);
  if(path.startsWith('/admin/restaurants/20/mention-drafts?'))return page([]);
  if(path.includes('/mention-drafts?'))return page([draft]);
  if(path.startsWith('/admin/restaurants/10?'))return detail;
  if(path.startsWith('/admin/restaurants/20?'))return {...detail,restaurant:{...place,id:20,kakaoPlaceId:'456',name:'골목밥',roadAddress:secondItem.roadAddress,placeUrl:'https://place.map.kakao.com/456'},summary:secondItem,mentions:page([])};
  if(path==='/admin/mention-drafts/201')return draft;
  if(path==='/admin/videos')return [{id:'11111111111',title:'영상 A',channelTitle:'채널 A',publishedAt:'2026-01-01T00:00:00Z',description:'',extractionWarning:''}];
  if(path==='/admin/chefs')return [{id:1,name:'김셰프'}];
  return [];
}
beforeEach(()=>{vi.resetAllMocks();window.history.replaceState(null,'','/');vi.spyOn(window,'scrollTo').mockImplementation(()=>{});api.mockImplementation(defaults);});
afterEach(()=>{cleanup();vi.restoreAllMocks();});
function mount(expired=vi.fn()){return render(<AdminWorkspace username="admin" authGeneration={1} onExpired={expired} onPublished={vi.fn(async()=>{})} onGuard={vi.fn()}/>);}
function listCalls(){return api.mock.calls.filter(([path])=>path.startsWith('/admin/restaurants?'));}
async function openDetail(){fireEvent.click(await screen.findByRole('button',{name:'상세 보기 · 한빛국수'}));await screen.findByRole('heading',{name:'한빛국수'});}
async function fixedReady(){await screen.findByText(/선택 식당을 고정했습니다/);await waitFor(()=>expect(screen.getByRole('button',{name:'임시 저장'})).toBeEnabled());}
it('shows the real list and two work menus without invented submissions',async()=>{
  mount();expect(await screen.findByRole('button',{name:'한빛국수'})).toBeInTheDocument();
  expect(screen.getByRole('button',{name:'제보 관리 · 준비 중'})).toBeDisabled();expect(screen.getByText('연결 출처 없음')).toBeInTheDocument();
  expect(api).toHaveBeenCalledWith('/admin/restaurants?query=&publication=ALL&needsSourceReview=false&page=0&size=20',expect.objectContaining({signal:expect.any(AbortSignal)}));
  expect(api.mock.calls.some(([path])=>path.includes('submission'))).toBe(false);
});
it('does not search on typing and resets pages on executed filters, preserving conditions on return',async()=>{
  api.mockImplementation(async(path)=>path.startsWith('/admin/restaurants?')?page([item],40,new URLSearchParams(path.split('?')[1]).get('page')==='1'?1:0,true):defaults(path));mount();await screen.findByRole('button',{name:'한빛국수'});
  const before=listCalls().length;fireEvent.change(screen.getByLabelText('관리자 식당 검색'),{target:{value:'한빛 & 김'}});expect(listCalls()).toHaveLength(before);
  fireEvent.click(screen.getByRole('button',{name:'검색'}));await waitFor(()=>expect(listCalls().at(-1)?.[0]).toContain('query=%ED%95%9C%EB%B9%9B+%26+%EA%B9%80'));
  await screen.findByRole('button',{name:'다음 식당 페이지'});fireEvent.click(screen.getByRole('button',{name:'다음 식당 페이지'}));await waitFor(()=>expect(listCalls().at(-1)?.[0]).toContain('page=1'));
  await openDetail();fireEvent.click(screen.getByRole('button',{name:'← 목록으로'}));expect(screen.getByLabelText('관리자 식당 검색')).toHaveValue('한빛 & 김');expect(screen.getByText(/전체 40곳 · 2페이지/)).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('공개 관계 필터'),{target:{value:'PUBLISHED'}});await waitFor(()=>expect(listCalls().at(-1)?.[0]).toContain('publication=PUBLISHED&needsSourceReview=false&page=0'));
  fireEvent.click(screen.getByLabelText('출처 재확인 필요만'));await waitFor(()=>expect(listCalls().at(-1)?.[0]).toContain('needsSourceReview=true&page=0'));
});
it('keeps each relationship fields and source links attached to its own ID',async()=>{
  mount();await openDetail();const first=within(screen.getByRole('article',{name:'관계 101'})),second=within(screen.getByRole('article',{name:'관계 102'}));
  expect(first.getByText('메모 A',{exact:false})).toBeInTheDocument();expect(first.queryByText('메모 B',{exact:false})).not.toBeInTheDocument();
  expect(first.getByText('00:00')).toBeInTheDocument();expect(first.getByRole('link',{name:/영상 확인/})).toHaveAttribute('href','https://www.youtube.com/watch?v=11111111111&t=0s');
  expect(second.getByText('시점 미확인')).toBeInTheDocument();expect(second.getByRole('link',{name:/영상 확인/})).toHaveAttribute('href','https://www.youtube.com/watch?v=22222222222');
  expect(second.getByText(/채널 B/)).toBeInTheDocument();expect(second.getByText('검증: REJECTED')).toBeInTheDocument();
  for(const name of ['정정','공개 중단','복구','변경 이력'])expect(screen.queryByRole('button',{name})).not.toBeInTheDocument();
});
it('uses the fixed restaurant ID only after explicit draft save, then keeps success and offers result detail',async()=>{
  api.mockImplementation(async(path,options)=>path==='/admin/mention-drafts'&&options?.method==='POST'?{...draft,chefId:null,videoId:null,mentionType:null,timestampSeconds:null,verificationNote:null}:defaults(path));
  mount();await openDetail();fireEvent.click(screen.getByRole('button',{name:'관계 추가'}));await fixedReady();
  expect(api.mock.calls.filter(([,o])=>o?.method==='POST')).toHaveLength(0);expect(screen.queryByRole('button',{name:'장소 다시 선택'})).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'임시 저장'}));await screen.findByText('초안을 저장했습니다.');
  const writes=api.mock.calls.filter(([path,o])=>path==='/admin/mention-drafts'&&o?.method==='POST');expect(writes).toHaveLength(1);
  expect(writes[0][1]?.body).toEqual({restaurantId:10,chefId:null,videoId:null,mentionType:null,timestampSeconds:null,verificationNote:null});
  expect(screen.getByLabelText('식당 직접 등록')).toBeInTheDocument();await waitFor(()=>expect(screen.getByRole('button',{name:'저장한 식당 상세로 이동'})).toBeEnabled());fireEvent.click(screen.getByRole('button',{name:'저장한 식당 상세로 이동'}));await screen.findByRole('heading',{name:'한빛국수'});
});
it('resumes the server draft and preserves the version and zero timestamp',async()=>{
  mount();await openDetail();fireEvent.click(await screen.findByRole('button',{name:'초안 #201 이어쓰기'}));
  expect(await screen.findByLabelText('검증 메모 (선택)')).toHaveValue('초안 메모');expect(screen.getByLabelText('확인된 시점 (선택)')).toHaveValue('0:00');
  expect(api).toHaveBeenCalledWith('/admin/mention-drafts/201');expect(screen.getByText(/초안 #201 · 버전 7/)).toBeInTheDocument();
});
it('ignores a slow previous detail response after another selection',async()=>{
  let resolve!:(value:unknown)=>void;api.mockImplementation(async(path)=>path.startsWith('/admin/restaurants/10?')?new Promise(r=>{resolve=r;}):defaults(path));mount();
  fireEvent.click(await screen.findByRole('button',{name:'상세 보기 · 한빛국수'}));fireEvent.click(screen.getByRole('button',{name:'← 목록으로'}));
  fireEvent.click(await screen.findByRole('button',{name:'상세 보기 · 골목밥'}));await screen.findByRole('heading',{name:'골목밥'});
  await act(async()=>{resolve(detail);});expect(screen.getByRole('heading',{name:'골목밥'})).toBeInTheDocument();expect(screen.queryByRole('heading',{name:'한빛국수'})).not.toBeInTheDocument();
});
it('keeps draft errors separate and removes stale detail on 404',async()=>{
  let missing=false;api.mockImplementation(async path=>path.includes('/mention-drafts?')?Promise.reject(new ApiError(403,'초안 권한')):missing&&path.startsWith('/admin/restaurants/10?')?Promise.reject(new ApiError(404,'없음')):defaults(path));mount();await openDetail();
  expect(await screen.findByText(/초안 목록 조회 실패/)).toBeInTheDocument();expect(screen.getByRole('article',{name:'관계 101'})).toBeInTheDocument();
  missing=true;fireEvent.click(screen.getByRole('button',{name:'← 목록으로'}));fireEvent.click(await screen.findByRole('button',{name:'상세 보기 · 한빛국수'}));
  // Reopening a detail triggers a refresh even when the selected ID is unchanged.
  await screen.findByText(/식당을 찾을 수 없습니다/);expect(screen.queryByRole('article',{name:'관계 101'})).not.toBeInTheDocument();
});
it('changes relationship and draft pages independently',async()=>{
  api.mockImplementation(async path=>path.startsWith('/admin/restaurants/10?')?{...detail,mentions:{...detail.mentions,page:Number(new URLSearchParams(path.split('?')[1]).get('mentionPage')),hasNext:true}}:path.includes('/mention-drafts?')?page([draft],40,Number(new URLSearchParams(path.split('?')[1]).get('page')),true):defaults(path));
  mount();await openDetail();await screen.findByRole('button',{name:'다음 초안 페이지'});
  const draftCalls=()=>api.mock.calls.filter(([path])=>path.includes('/mention-drafts?'));
  const before=draftCalls().length;fireEvent.click(screen.getByRole('button',{name:'다음 관계 페이지'}));
  await waitFor(()=>expect(api.mock.calls.some(([path])=>path.includes('mentionPage=1'))).toBe(true));expect(draftCalls()).toHaveLength(before);
  fireEvent.click(screen.getByRole('button',{name:'다음 초안 페이지'}));await waitFor(()=>expect(draftCalls().at(-1)?.[0]).toContain('page=1'));
});
it('keeps successful draft creation and retries only GET after a refresh failure',async()=>{
  let saved=false;
  api.mockImplementation(async(path,options)=>{
    if(path==='/admin/mention-drafts'&&options?.method==='POST'){saved=true;return {...draft,chefId:null,videoId:null,mentionType:null,timestampSeconds:null,verificationNote:null};}
    if(saved&&path.startsWith('/admin/restaurants/10?'))throw new ApiError(500,'query failed');
    return defaults(path);
  });mount();await openDetail();fireEvent.click(screen.getByRole('button',{name:'관계 추가'}));await fixedReady();
  fireEvent.click(screen.getByRole('button',{name:'임시 저장'}));expect(await screen.findByText('초안을 저장했습니다.')).toBeInTheDocument();
  const retry=await screen.findByRole('button',{name:'관련 조회 다시 시도'});fireEvent.click(retry);
  await waitFor(()=>expect(api.mock.calls.filter(([path])=>path.startsWith('/admin/restaurants/10?')).length).toBeGreaterThanOrEqual(3));
  expect(api.mock.calls.filter(([,options])=>options?.method==='POST')).toHaveLength(1);
  expect(screen.getByText('초안을 저장했습니다.')).toBeInTheDocument();
});
it('does not insert a saved restaurant back into a filter that excludes it',async()=>{
  let saved=false;api.mockImplementation(async(path,options)=>{
    if(path==='/admin/mention-drafts'&&options?.method==='POST'){saved=true;return {...draft,chefId:null,videoId:null,mentionType:null,timestampSeconds:null,verificationNote:null};}
    if(path.startsWith('/admin/restaurants?'))return page(saved?[]:[item]);return defaults(path);
  });mount();await screen.findByRole('button',{name:'한빛국수'});fireEvent.change(screen.getByLabelText('공개 관계 필터'),{target:{value:'PUBLISHED'}});
  await openDetail();fireEvent.click(screen.getByRole('button',{name:'관계 추가'}));await fixedReady();fireEvent.click(screen.getByRole('button',{name:'임시 저장'}));
  await screen.findByText('초안을 저장했습니다.');await waitFor(()=>expect(screen.getByRole('button',{name:'수정 저장'})).toBeEnabled());
  fireEvent.click(screen.getByRole('button',{name:'식당 관리'}));await screen.findByText('조건에 맞는 식당이 없습니다.');expect(screen.queryByRole('button',{name:'한빛국수'})).not.toBeInTheDocument();
});
it('uses a single guard dialog for internal navigation and preserves input on cancel',async()=>{
  mount();await screen.findByRole('button',{name:'한빛국수'});fireEvent.click(screen.getByRole('button',{name:'식당 직접 등록 · 초안 이어쓰기'}));
  // A new entry has no place yet; resume an existing draft through the URL entry instead below.
  cleanup();window.history.replaceState(null,'','/?draftId=201');mount();await screen.findByDisplayValue('초안 메모');
  fireEvent.change(screen.getByLabelText('검증 메모 (선택)'),{target:{value:'내 미저장 메모'}});
  await waitFor(()=>expect(screen.getByRole('button',{name:'수정 저장'})).toBeEnabled());fireEvent.click(screen.getByRole('button',{name:'식당 관리'}));expect(screen.getAllByRole('dialog')).toHaveLength(1);
  fireEvent.click(screen.getByRole('button',{name:'계속 작성'}));expect(screen.getByLabelText('검증 메모 (선택)')).toHaveValue('내 미저장 메모');
  fireEvent.click(screen.getByRole('button',{name:'식당 관리'}));fireEvent.click(screen.getByRole('button',{name:'저장하지 않고 나가기'}));await screen.findByRole('button',{name:'한빛국수'});
});
it('clears query data on expiry and ignores previous-account responses',async()=>{
  const expired=vi.fn();let reject!:(e:unknown)=>void;
  api.mockImplementation(async path=>path.startsWith('/admin/restaurants?')?new Promise((_,r)=>{reject=r;}):defaults(path));
  const view=mount(expired);await act(async()=>{reject(new ApiError(401,'login'));});await waitFor(()=>expect(expired).toHaveBeenCalled());
  view.rerender(<AdminWorkspace username={undefined} authGeneration={2} onExpired={expired} onPublished={vi.fn(async()=>{})} onGuard={vi.fn()}/>);
  expect(screen.queryByRole('button',{name:'한빛국수'})).not.toBeInTheDocument();expect(screen.queryByText('login')).not.toBeInTheDocument();
});
