import { useCallback, useState } from 'react'
import { Modal } from '../../components/Modal'
import { Notice } from '../../components/Notice'
import { useResource } from '../../hooks/useResource'
import { useTableState } from '../../hooks/useTableState'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { downloadBlob, errorMessage } from '../../utils/format'
import { participantsApi } from './participantsApi'
import { EnrollmentImportRows } from './EnrollmentImportRows'
import { importActions } from './importLabels'

export function EnrollmentImportReportModal({ org, evaluation, report, onClose }: { org: number; evaluation: number; report: number; onClose: () => void }) {
  const table = useTableState({ query: '', action: '', page: 0, size: 20 }, 'enrollmentReport')
  const { query, action, page, size } = table.values
  const search = useDebouncedValue(query)
  const load = useCallback(() => participantsApi.report(org, evaluation, report, { query: search, action, page, size }), [org, evaluation, report, search, action, page, size])
  const { data, error, loading } = useResource(load)
  const [busy, setBusy] = useState(false)
  const [exportError, setExportError] = useState<string | null>(null)
  async function download() {
    setBusy(true); setExportError(null)
    try { downloadBlob(await participantsApi.reportCsv(org, evaluation, report), `informe-participantes-${report}.csv`) }
    catch (cause) { setExportError(errorMessage(cause)) } finally { setBusy(false) }
  }
  return <Modal wide title="Resultado de la importación" description="Cambios aplicados a las inscripciones de esta evaluación. Los datos personales completos se conservaron." onClose={onClose} busy={busy}>
    <div className="modal-form"><div className="participant-import-filters"><label className="field"><span>Buscar en el informe</span><input value={query} onChange={(event) => table.set('query', event.target.value)} /></label>
      <label className="field"><span>Acción realizada</span><select value={action} onChange={(event) => table.set('action', event.target.value)}><option value="">Todas</option>{Object.entries(importActions).filter(([value]) => value !== 'ERROR').map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label></div>
      {loading && <p role="status">Consultando informe…</p>}{error && <Notice tone="error">{error}</Notice>}
      {data && <EnrollmentImportRows result={data} loading={loading} pageSize={size} onSize={(value) => table.set('size', value)} onPage={(value) => table.set('page', value)} />}
      {exportError && <Notice tone="error">{exportError}</Notice>}
      <div className="modal-form__actions"><button type="button" className="button button--secondary" onClick={onClose}>Cerrar</button><button type="button" className="button button--primary" disabled={busy} onClick={() => void download()}>Descargar detalle CSV</button></div>
    </div></Modal>
}
