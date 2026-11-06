import { useEffect, useRef, useState } from 'react';
import { restaurantApi } from './restaurantApi';
import type { Draft, Page, RestaurantDetail } from './types';

interface Read<T> { key: string; data?: T; error?: unknown; loading: boolean }
function useRead<T>(key: string, enabled: boolean, load: (signal: AbortSignal) => Promise<T>, onFailure: (e: unknown) => void) {
  const [state, setState] = useState<Read<T>>({ key: '', loading: false });
  const [retry, setRetry] = useState(0);
  const sequence = useRef(0);
  const callback = useRef(onFailure); callback.current = onFailure;
  const fullKey = key + ':' + retry;
  useEffect(() => {
    const request = ++sequence.current, controller = new AbortController();
    setState({ key: fullKey, loading: enabled });
    if (enabled) void load(controller.signal).then(data => {
      if (!controller.signal.aborted && request === sequence.current) setState({ key: fullKey, data, loading: false });
    }).catch(e => {
      if (controller.signal.aborted || request !== sequence.current) return;
      setState({ key: fullKey, error: e, loading: false }); callback.current(e);
    });
    return () => { sequence.current++; controller.abort(); };
  }, [fullKey, enabled]);
  const current = enabled && state.key === fullKey;
  return { data: current ? state.data : undefined, error: current ? state.error : undefined,
    loading: enabled && (!current || state.loading), refresh: () => setRetry(n => n + 1) };
}
export function useRestaurantDetail(id: number | null, mentionPage: number, draftPage: number, enabled: boolean,
  authGeneration: number, revision: number, onFailure: (e: unknown) => void) {
  const base = JSON.stringify([id, authGeneration, revision]);
  const detail = useRead<RestaurantDetail>(base + ':m:' + mentionPage, enabled && id !== null,
    signal => restaurantApi.detail(id!, mentionPage, 20, signal), onFailure);
  const drafts = useRead<Page<Draft>>(base + ':d:' + draftPage, enabled && id !== null,
    signal => restaurantApi.drafts(id!, draftPage, 20, signal), onFailure);
  return { detail, drafts };
}
