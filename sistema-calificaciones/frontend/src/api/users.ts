import { api, params } from './client'
import type { Page, User } from './types'

const root = (org: number) => `/organizaciones/${org}/usuarios`

export const usersApi = {
  page: (org: number, options: { query: string; role: string; page: number; size: number }) =>
    api<Page<User>>(`${root(org)}/pagina?${params(options)}`),
  create: (org: number, payload: { email: string; name: string; password: string; role: string }) =>
    api<User>(root(org), { method: 'POST', body: JSON.stringify(payload) }),
  link: (org: number, email: string, role: string) =>
    api<User>(`${root(org)}/vincular`, { method: 'POST', body: JSON.stringify({ email, role }) }),
  update: (org: number, userId: number, role: string, active: boolean) =>
    api<void>(`${root(org)}/${userId}`, { method: 'PATCH', body: JSON.stringify({ role, active }) }),
  updateProfile: (org: number, userId: number, payload: {
    name: string; email: string; password: string | null; role: string; active: boolean
  }) => api<void>(`${root(org)}/${userId}/perfil`, { method: 'PATCH', body: JSON.stringify(payload) }),
}
