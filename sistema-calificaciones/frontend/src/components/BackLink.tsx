import { Link, useLocation, type LinkProps } from 'react-router'

/** Conserva el contexto de los listados al volver por la jerarquía de una evaluación. */
export function BackLink(props: LinkProps) {
  const { search, state } = useLocation()
  const target = typeof props.to === 'string' && !props.to.includes('?') ? `${props.to}${search}` : props.to
  return <Link state={state} {...props} to={target} className={props.className ?? 'back-link'} />
}
