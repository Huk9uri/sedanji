import { useEffect, useRef, useState } from 'react';

let sdk;
function loadSdk(key) {
  if (window.kakao?.maps?.Map) return Promise.resolve(window.kakao);
  if (!sdk) sdk = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?autoload=false&appkey=${encodeURIComponent(key)}`;
    const timer = setTimeout(() => reject(new Error('지도 로딩 시간이 초과되었습니다. JavaScript 키와 등록 도메인을 확인하세요.')), 15000);
    script.onload = () => {
      if (!window.kakao?.maps) { clearTimeout(timer); reject(new Error('지도 SDK를 확인할 수 없습니다.')); return; }
      window.kakao.maps.load(() => { clearTimeout(timer); resolve(window.kakao); });
    };
    script.onerror = () => { clearTimeout(timer); reject(new Error('지도에 연결하지 못했습니다. JavaScript 키와 SDK 도메인을 확인하세요.')); };
    document.head.appendChild(script);
  }).catch(error => { sdk = undefined; throw error; });
  return sdk;
}

export default function KakaoMap({ restaurants, selected, onSelect }) {
  const node = useRef(null);
  const map = useRef(null);
  const markers = useRef([]);
  const [error, setError] = useState('');
  const key = import.meta.env.VITE_KAKAO_JAVASCRIPT_KEY;
  useEffect(() => {
    let active = true;
    if (!key) { setError('지도를 준비하고 있습니다. 카카오 JavaScript 키를 설정하면 승인된 식당이 지도에 표시됩니다.'); return; }
    loadSdk(key).then(kakao => {
      if (!active) return;
      if (!map.current) map.current = new kakao.maps.Map(node.current, { center: new kakao.maps.LatLng(37.558, 126.98), level: 7 });
      markers.current.forEach(marker => marker.setMap(null));
      const bounds = new kakao.maps.LatLngBounds();
      markers.current = restaurants.map(restaurant => {
        const position = new kakao.maps.LatLng(restaurant.latitude, restaurant.longitude);
        bounds.extend(position);
        const marker = new kakao.maps.Marker({ map: map.current, position, title: restaurant.name });
        kakao.maps.event.addListener(marker, 'click', () => onSelect(restaurant.id));
        return marker;
      });
      if (restaurants.length) map.current.setBounds(bounds);
      setError('');
    }).catch(error => { if (active) setError(error.message); });
    return () => { active = false; markers.current.forEach(marker => marker.setMap(null)); };
  }, [restaurants, key, onSelect]);
  useEffect(() => {
    const restaurant = restaurants.find(item => item.id === selected);
    if (restaurant && map.current && window.kakao?.maps) map.current.panTo(new window.kakao.maps.LatLng(restaurant.latitude, restaurant.longitude));
  }, [selected, restaurants]);
  return <div className="map-shell"><div ref={node} className="map" aria-label="승인된 식당 지도"/>
    {error && <div className="map-placeholder" role="status"><span className="map-symbol">⌖</span><h3>맛있는 다음 목적지</h3><p>{error}</p><small>식당 목록과 카카오맵 링크는 아래에서 확인할 수 있습니다.</small></div>}
    <span className="map-caption">관리자가 확인한 장소만 표시합니다</span>
  </div>;
}
