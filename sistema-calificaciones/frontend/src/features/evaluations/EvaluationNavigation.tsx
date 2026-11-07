import { useCallback } from 'react'
import { Link, NavLink, useLocation } from 'react-router'
import { evaluationsApi } from '../../api/evaluations'
import { useAuth } from '../../auth/useAuth'
import { useResource } from '../../hooks/useResource'
import { StatusBadge } from '../../components/ui'
import { useEvaluationRevision } from '../../hooks/useEvaluationRevision'

const sections = [['', 'Resumen'], ['configuracion', 'Configuración'], ['participantes', 'Participantes'], ['importaciones', 'Archivos DBF'],
  ['examenes', 'Exámenes'], ['revisiones', 'Revisiones'], ['publicacion', 'Publicación'], ['informes', 'Informes']] as const

function Navigation({ evaluation }: { evaluation: number }) {
  const { organization } = useAuth()
  const location = useLocation()
  const revision = useEvaluationRevision(organization!.id, evaluation)
  const load = useCallback(() => evaluationsApi.overview(organization!.id, evaluation), [organization, evaluation])
  const { data } = useResource(load, `${location.pathname}:${revision}`)
  return <section className="evaluation-navigation" aria-label="Evaluación actual">
    <div>{data ? <><Link to={`/evaluaciones/${evaluation}${location.search}`} state={location.state}>{data.code} · {data.name}</Link><StatusBadge status={data.status} /></>
      : <span>Cargando evaluación…</span>}</div>
    <nav aria-label="Secciones de la evaluación">{sections.map(([path, label]) => <NavLink key={path}
      to={`/evaluaciones/${evaluation}${path ? `/${path}` : ''}${location.search}`} state={location.state} end={!path}
      className={({ isActive }) => isActive ? 'evaluation-tab evaluation-tab--active' : 'evaluation-tab'}>{label}</NavLink>)}</nav>
  </section>
}
export function EvaluationNavigation() {
  const location = useLocation()
  const match = location.pathname.match(/^\/evaluaciones\/(\d+)(?:\/|$)/)
  return match ? <Navigation key={match[1]} evaluation={Number(match[1])} /> : null
}
