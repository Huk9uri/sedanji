import { useEffect, useRef, useState } from 'react';
import { registrationApi } from './registrationApi';
import type { Chef, Fields } from './types';
export const typeLabels = { REGULAR: '단골', RECOMMENDED: '추천', VISITED: '방문', MENTIONED: '단순 언급' };
export default function RelationshipFields({ fields, onChange, chefs, onChef, onFailure, disabled, onBusy }: {
  fields: Fields; onChange: (fields: Fields) => void; chefs: Chef[]; onChef: (name: string) => void;
  onFailure: (e: unknown) => void; disabled: boolean; onBusy: (busy: boolean) => void;
}) {
  const [loading, setLoading] = useState(false);
  const request = useRef(0);
  const current = useRef(fields); current.current = fields;
  const loadingRef = useRef(false);
  useEffect(() => () => { request.current++; }, []);
  const update = (patch: Partial<Fields>) => onChange({ ...fields, ...patch });
  async function metadata() {
    if (loadingRef.current || !fields.videoUrl.trim()) return;
    const id = ++request.current, url = fields.videoUrl;
    loadingRef.current = true; setLoading(true); onBusy(true);
    try {
      const video = await registrationApi.metadata(url);
      if (id === request.current && current.current.videoUrl === url) onChange({ ...current.current, video });
    } catch(e) { if (id === request.current) onFailure(e); }
    finally { loadingRef.current = false; setLoading(false); onBusy(false); }
  }
  return <fieldset disabled={disabled} className="relationship-fields"><legend>2. 관계 입력</legend>
    <label>기존 셰프 검색<input value={fields.chefSearch} maxLength={100} onChange={e => update({ chefSearch: e.target.value })}/></label>
    <div className="registration-inline"><label>관계 셰프 (승인 필수)<select value={fields.chefId ?? ''} onChange={e => update({ chefId: e.target.value ? Number(e.target.value) : null, chefSearch: '' })}>
      <option value="">미선택</option>{chefs.filter(c => c.id === fields.chefId || c.name.toLowerCase().includes(fields.chefSearch.toLowerCase())).map(c => <option key={c.id} value={c.id}>{c.name} · ID {c.id}</option>)}
    </select></label><button type="button" className="secondary" disabled={!fields.chefSearch.trim()} onClick={() => onChef(fields.chefSearch)}>동명이인 포함 신규 셰프 생성</button></div>
    <p className="field-help">이름이 같아도 ID가 다르면 별개의 셰프입니다. 검색 결과를 확인한 뒤 신규 생성하세요.</p>
    <div className="registration-inline"><label>출처 영상 URL<input value={fields.videoUrl} maxLength={500} placeholder="https://www.youtube.com/watch?v=…"
      onChange={e => { request.current++; update({ videoUrl: e.target.value, video: null }); }}/></label>
      <button type="button" className="secondary" disabled={loading || !fields.videoUrl.trim()} onClick={() => void metadata()}>{loading ? '영상 확인 중…' : '메타데이터 확인'}</button></div>
    {fields.video && <article className="registration-video"><strong>{fields.video.title}</strong><p>{fields.video.channelTitle} · {new Date(fields.video.publishedAt).toLocaleDateString('ko-KR')}</p>
      <a href={`https://www.youtube.com/watch?v=${fields.video.id}`} target="_blank" rel="noreferrer">원본 영상 확인 ↗</a><small>Video ID {fields.video.id}</small></article>}
    <label>관계 유형 (승인 필수)<select value={fields.mentionType} onChange={e => update({ mentionType: e.target.value as Fields['mentionType'] })}>
      <option value="">미선택</option>{Object.entries(typeLabels).map(([value,label]) => <option key={value} value={value}>{label}</option>)}
    </select></label>
    <label>확인된 시점 (선택)<input value={fields.timestamp} placeholder="03:24 또는 1:02:03" onChange={e => update({ timestamp: e.target.value })}/></label>
    <p className="field-help">공란은 미확인입니다. 확인한 0초는 00:00으로 입력하세요.</p>
    <label>검증 메모 (선택)<textarea value={fields.note} maxLength={2000} onChange={e => update({ note: e.target.value })}/></label>
    <small>{fields.note.length} / 2000 · 내부 검토용이며 공개하지 않습니다.</small>
  </fieldset>;
}
