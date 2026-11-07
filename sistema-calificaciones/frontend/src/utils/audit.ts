const actions: Record<string, { title: string; description: string; section: string }> = {
  PARTICIPANTE_INSCRITO: { title: 'Participante inscrito', description: 'Registró el código y grupo de una persona en esta evaluación.', section: 'participantes' },
  PARTICIPANTE_ACTUALIZADO: { title: 'Participante actualizado', description: 'Actualizó los datos o la asignación de una inscripción.', section: 'participantes' },
  PARTICIPANTE_IMPORTADO: { title: 'Inscripción procesada desde CSV', description: 'Registró o completó una inscripción y su grupo desde un archivo validado.', section: 'participantes' },
  INSCRIPCIONES_IMPORTADAS: { title: 'Carga de participantes aplicada', description: 'Aplicó las inscripciones y asignaciones del CSV y actualizó el ranking.', section: 'participantes' },
  REVISION_RESUELTA: { title: 'Caso resuelto', description: 'Registró la decisión de una revisión.', section: 'revisiones' },
  REVISION_ABIERTA: { title: 'Revisión abierta', description: 'Registró un caso para revisar el examen.', section: 'revisiones' },
  REVISION_ASIGNADA: { title: 'Responsable actualizado', description: 'Cambió la asignación de una revisión.', section: 'revisiones' },
  EVIDENCIA_ADJUNTADA: { title: 'Evidencia registrada', description: 'Adjuntó una imagen a una revisión.', section: 'revisiones' },
  EVIDENCIA_ELIMINADA: { title: 'Evidencia eliminada', description: 'Retiró una imagen de una revisión.', section: 'revisiones' },
  EXAMEN_ANULADO: { title: 'Examen anulado', description: 'Registró la anulación de un examen.', section: 'examenes' },
  ANULACION_REVERTIDA: { title: 'Anulación revertida', description: 'Restableció un examen y su participación en los resultados.', section: 'examenes' },
  RESULTADOS_RECALCULADOS: { title: 'Resultados recalculados', description: 'Generó nuevas versiones con la configuración vigente.', section: 'examenes' },
  CLAVE_CREADA: { title: 'Clave actualizada', description: 'Guardó una nueva versión de clave.', section: 'configuracion' },
  CLAVE_IMPORTADA: { title: 'Clave importada', description: 'Registró las respuestas correctas de un tema desde el archivo DBF.', section: 'configuracion' },
  LOTE_IMPORTADO: { title: 'Archivos DBF procesados', description: 'Importó un lote de hojas y generó sus resultados y observaciones.', section: 'importaciones' },
  MOTIVO_ACTUALIZADO: { title: 'Motivo actualizado', description: 'Actualizó el motivo registrado en el examen.', section: 'examenes' },
  REGLA_CREADA: { title: 'Regla actualizada', description: 'Guardó una nueva regla de calificación.', section: 'configuracion' },
  POLITICA_CREADA: { title: 'Criterios actualizados', description: 'Actualizó el ordenamiento y el criterio de resultado.', section: 'configuracion' },
  SECCIONES_CREADAS: { title: 'Secciones actualizadas', description: 'Guardó rangos y puntajes del tema.', section: 'configuracion' },
  ESTADO_CAMBIADO: { title: 'Estado actualizado', description: 'Cambió la etapa de la evaluación.', section: '' },
  PUBLICACION_CONFIGURADA: { title: 'Consulta configurada', description: 'Definió los datos y el periodo de consulta.', section: 'publicacion' },
  RESULTADOS_APROBADOS: { title: 'Resultados aprobados', description: 'Confirmó la revisión y bloqueó los resultados.', section: 'publicacion' },
  RESULTADOS_PUBLICADOS: { title: 'Resultados publicados', description: 'Habilitó la consulta individual.', section: 'publicacion' },
  ACCESOS_GENERADOS: { title: 'Accesos generados', description: 'Generó códigos individuales de consulta.', section: 'publicacion' },
}
export function auditDescription(action: string) {
  return actions[action] ?? { title: action.replaceAll('_', ' ').toLowerCase(), description: 'Registró una acción en la evaluación.', section: '' }
}

export function auditParticipantCode(event: { action: string; details: string }): string | null {
  if (!event.action.startsWith('PARTICIPANTE_')) return null
  try {
    const value: unknown = (JSON.parse(event.details) as { codigo?: unknown }).codigo
    return typeof value === 'string' && value.length <= 80 ? value : null
  } catch { return null }
}
