import { useTableState } from '../../hooks/useTableState'
import { Notice } from '../../components/Notice'
import { useCallback } from 'react'
import { Download, Search } from 'lucide-react'
import { analyticsApi } from '../../api/analytics'
import { Pagination } from '../../components/Pagination'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { useResource } from '../../hooks/useResource'
import { numberFormatter } from '../../utils/format'
import { useReportExport } from './useReportExport'
import type { AnalyticsScope } from './types'

export function QuestionAnalysisTable({ org, evaluation, scope }: { org: number; evaluation: number; scope: AnalyticsScope }) {
  const table = useTableState({ query: '', mode: '', order: 'errores', page: 0, size: 10 }, 'analysisQuestions')
  const { query, mode, order, page, size } = table.values
  const setSize = (value: number) => table.set('size', value)
  const setQuery = (value: string) => table.set('query', value)
  const setMode = (value: string) => table.set('mode', value)
  const setOrder = (value: string) => table.set('order', value)
  const setPage = (value: number) => table.set('page', value)
  const search = useDebouncedValue(query)
  const load = useCallback(() => analyticsApi.questions(org, evaluation, scope, { query: search, mode, order, page, size }),
    [org, evaluation, scope, search, mode, order, page, size])
  const { data, error, loading, reload } = useResource(load)
  const report = useReportExport(org, evaluation, scope)
  return <section className="surface-card">
    <div className="list-toolbar"><div><p className="section-kicker">ANÁLISIS DE PREGUNTAS</p><h2>Preguntas con más errores</h2>
      <p>{numberFormatter.format(data?.total ?? 0)} preguntas por tema y versión de clave</p></div>
      <button type="button" className="button button--secondary" disabled={report.busy || query !== search}
        onClick={() => void report.download('preguntas.csv', { query: search, mode, order })}>
        <Download size={16} /> {report.busy ? 'Exportando…' : 'Exportar preguntas'}</button></div>
    <div className="analytics-table-filters">
      <label className="search-field"><Search size={16} /><span className="sr-only">Buscar tema o pregunta</span>
        <input value={query} onChange={(event) => { setQuery(event.target.value); setPage(0) }} placeholder="Tema o número de pregunta…" /></label>
      <label className="select-field"><span className="sr-only">Filtrar preguntas</span>
        <select value={mode} onChange={(event) => { setMode(event.target.value); setPage(0) }}>
          <option value="">Todas las preguntas</option><option value="CON_ERRORES">Con respuestas incorrectas</option>
          <option value="SIN_ERRORES">Sin respuestas incorrectas</option></select></label>
      <label className="select-field"><span className="sr-only">Ordenar preguntas</span>
        <select value={order} onChange={(event) => { setOrder(event.target.value); setPage(0) }}>
          <option value="errores">Mayor porcentaje de errores</option><option value="pregunta">Número de pregunta</option>
        </select></label>
    </div>
    <p className="analytics-table-note">Se usa la clave guardada con cada resultado. Las respuestas en blanco se muestran por separado;
      una pregunta libre en la clave cuenta como correcta, según la regla de calificación.</p>
    {(error || report.error) && <Notice tone="error">{error || report.error}
      {error && <button type="button" onClick={reload}>Reintentar</button>}</Notice>}
    {loading && <p className="loading-inline">Analizando respuestas…</p>}
    {data && <><div className="table-scroll" aria-busy={loading} inert={loading}><table className="data-table"><thead><tr>
      <th>Tema / clave</th><th>Pregunta</th><th>Respuestas</th><th>Correctas</th><th>Incorrectas</th><th>Blancas</th><th>Errores (%)</th>
    </tr></thead><tbody>{data.items.map((row) => <tr key={`${row.formId}-${row.keyVersion}-${row.question}`}>
      <td><strong className="table-title">{row.formCode}</strong><span className="table-subtitle">Clave v{row.keyVersion}</span></td>
      <td>{row.question}</td><td>{row.responses}</td><td>{row.correct}</td><td>{row.incorrect}</td><td>{row.blank}</td>
      <td><div className="analytics-error-rate"><strong>{row.incorrectPercent.toLocaleString('es-PE', { maximumFractionDigits: 2 })}%</strong>
        <span><i style={{ width: `${row.incorrectPercent}%` }} /></span></div></td>
    </tr>)}</tbody></table>{data.items.length === 0 && <div className="empty-state">No hay preguntas con esos criterios.</div>}</div>
      <Pagination result={data} onPage={setPage} size={size} onSize={setSize} busy={loading} /></>}
  </section>
}
