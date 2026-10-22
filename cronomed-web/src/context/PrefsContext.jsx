import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { applyPrefs, loadPrefs, savePrefs } from '../lib/prefs.js';

const PrefsCtx = createContext(null);

export function PrefsProvider({ children }) {
  const [prefs, setPrefs] = useState(loadPrefs);
  useEffect(() => {
    applyPrefs(prefs);
    savePrefs(prefs);
  }, [prefs]);
  const setPref = useCallback((key, value) => setPrefs((p) => ({ ...p, [key]: value })), []);
  const value = useMemo(() => ({ prefs, setPref }), [prefs, setPref]);
  return <PrefsCtx.Provider value={value}>{children}</PrefsCtx.Provider>;
}

export function usePrefs() {
  return useContext(PrefsCtx);
}
