import { ApiError, errorMessage } from '../../../api';
import type { ListConditions, Page, PublicationFilter, RestaurantItem } from './types';
export function SourceState({ needsSourceReview, sourceCount }: { needsSourceReview: boolean | null; sourceCount: number }) {
  return <span className={`admin-badge ${needsSourceReview === true ? 'review' : ''}`}>{sourceCount === 0 ? '연결 출처 없음' : needsSourceReview === null ? '출처 미확인' : needsSourceReview ? '출처 재확인 필요' : '연결 출처 정상 확인'}</span>;
}
export default function RestaurantList({ input, onInput, conditions, onConditions, data, loading, error, onRetry, onOpen, onNew, onCandidates }: {
  input: string; onInput: (value: string) => void; conditions: ListConditions; onConditions: (value: ListConditions) => void;
  data?: Page<RestaurantItem>; loading: boolean; error?: unknown; onRetry: () => void; onOpen: (id: number) => void; onNew: () => void; onCandidates: () => void;
}) {
  return <section aria-label="관리자 식당 목록"><div className="section-heading"><h2>식당 관리</h2><button onClick={onNew}>식당 직접 등록 · 초안 이어쓰기</button></div>
    <div className="admin-list-tools"><form onSubmit={event => { event.preventDefault(); onConditions({ ...conditions, query: input.trim(), page: 0 }); }}>
      <label>관리자 식당 검색<input value={input} onChange={event => onInput(event.target.value)} placeholder="식당명, 주소, 셰프"/></label><button>검색</button></form>
      <label>공개 관계 필터<select value={conditions.publication} onChange={event => onConditions({ ...conditions, publication: event.target.value as PublicationFilter, page: 0 })}>
        <option value="ALL">전체</option><option value="PUBLISHED">공개 관계 있음</option><option value="NO_PUBLIC_RELATION">공개 관계 없음</option></select></label>
      <label className="admin-check"><input type="checkbox" checked={conditions.needsSourceReview} onChange={event => onConditions({ ...conditions, needsSourceReview: event.target.checked, page: 0 })}/>출처 재확인 필요만</label>
      <label>페이지 크기<select value={conditions.size} onChange={event => onConditions({ ...conditions, size: Number(event.target.value), page: 0 })}>{[20,50,100].map(size => <option key={size} value={size}>{size}개</option>)}</select></label>
      <button className="secondary" onClick={onRetry}>목록 새로고침</button></div>
    <p className="field-help">최근 식당 등록순 · 출처 필터 해제는 정상 출처만 조회한다는 의미가 아닙니다.</p>
    {loading && <p role="status">식당 목록을 불러오는 중…</p>}
    {error !== undefined && <p role="alert" className="error">{error instanceof ApiError && error.status === 403 ? '식당 목록 접근 권한이 없습니다.' : errorMessage(error)} <button onClick={onRetry}>목록 다시 시도</button></p>}
    {data && <><p>전체 {data.totalElements}곳 · {data.page + 1}페이지</p>
      {!data.items.length && <p className="empty">조건에 맞는 식당이 없습니다.</p>}
      <div className="admin-restaurant-list">{data.items.map(item => <article key={item.id} className="admin-restaurant-row">
        <div><button className="admin-name" onClick={() => onOpen(item.id)}>{item.name}</button><p>{item.roadAddress || item.address || '주소 정보 없음'}</p>
          <p>{item.chefs.map(chef => chef.name).join(', ') || '연결 셰프 없음'}{item.hasMoreChefs ? ` 외 ${item.chefCount - item.chefs.length}명` : ''}</p></div>
        <div><span className="admin-badge">{item.hasPublicRelation ? '공개 관계 있음' : '공개 관계 없음'}</span><p>공개 관계 {item.publicRelationCount} / 전체 관계 {item.relationCount}</p>
          <SourceState needsSourceReview={item.needsSourceReview} sourceCount={item.sourceCount}/><p>연결 영상 {item.sourceCount} · 미확인 {item.uncheckedSourceCount} · 재확인 {item.reviewRequiredSourceCount}</p></div>
        <button className="secondary" onClick={() => onOpen(item.id)}>상세 보기 · {item.name}</button>
      </article>)}</div>
      <nav aria-label="식당 목록 페이지" className="admin-pagination"><button disabled={data.page === 0 || loading} onClick={() => onConditions({ ...conditions, page: data.page - 1 })}>이전 식당 페이지</button>
        <button disabled={!data.hasNext || loading} onClick={() => onConditions({ ...conditions, page: data.page + 1 })}>다음 식당 페이지</button></nav></>}
    <button className="secondary" onClick={onCandidates}>기존 후보 검토</button>
  </section>;
}
