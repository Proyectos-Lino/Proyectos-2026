import { X } from 'lucide-react';
import { useEffect, type ReactNode } from 'react';

interface ModalProps {
  children: ReactNode;
  description?: string;
  isOpen: boolean;
  onClose: () => void;
  panelClassName?: string;
  title: string;
}

export function Modal({
  children,
  description,
  isOpen,
  onClose,
  panelClassName = 'max-w-3xl',
  title,
}: ModalProps) {
  useEffect(() => {
    if (!isOpen) {
      return undefined;
    }

    const previousOverflow = document.body.style.overflow;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };

    document.body.style.overflow = 'hidden';
    window.addEventListener('keydown', onKeyDown);

    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', onKeyDown);
    };
  }, [isOpen, onClose]);

  if (!isOpen) {
    return null;
  }

  return (
    <div
      aria-labelledby="modal-title"
      aria-modal="true"
      className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-slate-950/45 p-4 backdrop-blur-sm"
      role="dialog"
    >
      <button
        aria-label="Cerrar ventana"
        className="absolute inset-0 cursor-default"
        onClick={onClose}
        type="button"
      />
      <section
        className={`relative z-10 w-full overflow-hidden rounded-2xl border border-white/60 bg-white shadow-2xl ${panelClassName}`}
      >
        <header className="flex items-start justify-between gap-4 border-b border-slate-100 px-6 py-5">
          <div>
            <h2 className="text-lg font-bold tracking-tight text-slate-950" id="modal-title">
              {title}
            </h2>
            {description ? <p className="mt-1 text-sm text-slate-500">{description}</p> : null}
          </div>
          <button
            aria-label="Cerrar"
            className="rounded-lg p-2 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-lab-100"
            onClick={onClose}
            type="button"
          >
            <X size={20} />
          </button>
        </header>
        <div className="max-h-[calc(100vh-10rem)] overflow-x-hidden overflow-y-auto px-6 py-5">
          {children}
        </div>
      </section>
    </div>
  );
}
