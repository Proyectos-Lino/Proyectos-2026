import type { RoleCode } from '../types/domain';

export const roleLabels: Record<RoleCode, string> = {
  ADMIN: 'Administrador',
  COMMERCIAL: 'Atención comercial',
  TECHNICAL_MANAGER: 'Responsable técnico',
  ANALYST: 'Analista',
  VALIDATOR: 'Validador',
};

export const resultModeLabels = {
  SCALAR: 'Escalar',
  REPEATABLE: 'Varias lecturas',
  MATRIX: 'Matriz',
} as const;

export const resultTypeLabels = {
  NUMERIC: 'Numérico',
  TEXT: 'Texto',
  QUALITATIVE: 'Cualitativo',
} as const;

export const fieldKindLabels = {
  CAPTURED: 'Capturado',
  CALCULATED: 'Calculado',
  INFORMATIONAL: 'Informativo',
} as const;

export const documentTypeLabels = {
  RUC: 'RUC',
  DNI: 'DNI',
  PASSPORT: 'Pasaporte',
  OTHER: 'Otro documento',
} as const;

export const auditActionLabels: Record<string, string> = {
  CREATED: 'Creación',
  UPDATED: 'Actualización',
  CONFIRMED: 'Confirmación',
  COMMERCIAL_DETAILS_UPDATED: 'Datos comerciales actualizados',
  SAMPLES_UPDATED: 'Muestras actualizadas',
  COMMERCIAL_CORRECTION_REQUESTED: 'Devuelta a corrección comercial',
  COMMERCIAL_CORRECTION_SUBMITTED: 'Corrección enviada a revisión',
  COMMERCIAL_CORRECTION_APPROVED: 'Corrección aprobada',
  COMMERCIAL_CORRECTION_PAUSED: 'Operación pausada por corrección comercial',
  COMMERCIAL_CORRECTION_RESUMED: 'Operación reanudada tras corrección comercial',
  TECHNICAL_DATA_INVALIDATED: 'Datos técnicos invalidados',
  PROCESSING_STARTED: 'Procesamiento iniciado',
  FINALIZED: 'Finalización',
  CANCELLED: 'Anulación',
  ISSUED: 'Emisión',
  REISSUED: 'Reemisión',
  RESULT_CORRECTED: 'Resultado corregido',
  SAMPLES_CLASSIFIED: 'Muestras clasificadas',
  STATUS_UPDATED: 'Estado actualizado',
  RESULT_CORRECTION_REQUESTED: 'Corrección solicitada',
  RESULT_RESUBMITTED: 'Resultado corregido y reenviado',
  MATRIX_CORRECTED: 'Matriz corregida',
  RESULT_RECORDED: 'Resultado registrado',
  RESULT_REVISED: 'Resultado corregido',
  RESULT_REJECTED: 'Resultado rechazado',
  RESULT_VALIDATED: 'Resultado validado',
  RESULT_CORRECTED_AND_VALIDATED: 'Resultado corregido y validado',
  RESULT_RECALCULATED: 'Resultado recalculado',
  REGISTERED: 'Registro de resultado',
  CORRECTED_AND_VALIDATED: 'Corrección y validación',
  REPORT_PREPARED: 'Informe preparado',
  REPORT_ISSUED: 'Informe emitido',
  REPORT_CANCELLED: 'Informe anulado',
  REPORT_REISSUED: 'Informe reemitido',
  REPORT_VIEWED: 'Informe consultado',
  REPORT_DOWNLOADED: 'Informe descargado',
  PROFILE_UPDATED: 'Configuración actualizada',
};

const systemCodeLabels: Record<string, string> = {
  ...auditActionLabels,
  ...roleLabels,
  ...resultModeLabels,
  ...resultTypeLabels,
  ...fieldKindLabels,
  DRAFT: 'Borrador',
  PENDING: 'Pendiente',
  ASSIGNED: 'Asignado',
  COMPLETED: 'Completado',
  IN_PROGRESS: 'En proceso',
  RECORDED: 'Registrado',
  CORRECTION_REQUESTED: 'Corrección solicitada',
  COMMERCIAL_CORRECTION: 'En corrección comercial',
  CORRECTION_REVIEW: 'Corrección en revisión técnica',
  CORRECTED: 'Corregido',
  RESULTS_RECORDED: 'Resultados registrados',
  VALIDATED: 'Validado',
  INVALIDATED: 'Invalidado',
  FINALIZED: 'Finalizado',
  CLOSED: 'Cerrado',
  CANCELLED: 'Anulado',
  ISSUED: 'Emitido',
  REISSUED: 'Reemitido',
  APPROVED: 'Aprobado',
  CLIENT: 'Cliente',
  PRODUCER: 'Productor',
  BOTH: 'Cliente y productor',
  EMAIL: 'Correo electrónico',
  PHONE: 'Teléfono',
  PICKUP: 'Recojo',
};

export function labelForAction(action: string) {
  return auditActionLabels[action] ?? labelForSystemCode(action);
}

export function labelForSystemCode(code?: string) {
  if (!code) return '';
  return systemCodeLabels[code] ?? sentenceCase(code);
}

export function localizeSystemText(text?: string) {
  if (!text) return text ?? '';
  return Object.entries(systemCodeLabels)
    .sort(([first], [second]) => second.length - first.length)
    .reduce(
      (localized, [code, label]) => localized.replace(new RegExp(`\\b${code}\\b`, 'g'), label),
      text,
    );
}

function sentenceCase(code: string) {
  const normalized = code.replaceAll('_', ' ').toLocaleLowerCase('es-PE');
  return normalized.charAt(0).toLocaleUpperCase('es-PE') + normalized.slice(1);
}
