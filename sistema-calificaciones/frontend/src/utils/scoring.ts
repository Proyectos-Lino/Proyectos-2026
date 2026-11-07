import type { RankingPolicy } from '../api/types'

export const approvalLabels: Record<RankingPolicy['approvalMode'], string> = {
  NINGUNA: 'Solo ranking, sin decisión automática',
  MINIMA: 'Nota mínima',
  VACANTES: 'Vacantes',
  AMBAS: 'Nota mínima y vacantes',
  VACANTES_O_MINIMA: 'Vacantes si hay cupo; si no, nota mínima',
}

export const tieLabels: Record<RankingPolicy['tieBreak'], string> = {
  COMPARTIR_PUESTO: 'Compartir puesto si hay empate',
  CORRECTAS: 'Más respuestas correctas',
  MENOS_INCORRECTAS: 'Menos respuestas incorrectas',
  CODIGO: 'Código del participante',
}

export const scopeLabels: Record<RankingPolicy['scope'], string> = {
  GLOBAL: 'Toda la evaluación', GRUPO: 'Por grupo',
}
