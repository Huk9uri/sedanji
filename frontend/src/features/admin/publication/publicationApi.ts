import { api } from '../../../api';
import type { MentionType } from '../../../types';
import type { HistoryEvent } from '../corrections/types';
import type { Page } from '../restaurants/types';

export type PublicationAction = 'suspend' | 'restore';
export interface PublicationContext {
  mention: {
    id: number; version: number; chefId: number; chefName: string; restaurantId: number; restaurantName: string;
    videoId: string; videoTitle: string; channelTitle: string; mentionType: MentionType | null;
    timestampSeconds: number | null; verificationStatus: string; publicationStatus: string;
    evidence: string | null; verificationNote: string | null; candidateId: number | null;
  };
  references: { restaurant: { roadAddress: string | null; address: string | null; kakaoPlaceId: string; placeUrl: string } };
  contextHash: string; latestControl: HistoryEvent | null; lastSuspendReason: string | null;
}
export interface PublicationBody {
  expectedVersion: number; reason: string; expectedContextHash?: string; evidenceRechecked?: true; recheckNote?: string;
}
export interface PublicationResult {
  action: 'SUSPEND_MENTION' | 'RESTORE_MENTION'; mentionId: number; restaurantId: number;
  previousVersion: number; version: number; verificationStatus: 'APPROVED'; publicationStatus: 'PUBLISHED' | 'SUSPENDED';
  auditEventId: number; occurredAt: string;
}
export const publicationApi = {
  get: (id: number, signal?: AbortSignal) => api<PublicationContext>(`/admin/mentions/${id}/publication`, { signal }),
  history: (id: number, page: number, signal?: AbortSignal) => api<Page<HistoryEvent>>(`/admin/mentions/${id}/history?page=${page}&size=20`, { signal }),
  write: (id: number, action: PublicationAction, body: PublicationBody, key: string) =>
    api<PublicationResult>(`/admin/mentions/${id}/${action}`, { method: 'POST', body, headers: { 'Idempotency-Key': key } }),
};
