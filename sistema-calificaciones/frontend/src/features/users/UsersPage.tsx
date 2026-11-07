import { useTableState } from '../../hooks/useTableState'
import { useNoticeMessage } from '../../hooks/useNoticeMessage'
import { Notice } from '../../components/Notice'
import { useCallback, useState } from 'react'
import { Plus, Search } from 'lucide-react'
import { Navigate } from 'react-router'
import { usersApi } from '../../api/users'
import type { User } from '../../api/types'
import { useAuth } from '../../auth/useAuth'
import { Pagination } from '../../components/Pagination'
import { PageHeader, StatusBadge } from '../../components/ui'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { useResource } from '../../hooks/useResource'
import { labelStatus } from '../../utils/format'
import { UserModal } from './UserModal'

const roles = ['ADMINISTRADOR', 'OPERADOR', 'REVISOR', 'LECTOR'] as const

export function UsersPage() {
  const { organization, session, refresh } = useAuth()
  const org = organization!.id
  const table = useTableState({ query: '', role: '', page: 0, size: 10 }, 'users')
  const { query, role, page, size } = table.values
  const setSize = (value: number) => table.set('size', value)
  const setQuery = (value: string) => table.set('query', value)
  const setRole = (value: string) => table.set('role', value)
  const setPage = (value: number) => table.set('page', value)
  const search = useDebouncedValue(query)
  const load = useCallback(() => usersApi.page(org, { query: search, role, page, size }), [org, search, role, page, size])
  const { data, loading, error, reload } = useResource(load)
  const [selected, setSelected] = useState<User | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [message, setMessage] = useNoticeMessage()
  if (organization?.role !== 'ADMINISTRADOR') return <Navigate to="/" replace />

  function saved(feedback: string, userId: number | null) {
    setModalOpen(false)
    setMessage(feedback)
    reload()
    if (userId === session?.id) void refresh()
  }

  return <div className="page-stack">
    <PageHeader eyebrow="ADMINISTRACIÓN" title="Usuarios"
      description="Controla quién puede administrar, operar, revisar o consultar los resultados."
      action={<button type="button" className="button button--primary" onClick={() => {
        setSelected(null); setModalOpen(true)
      }}><Plus size={17} /> Nuevo usuario</button>} />
    {message && <Notice tone="success">{message}</Notice>}
    <section className="surface-card form-section">
      <div className="form-section__heading"><div><h2>Funciones de trabajo</h2>
        <p>El rol se asigna por organización. Una misma cuenta puede tener un rol diferente en cada espacio.</p></div></div>
      <div className="roles-grid">
        <div><strong>Administrador</strong><span>Organizaciones, usuarios y control completo.</span></div>
        <div><strong>Operador</strong><span>Evaluaciones, participantes, temas y archivos.</span></div>
        <div><strong>Revisor</strong><span>Consulta y resolución de revisiones de exámenes.</span></div>
        <div><strong>Lector</strong><span>Consulta de evaluaciones y resultados.</span></div>
      </div>
    </section>
    <section className="surface-card">
      <div className="list-toolbar"><div><p className="section-kicker">ACCESOS</p><h2>Miembros</h2>
        <p>{data?.total ?? 0} usuarios</p></div>
        <div className="list-toolbar__controls">
          <label className="search-field"><Search size={17} /><span className="sr-only">Buscar usuario</span>
            <input value={query} onChange={(event) => { setQuery(event.target.value); setPage(0) }}
              placeholder="Nombre o correo…" /></label>
          <label className="select-field"><span className="sr-only">Filtrar rol</span>
            <select value={role} onChange={(event) => { setRole(event.target.value); setPage(0) }}>
              <option value="">Todos los roles</option>
              {roles.map((item) => <option key={item} value={item}>{labelStatus(item)}</option>)}
            </select></label>
        </div></div>
      {error && <Notice tone="error">{error}</Notice>}
      {loading && <p className="loading-inline">Consultando usuarios…</p>}
      {data && <><div className="table-scroll" aria-busy={loading} inert={loading}><table className="data-table">
        <thead><tr><th>Usuario</th><th>Rol</th><th>Acceso</th><th>Acción</th></tr></thead>
        <tbody>{data.items.map((user) => <tr key={user.id}>
          <td><strong className="table-title">{user.name}</strong><span className="table-subtitle">{user.email}</span></td>
          <td>{labelStatus(user.role)}</td>
          <td><StatusBadge status={user.membershipActive && user.active ? 'ACTIVO' : 'INACTIVO'} /></td>
          <td><button type="button" className="text-link" onClick={() => {
            setSelected(user); setModalOpen(true)
          }}>Editar</button></td>
        </tr>)}</tbody>
      </table>{data.items.length === 0 && <div className="empty-state">No hay usuarios con esos criterios.</div>}</div>
        <Pagination result={data} onPage={setPage} size={size} onSize={setSize} busy={loading} /></>}
    </section>
    {modalOpen && <UserModal key={selected?.id ?? 'new'} organizationId={org} user={selected} currentUserId={session!.id}
      onClose={() => setModalOpen(false)} onSaved={saved} />}
  </div>
}
