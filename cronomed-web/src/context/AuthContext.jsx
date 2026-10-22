import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { AuthApi, UserApi, setUnauthorizedHandler } from '../lib/api.js';

const AuthCtx = createContext(null);

export function AuthProvider({ children }) {
  const qc = useQueryClient();
  const [user, setUser] = useState(null);
  const [status, setStatus] = useState('loading'); // loading | authed | anon

  // Se a sessao expirar de vez (refresh falhou), volta para o login.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      setUser(null);
      setStatus('anon');
      qc.clear();
    });
  }, [qc]);

  useEffect(() => {
    let alive = true;
    UserApi.me()
      .then((u) => {
        if (!alive) return;
        setUser(u);
        setStatus('authed');
      })
      .catch(() => {
        if (alive) setStatus('anon');
      });
    return () => {
      alive = false;
    };
  }, []);

  const login = useCallback(
    async (email, password) => {
      const session = await AuthApi.login(email.trim(), password);
      qc.clear();
      setUser(session.user);
      setStatus('authed');
      return session.user;
    },
    [qc],
  );

  const register = useCallback(
    async (data) => {
      await AuthApi.register(data);
      return login(data.email, data.password);
    },
    [login],
  );

  const logout = useCallback(async () => {
    await AuthApi.logout();
    setUser(null);
    setStatus('anon');
    qc.clear();
  }, [qc]);

  const value = useMemo(() => ({ user, status, login, register, logout, setUser }), [user, status, login, register, logout]);
  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>;
}

export function useAuth() {
  return useContext(AuthCtx);
}
