import { useNoticeMessage } from '../../hooks/useNoticeMessage'
import { Notice } from '../../components/Notice'
import { useCallback, useState } from 'react'
import { Plus } from 'lucide-react'
import { evaluationsApi } from '../../api/evaluations'
import type { Form } from '../../api/types'
import { useResource } from '../../hooks/useResource'
import { AnswerKeyModal } from './AnswerKeyModal'
import { FormEditorModal } from './FormEditorModal'
import { SectionsModal } from './SectionsModal'

export function FormsSection({ org, evaluationId, canEdit }: { org: number; evaluationId: number; canEdit: boolean }) {
  const load = useCallback(() => evaluationsApi.forms(org, evaluationId), [org, evaluationId])
  const { data: forms, loading, error, reload } = useResource(load)
  const [selected, setSelected] = useState<Form | null>(null)
  const [editor, setEditor] = useState<'create' | 'key' | 'sections' | null>(null)
  const [message, setMessage] = useNoticeMessage()

  function open(form: Form, kind: 'key' | 'sections') { setSelected(form); setEditor(kind) }

  return <section className="surface-card form-section">
    <div className="form-section__heading"><span className="step-number">01</span>
      <div><h2>Temas, claves y secciones</h2><p>Cada tema define sus preguntas; la clave y las secciones tienen versiones.</p></div></div>
    {canEdit && <button type="button" className="button button--secondary" onClick={() => setEditor('create')}>
      <Plus size={16} /> Nuevo tema</button>}
    {loading && !forms && <p className="loading-inline">Cargando temas…</p>}
    {error && <Notice tone="error">{error}</Notice>}
    <div className="simple-list">
      {forms?.map((form) => <div key={form.id} className="group-card">
        <div><strong>{form.code}</strong><span>{form.questions} preguntas · {form.options} opciones</span></div>
        <div className="list-toolbar__controls"><button type="button" className="button button--secondary"
          onClick={() => open(form, 'key')}>Clave</button>
          <button type="button" className="button button--secondary" onClick={() => open(form, 'sections')}>Secciones</button></div>
      </div>)}
      {forms?.length === 0 && <p className="muted-copy">Aún no hay temas.</p>}
    </div>
    {message && <Notice tone="success">{message}</Notice>}
    {editor === 'create' && <FormEditorModal org={org} evaluationId={evaluationId} onClose={() => setEditor(null)}
      onSaved={() => { setEditor(null); setMessage('Tema creado.'); reload() }} />}
    {editor === 'key' && selected && <AnswerKeyModal key={selected.id} org={org} evaluationId={evaluationId}
      form={selected} canEdit={canEdit} onClose={() => setEditor(null)} />}
    {editor === 'sections' && selected && <SectionsModal key={selected.id} org={org} evaluationId={evaluationId}
      form={selected} canEdit={canEdit} onClose={() => setEditor(null)}
      onSaved={() => { setEditor(null); setMessage('Configuración de secciones guardada.') }} />}
  </section>
}
