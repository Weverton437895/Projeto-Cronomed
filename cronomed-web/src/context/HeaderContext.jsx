import { createContext, useContext, useEffect, useMemo, useState } from 'react';

// Permite que cada tela defina o subtitulo do topo (ex.: data de hoje, "Compartimento 1 - novo cadastro").
const HeaderCtx = createContext({ subtitle: '', setSubtitle: () => {} });

export function HeaderProvider({ children }) {
  const [subtitle, setSubtitle] = useState('');
  const value = useMemo(() => ({ subtitle, setSubtitle }), [subtitle]);
  return <HeaderCtx.Provider value={value}>{children}</HeaderCtx.Provider>;
}

export function useSubtitle(text) {
  const { setSubtitle } = useContext(HeaderCtx);
  useEffect(() => {
    setSubtitle(text || '');
    return () => setSubtitle('');
  }, [text, setSubtitle]);
}

export function useHeader() {
  return useContext(HeaderCtx);
}
