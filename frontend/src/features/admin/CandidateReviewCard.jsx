import { useEffect, useState } from 'react';
import { api } from '../../api';

const dispositionLabels = { OPEN: '검토 대기', RESOLVED: '관계 승인됨', DISMISSED: '후보 제외' };
const matchLabels = { MATCHED: '장소 일치', REVIEW_REQUIRED: '장소 확인 필요', NOT_FOUND: '검색 결과 없음' };

export default function CandidateReviewCard({ candidate, chefs, refresh, setError }) {
  const [search, setSearch] = useState(null);
  const [query, setQuery] = useState(candidate.extractedName);
  const [placeId, setPlaceId] = useState('');
  const [chefId, setChefId] = useState('');
  const [mentionType, setMentionType] = useState('');
  const [timestamp, setTimestamp] = useState('');
  const [evidence, setEvidence] = useState('');
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    api(`/admin/candidates/${candidate.id}/places`).then(result => { if (active) setSearch(result); }).catch(error => { if (active) setError(error.message); });
    return () => { active = false; };
  }, [candidate.id]);
  async function action(fn) {
    setBusy(true); setError('');
    try { await fn(); } catch (error) {
      setError(error.message);
      try { await refresh(); } catch { /* Keep the original operation error visible. */ }
    } finally { setBusy(false); }
  }
  const canApprove = placeId && chefId && mentionType && evidence.trim() && note.trim()
    && (timestamp === '' || (/^\d+$/.test(timestamp) && Number(timestamp) <= 2147483647));
  return <article className="review-card">
    <div className="card-top"><span className="eyebrow">{dispositionLabels[candidate.disposition]}</span><a href={`https://www.youtube.com/watch?v=${candidate.videoId}`} target="_blank" rel="noreferrer">원본 영상 ↗</a></div>
    <h3>{candidate.extractedName}</h3><p>{candidate.extractedAddress || '설명란에 주소 없음 · 지점 확인 필요'}</p>
    <p className="notice">장소 매칭: {matchLabels[candidate.matchStatus] || '미실행'} · 관계 승인은 별도로 확인합니다.</p>
    {candidate.matchingErrorCode && <p className="error">최근 장소 검색에 실패했습니다. 이전 결과가 있다면 보존되며, 새 결과로 확정하지 않습니다.</p>}
    <details><summary>추출 근거 · {candidate.sourceLine ? `${candidate.sourceLine}행` : '수동 추가'}</summary><pre>{candidate.evidence}</pre><p>{candidate.videoTitle}</p></details>
    {candidate.disposition !== 'DISMISSED' ? <>
      {candidate.disposition === 'RESOLVED' && <p className="notice">이 후보에 승인된 관계가 있습니다. 다른 셰프의 관계는 근거를 따로 확인해 추가할 수 있습니다.</p>}
      <form className="inline-form" onSubmit={event => { event.preventDefault(); action(async () => {
        setPlaceId(''); setSearch(await api(`/admin/candidates/${candidate.id}/places`, { method: 'POST', body: { query } })); await refresh();
      }); }}><label>장소 검색어<input required maxLength={100} value={query} onChange={event => setQuery(event.target.value)}/></label><button disabled={busy}>Kakao 검색</button></form>
      {search && <fieldset><legend>장소 후보 {search.candidates.length}개</legend>
        {!!search.candidates.length && !search.complete && <p className="notice">전체 후보 확보 여부가 확인되지 않았습니다. 다른 지점을 함께 확인하세요.</p>}
        {!search.candidates.length && <p>저장된 장소 후보가 없습니다. 상호와 지역을 확인해 검색해 주세요.</p>}
        {search.candidates.map(place => <label className="place-option" key={place.id}><input type="radio" name={`place-${candidate.id}`} checked={placeId === place.id} onChange={() => setPlaceId(place.id)}/><span><strong>{place.name}</strong><span>{place.roadAddress || place.address}</span><small>ID {place.id} · {place.latitude}, {place.longitude}</small><a href={place.placeUrl} target="_blank" rel="noreferrer">카카오맵 확인 ↗</a></span></label>)}
      </fieldset>}
      <label>관계 셰프<select value={chefId} onChange={event => setChefId(event.target.value)}><option value="">영상에서 관계가 확인된 셰프 선택</option>{chefs.map(chef => <option key={chef.id} value={chef.id}>{chef.name}</option>)}</select></label>
      <label>언급 유형<select value={mentionType} onChange={event => setMentionType(event.target.value)}><option value="">근거에 맞는 유형을 선택하세요</option><option value="REGULAR">단골 · 명시적 확인</option><option value="RECOMMENDED">추천 · 의사 확인</option><option value="VISITED">방문 · 실제 방문 확인</option><option value="MENTIONED">단순 언급 · 해당 셰프의 언급 확인</option></select></label>
      <label>확인된 영상 시점 (초, 미확인 시 공란)<input type="number" min="0" max="2147483647" step="1" value={timestamp} onChange={event => setTimestamp(event.target.value)}/></label>
      <label>관계 확인 근거<textarea maxLength={5000} placeholder="해당 셰프가 이 식당을 어떻게 언급했는지 확인한 근거" value={evidence} onChange={event => setEvidence(event.target.value)}/></label>
      <label>검토 메모<textarea maxLength={2000} placeholder="선택 지점·주소 및 관계 검증 내용을 남겨 주세요." value={note} onChange={event => setNote(event.target.value)}/></label>
      <div className="actions"><button disabled={busy || !canApprove} onClick={() => action(async () => {
        await api(`/admin/candidates/${candidate.id}/mentions`, { method: 'POST', body: {
          chefId: Number(chefId), placeId, mentionType, timestampSeconds: timestamp === '' ? null : Number(timestamp), evidence, note
        } }); setChefId(''); setMentionType(''); setEvidence(''); setNote(''); setTimestamp(''); await refresh();
      })}>관계 승인하고 공개</button>
      {candidate.disposition === 'OPEN' && <button className="secondary" disabled={busy || !note.trim()} onClick={() => action(async () => {
        await api(`/admin/candidates/${candidate.id}/dismiss`, { method: 'POST', body: { note } }); await refresh();
      })}>후보 제외</button>}</div>
    </> : <p className="notice">{candidate.resolutionNote}</p>}
  </article>;
}
