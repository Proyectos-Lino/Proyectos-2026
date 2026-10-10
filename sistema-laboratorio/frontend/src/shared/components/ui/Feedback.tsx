import { CircleAlert, Inbox } from 'lucide-react';

export function ErrorNotice({ message }: { message: string | null }) {
  if (!message) {
    return null;
  }

  return (
    <div
      className="flex items-start gap-3 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-900"
      role="alert"
    >
      <CircleAlert className="mt-0.5 shrink-0 text-rose-600" size={18} />
      <p>{message}</p>
    </div>
  );
}

export function EmptyState({ message }: { message: string }) {
  return (
    <div className="grid min-h-48 place-items-center px-6 py-10 text-center">
      <div>
        <span className="mx-auto grid size-11 place-items-center rounded-xl bg-slate-100 text-slate-400">
          <Inbox size={21} />
        </span>
        <p className="mt-3 text-sm font-medium text-slate-600">{message}</p>
      </div>
    </div>
  );
}
