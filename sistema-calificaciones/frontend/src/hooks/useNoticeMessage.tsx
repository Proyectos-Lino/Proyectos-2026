import { useCallback, useMemo, useState, type ReactNode } from 'react'

/** Cada operación genera un aviso, incluso si repite el texto del anterior. */
export function useNoticeMessage(): [ReactNode, (text: string | null) => void] {
  const [notice, setNotice] = useState({ text: null as string | null, revision: 0 })
  const setMessage = useCallback((text: string | null) => {
    setNotice((current) => ({ text, revision: current.revision + 1 }))
  }, [])
  const message = useMemo(() => notice.text === null ? null
    : <span key={notice.revision}>{notice.text}</span>, [notice])
  return [message, setMessage]
}
