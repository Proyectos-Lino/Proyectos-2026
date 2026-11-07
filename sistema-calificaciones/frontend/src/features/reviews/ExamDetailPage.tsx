import { BackLink } from '../../components/BackLink'
import { useNoticeMessage } from '../../hooks/useNoticeMessage'
import { Notice } from '../../components/Notice'
import { useCallback, useState } from 'react'
import { ArrowLeft } from 'lucide-react'
import { Link, useParams, useLocation } from 'react-router'
import { examsApi } from '../../api/exams'
import { evaluationsApi } from '../../api/evaluations'
import { publicationApi } from '../../api/publication'
import { reviewsApi } from '../../api/reviews'
import { useAuth } from '../../auth/useAuth'
import { PageHeader, StatusBadge } from '../../components/ui'
import { useResource } from '../../hooks/useResource'
import { labelStatus, scoreFormatter } from '../../utils/format'
import { EvidenceGallery } from './EvidenceGallery'
import { ResultExplanationSection } from '../exams/ResultExplanationSection'
import { ReviewActionModal, type ReviewAction } from './ReviewActionModal'
import { Modal } from '../../components/Modal'
import { resultLanguage } from '../../utils/evaluation'

export function ExamDetailPage() {
  const { evaluationId, sheetId } = useParams()
  const id = Number(evaluationId)
  const sheet = Number(sheetId)
  const location = useLocation()
  const { organization } = useAuth()
  const org = organization!.id
  const hasReviewRole = ['ADMINISTRADOR', 'REVISOR'].includes(organization!.role)
  const loadExam = useCallback(() => examsApi.one(org, id, sheet), [org, id, sheet])
  const loadReviews = useCallback(() => reviewsApi.list(org, id, sheet), [org, id, sheet])
  const { data: exam, loading, error, reload: reloadExam } = useResource(loadExam)
  const loadEvaluation = useCallback(() => evaluationsApi.overview(org, id), [org, id])
  const loadPublication = useCallback(() => publicationApi.policy(org, id), [org, id])
  const { data: evaluation } = useResource(loadEvaluation)
  const { data: publication } = useResource(loadPublication)
  const { data: reviews, reload: reloadReviews } = useResource(loadReviews)
  const [action, setAction] = useState<ReviewAction | null>(null)
  const [message, setMessage] = useNoticeMessage()
  const [revision, setRevision] = useState(0)
  const [calculationOpen, setCalculationOpen] = useState(false)
  const assignment = new URLSearchParams(location.search).get('cases.assignment') ?? 'ALL'
  const loadNeighbors = useCallback(() => reviewsApi.neighbors(org, id, sheet, assignment), [org, id, sheet, assignment])
  const { data: neighbors } = useResource(loadNeighbors, revision)

  function refresh() { reloadExam(); reloadReviews(); setRevision((current) => current + 1) }

  async function confirm(text: string) {
    if (!action) return
    switch (action.type) {
      case 'open': await reviewsApi.open(org, id, sheet, text); break
      case 'annul': await reviewsApi.annul(org, id, sheet, text); break
      case 'reason': await reviewsApi.updateReason(org, id, sheet, text); break
      case 'restore': await reviewsApi.restore(org, id, sheet); break
      case 'approve': await reviewsApi.resolve(org, id, action.reviewId!, 'APROBADA', text); break
      case 'reject': await reviewsApi.resolve(org, id, action.reviewId!, 'ANULADA', text); break
    }
    setAction(null); refresh(); setMessage('Decisión registrada y resultados actualizados.')
  }

  if (loading && (!exam || exam.id !== sheet)) return <p className="loading-inline">Cargando examen…</p>
  if (error && !exam) return <Notice tone="error">{error}</Notice>
  if (!exam) return null
  const canReview = hasReviewRole && evaluation !== null && publication !== null && !publication.approvedAt
    && !['PUBLICADA', 'ARCHIVADA'].includes(evaluation.status)
  const openReview = reviews?.find((review) => ['ABIERTA', 'EN_REVISION'].includes(review.status))
  const language = resultLanguage(evaluation?.type)
  const fromCases = location.state?.fromCases === true

  return <div className="page-stack">
    <BackLink to={`/evaluaciones/${id}/${fromCases ? 'revisiones' : 'examenes'}${location.search}`} className="back-link"><ArrowLeft size={17} /> {fromCases ? 'Volver a la bandeja de casos' : 'Volver a exámenes'}</BackLink>
    <PageHeader eyebrow={exam.participantCode} title={exam.participantName}
      description={`Hoja ${exam.sheetCode ?? 'sin código'} · Tema ${exam.formCode} · ${exam.groupName ?? 'Sin asignar'}`}
      action={<StatusBadge status={exam.status} />} />
    <div className="review-navigation"><span>Recorrer otros casos pendientes</span>
      {neighbors?.previous && <Link className="button button--secondary" state={{ ...location.state, fromCases }} to={`/evaluaciones/${id}/examenes/${neighbors.previous}${location.search}`}>Anterior pendiente</Link>}
      {neighbors?.next && <Link className="button button--secondary" state={{ ...location.state, fromCases }} to={`/evaluaciones/${id}/examenes/${neighbors.next}${location.search}`}>Siguiente pendiente</Link>}
      {!neighbors?.previous && !neighbors?.next && <small>No hay otros casos pendientes con esta asignación.</small>}
    </div>
    <div className="detail-summary">
      <div><span>Correctas</span><strong>{exam.correct ?? '—'}</strong></div>
      <div><span>Incorrectas</span><strong>{exam.incorrect ?? '—'}</strong></div>
      <div><span>Blancas</span><strong>{exam.blank ?? '—'}</strong></div>
      <div><span>Puntaje / puesto</span><strong>{exam.score === null ? '—' : scoreFormatter.format(exam.score)} / {exam.position ?? '—'}</strong></div>
    </div>
    <section className="surface-card form-section">
      <p className="section-kicker">DECISIÓN ACTUAL</p>
      <h2>{labelStatus(exam.status)} {exam.passed === true ? `· ${language.positive}` : exam.passed === false ? `· ${language.negative}` : ''}</h2>
      {exam.observation && <p className="muted-copy">Motivo: {exam.observation}</p>}
      {openReview && <div className="pending-case-summary"><strong>Caso pendiente · {openReview.reason}</strong>
        <p>Responsable: {openReview.assigneeName ?? 'Sin asignar'}</p>
        {canReview && <div className="list-toolbar__controls"><button type="button" className="button button--primary" onClick={() => setAction({ type: 'approve', reviewId: openReview.id })}>Validar examen</button>
          <button type="button" className="button button--danger" onClick={() => setAction({ type: 'reject', reviewId: openReview.id })}>Anular examen</button></div>}</div>}
      {canReview && !openReview && exam.status !== 'ANULADA' && <button type="button"
        className={exam.status === 'OBSERVADA' ? 'button button--secondary' : 'button button--danger'}
        onClick={() => setAction({ type: exam.status === 'OBSERVADA' ? 'open' : 'annul' })}>
        {exam.status === 'OBSERVADA' ? 'Abrir revisión' : 'Anular examen'}</button>}
      {canReview && exam.status === 'ANULADA' && !openReview && <>
        <div className="publication-actions"><button type="button" className="button button--secondary"
          onClick={() => setAction({ type: 'reason' })}>Actualizar motivo</button>
          <button type="button" className="button button--primary"
            onClick={() => setAction({ type: 'restore' })}>Revertir anulación</button></div>
      </>}
      {message && <Notice tone="success">{message}</Notice>}
    </section>
    <button type="button" className="button button--secondary" onClick={() => setCalculationOpen(true)}>Ver cálculo e historial del resultado</button>
    {calculationOpen && <Modal wide title="Cálculo e historial del resultado" description={`Participante ${exam.participantCode} · Tema ${exam.formCode}`} onClose={() => setCalculationOpen(false)}>
      <div className="modal-form"><ResultExplanationSection key={revision} org={org} evaluationId={id} sheetId={sheet} evaluationType={evaluation?.type} /></div></Modal>}
    <section className="surface-card form-section">
      <p className="section-kicker">TRAZABILIDAD</p><h2>Revisiones y evidencias</h2>
      {reviews?.length === 0 && <p className="muted-copy">Este examen todavía no tiene revisiones.</p>}
      <div className="review-list">{reviews?.map((review) => <article className="review-card" key={review.id}>
        <div className="review-card__heading"><strong>Revisión {review.id}</strong><StatusBadge status={review.status} /></div>
        <p><strong>Motivo:</strong> {review.reason}</p>
        <p><strong>Responsable:</strong> {review.assigneeName ?? 'Sin asignar'}</p>
        <p className="muted-copy">Registrada por {review.openedByName ?? 'Sistema'} · {new Date(review.openedAt).toLocaleString('es-PE')}</p>
        {review.resolvedAt && <p className="muted-copy">Resuelta por {review.resolvedByName ?? 'Usuario no disponible'} · {new Date(review.resolvedAt).toLocaleString('es-PE')}</p>}
        {review.resolution && <p><strong>Resolución:</strong> {review.resolution}</p>}
        <EvidenceGallery org={org} evaluationId={id} reviewId={review.id} canEdit={canReview} />
      </article>)}</div>
    </section>
    {action && <ReviewActionModal action={action} onClose={() => setAction(null)} onConfirm={confirm} />}
  </div>
}
