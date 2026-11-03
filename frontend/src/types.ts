// Current backend Models records; planned admin APIs are not included here.
export type MentionType = 'REGULAR' | 'RECOMMENDED' | 'VISITED' | 'MENTIONED';
export type CandidateDisposition = 'OPEN' | 'RESOLVED' | 'DISMISSED';
export type MatchStatus = 'MATCHED' | 'REVIEW_REQUIRED' | 'NOT_FOUND';
export type VerificationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface Chef { id: number; name: string }
export interface Session { authenticated: boolean; username?: string }
export interface Video {
  id: string;
  title: string;
  channelTitle: string;
  publishedAt: string;
  description: string;
  extractionWarning: string;
}
export interface Source {
  mentionId: number;
  chefName: string;
  videoId: string;
  videoTitle: string;
  mentionType: MentionType;
  timestampSeconds: number | null;
}
export interface Place {
  id: string;
  name: string;
  roadAddress: string | null;
  address: string | null;
  longitude: number;
  latitude: number;
  placeUrl: string;
}
export interface Restaurant extends Omit<Place, 'id'> {
  id: number;
  kakaoPlaceId: string;
  mentions: Source[];
}
export interface Candidate {
  id: number;
  videoId: string;
  videoTitle: string;
  extractedName: string;
  extractedAddress: string | null;
  sourceLine: number | null;
  evidence: string;
  matchStatus: MatchStatus | null;
  matchingErrorCode: string | null;
  matchedAt: string | null;
  disposition: CandidateDisposition;
  resolutionNote: string | null;
}
export interface CandidateSearch {
  candidates: Place[];
  totalCount: number;
  complete: boolean;
  matchStatus: MatchStatus | null;
}
export interface Mention {
  id: number;
  candidateId: number | null;
  videoId: string;
  videoTitle: string;
  chefId: number;
  chefName: string;
  restaurantId: number;
  restaurantName: string;
  mentionType: MentionType | null;
  timestampSeconds: number | null;
  verificationStatus: VerificationStatus;
  evidence: string | null;
  reviewNote: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
}
