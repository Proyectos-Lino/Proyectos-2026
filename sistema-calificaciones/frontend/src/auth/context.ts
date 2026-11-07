import { createContext } from 'react'
import type { Organization, Session } from '../api/types'

export interface AuthState {
  session: Session | null
  organization: Organization | null
  loading: boolean
  error: string | null
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  selectOrganization: (id: number) => void
  refresh: () => Promise<void>
}

export const AuthContext = createContext<AuthState | null>(null)
