import { request, requestBlob, toQuery } from '../../../shared/api/http-client';
import type {
  LaboratoryReportDetail,
  LaboratoryReportHistoryEntry,
  LaboratoryReportStatus,
  LaboratoryReportSummary,
  PageResponse,
  ReportPreparationCandidate,
} from '../../../shared/types/domain';

export function getReportPreparationCandidates(): Promise<ReportPreparationCandidate[]> {
  return request<ReportPreparationCandidate[]>('/reports/preparation-candidates');
}

export interface ReportFilters {
  search?: string;
  status?: LaboratoryReportStatus | '';
  serviceOrderId?: string;
  issuedFrom?: string;
  issuedTo?: string;
  laboratoryAreaId?: string;
  page?: number;
  size?: number;
}

export function getReports(
  filters: ReportFilters = {},
): Promise<PageResponse<LaboratoryReportSummary>> {
  return request<PageResponse<LaboratoryReportSummary>>(
    `/reports${toQuery({
      search: filters.search,
      status: filters.status,
      serviceOrderId: filters.serviceOrderId,
      issuedFrom: filters.issuedFrom,
      issuedTo: filters.issuedTo,
      laboratoryAreaId: filters.laboratoryAreaId,
      page: filters.page ?? 0,
      size: filters.size ?? 20,
    })}`,
  );
}

export function getReport(reportId: string): Promise<LaboratoryReportDetail> {
  return request<LaboratoryReportDetail>(`/reports/${reportId}`);
}

export function getReportHistory(
  reportId: string,
): Promise<PageResponse<LaboratoryReportHistoryEntry>> {
  return request<PageResponse<LaboratoryReportHistoryEntry>>(
    `/reports/${reportId}/history?page=0&size=100`,
  );
}

export function createReportDraft(serviceOrderId: string): Promise<LaboratoryReportDetail> {
  return request<LaboratoryReportDetail>(`/reports/service-orders/${serviceOrderId}`, {
    method: 'POST',
  });
}

export function issueReport(reportId: string): Promise<LaboratoryReportDetail> {
  return request<LaboratoryReportDetail>(`/reports/${reportId}/issue`, { method: 'POST' });
}

export function cancelReport(reportId: string, reason: string): Promise<LaboratoryReportDetail> {
  return request<LaboratoryReportDetail>(`/reports/${reportId}/cancel`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
}

export function reissueReport(reportId: string): Promise<LaboratoryReportDetail> {
  return request<LaboratoryReportDetail>(`/reports/${reportId}/reissue`, { method: 'POST' });
}

export function getReportPdf(reportId: string): Promise<Blob> {
  return requestBlob(`/reports/${reportId}/pdf`);
}

export function exportReports(filters: ReportFilters): Promise<Blob> {
  return requestBlob(
    `/reports/export${toQuery({
      search: filters.search,
      status: filters.status,
      serviceOrderId: filters.serviceOrderId,
      issuedFrom: filters.issuedFrom,
      issuedTo: filters.issuedTo,
      laboratoryAreaId: filters.laboratoryAreaId,
    })}`,
  );
}
