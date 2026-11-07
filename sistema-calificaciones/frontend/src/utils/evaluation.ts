export const evaluationStatuses = {
  BORRADOR: 'Borrador', CONFIGURADA: 'Configurada', EN_PROCESO: 'En proceso',
  CERRADA: 'Cerrada', PUBLICADA: 'Publicada', ARCHIVADA: 'Archivada',
}
export function evaluationTypeLabel(type: string) {
  return ({ ADMISION: 'Admisión', CERTIFICACION: 'Certificación', SELECCION: 'Selección', GENERAL: 'General' } as Record<string, string>)[type] ?? type
}
export function resultLanguage(type?: string | null) {
  if (type === 'ADMISION') return { heading: 'Ingreso', positive: 'Ingresó', negative: 'No ingresó', people: 'Ingresantes', negativePeople: 'No ingresantes', policy: 'Regla de ingreso' }
  if (type === 'SELECCION') return { heading: 'Selección', positive: 'Seleccionado', negative: 'No seleccionado', people: 'Seleccionados', negativePeople: 'No seleccionados', policy: 'Criterio de selección' }
  return { heading: 'Aprobación', positive: 'Aprobó', negative: 'No aprobó', people: 'Aprobados', negativePeople: 'No aprobados', policy: 'Criterio de aprobación' }
}
export function reviewProgress(exams: number, observed: number) {
  return exams > 0 ? Math.floor((exams - observed) * 1000 / exams) / 10 : 0
}
