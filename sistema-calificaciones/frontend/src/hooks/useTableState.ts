import { useLayoutEffect, useRef, type SetStateAction } from 'react'
import { useSearchParams } from 'react-router'

type Value = string | number | null

/** Mantiene cada listado en la URL y permite combinar cambios de filtro y página. */
export function useTableState<T extends Record<string, Value>>(defaults: T, prefix: string) {
  const [params, setParams] = useSearchParams()
  const pending = useRef(params)
  useLayoutEffect(() => { pending.current = params }, [params])
  const values = Object.fromEntries(Object.entries(defaults).map(([name, fallback]) => {
    const raw = params.get(`${prefix}.${name}`)
    if (raw === null) return [name, fallback]
    if (typeof fallback === 'number' || fallback === null) {
      const number = Number(raw)
      return [name, Number.isSafeInteger(number) && number >= 0
        && (name !== 'size' || [3, 10, 20, 25, 50, 100].includes(number)) ? number : fallback]
    }
    return [name, raw]
  })) as T
  function set<K extends keyof T & string>(name: K, action: SetStateAction<T[K]>) {
    const next = new URLSearchParams(pending.current)
    const value = typeof action === 'function' ? action(values[name]) : action
    if (value === defaults[name] || value === null || value === '') next.delete(`${prefix}.${name}`)
    else next.set(`${prefix}.${name}`, String(value))
    if (name !== 'page') next.delete(`${prefix}.page`)
    if (prefix === 'scope') {
      next.delete('analysisGroups.page'); next.delete('analysisQuestions.page')
    }
    pending.current = next
    setParams(next, { replace: true })
  }
  return { values, set }
}
