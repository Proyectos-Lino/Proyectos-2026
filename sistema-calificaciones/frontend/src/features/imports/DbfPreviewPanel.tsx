import { Notice } from '../../components/Notice'
import { useCallback, useState } from 'react'
import { Link } from 'react-router'
import { importsApi, type DbfFiles, type DbfPreviewOptions } from '../../api/imports'
import type { DbfColumnMapping, ImportSummary } from '../../api/types'
import { Pagination } from '../../components/Pagination'
import { useResource } from '../../hooks/useResource'
import { errorMessage } from '../../utils/format'
import { useTableState } from '../../hooks/useTableState'

export interface PreviewSelection {
  files: DbfFiles; mapping: DbfColumnMapping; options: DbfPreviewOptions
}

const decisions = {
  ACEPTAR: 'Apta', OBSERVADA: 'Apta con observación', CORREGIR: 'Necesita corrección',
}

export function DbfPreviewPanel({ org, evaluationId, selection, onConfirmed }: {
  org: number; evaluationId: number; selection: PreviewSelection
  onConfirmed: (summary: ImportSummary) => void
}) {
  const table = useTableState({ page: 0, size: 20, filter: 'TODOS' }, 'dbfPreview')
  const { page, size, filter } = table.values
  const setPage = (value: number) => table.set('page', value)
  const setFilter = (value: string) => table.set('filter', value)
  const [working, setWorking] = useState(false)
  const [confirmError, setConfirmError] = useState<string | null>(null)
  const load = useCallback(() => importsApi.preview(org, evaluationId, selection.files,
    selection.mapping, selection.options, filter, page, size), [org, evaluationId, selection, filter, page, size])
  const { data: preview, loading, error, reload } = useResource(load)
  const canConfirm = preview && preview.total > 0 && preview.correctionNeeded === 0
    && preview.generalIssues.length === 0

  async function confirm() {
    if (!preview || !canConfirm) return
    setWorking(true); setConfirmError(null)
    try { onConfirmed(await importsApi.confirm(org, evaluationId, selection.files,
      selection.mapping, selection.options, preview.fingerprint)) }
    catch (cause) { setConfirmError(errorMessage(cause)) }
    finally { setWorking(false) }
  }

  return <section className="surface-card form-section" aria-label="Vista previa de importación">
    <div className="form-section__heading"><span className="step-number">02</span>
      <div><h2>Vista previa y validación</h2><p>Revisa lo que se procesará antes de confirmar.</p></div></div>
    {loading && <p className="loading-inline">Analizando archivos y registros existentes…</p>}
    {error && <Notice tone="error">{error} <button type="button" onClick={reload}>Reintentar</button></Notice>}
    {preview && <>
      <div className="preview-metrics" aria-label="Resumen de validación">
        <div><strong>{preview.total}</strong><span>Hojas leídas</span></div>
        <div><strong>{preview.accepted}</strong><span>Aptas</span></div>
        <div><strong>{preview.observed}</strong><span>Aptas con observación</span></div>
        <div><strong>{preview.correctionNeeded}</strong><span>Por corregir</span></div>
      </div>
      {preview.generalIssues.length > 0 && <div className="preview-issues" role="alert">
        <strong>Problemas en los archivos o la configuración</strong>
        <ul>{preview.generalIssues.map((issue, index) => <li key={index}>{issue}</li>)}</ul>
      </div>}
      <div className="list-toolbar"><div><h3>Detalle por hoja</h3><p>La fila corresponde al archivo RESPUEST.</p></div>
        <label className="select-field"><span className="sr-only">Filtrar resultado de validación</span>
          <select value={filter} onChange={(event) => { setFilter(event.target.value); setPage(0) }}>
            <option value="TODOS">Todas las hojas</option><option value="ACEPTAR">Aptas</option>
            <option value="OBSERVADA">Observadas</option><option value="CORREGIR">Por corregir</option>
          </select></label></div>
      <div className="table-scroll"><table className="data-table">
        <thead><tr><th>Fila</th><th>Hoja</th><th>Participante</th><th>Tema</th><th>Decisión</th><th>Detalle</th></tr></thead>
        <tbody>{preview.rows.items.map((row) => <tr key={row.row}>
          <td>{row.row}</td><td>{row.sheetCode || '—'}</td><td>{row.participantCode || '—'}</td>
          <td>{row.formCode || '—'}</td><td><span className={`preview-decision preview-decision--${row.decision.toLowerCase()}`}>
            {decisions[row.decision]}</span></td><td>{row.issues.join('; ') || 'Lista para procesar'}</td>
        </tr>)}</tbody>
      </table>{preview.rows.items.length === 0 && <div className="empty-state">No hay hojas con este filtro.</div>}</div>
      <Pagination result={preview.rows} onPage={setPage} size={size} onSize={(value) => table.set('size', value)} busy={loading} />
      {canConfirm ? <p className="muted-copy">Al confirmar se volverán a validar los archivos. Las hojas observadas quedarán disponibles para revisión.</p>
        : <p className="muted-copy">Corrige los archivos, la configuración o el mapeo y genera otra vista previa.</p>}
      {confirmError && <Notice tone="error">{confirmError}</Notice>}
      <div className="modal-form__actions"><Link className="button button--secondary" to={`/evaluaciones/${evaluationId}/configuracion`}>Revisar configuración</Link>
        <button type="button" className="button button--primary" disabled={!canConfirm || working}
          onClick={() => void confirm()}>{working ? 'Procesando…' : 'Confirmar importación'}</button></div>
    </>}
  </section>
}
