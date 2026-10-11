import { request } from '../../../shared/api/http-client';
import type { TokenResponse } from '../../../shared/types/domain';

interface LoginCredentials {
  email: string;
  password: string;
}

export function authenticate(credentials: LoginCredentials): Promise<TokenResponse> {
  return request<TokenResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify(credentials),
  });
}
