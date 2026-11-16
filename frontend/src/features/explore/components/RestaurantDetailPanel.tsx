import type { Ref } from 'react';
import { youtubeLink } from '../contracts';
import type { Detail, Source } from '../contracts';

const labels = { REGULAR: '단골', RECOMMENDED: '추천', VISITED: '방문', MENTIONED: '단순 언급' };
function sourceLabel(seconds: number | null) {
  if (seconds === null) return 'YouTube에서 보기';
  const minutes = Math.floor(seconds / 60);
  const remainder = String(seconds % 60).padStart(2, '0');
  const time = minutes < 60 ? `${minutes}:${remainder}` : `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, '0')}:${remainder}`;
  return `${time}부터 보기`;
}
interface Props {
  titleRef: Ref<HTMLHeadingElement>;
  detail: Detail | null;
  chefId: number | null;
  unavailable: boolean;
  loading: boolean;
  error: string;
  page: number;
  onClose: () => void;
  onPageChange: (page: number) => void;
}

function RestaurantSourceCard({ source, selected }: { source: Source; selected: boolean }) {
  return <article className={selected ? 'selected-chef' : ''}>
    <h3>{source.chef.name} · {labels[source.mentionType]}{selected ? ' · 선택 셰프' : ''}</h3>
    <p>{source.video.title}</p>
    <a href={youtubeLink(source.video.id, source.timestampSeconds)} target="_blank" rel="noreferrer">
      {sourceLabel(source.timestampSeconds)}
    </a>
    <p>{source.video.channelTitle} · {source.video.publishedAt.slice(0, 10)}</p>
  </article>;
}

export default function RestaurantDetailPanel({
  titleRef, detail, chefId, unavailable, loading, error, page, onClose, onPageChange,
}: Props) {
  const restaurant = detail?.restaurant;
  const address = restaurant?.roadAddress || restaurant?.address;
  const sources = detail?.sources;

  return <section className="public-detail" aria-label="식당 상세">
    <button onClick={onClose}>상세 닫기</button>
    <h2 ref={titleRef} tabIndex={-1}>{restaurant?.name ?? '식당 상세'}</h2>
    {unavailable && <p role="alert">공개된 식당을 찾을 수 없습니다.</p>}
    {loading && <p role="status">상세 확인 중</p>}
    {error && <p role="alert">{error}<button onClick={() => onPageChange(page)}>상세 다시 시도</button></p>}
    {address && <p>{address}</p>}
    {restaurant?.kakaoPlaceId.trim() && <a href={`https://place.map.kakao.com/${encodeURIComponent(restaurant.kakaoPlaceId)}`} target="_blank" rel="noreferrer">카카오맵에서 보기 ↗</a>}
    {sources && <>
      <h3>셰프가 소개한 출처</h3>
      {sources.items.map(source => <RestaurantSourceCard key={source.mentionId} source={source} selected={source.chef.id === chefId}/>)}
      {(page > 0 || sources.hasNext) && <div>
        <button disabled={page === 0 || loading} onClick={() => onPageChange(page - 1)}>이전 출처</button>
        <span> {page + 1}페이지 </span>
        <button disabled={!sources.hasNext || loading} onClick={() => onPageChange(page + 1)}>다음 출처</button>
      </div>}
    </>}
  </section>;
}
