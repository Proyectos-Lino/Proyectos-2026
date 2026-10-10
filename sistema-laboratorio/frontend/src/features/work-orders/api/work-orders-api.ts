import { request, requestBlob, toQuery } from '../../../shared/api/http-client';
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
  WorkOrderStatus,
} from '../../../shared/types/domain';

export interface WorkOrderFilters {
  search?: string;
  status?: WorkOrderStatus | '';
  laboratoryAreaId?: string;
  laboratoryDueFrom?: string;
  laboratoryDueTo?: string;
  createdFrom?: string;
  createdTo?: string;
}

function toStartOfDay(value?: string): string | undefined {
  return value ? new Date(`${value}T00:00:00`).toISOString() : undefined;
}

function toEndOfDay(value?: string): string | undefined {
  return value ? new Date(`${value}T23:59:59.999`).toISOString() : undefined;
}

function workOrderQuery(filters: WorkOrderFilters): string {
  return toQuery({
    search: filters.search,
    status: filters.status,
    laboratoryAreaId: filters.laboratoryAreaId,
    laboratoryDueFrom: toStartOfDay(filters.laboratoryDueFrom),
    laboratoryDueTo: toEndOfDay(filters.laboratoryDueTo),
    createdFrom: toStartOfDay(filters.createdFrom),
    createdTo: toEndOfDay(filters.createdTo),
  });
}

export function getWorkOrders(
  filters: WorkOrderFilters = {},
): Promise<PageResponse<WorkOrderSummary>> {
  const filtersQuery = workOrderQuery(filters);
  return request<PageResponse<WorkOrderSummary>>(
    `/work-orders${filtersQuery}${filtersQuery ? '&' : '?'}page=0&size=50`,
  );
}

export function exportWorkOrders(filters: WorkOrderFilters = {}): Promise<Blob> {
  return requestBlob(`/work-orders/export${workOrderQuery(filters)}`);
}

export interface CreateWorkOrderInput {
  serviceOrderId: string;
  laboratoryAreaId: string;
  responsibleId: string;
  laboratoryDueAt: string;
  sampleClassifications: Array<{
    sourceSampleId: string;
    technicalSampleTypeId: string;
  }>;
  assignments: Array<{
    sourceSampleTestId: string;
    analystId: string;
  }>;
}

export interface WorkOrdersContext {
  workOrders: WorkOrderSummary[];
  eligibleServiceOrders: EligibleServiceOrder[];
  catalog: CatalogSnapshot;
  analysts: UserSummary[];
  responsibleUsers: UserSummary[];
}

export async function getWorkOrdersContext(
  canManageWorkOrders: boolean,
  filters: WorkOrderFilters = {},
): Promise<WorkOrdersContext> {
  const [workOrderPage, catalog, eligibleServiceOrders, analysts, responsibleUsers] =
    await Promise.all([
      getWorkOrders(filters),
      request<CatalogSnapshot>('/catalogs'),
      canManageWorkOrders
        ? request<EligibleServiceOrder[]>('/work-orders/eligible-service-orders')
        : Promise.resolve([]),
      canManageWorkOrders ? request<UserSummary[]>('/users/analysts') : Promise.resolve([]),
      canManageWorkOrders
        ? request<UserSummary[]>('/users/technical-managers')
        : Promise.resolve([]),
    ]);

  return {
    workOrders: workOrderPage.content,
    eligibleServiceOrders,
    catalog,
    analysts,
    responsibleUsers,
  };
}

export function getWorkOrder(workOrderId: string): Promise<WorkOrderDetail> {
  return request<WorkOrderDetail>(`/work-orders/${workOrderId}`);
}

export function getEligibleServiceOrder(
  serviceOrderId: string,
): Promise<EligibleServiceOrderDetail> {
  return request<EligibleServiceOrderDetail>(
    `/work-orders/eligible-service-orders/${serviceOrderId}`,
  );
}

export function createWorkOrder(input: CreateWorkOrderInput): Promise<WorkOrderDetail> {
  return request<WorkOrderDetail>('/work-orders', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function cancelWorkOrder(workOrderId: string, reason: string): Promise<void> {
  return request<void>(`/work-orders/${workOrderId}/cancel`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
}

export function closeWorkOrder(workOrderId: string): Promise<void> {
  return request<void>(`/work-orders/${workOrderId}/close`, { method: 'POST' });
}

export function returnOrderToCommercial(
  serviceOrderId: string,
  reason: string,
): Promise<void> {
  return request<void>(`/service-orders/${serviceOrderId}/return-to-commercial`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
}

export function approveCommercialCorrection(serviceOrderId: string): Promise<void> {
  return request<void>(`/service-orders/${serviceOrderId}/approve-correction`, {
    method: 'POST',
  });
}

export function getWorkOrderHistory(
  workOrderId: string,
  page = 0,
): Promise<PageResponse<WorkOrderHistoryEntry>> {
  return request<PageResponse<WorkOrderHistoryEntry>>(
    `/work-orders/${workOrderId}/history?page=${page}&size=3`,
  );
}

export function getWorkOrderDocument(workOrderId: string): Promise<WorkOrderDocument> {
  return request<WorkOrderDocument>(`/work-orders/${workOrderId}/document`);
}
