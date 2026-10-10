import type { ReactNode } from 'react';

interface PageHeaderProps {
  actions?: ReactNode;
  eyebrow: string;
  title: string;
  description: string;
}

export function PageHeader({ actions, description, eyebrow, title }: PageHeaderProps) {
  return (
    <header className="flex flex-wrap items-end justify-between gap-5">
      <div>
        <p className="text-sm font-bold tracking-wide text-lab-700">{eyebrow}</p>
        <h1 className="mt-1 text-3xl font-bold tracking-tight text-slate-950">{title}</h1>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">{description}</p>
      </div>
      {actions ? <div className="flex items-center gap-3">{actions}</div> : null}
    </header>
  );
}
