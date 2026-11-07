import { Link } from 'react-router'
import type { AuditEvent } from '../../api/types'
import { auditDescription, auditParticipantCode } from '../../utils/audit'

export function EvaluationActivity({ evaluation, events, formatDate }: {
  evaluation: number; events: AuditEvent[]; formatDate: (value: string) => string
}) {
  return <div className="result-history">{events.map((event) => {
    const description = auditDescription(event.action)
    const code = auditParticipantCode(event)
    const target = event.sheetId ? `/evaluaciones/${evaluation}/examenes/${event.sheetId}`
      : code ? `/evaluaciones/${evaluation}/participantes?participants.query=${encodeURIComponent(code)}`
        : description.section ? `/evaluaciones/${evaluation}/${description.section}` : null
    return <article key={event.id}>
      <div><strong>{description.title}</strong><span>{formatDate(event.occurredAt)}</span></div>
      <p>{event.userName ?? 'Sistema'} · {description.description}</p>
      {code && <p>Código en la evaluación: <strong>{code}</strong></p>}
      {target && <Link className="text-link" to={target}>{event.sheetId ? 'Abrir examen relacionado' : code ? 'Abrir participante relacionado' : 'Abrir sección relacionada'}</Link>}
    </article>
  })}{events.length === 0 && <p className="muted-copy">Aún no hay eventos registrados.</p>}</div>
}
