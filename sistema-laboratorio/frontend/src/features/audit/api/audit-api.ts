import { request, requestBlob, toQuery } from '../../../shared/api/http-client';
import type { AuditLogEntry, PageResponse } from '../../../shared/types/domain';

export interface AuditFilters {
  module?: string;
  actor?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

function startOfDay(value?: string) {
  return value ? new Date(`${value}T00:00:00`).toISOString() : undefined;
}

function endOfDay(value?: string) {
  return value ? new Date(`${value}T23:59:59.999`).toISOString() : undefined;
}

export function getAuditLog(filters: AuditFilters = {}): Promise<PageResponse<AuditLogEntry>> {
  return request<PageResponse<AuditLogEntry>>(
    `/audit-log${toQuery({
      module: filters.module,
      actor: filters.actor,
      from: startOfDay(filters.from),
      to: endOfDay(filters.to),
      page: filters.page ?? 0,
      size: filters.size ?? 20,
    })}`,
  );
}

export function exportAuditLog(filters: AuditFilters = {}): Promise<Blob> {
  return requestBlob(
    `/audit-log/export${toQuery({
      module: filters.module,
      actor: filters.actor,
      from: startOfDay(filters.from),
      to: endOfDay(filters.to),
    })}`,
  );
}
