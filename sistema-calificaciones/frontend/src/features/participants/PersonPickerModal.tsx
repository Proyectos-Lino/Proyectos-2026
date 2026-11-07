import { useCallback } from 'react'
import { participantsApi } from './participantsApi'
import type { Person } from './types'
import { Modal } from '../../components/Modal'
import { Notice } from '../../components/Notice'
import { Pagination } from '../../components/Pagination'
import { useResource } from '../../hooks/useResource'
import { useTableState } from '../../hooks/useTableState'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'

export function PersonPickerModal({ org, onClose, onSelect }: { org: number; onClose: () => void; onSelect: (person: Person) => void }) {
  const table = useTableState({ query: '', page: 0, size: 10 }, 'personPicker')
  const { query, page, size } = table.values
  const search = useDebouncedValue(query)
  const load = useCallback(() => participantsApi.people(org, { query: search, page, size }), [org, search, page, size])
  const { data, error, loading } = useResource(load)
  return <Modal wide title="Usar una persona existente" description="Busca por nombre, documento o correo. Su código y grupo se asignan por separado para esta evaluación." onClose={onClose}>
    <div className="modal-form"><label className="field"><span>Buscar persona</span><input value={query} onChange={(event) => table.set('query', event.target.value)} /></label>
      {loading && <p role="status">Buscando personas…</p>}{error && <Notice tone="error">{error}</Notice>}
      {data && <><div className="table-scroll" aria-busy={loading} inert={loading}><table className="data-table"><thead><tr><th>Persona</th><th>Documento</th><th>Correo</th><th>Acción</th></tr></thead>
        <tbody>{data.items.map((person) => <tr key={person.id}><td>{person.names} {person.surnames}</td><td>{person.document || '—'}</td><td>{person.email || '—'}</td><td><button type="button" className="text-link" onClick={() => onSelect(person)} aria-label={`Usar ${person.names} ${person.surnames ?? ''}`}>Usar persona</button></td></tr>)}</tbody></table>
        {!data.items.length && <p className="empty-state">No se encontraron personas con datos registrados.</p>}</div>
        <Pagination result={data} onPage={(value) => table.set('page', value)} size={size} onSize={(value) => table.set('size', value)} busy={loading} /></>}
    </div></Modal>
}
