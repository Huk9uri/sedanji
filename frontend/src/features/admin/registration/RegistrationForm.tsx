import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { useRegistration } from './useRegistration';
import PlaceSearchStep from './PlaceSearchStep';
import RelationshipFields, { typeLabels } from './RelationshipFields';
import DraftResumePicker from './DraftResumePicker';
import './registration.css';
import type { RegistrationEntry, RegistrationCommitted } from './types';
export default function RegistrationForm({ username, onExpired, onPublished, onClose, onGuard, entry, onCommitted, onDetail }: {
  username: string | undefined; onExpired: () => void; onPublished: () => Promise<void>;
  onClose: () => void; onGuard: (guard: ((proceed: () => void) => void) | null) => void;
  entry?: RegistrationEntry; onCommitted?: (event: RegistrationCommitted) => Promise<void>; onDetail?: (id: number) => void;
}) {
  const state = useRegistration(username, onExpired, onPublished, entry, onCommitted);
  const [changingPlace, setChangingPlace] = useState(false);
  const [review, setReview] = useState(false);
  const [metadataBusy, setMetadataBusy] = useState(false);
  const [leaving, setLeaving] = useState<(() => void) | null>(null);
  const blocked = state.busy || !!state.pending || metadataBusy;
  const place = state.selection?.place ?? state.draft?.restaurant ?? state.fixedRestaurant;
  const fixed = entry?.kind === 'addRelation';
  const selected = place && !changingPlace;
  function requestLeave(proceed: () => void) {
    if (metadataBusy || !state.canLeave()) return;
    if (state.dirty) setLeaving(() => proceed); else proceed();
  }
  useEffect(() => { onGuard(requestLeave); return () => onGuard(null); });
  useEffect(() => { setReview(false); }, [state.fields, state.selection, state.draft]);
  function close() { requestLeave(() => { state.reset(); onClose(); }); }
  return <section className="registration" aria-label="식당 직접 등록">
    <div className="section-heading"><div><span className="eyebrow">RESTAURANT REGISTRATION</span><h2>식당 등록</h2></div><button className="secondary" onClick={close}>양식 닫기</button></div>
    <ol className="registration-progress"><li className={!selected ? 'current' : ''}>1 장소 선택</li><li className={selected ? 'current' : ''}>2 관계 입력</li></ol>
    {state.error && <p role="alert" className="error">{state.error}</p>}
    {state.notice && <p role="status" className="notice">{state.notice}</p>}
    {state.committed && onDetail && <button className="secondary" disabled={blocked} onClick={() => requestLeave(() => onDetail(state.committed!.restaurantId))}>저장한 식당 상세로 이동</button>}
    {!fixed && <DraftResumePicker key={`resume-${username}`} disabled={blocked} onOpen={id => requestLeave(() => { setChangingPlace(false); void state.openDraft(id, true); })} onFailure={state.failure}/>}
    {fixed && !place && <><p>선택 식당 정보를 확인하고 있습니다.</p><button disabled={state.busy} onClick={state.retryEntry}>선택 식당 다시 조회</button></>}
    {!fixed && !selected && !state.readonly && <PlaceSearchStep key={`place-${username}`} disabled={blocked} onFailure={state.failure} onStop={close}
      onSelect={s => { state.setSelection(s); setChangingPlace(false); }}/>}
    {place && <><article className="registration-selected"><div><small>선택한 식당{state.draft ? ` · 초안 #${state.draft.id} · 버전 ${state.draft.version}` : ''}</small><h3>{place.name}</h3>
      <p>{place.roadAddress || place.address || '주소 정보 없음'}</p><p>Kakao Place ID {'kakaoPlaceId' in place ? place.kakaoPlaceId : place.id}</p></div>
      {!fixed && !state.readonly && <button disabled={blocked} className="secondary" onClick={() => setChangingPlace(v => !v)}>{changingPlace ? '기존 장소 유지' : '장소 다시 선택'}</button>}</article>
      {selected && <RelationshipFields key={`fields-${username}`} fields={state.fields} onChange={state.setFields} chefs={state.chefs}
        onChef={state.createChef} onFailure={state.failure} onBusy={setMetadataBusy} disabled={blocked || state.readonly}/>}
      {!state.readonly && <><p className="field-help">임시 저장은 장소만 선택해도 가능합니다. 승인에는 셰프·영상·관계 유형과 근거 확인이 필요합니다.</p>
        <div className="registration-actions"><button className="secondary" disabled={blocked || !selected} onClick={state.save}>{state.draft ? '수정 저장' : '임시 저장'}</button>
          <button disabled={blocked || !selected || (fixed && !state.draft) || !state.fields.chefId || !state.fields.video || !state.fields.mentionType || (!!state.draft && state.dirty)} onClick={() => setReview(true)}>승인 전 검토</button></div>
        {fixed && !state.draft && <p className="notice">선택 식당을 고정했습니다. 관계를 추가하려면 먼저 임시 저장 후 초안을 승인하세요.</p>}
        {state.draft && state.dirty && <p className="notice">저장되지 않은 변경이 있습니다. 먼저 수정 저장하세요.</p>}
        {review && <section className="registration-review" aria-label="승인 전 검토"><h3>출처와 관계를 확인하세요</h3>
          <p>{place.name} · {state.chefs.find(c => c.id === state.fields.chefId)?.name} · {state.fields.video?.title} · {state.fields.mentionType && typeLabels[state.fields.mentionType]}</p>
          <p>영상의 실제 근거와 지점·관계 유형을 확인했을 때 승인하세요.</p>
          <button disabled={blocked} onClick={state.approve}>{state.draft ? '저장된 초안 승인·공개' : '직접 승인·공개'}</button></section>}
      </>}
      {state.readonly && <p className="notice">적용 완료 · 관계 ID {state.applied ?? state.draft?.appliedMentionId} · 읽기 전용입니다.</p>}
    </>}
    {state.pending && <section className="registration-review"><h3>요청 결과 확인</h3>
      {state.pending.kind === 'patch' ? <p>PATCH는 자동 재전송하지 않습니다. 서버 최신본과 입력을 비교하세요.</p>
        : state.pending.kind === 'chef' ? <><p>셰프 생성은 자동 재전송하지 않습니다. 목록을 조회해 생성 여부를 확인하세요.</p><button disabled={state.busy} onClick={() => void state.refreshChefs()}>셰프 목록 다시 조회</button>
          <ul>{state.chefs.map(c => <li key={c.id}>{c.name} · ID {c.id}</li>)}</ul><button disabled={state.busy} onClick={state.finishChefCheck}>목록 확인 완료 · 계속 작성</button></>
        : <button disabled={state.busy || !username} onClick={() => void state.retry()}>동일 키·동일 요청으로 결과 재확인</button>}
    </section>}
    {state.draft && <button className="secondary" disabled={state.busy} onClick={() => void state.loadLatest()}>서버 최신 초안 조회·비교</button>}
    {state.latest && <section className="registration-review"><h3>서버 최신본 (현재 입력은 유지 중)</h3><p>버전 {state.latest.version} · {state.latest.status}</p>
      <dl><dt>식당</dt><dd>{state.latest.restaurant.name} · {state.latest.restaurant.roadAddress || state.latest.restaurant.address}</dd><dt>셰프 ID</dt><dd>{state.latest.chefId ?? '미선택'}</dd><dt>영상 ID</dt><dd>{state.latest.videoId ?? '미선택'}</dd><dt>유형</dt><dd>{state.latest.mentionType ?? '미선택'}</dd>
        <dt>시점</dt><dd>{state.latest.timestampSeconds === null ? '미확인' : `${state.latest.timestampSeconds}초`}</dd><dt>메모</dt><dd>{state.latest.verificationNote ?? '없음'}</dd></dl>
      <button onClick={() => void state.useLatest()}>서버 최신본으로 전환</button>
      {state.latest.status === 'DRAFT' && state.latest.restaurant.id === state.draft?.restaurant.id && <button className="secondary" onClick={() => void state.useLatest(true)}>최신 버전 확인 · 내 입력 유지</button>}
    </section>}
    {leaving && createPortal(<div className="registration-dialog-backdrop"><section className="registration-review" role="dialog" aria-modal="true" aria-labelledby="leave-title" onKeyDown={event => {
      if (event.key === 'Escape') setLeaving(null);
      if (event.key === 'Tab') {
        const buttons=event.currentTarget.querySelectorAll<HTMLButtonElement>('button');
        if (event.shiftKey && document.activeElement === buttons[0]) { event.preventDefault(); buttons[buttons.length-1].focus(); }
        else if (!event.shiftKey && document.activeElement === buttons[buttons.length-1]) { event.preventDefault(); buttons[0].focus(); }
      }
    }}>
      <h3 id="leave-title">저장하지 않은 변경이 있습니다</h3><p>저장하지 않고 나가면 현재 입력은 사라집니다. 이미 저장된 초안·셰프·영상은 유지됩니다.</p>
      <button autoFocus className="secondary" onClick={() => setLeaving(null)}>계속 작성</button>
      <button onClick={() => { const proceed=leaving; setLeaving(null); proceed(); }}>저장하지 않고 나가기</button>
    </section></div>, document.body)}
  </section>;
}
