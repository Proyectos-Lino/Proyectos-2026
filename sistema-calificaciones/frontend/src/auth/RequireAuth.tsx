import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from './useAuth'

export function RequireAuth() {
  const { session, organization, loading, error } = useAuth()
  const location = useLocation()
  if (loading) return <main className="loading-screen">Comprobando sesión…</main>
  if (error) return <main className="loading-screen" role="alert">{error}</main>
  if (!session) return <Navigate to="/login" state={{ from: location.pathname }} replace />
  if (!organization) return <main className="loading-screen">Tu cuenta no tiene una organización activa.</main>
  return <Outlet />
}
