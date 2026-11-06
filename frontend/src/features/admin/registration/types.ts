import type { Chef, MentionType, Place, Video } from '../../../types';
export type { Chef, MentionType, Place, Video };
export interface Page<T> { items: T[]; totalElements: number; page: number; size: number; hasNext: boolean }
export interface Selection { placeSelectionToken: string; place: Place }
export interface PlaceSearch { candidates: Selection[]; totalCount: number; complete: boolean }
export interface Draft {
  id: number; kind: 'CREATE'; status: 'DRAFT' | 'APPLIED'; version: number;
  restaurant: Omit<Place, 'id'> & { id: number; kakaoPlaceId: string };
  chefId: number | null; videoId: string | null; mentionType: MentionType | null;
  timestampSeconds: number | null; verificationNote: string | null;
  candidateId: number | null; appliedMentionId: number | null;
  createdBy: number; updatedBy: number; createdAt: string; updatedAt: string;
}
export interface Fields { chefId: number | null; chefSearch: string; video: Video | null; videoUrl: string; mentionType: MentionType | ''; timestamp: string; note: string }
export interface Values { chefId: number | null; videoId: string | null; mentionType: MentionType | null; timestampSeconds: number | null; verificationNote: string | null }
export type PlaceIdentity = { restaurantId: number; placeSelectionToken?: never } | { restaurantId?: never; placeSelectionToken: string };
export type CreateDraft = Values & PlaceIdentity;
export interface DirectResult { mentionId: number; restaurantId: number; version: number; verificationStatus: string; publicationStatus: string }
export interface ApprovalResult { draftId: number; draftVersion: number; draftStatus: 'APPLIED'; mentionId: number; restaurantId: number; mentionVersion: number; verificationStatus: string; publicationStatus: string }
export interface RestaurantChoice { id: number; name: string; roadAddress: string | null; address: string | null }
