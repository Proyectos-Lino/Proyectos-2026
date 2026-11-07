import { useTableState } from '../hooks/useTableState'
import { evaluationStatuses } from '../utils/evaluation'
import { Notice } from '../components/Notice'
import { useCallback } from 'react'
import { Plus, Search, SlidersHorizontal } from 'lucide-react'
import { evaluationsApi } from '../api/evaluations'
import { useAuth } from '../auth/useAuth'
import { EvaluationTable } from '../components/EvaluationTable'
import { Pagination } from '../components/Pagination'
import { ActionLink, PageHeader } from '../components/ui'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { useResource } from '../hooks/useResource'

export function EvaluationsPage() {
  const { organization } = useAuth()
  const org = organization!.id
  const canOperate = ['ADMINISTRADOR', 'OPERADOR'].includes(organization!.role)
  const table = useTableState({ query: '', status: '', page: 0, size: 10 }, 'evaluations')
  const { query, status, page, size } = table.values
  const setSize = (value: number) => table.set('size', value)
  const setQuery = (value: string) => table.set('query', value)
  const setStatus = (value: string) => table.set('status', value)
  const setPage = (value: number) => table.set('page', value)
  const search = useDebouncedValue(query)
  const load = useCallback(() => evaluationsApi.page(org, { query: search, status, page, size }),
    [org, search, status, page, size])
  const { data, loading, error, reload } = useResource(load)

  return (
    <div className="page-stack">
      <PageHeader eyebrow="ESPACIO DE TRABAJO / EVALUACIONES" title="Evaluaciones"
        description="Consulta el estado de cada proceso y encuentra rápidamente lo que necesitas."
        action={canOperate ? <ActionLink to="/evaluaciones/nueva"><Plus size={17} /> Nueva evaluación</ActionLink> : undefined} />
      <section className="surface-card">
        <div className="list-toolbar">
          <div><p className="section-kicker">PROCESOS</p><h2>Todos los procesos</h2>
            <p>{data ? `${data.total} evaluaciones` : 'Cargando evaluaciones…'}</p></div>
          <div className="list-toolbar__controls">
            <label className="search-field"><Search size={18} aria-hidden="true" />
              <span className="sr-only">Buscar evaluaciones</span>
              <input value={query} onChange={(event) => { setQuery(event.target.value); setPage(0) }}
                placeholder="Nombre, código o tipo…" /></label>
            <label className="select-field"><SlidersHorizontal size={17} aria-hidden="true" />
              <span className="sr-only">Filtrar por estado</span>
              <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0) }}>
                <option value="">Todos los estados</option>
                {Object.entries(evaluationStatuses).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </label>
          </div>
        </div>
        {error && <Notice tone="error">{error} <button type="button" onClick={reload}>Reintentar</button></Notice>}
        {loading && <p className="loading-inline">Consultando evaluaciones…</p>}
        {data && <><EvaluationTable items={data.items} busy={loading} /><Pagination result={data} onPage={setPage} size={size} onSize={setSize} busy={loading} /></>}
      </section>
    </div>
  )
}
