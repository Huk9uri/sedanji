import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ApiError } from '../../../api';
import type { LeaveGuard } from '../AdminWorkspace';
import PublicationDialog from './PublicationDialog';
import { publicationApi } from './publicationApi';
import type { PublicationContext, PublicationResult } from './publicationApi';

vi.mock('./publicationApi',()=>({publicationApi:{get:vi.fn(),history:vi.fn(),write:vi.fn()}}));
const context:PublicationContext={mention:{id:12,version:3,chefId:1,chefName:'확인 셰프',restaurantId:10,restaurantName:'확인 지점',videoId:'11111111111',videoTitle:'근거 영상',channelTitle:'채널',mentionType:'VISITED',timestampSeconds:0,verificationStatus:'APPROVED',publicationStatus:'SUSPENDED',evidence:'보존 근거',verificationNote:'기존 메모',candidateId:7},
  references:{restaurant:{roadAddress:'현재 도로 주소',address:null,kakaoPlaceId:'123',placeUrl:'https://place.map.kakao.com/123'}},contextHash:'a'.repeat(64),latestControl:null,lastSuspendReason:null};
const result:PublicationResult={action:'RESTORE_MENTION',mentionId:12,restaurantId:10,previousVersion:3,version:4,verificationStatus:'APPROVED',publicationStatus:'PUBLISHED',auditEventId:1,occurredAt:'2026-11-07T00:00:00Z'};
function deferred<T>() {let resolve!:(value:T)=>void;let reject!:(error:unknown)=>void;const promise=new Promise<T>((yes,no)=>{resolve=yes;reject=no;});return {promise,resolve,reject};}
function props(){return {id:12,action:'restore' as const,username:'admin' as string|undefined,authGeneration:1,onExpired:vi.fn(),onGuard:vi.fn<(guard:LeaveGuard|null)=>void>(),onClose:vi.fn(),onCommitted:vi.fn(async(_result:PublicationResult)=>{})};}
beforeEach(()=>{
  vi.resetAllMocks();vi.mocked(publicationApi.get).mockResolvedValue(context);
  vi.mocked(publicationApi.history).mockResolvedValue({items:[],page:0,size:20,totalElements:0,hasNext:false});
  vi.mocked(publicationApi.write).mockResolvedValue(result);
});
afterEach(cleanup);
async function fill() {
  await screen.findByText('확인 셰프 · 확인 지점');
  fireEvent.change(screen.getByLabelText('처리 사유'),{target:{value:'복구 사유'}});
  fireEvent.change(screen.getByLabelText('재확인 내용'),{target:{value:'현재 근거 재확인'}});
  fireEvent.click(screen.getByRole('checkbox'));
}
it('shows current provenance and requires explicit confirmation while keeping zero seconds',async()=>{
  render(<PublicationDialog {...props()}/>);await screen.findByText('확인 셰프 · 확인 지점');
  expect(screen.getByText(/현재 도로 주소/)).toBeInTheDocument();expect(screen.getByText(/중단 사유 기록 없음/)).toBeInTheDocument();
  expect(screen.getByRole('link',{name:'출처 영상 확인'})).toHaveAttribute('href','https://www.youtube.com/watch?v=11111111111&t=0s');
  fireEvent.click(screen.getByRole('button',{name:'명시적 재승인 후 복구'}));expect(publicationApi.write).not.toHaveBeenCalled();
  await fill();fireEvent.click(screen.getByRole('button',{name:'명시적 재승인 후 복구'}));
  await waitFor(()=>expect(publicationApi.write).toHaveBeenCalledTimes(1));
  expect(publicationApi.write).toHaveBeenCalledWith(12,'restore',{expectedVersion:3,expectedContextHash:context.contextHash,evidenceRechecked:true,reason:'복구 사유',recheckNote:'현재 근거 재확인'},expect.any(String));
});
it('locks synchronous double clicks and retries an ambiguous result with exactly the same operation',async()=>{
  const pending=deferred<PublicationResult>();vi.mocked(publicationApi.write).mockReturnValueOnce(pending.promise);
  const p=props();render(<PublicationDialog {...p}/>);await fill();const button=screen.getByRole('button',{name:'명시적 재승인 후 복구'});
  fireEvent.click(button);fireEvent.click(button);expect(publicationApi.write).toHaveBeenCalledTimes(1);
  await act(async()=>pending.reject(new TypeError('connection lost')));
  expect(screen.getByLabelText('처리 사유')).toHaveValue('복구 사유');
  const original=vi.mocked(publicationApi.write).mock.calls[0];
  fireEvent.click(screen.getByRole('button',{name:'동일 요청 결과 확인'}));
  await waitFor(()=>expect(publicationApi.write).toHaveBeenCalledTimes(2));
  expect(vi.mocked(publicationApi.write).mock.calls[1]).toEqual(original);
  await waitFor(()=>expect(p.onCommitted).toHaveBeenCalledTimes(1));
});
it('keeps input on conflict and requires reconfirming the latest context',async()=>{
  vi.mocked(publicationApi.write).mockRejectedValueOnce(new ApiError(409,'공유 정보 변경',{code:'CONTEXT_CHANGED'}));
  render(<PublicationDialog {...props()}/>);await fill();fireEvent.click(screen.getByRole('button',{name:'명시적 재승인 후 복구'}));
  await screen.findByText('공유 정보 변경');expect(screen.getByLabelText('재확인 내용')).toHaveValue('현재 근거 재확인');
  const fresh={...context,contextHash:'b'.repeat(64)};vi.mocked(publicationApi.get).mockResolvedValue(fresh);
  fireEvent.click(screen.getByRole('button',{name:'최신 상태·이력 조회 · 입력 유지'}));await screen.findByText('확인 셰프 · 확인 지점');
  expect(screen.getByRole('checkbox')).not.toBeChecked();
  fireEvent.click(screen.getByRole('checkbox'));fireEvent.click(screen.getByRole('button',{name:'명시적 재승인 후 복구'}));
  await waitFor(()=>expect(publicationApi.write).toHaveBeenCalledTimes(2));
  expect(vi.mocked(publicationApi.write).mock.calls[1][2].expectedContextHash).toBe(fresh.contextHash);
});
it('invalidates related data before confirmation reads and performs GET-only recovery after success',async()=>{
  const p=props();p.onCommitted.mockImplementation(async()=>{throw new Error('public GET failed');});
  render(<PublicationDialog {...p}/>);await fill();
  vi.mocked(publicationApi.get).mockRejectedValueOnce(new Error('confirmation failed'));
  fireEvent.click(screen.getByRole('button',{name:'명시적 재승인 후 복구'}));await screen.findByText(/공개 제어 요청이 완료됐습니다/);
  await waitFor(()=>expect(p.onCommitted).toHaveBeenCalledTimes(1));
  expect(p.onCommitted.mock.invocationCallOrder[0]).toBeLessThan(vi.mocked(publicationApi.get).mock.invocationCallOrder[1]);
  fireEvent.click(screen.getByRole('button',{name:'최신 상태·이력 조회 · 입력 유지'}));await screen.findByText('확인 셰프 · 확인 지점');
  expect(publicationApi.write).toHaveBeenCalledTimes(1);expect(screen.queryByRole('button',{name:'명시적 재승인 후 복구'})).not.toBeInTheDocument();
});
it('preserves pending input across expiry and discards the old read generation',async()=>{
  const stale=deferred<PublicationContext>();vi.mocked(publicationApi.get).mockReturnValueOnce(stale.promise);
  const p=props();const view=render(<PublicationDialog {...p}/>);
  view.rerender(<PublicationDialog {...p} username={undefined} authGeneration={2}/>);
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  view.rerender(<PublicationDialog {...p} authGeneration={3}/>);await fill();
  await act(async()=>stale.resolve({...context,mention:{...context.mention,chefName:'이전 계정 응답'}}));
  expect(screen.queryByText(/이전 계정 응답/)).not.toBeInTheDocument();
  vi.mocked(publicationApi.write).mockRejectedValueOnce(new ApiError(401,'만료'));
  fireEvent.click(screen.getByRole('button',{name:'명시적 재승인 후 복구'}));await waitFor(()=>expect(p.onExpired).toHaveBeenCalled());
  view.rerender(<PublicationDialog {...p} username={undefined} authGeneration={4}/>);
  view.rerender(<PublicationDialog {...p} authGeneration={5}/>);await screen.findByText('확인 셰프 · 확인 지점');
  expect(screen.getByLabelText('처리 사유')).toHaveValue('복구 사유');expect(screen.getByRole('button',{name:'동일 요청 결과 확인'})).toBeInTheDocument();
});
it('uses the registered single leave guard to preserve dirty input and blocks leaving during uncertainty',async()=>{
  const p=props();render(<PublicationDialog {...p}/>);await fill();const next=vi.fn();
  act(()=>p.onGuard.mock.calls.at(-1)?.[0]?.(next));expect(screen.getByRole('alertdialog')).toBeInTheDocument();expect(next).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button',{name:'계속 작성'}));expect(screen.getByLabelText('처리 사유')).toHaveValue('복구 사유');
  vi.mocked(publicationApi.write).mockRejectedValueOnce(new TypeError('불명확'));
  fireEvent.click(screen.getByRole('button',{name:'명시적 재승인 후 복구'}));await screen.findByRole('button',{name:'동일 요청 결과 확인'});
  act(()=>p.onGuard.mock.calls.at(-1)?.[0]?.(next));expect(next).not.toHaveBeenCalled();expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
});
