import { useCallback, useEffect, useRef, useState } from 'react';
import { getErrorMessage } from '../../../shared/lib/errors';
import type {
  CatalogSnapshot,
  Client,
  PageResponse,
  ServiceOrderDetail,
  ServiceOrderDocument,
  ServiceOrderHistoryEntry,
  ServiceOrderSummary,
} from '../../../shared/types/domain';
import { createClient, type CreateClientInput } from '../../clients/api/clients-api';
import {
  cancelServiceOrder,
  assessCorrectionImpact,
  confirmServiceOrder,
  createServiceOrder,
  finalizeServiceOrder,
  getOrderContext,
  getServiceOrder,
  getServiceOrderDocument,
  getServiceOrderHistory,
  updateCommercialDetails,
  updateOrderSamples,
  submitServiceOrderCorrection,
  type CommercialDetailsInput,
  type CorrectionImpact,
  type CreateServiceOrderInput,
  type ServiceOrderFilters,
  type UpdateOrderSamplesInput,
} from '../api/orders-api';

const initialPagination = { page: 0, totalElements: 0, totalPages: 0 };

export function useOrders(initialFilters: ServiceOrderFilters = {}) {
  const [orders, setOrders] = useState<ServiceOrderSummary[]>([]);
  const [clients, setClients] = useState<Client[]>([]);
  const [activeClients, setActiveClients] = useState<Client[]>([]);
  const [activeProducers, setActiveProducers] = useState<Client[]>([]);
  const [catalog, setCatalog] = useState<CatalogSnapshot | null>(null);
  const [selectedOrder, setSelectedOrder] = useState<ServiceOrderDetail | null>(null);
  const [document, setDocument] = useState<ServiceOrderDocument | null>(null);
  const [history, setHistory] = useState<PageResponse<ServiceOrderHistoryEntry> | null>(null);
  const [completeHistory, setCompleteHistory] =
    useState<PageResponse<ServiceOrderHistoryEntry> | null>(null);
  const [pagination, setPagination] = useState(initialPagination);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isChangingStatus, setIsChangingStatus] = useState(false);
  const [isCreatingClient, setIsCreatingClient] = useState(false);
  const lastFilters = useRef<ServiceOrderFilters>(initialFilters);

  const loadOrders = useCallback(async (filters: ServiceOrderFilters = {}) => {
    setIsLoading(true);
    lastFilters.current = filters;

    try {
      const [orderPage, clientPage, activeClientPage, producerPage, catalogSnapshot] =
        await getOrderContext(filters);
      setOrders(orderPage.content);
      setClients(clientPage.content);
      setActiveClients(activeClientPage.content);
      setActiveProducers(producerPage.content);
      setCatalog(catalogSnapshot);
      setPagination({
        page: orderPage.page,
        totalElements: orderPage.totalElements,
        totalPages: orderPage.totalPages,
      });
      setError(null);
    } catch (reason) {
      setError(getErrorMessage(reason, 'No se pudo cargar la información de órdenes.'));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    let isMounted = true;

    void getOrderContext(initialFilters)
      .then(([orderPage, clientPage, activeClientPage, producerPage, catalogSnapshot]) => {
        if (isMounted) {
          setOrders(orderPage.content);
          setClients(clientPage.content);
          setActiveClients(activeClientPage.content);
          setActiveProducers(producerPage.content);
          setCatalog(catalogSnapshot);
          setPagination({
            page: orderPage.page,
            totalElements: orderPage.totalElements,
            totalPages: orderPage.totalPages,
          });
          setError(null);
        }
      })
      .catch((reason: unknown) => {
        if (isMounted) {
          setError(getErrorMessage(reason, 'No se pudo cargar la información de órdenes.'));
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
  }, [initialFilters]);

  const loadHistory = useCallback(async (orderId: string, page = 0) => {
    try {
      setHistory(await getServiceOrderHistory(orderId, page));
    } catch (reason) {
      setError(getErrorMessage(reason, 'No se pudo cargar el historial de la orden.'));
    }
  }, []);

  const loadCompleteHistory = useCallback(async (orderId: string, page = 0) => {
    setCompleteHistory(null);

    try {
      setCompleteHistory(await getServiceOrderHistory(orderId, page, 100));
      return true;
    } catch (reason) {
      setError(getErrorMessage(reason, 'No se pudo cargar el expediente de la orden.'));
      return false;
    }
  }, []);

  const openOrder = useCallback(async (orderId: string) => {
    try {
      const [order, orderHistory] = await Promise.all([
        getServiceOrder(orderId),
        getServiceOrderHistory(orderId),
      ]);
      setSelectedOrder(order);
      setHistory(orderHistory);
      setError(null);
    } catch (reason) {
      setError(getErrorMessage(reason, 'No se pudo abrir la orden de servicio.'));
    }
  }, []);

  const saveOrder = useCallback(
    async (input: CreateServiceOrderInput) => {
      setIsSaving(true);

      try {
        const createdOrder = await createServiceOrder(input);
        setSelectedOrder(createdOrder);
        await Promise.all([loadOrders(lastFilters.current), loadHistory(createdOrder.id)]);
        return true;
      } catch (reason) {
        setError(getErrorMessage(reason, 'No fue posible crear la orden.'));
        return false;
      } finally {
        setIsSaving(false);
      }
    },
    [loadHistory, loadOrders],
  );

  const saveCommercialDetails = useCallback(
    async (orderId: string, input: CommercialDetailsInput) => {
      setIsSaving(true);

      try {
        const updatedOrder = await updateCommercialDetails(orderId, input);
        setSelectedOrder(updatedOrder);
        await Promise.all([loadOrders(lastFilters.current), loadHistory(orderId)]);
        return true;
      } catch (reason) {
        setError(getErrorMessage(reason, 'No fue posible actualizar los datos comerciales.'));
        return false;
      } finally {
        setIsSaving(false);
      }
    },
    [loadHistory, loadOrders],
  );

  const saveOrderSamples = useCallback(
    async (orderId: string, input: UpdateOrderSamplesInput) => {
      setIsSaving(true);

      try {
        const updatedOrder = await updateOrderSamples(orderId, input);
        setSelectedOrder(updatedOrder);
        await Promise.all([loadOrders(lastFilters.current), loadHistory(orderId)]);
        return true;
      } catch (reason) {
        setError(getErrorMessage(reason, 'No fue posible actualizar las muestras y ensayos.'));
        return false;
      } finally {
        setIsSaving(false);
      }
    },
    [loadHistory, loadOrders],
  );

  const saveCorrection = useCallback(
    async (
      orderId: string,
      commercialDetails: CommercialDetailsInput,
      input: UpdateOrderSamplesInput,
      confirmResultInvalidation = false,
    ): Promise<{ saved: boolean; impact?: CorrectionImpact }> => {
      setIsSaving(true);
      try {
        const impact = await assessCorrectionImpact(orderId, input);
        if (impact.requiresResultInvalidation && !confirmResultInvalidation) {
          return { saved: false, impact };
        }
        await updateCommercialDetails(orderId, commercialDetails);
        const updatedOrder = await updateOrderSamples(orderId, {
          ...input,
          confirmResultInvalidation,
        });
        setSelectedOrder(updatedOrder);
        await Promise.all([loadOrders(lastFilters.current), loadHistory(orderId)]);
        setError(null);
        return { saved: true, impact };
      } catch (reason) {
        setError(getErrorMessage(reason, 'No fue posible guardar la corrección comercial.'));
        return { saved: false };
      } finally {
        setIsSaving(false);
      }
    },
    [loadHistory, loadOrders],
  );

  const openDocument = useCallback(async (orderId: string) => {
    try {
      setDocument(await getServiceOrderDocument(orderId));
      setError(null);
    } catch (reason) {
      setError(getErrorMessage(reason, 'No fue posible cargar el documento de la orden.'));
    }
  }, []);

  const closeDocument = useCallback(() => {
    setDocument(null);
  }, []);

  const runStatusChange = useCallback(
    async (orderId: string, action: () => Promise<void>, fallbackMessage: string) => {
      setIsChangingStatus(true);

      try {
        await action();
        await Promise.all([loadOrders(lastFilters.current), openOrder(orderId)]);
        setError(null);
        return true;
      } catch (reason) {
        setError(getErrorMessage(reason, fallbackMessage));
        return false;
      } finally {
        setIsChangingStatus(false);
      }
    },
    [loadOrders, openOrder],
  );

  const confirmOrder = useCallback(
    (orderId: string) =>
      runStatusChange(
        orderId,
        () => confirmServiceOrder(orderId),
        'No fue posible confirmar y enviar la orden al laboratorio.',
      ),
    [runStatusChange],
  );

  const finalizeOrder = useCallback(
    (orderId: string) =>
      runStatusChange(
        orderId,
        () => finalizeServiceOrder(orderId),
        'No fue posible finalizar la orden.',
      ),
    [runStatusChange],
  );

  const cancelOrder = useCallback(
    (orderId: string, reason: string) =>
      runStatusChange(
        orderId,
        () => cancelServiceOrder(orderId, reason),
        'No fue posible anular la orden.',
      ),
    [runStatusChange],
  );

  const submitCorrection = useCallback(
    (orderId: string, changeNote: string) =>
      runStatusChange(
        orderId,
        async () => {
          await submitServiceOrderCorrection(orderId, changeNote);
        },
        'No fue posible enviar la corrección a revisión técnica.',
      ),
    [runStatusChange],
  );

  const createQuickClient = useCallback(async (input: CreateClientInput) => {
    setIsCreatingClient(true);

    try {
      const client = await createClient(input);
      if (client.participantType !== 'PRODUCER') {
        setActiveClients((current) =>
          [...current, client].sort((first, second) =>
            first.legalName.localeCompare(second.legalName),
          ),
        );
      }
      if (client.participantType !== 'CLIENT') {
        setActiveProducers((current) =>
          [...current, client].sort((first, second) =>
            first.legalName.localeCompare(second.legalName),
          ),
        );
      }
      setClients((current) =>
        [...current, client].sort((first, second) =>
          first.legalName.localeCompare(second.legalName),
        ),
      );
      setError(null);
      return client;
    } catch (reason) {
      setError(getErrorMessage(reason, 'No fue posible registrar el cliente.'));
      return null;
    } finally {
      setIsCreatingClient(false);
    }
  }, []);

  return {
    activeClients,
    activeProducers,
    cancelOrder,
    catalog,
    closeDocument,
    completeHistory,
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
    loadHistory,
    loadOrders,
    openOrder,
    openDocument,
    orders,
    pagination,
    saveCommercialDetails,
    saveCorrection,
    saveOrderSamples,
    saveOrder,
    selectedOrder,
    submitCorrection,
    confirmOrder,
  };
}
