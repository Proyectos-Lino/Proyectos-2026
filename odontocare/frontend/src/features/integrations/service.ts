import { request, saveJson } from '../../shared/api/http'
import type { IntegrationForm, IntegrationSettings } from './model'
const endpoint = '/api/v1/settings/integrations'
export const integrations = {
  get: (signal?: AbortSignal) => request<IntegrationSettings>(endpoint, { signal }),
  save: (version: number, form: IntegrationForm) =>
    saveJson<IntegrationSettings>(
      endpoint,
      {
        ...form,
        version,
        participants: form.participants.split(/[\s,;]+/).filter(Boolean),
      },
      'PUT',
    ),
  check: (provider: 'kapso' | 'groq', version: number) =>
    saveJson<{ success: boolean; message: string }>(`${endpoint}/check/${provider}`, { version }),
}
