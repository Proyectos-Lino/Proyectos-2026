import { api, params } from './client'
import type { Preparation } from './types'
import type { AnswerKey, BulkImportSummary, Evaluation, EvaluationOverview, Form, Group, Page, RankingPolicy, Rule, SectionConfiguration, SectionInput, Statistics } from './types'

const root = (org: number) => `/organizaciones/${org}`
const item = (org: number, id: number) => `${root(org)}/evaluaciones/${id}`

export const evaluationsApi = {
  preparation: (org: number, id: number) => api<Preparation>(`${item(org, id)}/preparacion`),
  page: (org: number, options: { query: string; status: string; page: number; size: number }) =>
    api<Page<EvaluationOverview>>(`${root(org)}/evaluaciones/pagina?${params(options)}`),
  overview: (org: number, id: number) => api<EvaluationOverview>(`${item(org, id)}/resumen`),
  statistics: (org: number) => api<Statistics>(`${root(org)}/estadisticas`),
  create: (org: number, payload: { code: string; name: string; description: string; type: string; date: string | null; groupType: Evaluation['groupType'] }) =>
    api<Evaluation>(`${root(org)}/evaluaciones`, { method: 'POST', body: JSON.stringify(payload) }),
  update: (org: number, id: number, payload: { name: string; description: string; date: string | null; groupType: Evaluation['groupType'] }) =>
    api<Evaluation>(item(org, id), { method: 'PATCH', body: JSON.stringify(payload) }),
  status: (org: number, id: number, status: Evaluation['status']) =>
    api<Evaluation>(`${item(org, id)}/estado`, { method: 'PATCH', body: JSON.stringify({ status }) }),
  groups: (org: number, id: number) => api<Group[]>(`${item(org, id)}/grupos`),
  createGroup: (org: number, id: number, payload: { code: string; name: string; capacity: number | null }) =>
    api<{ id: number }>(`${item(org, id)}/grupos`, { method: 'POST', body: JSON.stringify(payload) }),
  updateCapacity: (org: number, id: number, groupId: number, capacity: number | null) =>
    api<void>(`${item(org, id)}/grupos/${groupId}`, { method: 'PATCH', body: JSON.stringify({ capacity }) }),
  updateGroup: (org: number, id: number, groupId: number, payload: { code: string; name: string; capacity: number | null }) =>
    api<void>(`${item(org, id)}/grupos/${groupId}`, { method: 'PUT', body: JSON.stringify(payload) }),
  importGroups: (org: number, id: number, file: File) => {
    const body = new FormData(); body.append('file', file)
    return api<BulkImportSummary>(`${item(org, id)}/grupos/importar`, { method: 'POST', body })
  },
  forms: (org: number, id: number) => api<Form[]>(`${item(org, id)}/formularios`),
  createForm: (org: number, id: number, payload: { code: string; questions: number; options: number }) =>
    api<{ id: number }>(`${item(org, id)}/formularios`, { method: 'POST', body: JSON.stringify(payload) }),
  key: (org: number, id: number, formId: number) => api<AnswerKey>(`${item(org, id)}/formularios/${formId}/clave`),
  saveKey: (org: number, id: number, formId: number, answers: string[]) =>
    api<{ id: number }>(`${item(org, id)}/formularios/${formId}/clave`, { method: 'PUT', body: JSON.stringify({ answers }) }),
  rule: (org: number, id: number) => api<Rule>(`${item(org, id)}/reglas/actual`),
  saveRule: (org: number, id: number, payload: { correct: number; incorrect: number; blank: number; passingScore: number | null }) =>
    api<{ id: number }>(`${item(org, id)}/reglas`, { method: 'POST', body: JSON.stringify(payload) }),
  policy: (org: number, id: number) => api<RankingPolicy>(`${item(org, id)}/politica-resultados`),
  savePolicy: (org: number, id: number, payload: Omit<RankingPolicy, 'id' | 'evaluationId' | 'version'>) =>
    api<RankingPolicy>(`${item(org, id)}/politica-resultados`, { method: 'POST', body: JSON.stringify(payload) }),
  sections: (org: number, id: number, formId: number) =>
    api<SectionConfiguration>(`${item(org, id)}/formularios/${formId}/secciones/actual`),
  saveSections: (org: number, id: number, formId: number, sections: SectionInput[]) =>
    api<SectionConfiguration>(`${item(org, id)}/formularios/${formId}/secciones`,
      { method: 'POST', body: JSON.stringify({ sections }) }),
  pdf: (org: number, id: number) => api<Blob>(`${item(org, id)}/resultados.pdf`),
}
