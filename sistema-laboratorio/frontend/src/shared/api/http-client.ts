const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1';
const TOKEN_KEY = 'labcore.token';

export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem(TOKEN_KEY);
  const headers = new Headers(options.headers);

  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(`${API_URL}${path}`, { ...options, headers });

  if (!response.ok) {
    const body = (await response.json().catch(() => ({}))) as {
      detail?: string;
      message?: string;
    };
    throw new ApiError(
      body.detail ?? body.message ?? 'No fue posible completar la operación.',
      response.status,
    );
  }

  if (response.status === 204 || response.status === 205) {
    return undefined as T;
  }

  const responseText = await response.text();
  if (!responseText.trim()) {
    return undefined as T;
  }

  return JSON.parse(responseText) as T;
}

export async function requestBlob(path: string, options: RequestInit = {}): Promise<Blob> {
  const token = localStorage.getItem(TOKEN_KEY);
  const headers = new Headers(options.headers);

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(`${API_URL}${path}`, { ...options, headers });

  if (!response.ok) {
    const body = (await response.json().catch(() => ({}))) as {
      detail?: string;
      message?: string;
    };
    throw new ApiError(
      body.detail ?? body.message ?? 'No fue posible completar la operación.',
      response.status,
    );
  }

  return response.blob();
}

export function toQuery(params: Record<string, string | number | undefined>): string {
  const searchParams = new URLSearchParams();

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') {
      searchParams.set(key, String(value));
    }
  });

  const query = searchParams.toString();
  return query ? `?${query}` : '';
}
