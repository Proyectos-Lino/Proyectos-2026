import { useCallback, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { Plus, Search, Upload } from 'lucide-react'
import { evaluationsApi } from '../../api/evaluations'
import { useAuth } from '../../auth/useAuth'
import { Notice } from '../../components/Notice'
import { Pagination } from '../../components/Pagination'
import { PageHeader, StatusBadge } from '../../components/ui'
import { useResource } from '../../hooks/useResource'
import { useTableState } from '../../hooks/useTableState'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { useEvaluationRevision } from '../../hooks/useEvaluationRevision'
import { useNoticeMessage } from '../../hooks/useNoticeMessage'
import { groupLabel } from '../../utils/group'
import { participantsApi } from './participantsApi'
import type { Enrollment } from './types'
import { EnrollmentModal } from './EnrollmentModal'
import { EnrollmentImportModal } from './EnrollmentImportModal'
import { EnrollmentImportReportModal } from './EnrollmentImportReportModal'

export function EvaluationParticipants({ org, evaluation, onChangeEvaluation }: { org: number; evaluation: number; onChangeEvaluation: () => void }) {
  const { organization } = useAuth()
  const location = useLocation()
  const revision = useEvaluationRevision(org, evaluation)
  const loadContext = useCallback(async () => {
    const [overview, groups, preparation] = await Promise.all([evaluationsApi.overview(org, evaluation), evaluationsApi.groups(org, evaluation), evaluationsApi.preparation(org, evaluation)])
    return { overview, groups, preparation }
  }, [org, evaluation])
  const { data: context, error: contextError, loading: contextLoading, reload: reloadContext } = useResource(loadContext, revision)
  const loadSummary = useCallback(() => participantsApi.summary(org, evaluation), [org, evaluation])
  const { data: summary, error: summaryError, reload: reloadSummary } = useResource(loadSummary, revision)
  const table = useTableState({ query: '', profile: '', document: '', groupId: null as number | null, page: 0, size: 20 }, 'participants')
  const { query, profile, document, groupId, page, size } = table.values
  const search = useDebouncedValue(query)
  const load = useCallback(() => participantsApi.page(org, evaluation, { query: search, profile, document, groupId, page, size }), [org, evaluation, search, profile, document, groupId, page, size])
  const { data, error, loading, reload } = useResource(load)
  const reportState = useTableState({ id: null as number | null }, 'enrollmentImport')
  const [selected, setSelected] = useState<Enrollment | null>(null)
  const [editOpen, setEditOpen] = useState(false)
  const [importOpen, setImportOpen] = useState(false)
  const [reportOpen, setReportOpen] = useState(false)
  const [message, setMessage] = useNoticeMessage()
  const mutable = context?.preparation.editable && ['ADMINISTRADOR', 'OPERADOR'].includes(organization!.role)
  const label = groupLabel(context?.overview.groupType)
  function refreshed() { reload(); reloadSummary(); reloadContext() }
  return <div className="page-stack"><PageHeader eyebrow={context?.overview.code ?? 'EVALUACIÓN'} title="Participantes" description={context?.overview.name ?? 'Consulta las inscripciones de esta evaluación.'}
    action={<div className="list-toolbar__controls"><button type="button" className="button button--secondary" onClick={onChangeEvaluation}>Cambiar evaluación</button>
      {mutable && <><button type="button" className="button button--secondary" disabled={contextLoading} onClick={() => setImportOpen(true)}><Upload size={17} /> Importar CSV</button>
        <button type="button" className="button button--primary" disabled={contextLoading} onClick={() => { setSelected(null); setEditOpen(true) }}><Plus size={17} /> Registrar participante</button></>}</div>} />
    {contextError && <Notice tone="error">{contextError} <button type="button" onClick={reloadContext}>Reintentar</button></Notice>}
    {contextLoading && !context && <p role="status">Cargando evaluación…</p>}
    {context && <div className="participant-context"><StatusBadge status={context.overview.status} /><span>Los códigos y grupos de esta lista pertenecen a <strong>{context.overview.name}</strong>.</span>
      <Link className="text-link" state={location.state} to={`/evaluaciones/${evaluation}${location.search}`}>Abrir evaluación</Link></div>}
    {context && !context.preparation.editable && <p className="muted-copy">Las inscripciones están protegidas porque la evaluación fue aprobada, publicada o archivada. Puedes consultar sus datos e informes.</p>}
    {message && <Notice tone="success">{message}</Notice>}{summaryError && <Notice tone="error">{summaryError}</Notice>}
    {summary && <div className="detail-summary"><div><span>Inscritos en la evaluación</span><strong>{summary.total}</strong></div>
      <div><span>Datos pendientes</span><strong>{summary.incomplete}</strong></div><div><span>{context?.groups.length ? `Sin ${label.toLowerCase()}` : 'Grupos configurados'}</span><strong>{context?.groups.length ? summary.withoutGroup : 0}</strong></div><div><span>Sin correo</span><strong>{summary.withoutEmail}</strong></div></div>}
    {reportState.values.id && <button type="button" className="text-link" onClick={() => setReportOpen(true)}>Ver detalle de la última importación</button>}
    <section className="surface-card"><div className="list-toolbar"><div><p className="section-kicker">INSCRIPCIONES</p><h2>Participantes de la evaluación</h2><p>{data?.total ?? 0} con los filtros actuales</p></div>
      <div className="list-toolbar__controls"><label className="search-field"><Search size={17} /><span className="sr-only">Buscar participante</span><input value={query} placeholder="Código, nombre, documento o correo" onChange={(event) => table.set('query', event.target.value)} /></label>
        <label className="select-field"><span className="sr-only">Filtrar datos del participante</span><select value={profile} onChange={(event) => table.set('profile', event.target.value)}><option value="">Todos los participantes</option><option value="AUTOMATICOS">Pendientes de datos</option><option value="COMPLETOS">Datos registrados</option><option value="SIN_CORREO">Sin correo</option></select></label>
        <label className="select-field"><span className="sr-only">Filtrar documento</span><select value={document} onChange={(event) => table.set('document', event.target.value)}><option value="">Todos los documentos</option><option value="CON_DOCUMENTO">Con documento</option><option value="SIN_DOCUMENTO">Sin documento</option></select></label>
        {Boolean(context?.groups.length) && <label className="select-field"><span className="sr-only">Filtrar {label.toLowerCase()}</span><select value={groupId ?? ''} onChange={(event) => table.set('groupId', event.target.value === '' ? null : Number(event.target.value))}>
          <option value="">Todos los grupos</option><option value="0">Sin asignar</option>{context!.groups.map((group) => <option key={group.id} value={group.id}>{group.code} — {group.name}</option>)}</select></label>}
      </div></div>
      {error && <Notice tone="error">{error} <button type="button" onClick={reload}>Reintentar</button></Notice>}{loading && <p className="loading-inline" role="status">Consultando participantes…</p>}
      {data && <><div className="table-scroll" aria-busy={loading} inert={loading}><table className="data-table"><thead><tr><th>Código en la evaluación</th><th>Participante</th><th>Documento</th><th>Correo</th><th>{label}</th>{mutable && <th>Acción</th>}</tr></thead>
        <tbody>{data.items.map((item) => <tr key={item.id}><td><strong>{item.code}</strong></td><td>{item.names} {item.surnames}{item.automatic && <span className="table-subtitle">Pendiente de datos</span>}</td>
          <td>{item.document ? `${item.documentType === 'DOCUMENTO' ? '' : item.documentType + ' '}${item.document}` : '—'}</td><td>{item.email || '—'}</td><td>{item.groupName || 'Sin asignar'}</td>
          {mutable && <td><button type="button" className="text-link" onClick={() => { setSelected(item); setEditOpen(true) }}>Editar</button></td>}</tr>)}</tbody></table>
        {!data.items.length && <p className="empty-state">No hay inscripciones con estos criterios. {mutable ? 'Registra participantes o carga su archivo CSV.' : ''}</p>}</div>
        <Pagination result={data} onPage={(value) => table.set('page', value)} size={size} onSize={(value) => table.set('size', value)} busy={loading} /></>}
    </section>
    {context && editOpen && <EnrollmentModal org={org} evaluation={context.overview} groups={context.groups} enrollment={selected} onClose={() => setEditOpen(false)} onSaved={(value) => {
      setEditOpen(false); setMessage(`Participante ${value.code} guardado en esta evaluación.`); refreshed()
    }} />}
    {context && importOpen && <EnrollmentImportModal org={org} evaluation={context.overview} groups={context.groups} onClose={() => setImportOpen(false)} onComplete={(result) => {
      setImportOpen(false); reportState.set('id', result.reportId); setReportOpen(true); refreshed()
      setMessage(`${result.counts.newEnrollments} inscripciones nuevas · ${result.counts.completed} registros completados · ${result.counts.groupChanges} grupos actualizados. Carga aplicada correctamente.`)
    }} />}
    {reportState.values.id && reportOpen && <EnrollmentImportReportModal org={org} evaluation={evaluation} report={reportState.values.id} onClose={() => setReportOpen(false)} />}
  </div>
}
