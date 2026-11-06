import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import RegistrationForm from './RegistrationForm';
import { registrationApi as api } from './registrationApi';
import type { Draft, Video } from './types';
import { ApiError } from '../../../api';
vi.mock('./registrationApi',()=>({registrationApi:{chefs:vi.fn(),places:vi.fn(),metadata:vi.fn(),videos:vi.fn(),draft:vi.fn(),createDraft:vi.fn(),patch:vi.fn(),direct:vi.fn(),approve:vi.fn(),createChef:vi.fn(),restaurants:vi.fn(),drafts:vi.fn()}}));
const place={id:'123',name:'한빛국수',roadAddress:'서울 마포구 10',address:null,longitude:127,latitude:37,placeUrl:'https://place.map.kakao.com/123'};
const video:Video={id:'12345678901',title:'원본 영상',channelTitle:'채널',publishedAt:'2026-01-01T00:00:00Z',description:'',extractionWarning:''};
const draft:Draft={id:4,kind:'CREATE',status:'DRAFT',version:2,restaurant:{...place,id:10,kakaoPlaceId:'123'},chefId:1,videoId:video.id,mentionType:'VISITED',timestampSeconds:0,verificationNote:null,candidateId:null,appliedMentionId:null,createdBy:1,updatedBy:1,createdAt:'2026-01-01T00:00:00Z',updatedAt:'2026-01-01T00:00:00Z'};
beforeEach(()=>{vi.resetAllMocks();window.history.replaceState(null,'','/');vi.mocked(api.chefs).mockResolvedValue([{id:1,name:'동명'},{id:2,name:'동명'}]);vi.mocked(api.videos).mockResolvedValue([video]);vi.mocked(api.places).mockResolvedValue({candidates:[{placeSelectionToken:'token',place}],totalCount:1,complete:true});vi.mocked(api.metadata).mockResolvedValue(video);vi.spyOn(window,'confirm').mockReturnValue(true);});
afterEach(()=>{cleanup();vi.restoreAllMocks();});
const published=vi.fn(async()=>{});
function mount() {return render(<RegistrationForm username="admin" onExpired={vi.fn()} onPublished={published} onClose={vi.fn()} onGuard={vi.fn()}/>);}
async function choose() {fireEvent.change(screen.getByLabelText('식당 이름 검색'),{target:{value:'한빛'}});fireEvent.click(screen.getByRole('button',{name:'장소 검색'}));fireEvent.click(await screen.findByRole('button',{name:'이 지점 선택'}));}
async function complete() {await choose();fireEvent.change(screen.getByLabelText('관계 셰프 (승인 필수)'),{target:{value:'1'}});fireEvent.change(screen.getByLabelText('출처 영상 URL'),{target:{value:'https://youtu.be/12345678901'}});fireEvent.click(screen.getByRole('button',{name:'메타데이터 확인'}));await screen.findByText('원본 영상');fireEvent.change(screen.getByLabelText('관계 유형 (승인 필수)'),{target:{value:'VISITED'}});}
async function readyDraft() {
  await screen.findByDisplayValue('0:00');
  await waitFor(()=>expect(screen.getByRole('button',{name:'승인 전 검토'})).toBeEnabled());
}
it('saves a restaurant-only draft with null fields and keeps unconfirmed URL unsaved',async()=>{
  vi.mocked(api.createDraft).mockResolvedValue({...draft,chefId:null,videoId:null,mentionType:null,timestampSeconds:null});mount();await choose();
  expect(screen.getByRole('button',{name:'승인 전 검토'})).toBeDisabled();
  fireEvent.change(screen.getByLabelText('출처 영상 URL'),{target:{value:'https://youtu.be/unconfirmed'}});
  fireEvent.click(screen.getByRole('button',{name:'임시 저장'}));
  await waitFor(()=>expect(api.createDraft).toHaveBeenCalledWith({placeSelectionToken:'token',chefId:null,videoId:null,mentionType:null,timestampSeconds:null,verificationNote:null},expect.any(String)));
  expect(await screen.findByText(/미확정 URL은 저장되지 않았습니다/)).toBeInTheDocument();
  expect(screen.getByLabelText('출처 영상 URL')).toHaveValue('https://youtu.be/unconfirmed');
  expect(window.location.search).toBe('?draftId=4');expect(api.direct).not.toHaveBeenCalled();
});
it('directly approves with zero and no note, locks double clicks and keeps success on refresh failure',async()=>{
  let resolve!:(value:Awaited<ReturnType<typeof api.direct>>)=>void;
  vi.mocked(api.direct).mockImplementation(()=>new Promise(r=>{resolve=r;}));published.mockRejectedValueOnce(new Error('refresh'));
  mount();await complete();fireEvent.change(screen.getByLabelText('확인된 시점 (선택)'),{target:{value:'00:00'}});
  fireEvent.click(screen.getByRole('button',{name:'승인 전 검토'}));const button=screen.getByRole('button',{name:'직접 승인·공개'});fireEvent.click(button);fireEvent.click(button);
  expect(api.direct).toHaveBeenCalledTimes(1);expect(api.direct).toHaveBeenCalledWith(expect.objectContaining({timestampSeconds:0,verificationNote:null,mentionType:'VISITED'}),expect.any(String));
  resolve({mentionId:9,restaurantId:10,version:0,verificationStatus:'APPROVED',publicationStatus:'PUBLISHED'});
  expect(await screen.findByText(/공개 목록 갱신은 실패/)).toBeInTheDocument();expect(api.createDraft).not.toHaveBeenCalled();
});
it('reconfirms an ambiguous create with identical key and body and never creates a second request on edit',async()=>{
  vi.mocked(api.createDraft).mockRejectedValueOnce(new TypeError('network')).mockResolvedValueOnce({...draft,chefId:null,videoId:null,mentionType:null,timestampSeconds:null});
  mount();await choose();fireEvent.click(screen.getByRole('button',{name:'임시 저장'}));
  fireEvent.click(await screen.findByRole('button',{name:'동일 키·동일 요청으로 결과 재확인'}));
  await waitFor(()=>expect(api.createDraft).toHaveBeenCalledTimes(2));expect(vi.mocked(api.createDraft).mock.calls[0]).toEqual(vi.mocked(api.createDraft).mock.calls[1]);
  expect(await screen.findByText('초안을 저장했습니다.')).toBeInTheDocument();
});
it('resumes by draft id without external metadata, patches explicit nulls then approves the saved version',async()=>{
  window.history.replaceState(null,'','/?draftId=4');vi.mocked(api.draft).mockResolvedValue(draft);
  vi.mocked(api.patch).mockResolvedValue({...draft,version:3,verificationNote:'수정'});
  vi.mocked(api.approve).mockResolvedValue({draftId:4,draftVersion:4,draftStatus:'APPLIED',mentionId:9,restaurantId:10,mentionVersion:0,verificationStatus:'APPROVED',publicationStatus:'PUBLISHED'});
  mount();await readyDraft();
  expect(api.metadata).not.toHaveBeenCalled();fireEvent.change(screen.getByLabelText('검증 메모 (선택)'),{target:{value:'수정'}});
  expect(screen.getByRole('button',{name:'승인 전 검토'})).toBeDisabled();fireEvent.click(screen.getByRole('button',{name:'수정 저장'}));
  await waitFor(()=>expect(api.patch).toHaveBeenCalledWith(4,{expectedVersion:2,verificationNote:'수정'}));
  await waitFor(()=>expect(screen.getByRole('button',{name:'승인 전 검토'})).toBeEnabled());fireEvent.click(screen.getByRole('button',{name:'승인 전 검토'}));fireEvent.click(screen.getByRole('button',{name:'저장된 초안 승인·공개'}));
  await waitFor(()=>expect(api.approve).toHaveBeenCalledWith(4,3,expect.any(String)));expect(await screen.findByText(/적용 완료/)).toBeInTheDocument();expect(api.direct).not.toHaveBeenCalled();
});
it('invalidates confirmed video on URL changes and rejects invalid timestamps before saving',async()=>{
  mount();await complete();fireEvent.change(screen.getByLabelText('출처 영상 URL'),{target:{value:'https://youtu.be/other'}});
  expect(screen.getByRole('button',{name:'승인 전 검토'})).toBeDisabled();fireEvent.change(screen.getByLabelText('확인된 시점 (선택)'),{target:{value:'1:60'}});fireEvent.click(screen.getByRole('button',{name:'임시 저장'}));
  expect(await screen.findByRole('alert')).toHaveTextContent('0~59');expect(api.createDraft).not.toHaveBeenCalled();
});
it('distinguishes incomplete and empty search results and never auto-selects',async()=>{
  vi.mocked(api.places).mockResolvedValueOnce({candidates:[],totalCount:0,complete:true});mount();fireEvent.change(screen.getByLabelText('식당 이름 검색'),{target:{value:'없음'}});fireEvent.click(screen.getByRole('button',{name:'장소 검색'}));
  expect(await screen.findByText(/검색 결과가 없습니다/)).toBeInTheDocument();expect(api.createDraft).not.toHaveBeenCalled();
});
it('clears selected optional fields with null and omits untouched fields',async()=>{
  window.history.replaceState(null,'','/?draftId=4');vi.mocked(api.draft).mockResolvedValue({...draft,verificationNote:'메모'});
  vi.mocked(api.patch).mockResolvedValue({...draft,version:3,chefId:null,verificationNote:null});mount();await screen.findByDisplayValue('메모');
  fireEvent.change(screen.getByLabelText('관계 셰프 (승인 필수)'),{target:{value:''}});fireEvent.change(screen.getByLabelText('검증 메모 (선택)'),{target:{value:''}});
  fireEvent.click(screen.getByRole('button',{name:'수정 저장'}));await waitFor(()=>expect(api.patch).toHaveBeenCalledWith(4,{expectedVersion:2,chefId:null,verificationNote:null}));
});
it('does not retry ambiguous PATCH and reconciles the server version explicitly',async()=>{
  window.history.replaceState(null,'','/?draftId=4');vi.mocked(api.draft).mockResolvedValue(draft);vi.mocked(api.patch).mockRejectedValueOnce(new TypeError('lost'));
  mount();await readyDraft();fireEvent.change(screen.getByLabelText('검증 메모 (선택)'),{target:{value:'수정'}});fireEvent.click(screen.getByRole('button',{name:'수정 저장'}));
  expect(await screen.findByText(/PATCH는 자동 재전송하지 않습니다/)).toBeInTheDocument();expect(screen.queryByRole('button',{name:'동일 키·동일 요청으로 결과 재확인'})).not.toBeInTheDocument();
  vi.mocked(api.draft).mockResolvedValue({...draft,version:3,verificationNote:'수정'});fireEvent.click(screen.getByRole('button',{name:'서버 최신 초안 조회·비교'}));
  fireEvent.click(await screen.findByRole('button',{name:'최신 버전 확인 · 내 입력 유지'}));
  await waitFor(()=>expect(screen.getByRole('button',{name:'승인 전 검토'})).toBeEnabled());expect(api.patch).toHaveBeenCalledTimes(1);
});
it('creates homonymous chefs explicitly and never retries an ambiguous chef create',async()=>{
  vi.mocked(api.createChef).mockRejectedValueOnce(new TypeError('lost'));mount();await choose();
  fireEvent.change(screen.getByLabelText('기존 셰프 검색'),{target:{value:'동명'}});
  expect(screen.getByRole('option',{name:'동명 · ID 1'})).toBeInTheDocument();expect(screen.getByRole('option',{name:'동명 · ID 2'})).toBeInTheDocument();
  expect(api.createChef).not.toHaveBeenCalled();fireEvent.click(screen.getByRole('button',{name:'동명이인 포함 신규 셰프 생성'}));
  expect(await screen.findByText(/셰프 생성은 자동 재전송하지 않습니다/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'셰프 목록 다시 조회'}));await waitFor(()=>expect(api.chefs).toHaveBeenCalledTimes(2));
  expect(api.createChef).toHaveBeenCalledTimes(1);fireEvent.click(screen.getByRole('button',{name:'목록 확인 완료 · 계속 작성'}));
});
it('preserves relation inputs after place expiry and submits a newly selected token',async()=>{
  vi.mocked(api.direct).mockRejectedValueOnce(new ApiError(410,'expired',{code:'PLACE_SELECTION_EXPIRED'}))
    .mockResolvedValueOnce({mentionId:9,restaurantId:10,version:0,verificationStatus:'APPROVED',publicationStatus:'PUBLISHED'});
  mount();await complete();fireEvent.change(screen.getByLabelText('검증 메모 (선택)'),{target:{value:'유지할 메모'}});
  fireEvent.click(screen.getByRole('button',{name:'승인 전 검토'}));fireEvent.click(screen.getByRole('button',{name:'직접 승인·공개'}));
  expect(await screen.findByRole('alert')).toHaveTextContent('만료');
  vi.mocked(api.places).mockResolvedValue({candidates:[{placeSelectionToken:'new-token',place}],totalCount:1,complete:true});await choose();
  expect(screen.getByLabelText('검증 메모 (선택)')).toHaveValue('유지할 메모');fireEvent.click(screen.getByRole('button',{name:'승인 전 검토'}));fireEvent.click(screen.getByRole('button',{name:'직접 승인·공개'}));
  await waitFor(()=>expect(api.direct).toHaveBeenLastCalledWith(expect.objectContaining({placeSelectionToken:'new-token',verificationNote:'유지할 메모'}),expect.any(String)));
});
it.each([['DUPLICATE_RELATION', '기존 관계 ID: 19'],['STALE_VERSION','서버 버전: 8'],['DRAFT_ALREADY_APPLIED','적용 관계 ID: 20']])('shows %s without overwriting or resubmitting',async(code,message)=>{
  window.history.replaceState(null,'','/?draftId=4');vi.mocked(api.draft).mockResolvedValue(draft);
  vi.mocked(api.approve).mockRejectedValue(new ApiError(409,'conflict',{code,existingMentionId:19,currentVersion:8,appliedMentionId:20}));
  mount();await readyDraft();fireEvent.click(screen.getByRole('button',{name:'승인 전 검토'}));fireEvent.click(screen.getByRole('button',{name:'저장된 초안 승인·공개'}));
  expect(await screen.findByRole('alert')).toHaveTextContent(message);expect(api.approve).toHaveBeenCalledTimes(1);expect(api.direct).not.toHaveBeenCalled();expect(api.patch).not.toHaveBeenCalled();
});
it('protects unsaved exit with continue/discard actions and never auto-saves',async()=>{
  const close=vi.fn();render(<RegistrationForm username="admin" onExpired={vi.fn()} onPublished={published} onClose={close} onGuard={vi.fn()}/>);await choose();
  fireEvent.click(screen.getByRole('button',{name:'양식 닫기'}));expect(screen.getByRole('dialog')).toBeInTheDocument();fireEvent.click(screen.getByRole('button',{name:'계속 작성'}));
  expect(screen.getByLabelText('검증 메모 (선택)')).toBeInTheDocument();expect(close).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button',{name:'양식 닫기'}));fireEvent.click(screen.getByRole('button',{name:'저장하지 않고 나가기'}));expect(close).toHaveBeenCalledOnce();expect(api.createDraft).not.toHaveBeenCalled();
});
it('keeps inputs through same-account login expiry, clears old place results and isolates a different account',async()=>{
  const expired=vi.fn(), onGuard=vi.fn(), close=vi.fn();
  const view=render(<RegistrationForm username="admin" onExpired={expired} onPublished={published} onClose={close} onGuard={onGuard}/>);
  await choose();fireEvent.change(screen.getByLabelText('검증 메모 (선택)'),{target:{value:'계정 메모'}});
  view.rerender(<RegistrationForm username={undefined} onExpired={expired} onPublished={published} onClose={close} onGuard={onGuard}/>);
  view.rerender(<RegistrationForm username="admin" onExpired={expired} onPublished={published} onClose={close} onGuard={onGuard}/>);
  expect(screen.queryByRole('button',{name:'이 지점 선택'})).not.toBeInTheDocument();await choose();expect(screen.getByLabelText('검증 메모 (선택)')).toHaveValue('계정 메모');
  view.rerender(<RegistrationForm username="other" onExpired={expired} onPublished={published} onClose={close} onGuard={onGuard}/>);
  await choose();expect(screen.getByLabelText('검증 메모 (선택)')).toHaveValue('');expect(api.direct).not.toHaveBeenCalled();
});
it('reports external search failure and partial results without saving any place',async()=>{
  vi.mocked(api.places).mockRejectedValueOnce(new Error('외부 검색 실패')).mockResolvedValueOnce({candidates:[{placeSelectionToken:'token',place}],totalCount:50,complete:false});
  mount();fireEvent.change(screen.getByLabelText('식당 이름 검색'),{target:{value:'국수'}});fireEvent.click(screen.getByRole('button',{name:'장소 검색'}));
  expect(await screen.findByRole('alert')).toHaveTextContent('외부 검색 실패');fireEvent.click(screen.getByRole('button',{name:'장소 검색'}));
  expect(await screen.findByText(/일부만 표시됩니다/)).toBeInTheDocument();expect(api.createDraft).not.toHaveBeenCalled();expect(screen.queryByLabelText('검증 메모 (선택)')).not.toBeInTheDocument();
});
it('saves, closes, resumes through the restaurant picker and approves without a place token',async()=>{
  vi.mocked(api.createDraft).mockResolvedValue(draft);vi.mocked(api.draft).mockResolvedValue(draft);
  vi.mocked(api.restaurants).mockResolvedValue({items:[{id:10,name:place.name,roadAddress:place.roadAddress,address:null}],totalElements:1,page:0,size:20,hasNext:false});
  vi.mocked(api.drafts).mockResolvedValue({items:[draft],totalElements:1,page:0,size:20,hasNext:false});
  vi.mocked(api.approve).mockResolvedValue({draftId:4,draftVersion:3,draftStatus:'APPLIED',mentionId:9,restaurantId:10,mentionVersion:0,verificationStatus:'APPROVED',publicationStatus:'PUBLISHED'});
  const first=mount();await complete();fireEvent.change(screen.getByLabelText('확인된 시점 (선택)'),{target:{value:'00:00'}});
  fireEvent.click(screen.getByRole('button',{name:'임시 저장'}));await screen.findByText('초안을 저장했습니다.');
  fireEvent.click(screen.getByRole('button',{name:'양식 닫기'}));first.unmount();expect(window.location.search).toBe('');
  mount();fireEvent.click(screen.getByText('초안 이어쓰기'));fireEvent.change(screen.getByLabelText('초안을 찾을 식당명·주소·셰프'),{target:{value:'한빛'}});
  fireEvent.click(screen.getByRole('button',{name:'식당에서 초안 찾기'}));fireEvent.click(await screen.findByRole('button',{name:/한빛국수 · 서울/}));
  fireEvent.click(await screen.findByRole('button',{name:/초안 #4 · 수정/}));await readyDraft();
  fireEvent.click(screen.getByRole('button',{name:'승인 전 검토'}));fireEvent.click(screen.getByRole('button',{name:'저장된 초안 승인·공개'}));
  await waitFor(()=>expect(api.approve).toHaveBeenCalledWith(4,2,expect.any(String)));expect(api.createDraft).toHaveBeenCalledTimes(1);expect(api.metadata).toHaveBeenCalledTimes(1);expect(api.direct).not.toHaveBeenCalled();
});
it.each([401,403])('preserves inputs and does not retry a rejected %i write',async status=>{
  vi.mocked(api.direct).mockRejectedValue(new ApiError(status,'권한 확인'));const expired=vi.fn();
  render(<RegistrationForm username="admin" onExpired={expired} onPublished={published} onClose={vi.fn()} onGuard={vi.fn()}/>);
  await complete();fireEvent.change(screen.getByLabelText('검증 메모 (선택)'),{target:{value:'보존'}});fireEvent.click(screen.getByRole('button',{name:'승인 전 검토'}));fireEvent.click(screen.getByRole('button',{name:'직접 승인·공개'}));
  await screen.findByRole('alert');expect(api.direct).toHaveBeenCalledTimes(1);expect(expired).toHaveBeenCalledTimes(status===401?1:0);
  if(status===403) expect(screen.getByLabelText('검증 메모 (선택)')).toHaveValue('보존');else {await choose();expect(screen.getByLabelText('검증 메모 (선택)')).toHaveValue('보존');}
});
it('reconfirms a lost draft approval with the same version and idempotency key',async()=>{
  window.history.replaceState(null,'','/?draftId=4');vi.mocked(api.draft).mockResolvedValue(draft);
  vi.mocked(api.approve).mockRejectedValueOnce(new TypeError('lost')).mockResolvedValueOnce({draftId:4,draftVersion:3,draftStatus:'APPLIED',mentionId:9,restaurantId:10,mentionVersion:0,verificationStatus:'APPROVED',publicationStatus:'PUBLISHED'});
  mount();await readyDraft();fireEvent.click(screen.getByRole('button',{name:'승인 전 검토'}));fireEvent.click(screen.getByRole('button',{name:'저장된 초안 승인·공개'}));
  fireEvent.click(await screen.findByRole('button',{name:'동일 키·동일 요청으로 결과 재확인'}));await waitFor(()=>expect(api.approve).toHaveBeenCalledTimes(2));
  expect(vi.mocked(api.approve).mock.calls[0]).toEqual(vi.mocked(api.approve).mock.calls[1]);expect(await screen.findByText(/읽기 전용입니다/)).toBeInTheDocument();
});
it('reconfirms a lost direct approval with the identical request and key',async()=>{
  vi.mocked(api.direct).mockRejectedValueOnce(new TypeError('lost')).mockResolvedValueOnce({mentionId:9,restaurantId:10,version:0,verificationStatus:'APPROVED',publicationStatus:'PUBLISHED'});
  mount();await complete();fireEvent.click(screen.getByRole('button',{name:'승인 전 검토'}));fireEvent.click(screen.getByRole('button',{name:'직접 승인·공개'}));
  fireEvent.click(await screen.findByRole('button',{name:'동일 키·동일 요청으로 결과 재확인'}));await waitFor(()=>expect(api.direct).toHaveBeenCalledTimes(2));
  expect(vi.mocked(api.direct).mock.calls[0]).toEqual(vi.mocked(api.direct).mock.calls[1]);expect(await screen.findByText(/읽기 전용입니다/)).toBeInTheDocument();
});
it('creates a homonym as a separate ID only on explicit request',async()=>{
  vi.mocked(api.createChef).mockResolvedValue({id:3,name:'동명'});mount();await choose();fireEvent.change(screen.getByLabelText('기존 셰프 검색'),{target:{value:'동명'}});
  fireEvent.click(screen.getByRole('button',{name:'동명이인 포함 신규 셰프 생성'}));await waitFor(()=>expect(screen.getByLabelText('관계 셰프 (승인 필수)')).toHaveValue('3'));
  expect(api.createChef).toHaveBeenCalledWith('동명');expect(screen.getByRole('option',{name:'동명 · ID 1'})).toBeInTheDocument();
});
it('does not allow mutation of a resumed applied draft',async()=>{
  window.history.replaceState(null,'','/?draftId=4');vi.mocked(api.draft).mockResolvedValue({...draft,status:'APPLIED',appliedMentionId:9});mount();
  expect(await screen.findByText(/읽기 전용입니다/)).toBeInTheDocument();expect(screen.getByLabelText('검증 메모 (선택)')).toBeDisabled();
  expect(screen.queryByRole('button',{name:'수정 저장'})).not.toBeInTheDocument();expect(api.approve).not.toHaveBeenCalled();expect(api.direct).not.toHaveBeenCalled();
});
