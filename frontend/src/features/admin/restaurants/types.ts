import type { MentionType } from '../../../types';
import type { Draft, Page } from '../registration/types';
export type { Draft, Page };
export type PublicationFilter = 'ALL' | 'PUBLISHED' | 'NO_PUBLIC_RELATION';
export interface ListConditions { query: string; publication: PublicationFilter; needsSourceReview: boolean; page: number; size: number }
export const initialConditions = (): ListConditions => ({ query: '', publication: 'ALL', needsSourceReview: false, page: 0, size: 20 });
export interface Summary {
  relationCount: number; publicRelationCount: number; hasPublicRelation: boolean;
  needsSourceReview: boolean | null; sourceCount: number; uncheckedSourceCount: number; reviewRequiredSourceCount: number;
}
export interface RestaurantItem extends Summary {
  id: number; name: string; roadAddress: string | null; address: string | null; createdAt: string;
  chefCount: number; chefs: { id: number; name: string; relationCount: number; publicRelationCount: number }[]; hasMoreChefs: boolean;
}
export interface RestaurantPlace {
  id: number; kakaoPlaceId: string; name: string; roadAddress: string | null; address: string | null;
  longitude: number; latitude: number; placeUrl: string; version: number; createdAt: string;
}
export interface AdminMention {
  id: number; version: number; chef: { id: number; name: string };
  video: { id: string; title: string; channelTitle: string; publishedAt: string;
    sourceCheckStatus: 'UNCHECKED' | 'AVAILABLE' | 'REVIEW_REQUIRED'; sourceCheckedAt: string | null; sourceCheckReason: 'UNAVAILABLE' | null };
  mentionType: MentionType | null; timestampSeconds: number | null;
  verificationStatus: 'PENDING' | 'APPROVED' | 'REJECTED'; publicationStatus: 'PUBLISHED' | 'SUSPENDED';
  verificationNote: string | null; reviewedAt: string | null;
}
export interface RestaurantDetail { restaurant: RestaurantPlace; summary: Summary; mentions: Page<AdminMention> }
