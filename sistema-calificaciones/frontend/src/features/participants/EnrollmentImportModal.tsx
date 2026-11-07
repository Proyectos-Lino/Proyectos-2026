import { useState, type FormEvent } from 'react'
import type { EvaluationOverview, Group } from '../../api/types'
import { Modal } from '../../components/Modal'
import { Notice } from '../../components/Notice'
import { downloadBlob, errorMessage } from '../../utils/format'
import { groupLabel } from '../../utils/group'
import { participantsApi } from './participantsApi'
import type { ImportMode, EnrollmentImportResult } from './types'
import { EnrollmentImportPreview } from './EnrollmentImportPreview'

export function EnrollmentImportModal({ org, evaluation, groups, onClose, onComplete }: {
  org: number; evaluation: EvaluationOverview; groups: Group[]; onClose: () => void; onComplete: (result: EnrollmentImportResult) => void
}) {
  const [file, setFile] = useState<File | null>(null)
  const [mode, setMode] = useState<ImportMode>('REGISTROS')
  const [preview, setPreview] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  async function template() {
    setBusy(true); setError(null)
    try { downloadBlob(await participantsApi.template(org, evaluation.id, mode), `plantilla-${evaluation.code}-${mode.toLowerCase()}.csv`) }
    catch (cause) { setError(errorMessage(cause)) } finally { setBusy(false) }
  }
  function analyze(event: FormEvent) { event.preventDefault(); if (file) setPreview(true) }
  return <Modal wide title={preview ? 'Revisar importación de participantes' : 'Importar participantes CSV'} description={`${evaluation.name} · Los códigos y las asignaciones pertenecen a esta evaluación.`} onClose={onClose} busy={busy}>
    {preview && file ? <EnrollmentImportPreview org={org} evaluation={evaluation.id} file={file} mode={mode} onBack={() => setPreview(false)} onComplete={onComplete} busy={busy} setBusy={setBusy} /> :
      <form className="modal-form" onSubmit={analyze}><fieldset className="choice-group"><legend>Qué quieres cargar</legend>
        <label><input type="radio" name="participantMode" checked={mode === 'REGISTROS'} onChange={() => setMode('REGISTROS')} /> Registrar o completar participantes y asignar grupos</label>
        <label><input type="radio" name="participantMode" checked={mode === 'GRUPOS'} disabled={!groups.length} onChange={() => setMode('GRUPOS')} /> Actualizar solo los grupos de personas ya inscritas</label>
      </fieldset>
      <p className="muted-copy">{mode === 'GRUPOS' ? 'Incluye codigo_evaluacion y grupo. Los nombres, documentos y correos existentes se conservarán.' : 'Incluye codigo_evaluacion y nombres. La plantilla también permite apellidos, tipo_documento, documento y correo. Los registros provisionales del DBF se completan conservando sus exámenes.'}</p>
      {groups.length > 0 ? <div className="participant-group-reference"><strong>{groupLabel(evaluation.groupType)}: opciones disponibles ({groups.length})</strong>
        <p>La columna grupo es obligatoria. Copia el código de esta lista; también se acepta el nombre completo si identifica una única opción.</p>
        <ul>{groups.map((group) => <li key={group.id}><code>{group.code}</code><span>{group.name}</span></li>)}</ul></div>
        : <p className="muted-copy">Esta evaluación no tiene grupos. La plantilla se descarga sin la columna grupo. Puedes configurarlos desde la evaluación cuando los necesites.</p>}
      {mode === 'REGISTROS' && <p className="muted-copy">Para reutilizar una persona de otra evaluación, indica el mismo tipo y número de documento. Tipos: DOCUMENTO, DNI, CE, PASAPORTE u OTRO. Si omites el tipo se usa DOCUMENTO. Sin documento, cada código nuevo registra una persona nueva; el nombre o el correo no se usan para unir personas.</p>}
      <p className="muted-copy">Guarda el CSV en UTF-8, separado por punto y coma o coma. Hasta 5000 filas y 2 MB. Conserva los códigos como texto para mantener los ceros iniciales. Verás todos los cambios antes de aplicarlos.</p>
      <button type="button" className="button button--secondary" disabled={busy} onClick={() => void template()}>Descargar plantilla CSV</button>
      <label className="field"><span>Archivo CSV</span><input type="file" accept=".csv,text/csv" required={!file} onChange={(event) => setFile(event.target.files?.[0] ?? null)} /></label>
      {file && <p className="muted-copy">Seleccionado: {file.name}</p>}{error && <Notice tone="error">{error}</Notice>}
      <div className="modal-form__actions"><button type="button" className="button button--secondary" disabled={busy} onClick={onClose}>Cancelar</button><button className="button button--primary" disabled={busy || !file}>Analizar CSV</button></div>
    </form>}
  </Modal>
}
