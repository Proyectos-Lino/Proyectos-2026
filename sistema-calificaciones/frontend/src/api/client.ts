const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

interface ErrorBody {
  code?: string
  message?: string
  fields?: Record<string, string>
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fields: Record<string, string>
  constructor(message: string, status: number, code: string, fields: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.code = code
    this.fields = fields
  }
}

let csrf: { headerName: string; token: string } | null = null

export async function refreshCsrf() {
  const response = await fetch(`${baseUrl}/api/v1/session/csrf`, { credentials: 'include' })
  if (!response.ok) throw new ApiError('No se pudo iniciar una sesión segura.', response.status, 'CSRF_ERROR')
  csrf = await response.json() as { headerName: string; token: string }
}

async function parseError(response: Response): Promise<ApiError> {
  let body: ErrorBody = {}
  try { body = await response.json() as ErrorBody } catch { /* La API puede devolver una respuesta vacía. */ }
  return new ApiError(body.message || 'No se pudo completar la operación.', response.status,
    body.code || 'HTTP_ERROR', body.fields || {})
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const write = !['GET', 'HEAD'].includes(method)
  if (write && !csrf) await refreshCsrf()
  const headers = new Headers(init.headers)
  if (write && csrf) headers.set(csrf.headerName, csrf.token)
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  let response: Response
  try {
    response = await fetch(`${baseUrl}/api/v1${path}`, { ...init, method, headers, credentials: 'include' })
  } catch {
    throw new ApiError('No se pudo conectar con el servidor. Comprueba que el backend esté iniciado.', 0, 'NETWORK_ERROR')
  }
  if (!response.ok) throw await parseError(response)
  if (write && /\/evaluaciones\/\d+(\/|$)/.test(path) && !/\/(vista-previa|columnas)(\?|$)/.test(path)) {
    window.dispatchEvent(new CustomEvent('califica:evaluation-changed', { detail: path }))
  }
  if (response.status === 204) return undefined as T
  if (response.headers.get('content-type')?.includes('application/json')) return response.json() as Promise<T>
  return response.blob() as Promise<T>
}

export function params(values: Record<string, string | number | null | undefined>) {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(values)) {
    if (value !== null && value !== undefined && value !== '') query.set(key, String(value))
  }
  return query.toString()
}

export function clearCsrf() { csrf = null }
