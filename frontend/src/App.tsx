import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { api, errorMessage } from './api';
import type { Restaurant, Session } from './types';
import KakaoMap from './KakaoMap';
import AdminWorkspace from './features/admin/AdminWorkspace';
import type { LeaveGuard } from './features/admin/AdminWorkspace';
import { draftUrl } from './features/admin/registration/useRegistration';
const mentionLabels = { REGULAR: '단골', RECOMMENDED: '추천', VISITED: '방문', MENTIONED: '단순 언급' };

function PlaceCard({ restaurant, selected, onSelect }: { restaurant: Restaurant; selected: boolean; onSelect: (id: number) => void }) {
  return <article className={`restaurant ${selected ? 'selected' : ''}`}>
    <button className="restaurant-title" onClick={() => onSelect(restaurant.id)}><span className="pin">⌖</span><h3>{restaurant.name}</h3><span>↗</span></button>
    <p>{restaurant.roadAddress || restaurant.address}</p>
    <div className="source-list">{restaurant.mentions.map(source => <a key={source.mentionId} href={`https://www.youtube.com/watch?v=${source.videoId}${source.timestampSeconds == null ? "" : `&t=${source.timestampSeconds}s`}`} target="_blank" rel="noreferrer"><span className="tag">{source.chefName} 셰프 · {mentionLabels[source.mentionType] || "관계 확인"}</span><span>{source.videoTitle} ↗</span></a>)}</div>
    <a className="text-link" href={restaurant.placeUrl} target="_blank" rel="noreferrer">카카오맵에서 보기 ↗</a>
  </article>;
}

