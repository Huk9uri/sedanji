import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from './api';
import KakaoMap from './KakaoMap';

function PlaceCard({ restaurant, selected, onSelect }) {
  return <article className={`restaurant ${selected ? 'selected' : ''}`}>
    <button className="restaurant-title" onClick={() => onSelect(restaurant.id)}><span className="pin">⌖</span><h3>{restaurant.name}</h3><span>↗</span></button>
    <p>{restaurant.roadAddress || restaurant.address}</p>
    <div className="source-list">{restaurant.mentions.map(source => <a key={source.mentionId} href={`https://www.youtube.com/watch?v=${source.videoId}`} target="_blank" rel="noreferrer"><span className="tag">{source.chefName} 셰프</span><span>{source.videoTitle} ↗</span></a>)}</div>
    <a className="text-link" href={restaurant.placeUrl} target="_blank" rel="noreferrer">카카오맵에서 보기 ↗</a>
  </article>;
}

function ReviewCard({ mention, chefs, refresh, setError }) {
  const [search, setSearch] = useState(null);
  const [query, setQuery] = useState(mention.extractedName);
  const [placeId, setPlaceId] = useState('');
  const [chefId, setChefId] = useState('');
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  async function action(fn) {
    setBusy(true); setError('');
    try { await fn(); } catch (error) { setError(error.message); } finally { setBusy(false); }
  }
  return <article className="review-card">
    <div className="card-top"><span className="eyebrow">{mention.status === 'PENDING' ? '검토 대기' : mention.status === 'APPROVED' ? '승인 완료' : '반려'}</span><a href={`https://www.youtube.com/watch?v=${mention.videoId}`} target="_blank" rel="noreferrer">원본 영상 ↗</a></div>
    <h3>{mention.extractedName}</h3><p>{mention.extractedAddress || '설명란에 주소 없음 · 지점 확인 필요'}</p>
    <details><summary>추출 근거 · {mention.sourceLine ? `${mention.sourceLine}행` : '수동 추가'}</summary><pre>{mention.evidence}</pre><p>{mention.videoTitle}</p></details>
    {mention.status === 'PENDING' ? <>
      <form className="inline-form" onSubmit={event => { event.preventDefault(); action(async () => { setPlaceId(''); setSearch(await api(`/admin/mentions/${mention.id}/places`, { method: 'POST', body: { query } })); }); }}>
        <label>장소 검색어<input required maxLength={100} value={query} onChange={event => setQuery(event.target.value)}/></label><button disabled={busy}>Kakao 검색</button>
      </form>
      {search && <fieldset><legend>장소 후보 {search.candidates.length}개</legend>{!search.complete && <p className="notice">전체 후보를 확보하지 못했습니다. 다른 지점이 있는지 확인하세요.</p>}{!search.candidates.length && <p>결과가 없습니다. 상호와 지역을 바꿔 검색해 주세요.</p>}{search.candidates.map(place => <label className="place-option" key={place.id}><input type="radio" name={`place-${mention.id}`} value={place.id} checked={placeId === place.id} onChange={() => setPlaceId(place.id)}/><span><strong>{place.name}</strong><span>{place.roadAddress || place.address}</span><small>ID {place.id} · {place.latitude}, {place.longitude}</small><a href={place.placeUrl} target="_blank" rel="noreferrer">카카오맵 확인 ↗</a></span></label>)}</fieldset>}
      <label>추천 셰프<select value={chefId} onChange={event => setChefId(event.target.value)}><option value="">영상의 추천 주체를 확인해 선택</option>{chefs.map(chef => <option key={chef.id} value={chef.id}>{chef.name}</option>)}</select></label>
      <label>검토 메모<textarea required maxLength={2000} placeholder="추천 셰프·지점·주소를 확인한 근거를 남겨 주세요." value={note} onChange={event => setNote(event.target.value)}/></label>
      <div className="actions"><button disabled={busy || !placeId || !chefId || !note.trim()} onClick={() => action(async () => { await api(`/admin/mentions/${mention.id}/approve`, { method: 'POST', body: { chefId: Number(chefId), placeId, note } }); await refresh(); })}>승인하고 지도에 공개</button><button className="secondary" disabled={busy || !note.trim()} onClick={() => action(async () => { await api(`/admin/mentions/${mention.id}/reject`, { method: 'POST', body: { note } }); await refresh(); })}>반려</button></div>
    </> : <p className="notice">{mention.reviewNote}</p>}
  </article>;
}

