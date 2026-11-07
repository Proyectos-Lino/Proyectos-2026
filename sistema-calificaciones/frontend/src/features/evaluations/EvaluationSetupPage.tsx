import { BackLink } from '../../components/BackLink'
import { Notice } from '../../components/Notice'
import { useCallback, useState, type FormEvent } from 'react'
import { ArrowLeft } from 'lucide-react'
import { useParams } from 'react-router'
import { evaluationsApi } from '../../api/evaluations'
import { publicationApi } from '../../api/publication'
import { useAuth } from '../../auth/useAuth'
import { PageHeader, StatusBadge } from '../../components/ui'
import { Modal } from '../../components/Modal'
import type { Evaluation } from '../../api/types'
import { errorMessage } from '../../utils/format'
import { groupLabel, groupLabels } from '../../utils/group'
import { useResource } from '../../hooks/useResource'
import { FormsSection } from './FormsSection'
import { GroupsSection } from './GroupsSection'
import { RuleSection } from './RuleSection'
import { RankingPolicySection } from './RankingPolicySection'
import { RegistrationsSection } from './RegistrationsSection'

export function EvaluationSetupPage() {
  const { evaluationId } = useParams()
  const id = Number(evaluationId)
  const { organization } = useAuth()
  const org = organization!.id
  const hasEditRole = ['ADMINISTRADOR', 'OPERADOR'].includes(organization!.role)
  const load = useCallback(() => evaluationsApi.overview(org, id), [org, id])
  const { data: evaluation, loading, error, reload } = useResource(load)
  const loadPolicy = useCallback(() => publicationApi.policy(org, id), [org, id])
  const { data: publication } = useResource(loadPolicy)
  const [groupType, setGroupType] = useState<Evaluation['groupType']>('GRUPO')
  const [typeOpen, setTypeOpen] = useState(false)
  const [working, setWorking] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  async function saveGroupType(event: FormEvent) {
    event.preventDefault()
    if (!evaluation) return
    setWorking(true); setSaveError(null)
    try {
      await evaluationsApi.update(org, id, { name: evaluation.name, description: evaluation.description ?? '',
        date: evaluation.date, groupType })
      setTypeOpen(false); reload()
    } catch (cause) { setSaveError(errorMessage(cause)) }
    finally { setWorking(false) }
  }

  if (loading && !evaluation) return <p className="loading-inline">Cargando configuración…</p>
  if (error && !evaluation) return <Notice tone="error">{error} <button type="button" onClick={reload}>Reintentar</button></Notice>
  if (!evaluation) return null
  const canEdit = hasEditRole && publication !== null && !publication.approvedAt
    && !['PUBLICADA', 'ARCHIVADA'].includes(evaluation.status)

  return (
    <div className="page-stack">
      <BackLink to={`/evaluaciones/${id}`} className="back-link"><ArrowLeft size={17} /> Volver a la evaluación</BackLink>
      <PageHeader eyebrow={evaluation.code} title="Configuración"
        description="Define temas, secciones, puntajes, ranking y vacantes para este proceso."
        action={<StatusBadge status={evaluation.status} />} />
      <section className="surface-card form-section">
        <div className="form-section__heading"><div><h2>Agrupación de participantes</h2>
          <p>En esta evaluación los grupos representan: <strong>{groupLabel(evaluation.groupType).toLowerCase()}s</strong>.</p></div></div>
        {canEdit && ['BORRADOR', 'CONFIGURADA'].includes(evaluation.status) && <button type="button" className="button button--secondary"
          onClick={() => { setGroupType(evaluation.groupType); setSaveError(null); setTypeOpen(true) }}>Cambiar tipo</button>}
      </section>
      <div className="configuration-grid">
        <FormsSection org={org} evaluationId={id} canEdit={canEdit} />
        <RuleSection org={org} evaluationId={id} canEdit={canEdit} />
        <RankingPolicySection org={org} evaluationId={id} canEdit={canEdit} evaluationType={evaluation.type} />
        <GroupsSection org={org} evaluationId={id} canEdit={canEdit} groupType={evaluation.groupType} />
      </div>
      <RegistrationsSection org={org} evaluationId={id} />
      {typeOpen && <Modal title="Tipo de agrupación" description="Elige cómo se denominarán los grupos de esta evaluación."
        onClose={() => setTypeOpen(false)} busy={working}>
        <form className="modal-form" onSubmit={(event) => void saveGroupType(event)}>
          <label className="field"><span>Los grupos representan</span><select value={groupType}
            onChange={(event) => setGroupType(event.target.value as Evaluation['groupType'])}>
            {Object.entries(groupLabels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
          </select></label>
          {saveError && <Notice tone="error">{saveError}</Notice>}
          <div className="modal-form__actions"><button type="button" className="button button--secondary" onClick={() => setTypeOpen(false)}>Cancelar</button>
            <button className="button button--primary" disabled={working}>{working ? 'Guardando…' : 'Guardar'}</button></div>
        </form>
      </Modal>}
    </div>
  )
}
