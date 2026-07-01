import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, getToken, setToken } from '../api/client';
import type { Role } from '../api/types';

interface AuthContextValue {
  isAdmin: boolean;
  isRoot: boolean;
  role: Role | null;
  username: string | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [role, setRole] = useState<Role | null>(null);
  const [username, setUsername] = useState<string | null>(null);
  // Anonymous visitors render immediately; only a stored token needs a validation round-trip.
  const [ready, setReady] = useState(() => !getToken());

  // Restore the session on load: if a token is stored, ask the server who we are (also validates it).
  useEffect(() => {
    if (!getToken()) {
      return;
    }
    let cancelled = false;
    api
      .me()
      .then((me) => {
        if (!cancelled) {
          setRole(me.role);
          setUsername(me.name);
        }
      })
      .catch(() => {
        if (!cancelled) setToken(null);
      })
      .finally(() => !cancelled && setReady(true));
    return () => {
      cancelled = true;
    };
  }, []);

  const login = async (user: string, password: string) => {
    const res = await api.login(user, password);
    setToken(res.token);
    setRole(res.role);
    setUsername(res.username);
  };

  const logout = async () => {
    try {
      await api.logout();
    } finally {
      setToken(null);
      setRole(null);
      setUsername(null);
    }
  };

  const value = useMemo<AuthContextValue>(
    () => ({
      isAdmin: role !== null,
      isRoot: role === 'ROOT',
      role,
      username,
      login,
      logout,
    }),
    [role, username],
  );

  // Avoid a flash of the signed-out state while restoring an existing session.
  if (!ready) return null;

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
