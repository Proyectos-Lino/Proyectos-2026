import { Notice } from '../../components/Notice'
import { useState, type FormEvent } from 'react'
import { evaluationsApi } from '../../api/evaluations'
import { Modal } from '../../components/Modal'
import { errorMessage } from '../../utils/format'

export function FormEditorModal({ org, evaluationId, onClose, onSaved }: {
  org: number; evaluationId: number; onClose: () => void; onSaved: () => void
}) {
  const [code, setCode] = useState('')
  const [questions, setQuestions] = useState(100)
  const [options, setOptions] = useState(5)
  const [working, setWorking] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function save(event: FormEvent) {
    event.preventDefault(); setWorking(true); setError(null)
    try { await evaluationsApi.createForm(org, evaluationId, { code, questions, options }); onSaved() }
    catch (cause) { setError(errorMessage(cause)) }
    finally { setWorking(false) }
  }

  return <Modal title="Nuevo tema" description="Cada tema tiene su propia cantidad de preguntas y clave de respuestas."
    onClose={onClose} busy={working}>
    <form className="modal-form" onSubmit={(event) => void save(event)}>
      <div className="modal-form__grid">
        <label className="field"><span>Código del tema</span><input value={code} required maxLength={60} autoFocus
          onChange={(event) => setCode(event.target.value)} /></label>
        <label className="field"><span>Preguntas</span><input type="number" min={1} required value={questions}
          onChange={(event) => setQuestions(Number(event.target.value))} /></label>
        <label className="field"><span>Opciones por pregunta</span><input type="number" min={2} max={26} required value={options}
          onChange={(event) => setOptions(Number(event.target.value))} /></label>
      </div>
      {error && <Notice tone="error">{error}</Notice>}
      <div className="modal-form__actions"><button type="button" className="button button--secondary" onClick={onClose}>Cancelar</button>
        <button className="button button--primary" disabled={working}>{working ? 'Guardando…' : 'Crear tema'}</button></div>
    </form>
  </Modal>
}
