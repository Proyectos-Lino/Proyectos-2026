import { useNoticeMessage } from '../../hooks/useNoticeMessage'
import { Notice } from '../../components/Notice'
import { useCallback, useState } from 'react'
import { ApiError } from '../../api/client'
import { evaluationsApi } from '../../api/evaluations'
import { useResource } from '../../hooks/useResource'
import { scoreFormatter } from '../../utils/format'
import { RuleModal } from './RuleModal'

export function RuleSection({ org, evaluationId, canEdit }: { org: number; evaluationId: number; canEdit: boolean }) {
  const load = useCallback(async () => {
    try { return await evaluationsApi.rule(org, evaluationId) }
    catch (cause) { if (cause instanceof ApiError && cause.status === 404) return null; throw cause }
  }, [org, evaluationId])
  const { data: rule, loading, error, reload } = useResource(load)
  const [open, setOpen] = useState(false)
  const [message, setMessage] = useNoticeMessage()

  return <section className="surface-card form-section">
    <div className="form-section__heading"><span className="step-number">02</span>
      <div><h2>Puntajes y nota mínima</h2><p>La regla general se conserva por versión en cada calificación.</p></div></div>
    {loading && <p className="loading-inline">Cargando regla…</p>}
    {error && <Notice tone="error">{error}</Notice>}
    {rule ? <div className="info-row"><strong>Versión {rule.version}</strong>
      <span>Correcta {scoreFormatter.format(rule.correct)} · Incorrecta {scoreFormatter.format(rule.incorrect)} · Blanca {scoreFormatter.format(rule.blank)}
        {rule.passingScore !== null ? ` · Mínima ${scoreFormatter.format(rule.passingScore)}` : ' · Sin nota mínima'}</span></div>
      : !loading && <p className="muted-copy">Todavía no se ha definido una regla.</p>}
    {rule?.officialFormula && <p className="muted-copy">Nota oficial con <code>{rule.officialFormula}</code></p>}
    {canEdit && <button type="button" className="button button--secondary" onClick={() => setOpen(true)}>
      {rule ? 'Crear nueva versión' : 'Configurar puntajes'}</button>}
    {message && <Notice tone="success">{message}</Notice>}
    {open && <RuleModal org={org} evaluationId={evaluationId} rule={rule ?? null} onClose={() => setOpen(false)}
      onSaved={() => { setOpen(false); reload(); setMessage('Regla guardada. Los resultados anteriores conservan su versión.') }} />}
  </section>
}
