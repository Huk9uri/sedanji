import type { useLocation } from '../useLocation';

const messages = {
  prompt: '',
  loading: '위치 확인 중 (최대 8초)',
  located: '현재 위치 주변',
  denied: '위치 권한 거부 · 서울 시청 기본 지역',
  failed: '위치 확인 실패 · 재시도하거나 기본 지역을 선택하세요.',
  timeout: '위치 요청 시간 초과 · 재시도하거나 기본 지역을 선택하세요.',
  default: '서울 시청 주변',
};

interface Props {
  state: ReturnType<typeof useLocation>['state'];
  canSearch: boolean;
  onLocate: () => void;
  onSearch: () => void;
  onFallback: () => void;
}

export default function MapSearchControls({ state, canSearch, onLocate, onSearch, onFallback }: Props) {
  return <div className="public-map-search location-controls">
    <button disabled={state === 'loading'} onClick={onLocate}>내 위치</button>
    <button disabled={!canSearch} onClick={onSearch}>이 지역에서 검색</button>
    {(state === 'failed' || state === 'timeout') && <button onClick={onFallback}>서울 시청 기본 지역</button>}
    <p role="status">{messages[state]}</p>
  </div>;
}
