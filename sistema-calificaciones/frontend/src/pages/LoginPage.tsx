import { Notice } from '../components/Notice'
import { useState, type FormEvent } from 'react'
import { ArrowRight, CheckCircle2, ClipboardList, Layers3 } from 'lucide-react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../auth/useAuth'
import { errorMessage } from '../utils/format'

export function LoginPage() {
  const { session, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [working, setWorking] = useState(false)

  if (session) return <Navigate to="/" replace />

  async function submit(event: FormEvent) {
    event.preventDefault()
    setWorking(true)
    setError(null)
    try {
      await login(email, password)
      const destination = (location.state as { from?: string } | null)?.from ?? '/'
      navigate(destination, { replace: true })
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setWorking(false)
    }
  }

  return (
    <main className="login-layout">
      <section className="login-aside">
        <div className="login-brand"><span className="brand__mark" aria-hidden="true"><span /><span /><span /></span><strong>califica</strong></div>
        <div className="login-aside__content">
          <p className="eyebrow">GESTIÓN DE EVALUACIONES</p>
          <h1>Decisiones claras comienzan con resultados confiables.</h1>
          <p>Organiza evaluaciones, procesa hojas OMR, revisa incidencias y presenta resultados en un solo lugar.</p>
          <div className="login-feature-list">
            <span><ClipboardList size={20} aria-hidden="true" /> Procesamiento organizado</span>
            <span><CheckCircle2 size={20} aria-hidden="true" /> Revisión con claridad</span>
            <span><Layers3 size={20} aria-hidden="true" /> Resultados y reportes</span>
          </div>
        </div>
        <span className="login-aside__footer">CALIFICA · PLATAFORMA DE EVALUACIONES</span>
        <div className="login-decoration login-decoration--one" /><div className="login-decoration login-decoration--two" />
      </section>
      <section className="login-form-panel">
        <div className="login-form-wrap">
          <span className="login-form-wrap__eyebrow">BIENVENIDO</span>
          <h2>Accede a tu espacio de trabajo</h2>
          <p>Ingresa con tu cuenta para continuar con tus evaluaciones.</p>
          <form onSubmit={(event) => void submit(event)}>
            <div className="login-fields">
              <label className="field"><span>Correo electrónico</span>
                <input type="email" value={email} onChange={(event) => setEmail(event.target.value)}
                  placeholder="tu@organizacion.com" autoComplete="username" required /></label>
              <label className="field"><span>Contraseña</span>
                <input type="password" value={password} onChange={(event) => setPassword(event.target.value)}
                  autoComplete="current-password" required /></label>
            </div>
            {error && <Notice tone="error">{error}</Notice>}
            <button className="button button--primary login-submit-button" type="submit" disabled={working}>
              {working ? 'Ingresando…' : 'Ingresar'} <ArrowRight size={18} />
            </button>
          </form>
          <Link to="/instalacion" className="login-form-wrap__caption">¿Es la primera vez? Crear la cuenta inicial</Link>
          <Link to="/consulta" className="login-form-wrap__caption">Consultar mi resultado</Link>
        </div>
      </section>
    </main>
  )
}
