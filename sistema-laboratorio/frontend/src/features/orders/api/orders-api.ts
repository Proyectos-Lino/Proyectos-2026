import { request, requestBlob, toQuery } from '../../../shared/api/http-client';
import type {
  CatalogSnapshot,
  Client,
  PageResponse,
  PostAnalysisCustody,
  ServiceOrderDetail,
  ServiceOrderDocument,
  ServiceOrderHistoryEntry,
  ServiceOrderSummary,
  ServiceOrderStatus,
} from '../../../shared/types/domain';

export interface CommercialDetailsInput {
  clientId?: string;
  reportName?: string;
  reportEmail?: string;
  reportAddress?: string;
  deliveryMethod: 'EMAIL' | 'PICKUP';
  pickupPerson?: string;
  plannedDeliveryAt: string;
  proformaNumber?: string;
  technicalProposalNumber?: string;
  developmentDays?: number;
  outsourced: boolean;
  postAnalysisCustody: PostAnalysisCustody;
  generalObservations?: string;
}

export interface CreateServiceOrderInput extends CommercialDetailsInput {
  clientId: string;
  laboratoryAreaId: string;
  samples: Array<{
    sourceSampleId?: string;
    clientIdentification?: string;
    producerId?: string;
    productName: string;
    varietyName?: string;
    sampleTypeId: string;
    physicalDescription?: string;
    samplingLocation?: string;
    origin?: string;
    samplerName?: string;
    sampledAt?: string;
    quantity?: number;
    measurementUnitId?: string;
    containerDescription?: string;
    batchNumber?: string;
    productionYear?: number;
    brand?: string;
    originCertificate?: string;
    sanitaryRegistration?: string;
    cropAge?: string;
    carrierCount?: number;
    budCount?: number;
    counterSampleInCustody: boolean;
    observations?: string;
    requestedTests: Array<{
      sourceRequestedTestId?: string;
      serviceId: string;
      testDefinitionId: string;
      testMethodId: string;
    }>;
  }>;
}

export interface UpdateOrderSamplesInput {
  samples: CreateServiceOrderInput['samples'];
  confirmResultInvalidation?: boolean;
  expectedAffectedResultCount?: number;
  changeReason?: string;
}

export interface CorrectionImpactItem {
  sourceSampleId?: string;
  sourceRequestedTestId?: string;
  sampleLabel: string;
  testName: string;
  reason: string;
  resultCount: number;
}

export interface CorrectionImpact {
  structuralChange: boolean;
  requiresResultInvalidation: boolean;
  affectedWorkOrderCount: number;
  affectedResultCount: number;
  affectedItems: CorrectionImpactItem[];
  warningMessage: string;
}

export interface ServiceOrderFilters {
  search?: string;
  status?: ServiceOrderStatus | '';
  clientId?: string;
  laboratoryAreaId?: string;
  plannedDeliveryFrom?: string;
  plannedDeliveryTo?: string;
  createdFrom?: string;
  createdTo?: string;
  page?: number;
  size?: number;
}

