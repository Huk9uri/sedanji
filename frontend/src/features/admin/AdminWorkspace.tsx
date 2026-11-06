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
  const [publicError, setPublicError] = useState(false);
  const [publicLoading, setPublicLoading] = useState(false);
  const [lastCommit, setLastCommit] = useState<RegistrationCommitted | null>(null);
  const guard = useRef<LeaveGuard | null>(null);
  const listScroll = useRef(0);
  const owner = useRef(username);
  const epoch = useRef(authGeneration); epoch.current = authGeneration;
  const auth = useRef(username); auth.current = username;
  const publicSequence = useRef(0);
  const registerGuard = useCallback((next: LeaveGuard | null) => { guard.current=next; onGuard(next); }, [onGuard]);
  function navigate(next: () => void) { if (guard.current) guard.current(next); else next(); }
  const failure = useCallback((e: unknown) => { if (auth.current && e instanceof ApiError && e.status === 401) onExpired(); }, [onExpired]);
  const list = useRestaurantList(conditions, setConditions, !!username && view === 'list', authGeneration, revision, failure);
  const detail = useRestaurantDetail(selected, mentionPage, draftPage, !!username && view === 'detail' && selected !== null, authGeneration, revision + detailRevision, failure);
  useEffect(() => {
    publicSequence.current++; setPublicError(false); setPublicLoading(false);
    if (!username) return;
    if (owner.current && owner.current !== username) {
      setInput(''); setConditions(initialConditions()); setSelected(null); setMentionPage(0); setDraftPage(0);
      setView('list'); setEntry({ kind:'new' }); setEntryKey(n=>n+1); setLastCommit(null); setVisitedCandidates(false); draftUrl(null); listScroll.current=0;
    }
    owner.current=username;
  }, [username, authGeneration]);
  useEffect(() => {
    auth.current=username; epoch.current=authGeneration;
    return () => { auth.current=undefined; epoch.current++; publicSequence.current++; };
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
    if (!auth.current || epoch.current !== authGeneration) return;
    setLastCommit(event);
    // Visible reads belong to hooks; hidden records, including both sides of a
    // restaurant move, are read afresh on entry instead of confirmation GETs.
    setRevision(n=>n+1); setMentionPage(0); setDraftPage(0);
  }
  async function refreshPublic() {
    if (!auth.current || epoch.current !== authGeneration) return;
    const gen=epoch.current, request=++publicSequence.current;
    setPublicError(false); setPublicLoading(true);
    try { await onPublished(); }
    catch (e) {
      if (auth.current && gen===epoch.current && request===publicSequence.current) {
        setPublicError(true); failure(e); throw e;
      }
    } finally {
      if (auth.current && gen===epoch.current && request===publicSequence.current) setPublicLoading(false);
    }
  }
  async function candidateChanged() {
    if (!auth.current || epoch.current !== authGeneration) return;
    setRevision(n=>n+1); setMentionPage(0); setDraftPage(0);
    // Candidate writes have already succeeded; read failures cannot undo them.
    try { await refreshPublic(); } catch { /* The read error is displayed below. */ }
  }
  const relatedError = !!username && lastCommit !== null && (view === 'list' ? list.error !== undefined :
    view === 'detail' && (detail.detail.error !== undefined || detail.drafts.error !== undefined));
  function retryRelated() {
    if (!auth.current) return;
    if (view === 'list' && list.error !== undefined) list.refresh();
    if (view === 'detail') {
      if (detail.detail.error !== undefined) detail.detail.refresh();
      if (detail.drafts.error !== undefined) detail.drafts.refresh();
    }
    if (publicError && !publicLoading) void refreshPublic().catch(()=>{});
  }
  const active=!!username;
  return <div className="admin-workspace"><aside className="admin-sidebar"><strong>세단지 <small>ADMIN</small></strong><nav aria-label="관리자 업무 메뉴">
    <button disabled title="준비 중">제보 관리 · 준비 중</button><button className="selected" onClick={openList}>식당 관리</button>
  </nav></aside><div className="admin-workspace-content">
    {active && lastCommit && <p role="status">작업 완료 · 식당 #{lastCommit.restaurantId}{lastCommit.previousRestaurantId !== undefined ? ` · 이전 식당 #${lastCommit.previousRestaurantId}` : ''}{lastCommit.draftId !== undefined ? ` · 초안 #${lastCommit.draftId}` : ''}{lastCommit.mentionId !== undefined ? ` · 관계 #${lastCommit.mentionId}` : ''}</p>}
    {(relatedError || (active && publicError)) && <p role="alert" className="error">{lastCommit ? '저장·승인은 완료됐지만 ' : ''}{publicError ? '공개 목록 또는 관련' : '관련'} 조회 갱신에 실패했습니다. 조회만 다시 시도하세요. <button disabled={!active || publicLoading} onClick={retryRelated}>관련 조회 다시 시도</button></p>}
    {view === 'list' && <RestaurantList input={input} onInput={setInput} conditions={conditions} onConditions={next=>{listScroll.current=0;setConditions(next);}}
      {...list} onRetry={list.refresh} onOpen={id=>{listScroll.current=window.scrollY;openDetail(id);}}
      onNew={()=>openRegistration({kind:'new'})} onCandidates={()=>navigate(()=>{listScroll.current=window.scrollY;setVisitedCandidates(true);setView('candidates');})}/>}
    {view === 'detail' && <RestaurantDetail data={detail.detail.data} loading={detail.detail.loading} error={detail.detail.error}
      drafts={detail.drafts.data} draftsLoading={detail.drafts.loading} draftsError={detail.drafts.error}
      onRetry={detail.detail.refresh} onDraftRetry={detail.drafts.refresh} onBack={openList}
      onAdd={id=>openRegistration({kind:'addRelation',restaurantId:id})} onResume={id=>openRegistration({kind:'resumeDraft',draftId:id})}
      onMentionPage={setMentionPage} onDraftPage={setDraftPage}/>}
    {view === 'registration' && <RegistrationForm key={entryKey} username={username} entry={entry} onExpired={onExpired}
      onPublished={refreshPublic} onCommitted={refreshRelated} onDetail={id=>{draftUrl(null);openDetail(id);}}
      onClose={()=>setView(backTo)} onGuard={registerGuard}/>}
    {visitedCandidates && <div hidden={view !== 'candidates'}><button className="secondary" onClick={openList}>← 식당 목록으로</button>
      {active && <LegacyCandidateReview key={authGeneration} onPublished={candidateChanged} onFailure={e=>{if(epoch.current===authGeneration)failure(e);}}/>}</div>}
    {!active && view !== 'registration' && <p>관리자 로그인이 필요합니다.</p>}
  </div></div>;
}
