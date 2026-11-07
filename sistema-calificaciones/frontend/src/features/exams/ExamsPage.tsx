import { BackLink } from '../../components/BackLink'
import { useTableState } from '../../hooks/useTableState'
import { Notice } from '../../components/Notice'
import { useCallback, useState } from 'react'
import { ArrowLeft, Download, Search } from 'lucide-react'
import { Link, useParams, useLocation } from 'react-router'
import { evaluationsApi } from '../../api/evaluations'
import { examsApi } from '../../api/exams'
import { useAuth } from '../../auth/useAuth'
import { Pagination } from '../../components/Pagination'
import { PageHeader, StatusBadge } from '../../components/ui'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { useResource } from '../../hooks/useResource'
import { downloadBlob, errorMessage, scoreFormatter } from '../../utils/format'
import { groupLabel } from '../../utils/group'
import { resultLanguage } from '../../utils/evaluation'

export function ExamsPage() {
  const id = Number(useParams().evaluationId)
  const location = useLocation()
  const { organization } = useAuth()
  const org = organization!.id
  const table = useTableState({ code: '', groupId: null as number | null, status: '', order: 'puntaje-desc', page: 0, size: 20 }, 'exams')
  const { code, groupId, status, order, page, size } = table.values
  const setSize = (value: number) => table.set('size', value)
  const setCode = (value: string) => table.set('code', value)
  const setGroupId = (value: number | null) => table.set('groupId', value)
  const setStatus = (value: string) => table.set('status', value)
  const setOrder = (value: string) => table.set('order', value)
  const setPage = (value: number) => table.set('page', value)
  const search = useDebouncedValue(code)
  const load = useCallback(() => examsApi.page(org, id,
    { code: search, groupId, status, order, page, size }), [org, id, search, groupId, status, order, page, size])
  const { data, loading, error, reload } = useResource(load)
  const loadEvaluation = useCallback(() => evaluationsApi.overview(org, id), [org, id])
  const { data: evaluation } = useResource(loadEvaluation)
  const label = groupLabel(evaluation?.groupType)
  const language = resultLanguage(evaluation?.type)
  const loadGroups = useCallback(() => evaluationsApi.groups(org, id), [org, id])
  const { data: groups } = useResource(loadGroups)
  const [pdfError, setPdfError] = useState<string | null>(null)

  async function downloadPdf() {
    setPdfError(null)
    try { downloadBlob(await evaluationsApi.pdf(org, id), `resultados-${id}.pdf`) }
    catch (cause) { setPdfError(errorMessage(cause)) }
  }

  return <div className="page-stack">
    <BackLink to={`/evaluaciones/${id}`} className="back-link"><ArrowLeft size={17} /> Volver a la evaluación</BackLink>
    <PageHeader eyebrow={evaluation?.code ?? 'EVALUACIÓN'} title="Exámenes"
      description={evaluation?.name ?? 'Consulta puntajes, puestos e incidencias.'}
      action={<button type="button" className="button button--secondary" onClick={() => void downloadPdf()}>
        <Download size={16} /> Descargar PDF</button>} />
    {pdfError && <Notice tone="error">{pdfError}</Notice>}
    <section className="surface-card">
      <div className="list-toolbar">
        <div><p className="section-kicker">RESULTADOS</p><h2>Hojas procesadas</h2><p>{data?.total ?? 0} exámenes</p></div>
        <div className="list-toolbar__controls">
          <label className="search-field"><Search size={17} /><span className="sr-only">Buscar examen</span>
            <input value={code} onChange={(event) => { setCode(event.target.value); setPage(0) }}
              placeholder="Código, hoja o nombre…" /></label>
          <label className="select-field"><span className="sr-only">{label}</span>
            <select value={groupId ?? ''} onChange={(event) => { setGroupId(event.target.value ? Number(event.target.value) : null); setPage(0) }}>
              <option value="">Todas las opciones</option>{groups?.map((group) => <option key={group.id} value={group.id}>{group.name}</option>)}
            </select></label>
          <label className="select-field"><span className="sr-only">Estado</span>
            <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0) }}>
              <option value="">Todos los estados</option><option value="VALIDADA">Válidos</option>
              <option value="OBSERVADA">Observados</option><option value="ANULADA">Anulados</option>
              <option value="RECIBIDA">Recibidos</option>
            </select></label>
          <label className="select-field"><span className="sr-only">Orden</span>
            <select value={order} onChange={(event) => { setOrder(event.target.value); setPage(0) }}>
              <option value="puntaje-desc">Mayor puntaje</option><option value="puntaje-asc">Menor puntaje</option>
              <option value="fecha-desc">Más recientes</option>
            </select></label>
        </div>
      </div>
      {error && <Notice tone="error">{error} <button type="button" onClick={reload}>Reintentar</button></Notice>}
      {loading && <p className="loading-inline">Consultando exámenes…</p>}
      {data && <><div className="table-scroll" aria-busy={loading} inert={loading}><table className="data-table">
        <thead><tr><th>Código / participante</th><th>{label}</th><th>Tema</th><th>Correctas</th>
          <th>Incorrectas</th><th>Blancas</th><th>Puntaje</th><th>Puesto</th><th>{language.heading}</th><th>Estado</th><th>Acción</th></tr></thead>
        <tbody>{data.items.map((exam) => <tr key={exam.id}>
          <td><strong className="table-title">{exam.participantName}</strong>
            <span className="table-subtitle">{exam.participantCode} · Hoja {exam.sheetCode ?? '—'}</span></td>
          <td>{exam.groupName ?? '—'}</td><td>{exam.formCode}</td>
          <td>{exam.correct ?? '—'}</td><td>{exam.incorrect ?? '—'}</td><td>{exam.blank ?? '—'}</td>
          <td><strong className="score-value">{exam.score === null ? '—' : scoreFormatter.format(exam.score)}</strong></td>
          <td>{exam.position ?? '—'}</td><td>{exam.passed === null ? '—' : exam.passed ? 'Sí' : 'No'}</td>
          <td><StatusBadge status={exam.status} /></td>
          <td><Link className="text-link" state={{ ...location.state, fromCases: false }} to={`/evaluaciones/${id}/examenes/${exam.id}${location.search}`}>Revisar</Link></td>
        </tr>)}</tbody>
      </table>{data.items.length === 0 && <div className="empty-state">No hay exámenes con esos criterios.</div>}</div>
        <Pagination result={data} onPage={setPage} size={size} onSize={setSize} busy={loading} /></>}
    </section>
  </div>
}
