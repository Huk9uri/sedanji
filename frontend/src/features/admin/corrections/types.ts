import type { Page } from '../restaurants/types';
export type CorrectionKind = 'MENTION' | 'RESTAURANT' | 'CHEF';
export interface CorrectionTarget { kind: CorrectionKind; id: number }
export type Snapshot = Record<string, unknown>;
export interface RevisionSummary { id: number; version: number; status: 'DRAFT' | 'APPLIED'; baseVersion: number; changeReason: string; updatedAt: string }
export interface Revision extends RevisionSummary {
  targetId: number; kind: CorrectionKind; baseSnapshot: Snapshot | null; currentSnapshot: Snapshot;
  proposedValues: Snapshot; changedFields: string[]; referenceSnapshot: Snapshot; currentReferences: Snapshot;
  baseConflict: boolean; referenceConflict: boolean; impactHash: string; impact: Page<Snapshot>; publicImpactCount: number;
  targetVersion?: number; restaurantId?: number; previousRestaurantId?: number; mentionId?: number;
}
export interface RevisionList extends Page<RevisionSummary> { target: Snapshot }
export interface ApprovalConfirmation {
  schemaVersion: number; expectedImpactHash: string; validatedImpactHash: string; targetVersion: number;
  revisionVersion: number; impactTotalElements: number; publicImpactCount: number;
}
export type AuditValue = Snapshot & { approvalConfirmation?: ApprovalConfirmation };
export interface HistoryEvent { id: number; actorId: number; action: string; reason: string | null; occurredAt: string; beforeValue: Snapshot | null; afterValue: AuditValue | null }
export const paths = {
  MENTION: { target: 'mentions', collection: 'corrections', revision: 'mention-corrections' },
  RESTAURANT: { target: 'restaurants', collection: 'revisions', revision: 'restaurant-revisions' },
  CHEF: { target: 'chefs', collection: 'revisions', revision: 'chef-revisions' },
};
