import { evaluationStatuses } from './evaluation'

export const numberFormatter = new Intl.NumberFormat('es-PE')
export const scoreFormatter = new Intl.NumberFormat('es-PE', { maximumFractionDigits: 3 })

export function formatDate(date: string | null) {
  if (!date) return 'Sin fecha'
  return new Intl.DateTimeFormat('es-PE', { day: '2-digit', month: 'short', year: 'numeric', timeZone: 'UTC' })
    .format(new Date(`${date}T12:00:00Z`))
}

export function labelStatus(status: string) {
  return ({
    ...evaluationStatuses,
    RECIBIDA: 'Recibida', OBSERVADA: 'Observada', VALIDADA: 'Válida', ANULADA: 'Anulada',
    ABIERTA: 'Pendiente', EN_REVISION: 'En revisión', APROBADA: 'Aprobada', RECHAZADA: 'Rechazada', COMPLETADO: 'Completado',
    COMPLETADO_CON_ERRORES: 'Completado con observaciones', PROCESANDO: 'Procesando',
    ADMINISTRADOR: 'Administrador', OPERADOR: 'Operador', REVISOR: 'Revisor', LECTOR: 'Lector',
    ACTIVO: 'Activo', INACTIVO: 'Sin acceso',
  } as Record<string, string>)[status] ?? status
}

export function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Ocurrió un error inesperado.'
}

export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
