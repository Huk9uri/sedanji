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
it('controls only an approved relation and refreshes detail, history and public reads after suspension',async()=>{
  let suspended=false;
  const published=vi.fn(async()=>{});
  api.mockImplementation(async(path,options)=>{
    if(path==='/admin/mentions/101/publication')return {mention:{id:101,version:suspended?4:3,chefId:1,chefName:'김셰프',restaurantId:10,restaurantName:place.name,
      videoId:'11111111111',videoTitle:'영상 A',channelTitle:'채널 A',mentionType:'REGULAR',timestampSeconds:0,verificationStatus:'APPROVED',publicationStatus:suspended?'SUSPENDED':'PUBLISHED',evidence:null,verificationNote:'메모 A',candidateId:null},
      references:{restaurant:place},contextHash:'a'.repeat(64),latestControl:null,lastSuspendReason:suspended?'근거 재검토':null};
    if(path.startsWith('/admin/mentions/101/history'))return page(suspended?[{id:1,actorId:1,action:'SUSPEND_MENTION',reason:'근거 재검토',occurredAt:'2026-11-07T00:00:00Z',beforeValue:null,afterValue:null}]:[]);
    if(path==='/admin/mentions/101/suspend'&&options?.method==='POST'){
      suspended=true;return {action:'SUSPEND_MENTION',mentionId:101,restaurantId:10,previousVersion:3,version:4,verificationStatus:'APPROVED',publicationStatus:'SUSPENDED',auditEventId:1,occurredAt:'2026-11-07T00:00:00Z'};
    }
    if(suspended&&path.startsWith('/admin/restaurants/10?'))return {...detail,summary:{...item,publicRelationCount:0,hasPublicRelation:false},mentions:page(detail.mentions.items.map(m=>m.id===101?{...m,version:4,publicationStatus:'SUSPENDED'}:m))};
    return defaults(path);
  });
  render(<AdminWorkspace username="admin" authGeneration={1} onExpired={vi.fn()} onPublished={published} onGuard={vi.fn()}/>);
  await openDetail();expect(screen.queryByRole('button',{name:'관계 #102 공개 복구'})).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'관계 #101 공개 중단'}));
  await screen.findByText('김셰프 · 한빛국수');fireEvent.change(screen.getByLabelText('처리 사유'),{target:{value:'근거 재검토'}});
  fireEvent.click(screen.getByRole('button',{name:'이 관계만 공개 중단'}));
  await screen.findByText('SUSPEND_MENTION · 2026-11-07T00:00:00Z');
  await waitFor(()=>expect(published).toHaveBeenCalledTimes(1));fireEvent.click(screen.getByRole('button',{name:'닫기'}));
  expect(await screen.findByRole('button',{name:'관계 #101 공개 복구'})).toBeInTheDocument();
  expect(screen.getByText('공개 관계 0 / 전체 관계 2')).toBeInTheDocument();
  expect(api.mock.calls.filter(([path,o])=>path==='/admin/mentions/101/suspend'&&o?.method==='POST')).toHaveLength(1);
  const listsBefore=listCalls().length;fireEvent.click(screen.getByRole('button',{name:'← 목록으로'}));
  await waitFor(()=>expect(listCalls().length).toBeGreaterThan(listsBefore));
});
it('does not search on typing and resets pages on executed filters, preserving conditions on return',async()=>{
  api.mockImplementation(async(path)=>path.startsWith('/admin/restaurants?')?page([item],40,new URLSearchParams(path.split('?')[1]).get('page')==='1'?1:0,true):defaults(path));mount();await screen.findByRole('button',{name:'한빛국수'});
  const before=listCalls().length;fireEvent.change(screen.getByLabelText('관리자 식당 검색'),{target:{value:'한빛 & 김'}});expect(listCalls()).toHaveLength(before);
  fireEvent.click(screen.getByRole('button',{name:'검색'}));await waitFor(()=>expect(listCalls().at(-1)?.[0]).toContain('query=%ED%95%9C%EB%B9%9B+%26+%EA%B9%80'));
  await screen.findByRole('button',{name:'다음 식당 페이지'});fireEvent.click(screen.getByRole('button',{name:'다음 식당 페이지'}));await waitFor(()=>expect(listCalls().at(-1)?.[0]).toContain('page=1'));
  await openDetail();fireEvent.click(screen.getByRole('button',{name:'← 목록으로'}));expect(screen.getByLabelText('관리자 식당 검색')).toHaveValue('한빛 & 김');expect(await screen.findByText(/전체 40곳 · 2페이지/)).toBeInTheDocument();
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
  const result=screen.getByRole('button',{name:'저장한 식당 상세로 이동'});
  await waitFor(()=>expect(result).toBeEnabled());
  const details=()=>api.mock.calls.filter(([path])=>path.startsWith('/admin/restaurants/10?'));
  const drafts=()=>api.mock.calls.filter(([path])=>path.includes('/mention-drafts?'));
  const before=details().length, beforeList=listCalls().length;
  fireEvent.click(result);
  const retry=await screen.findByRole('button',{name:'관련 조회 다시 시도'});
  expect(details()).toHaveLength(before+1); expect(listCalls()).toHaveLength(beforeList);
  await waitFor(()=>expect(drafts()).toHaveLength(2));
  const beforeDrafts=drafts().length;
  saved=false; fireEvent.click(retry);
  await screen.findByRole('article',{name:'관계 101'});
  expect(details()).toHaveLength(before+2);expect(drafts()).toHaveLength(beforeDrafts);
  expect(api.mock.calls.filter(([,options])=>options?.method==='POST')).toHaveLength(1);
  expect(screen.getByText('작업 완료 · 식당 #10 · 초안 #201')).toBeInTheDocument();
  expect(screen.queryByRole('button',{name:'관련 조회 다시 시도'})).not.toBeInTheDocument();
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
it('rejects an overlong executed search without fetching or replacing the current page and rows',async()=>{
  api.mockImplementation(async path=>path.startsWith('/admin/restaurants?')?page([item],40,Number(new URLSearchParams(path.split('?')[1]).get('page')),true):defaults(path));
  mount();await screen.findByRole('button',{name:'한빛국수'});
  fireEvent.change(screen.getByLabelText('관리자 식당 검색'),{target:{value:'기존'}});
  fireEvent.click(screen.getByRole('button',{name:'검색'}));await screen.findByRole('button',{name:'한빛국수'});
  fireEvent.click(screen.getByRole('button',{name:'다음 식당 페이지'}));await screen.findByText('전체 40곳 · 2페이지');
  const before=listCalls().length, last=listCalls().at(-1)?.[0];
  fireEvent.change(screen.getByLabelText('관리자 식당 검색'),{target:{value:'😀'.repeat(101)}});
  fireEvent.click(screen.getByRole('button',{name:'검색'}));
  expect(screen.getByRole('alert')).toHaveTextContent('100자 이하');
  expect(listCalls()).toHaveLength(before);expect(listCalls().at(-1)?.[0]).toBe(last);
  expect(screen.getByText('전체 40곳 · 2페이지')).toBeInTheDocument();expect(screen.getByRole('button',{name:'한빛국수'})).toBeInTheDocument();
});
it.each(['가','😀'])('requests 100 code points but never requests 101 using %s',async character=>{
  mount();await screen.findByRole('button',{name:'한빛국수'});
  fireEvent.change(screen.getByLabelText('관리자 식당 검색'),{target:{value:`  ${character.repeat(100)}  `}});
  fireEvent.click(screen.getByRole('button',{name:'검색'}));await screen.findByRole('button',{name:'한빛국수'});
  expect(new URLSearchParams(listCalls().at(-1)![0].split('?')[1]).get('query')).toBe(character.repeat(100));
  const before=listCalls().length;
  const input=screen.getByLabelText('관리자 식당 검색');fireEvent.change(input,{target:{value:character.repeat(101)}});
  fireEvent.submit(input.closest('form')!);
  expect(screen.getByRole('alert')).toHaveTextContent('100자 이하');expect(listCalls()).toHaveLength(before);
  expect(screen.getByRole('button',{name:'한빛국수'})).toBeInTheDocument();
});
it('retains direct approval R10/M301 and retries public and visible failed reads without another approval',async()=>{
  let approved=false, failDetail=true;
  const published=vi.fn<()=>Promise<void>>().mockRejectedValueOnce(new Error('public failed')).mockResolvedValue(undefined);
  api.mockImplementation(async(path,options)=>{
    if(path.startsWith('/admin/places?'))return {candidates:[{placeSelectionToken:'token',place:{...place,id:'123'}}],totalCount:1,complete:true};
    if(path==='/admin/videos/metadata')return {id:'11111111111',title:'영상 A',channelTitle:'채널 A',publishedAt:'2026-01-01T00:00:00Z',description:'',extractionWarning:''};
    if(path==='/admin/mentions'&&options?.method==='POST'){approved=true;return {restaurantId:10,mentionId:301,version:0,verificationStatus:'APPROVED',publicationStatus:'PUBLISHED'};}
    if(approved&&path.startsWith('/admin/restaurants/10?')){
      if(failDetail)throw new ApiError(500,'detail failed');
      return {...detail,mentions:page([{...detail.mentions.items[1],id:301}])};
    }
    return defaults(path);
  });
  render(<AdminWorkspace username="admin" authGeneration={1} onExpired={vi.fn()} onPublished={published} onGuard={vi.fn()}/>);
  await screen.findByRole('button',{name:'한빛국수'});fireEvent.click(screen.getByRole('button',{name:'식당 직접 등록 · 초안 이어쓰기'}));
  fireEvent.change(screen.getByLabelText('식당 이름 검색'),{target:{value:'한빛'}});fireEvent.click(screen.getByRole('button',{name:'장소 검색'}));
  fireEvent.click(await screen.findByRole('button',{name:'이 지점 선택'}));
  fireEvent.change(screen.getByLabelText('관계 셰프 (승인 필수)'),{target:{value:'1'}});
  fireEvent.change(screen.getByLabelText('출처 영상 URL'),{target:{value:'https://youtu.be/11111111111'}});
  fireEvent.click(screen.getByRole('button',{name:'메타데이터 확인'}));await screen.findByText('영상 A');
  fireEvent.change(screen.getByLabelText('관계 유형 (승인 필수)'),{target:{value:'VISITED'}});
  fireEvent.click(screen.getByRole('button',{name:'승인 전 검토'}));fireEvent.click(screen.getByRole('button',{name:'직접 승인·공개'}));
  await screen.findByText(/직접 승인 완료 · 관계 ID 301/);
  const result=screen.getByRole('button',{name:'저장한 식당 상세로 이동'});await waitFor(()=>expect(result).toBeEnabled());
  expect(screen.getByText('작업 완료 · 식당 #10 · 관계 #301')).toBeInTheDocument();
  expect(api.mock.calls.filter(([path])=>path.startsWith('/admin/restaurants/10?'))).toHaveLength(0);
  expect(listCalls()).toHaveLength(1);expect(published).toHaveBeenCalledTimes(1);
  fireEvent.click(result);await screen.findByText('detail failed');
  const draftsBefore=api.mock.calls.filter(([path])=>path.includes('/mention-drafts?')).length;
  failDetail=false;fireEvent.click(screen.getByRole('button',{name:'관련 조회 다시 시도'}));
  await screen.findByRole('article',{name:'관계 301'});
  await waitFor(()=>expect(screen.queryByRole('button',{name:'관련 조회 다시 시도'})).not.toBeInTheDocument());
  expect(published).toHaveBeenCalledTimes(2);
  expect(api.mock.calls.filter(([path])=>path.includes('/mention-drafts?'))).toHaveLength(draftsBefore);
  expect(api.mock.calls.filter(([path])=>path.startsWith('/admin/restaurants/10?'))).toHaveLength(2);
  expect(api.mock.calls.filter(([path,o])=>path==='/admin/mentions'&&o?.method==='POST')).toHaveLength(1);
  expect(screen.getByText('작업 완료 · 식당 #10 · 관계 #301')).toBeInTheDocument();
  expect(screen.queryByRole('button',{name:'동일 키·동일 요청으로 결과 재확인'})).not.toBeInTheDocument();
});
it('keeps D201 APPLIED and reads relation and draft page zero once when returning after approval',async()=>{
  let approved=false;
  const published=vi.fn(async()=>{});
  api.mockImplementation(async(path,options)=>{
    if(path==='/admin/mention-drafts/201/approve'&&options?.method==='POST'){
      approved=true;return {draftId:201,draftVersion:8,draftStatus:'APPLIED',mentionId:301,restaurantId:10,mentionVersion:0,verificationStatus:'APPROVED',publicationStatus:'PUBLISHED'};
    }
    if(path.startsWith('/admin/restaurants/10?'))return {...detail,mentions:approved?page([{...detail.mentions.items[1],id:301}]):page(detail.mentions.items,40,Number(new URLSearchParams(path.split('?')[1]).get('mentionPage')),true)};
    if(path.includes('/mention-drafts?'))return approved?page([]):page([draft],40,Number(new URLSearchParams(path.split('?')[1]).get('page')),true);
    return defaults(path);
  });
  render(<AdminWorkspace username="admin" authGeneration={1} onExpired={vi.fn()} onPublished={published} onGuard={vi.fn()}/>);
  await openDetail();await screen.findByRole('button',{name:'다음 초안 페이지'});
  fireEvent.click(screen.getByRole('button',{name:'다음 관계 페이지'}));
  await waitFor(()=>expect(api.mock.calls.some(([path])=>path.includes('mentionPage=1'))).toBe(true));
  fireEvent.click(screen.getByRole('button',{name:'다음 초안 페이지'}));
  await waitFor(()=>expect(api.mock.calls.some(([path])=>path.includes('/mention-drafts?status=DRAFT&page=1'))).toBe(true));
  fireEvent.click(await screen.findByRole('button',{name:'초안 #201 이어쓰기'}));
  await waitFor(()=>expect(screen.getByRole('button',{name:'승인 전 검토'})).toBeEnabled());
  const before=api.mock.calls.filter(([path])=>path.startsWith('/admin/restaurants/10')).length;
  fireEvent.click(screen.getByRole('button',{name:'승인 전 검토'}));fireEvent.click(screen.getByRole('button',{name:'저장된 초안 승인·공개'}));
  await screen.findByText(/적용 완료 · 관계 ID 301/);
  const result=screen.getByRole('button',{name:'저장한 식당 상세로 이동'});await waitFor(()=>expect(result).toBeEnabled());
  expect(api.mock.calls.filter(([path])=>path.startsWith('/admin/restaurants/10'))).toHaveLength(before);
  expect(screen.queryByRole('button',{name:'승인 전 검토'})).not.toBeInTheDocument();
  fireEvent.click(result);await screen.findByRole('article',{name:'관계 301'});await screen.findByText('작성 중인 초안이 없습니다.');
  const after=api.mock.calls.filter(([path])=>path.startsWith('/admin/restaurants/10')).slice(before);
  expect(after.map(([path])=>path)).toEqual(expect.arrayContaining(['/admin/restaurants/10?mentionPage=0&mentionSize=20','/admin/restaurants/10/mention-drafts?status=DRAFT&page=0&size=20']));
  expect(after).toHaveLength(2);expect(published).toHaveBeenCalledTimes(1);
  expect(api.mock.calls.filter(([path,o])=>path==='/admin/mention-drafts/201/approve'&&o?.method==='POST')).toHaveLength(1);
  expect(screen.getByText('작업 완료 · 식당 #10 · 초안 #201 · 관계 #301')).toBeInTheDocument();
});
it('invalidates both restaurants on a draft move and reads fresh data only on reentry with the same list conditions',async()=>{
  let moved=false;
  const newPlace={...place,id:20,kakaoPlaceId:'456',name:'골목밥',placeUrl:'https://place.map.kakao.com/456'};
  api.mockImplementation(async(path,options)=>{
    if(path.startsWith('/admin/places?'))return {candidates:[{placeSelectionToken:'move-token',place:{...newPlace,id:'456'}}],totalCount:1,complete:true};
    if(path==='/admin/mention-drafts/201'&&options?.method==='PATCH'){moved=true;return {...draft,version:8,restaurant:newPlace};}
    if(moved&&path.startsWith('/admin/restaurants/10/mention-drafts?'))return page([]);
    if(moved&&path.startsWith('/admin/restaurants/20/mention-drafts?'))return page([{...draft,version:8,restaurant:newPlace}]);
    return defaults(path);
  });
  mount();await screen.findByRole('button',{name:'한빛국수'});
  fireEvent.change(screen.getByLabelText('공개 관계 필터'),{target:{value:'NO_PUBLIC_RELATION'}});
  await openDetail();fireEvent.click(await screen.findByRole('button',{name:'초안 #201 이어쓰기'}));
  await waitFor(()=>expect(screen.getByRole('button',{name:'수정 저장'})).toBeEnabled());
  fireEvent.click(screen.getByRole('button',{name:'장소 다시 선택'}));
  fireEvent.change(screen.getByLabelText('식당 이름 검색'),{target:{value:'골목'}});fireEvent.click(screen.getByRole('button',{name:'장소 검색'}));
  fireEvent.click(await screen.findByRole('button',{name:'이 지점 선택'}));
  const before=api.mock.calls.filter(([path])=>path.startsWith('/admin/restaurants')).length;
  fireEvent.click(screen.getByRole('button',{name:'수정 저장'}));await screen.findByText('초안을 수정 저장했습니다.');
  const result=screen.getByRole('button',{name:'저장한 식당 상세로 이동'});await waitFor(()=>expect(result).toBeEnabled());
  expect(api.mock.calls.filter(([path])=>path.startsWith('/admin/restaurants'))).toHaveLength(before);
  expect(screen.getByText('작업 완료 · 식당 #20 · 이전 식당 #10 · 초안 #201')).toBeInTheDocument();
  fireEvent.click(result);await screen.findByRole('heading',{name:'골목밥'});await screen.findByRole('button',{name:'초안 #201 이어쓰기'});
  expect(api.mock.calls.filter(([path])=>path.startsWith('/admin/restaurants/20?'))).toHaveLength(1);
  expect(api.mock.calls.filter(([path])=>path.startsWith('/admin/restaurants/20/mention-drafts?'))).toHaveLength(1);
  fireEvent.click(screen.getByRole('button',{name:'← 목록으로'}));await screen.findByRole('button',{name:'한빛국수'});
  expect(screen.getByLabelText('공개 관계 필터')).toHaveValue('NO_PUBLIC_RELATION');
  expect(listCalls().at(-1)?.[0]).toContain('publication=NO_PUBLIC_RELATION');
  await openDetail();await screen.findByText('작성 중인 초안이 없습니다.');
  expect(screen.queryByRole('button',{name:'초안 #201 이어쓰기'})).not.toBeInTheDocument();
  expect(api.mock.calls.filter(([path,o])=>path==='/admin/mention-drafts/201'&&o?.method==='PATCH')).toHaveLength(1);
  expect(api.mock.calls.find(([path,o])=>path==='/admin/mention-drafts/201'&&o?.method==='PATCH')?.[1]?.body).toEqual({expectedVersion:7,placeSelectionToken:'move-token'});
});
it('discards a late detail authentication error after changing selection',async()=>{
  let reject!:(e:unknown)=>void;
  const expired=vi.fn();
  api.mockImplementation(async path=>path.startsWith('/admin/restaurants/10?')?new Promise((_,r)=>{reject=r;}):defaults(path));
  mount(expired);fireEvent.click(await screen.findByRole('button',{name:'상세 보기 · 한빛국수'}));
  fireEvent.click(screen.getByRole('button',{name:'← 목록으로'}));
  fireEvent.click(await screen.findByRole('button',{name:'상세 보기 · 골목밥'}));await screen.findByRole('heading',{name:'골목밥'});
  await act(async()=>{reject(new ApiError(401,'old session'));});
  expect(expired).not.toHaveBeenCalled();expect(screen.queryByText('old session')).not.toBeInTheDocument();
  expect(screen.getByRole('heading',{name:'골목밥'})).toBeInTheDocument();
});
it('opens relation, official-place and chef correction history from the selected detail without writing',async()=>{
  api.mockImplementation(async path=>{
    if(path.endsWith('/history?page=0&size=20'))return page([]);
    if(path.endsWith('/corrections?page=0&size=20')||path.endsWith('/revisions?page=0&size=20'))return {...page([]),target:{id:101,version:0,name:'현재 표시'}};
    return defaults(path);
  });
  mount();await openDetail();
  for(const [button,path] of [
    ['관계 #101 정정 · 이력','/admin/mentions/101/corrections?page=0&size=20'],
    ['식당 공식 정보 정정 · 이력','/admin/restaurants/10/revisions?page=0&size=20'],
    ['셰프 #1 이름 정정 · 이력','/admin/chefs/1/revisions?page=0&size=20'],
  ]) {
    fireEvent.click(screen.getByRole('button',{name:button}));await screen.findByRole('heading',{name:'정보 정정 · 변경 이력'});
    await waitFor(()=>expect(api).toHaveBeenCalledWith(path,expect.objectContaining({signal:expect.any(AbortSignal)})));
    await screen.findByRole('button',{name:'새 정정안 작성'});
    fireEvent.click(screen.getByRole('button',{name:'← 식당 상세로'}));await screen.findByRole('heading',{name:'한빛국수'});
  }
  expect(api.mock.calls.filter(([,options])=>options?.method==='POST'||options?.method==='PATCH')).toHaveLength(0);
});
it('refreshes public data once after reapproval and reads the changed hidden detail and list on return',async()=>{
  let applied=false;const published=vi.fn(async()=>{});
  const proposed={name:'정정된 식당',roadAddress:place.roadAddress,address:null,longitude:127,latitude:37,placeUrl:place.placeUrl};
  const revision={id:401,targetId:10,kind:'RESTAURANT',version:0,baseVersion:0,status:'DRAFT',changeReason:'공식 표기 갱신',updatedAt:place.createdAt,baseSnapshot:place,currentSnapshot:place,proposedValues:proposed,changedFields:['name'],referenceSnapshot:{},currentReferences:{},baseConflict:false,referenceConflict:false,impactHash:'a'.repeat(64),impact:page([]),publicImpactCount:1};
  api.mockImplementation(async(path,options)=>{
    if(path==='/admin/restaurant-revisions/401/approve'){applied=true;return {...revision,status:'APPLIED',version:1,targetVersion:1};}
    if(path==='/admin/restaurant-revisions/401?impactPage=0&impactSize=20')return revision;
    if(path==='/admin/restaurants/10/revisions?page=0&size=20')return {...page(applied?[]:[revision]),target:applied?{...place,name:proposed.name,version:1}:place};
    if(path==='/admin/restaurants/10/history?page=0&size=20')return page([]);
    if(applied&&path.startsWith('/admin/restaurants/10?'))return {...detail,restaurant:{...place,name:proposed.name,version:1},summary:{...item,name:proposed.name}};
    if(applied&&path.startsWith('/admin/restaurants?'))return page([{...item,name:proposed.name}]);
    return defaults(path);
  });
  render(<AdminWorkspace username="admin" authGeneration={1} onExpired={vi.fn()} onPublished={published} onGuard={vi.fn()}/>);
  await openDetail();fireEvent.click(screen.getByRole('button',{name:'식당 공식 정보 정정 · 이력'}));
  fireEvent.click(await screen.findByRole('button',{name:/정정안 #401 · DRAFT/}));await screen.findByRole('table');
  fireEvent.click(screen.getByRole('button',{name:'영향 확인 · 재승인'}));await screen.findByText(/APPLIED · 적용 완료/);
  await waitFor(()=>expect(published).toHaveBeenCalledTimes(1));
  await waitFor(()=>expect(screen.getByRole('button',{name:'최신 정정안 조회 · 입력 유지'})).toBeEnabled());
  const detailCalls=()=>api.mock.calls.filter(([path])=>path.startsWith('/admin/restaurants/10?'));
  expect(detailCalls()).toHaveLength(1);
  fireEvent.click(screen.getByRole('button',{name:'← 식당 상세로'}));await screen.findByRole('heading',{name:'정정된 식당'});expect(detailCalls()).toHaveLength(2);
  fireEvent.click(screen.getByRole('button',{name:'← 목록으로'}));await screen.findByRole('button',{name:'정정된 식당'});
  expect(api.mock.calls.filter(([path,options])=>path==='/admin/restaurant-revisions/401/approve'&&options?.method==='POST')).toHaveLength(1);
});
