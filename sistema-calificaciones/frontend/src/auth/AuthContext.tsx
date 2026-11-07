import { useEffect, useState, type ReactNode } from 'react'
import { ApiError } from '../api/client'
import { sessionApi } from '../api/session'
import type { Session } from '../api/types'
import { AuthContext } from './context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null)
  const [organizationId, setOrganizationId] = useState<number | null>(() => {
    const saved = window.localStorage.getItem('califica.organizationId')
    return saved ? Number(saved) : null
  })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  async function refresh() {
    try {
      setSession(await sessionApi.me())
      setError(null)
    } catch (cause) {
      setSession(null)
      if (!(cause instanceof ApiError && cause.status === 401)) {
        setError(cause instanceof Error ? cause.message : 'No se pudo verificar la sesión.')
      }
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let active = true
    sessionApi.me().then((current) => {
      if (active) { setSession(current); setError(null); setLoading(false) }
    }).catch((cause) => {
      if (active) {
        if (!(cause instanceof ApiError && cause.status === 401)) {
          setError(cause instanceof Error ? cause.message : 'No se pudo verificar la sesión.')
        }
        setLoading(false)
      }
    })
    return () => { active = false }
  }, [])

  async function login(email: string, password: string) {
    const current = await sessionApi.login(email, password)
    setSession(current)
    setError(null)
  }

  async function logout() {
    await sessionApi.logout()
    setSession(null)
  }

  function selectOrganization(id: number) {
    setOrganizationId(id)
    window.localStorage.setItem('califica.organizationId', String(id))
  }

  const organization = session?.organizations.find((item) => item.id === organizationId)
    ?? session?.organizations[0] ?? null

  return (
    <AuthContext.Provider value={{ session, organization, loading, error, login, logout, selectOrganization, refresh }}>
      {children}
    </AuthContext.Provider>
  )
}
