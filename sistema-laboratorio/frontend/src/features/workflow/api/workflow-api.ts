import { request } from '../../../shared/api/http-client';
import type { PageResponse, WorkOrderDetail } from '../../../shared/types/domain';

export type OperationType = 'SERVICE_ORDER' | 'WORK_ORDER' | 'RESULT';
export interface OperationVersion {
  id: string;
  revision: number;
  action: string;
  reason?: string;
  performedBy: string;
  performedByRole: string;
  capturedAt: string;
  snapshot: unknown;
}

export function getOperationVersions(type: OperationType, id: string, page: number) {
  return request<PageResponse<OperationVersion>>(`/operation-versions/${type}/${id}?page=${page}`);
}

export function returnOperation(endpoint: string, reason: string) {
  return request<void>(endpoint, { method: 'POST', body: JSON.stringify({ reason }) });
}

export function correctTechnicalAssignments(
  id: string,
  input: {
    reason: string;
    laboratoryDueAt: string;
    assignments: Array<{ testId: string; analystId: string }>;
  },
) {
  return request<WorkOrderDetail>(`/work-orders/${id}/correct-assignments`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}
