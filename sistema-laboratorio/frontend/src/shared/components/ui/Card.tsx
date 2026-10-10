import type { HTMLAttributes, ReactNode } from 'react';

interface CardProps extends HTMLAttributes<HTMLElement> {
  children: ReactNode;
}

export function Card({ children, className = '', ...props }: CardProps) {
  return (
    <section
      className={`overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-[0_10px_30px_-18px_rgba(15,23,42,0.28)] ${className}`}
      {...props}
    >
      {children}
    </section>
  );
}
