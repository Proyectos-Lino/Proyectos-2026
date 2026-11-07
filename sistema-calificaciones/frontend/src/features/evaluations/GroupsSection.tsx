import { useNoticeMessage } from '../../hooks/useNoticeMessage'
import { Notice } from '../../components/Notice'
import { useCallback, useState } from 'react'
import { Plus, Upload } from 'lucide-react'
import { evaluationsApi } from '../../api/evaluations'
import type { Evaluation, Group } from '../../api/types'
import { CsvImportModal } from '../../components/CsvImportModal'
import { useResource } from '../../hooks/useResource'
import { GroupModal } from './GroupModal'
import { groupLabel } from '../../utils/group'

export function GroupsSection({ org, evaluationId, canEdit, groupType }: {
  org: number; evaluationId: number; canEdit: boolean; groupType: Evaluation['groupType']
}) {
  const load = useCallback(() => evaluationsApi.groups(org, evaluationId), [org, evaluationId])
  const { data: groups, loading, error, reload } = useResource(load)
  const [selected, setSelected] = useState<Group | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [importOpen, setImportOpen] = useState(false)
  const [message, setMessage] = useNoticeMessage()
  const label = groupLabel(groupType)

  return <section className="surface-card form-section">
    <div className="form-section__heading"><span className="step-number">03</span>
      <div><h2>{label}s y vacantes</h2><p>Organiza participantes y define las vacantes de cada grupo.</p></div></div>
    {canEdit && <div className="list-toolbar__controls">
      <button type="button" className="button button--secondary" onClick={() => setImportOpen(true)}><Upload size={16} /> Importar CSV</button>
      <button type="button" className="button button--secondary" onClick={() => { setSelected(null); setModalOpen(true) }}><Plus size={16} /> Nueva {label.toLowerCase()}</button>
    </div>}
    {loading && !groups && <p className="loading-inline">Cargando {label.toLowerCase()}s…</p>}
    {error && <Notice tone="error">{error}</Notice>}
    <div className="simple-list">
      {groups?.map((group) => <div className="group-card" key={group.id}>
        <div><strong>{group.name}</strong><span>{group.code} · Vacantes: {group.capacity ?? 'sin definir'}</span></div>
        {canEdit && <button type="button" className="button button--secondary" onClick={() => { setSelected(group); setModalOpen(true) }}>Editar</button>}
      </div>)}
      {groups?.length === 0 && <p className="muted-copy">Aún no hay {label.toLowerCase()}s.</p>}
    </div>
    {message && <Notice tone="success">{message}</Notice>}
    {modalOpen && <GroupModal key={selected?.id ?? 'new'} org={org} evaluationId={evaluationId} group={selected} label={label}
      onClose={() => setModalOpen(false)} onSaved={() => { setModalOpen(false); setMessage(`${label} guardada.`); reload() }} />}
    {importOpen && <CsvImportModal title={`Importar ${label.toLowerCase()}s`} columns={['codigo', 'nombre', 'vacantes']}
      example={['G-001', 'Principal', '30']} onImport={(file) => evaluationsApi.importGroups(org, evaluationId, file)}
      onClose={() => setImportOpen(false)} onComplete={(summary) => {
        setImportOpen(false); setMessage(`${summary.created} registros importados · ${summary.skipped} ya existentes.`); reload()
      }} />}
  </section>
}
