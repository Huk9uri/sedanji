import { api } from '../../../api';
import type { ApprovalResult, Chef, CreateDraft, DirectResult, Draft, Page, PlaceSearch, RestaurantChoice, Values, Video } from './types';
export const registrationApi = {
  places: (query: string) => api<PlaceSearch>('/admin/places?' + new URLSearchParams({ query })),
  chefs: () => api<Chef[]>('/admin/chefs'),
  createChef: (name: string) => api<Chef>('/admin/chefs', { method: 'POST', body: { name } }),
  metadata: (url: string) => api<Video>('/admin/videos/metadata', { method: 'POST', body: { url } }),
  videos: () => api<Video[]>('/admin/videos'),
  draft: (id: number) => api<Draft>(`/admin/mention-drafts/${id}`),
  restaurants: (query: string, page = 0) => api<Page<RestaurantChoice>>('/admin/restaurants?' + new URLSearchParams({ query, page: String(page), size: '20' })),
  drafts: (id: number, page = 0) => api<Page<Draft>>(`/admin/restaurants/${id}/mention-drafts?` + new URLSearchParams({ status: 'DRAFT', page: String(page), size: '20' })),
  createDraft: (body: CreateDraft, key: string) => api<Draft>('/admin/mention-drafts', { method: 'POST', body, headers: { 'Idempotency-Key': key } }),
  patch: (id: number, body: Partial<Values> & { expectedVersion: number; placeSelectionToken?: string }) => api<Draft>(`/admin/mention-drafts/${id}`, { method: 'PATCH', body }),
  direct: (body: Values & { placeSelectionToken: string }, key: string) => api<DirectResult>('/admin/mentions', { method: 'POST', body, headers: { 'Idempotency-Key': key } }),
  approve: (id: number, version: number, key: string) => api<ApprovalResult>(`/admin/mention-drafts/${id}/approve`, { method: 'POST', body: { expectedVersion: version }, headers: { 'Idempotency-Key': key } }),
};
