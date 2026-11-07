export const workflowSections: Record<string, string> = {
  TEMAS: 'configuracion', CLAVES: 'configuracion', REGLA: 'configuracion', INSCRIPCIONES: 'participantes', GRUPOS: 'participantes',
  CIERRE: '', CONSULTA: 'publicacion', CASOS: 'revisiones', HOJAS: 'examenes', LOTES: 'importaciones',
  RESULTADOS: 'importaciones', VERSIONES: 'publicacion',
}
export function workflowLink(evaluation: number, code: string, currentSearch = '') {
  const section = workflowSections[code] ?? ''
  const search = new URLSearchParams(currentSearch)
  if (code === 'CASOS') { search.set('cases.status', 'PENDIENTES'); search.delete('cases.page') }
  if (code === 'HOJAS') { search.set('exams.status', 'OBSERVADA'); search.delete('exams.page') }
  if (code === 'GRUPOS') { search.set('participants.groupId', '0'); search.delete('participants.page') }
  const query = search.toString()
  return `/evaluaciones/${evaluation}${section ? `/${section}` : ''}${query ? `?${query}` : ''}`
}
