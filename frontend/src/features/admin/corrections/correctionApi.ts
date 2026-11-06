import { api } from '../../../api';
import type { Page } from '../restaurants/types';
import { paths } from './types';
import type { CorrectionTarget, HistoryEvent, Revision, RevisionList, Snapshot } from './types';
export const correctionApi = {
  list: (target: CorrectionTarget, page: number, signal?: AbortSignal) => api<RevisionList>(`/admin/${paths[target.kind].target}/${target.id}/${paths[target.kind].collection}?page=${page}&size=20`, { signal }),
  history: (target: CorrectionTarget, page: number, signal?: AbortSignal) => api<Page<HistoryEvent>>(`/admin/${paths[target.kind].target}/${target.id}/history?page=${page}&size=20`, { signal }),
  get: (target: CorrectionTarget, id: number, page = 0, signal?: AbortSignal) => api<Revision>(`/admin/${paths[target.kind].revision}/${id}?impactPage=${page}&impactSize=20`, { signal }),
  write: (target: CorrectionTarget, id: number | null, action: 'create' | 'patch' | 'rebase' | 'approve', body: Snapshot, key?: string) => {
    const path = id === null ? `/admin/${paths[target.kind].target}/${target.id}/${paths[target.kind].collection}` : `/admin/${paths[target.kind].revision}/${id}${action === 'patch' ? '' : `/${action}`}`;
    return api<Revision>(path, { method: action === 'patch' ? 'PATCH' : 'POST', body, headers: key ? { 'Idempotency-Key': key } : undefined });
  },
};
