import { useEffect, useId, useRef, useState } from 'react';
import { api, errorMessage } from '../../api';
import type { ChefItem, Page } from './contracts';
export default function ChefFilter({ selected, onChange, revision }: { selected: number | null; onChange: (id: number | null) => void; revision: number }) {
  const filterId = useId();
  const [query, setQuery] = useState(''); const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<ChefItem> | null>(null); const [error, setError] = useState('');
  const [loading, setLoading] = useState(false); const [retry, setRetry] = useState(0);
  const [loadedQuery, setLoadedQuery] = useState('');
  const [chosen, setChosen] = useState<ChefItem | null>(null); const generation = useRef(0);
  const previousRevision = useRef(revision);
  useEffect(() => {
    if (previousRevision.current !== revision) { setData(null); previousRevision.current = revision; }
    const controller = new AbortController(); const g = ++generation.current; setLoading(true); setError('');
    void api<Page<ChefItem>>(`/public/chefs?query=${encodeURIComponent(query)}&page=${page}&size=50`, { signal: controller.signal })
      .then(result => { if (g === generation.current) { setData(result); setLoadedQuery(query); } })
      .catch(e => { if (g === generation.current && !controller.signal.aborted) setError(errorMessage(e)); })
      .finally(() => { if (g === generation.current) setLoading(false); });
    return () => { generation.current++; controller.abort(); };
  }, [query, page, revision, retry]);
  // A missing selected chef stays selected, including when search/page no longer contains it.
  const missing = selected !== null && !data?.items.some(c => c.id === selected);
  return <div className="chef-filter"><label>셰프 이름 검색<input value={query} onChange={e => { setQuery(e.target.value); setPage(0); }}/></label>
    <label htmlFor={filterId}>셰프 필터</label><select id={filterId} value={selected ?? ''} onChange={e => {
      const id = e.target.value === '' ? null : Number(e.target.value); setChosen(data?.items.find(c => c.id === id) ?? chosen); onChange(id);
    }}><option value="">전체 셰프</option>{missing && <option value={selected!}>{chosen?.name ?? `셰프 #${selected}`} (선택 유지)</option>}
      {data?.items.map(c => <option key={c.id} value={c.id}>{c.name} · 공개 식당 {c.restaurantCount}곳</option>)}</select>
    {loading && <p role="status">셰프 목록 조회 중</p>}{error && <p role="alert">{error}<button onClick={() => setRetry(n => n + 1)}>셰프 다시 시도</button></p>}
    {data && (loading || error) && <p>이전 셰프 목록: {loadedQuery || '전체 이름'} · {data.page + 1}페이지</p>}
    <div><button disabled={page === 0 || loading || !!error} onClick={() => setPage(n => n - 1)}>이전 셰프</button><span> {page + 1}페이지 </span><button disabled={!data?.hasNext || loading || !!error} onClick={() => setPage(n => n + 1)}>다음 셰프</button></div>
  </div>;
}
