import { useEffect, useRef, useState } from 'react';
import { registrationApi } from './registrationApi';
import type { PlaceSearch, Selection } from './types';
export default function PlaceSearchStep({ onSelect, onFailure, onStop, disabled }: {
  onSelect: (selection: Selection) => void; onFailure: (error: unknown) => void; onStop: () => void; disabled: boolean;
}) {
  const [query, setQuery] = useState('');
  const [result, setResult] = useState<PlaceSearch | null>(null);
  const [busy, setBusy] = useState(false);
  const noResults = result !== null && result.candidates.length === 0;
  const sequence = useRef(0);
  useEffect(() => () => { sequence.current++; }, []);
  async function search() {
    if (noResults || disabled) return;
    const request = ++sequence.current; setBusy(true); setResult(null);
    try { const data = await registrationApi.places(query.trim()); if (request === sequence.current) setResult(data); }
    catch(e) { if (request === sequence.current) onFailure(e); }
    finally { if (request === sequence.current) setBusy(false); }
  }
  return <section className="registration-step"><h3>1. 장소 검색·선택</h3>
    <p>상호뿐 아니라 주소와 실제 지점을 확인하세요. 장소 선택만으로 자료를 저장하지 않습니다.</p>
    <form onSubmit={e => { e.preventDefault(); void search(); }}><label>식당 이름 검색<input value={query} maxLength={100} required disabled={noResults || disabled}
      onChange={e => { sequence.current++; setBusy(false); setQuery(e.target.value); setResult(null); }}/></label>
      <button disabled={disabled || busy || noResults || !query.trim()}>장소 검색</button></form>
    {busy && <p role="status">장소 검색 중…</p>}
    {result && <><p>Kakao Local 검색 결과 · 전체 {result.totalCount}개</p>
      {!result.complete && <p className="notice">검색 결과가 일부만 표시됩니다. 지점이 확실한 경우에만 선택하세요.</p>}
      {!result.candidates.length && <p className="notice">검색 결과가 없습니다. 이번 직접 등록을 중단하세요. 식당이나 보류 자료는 만들지 않습니다.</p>}
      {result.candidates.map(s => <article className="registration-place" key={s.placeSelectionToken}>
        <div><h4>{s.place.name}</h4><p>{s.place.roadAddress ?? '도로명 주소 없음'}</p><p>{s.place.address ?? '지번 주소 없음'}</p><small>Kakao Place ID {s.place.id}</small></div>
        <a href={s.place.placeUrl} target="_blank" rel="noreferrer">카카오맵 확인 ↗</a>
        <button disabled={disabled} onClick={() => { sequence.current++; onSelect(s); }}>이 지점 선택</button>
      </article>)}
      <button className="secondary" onClick={onStop} disabled={disabled}>지점 확정 불가 · 등록 중단</button>
    </>}
  </section>;
}
