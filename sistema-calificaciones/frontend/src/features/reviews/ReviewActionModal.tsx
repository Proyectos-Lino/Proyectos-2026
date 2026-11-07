import { Notice } from '../../components/Notice'
import { useState, type FormEvent } from 'react'
import { Modal } from '../../components/Modal'
import { errorMessage } from '../../utils/format'

export type ReviewAction = { type: 'open' | 'annul' | 'reason' | 'restore' | 'approve' | 'reject'; reviewId?: number }

const labels: Record<ReviewAction['type'], { title: string; field?: string; button: string }> = {
  open: { title: 'Abrir revisión', field: 'Motivo de revisión', button: 'Abrir revisión' },
  annul: { title: 'Anular examen', field: 'Motivo de anulación', button: 'Anular examen' },
  reason: { title: 'Actualizar motivo', field: 'Nuevo motivo', button: 'Guardar motivo' },
  restore: { title: 'Revertir anulación', button: 'Revertir anulación' },
  approve: { title: 'Validar examen', field: 'Explicación de la decisión', button: 'Validar examen' },
  reject: { title: 'Anular examen', field: 'Explicación de la decisión', button: 'Anular examen' },
}

export function ReviewActionModal({ action, onClose, onConfirm }: {
  action: ReviewAction; onClose: () => void; onConfirm: (text: string) => Promise<void>
}) {
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const label = labels[action.type]
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(null)
    try { await onConfirm(text.trim()) } catch (cause) { setError(errorMessage(cause)) }
    finally { setBusy(false) }
  }
  return <Modal title={label.title} description="La decisión quedará registrada en el historial del examen."
    onClose={onClose} busy={busy}>
    <form className="modal-form" onSubmit={(event) => void submit(event)}>
      {label.field && <label className="field"><span>{label.field}</span>
        <textarea rows={4} value={text} onChange={(event) => setText(event.target.value)} required /></label>}
      {error && <Notice tone="error">{error}</Notice>}
      <div className="modal-actions"><button type="button" className="button button--secondary" onClick={onClose}>Cancelar</button>
        <button type="submit" className={['annul', 'reject'].includes(action.type) ? 'button button--danger' : 'button button--primary'}
          disabled={busy}>{label.button}</button></div>
    </form>
  </Modal>
}
