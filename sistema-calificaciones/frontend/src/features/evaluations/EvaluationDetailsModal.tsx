import { useState, type FormEvent } from 'react'
import type { Evaluation } from '../../api/types'
import { evaluationsApi } from '../../api/evaluations'
import { Modal } from '../../components/Modal'
import { Notice } from '../../components/Notice'
import { errorMessage } from '../../utils/format'

export function EvaluationDetailsModal({ org, evaluation, onClose, onSaved }: {
  org: number; evaluation: Evaluation; onClose: () => void; onSaved: () => void
}) {
  const [name, setName] = useState(evaluation.name)
  const [description, setDescription] = useState(evaluation.description ?? '')
  const [date, setDate] = useState(evaluation.date ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  async function save(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(null)
    try { await evaluationsApi.update(org, evaluation.id, { name, description, date: date || null, groupType: evaluation.groupType }); onSaved() }
    catch (cause) { setError(errorMessage(cause)) }
    finally { setBusy(false) }
  }
  return <Modal title="Datos generales de la evaluación" description="Actualiza la identificación del proceso mientras está en preparación."
    busy={busy} onClose={onClose}><form className="modal-form" onSubmit={(event) => void save(event)}>
      <label className="field"><span>Nombre</span><input required maxLength={180} value={name} onChange={(event) => setName(event.target.value)} /></label>
      <label className="field"><span>Descripción</span><textarea rows={3} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
      <label className="field"><span>Fecha de la evaluación</span><input type="date" value={date} onChange={(event) => setDate(event.target.value)} /></label>
      {error && <Notice tone="error">{error}</Notice>}
      <div className="modal-form__actions"><button type="button" className="button button--secondary" onClick={onClose} disabled={busy}>Cancelar</button>
        <button className="button button--primary" disabled={busy}>{busy ? 'Guardando…' : 'Guardar cambios'}</button></div>
    </form></Modal>
}
