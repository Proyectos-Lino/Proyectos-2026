import { useCallback } from 'react'
import { evaluationsApi } from '../../api/evaluations'
import type { EvaluationOverview } from '../../api/types'
import { Modal } from '../../components/Modal'
import { Notice } from '../../components/Notice'
import { Pagination } from '../../components/Pagination'
import { StatusBadge } from '../../components/ui'
import { useResource } from '../../hooks/useResource'
import { useTableState } from '../../hooks/useTableState'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'

export function EvaluationPickerModal({ org, onClose, onSelect }: { org: number; onClose: () => void; onSelect: (evaluation: EvaluationOverview) => void }) {
  const table = useTableState({ query: '', status: '', page: 0, size: 10 }, 'participantEvaluations')
  const { query, status, page, size } = table.values
  const search = useDebouncedValue(query)
  const load = useCallback(() => evaluationsApi.page(org, { query: search, status, page, size }), [org, search, status, page, size])
  const { data, error, loading } = useResource(load)
  return <Modal wide title="Elegir evaluación" description="Los participantes, códigos y grupos se administran dentro del proceso seleccionado." onClose={onClose}>
    <div className="modal-form"><label className="field"><span>Buscar evaluación</span><input value={query} placeholder="Nombre o código" onChange={(event) => table.set('query', event.target.value)} /></label>
      {loading && <p role="status">Buscando evaluaciones…</p>}{error && <Notice tone="error">{error}</Notice>}
      {data && <><div className="table-scroll" aria-busy={loading} inert={loading}><table className="data-table"><thead><tr><th>Evaluación</th><th>Inscritos</th><th>Estado</th><th>Acción</th></tr></thead>
        <tbody>{data.items.map((evaluation) => <tr key={evaluation.id}><td><strong>{evaluation.name}</strong><span className="table-subtitle">{evaluation.code}</span></td><td>{evaluation.participants}</td><td><StatusBadge status={evaluation.status} /></td>
          <td><button type="button" className="text-link" onClick={() => onSelect(evaluation)} aria-label={`Elegir ${evaluation.name}`}>Elegir</button></td></tr>)}</tbody></table>
        {!data.items.length && <p className="empty-state">No hay evaluaciones con esos criterios.</p>}</div>
        <Pagination result={data} onPage={(value) => table.set('page', value)} size={size} onSize={(value) => table.set('size', value)} busy={loading} /></>}
    </div></Modal>
}
