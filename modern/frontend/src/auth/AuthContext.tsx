import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { login as loginRequest } from '../api/auth';
import { sessionStorageKey, setUnauthorizedHandler } from '../api/client';
import type { Session } from '../api/types';

interface AuthContextValue {
  session: Session | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function readSession(): Session | null {
  try {
    const raw = window.localStorage.getItem(sessionStorageKey);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(readSession);

  const logout = useCallback(() => {
    window.localStorage.removeItem(sessionStorageKey);
    setSession(null);
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const result = await loginRequest(username, password);
    const next: Session = { token: result.token, username: result.username, employeeName: result.employeeName };
    window.localStorage.setItem(sessionStorageKey, JSON.stringify(next));
    setSession(next);
  }, []);

  // A 401 from any API call means the token is missing, invalid or expired.
  useEffect(() => {
    setUnauthorizedHandler(logout);
    return () => setUnauthorizedHandler(null);
  }, [logout]);

  const value = useMemo(() => ({ session, login, logout }), [session, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
