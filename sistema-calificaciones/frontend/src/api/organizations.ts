import { api } from './client'
import type { Organization } from './types'
import type { OrganizationProfile, OrganizationProfileInput } from '../features/organizations/profile'

export const organizationsApi = {
  get: (id: number) => api<Organization>(`/organizaciones/${id}`),
  profile: (id: number) => api<OrganizationProfile>(`/organizaciones/${id}/perfil`),
  create: (currentOrganizationId: number, code: string, name: string, profile: OrganizationProfileInput) =>
    api<Organization>('/organizaciones', { method: 'POST', body: JSON.stringify({ currentOrganizationId, code, name, profile }) }),
  update: (id: number, name: string, profile: OrganizationProfileInput) =>
    api<Organization>(`/organizaciones/${id}`, { method: 'PATCH', body: JSON.stringify({ name, profile }) }),
  logo: (id: number) => api<Blob>(`/organizaciones/${id}/logo`),
  saveLogo: (id: number, file: File) => {
    const body = new FormData(); body.append('file', file)
    return api<void>(`/organizaciones/${id}/logo`, { method: 'PUT', body })
  },
  deleteLogo: (id: number) => api<void>(`/organizaciones/${id}/logo`, { method: 'DELETE' }),
}
