import { api, params } from './client'
import type { Page } from './types'
import type { AnalyticsDashboard, AnalyticsScope, AnalyticsTableOptions, GroupStats, QuestionStats, ReportExport } from '../features/analytics/types'

const root = (org: number, evaluation: number) => `/organizaciones/${org}/evaluaciones/${evaluation}/informes`

export const analyticsApi = {
  dashboard: (org: number, evaluation: number, scope: AnalyticsScope) =>
    api<AnalyticsDashboard>(`${root(org, evaluation)}/resumen?${params({ ...scope })}`),
  groups: (org: number, evaluation: number, scope: AnalyticsScope, options: AnalyticsTableOptions) =>
    api<Page<GroupStats>>(`${root(org, evaluation)}/grupos/pagina?${params({ ...scope, ...options })}`),
  questions: (org: number, evaluation: number, scope: AnalyticsScope, options: AnalyticsTableOptions & { order: string }) =>
    api<Page<QuestionStats>>(`${root(org, evaluation)}/preguntas/pagina?${params({ ...scope, ...options })}`),
  export: (org: number, evaluation: number, scope: AnalyticsScope, report: ReportExport,
    options: { query?: string; mode?: string; order?: string } = {}) =>
    api<Blob>(`${root(org, evaluation)}/${report}?${params({ ...scope, ...options })}`),
}
