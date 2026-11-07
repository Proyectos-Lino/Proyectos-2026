import { BackLink } from '../components/BackLink'
import { Notice } from '../components/Notice'
import { useState, type FormEvent } from 'react'
import { ArrowLeft, Info } from 'lucide-react'
import { Navigate, useNavigate } from 'react-router'
import { evaluationsApi } from '../api/evaluations'
import { useAuth } from '../auth/useAuth'
import { PageHeader } from '../components/ui'
import { errorMessage } from '../utils/format'

export function NewEvaluationPage() {
  const { organization } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ code: '', name: '', description: '', type: 'ADMISION', date: '', groupType: 'GRUPO' as const })
  const [working, setWorking] = useState(false)
  const [error, setError] = useState<string | null>(null)
  if (!organization || !['ADMINISTRADOR', 'OPERADOR'].includes(organization.role)) return <Navigate to="/evaluaciones" replace />

  async function submit(event: FormEvent) {
    event.preventDefault()
    setWorking(true)
    setError(null)
    try {
      const evaluation = await evaluationsApi.create(organization!.id, { ...form, date: form.date || null })
      navigate(`/evaluaciones/${evaluation.id}/importaciones`, { replace: true })
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setWorking(false)
    }
  }

  return (
    <div className="page-stack">
      <BackLink to="/evaluaciones" className="back-link"><ArrowLeft size={17} /> Volver a evaluaciones</BackLink>
      <PageHeader eyebrow="NUEVA EVALUACIÓN" title="Preparar una evaluación"
        description="Crea el proceso y luego configura sus temas, participantes y archivos OMR." />
      <form onSubmit={(event) => void submit(event)} className="form-grid">
        <div className="form-main">
          <section className="surface-card form-section">
            <div className="form-section__heading"><span className="step-number">01</span>
              <div><h2>Información general</h2><p>Estos datos identificarán el proceso en el historial y en los reportes.</p></div></div>
            <div className="form-fields">
              <label className="field"><span>Nombre de la evaluación</span>
                <input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })}
                  placeholder="Evaluación de competencias 2026-II" required maxLength={180} /></label>
              <label className="field"><span>Código único</span>
                <input value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value })}
                  placeholder="EVAL-2026-II" required maxLength={60} /></label>
              <label className="field"><span>Tipo</span>
                <select value={form.type} onChange={(event) => setForm({ ...form, type: event.target.value })}>
                  <option value="ADMISION">Admisión</option><option value="GENERAL">General</option>
                  <option value="CERTIFICACION">Certificación</option><option value="SELECCION">Selección</option>
                </select></label>
              <label className="field"><span>Fecha de la evaluación</span>
                <input type="date" value={form.date} onChange={(event) => setForm({ ...form, date: event.target.value })} /></label>
              <label className="field"><span>Cómo se agruparán los participantes</span>
                <select value={form.groupType} onChange={(event) => setForm({ ...form, groupType: event.target.value as typeof form.groupType })}>
                  <option value="GRUPO">Grupo</option><option value="CARRERA">Carrera</option>
                  <option value="SEDE">Sede</option><option value="CATEGORIA">Categoría</option>
                  <option value="PROGRAMA">Programa</option>
                </select></label>
            </div>
            <label className="field field--spaced"><span>Descripción (opcional)</span>
              <textarea value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })}
                rows={3} placeholder="Información útil para tu equipo" /></label>
          </section>
        </div>
        <aside className="form-side">
          <section className="surface-card form-section">
            <div className="form-section__heading"><span className="step-number">02</span>
              <div><h2>Siguiente paso</h2><p>Después de crearla podrás cargar los tres DBF o configurar manualmente temas y regla.</p></div></div>
            <div className="form-info"><Info size={19} aria-hidden="true" />
              <p>La carga guiada puede registrar participantes por código. Los nombres y grupos podrán completarse después.</p></div>
          </section>
          {error && <Notice tone="error">{error}</Notice>}
          <button className="button button--primary form-submit" type="submit" disabled={working}>
            {working ? 'Creando…' : 'Crear evaluación'}
          </button>
        </aside>
      </form>
    </div>
  )
}
