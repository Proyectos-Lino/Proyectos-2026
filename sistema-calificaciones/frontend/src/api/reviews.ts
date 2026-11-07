import { api, params } from './client'
import type { Assignee, Evidence, Page, Review, ReviewCase } from './types'

const root = (org: number, evaluationId: number) =>
  `/organizaciones/${org}/evaluaciones/${evaluationId}`

export const reviewsApi = {
  cases: (org: number, evaluationId: number, options: { query: string; status: string; page: number; size: number; assignment?: string }) =>
    api<Page<ReviewCase>>(`${root(org, evaluationId)}/revisiones/pagina?${params(options)}`),
  assignees: (org: number, evaluationId: number, query: string, page: number, size = 10) =>
    api<Page<Assignee>>(`${root(org, evaluationId)}/revisiones/responsables/pagina?${params({ query, page, size })}`),
  assign: (org: number, evaluationId: number, reviewId: number, userId: number | null) =>
    api<void>(`${root(org, evaluationId)}/revisiones/${reviewId}/responsable`,
      { method: 'PATCH', body: JSON.stringify({ userId }) }),
  list: (org: number, evaluationId: number, sheetId: number) =>
    api<Review[]>(`${root(org, evaluationId)}/examenes/${sheetId}/revisiones`),
  neighbors: (org: number, evaluation: number, sheet: number, assignment = 'ALL') =>
    api<{ previous: number | null; next: number | null }>(`${root(org, evaluation)}/examenes/${sheet}/revisiones/vecinos?${params({ assignment })}`),
  open: (org: number, evaluationId: number, sheetId: number, reason: string) =>
    api<{ id: number }>(`${root(org, evaluationId)}/examenes/${sheetId}/revisiones`,
      { method: 'POST', body: JSON.stringify({ reason }) }),
  resolve: (org: number, evaluationId: number, reviewId: number, decision: 'APROBADA' | 'ANULADA', explanation: string) =>
    api<void>(`${root(org, evaluationId)}/revisiones/${reviewId}`,
      { method: 'PATCH', body: JSON.stringify({ decision, explanation }) }),
  annul: (org: number, evaluationId: number, sheetId: number, reason: string) =>
    api<{ reviewId: number }>(`${root(org, evaluationId)}/examenes/${sheetId}/anulacion`,
      { method: 'POST', body: JSON.stringify({ reason }) }),
  restore: (org: number, evaluationId: number, sheetId: number) =>
    api<{ reviewId: number }>(`${root(org, evaluationId)}/examenes/${sheetId}/reversion`, { method: 'POST' }),
  updateReason: (org: number, evaluationId: number, sheetId: number, reason: string) =>
    api<void>(`${root(org, evaluationId)}/examenes/${sheetId}/motivo`,
      { method: 'PATCH', body: JSON.stringify({ reason }) }),
  evidence: (org: number, evaluationId: number, reviewId: number) =>
    api<Evidence[]>(`${root(org, evaluationId)}/revisiones/${reviewId}/evidencias`),
  attach: (org: number, evaluationId: number, reviewId: number, file: File) => {
    const body = new FormData()
    body.append('file', file)
    return api<{ id: number }>(`${root(org, evaluationId)}/revisiones/${reviewId}/evidencias`,
      { method: 'POST', body })
  },
  file: (org: number, evaluationId: number, evidenceId: number) =>
    api<Blob>(`${root(org, evaluationId)}/evidencias/${evidenceId}/archivo`),
  deleteEvidence: (org: number, evaluationId: number, evidenceId: number) =>
    api<void>(`${root(org, evaluationId)}/evidencias/${evidenceId}`, { method: 'DELETE' }),
}
