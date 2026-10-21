import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from './api';
import KakaoMap from './KakaoMap';
import CandidateReviewCard from './features/admin/CandidateReviewCard';
const mentionLabels = { REGULAR: '단골', RECOMMENDED: '추천', VISITED: '방문', MENTIONED: '단순 언급' };

function PlaceCard({ restaurant, selected, onSelect }) {
  return <article className={`restaurant ${selected ? 'selected' : ''}`}>
    <button className="restaurant-title" onClick={() => onSelect(restaurant.id)}><span className="pin">⌖</span><h3>{restaurant.name}</h3><span>↗</span></button>
    <p>{restaurant.roadAddress || restaurant.address}</p>
    <div className="source-list">{restaurant.mentions.map(source => <a key={source.mentionId} href={`https://www.youtube.com/watch?v=${source.videoId}${source.timestampSeconds == null ? "" : `&t=${source.timestampSeconds}s`}`} target="_blank" rel="noreferrer"><span className="tag">{source.chefName} 셰프 · {mentionLabels[source.mentionType] || "관계 확인"}</span><span>{source.videoTitle} ↗</span></a>)}</div>
    <a className="text-link" href={restaurant.placeUrl} target="_blank" rel="noreferrer">카카오맵에서 보기 ↗</a>
  </article>;
}

function Admin({ onPublished }) {
  const [session, setSession] = useState(null);
  const [videos, setVideos] = useState([]);
  const [mentions, setMentions] = useState([]);
  const [candidates, setCandidates] = useState([]);
  const [chefs, setChefs] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [filter, setFilter] = useState('OPEN');
  async function refresh() {
    const [v, m, c, extracted] = await Promise.all([api('/admin/videos'), api('/admin/mentions'), api('/admin/chefs'), api('/admin/candidates')]);
    setVideos(v); setMentions(m); setChefs(c); setCandidates(extracted); await onPublished();
  }
  useEffect(() => { api('/session').then(async s => { setSession(s); if (s.authenticated) await refresh(); }).catch(error => setError(error.message)); }, []);
  async function run(fn) { setBusy(true); setError(''); setMessage(''); try { await fn(); } catch (error) { setError(error.message); } finally { setBusy(false); } }
  if (!session?.authenticated) return <section className="login-panel"><span className="eyebrow">CURATOR DESK</span><h2>관리자 로그인</h2><p>영상의 근거를 확인하고, 다음 단골집을 지도에 더합니다.</p>{error && <p role="alert" className="error">{error}</p>}<form onSubmit={event => { event.preventDefault(); const form = new FormData(event.currentTarget); run(async () => { await api('/login', { method: 'POST', form: true, body: Object.fromEntries(form) }); setSession(await api('/session')); await refresh(); }); }}><label>아이디<input name="username" autoComplete="username" required/></label><label>비밀번호<input name="password" type="password" autoComplete="current-password" required/></label><button disabled={busy}>로그인</button></form></section>;
  return <section className="admin"><div className="section-heading"><div><span className="eyebrow">CURATOR DESK</span><h2>영상에서 지도까지</h2></div><button className="secondary" onClick={() => run(async () => { await api('/logout', { method: 'POST' }); setSession({ authenticated: false }); })}>로그아웃</button></div>
    {error && <p role="alert" className="error">{error}</p>}{message && <p role="status" className="notice">{message}</p>}
    <div className="admin-tools"><form onSubmit={event => { event.preventDefault(); const url = new FormData(event.currentTarget).get('url'); run(async () => { await api('/admin/videos', { method: 'POST', body: { url } }); await refresh(); setMessage('설명란을 가져왔습니다. 추출 후보와 누락된 식당을 확인하세요.'); }); }}><label>YouTube 영상 URL<input name="url" type="url" required placeholder="https://www.youtube.com/watch?v=…"/></label><button disabled={busy}>설명란 가져오기</button></form>
    <form onSubmit={event => { event.preventDefault(); const form = event.currentTarget; const name = new FormData(form).get('name'); run(async () => { await api('/admin/chefs', { method: 'POST', body: { name } }); await refresh(); form.reset(); }); }}><label>셰프 등록<input name="name" maxLength={100} required placeholder="직접 확인한 셰프 이름"/></label><button disabled={busy} className="secondary">등록</button></form></div>
    <div className="workflow"><span>01 설명란 확인</span><span>02 장소·셰프 대조</span><span>03 승인 후 공개</span></div>
    <details className="video-evidence"><summary>원문 확인 및 누락 후보 수동 추가 · 영상 {videos.length}개</summary>{videos.map(video => <article key={video.id}><h3>{video.title}</h3><p className="notice">{video.extractionWarning}</p><pre>{video.description || '(빈 설명란)'}</pre><form onSubmit={event => { event.preventDefault(); const form = event.currentTarget; const values = Object.fromEntries(new FormData(form)); run(async () => { await api(`/admin/videos/${video.id}/candidates`, { method: 'POST', body: values }); await refresh(); form.reset(); }); }}><label>누락 상호<input required name="name" maxLength={100}/></label><label>주소 (없으면 비워두기)<input name="address" maxLength={300}/></label><label>설명란 원문 근거<textarea required name="evidence" maxLength={5000}/></label><button disabled={busy}>검토 후보 추가</button></form></article>)}</details>
    <div className="section-heading"><h3>추출 후보</h3><label className="compact">상태<select value={filter} onChange={event => setFilter(event.target.value)}><option value="OPEN">검토 대기</option><option value="RESOLVED">관계 승인됨</option><option value="DISMISSED">후보 제외</option></select></label></div>
    <div className="review-grid">{candidates.filter(candidate => candidate.disposition === filter).map(candidate => <CandidateReviewCard key={candidate.id} candidate={candidate} chefs={chefs} refresh={refresh} setError={setError}/>)}</div>
    {!candidates.some(candidate => candidate.disposition === filter) && <p className="empty">해당 상태의 후보가 없습니다. 설명란 원문에서 누락된 식당을 확인할 수 있습니다.</p>}
    <div className="section-heading"><h3>셰프–식당 관계 · {mentions.length}개</h3></div>
    {!mentions.length && <p className="empty">승인된 관계가 없습니다. 추출 후보는 관계 승인 전까지 공개되지 않습니다.</p>}
    {mentions.map(mention => <article className="review-card" key={mention.id}><h3>{mention.chefName} · {mention.restaurantName}</h3><p>{mentionLabels[mention.mentionType]} · {mention.verificationStatus} · {mention.timestampSeconds == null ? '시점 미확인' : `${mention.timestampSeconds}초`}</p><a href={`https://www.youtube.com/watch?v=${mention.videoId}`} target="_blank" rel="noreferrer">{mention.videoTitle} ↗</a><details><summary>관계 검증 근거</summary><pre>{mention.evidence}</pre><p>{mention.reviewNote}</p><p>검토자: {mention.reviewedBy}</p></details></article>)}
  </section>;
}

