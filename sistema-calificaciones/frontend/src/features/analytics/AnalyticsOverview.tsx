import { Notice } from '../../components/Notice'
import { useCallback } from 'react'
import { analyticsApi } from '../../api/analytics'
import { useResource } from '../../hooks/useResource'
import { numberFormatter, scoreFormatter } from '../../utils/format'
import { ApprovalChart, ScoreDistribution } from './ResultCharts'
import type { AnalyticsScope } from './types'
import { resultLanguage } from '../../utils/evaluation'

export function AnalyticsOverview({ org, evaluation, scope, evaluationType }: { org: number; evaluation: number; scope: AnalyticsScope; evaluationType?: string }) {
  const language = resultLanguage(evaluationType)
  const load = useCallback(() => analyticsApi.dashboard(org, evaluation, scope), [org, evaluation, scope])
  const { data, loading, error, reload } = useResource(load)
  if (error) return <Notice tone="error">{error} <button type="button" onClick={reload}>Reintentar</button></Notice>
  if (loading || !data) return <p className="loading-inline">Calculando indicadores…</p>
  const s = data.summary
  const decided = s.passed + s.failed
  const score = (value: number | null) => value === null ? '—' : scoreFormatter.format(value)
  return <>
    <div className="analytics-metrics">
      <article><span>Resultados válidos</span><strong>{numberFormatter.format(s.scored)}</strong><small>de {numberFormatter.format(s.sheets)} exámenes</small></article>
      <article><span>Puntaje promedio</span><strong>{score(s.average)}</strong><small>Mediana: {score(s.median)}</small></article>
      <article><span>{language.heading}</span><strong>{decided ? `${Math.round(s.passed / decided * 100)}%` : '—'}</strong><small>sobre {numberFormatter.format(decided)} resultados con decisión</small></article>
      <article><span>Rango de puntajes</span><strong>{score(s.minimum)} <em>–</em> {score(s.maximum)}</strong><small>Desviación estándar: {score(s.deviation)}</small></article>
    </div>
    <div className="analytics-status-strip">
      <span>Inscripciones <strong>{numberFormatter.format(s.registrations)}</strong></span>
      <span>Sin examen <strong>{numberFormatter.format(s.registrations - s.sheets)}</strong></span>
      <span>Observados <strong>{numberFormatter.format(s.observed)}</strong></span>
      <span>Anulados <strong>{numberFormatter.format(s.annulled)}</strong></span>
      <span>Recibidos <strong>{numberFormatter.format(s.received)}</strong></span>
    </div>
    {s.valid > s.scored && <Notice tone="success">Hay {s.valid - s.scored} exámenes válidos sin resultado calculado; aún no participan en las estadísticas de puntaje.</Notice>}
    <div className="analytics-charts"><ScoreDistribution bins={data.distribution} /><ApprovalChart summary={s} evaluationType={evaluationType} /></div>
    <p className="analytics-timestamp">Consulta generada el {new Date(data.generatedAt).toLocaleString('es-PE')}.</p>
  </>
}
