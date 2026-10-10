import { createContext } from 'react';
import type { CurrentUser, RoleCode } from '../../../shared/types/domain';

export interface AuthContextValue {
  user: CurrentUser | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  hasRole: (...roles: RoleCode[]) => boolean;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);