export default function App() {
  const [page, setPage] = useState('map');
  const [restaurants, setRestaurants] = useState([]);
  const [selected, setSelected] = useState(null);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const loadRestaurants = useCallback(async () => { const data = await api('/restaurants'); setRestaurants(data); }, []);
  const select = useCallback(id => setSelected(id), []);
  useEffect(() => { loadRestaurants().catch(error => setError(error.message)).finally(() => setLoading(false)); }, [loadRestaurants]);
  const filtered = useMemo(() => restaurants.filter(restaurant => `${restaurant.name} ${restaurant.roadAddress} ${restaurant.mentions.map(source => source.chefName).join(' ')}`.includes(query.trim())), [restaurants, query]);
  return <><header><a className="brand" href="#" onClick={event => { event.preventDefault(); setPage('map'); }}><span className="brand-icon">세</span><span>세단지<small>셰프의 단골 지도</small></span></a><nav><button className={page === 'map' ? 'nav-active' : ''} onClick={() => setPage('map')}>맛집 지도</button><button className={page === 'admin' ? 'nav-active' : ''} onClick={() => setPage('admin')}>관리자</button></nav></header>
    <main>{page === 'map' ? <><section className="hero"><span className="eyebrow">A CHEF’S FAVORITE, YOUR NEXT FAVORITE</span><h1>셰프가 아끼는 한 끼,<br/><em>당신의 다음 단골집.</em></h1><p>영상 속 이야기를 따라, 믿고 찾아갈 한 곳을 발견하세요.<br/>식당과 추천 근거를 직접 확인한 뒤 지도에 담습니다.</p><div className="hero-bottom"><span className="count">{restaurants.length.toString().padStart(2, '0')} <small>확인한 장소</small></span><span className="seal">✓ 영상 근거 확인 · 관리자 승인</span></div></section>
    {error && <p role="alert" className="error">{error} <button onClick={() => { setError(''); loadRestaurants().catch(error => setError(error.message)); }}>다시 시도</button></p>}
    <section className="explore"><div className="explore-list"><div className="section-heading"><h2>단골집 탐색</h2><span>{filtered.length}곳</span></div><label className="search"><span>⌕</span><input aria-label="식당, 지역, 셰프 검색" placeholder="식당, 지역, 셰프로 찾아보세요" value={query} onChange={event => setQuery(event.target.value)}/></label><div className="restaurants">{filtered.map(restaurant => <PlaceCard key={restaurant.id} restaurant={restaurant} selected={selected === restaurant.id} onSelect={select}/>)}{!filtered.length && <div className="empty"><span>⌖</span><h3>{loading ? '단골집을 불러오고 있습니다' : restaurants.length ? '검색 결과가 없습니다' : '첫 단골집을 기다리고 있어요'}</h3><p>{restaurants.length ? '다른 지역이나 셰프 이름으로 찾아보세요.' : '관리자가 영상 속 식당을 확인하고 승인하면 이곳에 표시됩니다.'}</p></div>}</div></div><KakaoMap restaurants={filtered} selected={selected} onSelect={select}/></section></> : <Admin onPublished={loadRestaurants}/>}</main><footer><span className="footer-brand">세단지</span><span>취향을 잇는, 셰프의 단골 지도.</span><span>영상 속 근거에서 시작하는 맛있는 발견</span></footer></>;
}
