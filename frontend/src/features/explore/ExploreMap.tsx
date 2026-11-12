import { useEffect, useRef, useState } from 'react';
import { loadSdk } from '../../kakaoSdk';
import type { KakaoMap, KakaoMarker } from '../../kakao';
import type { Bounds, Item } from './contracts';
import { initialBounds } from './contracts';

interface Props { center: { latitude: number; longitude: number }; items: Item[]; onBounds: (bounds: Bounds) => void; onReady: (bounds: Bounds, failed: boolean) => void; onFailure: () => void; onSelect: (id: number) => void }
export default function ExploreMap({ center, items, onBounds, onReady, onFailure, onSelect }: Props) {
  const node = useRef<HTMLDivElement>(null); const map = useRef<KakaoMap | null>(null);
  const [ready, setReady] = useState(false); const [error, setError] = useState('');
  const [mapVersion, setMapVersion] = useState(0);
  const failMap = useRef<() => void>(() => {});
  const [overlap, setOverlap] = useState<Item[]>([]);
  const callbacks = useRef({ onBounds, onReady, onFailure, onSelect }); callbacks.current = { onBounds, onReady, onFailure, onSelect };
  useEffect(() => {
    let active = true;
    let initialized = false;
    let failed = false;
    let removeIdle = () => {};
    let observer: ResizeObserver | undefined;
    const dispose = () => {
      const remove = removeIdle;
      removeIdle = () => {};
      observer?.disconnect();
      observer = undefined;
      map.current = null;
      remove();
    };
    setReady(false);
    setOverlap([]);
    setError('');
    const key = import.meta.env.VITE_KAKAO_JAVASCRIPT_KEY;
    const fail = () => {
      if (!active || failed) return;
      failed = true;
      dispose();
      setReady(false);
      setOverlap([]);
      setError(initialized ? '지도 연결 실패 · 마지막 조회 결과' : '지도 연결 실패 · 초기 지역 목록');
      if (initialized) callbacks.current.onFailure();
      else callbacks.current.onReady(initialBounds(center), true);
    };
    failMap.current = fail;
    if (!key && !window.kakao?.maps?.Map) {
      fail();
      return;
    }
    void loadSdk(key ?? '').then(kakao => {
      if (!active || !node.current) return;
      const instance = new kakao.maps.Map(node.current, { center: new kakao.maps.LatLng(center.latitude, center.longitude), level: 5 });
      map.current = instance;
      const initial = initialBounds(center); const initialViewport = new kakao.maps.LatLngBounds();
      initialViewport.extend(new kakao.maps.LatLng(initial.south, initial.west));
      initialViewport.extend(new kakao.maps.LatLng(initial.north, initial.east));
      instance.setBounds(initialViewport);
      const bounds = (): Bounds => {
        const b = instance.getBounds?.(); const sw = b?.getSouthWest?.(); const ne = b?.getNorthEast?.();
        if (!sw?.getLng || !sw.getLat || !ne?.getLng || !ne.getLat) throw new Error('지도 영역 확인 실패');
        const area = { west: sw.getLng(), south: sw.getLat(), east: ne.getLng(), north: ne.getLat() };
        if (!Object.values(area).every(Number.isFinite) || area.west >= area.east || area.south >= area.north) {
          throw new Error('지도 영역 확인 실패');
        }
        return area;
      };
      const updateBounds = () => {
        if (!active || !initialized || failed || node.current?.closest('section')?.hidden) return;
        try { callbacks.current.onBounds(bounds()); } catch { fail(); }
      };
      removeIdle = () => kakao.maps.event.removeListener(instance, 'idle', updateBounds);
      kakao.maps.event.addListener(instance, 'idle', updateBounds);
      observer = new ResizeObserver(() => {
        if (!active || !initialized || failed || node.current?.closest('section')?.hidden) return;
        try {
          instance.relayout?.();
          updateBounds();
        } catch {
          fail();
        }
      });
      observer.observe(node.current);
      const actualBounds = bounds(); initialized = true;
      callbacks.current.onReady(actualBounds, false); setMapVersion(n => n + 1); setReady(true);
    }).catch(fail);
    return () => { active = false; dispose(); };
  }, [center]);
  useEffect(() => {
    if (!ready || !map.current || !window.kakao) return;
    setOverlap([]);
    const kakao = window.kakao; const markers: { marker: KakaoMarker; click: () => void }[] = [];
    const groups = new Map<string, Item[]>();
    for (const item of items) { const k = `${item.latitude},${item.longitude}`; groups.set(k, [...(groups.get(k) ?? []), item]); }
    const disposeMarkers = () => {
      for (const { marker, click } of markers) {
        try {
          kakao.maps.event.removeListener(marker, 'click', click);
        } finally {
          marker.setMap(null);
        }
      }
      markers.length = 0;
    };
    try {
      for (const group of groups.values()) {
        const r = group[0]; const marker = new kakao.maps.Marker({ map: map.current, position: new kakao.maps.LatLng(r.latitude, r.longitude), title: group.length > 1 ? `${group.length}개 식당` : r.name });
        const click = () => { if (group.length > 1) setOverlap(group); else callbacks.current.onSelect(r.id); };
        markers.push({ marker, click }); kakao.maps.event.addListener(marker, 'click', click);
      }
    } catch {
      disposeMarkers();
      failMap.current();
    }
    return disposeMarkers;
  }, [items, ready, mapVersion]);
  return <div className="public-map"><div ref={node} className="public-map-canvas" aria-label="공개 식당 지도"/>
    {error && <p role="status" className="map-failure">{error}. 목록에서 식당과 공식 링크를 확인하세요.</p>}
    {overlap.length > 0 && <div className="overlap" aria-label="겹친 핀 선택"><button onClick={() => setOverlap([])}>선택 목록 닫기</button>{overlap.map(r => <button key={r.id} onClick={() => { setOverlap([]); callbacks.current.onSelect(r.id); }}>{r.name}</button>)}</div>}
  </div>;
}
