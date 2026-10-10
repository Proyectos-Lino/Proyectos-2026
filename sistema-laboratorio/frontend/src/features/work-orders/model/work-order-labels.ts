import type {
  WorkOrderHistoryAction,
  WorkOrderStatus,
  WorkOrderTestStatus,
} from '../../../shared/types/domain';

export const workOrderStatusLabels: Record<WorkOrderStatus, string> = {
  PENDING: 'En espera',
  IN_PROGRESS: 'En ejecución',
  RESULTS_RECORDED: 'Resultados registrados',
  VALIDATED: 'Validada',
  COMMERCIAL_CORRECTION: 'Pausada por corrección comercial',
  CLOSED: 'Cerrada',
  CANCELLED: 'Anulada',
};

export const workOrderTestStatusLabels: Record<WorkOrderTestStatus, string> = {
  PENDING: 'Pendiente',
  RECORDED: 'Registrado',
  CORRECTION_REQUESTED: 'Corrección solicitada',
  CORRECTED: 'Corregido',
  VALIDATED: 'Validado',
  INVALIDATED: 'Invalidado por corrección',
};

export const workOrderHistoryActionLabels: Record<WorkOrderHistoryAction, string> = {
  CREATED: 'Orden técnica emitida',
  SAMPLES_CLASSIFIED: 'Clasificación técnica confirmada',
  STATUS_UPDATED: 'Estado actualizado',
  RESULT_RECORDED: 'Resultado registrado',
  RESULT_CORRECTED: 'Resultado corregido',
  RESULT_CORRECTION_REQUESTED: 'Corrección solicitada',
  RESULT_RESUBMITTED: 'Resultado corregido y reenviado',
  RESULT_CORRECTED_AND_VALIDATED: 'Resultado corregido y validado',
  RESULT_VALIDATED: 'Resultado validado',
  COMMERCIAL_CORRECTION_PAUSED: 'Operación pausada por corrección comercial',
  COMMERCIAL_CORRECTION_RESUMED: 'Operación reanudada',
  TECHNICAL_DATA_INVALIDATED: 'Datos técnicos invalidados',
  CANCELLED: 'Orden técnica anulada',
  CLOSED: 'Orden técnica cerrada',
};
