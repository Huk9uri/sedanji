import { useCallback, useEffect, useRef, useState } from 'react';
import { api, errorMessage } from '../../api';
import { areaPath } from './contracts';
import type { Area, Bounds, Query } from './contracts';

export function useExplore() {
  const [cameraBounds, setCameraBounds] = useState<Bounds | null>(null);
  const [requestedQuery, setRequestedQuery] = useState<Query | null>(null);
  const [lastSuccess, setLastSuccess] = useState<Area | null>(null);
  const [chefId, setChefId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false); const [error, setError] = useState('');
  const sequence = useRef(0); const controller = useRef<AbortController | null>(null);
  const lastQuery = useRef<Query | null>(null);
  const request = useCallback(async (q: Query, isCurrent?: () => boolean, propagateFailure = false) => {
    controller.current?.abort(); const abort = new AbortController(); controller.current = abort;
    const g = ++sequence.current; lastQuery.current = q; setRequestedQuery(q); setLoading(true); setError('');
    try { const result = await api<Area>(areaPath(q), { signal: abort.signal });
      if (g === sequence.current && (!isCurrent || isCurrent())) setLastSuccess(result);
    } catch (e) { if (g === sequence.current && !abort.signal.aborted && (!isCurrent || isCurrent())) {
      setError(errorMessage(e)); if (propagateFailure) throw e;
    } }
    finally { if (g === sequence.current) {
      if (isCurrent && !isCurrent()) setError('인증 상태가 변경됐습니다. 공개 목록을 다시 조회하세요.');
      setLoading(false);
    } }
  }, []);
  const invalidate = useCallback(async (isCurrent?: () => boolean) => {
    controller.current?.abort(); sequence.current++; setLastSuccess(null); setError('');
    if (lastQuery.current) await request(lastQuery.current, isCurrent, true);
  }, [request]);
  const removeUnavailable = useCallback((id: number) => {
    // A list snapshot started before the confirmed 404 must not restore its pin.
    controller.current?.abort(); sequence.current++; setLoading(false);
    setLastSuccess(old => old ? { ...old, items: old.items.filter(r => r.id !== id), returnedCount: old.items.filter(r => r.id !== id).length } : old);
  }, []);
  useEffect(() => () => { sequence.current++; controller.current?.abort(); }, []);
  function search() { if (cameraBounds) void request({ ...cameraBounds, chefId }); }
  function retry() { if (lastQuery.current) void request(lastQuery.current); }
  function changeChef(id: number | null) { setChefId(id); if (cameraBounds) void request({ ...cameraBounds, chefId: id }); }
  return { cameraBounds, setCameraBounds, requestedQuery, lastSuccess, chefId, loading, error, request, search, retry, changeChef, invalidate, removeUnavailable };
}
