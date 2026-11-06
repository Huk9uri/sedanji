import { useEffect, useRef, useState } from 'react';
import { registrationApi as api } from './registrationApi';
import type { Draft, Page, RestaurantChoice } from './types';
export default function DraftResumePicker({ onOpen, onFailure, disabled }: {
  onOpen: (id: number) => void; onFailure: (e: unknown) => void; disabled: boolean;
}) {
  const [query, setQuery] = useState('');
  const [restaurants, setRestaurants] = useState<Page<RestaurantChoice> | null>(null);
  const [selected, setSelected] = useState<RestaurantChoice | null>(null);
  const [drafts, setDrafts] = useState<Page<Draft> | null>(null);
  const [busy, setBusy] = useState(false);
  const sequence = useRef(0);
  useEffect(() => () => { sequence.current++; }, []);
  async function search(page = 0) {
    const request = ++sequence.current; setBusy(true); setSelected(null); setDrafts(null);
    try { const result = await api.restaurants(query, page); if (request === sequence.current) setRestaurants(result); }
    catch(e) { if (request === sequence.current) onFailure(e); }
    finally { if (request === sequence.current) setBusy(false); }
  }
  async function select(restaurant: RestaurantChoice, page = 0) {
    const request = ++sequence.current; setBusy(true); setSelected(restaurant); setDrafts(null);
    try { const result = await api.drafts(restaurant.id, page); if (request === sequence.current) setDrafts(result); }
    catch(e) { if (request === sequence.current) onFailure(e); }
    finally { if (request === sequence.current) setBusy(false); }
  }
  return <details className="draft-picker"><summary>초안 이어쓰기</summary>
    <form onSubmit={e => { e.preventDefault(); void search(); }}><label>초안을 찾을 식당명·주소·셰프<input value={query}
      onChange={e => { sequence.current++; setBusy(false); setQuery(e.target.value); setRestaurants(null); setDrafts(null); setSelected(null); }}/></label>
      <button disabled={disabled || busy}>식당에서 초안 찾기</button></form>
    {restaurants?.items.map(r => <div key={r.id}><button className="secondary" disabled={disabled || busy} onClick={() => void select(r)}>{r.name} · {r.roadAddress || r.address || '주소 없음'}</button></div>)}
    {restaurants && !restaurants.items.length && <p>해당 식당이 없습니다.</p>}
    {restaurants && <div className="registration-actions"><button disabled={disabled || busy || restaurants.page === 0} onClick={() => void search(restaurants.page - 1)}>이전 식당</button>
      <button disabled={disabled || busy || !restaurants.hasNext} onClick={() => void search(restaurants.page + 1)}>다음 식당</button></div>}
    {selected && <h4>{selected.name}의 작성 중 초안</h4>}
    {drafts?.items.map(d => <button key={d.id} className="secondary" disabled={disabled || busy} onClick={() => onOpen(d.id)}>초안 #{d.id} · 수정 {new Date(d.updatedAt).toLocaleString('ko-KR')}</button>)}
    {drafts && !drafts.items.length && <p>작성 중인 초안이 없습니다.</p>}
    {drafts && selected && <div className="registration-actions"><button disabled={disabled || busy || drafts.page === 0} onClick={() => void select(selected, drafts.page - 1)}>이전 초안</button>
      <button disabled={disabled || busy || !drafts.hasNext} onClick={() => void select(selected, drafts.page + 1)}>다음 초안</button></div>}
  </details>;
}
