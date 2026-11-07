import { BackLink } from '../components/BackLink'
import { Notice } from '../components/Notice'
import { useState, type FormEvent } from 'react'
import { ArrowLeft } from 'lucide-react'
import { useNavigate } from 'react-router'
import { sessionApi } from '../api/session'
import { errorMessage } from '../utils/format'
import { OrganizationProfileFields } from '../features/organizations/OrganizationProfileFields'
import { emptyProfile } from '../features/organizations/profile'

export function SetupPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState({
    token: '', organizationCode: '', organizationName: '', email: '', name: '', password: '',
  })
  const [error, setError] = useState<string | null>(null)
  const [working, setWorking] = useState(false)
  const [profile, setProfile] = useState({ ...emptyProfile })

  async function submit(event: FormEvent) {
    event.preventDefault()
    setWorking(true)
    setError(null)
    try {
      await sessionApi.bootstrap({ ...form, profile })
      navigate('/login', { replace: true })
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setWorking(false)
    }
  }

  return (
    <main className="setup-layout">
      <section className="surface-card setup-card">
        <BackLink to="/login" className="back-link"><ArrowLeft size={17} /> Volver al acceso</BackLink>
        <p className="eyebrow">CONFIGURACIÓN INICIAL</p>
        <h1>Crear la primera cuenta</h1>
        <p>Esta operación se realiza una sola vez en una base de datos nueva. Necesitas la clave de instalación configurada en el backend.</p>
        <form onSubmit={(event) => void submit(event)} className="setup-form">
          {([
            ['token', 'Clave de instalación', 'password'],
            ['organizationCode', 'Código de la organización', 'text'],
            ['organizationName', 'Nombre de la organización', 'text'],
            ['name', 'Tu nombre', 'text'],
            ['email', 'Tu correo', 'email'],
            ['password', 'Tu contraseña (mínimo 8 caracteres)', 'password'],
          ] as const).map(([key, label, type]) => (
            <label className="field" key={key}><span>{label}</span>
              <input type={type} value={form[key]} required minLength={key === 'password' ? 8 : undefined}
                onChange={(event) => setForm((current) => ({ ...current, [key]: event.target.value }))} />
            </label>
          ))}
          <div className="setup-profile"><h2>Datos institucionales</h2><OrganizationProfileFields value={profile} onChange={setProfile} /></div>
          {error && <Notice tone="error">{error}</Notice>}
          <button type="submit" className="button button--primary" disabled={working}>
            {working ? 'Creando cuenta…' : 'Crear organización y administrador'}
          </button>
        </form>
      </section>
    </main>
  )
}
