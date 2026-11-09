import type { KakaoSdk } from './kakao';

let sdk: Promise<KakaoSdk> | undefined;
export function loadSdk(key: string): Promise<KakaoSdk> {
  if (window.kakao?.maps?.Map) return Promise.resolve(window.kakao);
  if (!sdk) sdk = new Promise<KakaoSdk>((resolve, reject) => {
    const script = document.createElement('script'); let settled = false;
    const finish = (error?: Error) => {
      if (settled) return; settled = true; clearTimeout(timer);
      script.onload = null; script.onerror = null;
      if (error) { script.remove(); reject(error); }
      else if (window.kakao?.maps?.Map) resolve(window.kakao);
      else { script.remove(); reject(new Error('지도 SDK를 확인할 수 없습니다.')); }
    };
    const timer = setTimeout(() => finish(new Error('지도 로딩 시간이 초과되었습니다.')), 15000);
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?autoload=false&appkey=${encodeURIComponent(key)}`;
    script.onload = () => {
      if (!window.kakao?.maps) { finish(new Error('지도 SDK를 확인할 수 없습니다.')); return; }
      try { window.kakao.maps.load(() => finish()); } catch { finish(new Error('지도 SDK를 불러오지 못했습니다.')); }
    };
    script.onerror = () => finish(new Error('지도에 연결하지 못했습니다.'));
    document.head.appendChild(script);
  }).catch(error => { sdk = undefined; throw error; });
  return sdk;
}
