/// <reference types="vite/client" />

import type { KakaoSdk } from './kakao';

declare global {
  interface Window { kakao?: KakaoSdk }
  interface ImportMetaEnv { readonly VITE_KAKAO_JAVASCRIPT_KEY?: string }
}
