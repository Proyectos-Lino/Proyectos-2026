import { Notice } from '../../components/Notice'
import { useCallback } from 'react'
import { publicationApi } from '../../api/publication'
import { Modal } from '../../components/Modal'
import { useResource } from '../../hooks/useResource'
import { scoreFormatter } from '../../utils/format'

export function ResultVersionModal({ org, evaluation, sheet, version, onClose }: {
  org: number; evaluation: number; sheet: number; version: number; onClose: () => void
}) {
  const load = useCallback(() => publicationApi.versionDetail(org, evaluation, sheet, version),
    [org, evaluation, sheet, version])
  const { data, loading, error } = useResource(load)
  return <Modal title={`Cálculo de la versión ${data?.version.version ?? ''}`.trim()}
    description="Respuestas y configuración conservadas para este resultado." onClose={onClose}>
    <div className="modal-form">
      {loading && <p className="loading-inline">Cargando cálculo…</p>}
      {error && <Notice tone="error">{error}</Notice>}
      {data && <>
        <p><strong>{scoreFormatter.format(data.version.score)} puntos</strong> · Clave v{data.version.keyVersion}
          · Regla v{data.version.ruleVersion}{data.version.sectionVersion && ` · Secciones v${data.version.sectionVersion}`}</p>
        <p className="muted-copy">Motivo: {data.version.reason}</p>
        {data.version.officialScore != null && <p>
          <strong>Nota oficial · {scoreFormatter.format(data.version.officialScore)}</strong> · <code>{data.version.officialFormula}</code>
        </p>}
        {data.sections.length ? <div className="score-breakdown">{data.sections.map((section) => <div key={section.code}>
          <strong>{section.name}</strong><span>Preguntas {section.fromQuestion}–{section.toQuestion}</span>
          <span>{section.correct} correctas · {section.incorrect} incorrectas · {section.blank} blancas</span>
          <span>Valores: {scoreFormatter.format(section.correctPoints)} / {scoreFormatter.format(section.incorrectPoints)} / {scoreFormatter.format(section.blankPoints)}</span>
          <b>{scoreFormatter.format(section.points)} puntos</b>
        </div>)}</div> : <p className="muted-copy">Valores por respuesta: correcta {scoreFormatter.format(data.correctPoints)},
          incorrecta {scoreFormatter.format(data.incorrectPoints)}, blanca {scoreFormatter.format(data.blankPoints)}.</p>}
        <div className="table-scroll version-answer-table"><table className="data-table"><thead><tr>
          <th>Pregunta</th><th>Respuesta</th><th>Clave</th><th>Resultado</th>
        </tr></thead><tbody>{data.responses.map((answer, index) => <tr key={index}>
          <td>{index + 1}</td><td>{answer || '—'}</td><td>{data.key[index] || 'Libre'}</td>
          <td>{!data.key[index] || answer === data.key[index] ? 'Correcta' : !answer ? 'Blanca' : 'Incorrecta'}</td>
        </tr>)}</tbody></table></div>
      </>}
    </div>
  </Modal>
}
