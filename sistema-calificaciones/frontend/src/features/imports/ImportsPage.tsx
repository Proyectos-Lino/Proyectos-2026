import { BackLink } from '../../components/BackLink'
import { useTableState } from '../../hooks/useTableState'
import { Notice } from '../../components/Notice'
import { useCallback, useState, type FormEvent } from 'react'
import { ArrowLeft, FileSpreadsheet, Search, UploadCloud } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { evaluationsApi } from '../../api/evaluations'
import { importsApi, type DbfFiles } from '../../api/imports'
import type { DbfInspection, ImportSummary } from '../../api/types'
import { useAuth } from '../../auth/useAuth'
import { Pagination } from '../../components/Pagination'
import { PageHeader, StatusBadge } from '../../components/ui'
import { useResource } from '../../hooks/useResource'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { errorMessage } from '../../utils/format'
import { DbfMappingModal } from './DbfMappingModal'
import { DbfPreviewPanel, type PreviewSelection } from './DbfPreviewPanel'
import { useEvaluationRevision } from '../../hooks/useEvaluationRevision'

const fileFields = [
  { key: 'identifi', label: 'Identificaciones', description: 'Hoja, tema y código del participante' },
  { key: 'respuest', label: 'Respuestas', description: 'Marcas registradas por el lector' },
  { key: 'claves', label: 'Claves', description: 'Respuestas correctas por tema' },
] as const

