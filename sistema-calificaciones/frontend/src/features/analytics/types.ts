export interface AnalyticsScope { groupId: number | null; formId: number | null }
export interface AnalyticsSummary {
  registrations: number; sheets: number; valid: number; observed: number; annulled: number
  received: number; scored: number; passed: number; failed: number; undecided: number
  average: number | null; median: number | null; minimum: number | null; maximum: number | null; deviation: number | null
}
export interface ScoreBin { index: number; lower: number; upper: number; count: number }
export interface AnalyticsDashboard { summary: AnalyticsSummary; distribution: ScoreBin[]; generatedAt: string }
export interface GroupStats {
  groupId: number | null; code: string; name: string; registrations: number; sheets: number; scored: number
  observed: number; annulled: number; passed: number; failed: number; undecided: number
  average: number | null; minimum: number | null; maximum: number | null
}
export interface QuestionStats {
  formId: number; formCode: string; keyVersion: number; question: number; responses: number
  correct: number; incorrect: number; blank: number; incorrectPercent: number
}
export interface AnalyticsTableOptions { query: string; mode: string; page: number; size: number }
export type ReportExport = 'resultados.csv' | 'grupos.csv' | 'preguntas.csv' | 'resumen.pdf'
