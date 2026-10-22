declare global {
  interface Window {
    __ODONTOCARE_CONFIG__?: { apiBaseUrl?: string }
  }
}

const configuredUrl =
  window.__ODONTOCARE_CONFIG__?.apiBaseUrl || import.meta.env.VITE_API_BASE_URL || ''
const baseUrl = configuredUrl.replace(/\/+$/, '')

if (baseUrl) {
  const url = new URL(baseUrl)
  if (
    !['https:', 'http:'].includes(url.protocol) ||
    url.username ||
    url.password ||
    url.pathname !== '/' ||
    url.search ||
    url.hash
  ) {
    throw new Error('La dirección pública de la API no es válida.')
  }
}

export function apiUrl(path: string) {
  if (!path.startsWith('/api/')) throw new Error('La ruta de la API no es válida.')
  return baseUrl + path
}
