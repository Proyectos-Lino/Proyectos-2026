import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { CheckCircle2, CircleAlert, X } from 'lucide-react'
import { NotificationContext, type NotificationItem } from './NotificationContext'

export function Notifications({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<NotificationItem[]>([])
  const timers = useRef(new Map<string, ReturnType<typeof setTimeout>>())
  useEffect(() => {
    const activeTimers = timers.current
    return () => { activeTimers.forEach(clearTimeout); activeTimers.clear() }
  }, [])
  const remove = useCallback((id: string) => {
    clearTimeout(timers.current.get(id)); timers.current.delete(id)
    setItems((current) => current.filter((item) => item.id !== id))
  }, [])
  const show = useCallback((item: NotificationItem) => {
    clearTimeout(timers.current.get(item.id))
    setItems((current) => [...current.filter((existing) => existing.id !== item.id), item])
    if (item.tone === 'success') timers.current.set(item.id, setTimeout(() => remove(item.id), 7000))
  }, [remove])
  const context = useMemo(() => ({ show, remove }), [show, remove])
  const targets = [...new Set(items.map((item) => item.target))]
  return <NotificationContext.Provider value={context}>{children}
    {targets.map((target, index) => createPortal(<div className="notification-stack" aria-label="Notificaciones">
      {items.filter((item) => item.target === target).map((item) => <div key={item.id}
        className={`notification notification--${item.tone}`} role={item.tone === 'error' ? 'alert' : 'status'}>
        {item.tone === 'error' ? <CircleAlert size={20} /> : <CheckCircle2 size={20} />}
        <div className="notification__content">{item.content}</div>
        <button type="button" aria-label="Cerrar notificación" onClick={() => remove(item.id)}><X size={17} /></button>
      </div>)}
    </div>, target, `notices-${index}`))}
  </NotificationContext.Provider>
}
