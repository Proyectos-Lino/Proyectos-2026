import { request } from '../../../shared/api/http-client';
import type { DashboardSummary } from '../../../shared/types/domain';

export interface DashboardFilters {
  from: string;
  to: string;
  laboratoryAreaId: string;
}

export function getDashboardData(filters?: DashboardFilters): Promise<DashboardSummary> {
  const params = new URLSearchParams();

  if (filters?.from) params.set('from', filters.from);
  if (filters?.to) params.set('to', filters.to);
  if (filters?.laboratoryAreaId) params.set('laboratoryAreaId', filters.laboratoryAreaId);

  const query = params.toString();
  return request<DashboardSummary>(`/dashboard/summary${query ? `?${query}` : ''}`);
}
