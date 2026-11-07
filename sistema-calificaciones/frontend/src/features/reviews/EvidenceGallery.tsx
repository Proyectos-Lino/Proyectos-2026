import { useNoticeMessage } from '../../hooks/useNoticeMessage'
import { Notice } from '../../components/Notice'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { reviewsApi } from '../../api/reviews'
import { useResource } from '../../hooks/useResource'
import { errorMessage } from '../../utils/format'
import { Modal } from '../../components/Modal'
import { EvidenceViewer } from './EvidenceViewer'

export function EvidenceGallery({ org, evaluationId, reviewId, canEdit }: {
  org: number; evaluationId: number; reviewId: number; canEdit: boolean
}) {
  const load = useCallback(() => reviewsApi.evidence(org, evaluationId, reviewId), [org, evaluationId, reviewId])
  const { data: evidence, error, reload } = useResource(load)
  const [urls, setUrls] = useState<Record<number, string>>({})
  const [files, setFiles] = useState<File[]>([])
  const [uploadOpen, setUploadOpen] = useState(false)
  const [selected, setSelected] = useState<number | null>(null)
  const [working, setWorking] = useState(false)
  const [message, setMessage] = useNoticeMessage()
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    const created: string[] = []
    Promise.all((evidence ?? []).map(async (item) => {
      const blob = await reviewsApi.file(org, evaluationId, item.id)
      if (!active) return null
      const url = URL.createObjectURL(blob)
      created.push(url)
      return [item.id, url] as const
    })).then((entries) => { if (active) setUrls(Object.fromEntries(entries.filter((entry) => entry !== null))) })
      .catch((cause) => { if (active) setActionError(errorMessage(cause)) })
    return () => { active = false; created.forEach((url) => URL.revokeObjectURL(url)) }
  }, [evidence, org, evaluationId])

  async function upload(event: FormEvent) {
    event.preventDefault()
    if (!files?.length) return
    setWorking(true); setMessage(null); setActionError(null)
    try {
      for (const file of files) {
        await reviewsApi.attach(org, evaluationId, reviewId, file)
        setFiles((current) => current.filter((pending) => pending !== file))
      }
      setUploadOpen(false); setMessage('Evidencias guardadas.')
    } catch (cause) { setActionError(errorMessage(cause)) }
    finally { reload(); setWorking(false) }
  }

  async function remove(id: number) {
    if (!window.confirm('¿Eliminar esta evidencia?')) return
    setWorking(true); setMessage(null); setActionError(null)
    try { await reviewsApi.deleteEvidence(org, evaluationId, id); reload(); setMessage('Evidencia eliminada.') }
    catch (cause) { setActionError(errorMessage(cause)) }
    finally { setWorking(false) }
  }

  return <div className="evidence-section">
    <strong>Evidencias</strong>
    {error && <Notice tone="error">{error}</Notice>}
    <div className="evidence-grid">
      {evidence?.map((item) => <div className="evidence-item" key={item.id}>
        {urls[item.id] ? <button type="button" className="evidence-thumbnail" aria-label={`Ver evidencia ${item.filename}`} onClick={() => setSelected(item.id)}>
          <img src={urls[item.id]} alt={item.filename} /></button> : <span>Cargando imagen…</span>}
        <small title={item.filename}>{item.filename}</small>
        <small>Registrada por {item.uploadedByName ?? 'Usuario no disponible'}</small>
        <small>{new Date(item.uploadedAt).toLocaleString('es-PE')}</small>
        {canEdit && <button type="button" className="text-link" disabled={working}
          onClick={() => void remove(item.id)}>Eliminar</button>}
      </div>)}
      {evidence?.length === 0 && <p className="muted-copy">No hay imágenes adjuntas.</p>}
    </div>
    {canEdit && <button type="button" className="button button--secondary" onClick={() => setUploadOpen(true)}>Agregar evidencias</button>}
    {uploadOpen && <Modal title="Agregar evidencias" description="Selecciona imágenes JPEG, PNG o WebP de hasta 10 MiB cada una. Se registrarán con tu usuario y la fecha de carga." onClose={() => setUploadOpen(false)} busy={working}>
      <form onSubmit={(event) => void upload(event)} className="modal-form">
      <label className="field"><span>Agregar imágenes (JPEG, PNG o WebP)</span>
        <input type="file" accept="image/jpeg,image/png,image/webp" multiple
          onChange={(event) => setFiles(Array.from(event.target.files ?? []))} /></label>
      <p className="muted-copy">{files.length} imágenes pendientes de subir.</p>
      <button className="button button--secondary" type="submit" disabled={working || !files?.length}>
        {working ? 'Guardando…' : 'Subir evidencias'}</button>
      {actionError && <Notice tone="error">{actionError}</Notice>}
    </form></Modal>}
    {selected !== null && evidence?.find((item) => item.id === selected) && urls[selected] && <EvidenceViewer
      evidence={evidence.find((item) => item.id === selected)!} url={urls[selected]} onClose={() => setSelected(null)} />}
    {message && <Notice tone="success">{message}</Notice>}
    {!uploadOpen && actionError && <Notice tone="error">{actionError}</Notice>}
  </div>
}
