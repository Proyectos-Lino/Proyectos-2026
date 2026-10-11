import type { ServiceOrderHistoryAction, ServiceOrderStatus } from '../../../shared/types/domain';

export const serviceOrderStatusLabels: Record<ServiceOrderStatus, string> = {
  DRAFT: 'Borrador',
  IN_PROGRESS: 'En laboratorio',
  COMMERCIAL_CORRECTION: 'En corrección comercial',
  CORRECTION_REVIEW: 'Corrección en revisión',
  FINALIZED: 'Finalizada',
  CANCELLED: 'Anulada',
};

export const serviceOrderHistoryActionLabels: Record<ServiceOrderHistoryAction, string> = {
  CREATED: 'Orden registrada',
  COMMERCIAL_DETAILS_UPDATED: 'Datos comerciales actualizados',
  SAMPLES_UPDATED: 'Muestras y ensayos actualizados',
  COMMERCIAL_CORRECTION_REQUESTED: 'Devuelta a corrección comercial',
  COMMERCIAL_CORRECTION_SUBMITTED: 'Corrección enviada a revisión',
  COMMERCIAL_CORRECTION_APPROVED: 'Corrección aprobada',
  TECHNICAL_DATA_INVALIDATED: 'Datos técnicos invalidados',
  CONFIRMED: 'Enviada al laboratorio',
  PROCESSING_STARTED: 'Procesamiento iniciado (registro anterior)',
  FINALIZED: 'Proceso técnico finalizado',
  CANCELLED: 'Orden anulada',
};
