import { useMemo, useState, type ReactNode } from 'react';
import type { CurrentUser, RoleCode } from '../../../shared/types/domain';
import { authenticate } from '../api/auth-api';
import { AuthContext, type AuthContextValue } from '../model/auth-context';

const USER_KEY = 'labcore.user';
const TOKEN_KEY = 'labcore.token';

function readStoredUser(): CurrentUser | null {
  const serialized = localStorage.getItem(USER_KEY);

  if (!serialized) {
    return null;
  }

  try {
    return JSON.parse(serialized) as CurrentUser;
  } catch {
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(TOKEN_KEY);
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(readStoredUser);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      async login(email, password) {
        const result = await authenticate({ email, password });
        localStorage.setItem(TOKEN_KEY, result.accessToken);
        localStorage.setItem(USER_KEY, JSON.stringify(result.user));
        setUser(result.user);
      },
      logout() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
        setUser(null);
      },
      hasRole(...roles: RoleCode[]) {
        return user?.roles.some((role) => roles.includes(role)) ?? false;
      },
    }),
    [user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