function Admin({ onPublished }) {
  const [session, setSession] = useState(null);
  const [videos, setVideos] = useState([]);
  const [mentions, setMentions] = useState([]);
  const [chefs, setChefs] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [filter, setFilter] = useState('PENDING');
  async function refresh() {
    const [v, m, c] = await Promise.all([api('/admin/videos'), api('/admin/mentions'), api('/admin/chefs')]);
    setVideos(v); setMentions(m); setChefs(c); await onPublished();
  }
  useEffect(() => { api('/session').then(async s => { setSession(s); if (s.authenticated) await refresh(); }).catch(error => setError(error.message)); }, []);
  async function run(fn) { setBusy(true); setError(''); setMessage(''); try { await fn(); } catch (error) { setError(error.message); } finally { setBusy(false); } }
  if (!session?.authenticated) return <section className="login-panel"><span className="eyebrow">CURATOR DESK</span><h2>관리자 로그인</h2><p>영상의 근거를 확인하고, 다음 단골집을 지도에 더합니다.</p>{error && <p role="alert" className="error">{error}</p>}<form onSubmit={event => { event.preventDefault(); const form = new FormData(event.currentTarget); run(async () => { await api('/login', { method: 'POST', form: true, body: Object.fromEntries(form) }); setSession(await api('/session')); await refresh(); }); }}><label>아이디<input name="username" autoComplete="username" required/></label><label>비밀번호<input name="password" type="password" autoComplete="current-password" required/></label><button disabled={busy}>로그인</button></form></section>;
  return <section className="admin"><div className="section-heading"><div><span className="eyebrow">CURATOR DESK</span><h2>영상에서 지도까지</h2></div><button className="secondary" onClick={() => run(async () => { await api('/logout', { method: 'POST' }); setSession({ authenticated: false }); })}>로그아웃</button></div>
    {error && <p role="alert" className="error">{error}</p>}{message && <p role="status" className="notice">{message}</p>}
    <div className="admin-tools"><form onSubmit={event => { event.preventDefault(); const url = new FormData(event.currentTarget).get('url'); run(async () => { await api('/admin/videos', { method: 'POST', body: { url } }); await refresh(); setMessage('설명란을 가져왔습니다. 추출 후보와 누락된 식당을 확인하세요.'); }); }}><label>YouTube 영상 URL<input name="url" type="url" required placeholder="https://www.youtube.com/watch?v=…"/></label><button disabled={busy}>설명란 가져오기</button></form>
    <form onSubmit={event => { event.preventDefault(); const form = event.currentTarget; const name = new FormData(form).get('name'); run(async () => { await api('/admin/chefs', { method: 'POST', body: { name } }); await refresh(); form.reset(); }); }}><label>셰프 등록<input name="name" maxLength={100} required placeholder="직접 확인한 셰프 이름"/></label><button disabled={busy} className="secondary">등록</button></form></div>
    <div className="workflow"><span>01 설명란 확인</span><span>02 장소·셰프 대조</span><span>03 승인 후 공개</span></div>
    <details className="video-evidence"><summary>원문 확인 및 누락 후보 수동 추가 · 영상 {videos.length}개</summary>{videos.map(video => <article key={video.id}><h3>{video.title}</h3><p className="notice">{video.extractionWarning}</p><pre>{video.description || '(빈 설명란)'}</pre><form onSubmit={event => { event.preventDefault(); const form = event.currentTarget; const values = Object.fromEntries(new FormData(form)); run(async () => { await api(`/admin/videos/${video.id}/mentions`, { method: 'POST', body: { ...values, sourceLine: 0 } }); await refresh(); form.reset(); }); }}><label>누락 상호<input required name="name" maxLength={100}/></label><label>주소 (없으면 비워두기)<input name="address" maxLength={300}/></label><label>설명란 원문 근거<textarea required name="evidence" maxLength={5000}/></label><button disabled={busy}>검토 후보 추가</button></form></article>)}</details>
    <div className="section-heading"><h3>추출 후보</h3><label className="compact">상태<select value={filter} onChange={event => setFilter(event.target.value)}><option value="PENDING">검토 대기</option><option value="APPROVED">승인 완료</option><option value="REJECTED">반려</option></select></label></div>
    <div className="review-grid">{mentions.filter(mention => mention.status === filter).map(mention => <ReviewCard key={mention.id} mention={mention} chefs={chefs} refresh={refresh} setError={setError}/>)}</div>
    {!mentions.some(mention => mention.status === filter) && <p className="empty">해당 상태의 후보가 없습니다. 설명란 원문에서 누락된 식당을 확인할 수 있습니다.</p>}
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
    <main>{page === 'map' ? <><section className="hero"><span className="eyebrow">A CHEF’S FAVORITE, YOUR NEXT FAVORITE</span><h1>셰프가 아끼는 한 끼,<br/><em>당신의 다음 단골집.</em></h1><p>영상 속 추천을 따라, 믿고 찾아갈 한 곳을 발견하세요.<br/>식당과 추천 근거를 직접 확인한 뒤 지도에 담습니다.</p><div className="hero-bottom"><span className="count">{restaurants.length.toString().padStart(2, '0')} <small>확인한 장소</small></span><span className="seal">✓ 영상 근거 확인 · 관리자 승인</span></div></section>
    {error && <p role="alert" className="error">{error} <button onClick={() => { setError(''); loadRestaurants().catch(error => setError(error.message)); }}>다시 시도</button></p>}
    <section className="explore"><div className="explore-list"><div className="section-heading"><h2>단골집 탐색</h2><span>{filtered.length}곳</span></div><label className="search"><span>⌕</span><input aria-label="식당, 지역, 셰프 검색" placeholder="식당, 지역, 셰프로 찾아보세요" value={query} onChange={event => setQuery(event.target.value)}/></label><div className="restaurants">{filtered.map(restaurant => <PlaceCard key={restaurant.id} restaurant={restaurant} selected={selected === restaurant.id} onSelect={select}/>)}{!filtered.length && <div className="empty"><span>⌖</span><h3>{loading ? '단골집을 불러오고 있습니다' : restaurants.length ? '검색 결과가 없습니다' : '첫 단골집을 기다리고 있어요'}</h3><p>{restaurants.length ? '다른 지역이나 셰프 이름으로 찾아보세요.' : '관리자가 영상 속 식당을 확인하고 승인하면 이곳에 표시됩니다.'}</p></div>}</div></div><KakaoMap restaurants={filtered} selected={selected} onSelect={select}/></section></> : <Admin onPublished={loadRestaurants}/>}</main><footer><span className="footer-brand">세단지</span><span>취향을 잇는, 셰프의 단골 지도.</span><span>영상 속 근거에서 시작하는 맛있는 발견</span></footer></>;
}
