export interface Bounds { west: number; south: number; east: number; north: number }
export interface Query extends Bounds { chefId: number | null }
export interface Chef { id: number; name: string }
export interface Item { id: number; name: string; latitude: number; longitude: number; roadAddress: string | null; address: string | null; chefs: Chef[]; chefCount: number; hasMoreChefs: boolean }
export interface Area { items: Item[]; query: Query; returnedCount: number; limit: number; truncated: boolean; totalCount: null }
export interface Page<T> { items: T[]; totalElements: number; page: number; size: number; hasNext: boolean }
export interface ChefItem extends Chef { restaurantCount: number }
export interface Source { mentionId: number; chef: Chef; mentionType: 'REGULAR' | 'RECOMMENDED' | 'VISITED' | 'MENTIONED'; video: { id: string; title: string; channelTitle: string; publishedAt: string }; timestampSeconds: number | null }
export interface Detail { id: number; name: string; kakaoPlaceId: string; roadAddress: string | null; address: string | null; latitude: number; longitude: number; placeUrl: string; sources: Page<Source> }
export const defaultCenter = { latitude: 37.5665, longitude: 126.9780 };
// Explicit fallback rectangle, never described as SDK camera bounds.
export function initialBounds(center: typeof defaultCenter): Bounds {
  return { west: center.longitude - .0113, east: center.longitude + .0113, south: center.latitude - .009, north: center.latitude + .009 };
}
export function areaPath(q: Query): string {
  const p = new URLSearchParams({ west: String(q.west), south: String(q.south), east: String(q.east), north: String(q.north) });
  if (q.chefId !== null) p.set('chefId', String(q.chefId));
  return '/public/restaurants?' + p;
}
export function youtubeLink(id: string, seconds: number | null): string {
  return `https://www.youtube.com/watch?v=${encodeURIComponent(id)}${seconds === null ? '' : `&t=${seconds}s`}`;
}
