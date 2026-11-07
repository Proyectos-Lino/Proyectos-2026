import { Notice } from '../../components/Notice'
import { useState, type FormEvent } from 'react'
import { usersApi } from '../../api/users'
import type { User } from '../../api/types'
import { Modal } from '../../components/Modal'
import { errorMessage, labelStatus } from '../../utils/format'

const roles = ['ADMINISTRADOR', 'OPERADOR', 'REVISOR', 'LECTOR'] as const

interface UserModalProps {
  organizationId: number
  currentUserId: number
  user: User | null
  onClose: () => void
  onSaved: (message: string, userId: number | null) => void
}

export function UserModal({ organizationId, currentUserId, user, onClose, onSaved }: UserModalProps) {
  const membershipOnly = Boolean(user?.sharedAccount && user.id !== currentUserId)
  const [mode, setMode] = useState<'new' | 'existing'>('new')
  const [form, setForm] = useState({
    name: user?.name ?? '', email: user?.email ?? '', password: '',
    role: user?.role ?? 'OPERADOR', active: user?.membershipActive ?? true,
  })
  const [working, setWorking] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function save(event: FormEvent) {
    event.preventDefault()
    setWorking(true); setError(null)
    try {
      if (user && membershipOnly) {
        await usersApi.update(organizationId, user.id, form.role, form.active)
        onSaved('Permisos actualizados.', user.id)
      } else if (user) {
        await usersApi.updateProfile(organizationId, user.id, {
          ...form, password: form.password.trim() ? form.password : null,
        })
        onSaved('Usuario actualizado.', user.id)
      } else if (mode === 'existing') {
        await usersApi.link(organizationId, form.email, form.role)
        onSaved('Cuenta vinculada a esta organización.', null)
      } else {
        await usersApi.create(organizationId, {
          name: form.name, email: form.email, password: form.password, role: form.role,
        })
        onSaved('Usuario creado.', null)
      }
    } catch (cause) { setError(errorMessage(cause)) }
    finally { setWorking(false) }
  }

  return <Modal title={user ? 'Editar usuario' : 'Nuevo usuario'}
    description={membershipOnly ? 'Esta cuenta también pertenece a otros espacios. Aquí solo puedes cambiar su rol y acceso.'
      : user ? 'Actualiza los datos de acceso y permisos de esta organización.'
      : 'La persona podrá acceder con el correo y la contraseña asignados.'}
    onClose={onClose} busy={working}>
    <form onSubmit={(event) => void save(event)} className="modal-form">
      {!user && <div className="modal-form__grid">
        <label className="modal-form__checkbox"><input type="radio" name="user-mode" checked={mode === 'new'} onChange={() => setMode('new')} /> Crear cuenta</label>
        <label className="modal-form__checkbox"><input type="radio" name="user-mode" checked={mode === 'existing'} onChange={() => setMode('existing')} /> Vincular cuenta existente</label>
      </div>}
      <div className="modal-form__grid">
        {!membershipOnly && (user || mode === 'new') && <label className="field"><span>Nombre</span><input value={form.name} required autoFocus
          onChange={(event) => setForm({ ...form, name: event.target.value })} /></label>}
        {!membershipOnly && <label className="field"><span>Correo</span><input type="email" value={form.email} required
          onChange={(event) => setForm({ ...form, email: event.target.value })} /></label>}
        {!membershipOnly && (user || mode === 'new') && <label className="field"><span>{user ? 'Nueva contraseña' : 'Contraseña'}</span>
          <input type="password" minLength={8} value={form.password} required={!user} autoComplete="new-password"
            placeholder={user ? 'Dejar vacío para conservarla' : 'Mínimo 8 caracteres'}
            onChange={(event) => setForm({ ...form, password: event.target.value })} /></label>}
        <label className="field"><span>Rol</span><select value={form.role}
          onChange={(event) => setForm({ ...form, role: event.target.value as User['role'] })}>
          {roles.map((role) => <option key={role} value={role}>{labelStatus(role)}</option>)}
        </select></label>
      </div>
      <p className="muted-copy">{form.role === 'ADMINISTRADOR' ? 'Administra organizaciones, usuarios y todo el proceso.'
        : form.role === 'OPERADOR' ? 'Prepara evaluaciones, participantes y cargas de exámenes.'
          : form.role === 'REVISOR' ? 'Consulta evaluaciones y resuelve revisiones de exámenes.'
            : 'Consulta resultados y datos sin modificarlos.'}</p>
      {user && <label className="modal-form__checkbox">
        <input type="checkbox" checked={form.active}
          onChange={(event) => setForm({ ...form, active: event.target.checked })} />
        <span><strong>Acceso activo</strong><small>Permitir que este usuario acceda a la organización.</small></span>
      </label>}
      {error && <Notice tone="error">{error}</Notice>}
      <div className="modal-form__actions">
        <button type="button" className="button button--secondary" onClick={onClose} disabled={working}>Cancelar</button>
        <button type="submit" className="button button--primary" disabled={working}>
          {working ? 'Guardando…' : user ? 'Guardar cambios' : mode === 'existing' ? 'Vincular cuenta' : 'Crear usuario'}
        </button>
      </div>
    </form>
  </Modal>
}
