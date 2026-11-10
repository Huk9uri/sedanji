import { useCallback, useEffect, useRef, useState } from 'react';
import { api, errorMessage } from './api';
import type { Session } from './types';
import ExplorePage from './features/explore/ExplorePage';
import AdminWorkspace from './features/admin/AdminWorkspace';
import type { LeaveGuard } from './features/admin/AdminWorkspace';
import { draftUrl } from './features/admin/registration/useRegistration';

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
  const publicRefresh = useRef<(isCurrent?: () => boolean) => Promise<void>>(async () => {});
  const registerRefresh = useCallback((refresh: (isCurrent?: () => boolean) => Promise<void>) => { publicRefresh.current = refresh; }, []);
  const loadRestaurants = useCallback(async (isCurrent?: () => boolean) => { await publicRefresh.current(isCurrent); }, []);
  return <><header><a className="brand" href="#" onClick={event => { event.preventDefault(); changePage('map'); }}><span className="brand-icon">세</span><span>세단지<small>셰프의 단골 지도</small></span></a><nav><button className={page === 'map' ? 'nav-active' : ''} onClick={() => changePage('map')}>맛집 지도</button><button className={page === 'admin' ? 'nav-active' : ''} onClick={() => changePage('admin')}>관리자</button></nav></header>
    <main><ExplorePage active={page === 'map'} registerRefresh={registerRefresh}/>{page === 'admin' && <Admin onPublished={loadRestaurants} onGuard={onGuard}/>}</main><footer><span className="footer-brand">세단지</span><span>취향을 잇는, 셰프의 단골 지도.</span><span>영상 속 근거에서 시작하는 맛있는 발견</span></footer></>;
}
