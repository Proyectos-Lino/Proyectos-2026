import { useCallback, useState } from 'react'
import { participantsApi } from './participantsApi'
import type { ImportMode, EnrollmentImportResult } from './types'
import { ImportCounts, EnrollmentImportRows } from './EnrollmentImportRows'
import { importActions } from './importLabels'
import { Notice } from '../../components/Notice'
import { useResource } from '../../hooks/useResource'
import { useTableState } from '../../hooks/useTableState'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { errorMessage } from '../../utils/format'

export function EnrollmentImportPreview({ org, evaluation, file, mode, onBack, onComplete, busy, setBusy }: {
  org: number; evaluation: number; file: File; mode: ImportMode; onBack: () => void
  onComplete: (result: EnrollmentImportResult) => void; busy: boolean; setBusy: (value: boolean) => void
}) {
  const table = useTableState({ query: '', action: '', page: 0, size: 20 }, 'enrollmentPreview')
  const { query, action, page, size } = table.values
  const search = useDebouncedValue(query)
  const load = useCallback(() => participantsApi.preview(org, evaluation, file, mode, { query: search, action, page, size }), [org, evaluation, file, mode, search, action, page, size])
  const { data, error, loading, reload } = useResource(load)
  const [confirmationError, setConfirmationError] = useState<string | null>(null)
  const [outdated, setOutdated] = useState(false)
  async function confirm() {
    if (!data || loading) return
    setBusy(true); setConfirmationError(null)
    try { onComplete(await participantsApi.confirm(org, evaluation, file, mode, data.fingerprint)) }
    catch (cause) { setConfirmationError(errorMessage(cause)); setOutdated(true) }
    finally { setBusy(false) }
  }
  return <div className="modal-form"><p><strong>{file.name}</strong> · {mode === 'GRUPOS' ? 'Actualizar grupos de inscritos' : 'Registrar, completar y asignar'}</p>
    {loading && <p role="status">Validando el archivo y sus inscripciones…</p>}{error && <Notice tone="error">{error}</Notice>}
    {data && <><ImportCounts counts={data.counts} /><fieldset className="participant-import-filters" disabled={busy}>
      <label className="field"><span>Buscar en la vista previa</span><input value={query} onChange={(event) => table.set('query', event.target.value)} /></label>
      <label className="field"><span>Resultado de validación</span><select value={action} onChange={(event) => table.set('action', event.target.value)}><option value="">Todas las filas</option>{Object.entries(importActions).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
    </fieldset><EnrollmentImportRows result={data.rows} loading={loading || busy} pageSize={size} onSize={(value) => table.set('size', value)} onPage={(value) => table.set('page', value)} />
      <p className="muted-copy">{data.counts.errors ? 'Corrige las filas indicadas y vuelve a seleccionar el archivo. No se guardará ninguna fila mientras haya problemas.' : 'Al confirmar se aplicará todo el archivo. Se conservarán los datos personales completos y se actualizarán los grupos indicados. Los puestos se calcularán al terminar la carga.'}</p></>}
    {confirmationError && <Notice tone="error">{confirmationError}</Notice>}
    <div className="modal-form__actions"><button type="button" className="button button--secondary" disabled={busy} onClick={onBack}>Volver al archivo</button>
      <button type="button" className="button button--secondary" disabled={busy || loading} onClick={() => { setOutdated(false); setConfirmationError(null); reload() }}>Actualizar vista previa</button>
      <button type="button" className="button button--primary" disabled={busy || loading || !data || data.counts.errors > 0 || outdated} onClick={() => void confirm()}>{busy ? 'Aplicando cambios…' : 'Confirmar importación'}</button></div>
  </div>
}
