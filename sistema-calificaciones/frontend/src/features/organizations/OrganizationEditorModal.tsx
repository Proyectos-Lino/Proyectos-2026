import { useEffect, useState, type FormEvent } from 'react'
import { organizationsApi } from '../../api/organizations'
import type { Organization } from '../../api/types'
import { Modal } from '../../components/Modal'
import { Notice } from '../../components/Notice'
import { ImagePreview } from '../../components/ImagePreview'
import { errorMessage } from '../../utils/format'
import { OrganizationProfileFields } from './OrganizationProfileFields'
import { emptyProfile } from './profile'

export function OrganizationEditorModal({ currentId, organization, onClose, onSaved }: {
  currentId: number; organization: Organization | null; onClose: () => void; onSaved: (id: number, created: boolean) => Promise<void>
}) {
  const [code, setCode] = useState(organization?.code ?? '')
  const [name, setName] = useState(organization?.name ?? '')
  const [profile, setProfile] = useState({ ...emptyProfile })
  const [loading, setLoading] = useState(Boolean(organization))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [savedId, setSavedId] = useState<number | null>(organization?.id ?? null)
  const [existingLogo, setExistingLogo] = useState<Blob | null>(null)
  const [file, setFile] = useState<File | null>(null)
  const [removeLogo, setRemoveLogo] = useState(false)
  const [loadRevision, setLoadRevision] = useState(0)
  useEffect(() => {
    if (!organization) return
    let active = true
    organizationsApi.profile(organization.id).then(async (data) => {
      if (active) {
        const next = { ...emptyProfile }
        for (const key of Object.keys(emptyProfile) as (keyof typeof emptyProfile)[]) next[key] = data[key] ?? ''
        setProfile(next)
      }
      if (data.hasLogo) { const logo = await organizationsApi.logo(organization.id); if (active) setExistingLogo(logo) }
      if (active) setLoading(false)
    }).catch((cause) => { if (active) setError(errorMessage(cause)) })
    return () => { active = false }
  }, [organization, loadRevision])
  const preview = file ?? (removeLogo ? null : existingLogo)

  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(null)
    try {
      let id = savedId
      if (id) await organizationsApi.update(id, name, profile)
      else { id = (await organizationsApi.create(currentId, code, name, profile)).id; setSavedId(id) }
      if (file) await organizationsApi.saveLogo(id, file)
      else if (removeLogo) await organizationsApi.deleteLogo(id)
      await onSaved(id, !organization)
    } catch (cause) { setError(errorMessage(cause)) }
    finally { setBusy(false) }
  }

  return <Modal title={organization ? 'Datos institucionales y logo' : 'Nueva organización'} onClose={onClose} busy={busy}
    description="Estos datos identificarán a la organización en los documentos de resultados. Los campos con * son obligatorios.">
    <form className="modal-form" onSubmit={(event) => void submit(event)}>
      {loading && !error && <p className="loading-inline">Cargando datos institucionales…</p>}
      <div className="modal-form__grid"><label className="field"><span>Código *</span><input value={code} maxLength={40}
        required disabled={Boolean(savedId)} onChange={(event) => setCode(event.target.value)} /></label>
        <label className="field"><span>Nombre corto *</span><input value={name} maxLength={180} required onChange={(event) => setName(event.target.value)} /></label></div>
      <OrganizationProfileFields value={profile} onChange={setProfile} />
      <section className="organization-logo-editor"><div><strong>Logo institucional</strong><p>PNG o JPEG, hasta 2 MB y 2048 × 2048 píxeles.</p>
        <label className="field"><span>Seleccionar imagen</span><input type="file" accept="image/png,image/jpeg"
          onChange={(event) => { setFile(event.target.files?.[0] ?? null); setRemoveLogo(false) }} /></label></div>
        {preview && <ImagePreview blob={preview} alt="Vista previa del logo institucional" />}
        {(existingLogo || file) && <button type="button" className="text-link" onClick={() => { setRemoveLogo(true); setFile(null) }}>Quitar logo</button>}
      </section>
      {error && <Notice tone="error">{error}{loading && <button type="button" className="text-link" onClick={() => {
        setError(null); setLoadRevision((current) => current + 1)
      }}>Volver a cargar</button>}</Notice>}
      <div className="modal-form__actions"><button type="button" className="button button--secondary" disabled={busy} onClick={onClose}>Cancelar</button>
        <button className="button button--primary" disabled={loading || busy}>{busy ? 'Guardando…' : 'Guardar organización'}</button></div>
    </form>
  </Modal>
}