export function createServiceOrder(input: CreateServiceOrderInput): Promise<ServiceOrderDetail> {
  return request<ServiceOrderDetail>('/service-orders', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function finalizeServiceOrder(orderId: string): Promise<void> {
  return request<void>(`/service-orders/${orderId}/finalize`, { method: 'POST' });
}

export function confirmServiceOrder(orderId: string): Promise<void> {
  return request<void>(`/service-orders/${orderId}/confirm`, { method: 'POST' });
}

export function cancelServiceOrder(orderId: string, reason: string): Promise<void> {
  return request<void>(`/service-orders/${orderId}/cancel`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
}

export function updateCommercialDetails(
  orderId: string,
  input: CommercialDetailsInput,
): Promise<ServiceOrderDetail> {
  return request<ServiceOrderDetail>(`/service-orders/${orderId}/commercial-details`, {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}

export function updateOrderSamples(
  orderId: string,
  input: UpdateOrderSamplesInput,
): Promise<ServiceOrderDetail> {
  return request<ServiceOrderDetail>(`/service-orders/${orderId}/samples`, {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}

export function assessCorrectionImpact(
  orderId: string,
  input: UpdateOrderSamplesInput,
): Promise<CorrectionImpact> {
  return request<CorrectionImpact>(`/service-orders/${orderId}/correction-impact`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function returnServiceOrderToCommercial(
  orderId: string,
  reason: string,
): Promise<ServiceOrderDetail> {
  return request<ServiceOrderDetail>(`/service-orders/${orderId}/return-to-commercial`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
}

export function submitServiceOrderCorrection(
  orderId: string,
  changeNote: string,
): Promise<ServiceOrderDetail> {
  return request<ServiceOrderDetail>(`/service-orders/${orderId}/submit-correction`, {
    method: 'POST',
    body: JSON.stringify({ changeNote }),
  });
}

export function approveServiceOrderCorrection(orderId: string): Promise<ServiceOrderDetail> {
  return request<ServiceOrderDetail>(`/service-orders/${orderId}/approve-correction`, {
    method: 'POST',
  });
}

function toStartOfDay(value?: string): string | undefined {
  return value ? new Date(`${value}T00:00:00`).toISOString() : undefined;
}

function toEndOfDay(value?: string): string | undefined {
  return value ? new Date(`${value}T23:59:59.999`).toISOString() : undefined;
}

export function getServiceOrders(
  filters: ServiceOrderFilters = {},
): Promise<PageResponse<ServiceOrderSummary>> {
  return request<PageResponse<ServiceOrderSummary>>(
    `/service-orders${toQuery({
      search: filters.search,
      status: filters.status,
      clientId: filters.clientId,
      laboratoryAreaId: filters.laboratoryAreaId,
      plannedDeliveryFrom: toStartOfDay(filters.plannedDeliveryFrom),
      plannedDeliveryTo: toEndOfDay(filters.plannedDeliveryTo),
      createdFrom: toStartOfDay(filters.createdFrom),
      createdTo: toEndOfDay(filters.createdTo),
      page: filters.page ?? 0,
      size: filters.size ?? 20,
    })}`,
  );
}

export function exportServiceOrders(filters: ServiceOrderFilters = {}): Promise<Blob> {
  return requestBlob(
    `/service-orders/export${toQuery({
      search: filters.search,
      status: filters.status,
      clientId: filters.clientId,
      laboratoryAreaId: filters.laboratoryAreaId,
      plannedDeliveryFrom: toStartOfDay(filters.plannedDeliveryFrom),
      plannedDeliveryTo: toEndOfDay(filters.plannedDeliveryTo),
      createdFrom: toStartOfDay(filters.createdFrom),
      createdTo: toEndOfDay(filters.createdTo),
    })}`,
  );
}

export function getOrderContext(
  filters: ServiceOrderFilters = {},
): Promise<
  [
    PageResponse<ServiceOrderSummary>,
    PageResponse<Client>,
    PageResponse<Client>,
    PageResponse<Client>,
    CatalogSnapshot,
  ]
> {
  return Promise.all([
    getServiceOrders(filters),
    request<PageResponse<Client>>('/clients?page=0&size=100'),
    request<PageResponse<Client>>('/clients?active=true&participantType=CLIENT&page=0&size=100'),
    request<PageResponse<Client>>('/clients?active=true&participantType=PRODUCER&page=0&size=100'),
    request<CatalogSnapshot>('/catalogs'),
  ]);
}

export function getServiceOrder(orderId: string): Promise<ServiceOrderDetail> {
  return request<ServiceOrderDetail>(`/service-orders/${orderId}`);
}

export function getServiceOrderHistory(
  orderId: string,
  page = 0,
  size = 10,
): Promise<PageResponse<ServiceOrderHistoryEntry>> {
  return request<PageResponse<ServiceOrderHistoryEntry>>(
    `/service-orders/${orderId}/history${toQuery({ page, size })}`,
  );
}

export function getServiceOrderDocument(orderId: string): Promise<ServiceOrderDocument> {
  return request<ServiceOrderDocument>(`/service-orders/${orderId}/document`);
}
