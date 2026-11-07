import { ChevronLeft, ChevronRight } from 'lucide-react'
import type { Page } from '../api/types'
import { numberFormatter } from '../utils/format'
import { useState, type SyntheticEvent } from 'react'

export function Pagination({ result, onPage, size, onSize, busy = false }: {
  result: Pick<Page<unknown>, 'page' | 'total' | 'totalPages'>; onPage: (page: number) => void
  size?: number; onSize?: (size: number) => void; busy?: boolean
}) {
  const [destination, setDestination] = useState('')
  function jump(event: SyntheticEvent) {
    event.preventDefault()
    const page = Number(destination)
    if (Number.isInteger(page) && page >= 1 && page <= result.totalPages) onPage(page - 1)
    setDestination('')
  }
  return (
    <div className="pagination">
      <span>{numberFormatter.format(result.total)} registros · Página {result.page + 1} de {Math.max(1, result.totalPages)}</span>
      <div>
        {size && onSize && <label className="page-size">Filas <select aria-label="Filas por página" value={size}
          disabled={busy} onChange={(event) => onSize(Number(event.target.value))}>
          {[...new Set([size, 10, 20, 25, 50, 100])].sort((a, b) => a - b).map((value) => <option key={value}>{value}</option>)}
        </select></label>}
        {result.totalPages > 1 && <div className="page-jump"><label>Ir a <input aria-label="Ir a la página"
          type="text" inputMode="numeric" value={destination} disabled={busy}
          onChange={(event) => setDestination(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') jump(event) }} /></label><button type="button" onClick={jump} disabled={busy}>Ir</button></div>}
        <button type="button" className="button button--secondary" disabled={busy || result.page === 0}
          onClick={() => onPage(result.page - 1)} aria-label="Página anterior"><ChevronLeft size={17} /></button>
        <button type="button" className="button button--secondary"
          disabled={busy || result.page + 1 >= result.totalPages} onClick={() => onPage(result.page + 1)}
          aria-label="Página siguiente"><ChevronRight size={17} /></button>
      </div>
    </div>
  )
}
