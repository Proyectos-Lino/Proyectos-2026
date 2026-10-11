import { useCallback, useEffect, useState } from 'react';
import { getErrorMessage } from '../../../shared/lib/errors';
import type {
  CatalogSnapshot,
  EligibleServiceOrder,
  EligibleServiceOrderDetail,
  PageResponse,
  UserSummary,
  WorkOrderDocument,
  WorkOrderDetail,
  WorkOrderHistoryEntry,
  WorkOrderSummary,
} from '../../../shared/types/domain';
import {
  cancelWorkOrder,
  closeWorkOrder,
  createWorkOrder,
  approveCommercialCorrection,
  getEligibleServiceOrder,
  getWorkOrder,
  getWorkOrderDocument,
  getWorkOrderHistory,
  getWorkOrdersContext,
  getWorkOrders,
  returnOrderToCommercial,
  type CreateWorkOrderInput,
  type WorkOrderFilters,
} from '../api/work-orders-api';

export function useWorkOrders(canManageWorkOrders: boolean, initialFilters: WorkOrderFilters = {}) {
  const [workOrders, setWorkOrders] = useState<WorkOrderSummary[]>([]);
  const [eligibleServiceOrders, setEligibleServiceOrders] = useState<EligibleServiceOrder[]>([]);
  const [analysts, setAnalysts] = useState<UserSummary[]>([]);
  const [responsibleUsers, setResponsibleUsers] = useState<UserSummary[]>([]);
  const [catalog, setCatalog] = useState<CatalogSnapshot | null>(null);
  const [selectedWorkOrder, setSelectedWorkOrder] = useState<WorkOrderDetail | null>(null);
  const [history, setHistory] = useState<PageResponse<WorkOrderHistoryEntry> | null>(null);
  const [document, setDocument] = useState<WorkOrderDocument | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [filters, setFilters] = useState<WorkOrderFilters>(initialFilters);

  const loadWorkOrders = useCallback(async () => {
    setIsLoading(true);

    try {
      const context = await getWorkOrdersContext(canManageWorkOrders, filters);
      setWorkOrders(context.workOrders);
      setEligibleServiceOrders(context.eligibleServiceOrders);
      setCatalog(context.catalog);
      setAnalysts(context.analysts);
      setResponsibleUsers(context.responsibleUsers);
      setError(null);
    } catch (reason) {
      setError(getErrorMessage(reason, 'No se pudo cargar el módulo técnico.'));
    } finally {
      setIsLoading(false);
    }
  }, [canManageWorkOrders, filters]);

  useEffect(() => {
    let isMounted = true;

    void getWorkOrdersContext(canManageWorkOrders, initialFilters)
      .then((context) => {
        if (isMounted) {
          setWorkOrders(context.workOrders);
          setEligibleServiceOrders(context.eligibleServiceOrders);
          setCatalog(context.catalog);
          setAnalysts(context.analysts);
          setResponsibleUsers(context.responsibleUsers);
          setError(null);
        }
      })
      .catch((reason: unknown) => {
        if (isMounted) {
          setError(getErrorMessage(reason, 'No se pudo cargar el módulo técnico.'));
        }
      })
      .finally(() => {
        if (isMounted) {
          setIsLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, [canManageWorkOrders, initialFilters]);

  const applyFilters = useCallback(async (nextFilters: WorkOrderFilters) => {
    setIsLoading(true);
    try {
      const page = await getWorkOrders(nextFilters);
      setFilters(nextFilters);
      setWorkOrders(page.content);
      setSelectedWorkOrder(null);
      setHistory(null);
      setError(null);
    } catch (reason) {
      setError(getErrorMessage(reason, 'No se pudieron aplicar los filtros técnicos.'));
    } finally {
      setIsLoading(false);
    }
  }, []);

  const openWorkOrder = useCallback(async (workOrderId: string) => {
    try {
      const [workOrder, workOrderHistory] = await Promise.all([
        getWorkOrder(workOrderId),
        getWorkOrderHistory(workOrderId),
      ]);
      setSelectedWorkOrder(workOrder);
      setHistory(workOrderHistory);
      setError(null);
    } catch (reason) {
      setError(getErrorMessage(reason, 'No se pudo abrir la orden de trabajo.'));
    }
  }, []);

  const getEligibleSourceOrder = useCallback(
    async (orderId: string): Promise<EligibleServiceOrderDetail | null> => {
      try {
        return await getEligibleServiceOrder(orderId);
      } catch (reason) {
        setError(getErrorMessage(reason, 'No se pudo recuperar la orden de servicio elegible.'));
        return null;
      }
    },
    [],
  );

  const saveWorkOrder = useCallback(
    async (input: CreateWorkOrderInput) => {
      setIsSaving(true);

      try {
        const workOrder = await createWorkOrder(input);
        setSelectedWorkOrder(workOrder);
        const workOrderHistory = await getWorkOrderHistory(workOrder.id);
        setHistory(workOrderHistory);
        await loadWorkOrders();
        return true;
      } catch (reason) {
        setError(getErrorMessage(reason, 'No se pudo emitir la orden de trabajo.'));
        return false;
      } finally {
        setIsSaving(false);
      }
    },
    [loadWorkOrders],
  );

  const runOperationalAction = useCallback(
    async (workOrderId: string, action: () => Promise<void>, fallbackMessage: string) => {
      setIsSaving(true);

      try {
        await action();
        await Promise.all([loadWorkOrders(), openWorkOrder(workOrderId)]);
        setError(null);
        return true;
      } catch (reason) {
        setError(getErrorMessage(reason, fallbackMessage));
        return false;
      } finally {
        setIsSaving(false);
      }
    },
    [loadWorkOrders, openWorkOrder],
  );

  const cancelSelectedWorkOrder = useCallback(
    (workOrderId: string, reason: string) =>
      runOperationalAction(
        workOrderId,
        () => cancelWorkOrder(workOrderId, reason),
        'No se pudo anular la orden de trabajo.',
      ),
    [runOperationalAction],
  );

  const closeSelectedWorkOrder = useCallback(
    (workOrderId: string) =>
      runOperationalAction(
        workOrderId,
        () => closeWorkOrder(workOrderId),
        'No se pudo cerrar la orden de trabajo.',
      ),
    [runOperationalAction],
  );

  const returnSelectedOrderToCommercial = useCallback(
    (workOrderId: string, serviceOrderId: string, reason: string) =>
      runOperationalAction(
        workOrderId,
        () => returnOrderToCommercial(serviceOrderId, reason),
        'No se pudo devolver la orden de servicio a Comercial.',
      ),
    [runOperationalAction],
  );

  const approveSelectedCommercialCorrection = useCallback(
    (workOrderId: string, serviceOrderId: string) =>
      runOperationalAction(
        workOrderId,
        () => approveCommercialCorrection(serviceOrderId),
        'No se pudo aprobar la corrección comercial.',
      ),
    [runOperationalAction],
  );

  const loadHistory = useCallback(async (workOrderId: string, page = 0) => {
    try {
      setHistory(await getWorkOrderHistory(workOrderId, page));
    } catch (reason) {
      setError(getErrorMessage(reason, 'No se pudo cargar el historial técnico.'));
    }
  }, []);

  const openDocument = useCallback(async (workOrderId: string) => {
    try {
      setDocument(await getWorkOrderDocument(workOrderId));
      setError(null);
    } catch (reason) {
      setError(getErrorMessage(reason, 'No se pudo cargar el documento técnico.'));
    }
  }, []);

  const closeDocument = useCallback(() => {
    setDocument(null);
  }, []);

  return {
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
  };
}
