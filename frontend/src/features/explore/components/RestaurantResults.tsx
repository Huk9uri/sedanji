import type { Ref } from 'react';
import ChefFilter from '../ChefFilter';
import type { Area, Item } from '../contracts';

interface Props {
  titleRef: Ref<HTMLHeadingElement>;
  listRef: Ref<HTMLDivElement>;
  result: Area | null;
  chefId: number | null;
  chefLabel: string;
  revision: number;
  selectedId: number | null;
  showingPrevious: boolean;
  loading: boolean;
  error: string;
  onChefChange: (id: number | null, name?: string | null) => void;
  onSelect: (id: number) => void;
  onRetry: () => void;
}

function RestaurantResultCard({ item, selected, onSelect }: {
  item: Item;
  selected: boolean;
  onSelect: (id: number) => void;
}) {
  return <article className={selected ? 'public-result-selected' : undefined}>
    <button aria-pressed={selected} data-restaurant-id={item.id} className="restaurant-title" onClick={() => onSelect(item.id)}>
      {item.name}
    </button>
    <p>{item.roadAddress || item.address}</p>
    <p>{item.chefs.map(chef => chef.name).join(' · ')}{item.hasMoreChefs ? ` 외 ${item.chefCount - item.chefs.length}명` : ''}</p>
  </article>;
}

export default function RestaurantResults({
  titleRef, listRef, result, chefId, chefLabel, revision, selectedId,
  showingPrevious, loading, error, onChefChange, onSelect, onRetry,
}: Props) {
  return <div className="public-panel">
    <div className="public-panel-top">
      <h1 ref={titleRef} tabIndex={-1}>셰프의 단골 지도</h1>
      <ChefFilter selected={chefId} revision={revision} onChange={onChefChange}/>
      {result && <p className="result-condition">{showingPrevious ? '이전 검색 결과' : '이 지도 영역'} · {chefLabel} · {result.returnedCount}곳</p>}
      {result?.truncated && <p>일부 결과입니다. 지도를 확대하세요.</p>}
      {loading && <p role="status">식당 조회 중</p>}
      {error && <p role="alert">{error} <button onClick={onRetry}>다시 시도</button></p>}
    </div>
    <div ref={listRef} className="public-results">
      {result?.items.map(item => <RestaurantResultCard key={item.id} item={item} selected={item.id === selectedId} onSelect={onSelect}/>)}
      {result?.items.length === 0 && !loading && !error && <div className="public-empty">
        <p>검색 결과가 없습니다</p>
        {result.query.chefId !== null && chefId !== null
          ? <button onClick={() => onChefChange(null)}>셰프 필터 해제</button>
          : <p>지도를 넓히거나 이동한 뒤 이 지역에서 검색해 보세요.</p>}
      </div>}
    </div>
  </div>;
}
