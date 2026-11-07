import { useEffect, useState } from 'react'
import { errorMessage } from '../utils/format'

export function useResource<T>(load: () => Promise<T>, refreshKey?: unknown) {
  const [resource, setResource] = useState<{ data: T | null; error: string | null; load: typeof load | null; revision: number; key: unknown }>({ data: null, error: null, load: null, revision: -1, key: undefined })
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    let active = true
    load().then((result) => {
      if (active) setResource({ data: result, error: null, load, revision, key: refreshKey })
    }).catch((cause) => {
      if (active) setResource({ data: null, error: errorMessage(cause), load, revision, key: refreshKey })
    })
    return () => { active = false }
  }, [load, revision, refreshKey])

  const loading = resource.load !== load || resource.revision !== revision || resource.key !== refreshKey
  return { data: resource.load === load ? resource.data : null, loading, error: loading ? null : resource.error,
    reload: () => setRevision((current) => current + 1) }
}
