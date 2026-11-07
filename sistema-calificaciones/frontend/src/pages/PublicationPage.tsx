import { BackLink } from '../components/BackLink'
import { useNoticeMessage } from '../hooks/useNoticeMessage'
import { Notice } from '../components/Notice'
import { useCallback, useState, type FormEvent } from 'react'
import { ArrowLeft, Download, Settings2 } from 'lucide-react'
import { useParams } from 'react-router'
import type { PublicationPolicy } from '../api/types'
import { publicationApi } from '../api/publication'
import { evaluationsApi } from '../api/evaluations'
import { useAuth } from '../auth/useAuth'
import { Modal } from '../components/Modal'
import { Pagination } from '../components/Pagination'
import { PageHeader, StatusBadge } from '../components/ui'
import { useResource } from '../hooks/useResource'
import { downloadBlob, errorMessage } from '../utils/format'
import { PreparationChecklist } from '../features/evaluations/PreparationChecklist'
import { resultLanguage } from '../utils/evaluation'
import { EvaluationActivity } from '../features/evaluations/EvaluationActivity'
import { useTableState } from '../hooks/useTableState'

const dateLabel = (value: string | null) => value
  ? new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : 'Sin límite'
const inputDate = (value: string | null) => value ? new Date(value).toLocaleString('sv-SE').slice(0, 16) : ''
const outputDate = (value: string) => value ? new Date(value).toISOString() : null

function PolicyModal({ initial, onClose, onSave, evaluationType }: {
  initial: PublicationPolicy; onClose: () => void; onSave: (value: PublicationPolicy) => Promise<void>; evaluationType?: string
}) {
  const [policy, setPolicy] = useState(initial)
  const [from, setFrom] = useState(inputDate(initial.visibleFrom))
  const [until, setUntil] = useState(inputDate(initial.visibleUntil))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(null)
    try { await onSave({ ...policy, visibleFrom: outputDate(from), visibleUntil: outputDate(until) }) }
    catch (cause) { setError(errorMessage(cause)) }
    finally { setBusy(false) }
  }
  return <Modal title="Configurar consulta" description="Elige los datos que verá cada participante y el periodo de consulta."
    onClose={onClose} busy={busy}>
    <form className="modal-form" onSubmit={(event) => void submit(event)}>
      <div className="publication-options">
        {([['showScore', 'Puntaje'], ['showPosition', 'Puesto'], ['showPassed', resultLanguage(evaluationType).heading],
          ['showCounts', 'Respuestas correctas, incorrectas y blancas']] as const).map(([key, label]) =>
          <label key={key}><input type="checkbox" checked={policy[key]}
            onChange={(event) => setPolicy({ ...policy, [key]: event.target.checked })} /> {label}</label>)}
      </div>
      <div className="field-grid"><label className="field"><span>Visible desde (opcional)</span>
        <input type="datetime-local" value={from} onChange={(event) => setFrom(event.target.value)} /></label>
        <label className="field"><span>Visible hasta (opcional)</span>
          <input type="datetime-local" value={until} onChange={(event) => setUntil(event.target.value)} /></label></div>
      {error && <Notice tone="error">{error}</Notice>}
      <div className="modal-actions"><button type="button" className="button button--secondary" onClick={onClose}>Cancelar</button>
        <button type="submit" className="button button--primary" disabled={busy}>Guardar configuración</button></div>
    </form>
  </Modal>
}

function ReasonModal({ onClose, onSave }: { onClose: () => void; onSave: (reason: string) => Promise<void> }) {
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(null)
    try { await onSave(reason) } catch (cause) { setError(errorMessage(cause)) } finally { setBusy(false) }
  }
  return <Modal title="Recalcular resultados" description="Se guardará una nueva versión de cada puntaje con las claves y reglas actuales."
    onClose={onClose} busy={busy}>
    <form className="modal-form" onSubmit={(event) => void submit(event)}>
      <label className="field"><span>Motivo del recálculo</span>
        <textarea value={reason} onChange={(event) => setReason(event.target.value)} rows={3} required /></label>
      {error && <Notice tone="error">{error}</Notice>}
      <div className="modal-actions"><button type="button" className="button button--secondary" onClick={onClose}>Cancelar</button>
        <button type="submit" className="button button--primary" disabled={busy}>Recalcular</button></div>
    </form>
  </Modal>
}

