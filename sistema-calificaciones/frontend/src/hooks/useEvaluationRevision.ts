import { useEffect, useState } from 'react'

/** Invalida el contexto visible de la evaluación después de una escritura confirmada. */
export function useEvaluationRevision(organization: number, evaluation: number) {
  const [revision, setRevision] = useState(0)
  useEffect(() => {
    const prefix = `/organizaciones/${organization}/evaluaciones/${evaluation}`
    function changed(event: Event) {
      const path = (event as CustomEvent<string>).detail
      if (path === prefix || path.startsWith(`${prefix}/`)) setRevision((value) => value + 1)
    }
    window.addEventListener('califica:evaluation-changed', changed)
    return () => window.removeEventListener('califica:evaluation-changed', changed)
  }, [organization, evaluation])
  return revision
}
