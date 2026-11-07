import { useId } from 'react'
import { numberFormatter, scoreFormatter } from '../../utils/format'
import type { AnalyticsSummary, ScoreBin } from './types'
import { resultLanguage } from '../../utils/evaluation'

export function ScoreDistribution({ bins }: { bins: ScoreBin[] }) {
  const titleId = useId()
  const highest = Math.max(1, ...bins.map((bin) => bin.count))
  const width = bins.length ? 600 / bins.length : 600
  return <section className="surface-card analytics-chart">
    <p className="section-kicker">PUNTAJES</p><h2>Distribución de resultados</h2>
    <p className="muted-copy">Cantidad de exámenes válidos en cada intervalo.</p>
    {bins.length === 0 ? <div className="empty-state">Aún no hay resultados válidos en este filtro.</div> : <>
      <div className="analytics-histogram-scroll"><svg className="analytics-histogram" viewBox="0 0 660 240" role="img" aria-labelledby={titleId}>
        <title id={titleId}>{bins.map((bin) => `${scoreFormatter.format(bin.lower)} a ${scoreFormatter.format(bin.upper)}: ${bin.count} exámenes`).join('. ')}</title>
        {(highest < 2 ? [0, 1] : [0, 0.5, 1]).map((level) => <g key={level}>
          <line x1="40" x2="640" y1={190 - level * 145} y2={190 - level * 145} stroke="#e8efea" />
          <text x="32" y={194 - level * 145} textAnchor="end" className="analytics-axis">{numberFormatter.format(Math.round(highest * level))}</text>
        </g>)}
        {bins.map((bin, index) => {
          const height = bin.count / highest * 145
          const x = 40 + index * width
          return <g key={bin.index}>
            <rect x={x + width * 0.14} y={190 - height} width={width * 0.72} height={height}
              rx="5" fill={index % 2 === 0 ? '#177d67' : '#5c9f85'}>
              <title>{`${scoreFormatter.format(bin.lower)} – ${scoreFormatter.format(bin.upper)}: ${bin.count}`}</title></rect>
            <text x={x + width / 2} y={181 - height} textAnchor="middle" className="analytics-bar-count">{bin.count}</text>
            <text x={x + width / 2} y="213" textAnchor="middle" className="analytics-axis">{scoreFormatter.format(bin.lower)}</text>
          </g>
        })}
      </svg></div>
      <p className="analytics-chart-note">Intervalos desde {scoreFormatter.format(bins[0].lower)} hasta {scoreFormatter.format(bins[bins.length - 1].upper)}.
        Cada intervalo incluye su límite inferior; el último también incluye el superior.</p>
    </>}
  </section>
}

export function ApprovalChart({ summary, evaluationType }: { summary: AnalyticsSummary; evaluationType?: string }) {
  const language = resultLanguage(evaluationType)
  const segments = [
    { name: language.people, count: summary.passed, color: '#177d67' },
    { name: language.negativePeople, count: summary.failed, color: '#c98857' },
    { name: 'Sin decisión', count: summary.undecided, color: '#91a3ab' },
  ]
  return <section className="surface-card analytics-chart">
    <p className="section-kicker">{language.heading.toUpperCase()}</p><h2>Decisiones de resultado</h2>
    <p className="muted-copy">Base: {numberFormatter.format(summary.scored)} resultados válidos.</p>
    <div className="analytics-approval-bar" role="img" aria-label={segments.map((part) => `${part.name}: ${part.count}`).join(', ')}>
      {segments.map((part) => part.count > 0 && <span key={part.name}
        style={{ width: `${part.count / summary.scored * 100}%`, background: part.color }} />)}
    </div>
    <div className="analytics-legend">{segments.map((part) => <div key={part.name}>
      <span><i style={{ background: part.color }} />{part.name}</span><strong>{numberFormatter.format(part.count)}</strong>
    </div>)}</div>
    <p className="analytics-chart-note">Sin decisión indica que el resultado todavía no tiene una decisión o que esta no aplica.</p>
  </section>
}
