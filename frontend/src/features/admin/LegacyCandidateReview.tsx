import { useEffect, useRef, useState } from 'react';
import { api, errorMessage } from '../../api';
import type { Candidate, CandidateDisposition, Chef, Mention, Video } from '../../types';
import CandidateReviewCard from './CandidateReviewCard';
const mentionLabels = { REGULAR: '단골', RECOMMENDED: '추천', VISITED: '방문', MENTIONED: '단순 언급' };
export default function LegacyCandidateReview({ onPublished, onFailure }: { onPublished: () => Promise<void>; onFailure: (e: unknown) => void }) {
  const [videos, setVideos] = useState<Video[]>([]);
  const [mentions, setMentions] = useState<Mention[]>([]);
  const [candidates, setCandidates] = useState<Candidate[]>([]);
  const [chefs, setChefs] = useState<Chef[]>([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [filter, setFilter] = useState<CandidateDisposition>('OPEN');
  const active = useRef(true);
  async function refresh() {
    const [v,m,c,extracted] = await Promise.all([api<Video[]>('/admin/videos'),api<Mention[]>('/admin/mentions'),api<Chef[]>('/admin/chefs'),api<Candidate[]>('/admin/candidates')]);
    if (!active.current) return;
    setVideos(v); setMentions(m); setChefs(c); setCandidates(extracted);
    await onPublished();
  }
  useEffect(() => { active.current=true; void refresh().catch(e=>{if(active.current){setError(errorMessage(e));onFailure(e);}}); return ()=>{active.current=false;}; }, []);
  async function run(fn:()=>Promise<void>) {setBusy(true);setError('');setMessage('');try{await fn();}catch(e){if(active.current){setError(errorMessage(e));onFailure(e);}}finally{if(active.current)setBusy(false);}}
  return <section aria-label="기존 후보 검토">

    {error && <p role="alert" className="error">{error}</p>}{message && <p role="status" className="notice">{message}</p>}
    <div className="admin-tools"><form onSubmit={event => { event.preventDefault(); const url = new FormData(event.currentTarget).get('url'); run(async () => { await api('/admin/videos', { method: 'POST', body: { url } }); await refresh(); setMessage('설명란을 가져왔습니다. 추출 후보와 누락된 식당을 확인하세요.'); }); }}><label>YouTube 영상 URL<input name="url" type="url" required placeholder="https://www.youtube.com/watch?v=…"/></label><button disabled={busy}>설명란 가져오기</button></form>
    <form onSubmit={event => { event.preventDefault(); const form = event.currentTarget; const name = new FormData(form).get('name'); run(async () => { await api('/admin/chefs', { method: 'POST', body: { name } }); await refresh(); form.reset(); }); }}><label>셰프 등록<input name="name" maxLength={100} required placeholder="직접 확인한 셰프 이름"/></label><button disabled={busy} className="secondary">등록</button></form></div>
    <div className="workflow"><span>01 설명란 확인</span><span>02 장소·셰프 대조</span><span>03 승인 후 공개</span></div>
    <details className="video-evidence"><summary>원문 확인 및 누락 후보 수동 추가 · 영상 {videos.length}개</summary>{videos.map(video => <article key={video.id}><h3>{video.title}</h3><p className="notice">{video.extractionWarning}</p><pre>{video.description || '(빈 설명란)'}</pre><form onSubmit={event => { event.preventDefault(); const form = event.currentTarget; const values = Object.fromEntries(new FormData(form)); run(async () => { await api(`/admin/videos/${video.id}/candidates`, { method: 'POST', body: values }); await refresh(); form.reset(); }); }}><label>누락 상호<input required name="name" maxLength={100}/></label><label>주소 (없으면 비워두기)<input name="address" maxLength={300}/></label><label>설명란 원문 근거<textarea required name="evidence" maxLength={5000}/></label><button disabled={busy}>검토 후보 추가</button></form></article>)}</details>
    <div className="section-heading"><h3>추출 후보</h3><label className="compact">상태<select value={filter} onChange={event => setFilter(event.target.value as CandidateDisposition)}><option value="OPEN">검토 대기</option><option value="RESOLVED">관계 승인됨</option><option value="DISMISSED">후보 제외</option></select></label></div>
    <div className="review-grid">{candidates.filter(candidate => candidate.disposition === filter).map(candidate => <CandidateReviewCard key={candidate.id} candidate={candidate} chefs={chefs} refresh={refresh} setError={setError} onFailure={onFailure}/>)}</div>
    {!candidates.some(candidate => candidate.disposition === filter) && <p className="empty">해당 상태의 후보가 없습니다. 설명란 원문에서 누락된 식당을 확인할 수 있습니다.</p>}
    <div className="section-heading"><h3>셰프–식당 관계 · {mentions.length}개</h3></div>
    {!mentions.length && <p className="empty">승인된 관계가 없습니다. 추출 후보는 관계 승인 전까지 공개되지 않습니다.</p>}
    {mentions.map(mention => <article className="review-card" key={mention.id}><h3>{mention.chefName} · {mention.restaurantName}</h3><p>{mention.mentionType == null ? "" : mentionLabels[mention.mentionType]} · {mention.verificationStatus} · {mention.timestampSeconds == null ? '시점 미확인' : `${mention.timestampSeconds}초`}</p><a href={`https://www.youtube.com/watch?v=${mention.videoId}`} target="_blank" rel="noreferrer">{mention.videoTitle} ↗</a><details><summary>관계 검증 근거</summary><pre>{mention.evidence}</pre><p>{mention.reviewNote}</p><p>검토자: {mention.reviewedBy}</p></details></article>)}

  </section>;
}
