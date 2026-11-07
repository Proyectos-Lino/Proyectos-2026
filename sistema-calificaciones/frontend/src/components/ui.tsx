import type { ReactNode } from 'react'
import { ArrowUpRight } from 'lucide-react'
import { Link } from 'react-router'
import { labelStatus } from '../utils/format'

interface PageHeaderProps {
  eyebrow: string
  title: string
  description?: string
  action?: ReactNode
}

export function PageHeader({ eyebrow, title, description, action }: PageHeaderProps) {
  return (
    <header className="page-heading">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1 className="page-title">{title}</h1>
        {description && <p className="page-description">{description}</p>}
      </div>
      {action && <div className="page-heading-action">{action}</div>}
    </header>
  )
}

export function StatusBadge({ status }: { status: string }) {
  const tone =
    ['PUBLICADA', 'VALIDADA', 'APROBADA', 'COMPLETADO', 'ACTIVO'].includes(status)
      ? 'success'
      : ['OBSERVADA', 'ABIERTA', 'EN_REVISION', 'COMPLETADO_CON_ERRORES'].includes(status)
        ? 'warning'
        : ['ANULADA', 'RECHAZADA', 'FALLIDO'].includes(status)
          ? 'danger'
          : ['BORRADOR', 'INACTIVO'].includes(status)
            ? 'muted'
            : 'info'

  return <span className={`status-badge status-badge--${tone}`}><span className="status-dot" />{labelStatus(status)}</span>
}

export function ActionLink({ to, children, secondary = false }: { to: string; children: ReactNode; secondary?: boolean }) {
  return (
    <Link to={to} className={secondary ? 'button button--secondary' : 'button button--primary'}>
      {children}
    </Link>
  )
}

export function TextLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link to={to} className="text-link">
      {children}
      <ArrowUpRight size={15} aria-hidden="true" />
    </Link>
  )
}
