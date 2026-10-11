import { request, toQuery } from '../../../shared/api/http-client';
import type { PageResponse, RoleCode, UserSummary } from '../../../shared/types/domain';

export interface CreateUserInput {
  fullName: string;
  email: string;
  password: string;
  roles: RoleCode[];
  laboratoryAreaId?: string;
}

export interface UpdateUserInput extends Omit<CreateUserInput, 'password'> {
  password?: string;
}

export interface UsersQuery {
  active?: boolean;
  page?: number;
  role?: RoleCode;
  search?: string;
  size?: number;
}

export function createUser(input: CreateUserInput): Promise<UserSummary> {
  return request<UserSummary>('/users', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function updateUser(userId: string, input: UpdateUserInput): Promise<UserSummary> {
  return request<UserSummary>(`/users/${userId}`, { method: 'PUT', body: JSON.stringify(input) });
}

export function updateUserStatus(userId: string, active: boolean): Promise<UserSummary> {
  return request<UserSummary>(`/users/${userId}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  });
}

export function getUsers(params: UsersQuery = {}): Promise<PageResponse<UserSummary>> {
  return request<PageResponse<UserSummary>>(
    `/users${toQuery({
      search: params.search,
      active: params.active === undefined ? undefined : String(params.active),
      role: params.role,
      page: params.page ?? 0,
      size: params.size ?? 10,
    })}`,
  );
}