function Admin({ onPublished, onGuard }: { onPublished: (isCurrent?: () => boolean) => Promise<void>; onGuard: (guard: LeaveGuard | null) => void }) {
  const [session, setSession] = useState<Session | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [authGeneration, setAuthGeneration] = useState(0);
  const [workspaceKey, setWorkspaceKey] = useState(0);
  const sessionRequest = useRef(0);
  const identity = useRef<string | undefined>(undefined);
  const leaveGuard = useRef<LeaveGuard | null>(null);
  const registerGuard = useCallback((guard: LeaveGuard | null) => { leaveGuard.current = guard; onGuard(guard); }, [onGuard]);
  const expire = useCallback(() => { sessionRequest.current++; setSession({ authenticated: false }); setAuthGeneration(n=>n+1); setError('관리자 로그인이 만료됐습니다. 작성 입력은 유지됩니다. 다시 로그인하세요.'); }, []);
  function acceptSession(next: Session) {
    if (next.authenticated && next.username) {
      if (identity.current && identity.current !== next.username) { setWorkspaceKey(n=>n+1); draftUrl(null); }
      identity.current=next.username;
    }
    setSession(next); setAuthGeneration(n=>n+1);
  }
  useEffect(() => { let active=true; const request=++sessionRequest.current; void api<Session>('/session').then(s=>{if(active&&request===sessionRequest.current)acceptSession(s);}).catch(e=>{if(active&&request===sessionRequest.current)setError(errorMessage(e));}); return ()=>{active=false;sessionRequest.current++;}; }, []);
  async function login(form: FormData) {
    if (busy) return;
    setBusy(true);setError('');const request=++sessionRequest.current;
    try {await api('/login',{method:'POST',form:true,body:Object.fromEntries(form)});const next=await api<Session>('/session');if(request===sessionRequest.current)acceptSession(next);}
    catch(e){if(request===sessionRequest.current)setError(errorMessage(e));}finally{setBusy(false);}
  }
  function logout() {
    const proceed=()=>{void (async()=>{const request=++sessionRequest.current;setBusy(true);try{await api('/logout',{method:'POST'});if(request!==sessionRequest.current)return;setSession({authenticated:false});setAuthGeneration(n=>n+1);setWorkspaceKey(n=>n+1);draftUrl(null);}catch(e){if(request===sessionRequest.current)setError(errorMessage(e));}finally{setBusy(false);}})();};
    if(leaveGuard.current)leaveGuard.current(proceed);else proceed();
  }
  return <>
    {!session?.authenticated && <section className="login-panel"><span className="eyebrow">CURATOR DESK</span><h2>관리자 로그인</h2><p>식당과 영상의 근거를 확인합니다.</p>
      {error && <p role="alert" className="error">{error}</p>}<form onSubmit={event=>{event.preventDefault();void login(new FormData(event.currentTarget));}}>
        <label>아이디<input name="username" autoComplete="username" required/></label><label>비밀번호<input name="password" type="password" autoComplete="current-password" required/></label><button disabled={busy}>로그인</button>
      </form></section>}
    <section className="admin" hidden={!session?.authenticated}>
      <div className="section-heading"><span className="eyebrow">CURATOR DESK</span><button className="secondary" disabled={busy} onClick={logout}>로그아웃</button></div>
      {session?.authenticated && error && <p role="alert" className="error">{error}</p>}
      <AdminWorkspace key={workspaceKey} username={session?.authenticated ? session.username : undefined} authGeneration={authGeneration}
        onExpired={expire} onPublished={onPublished} onGuard={registerGuard}/>
    </section>
  </>;
}
export default function App() {
  const [page, setPage] = useState(() => new URLSearchParams(window.location.search).has('draftId') ? 'admin' : 'map');
  const leaveGuard = useRef<LeaveGuard | null>(null);
  const onGuard = useCallback((guard: LeaveGuard | null) => { leaveGuard.current = guard; }, []);
  function changePage(next: string) { if (next === page) return; const proceed=() => { if (next === 'map') draftUrl(null); setPage(next); }; if (leaveGuard.current) leaveGuard.current(proceed); else proceed(); }
  const [restaurants, setRestaurants] = useState<Restaurant[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const publicSequence = useRef(0);
  const loadRestaurants = useCallback(async (isCurrent?: () => boolean) => {
    const request=++publicSequence.current;
    // A successful admin write invalidates all previous public memory and in-flight reads.
    setRestaurants([]);setSelected(null);setError('');setLoading(true);
    try {
      const data=await api<Restaurant[]>('/restaurants');
      if(request===publicSequence.current && (!isCurrent || isCurrent()))setRestaurants(data);
    } catch(e) {
      if(request===publicSequence.current && (!isCurrent || isCurrent())){setError(errorMessage(e));throw e;}
    } finally {
      if(request===publicSequence.current){
        if(isCurrent && !isCurrent())setError('인증 상태가 변경됐습니다. 공개 목록을 다시 조회하세요.');
        setLoading(false);
      }
    }
  }, []);
  const select = useCallback((id: number) => setSelected(id), []);
  useEffect(() => { void loadRestaurants().catch(()=>{});return ()=>{publicSequence.current++;}; }, [loadRestaurants]);
  const filtered = useMemo(() => restaurants.filter(restaurant => `${restaurant.name} ${restaurant.roadAddress} ${restaurant.mentions.map(source => source.chefName).join(' ')}`.includes(query.trim())), [restaurants, query]);
  return <><header><a className="brand" href="#" onClick={event => { event.preventDefault(); changePage('map'); }}><span className="brand-icon">세</span><span>세단지<small>셰프의 단골 지도</small></span></a><nav><button className={page === 'map' ? 'nav-active' : ''} onClick={() => changePage('map')}>맛집 지도</button><button className={page === 'admin' ? 'nav-active' : ''} onClick={() => changePage('admin')}>관리자</button></nav></header>
    <main>{page === 'map' ? <><section className="hero"><span className="eyebrow">A CHEF’S FAVORITE, YOUR NEXT FAVORITE</span><h1>셰프가 아끼는 한 끼,<br/><em>당신의 다음 단골집.</em></h1><p>영상 속 이야기를 따라, 믿고 찾아갈 한 곳을 발견하세요.<br/>식당과 추천 근거를 직접 확인한 뒤 지도에 담습니다.</p><div className="hero-bottom"><span className="count">{restaurants.length.toString().padStart(2, '0')} <small>확인한 장소</small></span><span className="seal">✓ 영상 근거 확인 · 관리자 승인</span></div></section>
    {error && <p role="alert" className="error">{error} <button onClick={() => { void loadRestaurants().catch(()=>{}); }}>다시 시도</button></p>}
    <section className="explore"><div className="explore-list"><div className="section-heading"><h2>단골집 탐색</h2><span>{filtered.length}곳</span></div><label className="search"><span>⌕</span><input aria-label="식당, 지역, 셰프 검색" placeholder="식당, 지역, 셰프로 찾아보세요" value={query} onChange={event => setQuery(event.target.value)}/></label><div className="restaurants">{filtered.map(restaurant => <PlaceCard key={restaurant.id} restaurant={restaurant} selected={selected === restaurant.id} onSelect={select}/>)}{!filtered.length && <div className="empty"><span>⌖</span><h3>{loading ? '단골집을 불러오고 있습니다' : restaurants.length ? '검색 결과가 없습니다' : '첫 단골집을 기다리고 있어요'}</h3><p>{restaurants.length ? '다른 지역이나 셰프 이름으로 찾아보세요.' : '관리자가 영상 속 식당을 확인하고 승인하면 이곳에 표시됩니다.'}</p></div>}</div></div><KakaoMap restaurants={filtered} selected={selected} onSelect={select}/></section></> : <Admin onPublished={loadRestaurants} onGuard={onGuard}/>}</main><footer><span className="footer-brand">세단지</span><span>취향을 잇는, 셰프의 단골 지도.</span><span>영상 속 근거에서 시작하는 맛있는 발견</span></footer></>;
}
