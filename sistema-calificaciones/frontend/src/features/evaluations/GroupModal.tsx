import { Notice } from '../../components/Notice'
import { useState, type FormEvent } from 'react'
import { evaluationsApi } from '../../api/evaluations'
import type { Group } from '../../api/types'
import { Modal } from '../../components/Modal'
import { errorMessage } from '../../utils/format'

export function GroupModal({ org, evaluationId, group, label, onClose, onSaved }: {
  org: number; evaluationId: number; group: Group | null; label: string
  onClose: () => void; onSaved: () => void
}) {
  const [form, setForm] = useState({ code: group?.code ?? '', name: group?.name ?? '', capacity: group?.capacity?.toString() ?? '' })
  const [working, setWorking] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function save(event: FormEvent) {
    event.preventDefault(); setWorking(true); setError(null)
    const payload = { code: form.code, name: form.name, capacity: form.capacity === '' ? null : Number(form.capacity) }
    try {
      if (group) await evaluationsApi.updateGroup(org, evaluationId, group.id, payload)
      else await evaluationsApi.createGroup(org, evaluationId, payload)
      onSaved()
    } catch (cause) { setError(errorMessage(cause)) }
    finally { setWorking(false) }
  }

  return <Modal title={`${group ? 'Editar' : 'Nueva'} ${label.toLowerCase()}`}
    description="Define el nombre que se mostrará en inscripciones y resultados. Las vacantes son opcionales."
    onClose={onClose} busy={working}>
    <form className="modal-form" onSubmit={(event) => void save(event)}>
      <div className="modal-form__grid">
        <label className="field"><span>Código</span><input required maxLength={60} value={form.code}
          onChange={(event) => setForm({ ...form, code: event.target.value })} /></label>
        <label className="field"><span>Nombre</span><input required maxLength={180} value={form.name}
          onChange={(event) => setForm({ ...form, name: event.target.value })} /></label>
        <label className="field"><span>Vacantes</span><input type="number" min={0} value={form.capacity} placeholder="Sin definir"
          onChange={(event) => setForm({ ...form, capacity: event.target.value })} /></label>
      </div>
      {error && <Notice tone="error">{error}</Notice>}
      <div className="modal-form__actions"><button type="button" className="button button--secondary" onClick={onClose}>Cancelar</button>
        <button className="button button--primary" disabled={working}>{working ? 'Guardando…' : 'Guardar'}</button></div>
    </form>
  </Modal>
}
