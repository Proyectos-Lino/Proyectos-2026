import { api, params } from './client'
import type { Exam, ExamExplanation, Page, RankingDecision } from './types'

const root = (org: number, evaluationId: number) =>
  `/organizaciones/${org}/evaluaciones/${evaluationId}/examenes`

export const examsApi = {
  page: (org: number, evaluationId: number, options: {
    code: string; groupId: number | null; status: string; page: number; size: number; order: string
  }) => api<Page<Exam>>(`${root(org, evaluationId)}?${params(options)}`),
  one: (org: number, evaluationId: number, sheetId: number) =>
    api<Exam>(`${root(org, evaluationId)}/${sheetId}`),
  explanation: (org: number, evaluationId: number, sheetId: number) =>
    api<ExamExplanation>(`${root(org, evaluationId)}/${sheetId}/explicacion`),
  decisions: (org: number, evaluationId: number, sheetId: number, page: number, size = 10) =>
    api<Page<RankingDecision>>(`${root(org, evaluationId)}/${sheetId}/decisiones/pagina?${params({ page, size })}`),
}
