import { useEffect, useRef, useState } from 'react';
import { ApiError, errorMessage } from '../../../api';
import { registrationApi as api } from './registrationApi';
import { changedValues, emptyFields, timestampText, values } from './registrationInput';
import type { Chef, Draft, Fields, Selection, Values, RegistrationEntry, RegistrationCommitted } from './types';
import { restaurantApi } from '../restaurants/restaurantApi';
import type { RestaurantPlace } from '../restaurants/types';

type Operation = { kind: 'create' | 'direct' | 'approve' | 'patch' | 'chef'; owner: string; execute: (active: () => boolean) => Promise<void> };
export function draftUrl(id: number | null) {
  const url = new URL(window.location.href);
  url.searchParams.delete('draftId');
  if (id !== null) url.searchParams.set('draftId', String(id));
  window.history.replaceState(null, '', url);
}
export function useRegistration(username: string | undefined, onExpired: () => void, onPublished: () => Promise<void>,
  entry: RegistrationEntry = { kind: 'new' }, onCommitted?: (event: RegistrationCommitted) => Promise<void>) {
  const [fields, setFields] = useState<Fields>(emptyFields);
  const [selection, setSelection] = useState<Selection | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [baseline, setBaseline] = useState<Values | null>(null);
  const [baselineFields, setBaselineFields] = useState('');
  const [chefs, setChefs] = useState<Chef[]>([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [pending, setPending] = useState<Operation | null>(null);
  const [latest, setLatest] = useState<Draft | null>(null);
  const [applied, setApplied] = useState<number | null>(null);
  const [fixedRestaurant, setFixedRestaurant] = useState<RestaurantPlace | null>(null);
  const [committed, setCommitted] = useState<RegistrationCommitted | null>(null);
  const [entryRetry, setEntryRetry] = useState(0);
  const owner = useRef(username);
  const lock = useRef(false);
  const generation = useRef(0);
  const reads = useRef(0);
  const initialResume = useRef(false);
  const draftRead = useRef(false);
  const readonly = draft?.status === 'APPLIED' || applied !== null;
  const dirty = !readonly && (draft ? JSON.stringify(fields) !== baselineFields || selection !== null
    : selection !== null || JSON.stringify(fields) !== JSON.stringify(emptyFields()));
  const unresolved = busy || pending !== null;
  function canLeave() {
    if (unresolved || lock.current || draftRead.current) { setError('처리 중이거나 결과가 불명확한 요청입니다. 먼저 결과를 확인하세요.'); return false; }
    return true;
  }
  function reset() {
    generation.current++; reads.current++; setFields(emptyFields()); setSelection(null); setDraft(null);
    setBaseline(null); setBaselineFields(''); setLatest(null); setApplied(null); setPending(null); setError(''); setNotice(''); draftUrl(null);
    setFixedRestaurant(null); setCommitted(null);
  }
  useEffect(() => {
    if (!username) { setSelection(null); return; }
    const differentOwner = !!owner.current && owner.current !== username;
    if (differentOwner) { reset(); setChefs([]); }
    owner.current = username;
    let current = true;
    void api.chefs().then(data => { if (current) setChefs(data); }).catch(e => { if (current) failure(e); });
    if (!differentOwner && draft && draft.status === 'DRAFT') void loadLatest();
    return () => { current = false; };
  }, [username]);
  useEffect(() => {
    if (!dirty && !unresolved) return;
    const before = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', before);
    return () => window.removeEventListener('beforeunload', before);
  }, [dirty, unresolved]);
  useEffect(() => () => { generation.current++; reads.current++; }, []);
  useEffect(() => {
    if (!username || entry.kind !== 'addRelation') return;
    const controller = new AbortController(), gen = generation.current;
    setBusy(true); setError('');
    void restaurantApi.detail(entry.restaurantId, 0, 20, controller.signal).then(result => {
      if (!controller.signal.aborted && gen === generation.current) setFixedRestaurant(result.restaurant);
    }).catch(e => { if (!controller.signal.aborted && gen === generation.current) failure(e); })
      .finally(() => { if (!controller.signal.aborted && gen === generation.current) setBusy(false); });
    return () => controller.abort();
  }, [username, entry.kind === 'addRelation' ? entry.restaurantId : null, entryRetry]);
  function failure(e: unknown) {
    let message = errorMessage(e);
    if (e instanceof ApiError) {
      const descriptions: Record<string, string> = {
        PLACE_SELECTION_EXPIRED: '장소 선택이 만료됐습니다. 관계 입력은 유지됩니다. 장소를 다시 선택하세요.',
        DUPLICATE_RELATION: `이미 등록된 관계입니다. 기존 관계 ID: ${e.existingMentionId ?? '확인 필요'}. 자동 수정·복구하지 않습니다.`,
        IDEMPOTENCY_CONFLICT: '다른 요청에 사용된 키입니다. 새 키로 우회하지 말고 작업 상태를 확인하세요.',
        STALE_VERSION: `다른 관리자가 수정했습니다. 서버 버전: ${e.currentVersion ?? '확인 필요'}. 최신본과 비교하세요.`,
        DRAFT_ALREADY_APPLIED: `이미 적용된 초안입니다. 적용 관계 ID: ${e.appliedMentionId ?? '확인 필요'}. 최신본을 조회하세요.`,
        DRAFT_NOT_EDITABLE: '수정할 수 없는 초안입니다. 최신본을 조회하세요.',
        DRAFT_INCOMPLETE: `필수 항목을 확인하세요: ${e.missingFields?.join(', ') ?? ''}`,
      };
      message = descriptions[e.code ?? ''] ?? message;
      if (e.code === 'PLACE_SELECTION_EXPIRED') setSelection(null);
      if (e.status === 401) { setSelection(null); message = '로그인이 만료됐습니다. 입력은 유지됩니다. 재로그인 후 장소를 다시 선택하세요.'; onExpired(); }
      if (e.code === 'IDEMPOTENCY_CONFLICT') message += ' 자동 재전송은 중단했습니다.';
    }
    setError(message);
  }
  async function acceptDraft(result: Draft, resolvedVideo?: Fields['video']) {
    const gen = generation.current;
    if (entry.kind === 'addRelation' && result.restaurant.id !== entry.restaurantId)
      throw new Error('선택 식당과 초안의 식당이 다릅니다. 입력을 확인하고 식당 상세에서 다시 시작하세요.');
    const video = resolvedVideo === undefined ? result.videoId ? (await api.videos()).find(v => v.id === result.videoId) ?? null : null : resolvedVideo;
    if (gen !== generation.current) return;
    if (result.videoId && !video) throw new Error('저장된 영상 표시 정보를 찾을 수 없습니다. 초안 ID로 다시 조회하세요.');
    const next: Fields = { chefId: result.chefId, chefSearch: '', video, videoUrl: video ? `https://www.youtube.com/watch?v=${video.id}` : '',
      mentionType: result.mentionType ?? '', timestamp: timestampText(result.timestampSeconds), note: result.verificationNote ?? '' };
    setDraft(result); setFields(next); setBaseline(values(next)); setBaselineFields(JSON.stringify(next));
    setSelection(null); setApplied(result.appliedMentionId); setLatest(null); draftUrl(result.id);
  }
  async function openDraft(id: number, skipGuard = false) {
    if (draftRead.current || (!skipGuard && !canLeave()) || lock.current) return;
    draftRead.current = true; const read = ++reads.current; const gen = generation.current;
    setBusy(true); setError('');
    try {
      const [result, videos] = await Promise.all([api.draft(id), api.videos()]);
      if (read !== reads.current || gen !== generation.current) return;
      await acceptDraft(result, videos.find(v => v.id === result.videoId) ?? null); setNotice('저장된 초안을 불러왔습니다.');
    } catch(e) { if (gen === generation.current) failure(e); }
    finally { draftRead.current = false; setBusy(false); }
  }
  useEffect(() => {
    if (!username || initialResume.current) return;
    let current = true;
    queueMicrotask(() => {
      if (!current) return;
      initialResume.current = true;
      const id = entry.kind === 'resumeDraft' ? String(entry.draftId) : entry.kind === 'new' ? new URL(window.location.href).searchParams.get('draftId') : null;
      if (id && /^[1-9]\d*$/.test(id) && Number.isSafeInteger(Number(id))) void openDraft(Number(id), true);
    });
    return () => { current = false; };
  }, [username]);
  async function refreshPublished() {
    try { await onPublished(); } catch { setNotice(n => n + ' 공개 목록 갱신은 실패했습니다. 승인 요청을 다시 보내지 말고 목록만 새로고침하세요.'); }
  }
  async function notifyCommitted(event: RegistrationCommitted) {
    setCommitted(event);
    try { await onCommitted?.(event); }
    catch { setNotice(n => n + ' 저장·승인은 완료됐지만 관련 조회 갱신에 실패했습니다. 상세에서 조회만 다시 시도하세요.'); }
  }
  async function execute(operation: Operation) {
    if (lock.current || !username || operation.owner !== username) return;
    lock.current = true; setBusy(true); setError(''); const gen = generation.current;
    try { await operation.execute(() => gen === generation.current); if (gen === generation.current) setPending(null); }
    catch(e) {
      if (gen !== generation.current) return;
      failure(e);
      if (!(e instanceof ApiError) || e.status >= 500 || e.code === 'IDEMPOTENCY_CONFLICT') {
        setPending(operation); setNotice('요청 결과가 불명확합니다. 입력과 요청 스냅샷을 고정했습니다. 아래에서 결과를 확인하세요.');
      } else setPending(null);
    } finally { lock.current = false; setBusy(false); }
  }
  function start(kind: Operation['kind'], action: Operation['execute']) {
    if (lock.current || pending || readonly || !username) return;
    void execute({ kind, owner: username, execute: action });
  }
  // The ref lock is acquired before the first await, preventing same-tick double submits.
  function save() {
    if (lock.current || pending || readonly) return;
    try {
      const next = values(fields); const currentVideo = fields.video;
      const unsaved = { chefSearch: fields.chefSearch, ...(!fields.video && fields.videoUrl ? { videoUrl: fields.videoUrl } : {}) };
      const preserveUnsaved = () => {
        if (unsaved.chefSearch || 'videoUrl' in unsaved) {
          setFields(f => ({ ...f, ...unsaved }));
          setNotice(n => n + ' 셰프 검색어·미확정 URL은 저장되지 않았습니다. 입력은 미저장 상태로 유지합니다.');
        }
      };
      if (draft) {
        const changes = changedValues(next, baseline!);
        const body = { ...changes, ...(selection ? { placeSelectionToken: selection.placeSelectionToken } : {}), expectedVersion: draft.version };
        if (Object.keys(body).length === 1) { setNotice('저장할 변경 사항이 없습니다. 미확정 검색어·URL은 저장 대상이 아닙니다.'); return; }
        const id = draft.id;
        const previousRestaurantId = draft.restaurant.id;
        start('patch', async active => { const result = await api.patch(id, body); if (!active()) return; await acceptDraft(result, currentVideo); setNotice('초안을 수정 저장했습니다.'); preserveUnsaved();
          await notifyCommitted({ kind: 'draftUpdated', restaurantId: result.restaurant.id, previousRestaurantId, draftId: result.id }); });
      } else {
        if (!selection && !fixedRestaurant) throw new Error('식당 장소를 먼저 선택하세요.');
        const body = fixedRestaurant ? { ...next, restaurantId: fixedRestaurant.id } : { ...next, placeSelectionToken: selection!.placeSelectionToken }; const key = crypto.randomUUID();
        start('create', async active => {
          const result = await api.createDraft(body, key); if (!active()) return; await acceptDraft(result, currentVideo); setNotice('초안을 저장했습니다.'); preserveUnsaved();
          await notifyCommitted({ kind: 'draftCreated', restaurantId: result.restaurant.id, draftId: result.id });
        });
      }
    } catch(e) { failure(e); }
  }
  function approve() {
    if (lock.current || pending || readonly) return;
    try {
      const next = values(fields);
      if (!next.chefId || !next.videoId || !next.mentionType) throw new Error('승인에는 셰프·확정 영상·언급 유형이 필요합니다.');
      if (draft && dirty) throw new Error('변경 내용을 먼저 수정 저장한 뒤 승인하세요.');
      if (!draft && entry.kind === 'addRelation') throw new Error('기존 식당의 관계 추가는 먼저 초안을 저장한 뒤 승인하세요.');
      const key = crypto.randomUUID();
      if (draft) {
        const id = draft.id, version = draft.version;
        start('approve', async active => { const result = await api.approve(id, version, key); if (!active()) return; setApplied(result.mentionId);
          setDraft(d => d ? { ...d, status: 'APPLIED', version: result.draftVersion, appliedMentionId: result.mentionId } : d);
          setNotice(`초안 승인 완료 · 관계 ID ${result.mentionId}`);
          await notifyCommitted({ kind: 'draftApproved', restaurantId: result.restaurantId, draftId: result.draftId, mentionId: result.mentionId }); await refreshPublished(); });
      } else {
        if (!selection) throw new Error('장소를 다시 선택하세요.');
        const body = { ...next, placeSelectionToken: selection.placeSelectionToken };
        start('direct', async active => { const result = await api.direct(body, key); if (!active()) return; setApplied(result.mentionId);
          setNotice(`직접 승인 완료 · 관계 ID ${result.mentionId}`);
          await notifyCommitted({ kind: 'directApproved', restaurantId: result.restaurantId, mentionId: result.mentionId }); await refreshPublished(); });
      }
    } catch(e) { failure(e); }
  }
  async function loadLatest() {
    if (!draft || lock.current) return;
    const gen = generation.current, id = draft.id, request = ++reads.current;
    try { const result = await api.draft(id); if (gen === generation.current && request === reads.current) setLatest(result); }
    catch(e) { if (gen === generation.current && request === reads.current) failure(e); }
  }
  async function useLatest(keepInput = false) {
    if (!latest || lock.current) return;
    const gen = generation.current;
    if (!keepInput && !window.confirm('입력을 서버 최신본으로 바꾸시겠습니까?')) return;
    try {
      if (keepInput && latest.status === 'DRAFT') {
        const video = latest.videoId ? (await api.videos()).find(v => v.id === latest.videoId) ?? null : null;
        if (gen !== generation.current) return;
        if (latest.videoId && !video) throw new Error('최신 영상 정보를 찾을 수 없습니다. 다시 조회하세요.');
        setBaseline({ chefId: latest.chefId, videoId: latest.videoId, mentionType: latest.mentionType,
          timestampSeconds: latest.timestampSeconds, verificationNote: latest.verificationNote }); setDraft(latest);
        setBaselineFields(JSON.stringify({ chefId: latest.chefId, chefSearch: '', video,
          videoUrl: video ? `https://www.youtube.com/watch?v=${video.id}` : '', mentionType: latest.mentionType ?? '',
          timestamp: timestampText(latest.timestampSeconds), note: latest.verificationNote ?? '' })); setLatest(null);
      } else await acceptDraft(latest);
      if (gen !== generation.current) return;
      setPending(null); setError(''); setNotice('최신본을 확인했습니다. 자동 저장·승인은 수행하지 않았습니다.');
    } catch(e) { if (gen === generation.current) failure(e); }
  }
  function createChef(name: string) {
    if (!name.trim() || name.length > 100) { setError('셰프 이름은 1~100자로 입력하세요.'); return; }
    start('chef', async active => { const chef = await api.createChef(name.trim()); if (!active()) return; setChefs(c => [...c, chef]);
      setFields(f => ({ ...f, chefId: chef.id, chefSearch: '' })); setNotice('셰프를 등록하고 선택했습니다. 양식 취소 시 셰프는 삭제되지 않습니다.'); });
  }
  async function refreshChefs() { const gen=generation.current; try { const result=await api.chefs(); if(gen!==generation.current) return; setChefs(result); setNotice('셰프 목록을 다시 조회했습니다. 생성 여부를 직접 확인하고 선택하세요.'); } catch(e) { if(gen===generation.current) failure(e); } }
  return { fields, setFields, selection, setSelection, draft, chefs, error, notice, busy, pending, latest, applied, dirty, readonly, fixedRestaurant, committed,
    retryEntry: () => setEntryRetry(n => n + 1),
    canLeave, reset, save, approve, openDraft, loadLatest, useLatest, createChef, refreshChefs, failure,
    retry: () => pending && !['patch','chef'].includes(pending.kind) ? execute(pending) : undefined,
    finishChefCheck: () => { if (pending?.kind === 'chef') { setPending(null); setError(''); } },
  };
}
