import { useNoticeMessage } from '../../hooks/useNoticeMessage'
import { useState } from 'react'
import { Building2, Plus } from 'lucide-react'
import { Navigate } from 'react-router'
import type { Organization } from '../../api/types'
import { useAuth } from '../../auth/useAuth'
import { Notice } from '../../components/Notice'
import { PageHeader } from '../../components/ui'
import { labelStatus } from '../../utils/format'
import { OrganizationEditorModal } from './OrganizationEditorModal'

export function OrganizationsPage() {
  const { organization, session, refresh, selectOrganization } = useAuth()
  const [selected, setSelected] = useState<Organization | null>(null)
  const [open, setOpen] = useState(false)
  const [message, setMessage] = useNoticeMessage()
  if (!organization || organization.role !== 'ADMINISTRADOR') return <Navigate to="/" replace />

  async function saved(id: number, created: boolean) {
    await refresh()
    if (created) selectOrganization(id)
    setOpen(false)
    setMessage(created ? 'Organización creada con sus datos institucionales.' : 'Datos institucionales actualizados.')
  }

  return <div className="page-stack">
    <PageHeader eyebrow="ADMINISTRACIÓN" title="Organizaciones"
      description="Gestiona la identidad institucional, los datos de contacto y el logo que aparecerán en los documentos."
      action={<button type="button" className="button button--primary" onClick={() => { setSelected(null); setOpen(true) }}><Plus size={17} /> Nueva organización</button>} />
    {message && <Notice tone="success">{message}</Notice>}
    <section className="surface-card form-section">
      <div className="form-section__heading"><Building2 size={24} aria-hidden="true" />
        <div><h2>Espacios disponibles</h2><p>Cada organización conserva sus propios usuarios, participantes y evaluaciones.</p></div></div>
      <div className="simple-list">{session?.organizations.map((item) => <div className="group-card" key={item.id}>
        <div><strong>{item.name}</strong><span>{item.code} · {labelStatus(item.role)}{item.id === organization.id ? ' · Activa' : ''}</span></div>
        {item.role === 'ADMINISTRADOR' && <button type="button" className="button button--secondary"
          onClick={() => { setSelected(item); setOpen(true) }}>Datos y logo</button>}
      </div>)}</div>
    </section>
    {open && <OrganizationEditorModal key={selected?.id ?? 'new'} currentId={organization.id} organization={selected}
      onClose={() => { setOpen(false); void refresh() }} onSaved={saved} />}
  </div>
}
