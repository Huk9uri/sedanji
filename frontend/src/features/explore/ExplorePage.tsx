import { useCallback, useEffect, useRef, useState } from 'react';
import ExploreMap from './ExploreMap';
import type { ExploreMapHandle } from './ExploreMap';
import MapSearchControls from './components/MapSearchControls';
import RestaurantResults from './components/RestaurantResults';
import RestaurantDetailPanel from './components/RestaurantDetailPanel';
import { useLocation } from './useLocation';
import { useExplore } from './useExplore';
import { usePublicDetail } from './usePublicDetail';
import { defaultCenter } from './contracts';
import type { Bounds } from './contracts';
import './explore.css';

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
  const showingPrevious = !!cameraMoved || area.loading || !!area.error || !area.requestedQuery || mapMode === 'unavailable';
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
      <MapSearchControls
        state={location.state}
        canSearch={area.cameraBounds !== null}
        onLocate={() => changeLocation(location.locate)}
        onSearch={searchCurrentMap}
        onFallback={() => changeLocation(location.fallback)}
      />
      <RestaurantResults
        titleRef={listTitle}
        listRef={list}
        result={result}
        chefId={area.chefId}
        chefLabel={chefLabel}
        revision={revision}
        selectedId={detail.selectedId}
        showingPrevious={showingPrevious}
        loading={area.loading}
        error={area.error}
        onChefChange={changeChef}
        onSelect={onSelect}
        onRetry={area.retry}
      />
      {detail.selectedId !== null && <RestaurantDetailPanel
        titleRef={title}
        detail={detail.detail}
        chefId={area.chefId}
        unavailable={detail.isUnavailable}
        loading={detail.loading}
        error={detail.error}
        page={detail.page}
        onClose={close}
        onPageChange={page => {
          if (detail.selectedId !== null) void detail.load(detail.selectedId, page);
        }}
      />}
    <ExploreMap ref={map} expanded={location.state === 'default' || location.state === 'denied'} key={location.center ? 'explore' : 'preview'} center={location.center ?? defaultCenter} items={result?.items ?? []} onBounds={bounds => { if (hasLocation.current) area.setCameraBounds(bounds); }} onReady={onReady} onFailure={() => { area.setCameraBounds(null); setMapMode('unavailable'); }} onSelect={onSelect}/>
    </div>{mapMode === 'initial' && <p role="status">초기 지역 목록입니다. 실제 지도 영역이나 전체 식당 목록이 아닙니다.</p>}{mapMode === 'unavailable' && <p role="status">지도 연결 실패. 마지막 조회 결과와 공식 링크를 확인할 수 있습니다.</p>}
  </section>;
}
