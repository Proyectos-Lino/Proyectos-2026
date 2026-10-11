import { ArrowUpRight, History } from 'lucide-react';
import { Button, EmptyState } from '../../../shared/components/ui';
import { formatDate } from '../../../shared/lib/format';
import type { PageResponse, ServiceOrderHistoryEntry } from '../../../shared/types/domain';
import { serviceOrderHistoryActionLabels } from '../model/order-labels';

interface OrderHistoryProps {
  history: PageResponse<ServiceOrderHistoryEntry> | null;
  onViewAll: () => void;
}

export function OrderHistory({ history, onViewAll }: OrderHistoryProps) {
  const recentEntries = history?.content.slice(0, 3) ?? [];

  return (
    <section className="border-t border-slate-100 pt-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <History className="text-lab-700" size={17} />
          <div>
            <h3 className="font-bold text-slate-900">Historial de cambios</h3>
            <p className="mt-0.5 text-xs text-slate-500">Últimos tres movimientos registrados.</p>
          </div>
        </div>
        {history?.totalElements ? (
          <Button className="min-h-0 px-3 py-2 text-xs" onClick={onViewAll} variant="secondary">
            Ver expediente completo
            <ArrowUpRight size={15} />
          </Button>
        ) : null}
      </div>
      {recentEntries.length ? (
        <div className="mt-4 space-y-3">
          {recentEntries.map((entry) => (
            <article className="rounded-xl border border-slate-200 p-3.5" key={entry.id}>
              <p className="text-sm font-semibold text-slate-800">
                {serviceOrderHistoryActionLabels[entry.action]}
              </p>
              {entry.changeNote ? (
                <p className="mt-1 text-sm text-slate-600">{entry.changeNote}</p>
              ) : null}
              <p className="mt-2 text-xs text-slate-500">
                {entry.performedBy} · {formatDate(entry.occurredAt)}
              </p>
            </article>
          ))}
        </div>
      ) : (
        <div className="mt-4">
          <EmptyState message="No hay cambios registrados todavía." />
        </div>
      )}
    </section>
  );
}
