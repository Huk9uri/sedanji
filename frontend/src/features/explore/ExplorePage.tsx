import { useCallback, useEffect, useRef, useState } from 'react';
import ExploreMap from './ExploreMap';
import type { ExploreMapHandle } from './ExploreMap';
import ChefFilter from './ChefFilter';
import { useLocation } from './useLocation';
import { useExplore } from './useExplore';
import { usePublicDetail } from './usePublicDetail';
import { defaultCenter, youtubeLink } from './contracts';
import type { Bounds } from './contracts';
import './explore.css';

const labels = { REGULAR: '단골', RECOMMENDED: '추천', VISITED: '방문', MENTIONED: '단순 언급' };
function sourceLabel(seconds: number | null) {
  if (seconds === null) return 'YouTube에서 보기';
  const minutes = Math.floor(seconds / 60);
  const remainder = String(seconds % 60).padStart(2, '0');
  const time = minutes < 60 ? `${minutes}:${remainder}` : `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, '0')}:${remainder}`;
  return `${time}부터 보기`;
}
interface Props { active: boolean; registerRefresh: (refresh: (isCurrent?: () => boolean) => Promise<void>) => void }
export default function ExplorePage({ active, registerRefresh }: Props) {
  const location = useLocation(true); const area = useExplore(); const [mapMode, setMapMode] = useState<'live' | 'initial' | 'unavailable'>('live');
  const [chefNames, setChefNames] = useState<Record<number, string>>({});
  const [revision, setRevision] = useState(0);
  const detail = usePublicDetail(area.removeUnavailable);
  const title = useRef<HTMLHeadingElement>(null); const returnFocus = useRef<HTMLElement | null>(null);
  const map = useRef<ExploreMapHandle>(null);
  const listTitle = useRef<HTMLHeadingElement>(null);
  const list = useRef<HTMLDivElement>(null); const scroll = useRef(0);
  function close() {
    const target = detail.isUnavailable ? listTitle.current : returnFocus.current;
    detail.close();
    requestAnimationFrame(() => {
      if (list.current) list.current.scrollTop = scroll.current;
      const focusTarget = target?.isConnected ? target : listTitle.current;
      const desktop = list.current?.scrollHeight !== undefined && list.current.scrollHeight > list.current.clientHeight;
      focusTarget?.focus({ preventScroll: desktop });
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
  const hasLocation = useRef(false); hasLocation.current = location.center !== null;
  const onReady = useCallback((bounds: Bounds, failed: boolean) => {
    if (!hasLocation.current) return;
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
  const resultChefId = result?.query.chefId ?? null;
  const chefLabel = resultChefId === null ? '전체 셰프' : chefNames[resultChefId] ?? '선택한 셰프';
  const cameraMoved = result && area.cameraBounds && (['west', 'south', 'east', 'north'] as const)
    .some(edge => Math.abs(result.query[edge] - area.cameraBounds![edge]) > 0.000001);
  const showingPrevious = cameraMoved || area.loading || !!area.error || !area.requestedQuery || mapMode === 'unavailable';
  function changeChef(id: number | null, name: string | null = null) {
    if (id !== null && name) setChefNames(names => ({ ...names, [id]: name }));
    detail.close();
    area.changeChef(id, mapMode === 'initial' ? result?.query : undefined);
  }

  function searchCurrentMap() {
    const bounds = map.current?.readBounds();
    if (!bounds) return;
    detail.close();
    area.setCameraBounds(bounds);
    void area.request({ ...bounds, chefId: area.chefId });
  }

  return <section className="public-explore" hidden={!active} aria-label="단골집 탐색">
    <div className="public-layout">
      <div className="public-map-search location-controls">
        <button disabled={location.state === 'loading'} onClick={() => changeLocation(location.locate)}>내 위치</button>
        <button disabled={!area.cameraBounds} onClick={searchCurrentMap}>이 지역에서 검색</button>
        {(['failed', 'timeout'] as string[]).includes(location.state) && <button onClick={() => changeLocation(location.fallback)}>서울 시청 기본 지역</button>}
        <p role="status">{({ prompt: '', loading: '위치 확인 중 (최대 8초)', located: '현재 위치 주변', denied: '위치 권한 거부 · 서울 시청 기본 지역', failed: '위치 확인 실패 · 재시도하거나 기본 지역을 선택하세요.', timeout: '위치 요청 시간 초과 · 재시도하거나 기본 지역을 선택하세요.', default: '서울 시청 주변' })[location.state]}</p>
      </div>
      <div className="public-panel">
        <div className="public-panel-top">
          <h1 ref={listTitle} tabIndex={-1}>셰프의 단골 지도</h1>
          <ChefFilter selected={area.chefId} revision={revision} onChange={changeChef}/>
          {result && <p className="result-condition">{showingPrevious ? '이전 검색 결과' : '이 지도 영역'} · {chefLabel} · {result.returnedCount}곳</p>}
          {result?.truncated && <p>일부 결과입니다. 지도를 확대하세요.</p>}
          {area.loading && <p role="status">식당 조회 중</p>}
          {area.error && <p role="alert">{area.error} <button onClick={area.retry}>다시 시도</button></p>}
        </div>
        <div ref={list} className="public-results">{result?.items.map(r => <article key={r.id} className={r.id === detail.selectedId ? 'public-result-selected' : undefined}><button aria-pressed={r.id === detail.selectedId} data-restaurant-id={r.id} className="restaurant-title" onClick={() => select(r.id)}>{r.name}</button><p>{r.roadAddress || r.address}</p><p>{r.chefs.map(c => c.name).join(' · ')}{r.hasMoreChefs ? ` 외 ${r.chefCount - r.chefs.length}명` : ''}</p></article>)}
          {result?.items.length === 0 && !area.loading && !area.error && <div className="public-empty"><p>검색 결과가 없습니다</p>
            {resultChefId !== null && area.chefId !== null
              ? <button onClick={() => changeChef(null)}>셰프 필터 해제</button>
              : <p>지도를 넓히거나 이동한 뒤 이 지역에서 검색해 보세요.</p>}</div>}</div>
      </div>
      {detail.selectedId !== null && <section className="public-detail" aria-label="식당 상세"><button onClick={close}>상세 닫기</button><h2 ref={title} tabIndex={-1}>{detail.detail?.restaurant.name ?? '식당 상세'}</h2>
        {detail.isUnavailable && <p role="alert">공개된 식당을 찾을 수 없습니다.</p>}
        {detail.loading && <p role="status">상세 확인 중</p>}{detail.error && <p role="alert">{detail.error}<button onClick={() => { if (detail.selectedId !== null) void detail.load(detail.selectedId, detail.page); }}>상세 다시 시도</button></p>}
        {detail.detail && <>{(detail.detail.restaurant.roadAddress || detail.detail.restaurant.address) && <p>{detail.detail.restaurant.roadAddress || detail.detail.restaurant.address}</p>}{detail.detail.restaurant.kakaoPlaceId.trim() && <a href={`https://place.map.kakao.com/${encodeURIComponent(detail.detail.restaurant.kakaoPlaceId)}`} target="_blank" rel="noreferrer">카카오맵에서 보기 ↗</a>}
          <h3>셰프가 소개한 출처</h3>{detail.detail.sources.items.map(s => <article key={s.mentionId} className={s.chef.id === area.chefId ? 'selected-chef' : ''}><h3>{s.chef.name} · {labels[s.mentionType]}{s.chef.id === area.chefId ? ' · 선택 셰프' : ''}</h3><p>{s.video.title}</p><a href={youtubeLink(s.video.id, s.timestampSeconds)} target="_blank" rel="noreferrer">{sourceLabel(s.timestampSeconds)}</a><p>{s.video.channelTitle} · {s.video.publishedAt.slice(0, 10)}</p></article>)}
          {(detail.page > 0 || detail.detail.sources.hasNext) && <div><button disabled={detail.page === 0 || detail.loading} onClick={() => { if (detail.selectedId !== null) void detail.load(detail.selectedId, detail.page - 1); }}>이전 출처</button><span> {detail.page + 1}페이지 </span><button disabled={!detail.detail.sources.hasNext || detail.loading} onClick={() => { if (detail.selectedId !== null) void detail.load(detail.selectedId, detail.page + 1); }}>다음 출처</button></div>}</>}
      </section>}
    <ExploreMap ref={map} expanded={location.state === 'default' || location.state === 'denied'} key={location.center ? 'explore' : 'preview'} center={location.center ?? defaultCenter} items={result?.items ?? []} onBounds={bounds => { if (hasLocation.current) area.setCameraBounds(bounds); }} onReady={onReady} onFailure={() => { area.setCameraBounds(null); setMapMode('unavailable'); }} onSelect={onSelect}/>
    </div>{mapMode === 'initial' && <p role="status">초기 지역 목록입니다. 실제 지도 영역이나 전체 식당 목록이 아닙니다.</p>}{mapMode === 'unavailable' && <p role="status">지도 연결 실패. 마지막 조회 결과와 공식 링크를 확인할 수 있습니다.</p>}
  </section>;
}
