// Only the Kakao Maps Web SDK surface used by this application.
export interface KakaoLatLng {}
export interface KakaoBounds { extend(position: KakaoLatLng): void }
export interface KakaoMap {
  setBounds(bounds: KakaoBounds): void;
  panTo(position: KakaoLatLng): void;
}
export interface KakaoMarker { setMap(map: KakaoMap | null): void }
export interface KakaoSdk {
  maps: {
    load(callback: () => void): void;
    Map: new (node: HTMLElement, options: { center: KakaoLatLng; level: number }) => KakaoMap;
    LatLng: new (latitude: number, longitude: number) => KakaoLatLng;
    LatLngBounds: new () => KakaoBounds;
    Marker: new (options: { map: KakaoMap; position: KakaoLatLng; title: string }) => KakaoMarker;
    event: { addListener(target: KakaoMarker, event: string, callback: () => void): void };
  };
}
