// Only the Kakao Maps Web SDK surface used by this application.
export interface KakaoLatLng { getLat(): number; getLng(): number }
export interface KakaoBounds { extend(position: KakaoLatLng): void; getSouthWest(): KakaoLatLng; getNorthEast(): KakaoLatLng }
export interface KakaoMap {
  setBounds(bounds: KakaoBounds): void;
  panTo(position: KakaoLatLng): void;
  getBounds(): KakaoBounds;
  relayout(): void;
}
export interface KakaoMarker { setMap(map: KakaoMap | null): void }
export interface KakaoSdk {
  maps: {
    load(callback: () => void): void;
    Map: new (node: HTMLElement, options: { center: KakaoLatLng; level: number }) => KakaoMap;
    LatLng: new (latitude: number, longitude: number) => KakaoLatLng;
    LatLngBounds: new () => KakaoBounds;
    Marker: new (options: { map: KakaoMap; position: KakaoLatLng; title: string }) => KakaoMarker;
    event: { addListener(target: KakaoMarker | KakaoMap, event: string, callback: () => void): void;
      removeListener(target: KakaoMarker | KakaoMap, event: string, callback: () => void): void };
  };
}
