import { useCallback, useEffect, useRef, useState } from 'react';
import { api, ApiError, errorMessage } from '../../api';
import type { Detail } from './contracts';

export function usePublicDetail(onUnavailable: (id: number) => void) {
  const [isUnavailable, setIsUnavailable] = useState(false);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<Detail | null>(null);
  const [page, setPage] = useState(0); const [loading, setLoading] = useState(false); const [error, setError] = useState('');
  const sequence = useRef(0); const controller = useRef<AbortController | null>(null);
  const unavailable = useRef(onUnavailable); unavailable.current = onUnavailable;
  const load = useCallback(async (id: number, sourcePage: number) => {
    controller.current?.abort(); const abort = new AbortController(); controller.current = abort;
    const g = ++sequence.current; setLoading(true); setError(''); setIsUnavailable(false);
    try { const result = await api<Detail>(`/public/restaurants/${id}?sourcePage=${sourcePage}&sourceSize=20`, { signal: abort.signal });
      if (g === sequence.current && !abort.signal.aborted) { setDetail(result); setPage(sourcePage); }
    } catch (e) {
      if (g !== sequence.current || abort.signal.aborted) return;
      if (e instanceof ApiError && e.status === 404 && e.code === 'RESOURCE_NOT_FOUND') {
        setDetail(null);
        setIsUnavailable(true);
        unavailable.current(id);
        return;
      }
      setError(errorMessage(e));
    } finally { if (g === sequence.current) setLoading(false); }
  }, []);
  const close = useCallback(() => { sequence.current++; controller.current?.abort(); setIsUnavailable(false); setSelectedId(null); setDetail(null); setError(''); setLoading(false); setPage(0); }, []);
  function open(id: number) { setSelectedId(id); setDetail(null); setPage(0); void load(id, 0); }
  useEffect(() => () => { sequence.current++; controller.current?.abort(); }, []);
  return { isUnavailable, selectedId, detail, page, loading, error, close, open, load };
}
