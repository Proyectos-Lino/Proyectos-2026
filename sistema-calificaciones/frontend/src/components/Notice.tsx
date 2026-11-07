import { useContext, useEffect, useId, useRef, type ReactNode } from 'react'
import { NotificationContext } from './NotificationContext'

/** Conserva los avisos junto a su operación, pero los presenta fuera del flujo de la página. */
export function Notice({ tone, children }: { tone: 'error' | 'success'; children: ReactNode }) {
  const context = useContext(NotificationContext)
  const anchor = useRef<HTMLSpanElement>(null)
  const id = useId()
  useEffect(() => {
    if (!context) return
    const target = anchor.current?.closest('dialog') ?? document.body
    context.show({ id, tone, content: children, target })
    return () => context.remove(id)
  }, [context, id, tone, children])
  return <span ref={anchor} hidden />
}
