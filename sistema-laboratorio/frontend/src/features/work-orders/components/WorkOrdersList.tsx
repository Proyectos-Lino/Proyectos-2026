import { ChevronRight } from 'lucide-react';
import { Card, EmptyState } from '../../../shared/components/ui';
import { formatDate } from '../../../shared/lib/format';
import type { WorkOrderSummary } from '../../../shared/types/domain';
import { WorkOrderStatusBadge } from './WorkOrderStatusBadge';

interface WorkOrdersListProps {
  isLoading: boolean;
  onOpen: (workOrderId: string) => void;
  workOrders: WorkOrderSummary[];
}

export function WorkOrdersList({ isLoading, onOpen, workOrders }: WorkOrdersListProps) {
  return (
    <Card>
      <header className="border-b border-slate-100 px-5 py-4">
        <h2 className="font-bold text-slate-900">Órdenes técnicas emitidas</h2>
        <p className="mt-1 text-xs text-slate-500">
          Consulta asignaciones, códigos de laboratorio y avance técnico.
        </p>
      </header>
      <div className="divide-y divide-slate-100">
        {workOrders.map((order) => (
          <button
            className="flex w-full items-center justify-between gap-3 px-5 py-4 text-left transition hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-lab-500"
            key={order.id}
            onClick={() => onOpen(order.id)}
            type="button"
          >
            <div>
              <p className="font-semibold text-slate-800">{order.code}</p>
              <p className="mt-1 text-sm text-slate-500">
                {order.laboratoryAreaName} · {order.sampleCount} muestra(s)
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Responsable: {order.responsibleName} · Vence {formatDate(order.laboratoryDueAt)}
              </p>
            </div>
            <div className="flex items-center gap-2">
              <WorkOrderStatusBadge status={order.status} />
              <ChevronRight className="text-slate-400" size={17} />
            </div>
          </button>
        ))}
        {isLoading ? <p className="px-5 py-8 text-sm text-slate-500">Cargando órdenes…</p> : null}
        {!isLoading && workOrders.length === 0 ? (
          <EmptyState message="No hay órdenes de trabajo emitidas." />
        ) : null}
      </div>
    </Card>
  );
}
