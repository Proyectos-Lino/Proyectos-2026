import { useTableState } from '../hooks/useTableState'
import { Notice } from '../components/Notice'
import { useCallback } from 'react'
import { AlertCircle, ArrowRight, CheckCheck, ClipboardList, FileCheck2, Plus, Search } from 'lucide-react'
import { Link } from 'react-router'
import { evaluationsApi } from '../api/evaluations'
import { useAuth } from '../auth/useAuth'
import { EvaluationTable } from '../components/EvaluationTable'
import { Pagination } from '../components/Pagination'
import { ActionLink, PageHeader, TextLink } from '../components/ui'
import { useResource } from '../hooks/useResource'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { numberFormatter } from '../utils/format'
import { evaluationStatuses } from '../utils/evaluation'

const currentDateLabel = new Intl.DateTimeFormat('es-PE', {
  weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
}).format(new Date()).toLocaleUpperCase('es-PE')

export function DashboardPage() {
  const { organization } = useAuth()
  const org = organization!.id
  const canOperate = ['ADMINISTRADOR', 'OPERADOR'].includes(organization!.role)
  const table = useTableState({ query: '', status: '', page: 0, size: 3 }, 'dashboard')
  const { query, status, page, size } = table.values
  const setSize = (value: number) => table.set('size', value)
  const setQuery = (value: string) => table.set('query', value)
  const setStatus = (value: string) => table.set('status', value)
  const setPage = (value: number) => table.set('page', value)
  const search = useDebouncedValue(query)
  const load = useCallback(async () => {
    const [statistics, recent] = await Promise.all([
      evaluationsApi.statistics(org),
      evaluationsApi.page(org, { query: search, status, page, size }),
    ])
    return { statistics, recent }
  }, [org, search, status, page, size])
  const { data, loading, error, reload } = useResource(load)
  const metrics = data ? [
    { label: 'Evaluaciones', value: data.statistics.evaluations, detail: 'En esta organización', icon: ClipboardList, tone: 'blue' },
    { label: 'Exámenes procesados', value: data.statistics.exams, detail: 'De todas las evaluaciones', icon: FileCheck2, tone: 'mint' },
    { label: 'Requieren revisión', value: data.statistics.observed, detail: 'Hojas observadas', icon: AlertCircle, tone: 'amber' },
    { label: 'Resultados publicados', value: data.statistics.published, detail: 'Evaluaciones publicadas', icon: CheckCheck, tone: 'purple' },
  ] : []

  return (
    <div className="page-stack">
      <PageHeader eyebrow={currentDateLabel} title="Centro de evaluaciones"
        description="Sigue tus procesos, resultados y casos que necesitan atención."
        action={canOperate ? <ActionLink to="/evaluaciones/nueva"><Plus size={17} /> Nueva evaluación</ActionLink> : undefined} />
      <section className="surface-card getting-started" aria-labelledby="hero-title">
        <div><p className="section-kicker">TU ESPACIO DE TRABAJO</p><h2 id="hero-title">Continúa con el siguiente paso</h2>
          <p>{data?.statistics.observed ? `${data.statistics.observed} hojas requieren revisión. Abre la evaluación correspondiente para resolver sus casos.`
            : data?.statistics.evaluations === 0 ? 'Comienza creando una evaluación. La lista de preparación te guiará hasta publicar los resultados.'
              : 'Abre una evaluación para consultar sus pendientes, resultados y próximos pasos.'}</p></div>
        <div className="list-toolbar__controls"><Link className="button button--primary" to="/evaluaciones">Abrir evaluaciones <ArrowRight size={17} /></Link>
          <Link className="button button--secondary" to="/participantes?participants.profile=AUTOMATICOS">Completar participantes</Link></div>
      </section>
      {error && <Notice tone="error">{error} <button type="button" onClick={reload}>Reintentar</button></Notice>}
      {loading && <p className="loading-inline">Cargando indicadores…</p>}
      {data && <>
        <section aria-label="Indicadores principales" className="metric-grid">
          {metrics.map(({ label, value, detail, icon: Icon, tone }) => (
            <article key={label} className="metric-card">
              <span className={`metric-card__icon metric-card__icon--${tone}`}><Icon size={21} strokeWidth={1.8} aria-hidden="true" /></span>
              <p className="metric-card__label">{label}</p>
              <strong className="metric-card__value">{numberFormatter.format(value)}</strong>
              <p className="metric-card__detail">{detail}</p>
            </article>
          ))}
        </section>
        <section className="surface-card" aria-labelledby="recent-title">
          <div className="surface-card__header">
            <div><p className="section-kicker">SEGUIMIENTO</p><h2 id="recent-title">Evaluaciones recientes</h2></div>
            <TextLink to="/evaluaciones">Ver todas</TextLink>
          </div>
          <div className="list-toolbar">
            <label className="search-field"><Search size={17} /><span className="sr-only">Buscar evaluación</span>
              <input value={query} onChange={(event) => { setQuery(event.target.value); setPage(0) }}
                placeholder="Buscar evaluación" /></label>
            <label className="select-field"><span className="sr-only">Filtrar estado</span>
              <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0) }}>
                <option value="">Todos los estados</option>{Object.entries(evaluationStatuses).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select></label>
          </div>
          <EvaluationTable items={data.recent.items} busy={loading} />
          <Pagination result={data.recent} onPage={setPage} size={size} onSize={setSize} busy={loading} />
        </section>
      </>}
    </div>
  )
}
