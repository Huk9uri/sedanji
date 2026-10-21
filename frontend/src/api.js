export async function api(path, { method = 'GET', body, form } = {}) {
  const headers = {};
  if (method !== 'GET') {
    const response = await fetch('/api/csrf');
    if (!response.ok) throw new Error('보안 토큰을 가져오지 못했습니다. 다시 시도하세요.');
    const csrf = await response.json();
    headers[csrf.headerName] = csrf.token;
    headers['Content-Type'] = form ? 'application/x-www-form-urlencoded' : 'application/json';
  }
  const response = await fetch('/api' + path, { method, headers, body: body === undefined ? undefined : form ? new URLSearchParams(body) : JSON.stringify(body) });
  if (!response.ok) {
    let message = response.status === 401 ? '관리자 로그인이 필요하거나 로그인 정보가 올바르지 않습니다.' : response.status === 403 ? '접근 권한 또는 보안 토큰을 확인하세요.' : '요청을 처리하지 못했습니다.';
    try { message = (await response.json()).message || message; } catch { /* Empty security response. */ }
    throw new Error(message);
  }
  return response.status === 204 ? null : response.json();
}