export function ImportsPage() {
  const id = Number(useParams().evaluationId)
  const { organization } = useAuth()
  const org = organization!.id
  const revision = useEvaluationRevision(org, id)
  const canEdit = ['ADMINISTRADOR', 'OPERADOR'].includes(organization!.role)
  const loadEvaluation = useCallback(() => evaluationsApi.overview(org, id), [org, id])
  const { data: evaluation, error: evaluationError } = useResource(loadEvaluation, revision)
  const loadPreparation = useCallback(() => evaluationsApi.preparation(org, id), [org, id])
  const { data: preparation, error: preparationError } = useResource(loadPreparation, revision)
  const table = useTableState({ page: 0, status: '', query: '', size: 10 }, 'imports')
  const { page, status, query, size } = table.values
  const setSize = (value: number) => table.set('size', value)
  const setPage = (value: number) => table.set('page', value)
  const setStatus = (value: string) => table.set('status', value)
  const setQuery = (value: string) => table.set('query', value)
  const search = useDebouncedValue(query)
  const loadHistory = useCallback(() => importsApi.page(org, id, { query: search, status, page, size }),
    [org, id, search, status, page, size])
  const { data: history, error: historyError, loading, reload } = useResource(loadHistory)
  const [files, setFiles] = useState<Partial<DbfFiles>>({})
  const [fileRevision, setFileRevision] = useState(0)
  const [mode, setMode] = useState<'guided' | 'configured'>('guided')
  const [options, setOptions] = useState({ questions: 100, options: 5, correct: 1, incorrect: -0.25, blank: 0, passingScore: '' })
  const [working, setWorking] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [summary, setSummary] = useState<ImportSummary | null>(null)
  const [inspection, setInspection] = useState<DbfInspection | null>(null)
  const [selection, setSelection] = useState<PreviewSelection | null>(null)

  function updateOptions(update: Partial<typeof options>) {
    setOptions((current) => ({ ...current, ...update })); setSelection(null)
  }

  async function inspectColumns(event: FormEvent) {
    event.preventDefault()
    if (!files.identifi || !files.respuest || !files.claves) {
      setError('Selecciona los tres archivos DBF.'); return
    }
    setWorking(true); setError(null); setSummary(null); setSelection(null)
    try {
      setInspection(await importsApi.inspect(org, id, files as DbfFiles))
    } catch (cause) { setError(errorMessage(cause)) }
    finally { setWorking(false) }
  }

  return <div className="page-stack">
    <BackLink to={`/evaluaciones/${id}`} className="back-link"><ArrowLeft size={17} /> Volver a la evaluación</BackLink>
    <PageHeader eyebrow={evaluation?.code ?? 'EVALUACIÓN'} title="Carga de archivos DBF"
      description={evaluation?.name ?? 'Prepara los archivos del lector óptico y consulta las importaciones anteriores.'}
      action={evaluation ? <StatusBadge status={evaluation.status} /> : undefined} />
    {evaluationError && <Notice tone="error">{evaluationError}</Notice>}
    {preparationError && <Notice tone="error">{preparationError}</Notice>}
    {canEdit && evaluation && preparation && (!preparation.editable || !['BORRADOR', 'CONFIGURADA', 'EN_PROCESO'].includes(evaluation.status)) &&
      <section className="surface-card form-section"><h2>Carga de archivos bloqueada</h2><p>La evaluación está cerrada, aprobada, publicada o archivada. Puedes consultar el historial de importaciones.</p>
        <Link className="text-link" to={`/evaluaciones/${id}/publicacion`}>Consultar el estado de publicación</Link></section>}
    {canEdit && preparation?.editable && evaluation && ['BORRADOR', 'CONFIGURADA', 'EN_PROCESO'].includes(evaluation.status) && <form className="surface-card form-section" onSubmit={(event) => void inspectColumns(event)}>
      <div className="form-section__heading"><span className="step-number">01</span>
        <div><h2>Seleccionar archivos</h2><p>El sistema cruza las hojas por código y tema.</p></div></div>
      <div className="upload-grid">
        {fileFields.map(({ key, label, description }) => <label className="upload-field" key={key}>
          <span className="upload-field__icon"><FileSpreadsheet size={23} aria-hidden="true" /></span>
          <strong>{label}</strong><span>{files[key]?.name ?? description}</span>
          <span className="upload-field__action"><UploadCloud size={16} /> Seleccionar DBF</span>
          <input key={`${key}-${fileRevision}`} type="file" accept=".dbf,.DBF" required={!files[key]}
            onChange={(event) => { setFiles((current) => ({ ...current, [key]: event.target.files?.[0] }));
              setSelection(null); setInspection(null); setSummary(null) }} />
        </label>)}
      </div>
      <div className="import-options">
        <fieldset className="choice-group">
          <legend>Modo de carga</legend>
          <label><input type="radio" name="mode" checked={mode === 'guided'} onChange={() => { setMode('guided'); setSelection(null) }} />
            Preparar temas, regla e inscripciones que falten</label>
          <label><input type="radio" name="mode" checked={mode === 'configured'} onChange={() => { setMode('configured'); setSelection(null) }} />
            Solo importar (evaluación ya configurada y en proceso)</label>
        </fieldset>
        {mode === 'guided' && <>
          <p className="muted-copy">Los participantes nuevos se registrarán por su código. Los valores de abajo se usan para temas y regla aún no configurados. Si necesitas secciones o diferentes cantidades de preguntas por tema, <Link className="text-link" to={`/evaluaciones/${id}/configuracion`}>configúralos antes de cargar</Link>.</p>
          <div className="import-values">
            <label className="field"><span>Preguntas por tema</span><input type="number" min={1}
              value={options.questions} onChange={(event) => updateOptions({ questions: Number(event.target.value) })} required /></label>
            <label className="field"><span>Opciones por pregunta</span><input type="number" min={2} max={26}
              value={options.options} onChange={(event) => updateOptions({ options: Number(event.target.value) })} required /></label>
            <label className="field"><span>Correcta</span><input type="number" min={0} step="0.0001"
              value={options.correct} onChange={(event) => updateOptions({ correct: Number(event.target.value) })} required /></label>
            <label className="field"><span>Incorrecta</span><input type="number" step="0.0001"
              value={options.incorrect} onChange={(event) => updateOptions({ incorrect: Number(event.target.value) })} required /></label>
            <label className="field"><span>En blanco</span><input type="number" step="0.0001"
              value={options.blank} onChange={(event) => updateOptions({ blank: Number(event.target.value) })} required /></label>
            <label className="field"><span>Nota mínima (opcional)</span><input type="number" step="0.0001"
              value={options.passingScore} onChange={(event) => updateOptions({ passingScore: event.target.value })} /></label>
          </div>
        </>}
      </div>
      {error && <Notice tone="error">{error}</Notice>}
      {summary && <Notice tone="success">
        Lote {summary.batchId}: {summary.valid} hojas válidas, {summary.errors} con incidencias de {summary.total} leídas.
        <Link to={`/evaluaciones/${id}/examenes`} className="text-link"> Ver exámenes</Link>
      </Notice>}
      <button type="submit" className="button button--primary" disabled={working}>
        {working ? 'Leyendo columnas…' : selection ? 'Cambiar mapeo y analizar otra vez' : 'Revisar columnas y analizar'}
      </button>
    </form>}
    {inspection && <DbfMappingModal inspection={inspection} onClose={() => setInspection(null)}
      onApply={(mapping) => { setSelection({ files: files as DbfFiles, mapping,
        options: { ...options, mode: mode === 'guided' ? 'GUIADA' : 'CONFIGURADA',
          passingScore: options.passingScore === '' ? null : Number(options.passingScore) } }); setInspection(null) }} />}
    {selection && <DbfPreviewPanel org={org} evaluationId={id} selection={selection}
      onConfirmed={(result) => { setSummary(result); setSelection(null); setFiles({});
        setFileRevision((current) => current + 1); reload() }} />}
    <section className="surface-card">
      <div className="list-toolbar"><div><p className="section-kicker">HISTORIAL</p><h2>Importaciones</h2>
        <p>{history?.total ?? 0} lotes encontrados</p></div>
        <label className="search-field"><Search size={17} /><span className="sr-only">Buscar archivo</span>
          <input value={query} onChange={(event) => { setQuery(event.target.value); setPage(0) }}
            placeholder="Buscar archivo" /></label>
        <label className="select-field"><span className="sr-only">Filtrar importaciones</span>
          <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0) }}>
            <option value="">Todos los estados</option><option value="COMPLETADO">Completado</option>
            <option value="COMPLETADO_CON_ERRORES">Con incidencias</option>
            <option value="PROCESANDO">Procesando</option>
          </select></label>
      </div>
      {historyError && <Notice tone="error">{historyError}</Notice>}
      {loading && <p className="loading-inline" role="status">Consultando importaciones…</p>}
      {history && <><div className="table-scroll" aria-busy={loading} inert={loading}><table className="data-table">
        <thead><tr><th>Archivo</th><th>Estado</th><th>Total</th><th>Válidas</th><th>Incidencias</th></tr></thead>
        <tbody>{history.items.map((batch) => <tr key={batch.id}>
          <td>{batch.filename}</td><td><StatusBadge status={batch.status} /></td>
          <td>{batch.total}</td><td>{batch.valid}</td><td>{batch.errors}</td>
        </tr>)}</tbody>
      </table>{history.items.length === 0 && <div className="empty-state">Todavía no hay importaciones.</div>}</div>
        <Pagination result={history} onPage={setPage} size={size} onSize={setSize} busy={loading} /></>}
    </section>
  </div>
}
