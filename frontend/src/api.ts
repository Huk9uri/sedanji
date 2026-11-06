export type ApiOptions = { method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'; headers?: Record<string, string>; signal?: AbortSignal } & (
  | { form: true; body?: Record<string, FormDataEntryValue> }
  | { form?: false; body?: unknown }
);

function object(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
export class ApiError extends Error {
  readonly code?: string;
  readonly existingMentionId?: number;
  readonly currentVersion?: number;
  readonly appliedMentionId?: number;
  readonly missingFields?: string[];
  constructor(readonly status: number, message: string, data: unknown = null) {
    super(message); this.name = 'ApiError';
    if (!object(data)) return;
    if (typeof data.code === 'string') this.code = data.code;
    for (const field of ['existingMentionId', 'currentVersion', 'appliedMentionId'] as const) {
      if (typeof data[field] === 'number' && Number.isSafeInteger(data[field])) this[field] = data[field];
    }
    if (Array.isArray(data.missingFields) && data.missingFields.every(x => typeof x === 'string')) this.missingFields = data.missingFields;
  }
}

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '요청을 처리하지 못했습니다.';
}

// T describes the backend JSON contract; it does not validate JSON at runtime.
// Callers of endpoints with empty responses use the default null result.
export async function api<T = null>(path: string, { method = 'GET', body, form, headers: customHeaders, signal }: ApiOptions = {}): Promise<T> {
  const headers: Record<string, string> = { ...customHeaders };
  if (method !== 'GET') {
    const response = await fetch('/api/csrf');
    if (!response.ok) throw new ApiError(response.status, '보안 토큰을 가져오지 못했습니다. 다시 시도하세요.');
    const csrf = await response.json() as { headerName: string; token: string };
    for (const key of Object.keys(headers)) if (key.toLowerCase() === csrf.headerName.toLowerCase() || key.toLowerCase() === 'content-type') delete headers[key];
    headers[csrf.headerName] = csrf.token;
    headers['Content-Type'] = form ? 'application/x-www-form-urlencoded' : 'application/json';
  }
  const response = await fetch('/api' + path, { method, headers, ...(signal ? { signal } : {}), body: body === undefined ? undefined : form ? new URLSearchParams(Object.entries(body).map(([name, value]) => [name, String(value)])) : JSON.stringify(body) });
  if (!response.ok) {
    let message = response.status === 401 ? '관리자 로그인이 필요하거나 로그인 정보가 올바르지 않습니다.' : response.status === 403 ? '접근 권한 또는 보안 토큰을 확인하세요.' : '요청을 처리하지 못했습니다.';
    let data: unknown = null;
    try { data = await response.json(); if (object(data) && typeof data.message === 'string' && data.message) message = data.message; } catch { /* Empty security response. */ }
    throw new ApiError(response.status, message, data);
  }
  if (response.status === 204) return null as T;
  const text = await response.text();
  return (text.trim() ? JSON.parse(text) : null) as T;
}
