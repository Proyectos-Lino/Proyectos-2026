import { api, params } from './client'
import type { AuditEvent, Page, PublicationPolicy, PublicResult, ResultVersion, ResultVersionDetail } from './types'

const root = (org: number, evaluation: number) => `/organizaciones/${org}/evaluaciones/${evaluation}`

export const publicationApi = {
  policy: (org: number, evaluation: number) => api<PublicationPolicy>(`${root(org, evaluation)}/publicacion`),
  save: (org: number, evaluation: number, policy: Pick<PublicationPolicy, 'showScore' | 'showPosition' | 'showPassed' | 'showCounts' | 'visibleFrom' | 'visibleUntil'>) =>
    api<PublicationPolicy>(`${root(org, evaluation)}/publicacion`, { method: 'PUT', body: JSON.stringify(policy) }),
  approve: (org: number, evaluation: number) =>
    api<PublicationPolicy>(`${root(org, evaluation)}/publicacion/aprobacion`, { method: 'POST' }),
  publish: (org: number, evaluation: number) =>
    api<PublicationPolicy>(`${root(org, evaluation)}/publicacion/confirmacion`, { method: 'POST' }),
  accesses: (org: number, evaluation: number) =>
    api<Blob>(`${root(org, evaluation)}/publicacion/accesos`, { method: 'POST' }),
  lookup: (accessCode: string) =>
    api<PublicResult>('/consulta-resultados', { method: 'POST', body: JSON.stringify({ accessCode }) }),
  individualPdf: (accessCode: string) =>
    api<Blob>('/consulta-resultados/pdf', { method: 'POST', body: JSON.stringify({ accessCode }) }),
  recalculate: (org: number, evaluation: number, reason: string) =>
    api<{ recalculated: number }>(`${root(org, evaluation)}/recalculo`,
      { method: 'POST', body: JSON.stringify({ reason }) }),
  versions: (org: number, evaluation: number, sheet: number, page = 0, size = 10) =>
    api<Page<ResultVersion>>(`${root(org, evaluation)}/examenes/${sheet}/versiones/pagina?${params({ page, size })}`),
  versionDetail: (org: number, evaluation: number, sheet: number, version: number) =>
    api<ResultVersionDetail>(`${root(org, evaluation)}/examenes/${sheet}/versiones/${version}`),
  audit: (org: number, evaluation: number, page = 0, size = 20) =>
    api<Page<AuditEvent>>(`${root(org, evaluation)}/auditoria/pagina?${params({ page, size })}`),
}
