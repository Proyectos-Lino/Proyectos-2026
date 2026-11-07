import { useState } from 'react'
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router'
import { ClipboardList } from 'lucide-react'
import { useAuth } from '../../auth/useAuth'
import { PageHeader } from '../../components/ui'
import { EvaluationPickerModal } from './EvaluationPickerModal'
import { EvaluationParticipants } from './EvaluationParticipants'

export function ParticipantsPage() {
  const { organization } = useAuth()
  const route = useParams()
  const [params, setParams] = useSearchParams()
  const location = useLocation()
  const navigate = useNavigate()
  const [pickerOpen, setPickerOpen] = useState(false)
  const candidate = Number(route.evaluationId ?? params.get('participants.evaluationId'))
  const evaluation = Number.isSafeInteger(candidate) && candidate > 0 ? candidate : null
  function select(id: number) {
    const next = new URLSearchParams(params)
    for (const key of [...next.keys()]) {
      if (key.startsWith('participants.') || key.startsWith('enrollmentImport.') || key.startsWith('enrollmentPreview.') || key.startsWith('enrollmentReport.')) next.delete(key)
    }
    setPickerOpen(false)
    if (route.evaluationId) navigate(`/evaluaciones/${id}/participantes?${next}`, { state: location.state })
    else { next.set('participants.evaluationId', String(id)); setParams(next, { replace: true }) }
  }
  return <>{evaluation ? <EvaluationParticipants key={`${organization!.id}-${evaluation}`} org={organization!.id} evaluation={evaluation} onChangeEvaluation={() => setPickerOpen(true)} /> :
    <div className="page-stack"><PageHeader eyebrow="ESPACIO DE TRABAJO" title="Participantes" description="Administra las inscripciones, los códigos y los grupos de cada evaluación." />
      <section className="surface-card participant-empty"><ClipboardList size={32} aria-hidden="true" /><h2>Elige una evaluación para comenzar</h2>
        <p>Verás sus participantes y podrás registrarlos, completar los datos del DBF o asignar grupos mediante CSV.</p>
        <button type="button" className="button button--primary" onClick={() => setPickerOpen(true)}>Elegir evaluación</button></section>
    </div>}
    {pickerOpen && <EvaluationPickerModal org={organization!.id} onClose={() => setPickerOpen(false)} onSelect={(value) => select(value.id)} />}</>
}
