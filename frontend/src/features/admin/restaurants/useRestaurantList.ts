import { useEffect, useRef, useState } from 'react';
import { restaurantApi } from './restaurantApi';
import type { ListConditions, Page, RestaurantItem } from './types';

export function useRestaurantList(conditions: ListConditions, setConditions: (next: ListConditions) => void,
  enabled: boolean, authGeneration: number, revision: number, onFailure: (e: unknown) => void) {
  const [state, setState] = useState<{ key: string; data?: Page<RestaurantItem>; error?: unknown; loading: boolean }>({ key: '', loading: false });
  const [retry, setRetry] = useState(0);
  const generation = useRef(0);
  const failure = useRef(onFailure); failure.current = onFailure;
  const correction = useRef({ scope: '', used: false });
  const key = JSON.stringify([enabled, authGeneration, conditions, revision, retry]);
  const correctionScope = JSON.stringify([authGeneration, conditions.query, conditions.publication, conditions.needsSourceReview, conditions.size, revision, retry]);
  useEffect(() => {
    const request = ++generation.current, controller = new AbortController();
    if (!enabled) { setState({ key, loading: false }); return () => controller.abort(); }
    if (correction.current.scope !== correctionScope) correction.current = { scope: correctionScope, used: false };
    setState({ key, loading: true });
    async function load() {
      try {
        let query = conditions;
        const result = await restaurantApi.list(query, controller.signal);
        if (controller.signal.aborted || request !== generation.current) return;
        if (!result.items.length && query.page > 0 && !correction.current.used) {
          // One correction per response cycle, including the empty-database case.
          const last = Math.max(0, Math.ceil(result.totalElements / query.size) - 1);
          if (last !== query.page) {
            correction.current.used = true;
            query = { ...query, page: last };
            setConditions(query);
            return;
          }
        }
        setState({ key, loading: false, data: result });
      } catch(e) {
        if (controller.signal.aborted || request !== generation.current) return;
        setState({ key, loading: false, error: e }); failure.current(e);
      }
    }
    void load();
    return () => { generation.current++; controller.abort(); };
  }, [key]);
  const current = state.key === key && enabled;
  return { data: current ? state.data : undefined, error: current ? state.error : undefined,
    loading: enabled && (!current || state.loading), refresh: () => setRetry(n => n + 1) };
}
