import { useEffect, useState } from 'react';

/** Data/hora atual, reavaliada a cada `intervalMs` (para contagens como "Em 25 min"). */
export function useNow(intervalMs = 30000) {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), intervalMs);
    return () => clearInterval(t);
  }, [intervalMs]);
  return now;
}
