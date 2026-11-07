import { BackLink } from '../../components/BackLink'
import { useTableState } from '../../hooks/useTableState'
import { Notice } from '../../components/Notice'
import { useCallback, useState } from 'react'
import { ArrowLeft, Search } from 'lucide-react'
import { Link, useParams, useLocation } from 'react-router'
import type { ReviewCase } from '../../api/types'
import { reviewsApi } from '../../api/reviews'
import { useAuth } from '../../auth/useAuth'
import { Modal } from '../../components/Modal'
import { Pagination } from '../../components/Pagination'
import { PageHeader, StatusBadge } from '../../components/ui'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { useResource } from '../../hooks/useResource'
import { errorMessage } from '../../utils/format'

function AssignmentModal({ org, evaluation, review, onClose, onSaved }: {
  org: number; evaluation: number; review: ReviewCase; onClose: () => void; onSaved: () => void
}) {
  const table = useTableState({ query: '', page: 0, size: 10 }, 'assignees')
  const { query, page, size } = table.values
  const setQuery = (value: string) => table.set('query', value)
  const setPage = (value: number) => table.set('page', value)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const search = useDebouncedValue(query)
  const load = useCallback(() => reviewsApi.assignees(org, evaluation, search, page, size), [org, evaluation, search, page, size])
  const { data, loading, error: loadError } = useResource(load)

  async function assign(userId: number | null) {
    setBusy(true); setError(null)
    try { await reviewsApi.assign(org, evaluation, review.id, userId); onSaved() }
    catch (cause) { setError(errorMessage(cause)) }
    finally { setBusy(false) }
  }

  return <Modal title="Asignar responsable" description={`Revisión del examen ${review.sheetCode ?? review.sheetId}`}
    onClose={onClose} busy={busy}>
    <div className="modal-form">
      <label className="field"><span>Buscar administrador o revisor</span>
        <input value={query} onChange={(event) => { setQuery(event.target.value); setPage(0) }}
          placeholder="Nombre del responsable" /></label>
      {loadError && <Notice tone="error">{loadError}</Notice>}
      {loading && <p className="loading-inline">Buscando responsables…</p>}
      <div className="review-list">{data?.items.map((person) => <button key={person.id} type="button"
        className="button button--secondary" disabled={busy} onClick={() => void assign(person.id)}>
        {person.name} · {person.role === 'ADMINISTRADOR' ? 'Administrador' : 'Revisor'}
      </button>)}</div>
      {data?.items.length === 0 && <p className="muted-copy">No hay responsables con ese nombre.</p>}
      {data && <Pagination result={data} onPage={setPage} size={size} onSize={(value) => table.set('size', value)} busy={loading} />}
      {review.assigneeId && <button type="button" className="button button--secondary" disabled={busy}
        onClick={() => void assign(null)}>Quitar asignación</button>}
      {error && <Notice tone="error">{error}</Notice>}
    </div>
  </Modal>
}

export function ReviewCasesPage() {
  const { evaluationId } = useParams()
  const evaluation = Number(evaluationId)
  const location = useLocation()
  const { organization } = useAuth()
  const org = organization!.id
  const canReview = ['ADMINISTRADOR', 'REVISOR'].includes(organization!.role)
  const table = useTableState({ query: '', status: '', assignment: 'ALL', page: 0, size: 20 }, 'cases')
  const { query, status, assignment, page, size } = table.values
  const setSize = (value: number) => table.set('size', value)
  const setQuery = (value: string) => table.set('query', value)
  const setStatus = (value: string) => table.set('status', value)
  const setPage = (value: number) => table.set('page', value)
  const [selected, setSelected] = useState<ReviewCase | null>(null)
  const search = useDebouncedValue(query)
  const load = useCallback(() => reviewsApi.cases(org, evaluation, { query: search, status, assignment, page, size }),
    [org, evaluation, search, status, assignment, page, size])
  const { data, loading, error, reload } = useResource(load)

  return <div className="page-stack">
    <BackLink to={`/evaluaciones/${evaluation}`} className="back-link"><ArrowLeft size={17} /> Volver a evaluación</BackLink>
    <PageHeader eyebrow="REVISIÓN" title="Bandeja de casos"
      description="Consulta los exámenes observados, asigna un responsable y registra la decisión con sus evidencias." />
    <section className="surface-card">
      <div className="list-toolbar"><div><p className="section-kicker">CASOS</p><h2>Revisiones</h2>
        <p>{data?.total ?? 0} casos con los filtros actuales</p></div>
        <div className="list-toolbar__controls">
          <label className="select-field"><span className="sr-only">Filtrar por responsable</span><select value={assignment} onChange={(event) => table.set('assignment', event.target.value)}>
            <option value="ALL">Todos los responsables</option><option value="MINE">Mis casos</option><option value="UNASSIGNED">Sin responsable</option></select></label>
          <label className="search-field"><Search size={17} /><span className="sr-only">Buscar caso</span>
            <input value={query} onChange={(event) => { setQuery(event.target.value); setPage(0) }}
              placeholder="Código, participante o motivo…" /></label>
          <label className="select-field"><span className="sr-only">Filtrar estado</span>
            <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0) }}>
              <option value="">Todos los estados</option><option value="PENDIENTES">Pendientes</option>
              <option value="ABIERTA">Sin asignar</option><option value="EN_REVISION">En revisión</option>
              <option value="APROBADA">Validados</option>
              <option value="ANULADA">Anulados</option><option value="RECHAZADA">Rechazados</option>
            </select></label>
        </div></div>
      {error && <Notice tone="error">{error}</Notice>}
      {loading && <p className="loading-inline">Consultando casos…</p>}
      {data && <><div className="table-scroll" aria-busy={loading} inert={loading}><table className="data-table"><thead><tr>
        <th>Participante</th><th>Examen</th><th>Motivo</th><th>Estado</th><th>Responsable</th><th>Acciones</th>
      </tr></thead><tbody>{data.items.map((item) => <tr key={item.id}>
        <td><strong className="table-title">{item.participantName}</strong><span className="table-subtitle">{item.participantCode}</span></td>
        <td>{item.sheetCode ?? item.sheetId}</td><td>{item.reason}</td>
        <td><StatusBadge status={item.status} /></td><td>{item.assigneeName ?? 'Sin asignar'}</td>
        <td><Link className="text-link" state={{ ...location.state, fromCases: true }} to={`/evaluaciones/${evaluation}/examenes/${item.sheetId}${location.search}`}>Abrir</Link>
          {canReview && ['ABIERTA', 'EN_REVISION'].includes(item.status) && <button type="button"
            className="text-link" onClick={() => setSelected(item)}>Asignar</button>}</td>
      </tr>)}</tbody></table>{data.items.length === 0 && <div className="empty-state">No hay casos con esos criterios.</div>}</div>
        <Pagination result={data} onPage={setPage} size={size} onSize={setSize} busy={loading} /></>}
    </section>
    {selected && <AssignmentModal key={selected.id} org={org} evaluation={evaluation} review={selected}
      onClose={() => setSelected(null)} onSaved={() => { setSelected(null); reload() }} />}
  </div>
}
