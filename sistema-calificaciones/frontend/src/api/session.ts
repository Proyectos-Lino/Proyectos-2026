import { api, clearCsrf, refreshCsrf } from './client'
import type { Session } from './types'
import type { OrganizationProfileInput } from '../features/organizations/profile'

export const sessionApi = {
  me: () => api<Session>('/session/me'),
  async login(email: string, password: string) {
    const session = await api<Session>('/session/login', { method: 'POST', body: JSON.stringify({ email, password }) })
    await refreshCsrf()
    return session
  },
  async logout() {
    await api<void>('/session/logout', { method: 'POST' })
    clearCsrf()
  },
  bootstrap: (payload: {
    token: string; organizationCode: string; organizationName: string
    email: string; name: string; password: string; profile: OrganizationProfileInput
  }) => api<{ organizationId: number }>('/bootstrap', { method: 'POST', body: JSON.stringify(payload) }),
}
