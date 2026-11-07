import { BackLink } from '../components/BackLink'
import { Notice } from '../components/Notice'
import { useCallback, useState } from 'react'
import { ArrowLeft, CircleAlert, ClipboardCheck, Download, FileSpreadsheet, Settings2, Users, ListChecks, Send, ChartNoAxesCombined } from 'lucide-react'
import { Link, useParams, useLocation } from 'react-router'
import type { Evaluation } from '../api/types'
import { evaluationsApi } from '../api/evaluations'
import { useAuth } from '../auth/useAuth'
import { PageHeader, StatusBadge } from '../components/ui'
import { useResource } from '../hooks/useResource'
import { downloadBlob, errorMessage, formatDate, numberFormatter, scoreFormatter, labelStatus } from '../utils/format'
import { evaluationTypeLabel, resultLanguage, reviewProgress } from '../utils/evaluation'
import { PreparationChecklist } from '../features/evaluations/PreparationChecklist'
import { EvaluationDetailsModal } from '../features/evaluations/EvaluationDetailsModal'
import { Modal } from '../components/Modal'

const nextStatus: Partial<Record<Evaluation['status'], Evaluation['status'][]>> = {
  BORRADOR: ['CONFIGURADA'],
  CONFIGURADA: ['EN_PROCESO', 'BORRADOR'],
  EN_PROCESO: ['CERRADA'],
  CERRADA: ['EN_PROCESO'],
  PUBLICADA: ['ARCHIVADA'],
}

