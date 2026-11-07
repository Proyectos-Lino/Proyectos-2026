import { api, params } from '../../api/client'
import type { Page } from '../../api/types'
import type { Enrollment, EnrollmentInput, Person, ParticipantSummary, ImportMode, EnrollmentImportCounts, EnrollmentImportRow, EnrollmentImportResult } from './types'

const root = (org: number, evaluation: number) => `/organizaciones/${org}/evaluaciones/${evaluation}/participantes`
export const participantsApi = {
  page: (org: number, evaluation: number, options: { query: string; groupId: number | null; profile: string; document: string; page: number; size: number }) =>
    api<Page<Enrollment>>(`${root(org, evaluation)}/pagina?${params(options)}`),
  summary: (org: number, evaluation: number) => api<ParticipantSummary>(`${root(org, evaluation)}/resumen`),
  people: (org: number, options: { query: string; page: number; size: number }) =>
    api<Page<Person>>(`/organizaciones/${org}/personas/pagina?${params(options)}`),
  create: (org: number, evaluation: number, value: EnrollmentInput) => api<Enrollment>(root(org, evaluation), { method: 'POST', body: JSON.stringify(value) }),
  update: (org: number, evaluation: number, id: number, value: EnrollmentInput) => api<Enrollment>(`${root(org, evaluation)}/${id}`, { method: 'PATCH', body: JSON.stringify(value) }),
  template: (org: number, evaluation: number, mode: ImportMode) => api<Blob>(`${root(org, evaluation)}/plantilla.csv?${params({ mode })}`),
  preview: (org: number, evaluation: number, file: File, mode: ImportMode, options: { query: string; action: string; page: number; size: number }) => {
    const form = new FormData(); form.append('file', file); form.append('mode', mode)
    return api<{ counts: EnrollmentImportCounts; rows: Page<EnrollmentImportRow>; fingerprint: string }>(`${root(org, evaluation)}/importaciones/vista-previa?${params(options)}`, { method: 'POST', body: form })
  },
  confirm: (org: number, evaluation: number, file: File, mode: ImportMode, fingerprint: string) => {
    const form = new FormData(); form.append('file', file); form.append('mode', mode); form.append('fingerprint', fingerprint)
    return api<EnrollmentImportResult>(`${root(org, evaluation)}/importaciones/confirmar`, { method: 'POST', body: form })
  },
  report: (org: number, evaluation: number, report: number, options: { query: string; action: string; page: number; size: number }) =>
    api<Page<EnrollmentImportRow>>(`${root(org, evaluation)}/importaciones/${report}/pagina?${params(options)}`),
  reportCsv: (org: number, evaluation: number, report: number) => api<Blob>(`${root(org, evaluation)}/importaciones/${report}/resultado.csv`),
}
