import { Notice } from '../../components/Notice'
import { useCallback, useState, type FormEvent } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { ApiError } from '../../api/client'
import { evaluationsApi } from '../../api/evaluations'
import type { Form, SectionInput } from '../../api/types'
import { Modal } from '../../components/Modal'
import { useResource } from '../../hooks/useResource'
import { errorMessage } from '../../utils/format'

export function SectionsModal({ org, evaluationId, form, canEdit, onClose, onSaved }: {
  org: number; evaluationId: number; form: Form; canEdit: boolean; onClose: () => void; onSaved: () => void
}) {
  const load = useCallback(async () => {
    try { return await evaluationsApi.sections(org, evaluationId, form.id) }
    catch (cause) { if (cause instanceof ApiError && cause.status === 404) return null; throw cause }
  }, [org, evaluationId, form.id])
  const loadRule = useCallback(async () => {
    try { return await evaluationsApi.rule(org, evaluationId) }
    catch (cause) { if (cause instanceof ApiError && cause.status === 404) return null; throw cause }
  }, [org, evaluationId])
  const { data: configuration, loading, error: loadError } = useResource(load)
  const { data: rule, loading: ruleLoading, error: ruleError } = useResource(loadRule)
  const defaultRows: SectionInput[] = [{
    code: 'S1', name: 'Sección 1', fromQuestion: 1, toQuestion: form.questions,
    correct: rule?.correct ?? 1, incorrect: rule?.incorrect ?? 0, blank: rule?.blank ?? 0,
  }]
  const [editedRows, setEditedRows] = useState<SectionInput[] | null>(null)
  const rows = editedRows ?? (configuration?.sections.length ? configuration.sections.map((section) => ({
    code: section.code, name: section.name, fromQuestion: section.fromQuestion,
    toQuestion: section.toQuestion, correct: section.correct, incorrect: section.incorrect, blank: section.blank,
  })) : defaultRows)
  const [working, setWorking] = useState(false)
  const [error, setError] = useState<string | null>(null)
  function change(index: number, update: Partial<SectionInput>) {
    setEditedRows(rows.map((row, position) => position === index ? { ...row, ...update } : row))
  }

  function addSection() {
    const last = rows.at(-1)!
    if (last.fromQuestion >= last.toQuestion) return
    const boundary = Math.floor((last.fromQuestion + last.toQuestion) / 2)
    setEditedRows([...rows.slice(0, -1), { ...last, toQuestion: boundary }, {
      code: `S${rows.length + 1}`, name: `Sección ${rows.length + 1}`,
      fromQuestion: boundary + 1, toQuestion: last.toQuestion,
      correct: last.correct, incorrect: last.incorrect, blank: last.blank,
    }])
  }

  function removeSection(index: number) {
    if (rows.length < 2) return
    const next = rows.filter((_, position) => position !== index)
    if (index === 0) next[0] = { ...next[0], fromQuestion: rows[0].fromQuestion }
    else next[index - 1] = { ...next[index - 1], toQuestion: rows[index].toQuestion }
    setEditedRows(next)
  }

  async function save(event: FormEvent) {
    event.preventDefault(); setWorking(true); setError(null)
    try { await evaluationsApi.saveSections(org, evaluationId, form.id, rows); onSaved() }
    catch (cause) { setError(errorMessage(cause)) }
    finally { setWorking(false) }
  }

  async function disableSections() {
    setWorking(true); setError(null)
    try { await evaluationsApi.saveSections(org, evaluationId, form.id, []); onSaved() }
    catch (cause) { setError(errorMessage(cause)) }
    finally { setWorking(false) }
  }

  return <Modal title={`Secciones · ${form.code}`}
    description="Asigna rangos y puntajes distintos dentro del tema. Las secciones deben cubrir todas las preguntas, en orden y sin cruces."
    onClose={onClose} busy={working}>
    <form className="modal-form" onSubmit={(event) => void save(event)}>
      <p className="muted-copy">{configuration ? `Versión actual ${configuration.version}. ` : 'Aún no hay secciones. '}
        Una nueva versión se aplicará solo a las próximas calificaciones; los resultados anteriores conservarán su desglose.</p>
      {(loading || ruleLoading) && <p className="loading-inline">Cargando configuración…</p>}
      <div className="section-editor">
        {rows.map((row, index) => <div className="section-editor__row" key={index}>
          <div className="section-editor__heading"><strong>Sección {index + 1}</strong>
            {canEdit && rows.length > 1 && <button type="button" className="icon-button" aria-label={`Quitar sección ${index + 1}`}
              onClick={() => removeSection(index)}><Trash2 size={17} /></button>}</div>
          <div className="modal-form__grid">
            <label className="field"><span>Código</span><input value={row.code} maxLength={40} required readOnly={!canEdit}
              onChange={(event) => change(index, { code: event.target.value })} /></label>
            <label className="field"><span>Nombre</span><input value={row.name} maxLength={120} required readOnly={!canEdit}
              onChange={(event) => change(index, { name: event.target.value })} /></label>
            <label className="field"><span>Desde pregunta</span><input type="number" min={1} max={form.questions} value={row.fromQuestion} required readOnly={!canEdit}
              onChange={(event) => change(index, { fromQuestion: Number(event.target.value) })} /></label>
            <label className="field"><span>Hasta pregunta</span><input type="number" min={1} max={form.questions} value={row.toQuestion} required readOnly={!canEdit}
              onChange={(event) => change(index, { toQuestion: Number(event.target.value) })} /></label>
            <label className="field"><span>Correcta</span><input type="number" min={0} step="0.0001" value={row.correct} required readOnly={!canEdit}
              onChange={(event) => change(index, { correct: Number(event.target.value) })} /></label>
            <label className="field"><span>Incorrecta</span><input type="number" step="0.0001" value={row.incorrect} required readOnly={!canEdit}
              onChange={(event) => change(index, { incorrect: Number(event.target.value) })} /></label>
            <label className="field"><span>En blanco</span><input type="number" step="0.0001" value={row.blank} required readOnly={!canEdit}
              onChange={(event) => change(index, { blank: Number(event.target.value) })} /></label>
          </div>
        </div>)}
      </div>
      {canEdit && <button type="button" className="button button--secondary" disabled={rows.at(-1)!.fromQuestion >= rows.at(-1)!.toQuestion}
        onClick={addSection}><Plus size={16} /> Dividir última sección</button>}
      {loadError && <Notice tone="error">{loadError}</Notice>}
      {ruleError && <Notice tone="error">{ruleError}</Notice>}
      {error && <Notice tone="error">{error}</Notice>}
      <div className="modal-form__actions"><button type="button" className="button button--secondary" onClick={onClose}>Cerrar</button>
        {canEdit && <><button type="button" className="button button--secondary" disabled={working} onClick={() => void disableSections()}>Usar puntaje general</button>
          <button className="button button--primary" disabled={working || loading || ruleLoading || Boolean(loadError || ruleError)}>{working ? 'Guardando…' : 'Guardar secciones'}</button></>}
      </div>
    </form>
  </Modal>
}
