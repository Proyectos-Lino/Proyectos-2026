import { api, params } from './client'
import type { Batch, DbfColumnMapping, DbfInspection, DbfPreview, ImportSummary, Page } from './types'

const root = (org: number, evaluationId: number) =>
  `/organizaciones/${org}/evaluaciones/${evaluationId}/importaciones`

export interface DbfFiles { identifi: File; respuest: File; claves: File }
export interface ImportOptions {
  questions: number; options: number; correct: number; incorrect: number
  blank: number; passingScore: number | null
}
export interface DbfPreviewOptions extends ImportOptions { mode: 'GUIADA' | 'CONFIGURADA' }

function previewBody(files: DbfFiles, mapping: DbfColumnMapping, options: DbfPreviewOptions) {
  const body = filesBody(files)
  body.append('mapping', new Blob([JSON.stringify(mapping)], { type: 'application/json' }))
  body.append('options', new Blob([JSON.stringify(options)], { type: 'application/json' }))
  return body
}

function filesBody(files: DbfFiles) {
  const body = new FormData()
  body.append('identifi', files.identifi)
  body.append('respuest', files.respuest)
  body.append('claves', files.claves)
  return body
}

export const importsApi = {
  page: (org: number, evaluationId: number, options: { query: string; status: string; page: number; size: number }) =>
    api<Page<Batch>>(`${root(org, evaluationId)}/pagina?${params(options)}`),
  inspect: (org: number, evaluationId: number, files: DbfFiles) =>
    api<DbfInspection>(`${root(org, evaluationId)}/columnas`, { method: 'POST', body: filesBody(files) }),
  preview: (org: number, evaluationId: number, files: DbfFiles, mapping: DbfColumnMapping,
    options: DbfPreviewOptions, filter: string, page: number, size = 20) =>
    api<DbfPreview>(`${root(org, evaluationId)}/vista-previa?${params({ filter, page, size })}`,
      { method: 'POST', body: previewBody(files, mapping, options) }),
  confirm: (org: number, evaluationId: number, files: DbfFiles, mapping: DbfColumnMapping,
    options: DbfPreviewOptions, fingerprint: string) => {
    const body = previewBody(files, mapping, options)
    body.append('fingerprint', fingerprint)
    return api<ImportSummary>(`${root(org, evaluationId)}/confirmar`, { method: 'POST', body })
  },
}
