import { useState } from 'react'
import { analyticsApi } from '../../api/analytics'
import { downloadBlob, errorMessage } from '../../utils/format'
import type { AnalyticsScope, ReportExport } from './types'

export function useReportExport(org: number, evaluation: number, scope: AnalyticsScope) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  async function download(report: ReportExport, options: { query?: string; mode?: string; order?: string } = {}) {
    setBusy(true); setError(null)
    const suffix = `${scope.groupId === null ? 'todos' : `grupo-${scope.groupId}`}-${scope.formId === null ? 'temas' : `tema-${scope.formId}`}`
    try { downloadBlob(await analyticsApi.export(org, evaluation, scope, report, options), `${evaluation}-${suffix}-${report}`) }
    catch (cause) { setError(errorMessage(cause)) }
    finally { setBusy(false) }
  }
  return { busy, error, download }
}
