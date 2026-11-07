import { useNoticeMessage } from '../../hooks/useNoticeMessage'
import { Notice } from '../../components/Notice'
import { useCallback, useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import { evaluationsApi } from '../../api/evaluations'
import type { Form } from '../../api/types'
import { Modal } from '../../components/Modal'
import { useResource } from '../../hooks/useResource'
import { errorMessage } from '../../utils/format'

export function AnswerKeyModal({ org, evaluationId, form, canEdit, onClose }: {
  org: number; evaluationId: number; form: Form; canEdit: boolean; onClose: () => void
}) {
  const load = useCallback(async () => {
    try { return await evaluationsApi.key(org, evaluationId, form.id) }
    catch (cause) { if (cause instanceof ApiError && cause.status === 404) return null; throw cause }
  }, [org, evaluationId, form.id])
  const { data: key, error: loadError, reload } = useResource(load)
  const [editedAnswers, setEditedAnswers] = useState<string | null>(null)
  const [working, setWorking] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useNoticeMessage()
  const answers = editedAnswers ?? (key ? (JSON.parse(key.answers) as string[]).join(',') : '')
  const count = answers.split(',').length

  async function save(event: FormEvent) {
    event.preventDefault()
    const values = answers.split(',').map((value) => value.trim().toUpperCase())
    if (values.length !== form.questions) { setError(`Se necesitan ${form.questions} respuestas; hay ${values.length}.`); return }
    setWorking(true); setError(null)
    try { await evaluationsApi.saveKey(org, evaluationId, form.id, values); setEditedAnswers(null); reload(); setMessage('Nueva versión de la clave guardada.') }
    catch (cause) { setError(errorMessage(cause)) }
    finally { setWorking(false) }
  }

  return <Modal title={`Clave · ${form.code}`} description={`${form.questions} preguntas · ${form.options} opciones por pregunta`}
    onClose={onClose} busy={working}>
    <form className="modal-form" onSubmit={(event) => void save(event)}>
      <p className="muted-copy">{key ? `Versión actual ${key.version}. ` : 'Todavía no hay una clave. '}
        Escribe una respuesta por pregunta, separada por comas. Una posición vacía se considera correcta para todos.</p>
      <label className="field"><span>Respuestas · {count} de {form.questions}</span>
        <textarea rows={8} value={answers} readOnly={!canEdit} placeholder="A,B,C,D,..."
          onChange={(event) => setEditedAnswers(event.target.value)} /></label>
      {loadError && <Notice tone="error">{loadError}</Notice>}
      {error && <Notice tone="error">{error}</Notice>}
      {message && <Notice tone="success">{message}</Notice>}
      <div className="modal-form__actions"><button type="button" className="button button--secondary" onClick={onClose}>Cerrar</button>
        {canEdit && <button className="button button--primary" disabled={working}>{working ? 'Guardando…' : 'Guardar nueva versión'}</button>}</div>
    </form>
  </Modal>
}
