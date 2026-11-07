import { Notice } from './Notice'
import { useState, type FormEvent } from 'react'
import type { BulkImportSummary } from '../api/types'
import { errorMessage } from '../utils/format'
import { Modal } from './Modal'

interface Props {
  title: string
  columns: string[]
  example: string[]
  onImport: (file: File) => Promise<BulkImportSummary>
  onClose: () => void
  onComplete: (summary: BulkImportSummary) => void
  description?: string
}

export function CsvImportModal({ title, columns, example, onImport, onClose, onComplete, description }: Props) {
  const [file, setFile] = useState<File | null>(null)
  const [working, setWorking] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function template() {
    const csv = `\uFEFF${columns.join(';')}\r\n${example.join(';')}\r\n`
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
    const link = document.createElement('a')
    link.href = url; link.download = 'plantilla.csv'; link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!file) return
    setWorking(true); setError(null)
    try { onComplete(await onImport(file)) }
    catch (cause) { setError(errorMessage(cause)) }
    finally { setWorking(false) }
  }

  return <Modal title={title} description={description ?? 'Carga hasta 5000 registros en un CSV UTF-8. Los códigos que ya existan se omiten.'}
    onClose={onClose} busy={working}>
    <form className="modal-form" onSubmit={(event) => void submit(event)}>
      <p className="muted-copy">Columnas: {columns.join(', ')}. Se aceptan archivos separados por punto y coma o coma.</p>
      <button type="button" className="button button--secondary" onClick={template}>Descargar plantilla CSV</button>
      <label className="field"><span>Archivo CSV</span><input type="file" accept=".csv,text/csv" required
        onChange={(event) => setFile(event.target.files?.[0] ?? null)} /></label>
      {error && <Notice tone="error">{error}</Notice>}
      <div className="modal-form__actions"><button type="button" className="button button--secondary" onClick={onClose}>Cancelar</button>
        <button className="button button--primary" disabled={working || !file}>{working ? 'Importando…' : 'Importar registros'}</button></div>
    </form>
  </Modal>
}
