import { CheckCircle2, Circle } from 'lucide-react'
import { Link, useLocation } from 'react-router'
import type { PreparationCheck } from '../../api/types'
import { workflowLink } from './workflow'

export function PreparationChecklist({ evaluation, checks, actions = {} }: { evaluation: number; checks: PreparationCheck[]; actions?: Partial<Record<string, () => void>> }) {
  const location = useLocation()
  return <ul className="preparation-checklist">{checks.map((check) => <li key={check.code}>
    {check.completed ? <CheckCircle2 size={18} className="check-done" /> : <Circle size={18} />}
    <span>{check.label}{!check.completed && check.pending > 1 && <small> · {check.pending} pendientes</small>}</span>
    <span className="sr-only">{check.completed ? 'Completo' : 'Pendiente'}</span>
    {!check.completed && (actions[check.code] ? <button type="button" className="text-link" onClick={actions[check.code]}>Resolver</button>
      : <Link className="text-link" state={location.state} to={workflowLink(evaluation, check.code, location.search)}>Revisar</Link>)}
  </li>)}</ul>
}
