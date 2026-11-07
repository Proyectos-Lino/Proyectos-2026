import { Notice } from '../components/Notice'
import { useState, type FormEvent } from 'react'
import { ArrowRight, ClipboardCheck, Download } from 'lucide-react'
import { Link } from 'react-router'
import { publicationApi } from '../api/publication'
import type { PublicResult } from '../api/types'
import { ApiError } from '../api/client'
import { downloadBlob, errorMessage, scoreFormatter } from '../utils/format'
import { resultLanguage } from '../utils/evaluation'

export function PublicResultPage() {
  const [code, setCode] = useState('')
  const [result, setResult] = useState<PublicResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [working, setWorking] = useState(false)
  const [activeCode, setActiveCode] = useState('')
  const [downloading, setDownloading] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault(); setWorking(true); setError(null); setResult(null)
    try { setResult(await publicationApi.lookup(code.trim())); setActiveCode(code.trim()) }
    catch (cause) {
      setError(cause instanceof ApiError && cause.status === 404
        ? 'No hay un resultado disponible para ese código. Comprueba el código y el periodo de publicación.'
        : errorMessage(cause))
    } finally { setWorking(false) }
  }

  async function downloadPdf() {
    setDownloading(true); setError(null)
    try { downloadBlob(await publicationApi.individualPdf(activeCode), 'constancia-resultado.pdf') }
    catch (cause) { setError(errorMessage(cause)) }
    finally { setDownloading(false) }
  }

  return <main className="public-result-layout">
    <div className="public-result-card">
      <header className="public-result-brand"><span className="brand__mark" aria-hidden="true"><span /><span /><span /></span>
        <strong>califica</strong><Link to="/login">Acceso del personal</Link></header>
      <div className="public-result-intro"><p className="eyebrow">CONSULTA INDIVIDUAL</p>
        <h1>Consulta tu resultado</h1><p>Ingresa el código privado que te entregó la organización.</p></div>
      <form className="public-result-form" onSubmit={(event) => void submit(event)}>
        <label className="field"><span>Código de acceso</span>
          <input value={code} onChange={(event) => setCode(event.target.value)}
            autoComplete="off" spellCheck={false} required minLength={43} maxLength={43}
            placeholder="Escribe tu código de acceso" /></label>
        <button type="submit" className="button button--primary" disabled={working}>
          {working ? 'Consultando…' : 'Ver resultado'} <ArrowRight size={17} /></button>
      </form>
      {error && <Notice tone="error">{error}</Notice>}
      {result && <section className="public-result-detail" aria-live="polite">
        <div className="public-result-detail__heading"><ClipboardCheck size={24} aria-hidden="true" />
          <div><p className="section-kicker">RESULTADO PUBLICADO</p><h2>{result.evaluation}</h2></div></div>
        <p><strong>{result.participantName}</strong> · Código {result.participantCode} · Tema {result.formCode}</p>
        <div className="public-result-metrics">
          {result.score !== null && <div><span>Puntaje</span><strong>{scoreFormatter.format(result.score)}</strong></div>}
          {result.position !== null && <div><span>Puesto</span><strong>{result.position}</strong></div>}
          {result.passed !== null && <div><span>{resultLanguage(result.evaluationType).heading}</span><strong>{result.passed ? resultLanguage(result.evaluationType).positive : resultLanguage(result.evaluationType).negative}</strong></div>}
          {result.correct !== null && <div><span>Correctas</span><strong>{result.correct}</strong></div>}
          {result.incorrect !== null && <div><span>Incorrectas</span><strong>{result.incorrect}</strong></div>}
          {result.blank !== null && <div><span>Blancas</span><strong>{result.blank}</strong></div>}
        </div>
        <button type="button" className="button button--secondary public-result-print"
          disabled={downloading} onClick={() => void downloadPdf()}><Download size={16} /> {downloading ? 'Preparando PDF…' : 'Descargar constancia PDF'}</button>
      </section>}
    </div>
  </main>
}
