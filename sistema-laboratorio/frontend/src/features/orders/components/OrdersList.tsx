import { Ban, Send } from 'lucide-react';
import { Button, Card, EmptyState, Pagination } from '../../../shared/components/ui';
import { formatDate } from '../../../shared/lib/format';
import type { ServiceOrderSummary } from '../../../shared/types/domain';
import { OrderStatusBadge } from './OrderStatusBadge';

interface OrdersListProps {
  canManage: boolean;
  isChangingStatus: boolean;
  isLoading: boolean;
  onCancel: (order: ServiceOrderSummary) => void;
  onConfirm: (order: ServiceOrderSummary) => void;
  onOpen: (orderId: string) => void;
  onPageChange: (page: number) => void;
  orders: ServiceOrderSummary[];
  page: number;
  totalElements: number;
  totalPages: number;
}

export function OrdersList({
  canManage,
  isChangingStatus,
  isLoading,
  onCancel,
  onConfirm,
  onOpen,
  onPageChange,
  orders,
  page,
  totalElements,
  totalPages,
}: OrdersListProps) {
  return (
    <Card>
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-5 py-4">
        <div>
          <h2 className="font-bold text-slate-900">Órdenes de servicio</h2>
          <p className="mt-1 text-xs text-slate-500">
            Abre una orden para consultar sus datos comerciales y trazabilidad.
          </p>
        </div>
      </header>
      <div className="divide-y divide-slate-100">
        {orders.map((order) => (
          <article className="px-5 py-4 transition hover:bg-slate-50/80" key={order.id}>
            <button
              aria-label={`Abrir detalle de ${order.code}`}
              className="grid w-full cursor-pointer gap-4 rounded-xl text-left focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-lab-100 md:grid-cols-2 xl:grid-cols-[minmax(9rem,1fr)_minmax(10rem,1.2fr)_minmax(9rem,1fr)]"
              onClick={() => onOpen(order.id)}
              type="button"
            >
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="font-bold text-slate-900">{order.code}</p>
                  <OrderStatusBadge status={order.status} />
                </div>
                <p className="mt-1 text-xs leading-5 text-slate-500">
                  {order.sampleCount} muestra(s) · Registro {formatDate(order.createdAt)}
                </p>
              </div>
              <div className="min-w-0">
                <p className="break-words font-semibold text-slate-800">{order.clientName}</p>
                <p className="mt-1 text-xs text-slate-500">{order.laboratoryAreaName}</p>
              </div>
              <div className="min-w-0 text-sm text-slate-600">
                <p>Entrega: {formatDate(order.plannedDeliveryAt)}</p>
                <p className="mt-1 text-xs text-slate-500">
                  {order.proformaNumber ? `Proforma: ${order.proformaNumber}` : 'Sin proforma'}
                  {order.outsourced ? ' · Subcontratada' : ''}
                </p>
              </div>
            </button>
            {canManage && ['DRAFT', 'IN_PROGRESS'].includes(order.status) ? (
              <div className="mt-3 flex flex-wrap items-center gap-2">
                {order.status === 'DRAFT' ? (
                  <Button
                    disabled={isChangingStatus}
                    onClick={() => onConfirm(order)}
                    variant="ghost"
                  >
                    <Send size={16} />
                    Confirmar y enviar al laboratorio
                  </Button>
                ) : null}
                <button
                  aria-label={`Anular ${order.code}`}
                  className="rounded-xl px-2.5 py-2 text-rose-600 transition hover:bg-rose-50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-rose-100"
                  disabled={isChangingStatus}
                  onClick={() => onCancel(order)}
                  title="Anular orden"
                  type="button"
                >
                  <Ban size={17} />
                </button>
              </div>
            ) : null}
          </article>
        ))}
        {isLoading ? <p className="px-5 py-9 text-sm text-slate-500">Cargando órdenes…</p> : null}
        {!isLoading && orders.length === 0 ? (
          <EmptyState message="No se encontraron órdenes de servicio." />
        ) : null}
      </div>
      <div className="border-t border-slate-100 px-5 py-4">
        <Pagination
          onPageChange={onPageChange}
          page={page}
          totalElements={totalElements}
          totalPages={totalPages}
        />
      </div>
    </Card>
  );
}
