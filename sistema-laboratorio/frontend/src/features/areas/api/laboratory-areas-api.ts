import { request, toQuery } from '../../../shared/api/http-client';
import type {
  LaboratoryArea,
  LaboratoryAreaOption,
  PageResponse,
} from '../../../shared/types/domain';

export interface AreaInput {
  code: string;
  name: string;
}

export interface AreasQuery {
  active?: boolean;
  page?: number;
  search?: string;
  size?: number;
}

export function getAreas(params: AreasQuery = {}) {
  return request<PageResponse<LaboratoryArea>>(
    `/laboratory-areas${toQuery({
      search: params.search,
      active: params.active === undefined ? undefined : String(params.active),
      page: params.page ?? 0,
      size: params.size ?? 10,
    })}`,
  );
}

export function getActiveAreaOptions(): Promise<LaboratoryAreaOption[]> {
  return request<LaboratoryAreaOption[]>('/laboratory-areas/options');
}

export function createArea(input: AreaInput): Promise<LaboratoryArea> {
  return request<LaboratoryArea>('/laboratory-areas', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function updateArea(areaId: string, input: AreaInput): Promise<LaboratoryArea> {
  return request<LaboratoryArea>(`/laboratory-areas/${areaId}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}

export function updateAreaStatus(areaId: string, active: boolean): Promise<LaboratoryArea> {
  return request<LaboratoryArea>(`/laboratory-areas/${areaId}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  });
}
