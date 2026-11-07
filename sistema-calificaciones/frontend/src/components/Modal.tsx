import { useEffect, useId, useRef, type ReactNode } from 'react'
import { X } from 'lucide-react'

interface ModalProps {
  title: string
  description?: string
  children: ReactNode
  onClose: () => void
  busy?: boolean
  wide?: boolean
}

export function Modal({ title, description, children, onClose, busy = false, wide = false }: ModalProps) {
  const dialog = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const descriptionId = useId()

  useEffect(() => {
    const element = dialog.current
    const previousFocus = document.activeElement
    element?.showModal()
    element?.querySelector<HTMLInputElement>('.modal-form input:not(:disabled)')?.focus()
    return () => {
      element?.close()
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) previousFocus.focus()
    }
  }, [])

  function close() {
    if (!busy) onClose()
  }

  return <dialog ref={dialog} className={`modal-dialog${wide ? ' modal-dialog--wide' : ''}`} aria-labelledby={titleId}
    aria-describedby={description ? descriptionId : undefined}
    onCancel={(event) => { event.preventDefault(); event.stopPropagation(); close() }}
    onMouseDown={(event) => { if (event.target === event.currentTarget) close() }}>
    <div className="modal-dialog__header">
      <div><p className="section-kicker">GESTIÓN DE REGISTROS</p><h2 id={titleId}>{title}</h2>
        {description && <p id={descriptionId}>{description}</p>}</div>
      <button type="button" className="modal-dialog__close" aria-label="Cerrar ventana"
        onClick={close} disabled={busy}><X size={19} /></button>
    </div>
    {children}
  </dialog>
}
