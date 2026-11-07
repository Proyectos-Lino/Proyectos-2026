import { Notice } from '../../components/Notice'
import { useCallback, useState } from 'react'
import { examsApi } from '../../api/exams'
import { publicationApi } from '../../api/publication'
import { ResultVersionModal } from './ResultVersionModal'
import { Pagination } from '../../components/Pagination'
import { useResource } from '../../hooks/useResource'
import { scoreFormatter } from '../../utils/format'
import { approvalLabels, scopeLabels, tieLabels } from '../../utils/scoring'
import { resultLanguage } from '../../utils/evaluation'
import { useTableState } from '../../hooks/useTableState'

const timestamp = new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' })

export function ResultExplanationSection({ org, evaluationId, sheetId, evaluationType }: {
  org: number; evaluationId: number; sheetId: number; evaluationType?: string
}) {
  const language = resultLanguage(evaluationType)
  const decisionsTable = useTableState({ page: 0, size: 10 }, `decisions${sheetId}`)
  const versionsTable = useTableState({ page: 0, size: 10 }, `versions${sheetId}`)
  const { page, size } = decisionsTable.values
  const { page: versionPage, size: versionSize } = versionsTable.values
  const [selectedVersion, setSelectedVersion] = useState<number | null>(null)
  const load = useCallback(() => examsApi.explanation(org, evaluationId, sheetId), [org, evaluationId, sheetId])
  const loadDecisions = useCallback(() => examsApi.decisions(org, evaluationId, sheetId, page, size),
    [org, evaluationId, sheetId, page, size])
  const { data: explanation, loading, error } = useResource(load)
  const { data: decisions, error: decisionError, loading: decisionsLoading } = useResource(loadDecisions)
  const loadVersions = useCallback(() => publicationApi.versions(org, evaluationId, sheetId, versionPage, versionSize),
    [org, evaluationId, sheetId, versionPage, versionSize])
  const { data: versions, error: versionError, loading: versionsLoading } = useResource(loadVersions)

  return <section className="surface-card form-section">
    <p className="section-kicker">CÁLCULO Y TRAZABILIDAD</p><h2>Cómo se obtuvo este resultado</h2>
    {loading && <p className="loading-inline">Cargando explicación…</p>}
    {error && <Notice tone="error">{error}</Notice>}
    {explanation && <>
      <p className="muted-copy">Tema {explanation.formCode} · {explanation.questions} preguntas · Clave v{explanation.keyVersion} · Regla v{explanation.ruleVersion}
        {explanation.sectionVersion ? ` · Secciones v${explanation.sectionVersion}` : ' · Puntaje general'}</p>
      {explanation.sections.length ? <div className="score-breakdown">
        {explanation.sections.map((section) => <div key={section.code}>
          <strong>{section.name}</strong><span>Preguntas {section.fromQuestion}–{section.toQuestion}</span>
          <span>{section.correct} correctas · {section.incorrect} incorrectas · {section.blank} blancas</span>
          <span>Valor: {scoreFormatter.format(section.correctPoints)} / {scoreFormatter.format(section.incorrectPoints)} / {scoreFormatter.format(section.blankPoints)}</span>
          <b>{scoreFormatter.format(section.points)} puntos</b>
        </div>)}
      </div> : <p className="muted-copy">Puntajes: correcta {scoreFormatter.format(explanation.correctPoints)}, incorrecta {scoreFormatter.format(explanation.incorrectPoints)}, blanca {scoreFormatter.format(explanation.blankPoints)}.</p>}
      <div className="info-row"><strong>Puntaje final: {scoreFormatter.format(explanation.score)}</strong>
        <span>Nota mínima de la regla: {explanation.minimumScore === null ? 'sin definir' : scoreFormatter.format(explanation.minimumScore)}</span></div>
    </>}
    <h3 className="result-history__title">Historial de puestos y {language.heading.toLowerCase()}</h3>
    <p className="muted-copy">Se registra una nueva decisión cuando cambian el puesto, el resultado, las vacantes o los criterios aplicados.</p>
    {decisionError && <Notice tone="error">{decisionError}</Notice>}
    <div className="result-history">
      {decisions?.items.map((decision) => <article key={decision.id}>
        <div><strong>{decision.position === null ? 'Sin puesto' : `Puesto ${decision.position}`}
          {decision.passed === true ? ` · ${language.positive}` : decision.passed === false ? ` · ${language.negative}` : ' · Sin decisión'}</strong>
          <span>{timestamp.format(new Date(decision.recordedAt))}</span></div>
        <p>Política v{decision.policyVersion}: {approvalLabels[decision.approvalMode]} · {scopeLabels[decision.scope]} · {tieLabels[decision.tieBreak]}.</p>
        <p>Vacantes aplicadas: {decision.capacity ?? 'sin definir'} · Nota mínima aplicada: {decision.minimumScore === null ? 'sin definir' : scoreFormatter.format(decision.minimumScore)}.</p>
      </article>)}
      {decisions?.items.length === 0 && <p className="muted-copy">Aún no hay decisiones de ranking.</p>}
    </div>
    {decisionsLoading && <p role="status">Actualizando decisiones…</p>}
    {decisions && <Pagination result={decisions} onPage={(value) => decisionsTable.set('page', value)} size={size} onSize={(value) => decisionsTable.set('size', value)} busy={decisionsLoading} />}
    <h3 className="result-history__title">Versiones del puntaje</h3>
    <p className="muted-copy">Cada recálculo conserva la clave, regla, secciones, conteos y motivo utilizados.</p>
    {versionError && <Notice tone="error">{versionError}</Notice>}
    <div className="result-history">{versions?.items.map((version) => <article key={version.id}>
      <div><strong>Versión {version.version} · {scoreFormatter.format(version.score)} puntos</strong>
        <span>{timestamp.format(new Date(version.calculatedAt))}</span></div>
      <p>Clave v{version.keyVersion} · Regla v{version.ruleVersion}
        {version.sectionVersion ? ` · Secciones v${version.sectionVersion}` : ''}</p>
      <p>{version.correct} correctas · {version.incorrect} incorrectas · {version.blank} blancas</p>
      <p>Motivo: {version.reason}</p>
      <button type="button" className="text-link" onClick={() => setSelectedVersion(version.id)}>Ver cálculo</button>
    </article>)}{versions?.items.length === 0 && <p className="muted-copy">No hay versiones registradas.</p>}</div>
    {versionsLoading && <p role="status">Actualizando versiones…</p>}
    {versions && <Pagination result={versions} onPage={(value) => versionsTable.set('page', value)} size={versionSize} onSize={(value) => versionsTable.set('size', value)} busy={versionsLoading} />}
    {selectedVersion !== null && <ResultVersionModal org={org} evaluation={evaluationId} sheet={sheetId}
      version={selectedVersion} onClose={() => setSelectedVersion(null)} />}
  </section>
}
