import { useCallback, useEffect, useRef, useState } from 'react';
import ExploreMap from './ExploreMap';
import ChefFilter from './ChefFilter';
import { useLocation } from './useLocation';
import { useExplore } from './useExplore';
import { usePublicDetail } from './usePublicDetail';
import { youtubeLink } from './contracts';
import type { Bounds, Query } from './contracts';
import './explore.css';

const labels = { REGULAR: '단골', RECOMMENDED: '추천', VISITED: '방문', MENTIONED: '단순 언급' };
function condition(q: Query) { return `경도 ${q.west.toFixed(4)}~${q.east.toFixed(4)}, 위도 ${q.south.toFixed(4)}~${q.north.toFixed(4)} · ${q.chefId === null ? '전체 셰프' : `셰프 #${q.chefId}`}`; }
interface Props { active: boolean; registerRefresh: (refresh: (isCurrent?: () => boolean) => Promise<void>) => void }
export default function ExplorePage({ active, registerRefresh }: Props) {
  const location = useLocation(); const area = useExplore(); const [mapMode, setMapMode] = useState<'live' | 'initial' | 'unavailable'>('live');
  const [revision, setRevision] = useState(0);
  const detail = usePublicDetail(area.removeUnavailable);
  const title = useRef<HTMLHeadingElement>(null); const returnFocus = useRef<HTMLElement | null>(null);
  const listTitle = useRef<HTMLHeadingElement>(null);
  const list = useRef<HTMLDivElement>(null); const scroll = useRef(0);
  function close() {
    const target = detail.isUnavailable ? listTitle.current : returnFocus.current;
    detail.close();
    requestAnimationFrame(() => {
      if (list.current) list.current.scrollTop = scroll.current;
      (target?.isConnected ? target : listTitle.current)?.focus();
    });
  }
  function changeLocation(action: () => void) {
    detail.close();
    area.beginLocationChange();
    setMapMode('live');
    action();
  }
  function select(id: number) {
    const entry = list.current?.querySelector<HTMLButtonElement>(`[data-restaurant-id="${id}"]`);
    returnFocus.current = entry ?? listTitle.current;
    if (detail.selectedId === null) scroll.current = list.current?.scrollTop ?? 0;
    detail.open(id);
  }
  const selectRef = useRef(select); selectRef.current = select;
  const onSelect = useCallback((id: number) => selectRef.current(id), []);
  const latest = useRef({ area, detail }); latest.current = { area, detail };
  const onReady = useCallback((bounds: Bounds, failed: boolean) => {
    setMapMode(failed ? 'initial' : 'live'); latest.current.area.setCameraBounds(failed ? null : bounds);
    void latest.current.area.request({ ...bounds, chefId: latest.current.area.chefId });
  }, []);
  useEffect(() => { if (detail.selectedId !== null) title.current?.focus(); }, [detail.selectedId]);
  useEffect(() => { if (!active) return; const escape = (e: KeyboardEvent) => { if (e.key === 'Escape' && latest.current.detail.selectedId !== null) close(); };
    window.addEventListener('keydown', escape); return () => window.removeEventListener('keydown', escape);
  });
  const refresh = useCallback(async (isCurrent?: () => boolean) => {
    latest.current.detail.close(); setRevision(n => n + 1); await latest.current.area.invalidate(isCurrent);
  }, []);
  useEffect(() => { registerRefresh(refresh); }, [registerRefresh, refresh]);
  const previousActive = useRef(active);
  useEffect(() => {
    const recheck = () => { if (!active || document.visibilityState === 'hidden') return;
      setRevision(n => n + 1); const current = latest.current.detail;
      const query = latest.current.area.requestedQuery;
      if (query) void latest.current.area.request(query);
      if (current.selectedId !== null && !current.isUnavailable) void current.load(current.selectedId, current.page);
    };
    if (active && !previousActive.current) recheck(); previousActive.current = active;
    document.addEventListener('visibilitychange', recheck); window.addEventListener('focus', recheck);
    return () => { document.removeEventListener('visibilitychange', recheck); window.removeEventListener('focus', recheck); };
  }, [active]);
  const result = area.lastSuccess;
  return <section className="public-explore" hidden={!active} aria-label="단골집 탐색">
    <div className="location-controls"><p>주변 식당을 찾기 위해 위치를 요청합니다. 위치는 저장하지 않습니다.</p>
      <button disabled={location.state === 'loading'} onClick={() => changeLocation(location.locate)}>내 위치로 시작</button><button onClick={() => changeLocation(location.fallback)}>서울 시청 기본 지역</button>
      <p role="status">{({ prompt: '위치 또는 기본 지역을 선택하세요.', loading: '위치 확인 중 (최대 8초)', located: '현재 위치 주변', denied: '위치 권한 거부 · 서울 시청 기본 지역', failed: '위치 확인 실패 · 재시도하거나 기본 지역을 선택하세요.', timeout: '위치 요청 시간 초과 · 재시도하거나 기본 지역을 선택하세요.', default: '서울 시청 기본 지역' })[location.state]}</p>
    </div>
    <div className="public-layout"><div className="public-panel">
      <h1 ref={listTitle} tabIndex={-1}>셰프의 단골 지도</h1><ChefFilter selected={area.chefId} revision={revision} onChange={id => { if (area.cameraBounds) detail.close(); area.changeChef(id); }}/>
      <div hidden={detail.selectedId !== null}>
        <button disabled={!area.cameraBounds} onClick={() => { detail.close(); area.search(); }}>이 지역에서 검색</button>
        {area.requestedQuery && <p>요청 조건: {condition(area.requestedQuery)}</p>}
        {area.loading && <p role="status">식당 조회 중</p>}{area.error && <p role="alert">{area.error} <button onClick={area.retry}>다시 시도</button></p>}
        {result && <><p>{result.returnedCount}곳 표시{result.truncated ? ' · 일부 결과입니다. 지도를 확대하세요.' : ''}</p>
          <p className="result-condition">{!area.requestedQuery || area.loading || area.error || mapMode === 'unavailable' ? '이전 성공 결과' : '조회 결과'} 조건: {condition(result.query)}</p></>}
        <div ref={list} className="public-results">{result?.items.map(r => <article key={r.id}><button data-restaurant-id={r.id} className="restaurant-title" onClick={() => select(r.id)}>{r.name}</button><p>{r.roadAddress || r.address}</p><p>{r.chefs.map(c => c.name).join(' · ')}{r.hasMoreChefs ? ` 외 ${r.chefCount - r.chefs.length}명` : ''}</p></article>)}
          {result?.items.length === 0 && !area.loading && !area.error && <p>검색 결과가 없습니다</p>}</div>
      </div>
      {detail.selectedId !== null && <section aria-label="식당 상세"><button onClick={close}>상세 닫기</button><h2 ref={title} tabIndex={-1}>{detail.detail?.restaurant.name ?? '식당 상세'}</h2>
        {detail.isUnavailable && <p role="alert">공개된 식당을 찾을 수 없습니다.</p>}
        {detail.loading && <p role="status">상세 확인 중</p>}{detail.error && <p role="alert">{detail.error}<button onClick={() => { if (detail.selectedId !== null) void detail.load(detail.selectedId, detail.page); }}>상세 다시 시도</button></p>}
        {detail.detail && <><p>{detail.detail.restaurant.roadAddress}</p><p>{detail.detail.restaurant.address}</p><a href={`https://place.map.kakao.com/${encodeURIComponent(detail.detail.restaurant.kakaoPlaceId)}`} target="_blank" rel="noreferrer">카카오맵에서 보기 ↗</a>
          <p>모든 공개 관계 · 선택한 셰프와 무관하게 표시합니다.</p>{detail.detail.sources.items.map(s => <article key={s.mentionId} className={s.chef.id === area.chefId ? 'selected-chef' : ''}><h3>{s.chef.name} · {labels[s.mentionType]}{s.chef.id === area.chefId ? ' · 선택 셰프' : ''}</h3><a href={youtubeLink(s.video.id, s.timestampSeconds)} target="_blank" rel="noreferrer">{s.video.title} ↗</a><p>{s.video.channelTitle} · {s.video.publishedAt.slice(0, 10)} · {s.timestampSeconds === null ? '시점 미확인' : `${s.timestampSeconds}초`}</p></article>)}
          <button disabled={detail.page === 0 || detail.loading} onClick={() => { if (detail.selectedId !== null) void detail.load(detail.selectedId, detail.page - 1); }}>이전 출처</button><span> {detail.page + 1}페이지 </span><button disabled={!detail.detail.sources.hasNext || detail.loading} onClick={() => { if (detail.selectedId !== null) void detail.load(detail.selectedId, detail.page + 1); }}>다음 출처</button></>}
      </section>}
    </div>
    {location.center ? <ExploreMap center={location.center} items={result?.items ?? []} onBounds={area.setCameraBounds} onReady={onReady} onFailure={() => { area.setCameraBounds(null); setMapMode('unavailable'); }} onSelect={onSelect}/> : <div className="public-map"><p>위치 또는 기본 지역을 선택하면 탐색을 시작합니다.</p></div>}
    </div>{mapMode === 'initial' && <p role="status">초기 지역 목록입니다. 실제 지도 영역이나 전체 식당 목록이 아닙니다.</p>}{mapMode === 'unavailable' && <p role="status">지도 연결 실패. 마지막 조회 결과와 공식 링크를 확인할 수 있습니다.</p>}
  </section>;
}
