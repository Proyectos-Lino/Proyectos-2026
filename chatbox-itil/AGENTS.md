# Mesa de Ayuda ITIL — Reglas del proyecto

## Contexto
Sistema de mesa de ayuda interna (Service Desk) basado en procesos ITIL,
inspirado en la tesis de Yauricasa Rodríguez sobre chatbot ITIL en SUNARP,
pero adaptado por requerimiento docente a un enfoque INTERNO:
- Lo usa el personal de soporte (agentes), NO el ciudadano.
- El chatbox es la única puerta de entrada: sirve tanto para consultar la
  base de conocimiento como para registrar y buscar incidencias similares.

## Modelo de datos obligatorio (ticket de incidencia)
- id, categoria, equipoAsignado (mesa_de_ayuda | area_tecnica | supervisor | otro)
- reportadoPor, atendidoPor, turno (mañana | tarde | noche, configurable)
- bitacora: lista de acciones {autor, texto, fecha, minutosInvertidos} — NUNCA se sobrescribe, solo se agrega
- estado: abierto | en_progreso | derivado | cerrado
- tfrMinutos, ttrMinutos, resueltoEnPrimerContacto (se calculan solos, nunca a mano)

## Conservación de Historial y Trazabilidad (requisito no negociable)
Cualquier agente debe poder abrir cualquier ticket de su equipo/cola y ver: qué se intentó, qué funcionó, qué no, y cuánto tiempo tomó cada acción. Ningún dato de la bitácora se pierde ni se resume con IA: se muestra el historial real. El sistema opera de manera unificada sin un flujo estricto de pase de turnos en la UI, delegando la trazabilidad de tiempos y autores directamente a la bitácora.

## Portabilidad (requisito no negociable)
El motor NUNCA debe tener texto, reglas o lógica específica de una
institución. Todo el contenido (categorías, respuestas, requisitos,
tasas) vive en un único archivo `config/knowledge-base.json`. Cambiar de
institución (SUNARP -> Indecopi -> cualquier otra) = reemplazar ese
archivo. Cero cambios de código.

## Restricciones de estilo (no negociables)
- Prohibido usar emojis en cualquier respuesta del sistema, commit,
  código, comentario o interfaz.
- Sin frameworks obligatorios: backend en Node.js con módulos nativos
  (sin dependencias de compilación); frontend sin build step.
- Persistencia detrás de un patrón Storage Adapter (interfaz), nunca
  acoplada a una base de datos concreta.

## Objetivo de empaquetado
El código y la documentación deben quedar en condición de registrarse
como software (propiedad intelectual) ante Indecopi: licencia clara,
autoría, versión, README completo, sin dependencias de terceros con
licencias incompatibles.
