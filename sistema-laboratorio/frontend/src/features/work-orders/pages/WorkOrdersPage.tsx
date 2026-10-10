import { Download, Plus } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../../auth/hooks/useAuth';
import { Button, ErrorNotice, PageHeader } from '../../../shared/components/ui';
import type { WorkOrderDetail as WorkOrderDetailModel } from '../../../shared/types/domain';
import { CancelWorkOrderModal } from '../components/CancelWorkOrderModal';
import { WorkOrderDetail } from '../components/WorkOrderDetail';
import { WorkOrderDocumentModal } from '../components/WorkOrderDocumentModal';
import { WorkOrderFormModal } from '../components/WorkOrderFormModal';
import { ReturnToCommercialModal } from '../components/ReturnToCommercialModal';
import { WorkOrderFilters } from '../components/WorkOrderFilters';
import { WorkOrdersList } from '../components/WorkOrdersList';
import { useWorkOrders } from '../hooks/useWorkOrders';
import { exportWorkOrders, type WorkOrderFilters as Filters } from '../api/work-orders-api';
import { downloadBlob } from '../../../shared/lib/downloads';

export function WorkOrdersPage() {
  const [searchParams] = useSearchParams();
  const initialFilters = useMemo<Filters>(
    () => ({
      search: searchParams.get('search') ?? '',
      status: (searchParams.get('status') as Filters['status']) ?? '',
      laboratoryAreaId: searchParams.get('laboratoryAreaId') ?? '',
      createdFrom: searchParams.get('createdFrom') ?? '',
      createdTo: searchParams.get('createdTo') ?? '',
    }),
    [searchParams],
  );
  const { hasRole, user } = useAuth();
  const canManageWorkOrders = hasRole('ADMIN', 'TECHNICAL_MANAGER');
  const {
    analysts,
    approveSelectedCommercialCorrection,
    applyFilters,
    cancelSelectedWorkOrder,
    catalog,
    closeDocument,
    closeSelectedWorkOrder,
    document,
    eligibleServiceOrders,
    error,
    filters,
    getEligibleSourceOrder,
    history,
    isLoading,
    isSaving,
    loadHistory,
    openWorkOrder,
    openDocument,
    responsibleUsers,
    returnSelectedOrderToCommercial,
    saveWorkOrder,
    selectedWorkOrder,
    workOrders,
  } = useWorkOrders(canManageWorkOrders, initialFilters);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [cancellingWorkOrder, setCancellingWorkOrder] = useState<WorkOrderDetailModel | null>(null);
  const [returningWorkOrder, setReturningWorkOrder] = useState<WorkOrderDetailModel | null>(null);
  const canRecordResults = hasRole('ADMIN', 'ANALYST');
  const canValidateResults = hasRole('ADMIN', 'VALIDATOR');

  async function exportOrders() {
    downloadBlob(await exportWorkOrders(filters), 'ordenes-trabajo.csv');
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
            {canManageWorkOrders ? (
              <Button onClick={() => setIsFormOpen(true)}>
                <Plus size={17} />
                Emitir orden de trabajo
              </Button>
            ) : null}
          </div>
        }
        description="Asigna analistas, identifica muestras y controla el avance técnico de cada OT."
        eyebrow="Operación técnica"
        title="Órdenes de trabajo"
      />

      <ErrorNotice message={error} />

      <WorkOrderFilters
        catalog={catalog}
        initialValues={initialFilters}
        onApply={(nextFilters) => void applyFilters(nextFilters)}
      />

      <div className="grid gap-6 xl:grid-cols-[minmax(20rem,0.9fr)_minmax(0,1.1fr)]">
        <WorkOrdersList
          isLoading={isLoading}
          onOpen={(workOrderId) => void openWorkOrder(workOrderId)}
          workOrders={workOrders}
        />
        <WorkOrderDetail
          canManageWorkOrders={canManageWorkOrders}
          canRecordResults={canRecordResults}
          canValidateResults={canValidateResults}
          catalog={catalog}
          currentUserId={user?.id}
          history={history}
          isSaving={isSaving}
          isAdministrator={hasRole('ADMIN')}
          onCancel={setCancellingWorkOrder}
          onApproveCorrection={(workOrderId, serviceOrderId) =>
            void approveSelectedCommercialCorrection(workOrderId, serviceOrderId)
          }
          onClose={(workOrderId) => void closeSelectedWorkOrder(workOrderId)}
          onHistoryPageChange={(page) => {
            if (selectedWorkOrder) {
              void loadHistory(selectedWorkOrder.id, page);
            }
          }}
          onRefresh={() => {
            if (selectedWorkOrder) {
              void openWorkOrder(selectedWorkOrder.id);
            }
          }}
          onReturnToCommercial={setReturningWorkOrder}
          onViewDocument={(workOrderId) => void openDocument(workOrderId)}
          workOrder={selectedWorkOrder}
        />
      </div>

      {canManageWorkOrders ? (
        <WorkOrderFormModal
          analysts={analysts}
          eligibleServiceOrders={eligibleServiceOrders}
          isOpen={isFormOpen}
          isSaving={isSaving}
          onClose={() => setIsFormOpen(false)}
          onLoadEligibleSource={getEligibleSourceOrder}
          onSubmit={saveWorkOrder}
          responsibleUsers={responsibleUsers}
          sampleTypes={catalog?.sampleTypes ?? []}
        />
      ) : null}
      <CancelWorkOrderModal
        isSaving={isSaving}
        onClose={() => setCancellingWorkOrder(null)}
        onSubmit={cancelSelectedWorkOrder}
        workOrder={cancellingWorkOrder}
      />
      <ReturnToCommercialModal
        isSaving={isSaving}
        onClose={() => setReturningWorkOrder(null)}
        onSubmit={(serviceOrderId, reason) => {
          if (!returningWorkOrder) {
            return Promise.resolve(false);
          }
          return returnSelectedOrderToCommercial(
            returningWorkOrder.id,
            serviceOrderId,
            reason,
          );
        }}
        workOrder={returningWorkOrder}
      />
      <WorkOrderDocumentModal document={document} onClose={closeDocument} />
    </div>
  );
}
