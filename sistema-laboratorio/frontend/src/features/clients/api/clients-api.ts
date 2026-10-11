import { request, requestBlob, toQuery } from '../../../shared/api/http-client';
import type {
  Client,
  DocumentType,
  PageResponse,
  ParticipantType,
  ServiceOrderSummary,
} from '../../../shared/types/domain';

export interface CreateClientInput {
  participantType: ParticipantType;
  documentType: DocumentType;
  documentNumber: string;
  legalName: string;
  reportName: string;
  email: string;
  address: string;
  contactName: string;
  phone: string;
}

export interface ClientFilters {
  search?: string;
  documentType?: DocumentType | '';
  participantType?: ParticipantType | '';
  active?: 'true' | 'false' | '';
  page?: number;
  size?: number;
}

export function getClients(filters: ClientFilters = {}): Promise<PageResponse<Client>> {
  return request<PageResponse<Client>>(
    `/clients${toQuery({
      search: filters.search,
      documentType: filters.documentType,
      participantType: filters.participantType,
      active: filters.active,
      page: filters.page ?? 0,
      size: filters.size ?? 20,
    })}`,
  );
}

export function exportClients(filters: ClientFilters = {}): Promise<Blob> {
  return requestBlob(
    `/clients/export${toQuery({
      search: filters.search,
      documentType: filters.documentType,
      participantType: filters.participantType,
      active: filters.active,
    })}`,
  );
}

export interface ProducerSampleHistory {
  orderId: string;
  orderCode: string;
  orderStatus: ServiceOrderSummary['status'];
  orderCreatedAt: string;
  sampleId: string;
  commercialSequence: number;
  clientIdentification?: string;
  productName?: string;
  varietyName?: string;
}

export function getProducerSamples(
  participantId: string,
  page = 0,
): Promise<PageResponse<ProducerSampleHistory>> {
  return request(`/clients/${participantId}/producer-samples${toQuery({ page, size: 10 })}`);
}

export function createClient(input: CreateClientInput): Promise<Client> {
  return request<Client>('/clients', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function updateClient(clientId: string, input: CreateClientInput): Promise<Client> {
  return request<Client>(`/clients/${clientId}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}

export function updateClientStatus(clientId: string, active: boolean): Promise<Client> {
  return request<Client>(`/clients/${clientId}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  });
}

export function getClientOrders(
  clientId: string,
  page = 0,
): Promise<PageResponse<ServiceOrderSummary>> {
  return request(`/clients/${clientId}/orders${toQuery({ page, size: 10 })}`);
}
