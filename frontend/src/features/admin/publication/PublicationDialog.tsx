import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { ApiError, errorMessage } from '../../../api';
import type { LeaveGuard } from '../AdminWorkspace';
import type { HistoryEvent } from '../corrections/types';
import type { Page } from '../restaurants/types';
import { sourceLink, sourceTime } from '../restaurants/RestaurantDetail';
import { typeLabels } from '../registration/RelationshipFields';
import { publicationApi } from './publicationApi';
import type { PublicationAction, PublicationBody, PublicationContext, PublicationResult } from './publicationApi';

interface Pending { action: PublicationAction; body: PublicationBody; key: string }
export default function PublicationDialog({ id, action, username, authGeneration, onGuard, onExpired, onClose, onCommitted }: {
  id: number; action: PublicationAction; username: string | undefined; authGeneration: number;
  onGuard: (guard: LeaveGuard | null) => void; onExpired: () => void; onClose: () => void;
  onCommitted: (result: PublicationResult) => Promise<void>;
}) {
  const [context, setContext] = useState<PublicationContext>();
  const [history, setHistory] = useState<Page<HistoryEvent>>();
  const [page, setPage] = useState(0), [refresh, setRefresh] = useState(0);
  const [reason, setReason] = useState(''), [note, setNote] = useState(''), [checked, setChecked] = useState(false);
  const [error, setError] = useState(''), [readError, setReadError] = useState('');
  const [busy, setBusy] = useState(false), [reading, setReading] = useState(false), [done, setDone] = useState(false);
  const [pending, setPending] = useState<Pending>();
  const [leaving, setLeaving] = useState<(() => void) | null>(null);
  const lock = useRef(false), sequence = useRef(0), epoch = useRef(authGeneration);
  const auth = useRef(username); auth.current=username; epoch.current=authGeneration;
  const mounted = useRef(true);
  const dialog = useRef<HTMLElement>(null);
  const dirty = !done && (!!reason || !!note || checked);
  function failure(e: unknown) { if(e instanceof ApiError && e.status===401)onExpired(); }
  function guard(next: () => void) {
    if(lock.current || pending) {setError('결과가 불명확한 요청을 같은 요청으로 먼저 확인하세요.');return;}
    if(dirty)setLeaving(()=>next);else next();
  }
  useEffect(()=>{onGuard(guard);return ()=>onGuard(null);});
  useEffect(()=>{
    if(!dirty && !pending && !busy)return;
    const before=(e: BeforeUnloadEvent)=>{e.preventDefault();e.returnValue='';};
    window.addEventListener('beforeunload',before);return ()=>window.removeEventListener('beforeunload',before);
  },[dirty,pending,busy]);
  useEffect(()=>{mounted.current=true;return ()=>{mounted.current=false;sequence.current++;};},[]);
  useEffect(()=>{
    if(!username)return;
    const previous=document.activeElement instanceof HTMLElement?document.activeElement:null;
    dialog.current?.focus();
    return ()=>{if(previous?.isConnected)previous.focus();};
  },[username]);
  useEffect(()=>{
    const request=++sequence.current, controller=new AbortController(), gen=authGeneration, owner=username;
    setContext(undefined);setHistory(undefined);setChecked(false);setReadError('');setReading(!!username);
    if(username)void Promise.allSettled([publicationApi.get(id,controller.signal),publicationApi.history(id,page,controller.signal)]).then(results=>{
      if(controller.signal.aborted || request!==sequence.current || epoch.current!==gen || auth.current!==owner)return;
      if(results[0].status==='fulfilled')setContext(results[0].value);
      else {failure(results[0].reason);setReadError('최신 확인 조회에 실패했습니다.');}
      if(results[1].status==='fulfilled')setHistory(results[1].value);
      else {failure(results[1].reason);setReadError(e=>e+' 이력 조회에 실패했습니다.');}
      setReading(false);
    });
    return ()=>{controller.abort();sequence.current++;};
  },[id,username,authGeneration,page,refresh]);
  async function write(operation: Pending) {
    if(lock.current || !username || done)return;
    lock.current=true;setBusy(true);setError('');
    const gen=authGeneration, owner=username;
    const current=()=>mounted.current && epoch.current===gen && auth.current===owner;
    try {
      let result: PublicationResult;
      try {result=await publicationApi.write(id,operation.action,operation.body,operation.key);}
      catch(e) {
        if(!current()) {if(mounted.current)setPending(operation);return;}
        failure(e);setError(errorMessage(e));
        if(!(e instanceof ApiError) || e.status>=500 || e.status===401)setPending(operation);
        else {setPending(undefined);setContext(undefined);setChecked(false);}
        return;
      }
      if(!current()) {if(mounted.current)setPending(operation);return;}
      sequence.current++;setPending(undefined);setDone(true);setContext(undefined);setHistory(undefined);setChecked(false);
      // Invalidate public memory immediately, before awaiting any confirmation/history reads.
      const related=onCommitted(result);
      setRefresh(n=>n+1);
      try {await related;}catch(e) {if(current()){failure(e);setReadError('처리는 완료됐지만 관련 조회에 실패했습니다. 닫기 후 관련 조회 다시 시도를 이용하세요.');}}
    } finally {lock.current=false;if(mounted.current)setBusy(false);}
  }
  function submit() {
    if(lock.current || pending || !context || reading || done)return;
    if(!reason.trim() || reason.trim().length>2000 || (action==='restore' && (!checked || !note.trim() || note.trim().length>2000))) {
      setError('사유와 복구 시 필수 재확인 항목을 입력하세요. 각 내용은 2,000자 이하여야 합니다.');return;
    }
    const body: PublicationBody={expectedVersion:context.mention.version,reason:reason.trim()};
    if(action==='restore')Object.assign(body,{expectedContextHash:context.contextHash,evidenceRechecked:true,recheckNote:note.trim()});
    void write({action,body,key:crypto.randomUUID()});
  }
  const eligible=context?.mention.verificationStatus==='APPROVED' && context.mention.publicationStatus===(action==='restore'?'SUSPENDED':'PUBLISHED');
  if(!username)return null;
  return createPortal(<div className="publication-backdrop"><section ref={dialog} tabIndex={-1} className="publication-dialog" role="dialog" aria-modal="true" aria-label="관계 공개 제어" onKeyDown={event=>{
    if(event.key==='Escape'){event.preventDefault();if(leaving)setLeaving(null);else guard(onClose);}
    if(event.key==='Tab') {
      const nodes=dialog.current?.querySelectorAll<HTMLElement>('button:not(:disabled),a[href],input:not(:disabled),textarea:not(:disabled),[tabindex="0"]');
      const focusable=nodes?Array.from(nodes).filter(node=>!node.closest('fieldset:disabled')):[];
      const first=focusable[0],last=focusable.at(-1);
      if(!first){event.preventDefault();return;}
      if(event.shiftKey && (document.activeElement===first || document.activeElement===dialog.current)){event.preventDefault();last?.focus();}
      else if(!event.shiftKey && (document.activeElement===last || document.activeElement===dialog.current)){event.preventDefault();first.focus();}
    }
  }}>
    <button onClick={()=>guard(onClose)}>닫기</button><h2>관계 #{id} 공개 {action==='restore'?'복구 · 재승인':'중단'}</h2>
      {done&&<p role="status">공개 제어 요청이 완료됐습니다. 아래 최신 상태와 이력을 확인하세요.</p>}
      {error&&<p role="alert">{error}</p>}{readError&&<p role="alert">{readError}</p>}
      <button disabled={busy || !!pending} onClick={()=>setRefresh(n=>n+1)}>최신 상태·이력 조회 · 입력 유지</button>
      {reading&&<p role="status">현재 관계를 확인하는 중…</p>}
      {context&&<><p>현재 {context.mention.verificationStatus} / {context.mention.publicationStatus} · 버전 {context.mention.version}</p>
        <h3>{context.mention.chefName} · {context.mention.restaurantName}</h3>
        <p>{context.references.restaurant.roadAddress??context.references.restaurant.address} · Kakao {context.references.restaurant.kakaoPlaceId}</p>
        <a href={context.references.restaurant.placeUrl} target="_blank" rel="noreferrer">지점 확인</a>
        <p>{context.mention.videoTitle} · {context.mention.channelTitle}</p>
        <a href={sourceLink(context.mention.videoId,context.mention.timestampSeconds)} target="_blank" rel="noreferrer">출처 영상 확인</a>
        <p>{context.mention.mentionType===null?'유형 미확인':typeLabels[context.mention.mentionType]} · {sourceTime(context.mention.timestampSeconds)}</p>
        <p>원본 근거: {context.mention.evidence??'없음'} · 원본 후보 ID: {context.mention.candidateId??'없음'}</p>
        <p>기존 검증 메모: {context.mention.verificationNote??'없음'}</p>
        <p>최근 중단 사유: {context.lastSuspendReason??'중단 사유 기록 없음'}</p>
      </>}
      {!done&&<><fieldset disabled={busy || !!pending}>
        <label>처리 사유<textarea value={reason} maxLength={2000} onChange={e=>setReason(e.target.value)}/></label>
        {action==='restore'&&<><label><input type="checkbox" checked={checked} disabled={!context || reading} onChange={e=>setChecked(e.target.checked)}/>현재 셰프·지점·영상·유형·시점의 근거를 다시 확인했습니다.</label>
          <label>재확인 내용<textarea value={note} maxLength={2000} onChange={e=>setNote(e.target.value)}/></label></>}
      </fieldset><button disabled={busy || !!pending || !eligible || reading || !username} onClick={submit}>{action==='restore'?'명시적 재승인 후 복구':'이 관계만 공개 중단'}</button></>}
      {pending&&<><p>결과가 불명확하거나 요청 확인이 필요합니다. 기존 키·경로·본문으로 확인합니다.</p><button disabled={busy} onClick={()=>void write(pending)}>동일 요청 결과 확인</button></>}
      {history&&<section aria-label="공개 제어 관계 이력"><h3>관계 변경 이력</h3>{history.items.map(event=><article key={event.id}><p>{event.action} · {event.occurredAt}</p><p>{event.reason??'사유 없음'}</p><details><summary>처리 당시 기록</summary><pre>{JSON.stringify({before:event.beforeValue,after:event.afterValue},null,2)}</pre></details></article>)}
        <button disabled={busy || !!pending || page===0} onClick={()=>setPage(n=>n-1)}>이전 이력</button><button disabled={busy || !!pending || !history.hasNext} onClick={()=>setPage(n=>n+1)}>다음 이력</button></section>}
    {leaving&&<section role="alertdialog" aria-label="공개 제어 입력 이탈 확인"><p>저장하지 않은 입력을 버리고 이동할까요?</p><button onClick={()=>setLeaving(null)}>계속 작성</button><button onClick={()=>{const next=leaving;setLeaving(null);next();}}>입력 버리고 이동</button></section>}
  </section></div>,document.body);
}
