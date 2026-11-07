import type { Evaluation } from '../api/types'

export const groupLabels: Record<Evaluation['groupType'], string> = {
  GRUPO: 'Grupo', CARRERA: 'Carrera', SEDE: 'Sede', CATEGORIA: 'Categoría', PROGRAMA: 'Programa',
}

export function groupLabel(type: Evaluation['groupType'] | undefined) {
  return groupLabels[type ?? 'GRUPO']
}
