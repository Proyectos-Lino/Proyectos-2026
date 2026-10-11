import { History } from 'lucide-react';
import { EmptyState, Pagination } from '../../../shared/components/ui';
import { formatDate } from '../../../shared/lib/format';
import type { PageResponse, WorkOrderHistoryEntry } from '../../../shared/types/domain';
import { workOrderHistoryActionLabels, workOrderStatusLabels } from '../model/work-order-labels';

interface WorkOrderHistoryProps {
  history: PageResponse<WorkOrderHistoryEntry> | null;
  onPageChange: (page: number) => void;
}

export function WorkOrderHistory({ history, onPageChange }: WorkOrderHistoryProps) {
  return (
    <section className="border-t border-slate-100 pt-5">
      <div className="flex items-center gap-2">
        <History className="text-lab-700" size={17} />
        <h3 className="font-bold text-slate-900">Historial técnico</h3>
      </div>
      {history?.content.length ? (
        <div className="mt-4 space-y-3">
          {history.content.map((entry) => (
            <article className="rounded-xl border border-slate-200 p-3.5" key={entry.id}>
              <p className="text-sm font-semibold text-slate-800">
                {workOrderHistoryActionLabels[entry.action]}
              </p>
              {entry.changeNote ? (
                <p className="mt-1 text-sm text-slate-600">{entry.changeNote}</p>
              ) : null}
              {entry.previousStatus && entry.newStatus ? (
                <p className="mt-2 text-xs font-semibold text-slate-500">
                  Estado: {workOrderStatusLabels[entry.previousStatus]}
                  <span className="mx-1.5 text-slate-400">→</span>
                  {workOrderStatusLabels[entry.newStatus]}
                </p>
              ) : null}
              <p className="mt-2 text-xs text-slate-500">
                {entry.performedBy} · {formatDate(entry.occurredAt)}
              </p>
            </article>
          ))}
          <Pagination
            onPageChange={onPageChange}
            page={history.page}
            totalElements={history.totalElements}
            totalPages={history.totalPages}
          />
        </div>
      ) : (
        <div className="mt-4">
          <EmptyState message="No hay cambios técnicos registrados todavía." />
        </div>
      )}
    </section>
  );
}
