import type { Page } from '../../api/types'
import { Pagination } from '../../components/Pagination'
import type { EnrollmentImportCounts, EnrollmentImportRow } from './types'
import { importActions } from './importLabels'

export function ImportCounts({ counts }: { counts: EnrollmentImportCounts }) {
  return <><div className="participant-import-counts">{[['Inscripciones nuevas', counts.newEnrollments], ['Datos completados', counts.completed], ['Grupos actualizados', counts.groupChanges], ['Filas por corregir', counts.errors]].map(([label, value]) =>
    <div key={label}><span>{label}</span><strong>{value}</strong></div>)}</div>
    <p className="muted-copy">{counts.total} filas · {counts.newPeople} personas nuevas · {counts.reused} personas reutilizadas · {counts.preserved} registros personales conservados.</p></>
}
export function EnrollmentImportRows({ result, loading, pageSize, onSize, onPage }: {
  result: Page<EnrollmentImportRow>; loading: boolean; pageSize: number; onSize: (size: number) => void; onPage: (page: number) => void
}) {
  return <><div className="table-scroll participant-import-scroll" aria-busy={loading} inert={loading}><table className="data-table participant-import-table"><thead><tr><th>Fila</th><th>Código</th><th>Resultado</th><th>Grupo anterior → asignado</th><th>Detalle</th></tr></thead>
    <tbody>{result.items.map((row) => <tr key={row.row}><td>{row.row}</td><td>{row.code || '—'}</td><td><span className={row.action === 'ERROR' ? 'participant-import-issue' : ''}>{importActions[row.action] ?? row.action}</span></td>
      <td>{row.previousGroup || 'Sin grupo'} → {row.group || 'Sin grupo'}</td><td>{row.detail}</td></tr>)}</tbody></table>{!result.items.length && <p className="empty-state">No hay filas con esos criterios.</p>}</div>
    <Pagination result={result} onPage={onPage} size={pageSize} onSize={onSize} busy={loading} /></>
}
