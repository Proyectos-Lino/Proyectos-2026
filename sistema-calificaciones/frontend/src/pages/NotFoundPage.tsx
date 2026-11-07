import { ArrowLeft } from 'lucide-react'
import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <div className="not-found">
      <span>404</span>
      <h1>No encontramos esta página</h1>
      <p>Comprueba la dirección o vuelve al panel para continuar.</p>
      <Link to="/" className="button button--primary"><ArrowLeft size={17} /> Volver al panel</Link>
    </div>
  )
}
