import { useCallback } from 'react'
import { Link, useLocation } from 'react-router'
import { participantsApi } from '../participants/participantsApi'
import { useResource } from '../../hooks/useResource'
import { Notice } from '../../components/Notice'

export function RegistrationsSection({ org, evaluationId }: { org: number; evaluationId: number }) {
  const location = useLocation()
  const load = useCallback(() => participantsApi.summary(org, evaluationId), [org, evaluationId])
  const { data, error } = useResource(load)
  return <section className="surface-card form-section"><div className="form-section__heading"><div><p className="section-kicker">PARTICIPANTES</p><h2>Inscripciones y grupos</h2>
    <p>{data ? `${data.total} inscritos · ${data.incomplete} pendientes de datos · ${data.withoutGroup} sin grupo.` : 'Consulta los participantes de esta evaluación.'}</p></div></div>
    <p className="muted-copy">Registra personas, asigna sus códigos o carga participantes y grupos en bloque desde la gestión de esta evaluación.</p>
    <Link className="button button--secondary" state={location.state} to={`/evaluaciones/${evaluationId}/participantes${location.search}`}>Administrar participantes</Link>
    {error && <Notice tone="error">{error}</Notice>}</section>
}
