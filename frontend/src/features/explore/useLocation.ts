import { useEffect, useRef, useState } from 'react';
import { defaultCenter } from './contracts';
export function useLocation() {
  const [state, setState] = useState<'prompt' | 'loading' | 'located' | 'denied' | 'failed' | 'timeout' | 'default'>('prompt');
  const [center, setCenter] = useState<typeof defaultCenter | null>(null);
  const generation = useRef(0);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  function cancel() { generation.current++; clearTimeout(timer.current); }
  useEffect(() => () => cancel(), []);
  function fallback() { cancel(); setCenter({ ...defaultCenter }); setState('default'); }
  function locate() {
    cancel(); const g = generation.current; setState('loading'); setCenter(null);
    if (!navigator.geolocation) { setState('failed'); return; }
    timer.current = setTimeout(() => { if (generation.current === g) { cancel(); setState('timeout'); } }, 8000);
    navigator.geolocation.getCurrentPosition(position => {
      if (generation.current !== g) return;
      cancel(); setCenter({ latitude: position.coords.latitude, longitude: position.coords.longitude }); setState('located');
    }, error => {
      if (generation.current !== g) return;
      cancel();
      if (error.code === 1) { setCenter({ ...defaultCenter }); setState('denied'); }
      else setState(error.code === 3 ? 'timeout' : 'failed');
    }, { timeout: 8000, maximumAge: 0, enableHighAccuracy: false });
  }
  return { state, center, locate, fallback };
}
