import { Fragment, type ReactNode } from 'react';

interface ModalFooterProps {
  children: ReactNode;
  className?: string;
}

export function ModalFooter({ children, className = '' }: ModalFooterProps) {
  return (
    <Fragment>
      <div aria-hidden="true" className={`h-28 sm:h-20 ${className}`} />
      <footer
        className={`absolute inset-x-0 bottom-0 z-20 flex min-h-20 flex-wrap items-center justify-end gap-3 border-t border-slate-200 bg-white/95 px-6 py-4 shadow-[0_-8px_18px_-16px_rgba(15,23,42,0.45)] backdrop-blur ${className}`}
      >
        {children}
      </footer>
    </Fragment>
  );
}
