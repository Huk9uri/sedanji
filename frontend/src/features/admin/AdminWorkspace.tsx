import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api';
import RegistrationForm from './registration/RegistrationForm';
import { draftUrl } from './registration/useRegistration';
import type { RegistrationCommitted, RegistrationEntry } from './registration/types';
import RestaurantList from './restaurants/RestaurantList';
import RestaurantDetail from './restaurants/RestaurantDetail';
import { initialConditions } from './restaurants/types';
import { useRestaurantList } from './restaurants/useRestaurantList';
import { useRestaurantDetail } from './restaurants/useRestaurantDetail';
import { restaurantApi } from './restaurants/restaurantApi';
import LegacyCandidateReview from './LegacyCandidateReview';
import './admin-workspace.css';
export type LeaveGuard = (proceed: () => void) => void;
type View = 'list' | 'detail' | 'registration' | 'candidates';
export default function AdminWorkspace({ username, authGeneration, onExpired, onPublished, onGuard }: {
  username: string | undefined; authGeneration: number; onExpired: () => void;
  onPublished: () => Promise<void>; onGuard: (guard: LeaveGuard | null) => void;
}) {
  const [view, setView] = useState<View>(() => new URLSearchParams(window.location.search).has('draftId') ? 'registration' : 'list');
  const [input, setInput] = useState('');
  const [conditions, setConditions] = useState(initialConditions);
  const [selected, setSelected] = useState<number | null>(null);
  const [mentionPage, setMentionPage] = useState(0);
  const [draftPage, setDraftPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [detailRevision, setDetailRevision] = useState(0);
  const [entry, setEntry] = useState<RegistrationEntry>({ kind: 'new' });
  const [entryKey, setEntryKey] = useState(0);
  const [visitedCandidates, setVisitedCandidates] = useState(false);
  const [backTo, setBackTo] = useState<'list' | 'detail'>('list');
  const [refreshError, setRefreshError] = useState('');
  const [lastCommit, setLastCommit] = useState<RegistrationCommitted | null>(null);
  const guard = useRef<LeaveGuard | null>(null);
  const listScroll = useRef(0);
  const owner = useRef(username);
  const epoch = useRef(authGeneration); epoch.current = authGeneration;
  const auth = useRef(username); auth.current = username;
  const refreshController = useRef<AbortController | null>(null);
  const registerGuard = useCallback((next: LeaveGuard | null) => { guard.current=next; onGuard(next); }, [onGuard]);
  function navigate(next: () => void) { if (guard.current) guard.current(next); else next(); }
  const failure = useCallback((e: unknown) => { if (auth.current && e instanceof ApiError && e.status === 401) onExpired(); }, [onExpired]);
  const list = useRestaurantList(conditions, setConditions, !!username, authGeneration, revision, failure);
  const detail = useRestaurantDetail(selected, mentionPage, draftPage, !!username && selected !== null, authGeneration, revision + detailRevision, failure);
  useEffect(() => {
    refreshController.current?.abort();
    if (!username) { setRefreshError(''); return; }
    if (owner.current && owner.current !== username) {
      setInput(''); setConditions(initialConditions()); setSelected(null); setMentionPage(0); setDraftPage(0);
      setView('list'); setEntry({ kind:'new' }); setEntryKey(n=>n+1); setLastCommit(null); setVisitedCandidates(false); draftUrl(null); listScroll.current=0;
    }
    owner.current=username;
  }, [username, authGeneration]);
  useEffect(() => {
    auth.current=username; epoch.current=authGeneration;
    return () => { auth.current=undefined; epoch.current++; refreshController.current?.abort(); };
  }, []);
  useEffect(() => {
    if (view !== 'list' || !list.data) return;
    const frame=requestAnimationFrame(()=>window.scrollTo(0,listScroll.current));
    return ()=>cancelAnimationFrame(frame);
  }, [view, !!list.data]);
  function openDetail(id: number) {
    setSelected(id); setMentionPage(0); setDraftPage(0); setDetailRevision(n=>n+1); setView('detail');
  }
  function openRegistration(next: RegistrationEntry) {
    navigate(() => { setBackTo(view === 'detail' ? 'detail' : 'list'); setEntry(next); setEntryKey(n=>n+1); setView('registration'); });
  }
  function openList() { navigate(() => { setView('list'); draftUrl(null); }); }
  async function refreshRelated(event: RegistrationCommitted) {
    const gen=epoch.current;
    if (!auth.current) return;
    setLastCommit(event); setRefreshError(''); setRevision(n=>n+1); setMentionPage(0); setDraftPage(0);
    refreshController.current?.abort();
    const controller=new AbortController(); refreshController.current=controller;
    const ids=[...new Set([event.restaurantId,event.previousRestaurantId].filter((id): id is number=>id !== undefined))];
    // Refresh affected records even when a different detail or the form is visible.
    const results=await Promise.allSettled([restaurantApi.list(conditions,controller.signal), ...ids.flatMap(id=>[
      restaurantApi.detail(id,0,20,controller.signal),restaurantApi.drafts(id,0,20,controller.signal),
    ])]);
    if (controller.signal.aborted || gen!==epoch.current) return;
    const rejected=results.filter((result): result is PromiseRejectedResult=>result.status==='rejected');
    if(rejected.length) { rejected.forEach(result=>failure(result.reason)); setRefreshError('저장·승인은 완료됐지만 관련 조회 갱신에 실패했습니다. 조회만 다시 시도하세요.'); }
  }
  async function candidateChanged() { if (!auth.current || epoch.current !== authGeneration) return; setRevision(n=>n+1); setMentionPage(0); setDraftPage(0); await onPublished(); }
  const active=!!username;
  return <div className="admin-workspace"><aside className="admin-sidebar"><strong>세단지 <small>ADMIN</small></strong><nav aria-label="관리자 업무 메뉴">
    <button disabled title="준비 중">제보 관리 · 준비 중</button><button className="selected" onClick={openList}>식당 관리</button>
  </nav></aside><div className="admin-workspace-content">
    {refreshError && <p role="alert" className="error">{refreshError} <button disabled={!active} onClick={()=>{ if(lastCommit) void refreshRelated(lastCommit); }}>관련 조회 다시 시도</button></p>}
    {view === 'list' && <RestaurantList input={input} onInput={setInput} conditions={conditions} onConditions={next=>{listScroll.current=0;setConditions(next);}}
      {...list} onRetry={list.refresh} onOpen={id=>{listScroll.current=window.scrollY;openDetail(id);}}
      onNew={()=>openRegistration({kind:'new'})} onCandidates={()=>navigate(()=>{listScroll.current=window.scrollY;setVisitedCandidates(true);setView('candidates');})}/>}
    {view === 'detail' && <RestaurantDetail data={detail.detail.data} loading={detail.detail.loading} error={detail.detail.error}
      drafts={detail.drafts.data} draftsLoading={detail.drafts.loading} draftsError={detail.drafts.error}
      onRetry={detail.detail.refresh} onDraftRetry={detail.drafts.refresh} onBack={openList}
      onAdd={id=>openRegistration({kind:'addRelation',restaurantId:id})} onResume={id=>openRegistration({kind:'resumeDraft',draftId:id})}
      onMentionPage={setMentionPage} onDraftPage={setDraftPage}/>}
    {view === 'registration' && <RegistrationForm key={entryKey} username={username} entry={entry} onExpired={onExpired}
      onPublished={onPublished} onCommitted={refreshRelated} onDetail={id=>{draftUrl(null);openDetail(id);}}
      onClose={()=>setView(backTo)} onGuard={registerGuard}/>}
    {visitedCandidates && <div hidden={view !== 'candidates'}><button className="secondary" onClick={openList}>← 식당 목록으로</button>
      {active && <LegacyCandidateReview key={authGeneration} onPublished={candidateChanged} onFailure={e=>{if(epoch.current===authGeneration)failure(e);}}/>}</div>}
    {!active && view !== 'registration' && <p>관리자 로그인이 필요합니다.</p>}
  </div></div>;
}
