import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import CorrectionPanel from './CorrectionPanel';
import { correctionApi } from './correctionApi';
import { ApiError } from '../../../api';
import type { LeaveGuard } from '../AdminWorkspace';
import type { Revision, RevisionList } from './types';

vi.mock('./correctionApi',()=>({correctionApi:{list:vi.fn(),history:vi.fn(),get:vi.fn(),write:vi.fn()}}));
const draft: Revision={id:10,targetId:1,kind:'CHEF',version:0,baseVersion:0,status:'DRAFT',changeReason:'같은 사람 표기 정정',updatedAt:'2026-11-07T00:00:00Z',
  baseSnapshot:{id:1,version:0,name:'이전 이름'},currentSnapshot:{id:1,version:0,name:'이전 이름'},proposedValues:{name:'새 이름'},changedFields:['name'],referenceSnapshot:{},currentReferences:{},baseConflict:false,referenceConflict:false,impactHash:'a'.repeat(64),impact:{items:[{id:101,chefName:'이전 이름',restaurantName:'식당',videoTitle:'영상',publicationStatus:'PUBLISHED'}],totalElements:1,page:0,size:20,hasNext:false},publicImpactCount:1};
const list: RevisionList={target:{id:1,version:0,name:'이전 이름'},items:[draft],totalElements:1,page:0,size:20,hasNext:false};
beforeEach(()=>{
  vi.resetAllMocks();vi.mocked(correctionApi.list).mockResolvedValue(list);
  vi.mocked(correctionApi.history).mockResolvedValue({items:[],totalElements:0,page:0,size:20,hasNext:false});
  vi.mocked(correctionApi.get).mockResolvedValue(draft);vi.mocked(correctionApi.write).mockResolvedValue({...draft,version:1});
});
afterEach(cleanup);
function props(){return {target:{kind:'CHEF' as const,id:1},username:'admin' as string|undefined,authGeneration:1,onExpired:vi.fn(),onGuard:vi.fn<(guard:LeaveGuard|null)=>void>(),onBack:vi.fn(),onApproved:vi.fn(async()=>{})};}
async function open(){fireEvent.click(await screen.findByRole('button',{name:/정정안 #10 · DRAFT/}));await screen.findByDisplayValue('새 이름');}
it('compares baseline/current/proposal and keeps edits on explicit rebase after conflict',async()=>{
  vi.mocked(correctionApi.get).mockResolvedValue({...draft,baseConflict:true,currentSnapshot:{id:1,version:2,name:'다른 수정'}});
  vi.mocked(correctionApi.write).mockResolvedValue({...draft,version:1,baseVersion:2,currentSnapshot:{id:1,version:2,name:'다른 수정'}});
  render(<CorrectionPanel {...props()}/>);await open();
  const table=within(screen.getByRole('table'));expect(table.getByText('이전 이름')).toBeInTheDocument();expect(table.getByText('다른 수정')).toBeInTheDocument();expect(table.getByText('새 이름')).toBeInTheDocument();
  expect(screen.getByRole('button',{name:'영향 확인 · 재승인'})).toBeDisabled();
  fireEvent.change(screen.getByLabelText('같은 사람의 이름 표기'),{target:{value:'미저장 표기'}});
  fireEvent.click(screen.getByRole('button',{name:'비교한 현재본으로 기준 갱신'}));
  await waitFor(()=>expect(correctionApi.write).toHaveBeenCalledWith({kind:'CHEF',id:1},10,'rebase',{expectedVersion:0,expectedBaseVersion:2},undefined));
  await waitFor(()=>expect(screen.getByRole('button',{name:'정정안 저장'})).toBeEnabled());
  expect(screen.getByLabelText('같은 사람의 이름 표기')).toHaveValue('미저장 표기');expect(screen.getByRole('button',{name:'영향 확인 · 재승인'})).toBeDisabled();
});
it('preserves successful approval and retries GET only when history refresh fails',async()=>{
  vi.mocked(correctionApi.write).mockResolvedValue({...draft,status:'APPLIED',version:1,targetVersion:1});
  const p=props();render(<CorrectionPanel {...p}/>);await open();
  vi.mocked(correctionApi.history).mockRejectedValueOnce(new Error('read failure'));
  fireEvent.click(screen.getByRole('button',{name:'영향 확인 · 재승인'}));
  await screen.findByText('재승인 완료 · 정정안 #10 · 대상 #1');await screen.findByText(/후속 조회에 실패/);
  await waitFor(()=>expect(screen.getByRole('button',{name:'정정·이력 조회 다시 시도'})).toBeEnabled());
  expect(screen.getByText(/APPLIED · 적용 완료/)).toBeInTheDocument();expect(p.onApproved).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole('button',{name:'정정·이력 조회 다시 시도'}));await screen.findByText('변경 이력이 없습니다.');
  expect(correctionApi.write).toHaveBeenCalledTimes(1);expect(screen.getByText('재승인 완료 · 정정안 #10 · 대상 #1')).toBeInTheDocument();
  expect(screen.queryByRole('button',{name:'영향 확인 · 재승인'})).not.toBeInTheDocument();
});
it('locks double approval clicks and reconciles an ambiguous result with identical key and body',async()=>{
  let reject!:(e:unknown)=>void;
  vi.mocked(correctionApi.write).mockImplementationOnce(()=>new Promise((_,r)=>{reject=r;})).mockResolvedValueOnce({...draft,status:'APPLIED',version:1});
  render(<CorrectionPanel {...props()}/>);await open();
  const button=screen.getByRole('button',{name:'영향 확인 · 재승인'});fireEvent.click(button);fireEvent.click(button);
  expect(correctionApi.write).toHaveBeenCalledTimes(1);
  await act(async()=>reject(new TypeError('lost response')));
  fireEvent.click(await screen.findByRole('button',{name:'동일 키·동일 요청으로 결과 재확인'}));
  await screen.findByText(/APPLIED · 적용 완료/);expect(correctionApi.write).toHaveBeenCalledTimes(2);
  expect(vi.mocked(correctionApi.write).mock.calls[0]).toEqual(vi.mocked(correctionApi.write).mock.calls[1]);
  expect(vi.mocked(correctionApi.write).mock.calls[0][3]).toEqual({expectedVersion:0,expectedImpactHash:'a'.repeat(64)});
});
it('does not retry an ambiguous PATCH and preserves input while comparing the server version',async()=>{
  vi.mocked(correctionApi.write).mockRejectedValueOnce(new TypeError('lost PATCH'));
  render(<CorrectionPanel {...props()}/>);await open();
  fireEvent.change(screen.getByLabelText('같은 사람의 이름 표기'),{target:{value:'내 표기'}});fireEvent.click(screen.getByRole('button',{name:'정정안 저장'}));
  fireEvent.click(await screen.findByRole('button',{name:'최신본 조회 · 입력 비교'}));
  await waitFor(()=>expect(correctionApi.get).toHaveBeenCalledTimes(2));
  expect(screen.getByLabelText('같은 사람의 이름 표기')).toHaveValue('내 표기');
  expect(correctionApi.write).toHaveBeenCalledTimes(1);expect(screen.queryByRole('button',{name:'동일 키·동일 요청으로 결과 재확인'})).not.toBeInTheDocument();
});
it('uses one leave guard and retains input on cancel',async()=>{
  const p=props();render(<CorrectionPanel {...p}/>);await open();fireEvent.change(screen.getByLabelText('필수 정정 사유'),{target:{value:'미저장 사유'}});
  act(()=>p.onGuard.mock.calls.at(-1)?.[0]?.(p.onBack));expect(screen.getAllByRole('dialog')).toHaveLength(1);
  fireEvent.click(screen.getByRole('button',{name:'계속 작성'}));expect(screen.getByLabelText('필수 정정 사유')).toHaveValue('미저장 사유');expect(p.onBack).not.toHaveBeenCalled();
});
it('ignores stale revision errors after auth expiry and retains same-account input',async()=>{
  const p=props();const view=render(<CorrectionPanel {...p}/>);await open();
  fireEvent.change(screen.getByLabelText('필수 정정 사유'),{target:{value:'계정 입력'}});
  let reject!:(e:unknown)=>void;vi.mocked(correctionApi.get).mockImplementationOnce(()=>new Promise((_,r)=>{reject=r;}));
  fireEvent.click(screen.getByRole('button',{name:'최신 정정안 조회 · 입력 유지'}));
  view.rerender(<CorrectionPanel {...p} username={undefined} authGeneration={2}/>);
  expect(screen.queryByRole('table')).not.toBeInTheDocument();await act(async()=>reject(new ApiError(401,'old auth')));
  expect(p.onExpired).not.toHaveBeenCalled();view.rerender(<CorrectionPanel {...p} authGeneration={3}/>);
  expect(await screen.findByLabelText('필수 정정 사유')).toHaveValue('계정 입력');
});
it('does not replace an approved result with an earlier comparison response',async()=>{
  const p=props();render(<CorrectionPanel {...p}/>);await open();
  let resolve!:(r:Revision)=>void;vi.mocked(correctionApi.get).mockImplementationOnce(()=>new Promise(r=>{resolve=r;}));
  fireEvent.click(screen.getByRole('button',{name:'최신 정정안 조회 · 입력 유지'}));
  vi.mocked(correctionApi.write).mockResolvedValue({...draft,status:'APPLIED',version:1});
  fireEvent.click(screen.getByRole('button',{name:'영향 확인 · 재승인'}));await screen.findByText(/APPLIED · 적용 완료/);
  await act(async()=>resolve(draft));expect(screen.getByText(/APPLIED · 적용 완료/)).toBeInTheDocument();
  expect(screen.queryByRole('button',{name:'영향 확인 · 재승인'})).not.toBeInTheDocument();expect(correctionApi.write).toHaveBeenCalledTimes(1);
});
it('distinguishes stored pre-approval confirmation from post-apply snapshots and keeps legacy history readable',async()=>{
  const h0='b'.repeat(64),h1='c'.repeat(64);
  vi.mocked(correctionApi.history).mockResolvedValue({items:[
    {id:2,actorId:1,action:'CORRECTION_APPROVED',reason:'승인 사유',occurredAt:'2026-11-07T01:00:00Z',beforeValue:{name:'이전 이름'},afterValue:{actorUsername:'처리 당시 관리자',approvalConfirmation:{schemaVersion:1,expectedImpactHash:h0,validatedImpactHash:h0,targetVersion:3,revisionVersion:2,impactTotalElements:26,publicImpactCount:14},target:{name:'적용 이름'},revision:{impactHash:h1}}},
    {id:1,actorId:1,action:'PAST_EVENT',reason:'과거 사유',occurredAt:'2026-11-06T00:00:00Z',beforeValue:null,afterValue:{name:'과거 표시 이름'}},
  ],totalElements:2,page:0,size:20,hasNext:false});
  render(<CorrectionPanel {...props()}/>);
  fireEvent.click(await screen.findByText(/^CORRECTION_APPROVED · 승인 사유/));
  const confirmation=within(screen.getByRole('region',{name:'승인 당시 영향 확인'}));
  expect(confirmation.getAllByText(h0)).toHaveLength(2);expect(confirmation.getByText('26개')).toBeInTheDocument();expect(confirmation.getByText('14개')).toBeInTheDocument();
  expect(confirmation.getByText('3')).toBeInTheDocument();expect(confirmation.getByText('2')).toBeInTheDocument();expect(screen.queryByText(h1)).not.toBeInTheDocument();
  fireEvent.click(screen.getByText(/^PAST_EVENT · 과거 사유/));expect(screen.getByText('과거 표시 이름')).toBeVisible();
  expect(screen.getAllByRole('region',{name:'승인 당시 영향 확인'})).toHaveLength(1);expect(correctionApi.write).not.toHaveBeenCalled();
});
it('explains that restaurant rebase preserves the stored whole official proposal',async()=>{
  const restaurant={id:10,version:1,name:'현재 장소',roadAddress:'현재 도로',address:'현재 지번',longitude:127,latitude:37,placeUrl:'https://place.map.kakao.com/123'};
  const official={name:'공식 장소',roadAddress:'공식 도로',address:null,longitude:127,latitude:37,placeUrl:'https://place.map.kakao.com/123'};
  const revision:Revision={...draft,kind:'RESTAURANT',targetId:10,baseConflict:true,currentSnapshot:restaurant,proposedValues:official};
  vi.mocked(correctionApi.list).mockResolvedValue({...list,target:restaurant,items:[revision]});vi.mocked(correctionApi.get).mockResolvedValue(revision);
  vi.mocked(correctionApi.write).mockResolvedValue({...revision,baseVersion:1,version:1,baseConflict:false});
  render(<CorrectionPanel {...props()} target={{kind:'RESTAURANT',id:10}}/>);
  fireEvent.click(await screen.findByRole('button',{name:/정정안 #10 · DRAFT/}));await screen.findByRole('table');
  expect(screen.getByText(/기준 갱신은 기준본만 갱신하며 저장된 공식 장소 제안 전체를 유지/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'비교한 현재본으로 기준 갱신'}));
  await waitFor(()=>expect(correctionApi.write).toHaveBeenCalledWith({kind:'RESTAURANT',id:10},10,'rebase',{expectedVersion:0,expectedBaseVersion:1},undefined));
  await waitFor(()=>expect(screen.getByRole('button',{name:'정정안 저장'})).toBeEnabled());
  expect(within(screen.getByRole('table')).getByText('공식 도로')).toBeInTheDocument();
  const addressRow=within(screen.getByRole('table')).getByRole('row',{name:/지번 주소/});
  expect(within(addressRow).getAllByRole('cell')[2]).toHaveTextContent('미확인');
  expect(correctionApi.write).toHaveBeenCalledTimes(1);
});
