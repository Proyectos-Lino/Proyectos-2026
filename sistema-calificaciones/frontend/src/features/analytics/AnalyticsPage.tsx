import { BackLink } from '../../components/BackLink'
import { Notice } from '../../components/Notice'
import { useCallback, useMemo, useState } from 'react'
import { ArrowLeft, Download, RefreshCw } from 'lucide-react'
import { useParams } from 'react-router'
import { evaluationsApi } from '../../api/evaluations'
import { useAuth } from '../../auth/useAuth'
import { PageHeader, StatusBadge } from '../../components/ui'
import { useResource } from '../../hooks/useResource'
import { groupLabel } from '../../utils/group'
import { AnalyticsOverview } from './AnalyticsOverview'
import { GroupAnalysisTable } from './GroupAnalysisTable'
import { QuestionAnalysisTable } from './QuestionAnalysisTable'
import { useReportExport } from './useReportExport'
import { useTableState } from '../../hooks/useTableState'

function AnalyticsWorkspace({ org, evaluation }: { org: number; evaluation: number }) {
  const table = useTableState({ groupId: null as number | null, formId: null as number | null }, 'scope')
  const { groupId, formId } = table.values
  const setGroupId = (value: number | null) => table.set('groupId', value)
  const setFormId = (value: number | null) => table.set('formId', value)
  const [revision, setRevision] = useState(0)
  const scope = useMemo(() => ({ groupId, formId }), [groupId, formId])
  const loadContext = useCallback(async () => {
    const [overview, groups, forms] = await Promise.all([evaluationsApi.overview(org, evaluation),
      evaluationsApi.groups(org, evaluation), evaluationsApi.forms(org, evaluation)])
    return { overview, groups, forms }
  }, [org, evaluation])
  const { data: context, error, loading, reload } = useResource(loadContext)
  const report = useReportExport(org, evaluation, scope)
  const scopeKey = `${org}-${evaluation}-${groupId}-${formId}-${revision}`
  return <div className="page-stack">
    <BackLink className="back-link" to={`/evaluaciones/${evaluation}`}><ArrowLeft size={17} /> Volver a evaluación</BackLink>
    <PageHeader eyebrow={context?.overview.code ?? 'EVALUACIÓN'} title="Informes y análisis"
      description={context?.overview.name ?? 'Resultados, grupos y desempeño por pregunta.'}
      action={context && <StatusBadge status={context.overview.status} />} />
    {error && <Notice tone="error">{error} <button type="button" onClick={reload}>Reintentar</button></Notice>}
    {loading && <p className="loading-inline">Cargando evaluación…</p>}
    {context && <>
      <section className="surface-card analytics-scope">
        <div><p className="section-kicker">ALCANCE DEL INFORME</p><h2>Explora los resultados</h2>
          <p>Los filtros se aplican a los indicadores, tablas y exportaciones.</p></div>
        <div className="analytics-scope-controls">
          <label className="field"><span>{groupLabel(context.overview.groupType)}</span>
            <select value={groupId ?? ''} onChange={(event) => setGroupId(event.target.value === '' ? null : Number(event.target.value))}>
              <option value="">Todos los grupos</option><option value="0">Sin grupo</option>
              {context.groups.map((group) => <option key={group.id} value={group.id}>{group.name}</option>)}
            </select></label>
          <label className="field"><span>Tema</span>
            <select value={formId ?? ''} onChange={(event) => setFormId(event.target.value === '' ? null : Number(event.target.value))}>
              <option value="">Todos los temas</option>{context.forms.map((form) => <option key={form.id} value={form.id}>{form.code}</option>)}
            </select></label>
          <button type="button" className="button button--secondary" onClick={() => setRevision((value) => value + 1)}>
            <RefreshCw size={16} /> Actualizar</button>
        </div>
        <div className="analytics-export-actions">
          <button type="button" className="button button--primary" disabled={report.busy}
            onClick={() => void report.download('resumen.pdf')}><Download size={16} /> Descargar resumen PDF</button>
          <button type="button" className="button button--secondary" disabled={report.busy}
            onClick={() => void report.download('resultados.csv')}><Download size={16} /> Exportar exámenes CSV</button>
          {report.busy && <span role="status">Preparando archivo…</span>}
        </div>
        {report.error && <Notice tone="error">{report.error}</Notice>}
      </section>
      <p className="analytics-method">Los indicadores de puntaje, decisiones y preguntas consideran solo resultados de exámenes válidos.
        El CSV de exámenes también incluye los estados observados y anulados.
        {formId !== null && ' Al filtrar por tema, las inscripciones corresponden a personas con hoja en ese tema.'}</p>
      <AnalyticsOverview key={`overview-${scopeKey}`} org={org} evaluation={evaluation} scope={scope} evaluationType={context.overview.type} />
      <GroupAnalysisTable key={`groups-${scopeKey}`} org={org} evaluation={evaluation} scope={scope} evaluationType={context.overview.type} />
      <QuestionAnalysisTable key={`questions-${scopeKey}`} org={org} evaluation={evaluation} scope={scope} />
    </>}
  </div>
}

export function AnalyticsPage() {
  const evaluation = Number(useParams().evaluationId)
  const { organization } = useAuth()
  return <AnalyticsWorkspace key={`${organization!.id}-${evaluation}`} org={organization!.id} evaluation={evaluation} />
}
