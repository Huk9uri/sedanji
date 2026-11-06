import { api } from '../../../api';
import type { Draft, ListConditions, Page, RestaurantDetail, RestaurantItem } from './types';
export const restaurantApi = {
  list: (conditions: ListConditions, signal?: AbortSignal) => api<Page<RestaurantItem>>('/admin/restaurants?' + new URLSearchParams({
    query: conditions.query, publication: conditions.publication, needsSourceReview: String(conditions.needsSourceReview),
    page: String(conditions.page), size: String(conditions.size),
  }), { signal }),
  detail: (id: number, page = 0, size = 20, signal?: AbortSignal) => api<RestaurantDetail>(`/admin/restaurants/${id}?` + new URLSearchParams({ mentionPage: String(page), mentionSize: String(size) }), { signal }),
  drafts: (id: number, page = 0, size = 20, signal?: AbortSignal) => api<Page<Draft>>(`/admin/restaurants/${id}/mention-drafts?` + new URLSearchParams({ status: 'DRAFT', page: String(page), size: String(size) }), { signal }),
};