export function PublicationPage() {
  const { evaluationId } = useParams()
  const evaluation = Number(evaluationId)
  const { organization } = useAuth()
  const org = organization!.id
  const isAdmin = organization!.role === 'ADMINISTRADOR'
  const canRecalculate = ['ADMINISTRADOR', 'OPERADOR'].includes(organization!.role)
  const auditTable = useTableState({ page: 0, size: 20 }, 'audit')
  const { page: auditPage, size: auditSize } = auditTable.values
  const [policyOpen, setPolicyOpen] = useState(false)
  const [reasonOpen, setReasonOpen] = useState(false)
  const [accessOpen, setAccessOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useNoticeMessage()
  const [actionError, setActionError] = useState<string | null>(null)
  const [confirmation, setConfirmation] = useState<'approve' | 'publish' | 'close' | null>(null)
  const loadPreparation = useCallback(() => evaluationsApi.preparation(org, evaluation), [org, evaluation])
  const { data: preparation, error: preparationError, reload: reloadPreparation } = useResource(loadPreparation)
  const loadEvaluation = useCallback(() => evaluationsApi.overview(org, evaluation), [org, evaluation])
  const loadPolicy = useCallback(() => publicationApi.policy(org, evaluation), [org, evaluation])
  const loadAudit = useCallback(() => publicationApi.audit(org, evaluation, auditPage, auditSize), [org, evaluation, auditPage, auditSize])
  const { data: overview, error: overviewError, reload: reloadOverview } = useResource(loadEvaluation)
  const { data: policy, error: policyError, reload: reloadPolicy } = useResource(loadPolicy)
  const { data: audit, error: auditError, loading: auditLoading, reload: reloadAudit } = useResource(loadAudit)

  async function run(action: () => Promise<unknown>, success: string) {
    setBusy(true); setActionError(null); setMessage(null)
    try { await action(); setConfirmation(null); setMessage(success); reloadPolicy(); reloadOverview(); reloadAudit(); reloadPreparation() }
    catch (cause) { setActionError(errorMessage(cause)) }
    finally { setBusy(false) }
  }

  async function savePolicy(value: PublicationPolicy) {
    await publicationApi.save(org, evaluation, { showScore: value.showScore, showPosition: value.showPosition,
      showPassed: value.showPassed, showCounts: value.showCounts,
      visibleFrom: value.visibleFrom, visibleUntil: value.visibleUntil })
    reloadPreparation(); setPolicyOpen(false); setMessage(overview?.status === 'PUBLICADA'
      ? 'Configuración de consulta actualizada.'
      : 'Configuración de consulta guardada.')
    reloadPolicy(); reloadAudit()
  }

  async function recalculate(reason: string) {
    const result = await publicationApi.recalculate(org, evaluation, reason)
    reloadPreparation(); setReasonOpen(false); setMessage(`${result.recalculated} resultados recalculados con una nueva versión.`)
    reloadAudit(); reloadOverview()
  }

  async function accessCodes() {
    setBusy(true); setActionError(null)
    try {
      downloadBlob(await publicationApi.accesses(org, evaluation), `accesos-evaluacion-${evaluation}.csv`)
      setAccessOpen(false); setMessage('Se descargaron nuevos accesos. Los anteriores quedaron invalidados.'); reloadAudit()
    } catch (cause) { setActionError(errorMessage(cause)) }
    finally { setBusy(false) }
  }

  return <div className="page-stack">
    <BackLink to={`/evaluaciones/${evaluation}`} className="back-link"><ArrowLeft size={17} /> Volver a evaluación</BackLink>
    <PageHeader eyebrow="RESULTADOS" title="Aprobación y publicación"
      description="Controla qué se muestra a los participantes y cuándo pueden consultarlo."
      action={overview && <StatusBadge status={overview.status} />} />
    {(overviewError || policyError) && <Notice tone="error">{overviewError || policyError}</Notice>}
    {message && <Notice tone="success">{message}</Notice>}
    {actionError && <Notice tone="error">{actionError}</Notice>}
    {policy && overview && <><section className="surface-card form-section">
      <div className="form-section__heading"><div><p className="section-kicker">CONSULTA INDIVIDUAL</p>
        <h2>Información visible</h2><p>El código de acceso identifica un único resultado.</p></div>
        {isAdmin && (overview.status === 'PUBLICADA' || (overview.status === 'CERRADA' && !policy.approvedAt)) && <button type="button" className="button button--secondary"
          onClick={() => setPolicyOpen(true)}><Settings2 size={16} /> Configurar</button>}</div>
      <div className="publication-options publication-options--summary">
        <span>Puntaje: <strong>{policy.showScore ? 'Visible' : 'Oculto'}</strong></span>
        <span>Puesto: <strong>{policy.showPosition ? 'Visible' : 'Oculto'}</strong></span>
        <span>{resultLanguage(overview.type).heading}: <strong>{policy.showPassed ? 'Visible' : 'Oculto'}</strong></span>
        <span>Conteos: <strong>{policy.showCounts ? 'Visibles' : 'Ocultos'}</strong></span>
      </div>
      {!policy.configured && <p className="muted-copy">Guarda la configuración antes de aprobar los resultados.</p>}
      <p className="muted-copy">Disponible desde: {dateLabel(policy.visibleFrom)} · Hasta: {dateLabel(policy.visibleUntil)}</p>
    </section>
      <section className="surface-card form-section"><p className="section-kicker">FLUJO DE PUBLICACIÓN</p>
        <h2>Revisar, aprobar y publicar</h2>
      {preparationError && <Notice tone="error">{preparationError}</Notice>}
      {preparation && <PreparationChecklist evaluation={evaluation} checks={preparation.publication} actions={{
        ...(canRecalculate && overview.status === 'EN_PROCESO' ? { CIERRE: () => setConfirmation('close') } : {}),
        ...(isAdmin && overview.status === 'CERRADA' ? { CONSULTA: () => setPolicyOpen(true) } : {}),
        ...(canRecalculate && preparation.editable && ['EN_PROCESO', 'CERRADA'].includes(overview.status) ? { VERSIONES: () => setReasonOpen(true) } : {}),
      }} />}
        <p className="muted-copy">Antes de aprobar, resuelve todos los casos pendientes y los exámenes observados.
          Una vez aprobados los resultados, no podrán modificarse sus claves, reglas ni puntajes.</p>
        <div className="publication-steps">
          <div><strong>1. Cierre</strong><span>{overview.status === 'CERRADA' ? 'Evaluación cerrada' : overview.status === 'PUBLICADA' ? 'Completado' : 'Pendiente'}</span></div>
          <div><strong>2. Aprobación</strong><span>{policy.approvedAt ? dateLabel(policy.approvedAt)
            : overview.status === 'PUBLICADA' ? 'Sin registro anterior' : 'Pendiente'}</span></div>
          <div><strong>3. Publicación</strong><span>{policy.publishedAt ? dateLabel(policy.publishedAt) : 'Pendiente'}</span></div>
        </div>
        {isAdmin && overview.status === 'CERRADA' && <div className="publication-actions">
          {!policy.approvedAt && <button type="button" className="button button--secondary" disabled={busy || !preparation?.readyToApprove}
            onClick={() => setConfirmation('approve')}>Aprobar resultados</button>}
          {policy.approvedAt && <button type="button" className="button button--primary" disabled={busy || !preparation?.readyToPublish}
            onClick={() => setConfirmation('publish')}>Publicar resultados</button>}
        </div>}
        {canRecalculate && ['EN_PROCESO', 'CERRADA'].includes(overview.status) && !policy.approvedAt &&
          <button type="button" className="button button--secondary" onClick={() => setReasonOpen(true)}>Recalcular con las reglas actuales</button>}
        {isAdmin && overview.status === 'PUBLICADA' && <div className="publication-access">
          <p>Descarga un archivo CSV con el nombre, correo y código privado de cada participante con examen válido.
            Generar uno nuevo invalida todos los códigos anteriores.</p>
          <button type="button" className="button button--secondary" onClick={() => setAccessOpen(true)}
            disabled={busy}><Download size={16} /> Generar y descargar accesos</button>
        </div>}
      </section></>}
    <section className="surface-card form-section"><p className="section-kicker">TRAZABILIDAD</p>
      <h2>Actividad de la evaluación</h2>
      {auditError && <Notice tone="error">{auditError}</Notice>}
      {audit && <EvaluationActivity evaluation={evaluation} events={audit.items} formatDate={dateLabel} />}
      {auditLoading && <p role="status">Actualizando actividad…</p>}
      {audit && <Pagination result={audit} onPage={(value) => auditTable.set('page', value)} size={auditSize} onSize={(value) => auditTable.set('size', value)} busy={auditLoading} />}
    </section>
    {policyOpen && policy && <PolicyModal initial={policy} evaluationType={overview?.type} onClose={() => setPolicyOpen(false)} onSave={savePolicy} />}
    {reasonOpen && <ReasonModal onClose={() => setReasonOpen(false)} onSave={recalculate} />}
    {confirmation && overview && policy && <Modal title={confirmation === 'close' ? 'Cerrar evaluación' : confirmation === 'approve' ? 'Aprobar resultados' : 'Publicar resultados'}
      description={confirmation === 'approve' ? 'La aprobación bloqueará los cambios en claves, reglas y resultados.' : confirmation === 'publish' ? 'Los participantes con un código válido podrán consultar los datos habilitados durante el periodo indicado.' : 'Después del cierre podrás configurar y aprobar la publicación.'}
      onClose={() => setConfirmation(null)} busy={busy}><div className="modal-form">
        <p><strong>{overview.name}</strong> · {overview.exams} exámenes · {overview.observed} observados.</p>
        <p>Puntaje {policy.showScore ? 'visible' : 'oculto'} · Puesto {policy.showPosition ? 'visible' : 'oculto'} · {resultLanguage(overview.type).heading} {policy.showPassed ? 'visible' : 'oculta'}.</p>
        <p>Consulta: {dateLabel(policy.visibleFrom)} — {dateLabel(policy.visibleUntil)}.</p>
        {actionError && <Notice tone="error">{actionError}</Notice>}
        <div className="modal-form__actions"><button type="button" className="button button--secondary" disabled={busy} onClick={() => setConfirmation(null)}>Cancelar</button>
          <button type="button" className="button button--primary" disabled={busy} onClick={() => void run(
            () => confirmation === 'close' ? evaluationsApi.status(org, evaluation, 'CERRADA') : confirmation === 'approve' ? publicationApi.approve(org, evaluation) : publicationApi.publish(org, evaluation),
            confirmation === 'close' ? 'Evaluación cerrada.' : confirmation === 'approve' ? 'Resultados aprobados.' : 'Resultados publicados.')}>{busy ? 'Procesando…' : 'Confirmar'}</button></div>
      </div></Modal>}
    {accessOpen && <Modal title="Generar accesos individuales" onClose={() => setAccessOpen(false)} busy={busy}
      description="Esta operación reemplazará todos los códigos de acceso ya entregados.">
      <div className="modal-form"><p>Entrega a cada participante únicamente su propio código del archivo descargado.</p>
        <div className="modal-actions"><button type="button" className="button button--secondary"
          onClick={() => setAccessOpen(false)}>Cancelar</button>
          <button type="button" className="button button--primary" onClick={() => void accessCodes()}
            disabled={busy}>Generar y descargar CSV</button></div></div>
    </Modal>}
  </div>
}
