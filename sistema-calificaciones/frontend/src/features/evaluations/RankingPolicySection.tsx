import { useNoticeMessage } from '../../hooks/useNoticeMessage'
import { Notice } from '../../components/Notice'
import { useCallback, useState, type FormEvent } from 'react'
import { evaluationsApi } from '../../api/evaluations'
import type { RankingPolicy } from '../../api/types'
import { Modal } from '../../components/Modal'
import { useResource } from '../../hooks/useResource'
import { errorMessage } from '../../utils/format'
import { approvalLabels, scopeLabels, tieLabels } from '../../utils/scoring'
import { resultLanguage } from '../../utils/evaluation'

const initial: Omit<RankingPolicy, 'id' | 'evaluationId' | 'version'> = {
  scope: 'GRUPO', approvalMode: 'VACANTES_O_MINIMA', tieBreak: 'COMPARTIR_PUESTO', globalCapacity: null,
}

export function RankingPolicySection({ org, evaluationId, canEdit, evaluationType }: {
  org: number; evaluationId: number; canEdit: boolean; evaluationType?: string
}) {
  const language = resultLanguage(evaluationType)
  const load = useCallback(() => evaluationsApi.policy(org, evaluationId), [org, evaluationId])
  const { data: policy, loading, error, reload } = useResource(load)
  const [form, setForm] = useState(initial)
  const [open, setOpen] = useState(false)
  const [working, setWorking] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [message, setMessage] = useNoticeMessage()

  function edit() {
    setForm(policy ? { scope: policy.scope, approvalMode: policy.approvalMode,
      tieBreak: policy.tieBreak, globalCapacity: policy.globalCapacity } : initial)
    setSaveError(null); setOpen(true)
  }

  async function save(event: FormEvent) {
    event.preventDefault(); setWorking(true); setSaveError(null)
    try {
      await evaluationsApi.savePolicy(org, evaluationId, form)
      setOpen(false); reload(); setMessage('Criterios guardados. Se actualizaron los puestos y las decisiones de resultado; el historial anterior se conserva.')
    } catch (cause) { setSaveError(errorMessage(cause)) }
    finally { setWorking(false) }
  }

  const needsCapacity = form.scope === 'GLOBAL' && ['VACANTES', 'AMBAS', 'VACANTES_O_MINIMA'].includes(form.approvalMode)
  return <section className="surface-card form-section">
    <div className="form-section__heading"><span className="step-number">03</span>
      <div><h2>Ranking y {language.heading.toLowerCase()}</h2><p>Define el ordenamiento y el criterio de resultado de esta evaluación.</p></div></div>
    {loading && <p className="loading-inline">Cargando política…</p>}
    {error && <Notice tone="error">{error}</Notice>}
    {policy && <div className="policy-summary">
      <span className="policy-summary__version">Versión {policy.version}</span>
      <strong>{approvalLabels[policy.approvalMode]}</strong>
      <span>Ranking: {scopeLabels[policy.scope]}</span>
      <span>Desempate: {tieLabels[policy.tieBreak]}</span>
      {policy.scope === 'GLOBAL' && policy.globalCapacity !== null && <span>Vacantes globales: {policy.globalCapacity}</span>}
    </div>}
    {canEdit && <button type="button" className="button button--secondary" onClick={edit}>Configurar política</button>}
    {message && <Notice tone="success">{message}</Notice>}
    {open && <Modal title="Política de resultados" description="La nueva versión afecta el ranking actual. Las decisiones previas quedan en el historial de cada examen."
      onClose={() => setOpen(false)} busy={working}>
      <form className="modal-form" onSubmit={(event) => void save(event)}>
        <div className="modal-form__grid">
          <label className="field"><span>Alcance del ranking</span><select value={form.scope}
            onChange={(event) => setForm({ ...form, scope: event.target.value as RankingPolicy['scope'], globalCapacity: null })}>
            {Object.entries(scopeLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select></label>
          <label className="field"><span>{language.policy}</span><select value={form.approvalMode}
            onChange={(event) => setForm({ ...form, approvalMode: event.target.value as RankingPolicy['approvalMode'] })}>
            {Object.entries(approvalLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select></label>
          <label className="field"><span>Desempate</span><select value={form.tieBreak}
            onChange={(event) => setForm({ ...form, tieBreak: event.target.value as RankingPolicy['tieBreak'] })}>
            {Object.entries(tieLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select></label>
          {needsCapacity && <label className="field"><span>Vacantes globales</span><input type="number" min={0} required
            value={form.globalCapacity ?? ''} onChange={(event) => setForm({ ...form,
              globalCapacity: event.target.value === '' ? null : Number(event.target.value) })} /></label>}
        </div>
        <p className="muted-copy">La nota mínima se define en la regla de calificación. Si el ranking es por grupo, las vacantes se toman del cupo de cada grupo.</p>
        {saveError && <Notice tone="error">{saveError}</Notice>}
        <div className="modal-form__actions"><button type="button" className="button button--secondary" onClick={() => setOpen(false)}>Cancelar</button>
          <button className="button button--primary" disabled={working}>{working ? 'Guardando…' : 'Guardar nueva versión'}</button></div>
      </form>
    </Modal>}
  </section>
}
