import { ApiError, errorMessage } from '../../../api';
import { typeLabels } from '../registration/RelationshipFields';
import { SourceState } from './RestaurantList';
import type { Draft, Page, RestaurantDetail as Detail } from './types';
export function sourceLink(videoId: string, seconds: number | null) {
  return `https://www.youtube.com/watch?v=${videoId}${seconds === null ? '' : `&t=${seconds}s`}`;
}
export function sourceTime(seconds: number | null) {
  return seconds === null ? '시점 미확인' : `${String(Math.floor(seconds / 60)).padStart(2,'0')}:${String(seconds % 60).padStart(2,'0')}`;
}
export default function RestaurantDetail({ data, loading, error, drafts, draftsLoading, draftsError, onRetry, onDraftRetry, onBack, onAdd, onResume, onMentionPage, onDraftPage }: {
  data?: Detail; loading: boolean; error?: unknown; drafts?: Page<Draft>; draftsLoading: boolean; draftsError?: unknown;
  onRetry: () => void; onDraftRetry: () => void; onBack: () => void; onAdd: (id: number) => void; onResume: (id: number) => void;
  onMentionPage: (page: number) => void; onDraftPage: (page: number) => void;
}) {
  const notFound = error instanceof ApiError && error.status === 404;
  return <section aria-label="관리자 식당 상세"><button className="secondary" onClick={onBack}>← 목록으로</button>
    {loading && <p role="status">식당 상세를 불러오는 중…</p>}
    {error !== undefined && <p role="alert" className="error">{notFound ? '식당을 찾을 수 없습니다. 목록으로 돌아가세요.' : error instanceof ApiError && error.status === 403 ? '식당 상세 접근 권한이 없습니다.' : errorMessage(error)}
      {!notFound && <button onClick={onRetry}>상세 다시 시도</button>}</p>}
    {data && <><h2>{data.restaurant.name}</h2><article className="admin-place-info"><div><p>도로명 주소: {data.restaurant.roadAddress ?? '없음'}</p><p>지번 주소: {data.restaurant.address ?? '없음'}</p></div>
      <div>Kakao Place ID <strong>{data.restaurant.kakaoPlaceId}</strong></div><a href={data.restaurant.placeUrl} target="_blank" rel="noreferrer">카카오맵에서 보기 ↗</a></article>
      <div className="admin-summary"><span className="admin-badge">{data.summary.hasPublicRelation ? '공개 관계 있음' : '공개 관계 없음'}</span>
        <span>공개 관계 {data.summary.publicRelationCount} / 전체 관계 {data.summary.relationCount}</span><SourceState needsSourceReview={data.summary.needsSourceReview} sourceCount={data.summary.sourceCount}/></div>
      <div className="section-heading"><h3>셰프·영상 관계</h3><button onClick={() => onAdd(data.restaurant.id)}>관계 추가</button></div>
      {!data.mentions.items.length && <p className="empty">이 페이지에 실제 관계가 없습니다. 임시 저장 초안은 관계에 포함하지 않습니다.</p>}
      {data.mentions.items.map(mention => <article className="admin-relation" key={mention.id} aria-label={`관계 ${mention.id}`}>
        <div className="admin-relation-heading"><h4>{mention.chef.name} <small>셰프 ID {mention.chef.id} · 관계 ID {mention.id}</small></h4>
          <span className="admin-badge">{mention.mentionType === null ? '유형 미확인' : typeLabels[mention.mentionType]}</span>
          <span className="admin-badge">검증: {mention.verificationStatus}</span><span className="admin-badge">공개: {mention.publicationStatus}</span></div>
        <h4>{mention.video.title}</h4><p>{mention.video.channelTitle} · 게시 {new Date(mention.video.publishedAt).toLocaleDateString('ko-KR')}</p>
        <p>{sourceTime(mention.timestampSeconds)}</p><a href={sourceLink(mention.video.id,mention.timestampSeconds)} target="_blank" rel="noreferrer">영상 확인 · {mention.video.title} ↗</a>
        <p>출처 확인: {mention.video.sourceCheckStatus === 'UNCHECKED' ? '미확인' : mention.video.sourceCheckStatus === 'AVAILABLE' ? '정상 확인' : '재확인 필요'}</p>
        {mention.video.sourceCheckReason === 'UNAVAILABLE' && <p>접근 불가로 기록됨 · 삭제·비공개 여부는 구분하지 않습니다.</p>}
        <p>출처 확인 시각: {mention.video.sourceCheckedAt ? new Date(mention.video.sourceCheckedAt).toLocaleString('ko-KR') : '미확인'}</p>
        <p className="admin-note">검증 메모: {mention.verificationNote ?? '없음'}</p><p>검토 시각: {mention.reviewedAt ? new Date(mention.reviewedAt).toLocaleString('ko-KR') : '없음'}</p>
      </article>)}
      <nav aria-label="관계 페이지" className="admin-pagination"><span>전체 {data.mentions.totalElements}개</span><button disabled={data.mentions.page === 0} onClick={() => onMentionPage(data.mentions.page - 1)}>이전 관계 페이지</button>
        <button disabled={!data.mentions.hasNext} onClick={() => onMentionPage(data.mentions.page + 1)}>다음 관계 페이지</button></nav>
      <h3>임시 저장 초안</h3>{draftsLoading && <p role="status">초안 목록을 불러오는 중…</p>}
      {draftsError !== undefined && <p role="alert" className="error">초안 목록 조회 실패: {errorMessage(draftsError)} <button onClick={onDraftRetry}>초안 목록 다시 시도</button></p>}
      {drafts && <>{drafts.items.map(draft => <article key={draft.id} className="admin-draft-row"><span>초안 #{draft.id} · 수정 {new Date(draft.updatedAt).toLocaleString('ko-KR')}</span><button className="secondary" onClick={() => onResume(draft.id)}>초안 #{draft.id} 이어쓰기</button></article>)}
        {!drafts.items.length && <p>작성 중인 초안이 없습니다.</p>}<nav aria-label="초안 페이지" className="admin-pagination"><span>전체 {drafts.totalElements}개</span>
          <button disabled={drafts.page === 0} onClick={() => onDraftPage(drafts.page - 1)}>이전 초안 페이지</button><button disabled={!drafts.hasNext} onClick={() => onDraftPage(drafts.page + 1)}>다음 초안 페이지</button></nav></>}
      <p className="field-help">정정·공개 중단·복구·변경 이력과 실제 출처 확인은 후속 범위입니다.</p>
    </>}
  </section>;
}
