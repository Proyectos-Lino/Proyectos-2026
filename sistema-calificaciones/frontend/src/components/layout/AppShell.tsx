import { useState } from 'react'
import { Building2, ClipboardList, LayoutDashboard, LogOut, Menu, PanelLeftClose, Plus, Users, UserRoundCog, X } from 'lucide-react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../../auth/useAuth'
import { labelStatus } from '../../utils/format'
import { EvaluationNavigation } from '../../features/evaluations/EvaluationNavigation'

const navigation = [
  { label: 'Panel principal', to: '/', icon: LayoutDashboard, end: true },
  { label: 'Evaluaciones', to: '/evaluaciones', icon: ClipboardList, end: false },
  { label: 'Participantes', to: '/participantes', icon: Users, end: false },
]

function Brand() {
  return (
    <div className="brand">
      <span className="brand__mark" aria-hidden="true"><span /><span /><span /></span>
      <span className="brand__text"><strong>califica</strong><small>GESTIÓN DE EVALUACIONES</small></span>
    </div>
  )
}

export function AppShell() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const { session, organization, logout, selectOrganization } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const canOperate = organization && ['ADMINISTRADOR', 'OPERADOR'].includes(organization.role)
  const currentSection = location.pathname.startsWith('/organizaciones') ? 'Organizaciones'
    : location.pathname.startsWith('/usuarios') ? 'Usuarios'
    : location.pathname.startsWith('/participantes') ? 'Participantes'
      : location.pathname.startsWith('/evaluaciones') ? 'Evaluaciones' : 'Panel principal'

  async function signOut() {
    try {
      await logout()
      navigate('/login', { replace: true })
    } catch {
      // La sesión actual permanece visible para volver a intentar el cierre.
    }
  }

  return (
    <div className="app-layout">
      {mobileMenuOpen && <button className="sidebar-backdrop" type="button" aria-label="Cerrar menú" onClick={() => setMobileMenuOpen(false)} />}
      <aside id="navegacion-principal" className={`sidebar ${mobileMenuOpen ? 'sidebar--open' : ''}`}>
        <div className="sidebar__top">
          <Brand />
          <button type="button" className="icon-button sidebar__close" aria-label="Cerrar menú" onClick={() => setMobileMenuOpen(false)}><X size={20} /></button>
        </div>
        <div className="sidebar__organization">
          <span className="organization-avatar">{organization?.name.slice(0, 2).toUpperCase()}</span>
          <span className="organization-copy">
            {session && session.organizations.length > 1 ? (
              <select aria-label="Organización activa" value={organization?.id}
                onChange={(event) => { selectOrganization(Number(event.target.value)); navigate('/') }}>
                {session.organizations.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
              </select>
            ) : <strong title={organization?.name}>{organization?.name}</strong>}
            <small>{labelStatus(organization?.role ?? '')}</small>
          </span>
        </div>
        <nav aria-label="Navegación principal" className="sidebar__nav">
          <p className="sidebar__label">ESPACIO DE TRABAJO</p>
          {[...navigation, ...(organization?.role === 'ADMINISTRADOR'
            ? [{ label: 'Usuarios', to: '/usuarios', icon: UserRoundCog, end: false },
              { label: 'Organizaciones', to: '/organizaciones', icon: Building2, end: false }] : [])]
            .map(({ label, to, icon: Icon, end }) => (
              <NavLink key={to} to={to} end={end} onClick={() => setMobileMenuOpen(false)}
                className={({ isActive }) => `nav-link ${isActive ? 'nav-link--active' : ''}`}>
                <Icon size={19} strokeWidth={1.8} aria-hidden="true" /><span>{label}</span>
              </NavLink>
            ))}
        </nav>
        <div className="sidebar__bottom">
          <div className="sidebar__profile">
            <span className="profile-avatar">{session?.name.slice(0, 2).toUpperCase()}</span>
            <span><strong>{session?.name}</strong><small>{session?.email}</small></span>
          </div>
          <button type="button" className="sidebar__logout" onClick={() => void signOut()}>
            <LogOut size={16} /> Cerrar sesión
          </button>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <div className="topbar__left">
            <button type="button" className="icon-button topbar__menu" aria-label="Abrir menú"
              aria-controls="navegacion-principal" aria-expanded={mobileMenuOpen} onClick={() => setMobileMenuOpen(true)}>
              <Menu size={22} />
            </button>
            <PanelLeftClose size={18} className="topbar__desktop-icon" aria-hidden="true" />
            <span className="topbar__divider" /><span className="topbar__section">{currentSection}</span>
          </div>
          <div className="topbar__right">
            {canOperate && <NavLink to="/evaluaciones/nueva" className="button button--header"><Plus size={16} /> Nueva evaluación</NavLink>}
          </div>
        </header>
        <main id="contenido" className="workspace__content"><EvaluationNavigation /><Outlet /></main>
        <footer className="workspace__footer">Califica · Gestión y calificación de evaluaciones</footer>
      </div>
    </div>
  )
}
