import { useEffect, useState, type ReactNode } from 'react';
import { Modal, Pagination, StatusBadge } from '../../../shared/components/ui';
import { getErrorMessage } from '../../../shared/lib/errors';
import { formatDate } from '../../../shared/lib/format';
import { serviceOrderStatusLabels } from '../../orders/model/order-labels';
import { documentTypeLabels } from '../../../shared/lib/labels';
import type { Client, PageResponse, ServiceOrderSummary } from '../../../shared/types/domain';
import {
  getClientOrders,
  getProducerSamples,
  type ProducerSampleHistory,
} from '../api/clients-api';

interface ClientHistoryModalProps {
  client: Client | null;
  onClose: () => void;
}

type HistoryView = 'REQUESTER' | 'PRODUCER';

export function ClientHistoryModal({ client, onClose }: ClientHistoryModalProps) {
  const [view, setView] = useState<HistoryView>('REQUESTER');
  const [orders, setOrders] = useState<PageResponse<ServiceOrderSummary> | null>(null);
  const [samples, setSamples] = useState<PageResponse<ProducerSampleHistory> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(Boolean(client));

  async function loadHistory(page = 0, selectedView = view) {
    if (!client) return;
    setIsLoading(true);
    try {
      if (selectedView === 'REQUESTER') {
        setOrders(await getClientOrders(client.id, page));
      } else {
        setSamples(await getProducerSamples(client.id, page));
      }
      setError(null);
    } catch (reason) {
      setError(getErrorMessage(reason, 'No se pudo cargar el historial del participante.'));
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    if (!client) return;
    const selectedView = client.participantType === 'PRODUCER' ? 'PRODUCER' : 'REQUESTER';
    void Promise.resolve().then(() => {
      setView(selectedView);
      void loadHistory(0, selectedView);
    });
    // La apertura del modal reinicia el contexto del historial.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client]);

  const currentPage = view === 'REQUESTER' ? orders : samples;
  const canRequest = client?.participantType !== 'PRODUCER';
  const canProduce = client?.participantType !== 'CLIENT';

  function changeView(nextView: HistoryView) {
    setView(nextView);
    void loadHistory(0, nextView);
  }

  return (
    <Modal
      description={
        client ? `${documentTypeLabels[client.documentType]} ${client.documentNumber}` : undefined
      }
      isOpen={Boolean(client)}
      onClose={onClose}
      title={client ? `Historial de ${client.legalName}` : 'Historial del participante'}
    >
      <div className="space-y-4">
        {canRequest && canProduce ? (
          <div className="grid grid-cols-2 rounded-xl bg-slate-100 p-1">
            <HistoryTab active={view === 'REQUESTER'} onClick={() => changeView('REQUESTER')}>
              Como solicitante
            </HistoryTab>
            <HistoryTab active={view === 'PRODUCER'} onClick={() => changeView('PRODUCER')}>
              Como productor
            </HistoryTab>
          </div>
        ) : null}

        {isLoading ? <p className="text-sm text-slate-500">Cargando historial…</p> : null}
        {error ? <p className="rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{error}</p> : null}
        {!isLoading && !error && currentPage?.content.length === 0 ? (
          <p className="rounded-xl bg-slate-50 p-4 text-sm text-slate-600">
            No existen registros para este rol del participante.
          </p>
        ) : null}

        {view === 'REQUESTER'
          ? orders?.content.map((order) => <RequesterOrder key={order.id} order={order} />)
          : samples?.content.map((sample) => (
              <ProducerSample key={sample.sampleId} sample={sample} />
            ))}

        {currentPage ? (
          <Pagination
            onPageChange={(page) => void loadHistory(page)}
            page={currentPage.page}
            totalElements={currentPage.totalElements}
            totalPages={currentPage.totalPages}
          />
        ) : null}
      </div>
    </Modal>
  );
}

function HistoryTab({
  active,
  children,
  onClick,
}: {
  active: boolean;
  children: ReactNode;
  onClick: () => void;
}) {
  return (
    <button
      className={`rounded-lg px-3 py-2 text-sm font-bold transition ${
        active ? 'bg-white text-lab-800 shadow-sm' : 'text-slate-500'
      }`}
      onClick={onClick}
      type="button"
    >
      {children}
    </button>
  );
}

function RequesterOrder({ order }: { order: ServiceOrderSummary }) {
  return (
    <article className="rounded-xl border border-slate-200 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="font-bold text-slate-900">{order.code}</p>
        <StatusBadge value={serviceOrderStatusLabels[order.status]} />
      </div>
      <p className="mt-2 text-sm text-slate-600">
        {order.sampleCount} muestra(s) · Registrada el {formatDate(order.createdAt)}
      </p>
      <p className="mt-1 text-xs text-slate-500">
        Entrega programada: {formatDate(order.plannedDeliveryAt)}
      </p>
    </article>
  );
}

function ProducerSample({ sample }: { sample: ProducerSampleHistory }) {
  return (
    <article className="rounded-xl border border-slate-200 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="font-bold text-slate-900">
            {sample.orderCode} · Muestra {sample.commercialSequence}
          </p>
          <p className="mt-1 text-xs text-slate-500">{formatDate(sample.orderCreatedAt)}</p>
        </div>
        <StatusBadge value={serviceOrderStatusLabels[sample.orderStatus]} />
      </div>
      <p className="mt-3 text-sm text-slate-700">
        {[sample.productName, sample.varietyName].filter(Boolean).join(' · ') || 'Sin descripción'}
      </p>
      <p className="mt-1 text-xs text-slate-500">
        Identificación del propietario: {sample.clientIdentification || 'No indicada'}
      </p>
    </article>
  );
}
