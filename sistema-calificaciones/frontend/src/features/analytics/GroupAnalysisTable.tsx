import { useTableState } from '../../hooks/useTableState'
import { Notice } from '../../components/Notice'
import { useCallback } from 'react'
import { Download, Search } from 'lucide-react'
import { analyticsApi } from '../../api/analytics'
import { Pagination } from '../../components/Pagination'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { useResource } from '../../hooks/useResource'
import { numberFormatter, scoreFormatter } from '../../utils/format'
import { useReportExport } from './useReportExport'
import type { AnalyticsScope } from './types'
import { resultLanguage } from '../../utils/evaluation'

const points = (value: number | null) => value === null ? '—' : scoreFormatter.format(value)

export function GroupAnalysisTable({ org, evaluation, scope, evaluationType }: { org: number; evaluation: number; scope: AnalyticsScope; evaluationType?: string }) {
  const language = resultLanguage(evaluationType)
  const table = useTableState({ query: '', mode: '', page: 0, size: 10 }, 'analysisGroups')
  const { query, mode, page, size } = table.values
  const setSize = (value: number) => table.set('size', value)
  const setQuery = (value: string) => table.set('query', value)
  const setMode = (value: string) => table.set('mode', value)
  const setPage = (value: number) => table.set('page', value)
  const search = useDebouncedValue(query)
  const load = useCallback(() => analyticsApi.groups(org, evaluation, scope, { query: search, mode, page, size }),
    [org, evaluation, scope, search, mode, page, size])
  const { data, error, loading, reload } = useResource(load)
  const report = useReportExport(org, evaluation, scope)
  return <section className="surface-card">
    <div className="list-toolbar"><div><p className="section-kicker">COMPARACIÓN</p><h2>Resultados por grupo</h2>
      <p>{numberFormatter.format(data?.total ?? 0)} grupos · Promedios de resultados válidos</p></div>
      <button type="button" className="button button--secondary" disabled={report.busy || query !== search}
        onClick={() => void report.download('grupos.csv', { query: search, mode })}>
        <Download size={16} /> {report.busy ? 'Exportando…' : 'Exportar grupos'}</button></div>
    <div className="analytics-table-filters">
      <label className="search-field"><Search size={16} /><span className="sr-only">Buscar grupo</span>
        <input value={query} onChange={(event) => { setQuery(event.target.value); setPage(0) }} placeholder="Código o nombre de grupo…" /></label>
      <label className="select-field"><span className="sr-only">Filtrar grupos por resultados</span>
        <select value={mode} onChange={(event) => { setMode(event.target.value); setPage(0) }}>
          <option value="">Todos los grupos</option><option value="CON_RESULTADOS">Con resultados válidos</option>
          <option value="SIN_RESULTADOS">Sin resultados válidos</option>
        </select></label>
    </div>
    {(error || report.error) && <Notice tone="error">{error || report.error}
      {error && <button type="button" onClick={reload}>Reintentar</button>}</Notice>}
    {loading && <p className="loading-inline">Consultando grupos…</p>}
    {data && <><div className="table-scroll" aria-busy={loading} inert={loading}><table className="data-table"><thead><tr>
      <th>Grupo</th><th>Inscritos</th><th>Exámenes</th><th>Válidos</th><th>Promedio</th><th>Mín. / máx.</th>
      <th>{language.people}</th><th>{language.negativePeople}</th><th>Sin decisión</th><th>Observados / anulados</th>
    </tr></thead><tbody>{data.items.map((row) => <tr key={row.groupId ?? 'ungrouped'}>
      <td><strong className="table-title">{row.name}</strong><span className="table-subtitle">{row.code || 'Sin asignación'}</span></td>
      <td>{row.registrations}</td><td>{row.sheets}</td><td>{row.scored}</td>
      <td><strong className="score-value">{points(row.average)}</strong></td>
      <td>{points(row.minimum)} / {points(row.maximum)}</td><td>{row.passed}</td><td>{row.failed}</td>
      <td>{row.undecided}</td><td>{row.observed} / {row.annulled}</td>
    </tr>)}</tbody></table>{data.items.length === 0 && <div className="empty-state">No hay grupos con esos criterios.</div>}</div>
      <Pagination result={data} onPage={setPage} size={size} onSize={setSize} busy={loading} /></>}
  </section>
}
