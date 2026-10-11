import { Download, Plus } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../../auth/hooks/useAuth';
import { Button, ErrorNotice, PageHeader } from '../../../shared/components/ui';
import type { ServiceOrderDetail, ServiceOrderSummary } from '../../../shared/types/domain';
import { exportServiceOrders, type ServiceOrderFilters } from '../api/orders-api';
import { downloadBlob } from '../../../shared/lib/downloads';
import { CancelOrderModal } from '../components/CancelOrderModal';
import { CommercialDetailsModal } from '../components/CommercialDetailsModal';
import { ConfirmOrderModal } from '../components/ConfirmOrderModal';
import { OrderDocumentModal } from '../components/OrderDocumentModal';
import { OrderDetail } from '../components/OrderDetail';
import { OrderFilters } from '../components/OrderFilters';
import { OrderRecordModal } from '../components/OrderRecordModal';
import { OrderWizardModal } from '../components/OrderWizardModal';
import { OrdersList } from '../components/OrdersList';
import { SubmitCorrectionModal } from '../components/SubmitCorrectionModal';
import { useOrders } from '../hooks/useOrders';

export function OrdersPage() {
  const [searchParams] = useSearchParams();
  const initialFilters = useMemo<ServiceOrderFilters>(
    () => ({
      search: searchParams.get('search') ?? '',
      status: (searchParams.get('status') as ServiceOrderFilters['status']) ?? '',
      laboratoryAreaId: searchParams.get('laboratoryAreaId') ?? '',
      createdFrom: searchParams.get('createdFrom') ?? '',
      createdTo: searchParams.get('createdTo') ?? '',
    }),
    [searchParams],
  );
  const { hasRole } = useAuth();
  const {
    activeClients,
    activeProducers,
    cancelOrder,
    catalog,
    closeDocument,
    completeHistory,
    confirmOrder,
    clients,
    createQuickClient,
    document,
    error,
    finalizeOrder,
    history,
    isChangingStatus,
    isCreatingClient,
    isLoading,
    isSaving,
    loadCompleteHistory,
    loadOrders,
    openDocument,
    openOrder,
    orders,
    pagination,
    saveCommercialDetails,
    saveCorrection,
    saveOrderSamples,
    saveOrder,
    selectedOrder,
    submitCorrection,
  } = useOrders(initialFilters);
  const [filters, setFilters] = useState<ServiceOrderFilters>(initialFilters);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [cancellingOrder, setCancellingOrder] = useState<ServiceOrderSummary | null>(null);
  const [confirmingOrder, setConfirmingOrder] = useState<ServiceOrderSummary | null>(null);
  const [editingOrder, setEditingOrder] = useState<ServiceOrderDetail | null>(null);
  const [editingSamplesOrder, setEditingSamplesOrder] = useState<ServiceOrderDetail | null>(null);
  const [recordOrder, setRecordOrder] = useState<ServiceOrderDetail | null>(null);
  const [submittingCorrectionOrder, setSubmittingCorrectionOrder] =
    useState<ServiceOrderDetail | null>(null);
  const canManage = hasRole('ADMIN', 'COMMERCIAL');

  function applyFilters(nextFilters: ServiceOrderFilters) {
    setFilters(nextFilters);
    void loadOrders(nextFilters);
  }

  function changePage(page: number) {
    void loadOrders({ ...filters, page });
  }

  async function submitCancellation(orderId: string, reason: string) {
    return cancelOrder(orderId, reason);
  }

  function openRecord(order: ServiceOrderDetail) {
    setRecordOrder(order);
    void loadCompleteHistory(order.id);
  }

  async function exportOrders() {
    downloadBlob(await exportServiceOrders(filters), 'ordenes-servicio.csv');
  }

  return (
    <div className="space-y-7">
      <PageHeader
        actions={
          <div className="flex flex-wrap gap-2">
            <Button onClick={() => void exportOrders()} variant="secondary">
              <Download size={17} />
              Exportar CSV
            </Button>
            {canManage ? (
              <Button
                onClick={() => {
                  setEditingSamplesOrder(null);
                  setIsFormOpen(true);
                }}
              >
                <Plus size={17} />
                Nueva orden
              </Button>
            ) : null}
          </div>
        }
        description="Control comercial, trazabilidad y avance de cada orden de servicio."
        eyebrow="Gestión comercial"
        title="Órdenes de servicio"
      />

      <ErrorNotice message={error} />

      <OrderFilters
        catalog={catalog}
        clients={clients}
        initialValues={initialFilters}
        onApply={applyFilters}
      />

      <div className="grid gap-6 2xl:grid-cols-[minmax(0,1.45fr)_minmax(25rem,0.8fr)]">
        <OrdersList
          canManage={canManage}
          isChangingStatus={isChangingStatus}
          isLoading={isLoading}
          onCancel={setCancellingOrder}
          onConfirm={setConfirmingOrder}
          onOpen={(orderId) => void openOrder(orderId)}
          onPageChange={changePage}
          orders={orders}
          page={pagination.page}
          totalElements={pagination.totalElements}
          totalPages={pagination.totalPages}
        />
        <OrderDetail
          canManage={canManage}
          history={history}
          isChangingStatus={isChangingStatus}
          onEditCommercialDetails={setEditingOrder}
          onEditSamples={(order) => {
            setEditingSamplesOrder(order);
            setIsFormOpen(true);
          }}
          onFinalize={(orderId) => void finalizeOrder(orderId)}
          onOpenRecord={openRecord}
          onSubmitCorrection={setSubmittingCorrectionOrder}
          order={selectedOrder}
          onViewDocument={(orderId) => void openDocument(orderId)}
        />
      </div>

      <OrderWizardModal
        catalog={catalog}
        clients={activeClients}
        editingOrder={editingSamplesOrder}
        isCreatingClient={isCreatingClient}
        isOpen={isFormOpen}
        isSaving={isSaving}
        key={editingSamplesOrder?.id ?? 'new-service-order'}
        onClose={() => {
          setIsFormOpen(false);
          setEditingSamplesOrder(null);
        }}
        onCreateClient={createQuickClient}
        onSubmitCreate={saveOrder}
        onSubmitCorrection={saveCorrection}
        onSubmitSamplesUpdate={saveOrderSamples}
        producers={activeProducers}
      />
      <CommercialDetailsModal
        isOpen={Boolean(editingOrder)}
        isSaving={isSaving}
        key={editingOrder?.id ?? 'no-commercial-order'}
        onClose={() => setEditingOrder(null)}
        onSubmit={saveCommercialDetails}
        order={editingOrder}
      />
      <CancelOrderModal
        isSaving={isChangingStatus}
        onClose={() => setCancellingOrder(null)}
        onSubmit={submitCancellation}
        order={cancellingOrder}
      />
      <ConfirmOrderModal
        isSaving={isChangingStatus}
        onClose={() => setConfirmingOrder(null)}
        onConfirm={confirmOrder}
        order={confirmingOrder}
      />
      <OrderDocumentModal document={document} onClose={closeDocument} />
      <SubmitCorrectionModal
        isSaving={isChangingStatus}
        onClose={() => setSubmittingCorrectionOrder(null)}
        onSubmit={submitCorrection}
        order={submittingCorrectionOrder}
      />
      <OrderRecordModal
        catalog={catalog}
        history={completeHistory}
        onClose={() => setRecordOrder(null)}
        onHistoryPageChange={(page) => {
          if (recordOrder) {
            void loadCompleteHistory(recordOrder.id, page);
          }
        }}
        order={recordOrder}
      />
    </div>
  );
}
