import type { ResultEntryStatus, WorkOrderTestStatus } from '../../../shared/types/domain';

export const resultEntryStatusLabels: Record<ResultEntryStatus, string> = {
  DRAFT: 'Evaluación en curso',
  RECORDED: 'Registrado',
  CORRECTION_REQUESTED: 'Corrección solicitada',
  CORRECTED: 'Corregido, pendiente de validación',
  VALIDATED: 'Validado',
  INVALIDATED: 'Invalidado por cambio técnico',
};

export const workOrderTestStatusLabels: Record<WorkOrderTestStatus, string> = {
  PENDING: 'Pendiente',
  RECORDED: 'Registrado',
  CORRECTION_REQUESTED: 'Corrección solicitada',
  CORRECTED: 'Corregido',
  VALIDATED: 'Validado',
  INVALIDATED: 'Invalidado por cambio técnico',
};
