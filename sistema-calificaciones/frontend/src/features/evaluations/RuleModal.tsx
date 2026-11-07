import { Notice } from '../../components/Notice'
import { useState, type FormEvent } from 'react'
import { evaluationsApi } from '../../api/evaluations'
import type { Rule } from '../../api/types'
import { Modal } from '../../components/Modal'
import { errorMessage } from '../../utils/format'

export function RuleModal({ org, evaluationId, rule, onClose, onSaved }: {
  org: number; evaluationId: number; rule: Rule | null; onClose: () => void; onSaved: () => void
}) {
  const [form, setForm] = useState({ correct: rule?.correct ?? 1, incorrect: rule?.incorrect ?? 0,
    blank: rule?.blank ?? 0, passingScore: rule?.passingScore?.toString() ?? '' })
  const [working, setWorking] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function save(event: FormEvent) {
    event.preventDefault(); setWorking(true); setError(null)
    try {
      await evaluationsApi.saveRule(org, evaluationId, { ...form,
        passingScore: form.passingScore === '' ? null : Number(form.passingScore) })
      onSaved()
    } catch (cause) { setError(errorMessage(cause)) }
    finally { setWorking(false) }
  }

  return <Modal title="Regla de calificación" description="Los puntajes generales se aplican a los temas sin secciones. La nota mínima se utiliza según el criterio de resultado configurado."
    onClose={onClose} busy={working}>
    <form className="modal-form" onSubmit={(event) => void save(event)}>
      <div className="modal-form__grid">
        <label className="field"><span>Respuesta correcta</span><input type="number" min={0} step="0.0001" required value={form.correct}
          onChange={(event) => setForm({ ...form, correct: Number(event.target.value) })} /></label>
        <label className="field"><span>Respuesta incorrecta</span><input type="number" step="0.0001" required value={form.incorrect}
          onChange={(event) => setForm({ ...form, incorrect: Number(event.target.value) })} /></label>
        <label className="field"><span>En blanco</span><input type="number" step="0.0001" required value={form.blank}
          onChange={(event) => setForm({ ...form, blank: Number(event.target.value) })} /></label>
        <label className="field"><span>Nota mínima</span><input type="number" step="0.0001" value={form.passingScore}
          placeholder="Opcional" onChange={(event) => setForm({ ...form, passingScore: event.target.value })} /></label>
      </div>
      {error && <Notice tone="error">{error}</Notice>}
      <div className="modal-form__actions"><button type="button" className="button button--secondary" onClick={onClose}>Cancelar</button>
        <button className="button button--primary" disabled={working}>{working ? 'Guardando…' : 'Guardar nueva versión'}</button></div>
    </form>
  </Modal>
}