export function EvaluationDetailPage() {
  const { evaluationId } = useParams()
  const location = useLocation()
  const id = Number(evaluationId)
  const { organization } = useAuth()
  const org = organization!.id
  const canOperate = ['ADMINISTRADOR', 'OPERADOR'].includes(organization!.role)
  const load = useCallback(() => evaluationsApi.overview(org, id), [org, id])
  const { data: evaluation, loading, error, reload } = useResource(load)
  const [working, setWorking] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [detailsOpen, setDetailsOpen] = useState(false)
  const [requestedStatus, setRequestedStatus] = useState<Evaluation['status'] | null>(null)
  const loadPreparation = useCallback(() => evaluationsApi.preparation(org, id), [org, id])
  const { data: preparation, reload: reloadPreparation } = useResource(loadPreparation)

  async function changeStatus(status: Evaluation['status']) {
    setWorking(true)
    setActionError(null)
    try { await evaluationsApi.status(org, id, status); setRequestedStatus(null); reload(); reloadPreparation() }
    catch (cause) { setActionError(errorMessage(cause)) }
    finally { setWorking(false) }
  }

  async function downloadPdf() {
    setWorking(true)
    setActionError(null)
    try { downloadBlob(await evaluationsApi.pdf(org, id), `resultados-${id}.pdf`) }
    catch (cause) { setActionError(errorMessage(cause)) }
    finally { setWorking(false) }
  }

  if (loading && !evaluation) return <p className="loading-inline">Cargando evaluación…</p>
  if (error && !evaluation) return <Notice tone="error">{error} <button type="button" onClick={reload}>Reintentar</button></Notice>
  if (!evaluation) return null

  const reviewPercent = reviewProgress(evaluation.exams, evaluation.observed)
  const language = resultLanguage(evaluation.type)
  return (
    <div className="page-stack">
      <BackLink to={`${location.state?.evaluationListPath === '/' ? '/' : '/evaluaciones'}${location.search}`} className="back-link"><ArrowLeft size={17} /> {location.state?.evaluationListPath === '/' ? 'Volver al panel principal' : 'Volver a evaluaciones'}</BackLink>
      <PageHeader eyebrow={evaluation.code} title={evaluation.name}
        description={`${evaluationTypeLabel(evaluation.type)} · ${formatDate(evaluation.date)}`}
        action={<div className="list-toolbar__controls"><StatusBadge status={evaluation.status} />
          {canOperate && preparation?.editable && ['BORRADOR', 'CONFIGURADA'].includes(evaluation.status)
            && <button type="button" className="button button--secondary" onClick={() => setDetailsOpen(true)}>Editar datos generales</button>}</div>} />
      {preparation && <section className="surface-card form-section"><p className="section-kicker">SIGUIENTE PASO</p>
        <h2>{evaluation.status === 'ARCHIVADA' ? 'Evaluación archivada' : evaluation.status === 'PUBLICADA' ? 'Resultados publicados' : preparation.setup.every((check) => check.completed) ? 'Preparación completa' : 'Preparación de la evaluación'}</h2>
        {!preparation.setup.every((check) => check.completed) ? <><p className="muted-copy">Puedes configurar los datos o utilizar la carga guiada DBF para preparar los elementos que falten.</p>
          <PreparationChecklist evaluation={id} checks={preparation.setup} /></> : <p className="muted-copy">{evaluation.status === 'ARCHIVADA' ? 'La consulta individual está cerrada. El equipo puede seguir consultando el historial.' : evaluation.status === 'PUBLICADA' ? 'Consulta los informes o administra la información visible para cada participante desde Publicación.' : 'Revisa las incidencias y comprueba los requisitos antes de aprobar los resultados.'}</p>}
        {preparation.setup.every((check) => check.completed) && <Link className="text-link" state={location.state} to={`/evaluaciones/${id}/publicacion${location.search}`}>Abrir publicación y consulta</Link>}
      </section>}
      <div className="detail-summary">
        <div><span>Participantes</span><strong>{numberFormatter.format(evaluation.participants)}</strong><Users size={21} aria-hidden="true" /></div>
        <div><span>Exámenes</span><strong>{numberFormatter.format(evaluation.exams)}</strong><ClipboardCheck size={21} aria-hidden="true" /></div>
        <div><span>Observados</span><strong>{numberFormatter.format(evaluation.observed)}</strong><CircleAlert size={21} aria-hidden="true" /></div>
        <div><span>{language.people}</span><strong>{numberFormatter.format(evaluation.admitted)}</strong><ClipboardCheck size={21} aria-hidden="true" /></div>
      </div>
      <div className="workflow-grid">
        <Link to={`/evaluaciones/${id}/participantes${location.search}`} state={location.state} className="surface-card workflow-card">
          <Users size={23} /><strong>Participantes</strong><span>Inscripciones, códigos y asignación masiva de grupos</span>
        </Link>
        <Link to={`/evaluaciones/${id}/configuracion${location.search}`} state={location.state} className="surface-card workflow-card">
          <Settings2 size={23} /><strong>Configuración</strong><span>Temas, regla, grupos y vacantes</span>
        </Link>
        <Link to={`/evaluaciones/${id}/importaciones${location.search}`} state={location.state} className="surface-card workflow-card">
          <FileSpreadsheet size={23} /><strong>Archivos DBF</strong><span>Carga guiada e historial de lotes</span>
        </Link>
        <Link to={`/evaluaciones/${id}/examenes${location.search}`} state={location.state} className="surface-card workflow-card">
          <ClipboardCheck size={23} /><strong>Exámenes</strong><span>Puntajes, puestos e incidencias</span>
        </Link>
        <Link to={`/evaluaciones/${id}/revisiones${location.search}`} state={location.state} className="surface-card workflow-card">
          <ListChecks size={23} /><strong>Casos pendientes</strong><span>Responsables, decisiones y evidencias</span>
        </Link>
        <Link to={`/evaluaciones/${id}/publicacion${location.search}`} state={location.state} className="surface-card workflow-card">
          <Send size={23} /><strong>Publicación</strong><span>Aprobación y consulta individual</span>
        </Link>
        <Link to={`/evaluaciones/${id}/informes${location.search}`} state={location.state} className="surface-card workflow-card">
          <ChartNoAxesCombined size={23} /><strong>Informes y análisis</strong><span>Puntajes, grupos y preguntas</span>
        </Link>
      </div>
      <div className="detail-grid">
        <section className="surface-card detail-progress">
          <p className="section-kicker">RESULTADOS</p>
          <h2>Seguimiento de exámenes</h2>
          <p>{evaluation.exams === 0 ? 'Todavía no se han importado hojas.' : evaluation.observed > 0 ? 'Los exámenes observados requieren una decisión.' : 'No hay exámenes observados pendientes.'}</p>
          <div className="progress-value"><strong>{reviewPercent}%</strong><span>sin observaciones</span></div>
          <div className="progress-track" role="progressbar" aria-valuenow={reviewPercent} aria-valuemin={0}
            aria-valuemax={100} aria-label="Exámenes sin observaciones"><span style={{ width: `${reviewPercent}%` }} /></div>
          <div className="progress-legend">
            <span>Puntaje promedio: {evaluation.averageScore === null ? '—' : scoreFormatter.format(evaluation.averageScore)}</span>
            <Link to={`/evaluaciones/${id}/revisiones?cases.status=PENDIENTES`}>Exámenes observados: {numberFormatter.format(evaluation.observed)}</Link>
          </div>
        </section>
        <section className="surface-card detail-next">
          <p className="section-kicker">ACCIONES</p>
          <h2>Gestionar el proceso</h2>
          <p>Descarga los resultados o avanza el estado cuando hayas terminado cada etapa.</p>
          <div className="action-stack">
            <button type="button" className="button button--secondary" onClick={() => void downloadPdf()} disabled={working}>
              <Download size={16} /> Descargar PDF</button>
            {canOperate && nextStatus[evaluation.status]?.map((status) => (
              <button key={status} type="button" className="button button--primary"
                onClick={() => setRequestedStatus(status)} disabled={working || (preparation?.approved && status === 'EN_PROCESO')}>
                Cambiar a {labelStatus(status).toLowerCase()}
              </button>
            ))}
          </div>
          {actionError && <Notice tone="error">{actionError}</Notice>}
        </section>
      </div>
      {detailsOpen && <EvaluationDetailsModal org={org} evaluation={evaluation} onClose={() => setDetailsOpen(false)}
        onSaved={() => { setDetailsOpen(false); reload(); reloadPreparation() }} />}
      {requestedStatus && <Modal title={`Cambiar evaluación a ${labelStatus(requestedStatus).toLowerCase()}`}
        description={requestedStatus === 'ARCHIVADA' ? 'Se cerrará también la consulta pública de resultados.' : 'Comprueba el estado y los pendientes antes de continuar.'}
        onClose={() => setRequestedStatus(null)} busy={working}><div className="modal-form">
          <p>{evaluation.name} · {evaluation.exams} exámenes · {evaluation.observed} observados.</p>
          <div className="modal-form__actions"><button type="button" className="button button--secondary" disabled={working} onClick={() => setRequestedStatus(null)}>Cancelar</button>
            <button type="button" className="button button--primary" disabled={working} onClick={() => void changeStatus(requestedStatus)}>Confirmar cambio</button></div>
          {actionError && <Notice tone="error">{actionError}</Notice>}
        </div></Modal>}
    </div>
  )
}
