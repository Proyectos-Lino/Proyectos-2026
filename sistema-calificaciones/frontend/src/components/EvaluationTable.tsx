import { ArrowUpRight } from 'lucide-react'
import { Link, useLocation } from 'react-router'
import type { EvaluationOverview } from '../api/types'
import { formatDate, numberFormatter } from '../utils/format'
import { StatusBadge } from './ui'
import { evaluationTypeLabel } from '../utils/evaluation'

export function EvaluationTable({ items, busy = false }: { items: EvaluationOverview[]; busy?: boolean }) {
  const location = useLocation()
  if (items.length === 0) {
    return <div className="empty-state">No se encontraron evaluaciones con esos criterios.</div>
  }

  return (
    <div className="table-scroll" aria-busy={busy} inert={busy}>
      <table className="data-table">
        <thead>
          <tr>
            <th scope="col">Evaluación</th>
            <th scope="col">Tipo</th>
            <th scope="col">Participantes</th>
            <th scope="col">Fecha</th>
            <th scope="col">Estado</th>
            <th scope="col"><span className="sr-only">Abrir</span></th>
          </tr>
        </thead>
        <tbody>
          {items.map((evaluation) => (
            <tr key={evaluation.id}>
              <td>
                <Link to={`/evaluaciones/${evaluation.id}${location.search}`} state={{ evaluationListPath: location.pathname }} className="table-title">{evaluation.name}</Link>
                <span className="table-subtitle">{evaluation.code}</span>
              </td>
              <td>{evaluationTypeLabel(evaluation.type)}</td>
              <td>{numberFormatter.format(evaluation.participants)}</td>
              <td>{formatDate(evaluation.date)}</td>
              <td><StatusBadge status={evaluation.status} /></td>
              <td className="table-action">
                <Link to={`/evaluaciones/${evaluation.id}${location.search}`} state={{ evaluationListPath: location.pathname }} aria-label={`Ver ${evaluation.name}`}><ArrowUpRight size={18} /></Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
