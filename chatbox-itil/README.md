# Mesa de Ayuda ITIL con Asistente de IA

## Descripcion General
Sistema de Mesa de Ayuda interna (Service Desk) estructurado bajo los lineamientos y buenas practicas del marco de trabajo ITIL. Este sistema esta disenado exclusivamente para el uso del personal de soporte tecnico e incorpora un Motor de Asistencia basado en Inteligencia Artificial Generativa.

El diseno arquitectonico se inspira conceptualmente en la investigacion "Chatbot ITIL en SUNARP" de Yauricasa Rodriguez, pero con una adaptacion orientada al cliente interno (Agentes de Nivel 1 y 2). El asistente de IA actua como soporte cognitivo del agente, permitiendo consultas rapidas a la base de conocimiento institucional y sugiriendo flujos de resolucion o derivacion.

## Caracteristicas Principales
1. **Gestion de Incidencias ITIL**: Seguimiento completo del ciclo de vida del ticket (Abierto, En Progreso, Derivado, Cerrado).
2. **Calculo de Metricas de Rendimiento**: Medicion automatizada de los indicadores clave de rendimiento (KPIs) requeridos por ITIL: TFR (Time to First Response), TTR (Time to Resolve) y FCR (First Contact Resolution).
3. **Parametrizacion Dinamica**: Configuracion inicial adaptable para establecer horarios (un unico horario o multiples turnos) y los niveles de soporte o areas a las que se podran derivar los casos, segun las necesidades de la organizacion.
4. **Flexibilidad de Atencion (Suplencias)**: Permite que si un tecnico no se encuentra disponible, un companero o suplente pueda gestionar y atender el ticket desde su propia sesion y cuenta, manteniendo intacta la trazabilidad del caso.
5. **Motor de IA Desacoplado**: Asistente tecnico cognitivo para resolucion en primer contacto (FCR) impulsado por modelos generativos.
6. **Analisis de Imagenes con IA**: Capacidad avanzada del asistente cognitivo para procesar imagenes de fallas tecnicas subidas al sistema y proponer multiples alternativas de solucion estructuradas directamente en el chat.
7. **Portabilidad Total y Generalizacion**: El sistema es completamente generico y adaptable a cualquier empresa o institucion. El dominio de negocio y las politicas institucionales estan abstraidas en un unico archivo de configuracion (`data/knowledge-base.json`), eliminando cualquier dato estricto o fijo de un solo negocio y permitiendo la migracion del sistema sin alterar el codigo fuente.

## Stack Tecnologico (Sin Dependencias de Compilacion)
Para garantizar la sostenibilidad a largo plazo, el proyecto se construyo evitando herramientas de compilacion que generan deuda tecnica:
- **Frontend**: HTML5, CSS3 (Vanilla), y JavaScript (ES6). No requiere Node.js para transpilar ni empaquetar archivos estaticos. Diseno basado en CSS Grid.
- **Backend**: Node.js puro, exponiendo una API REST. Uso estricto de modulos nativos y persistencia en formato JSON (patron Storage Adapter) facilmente migrable a motores SQL/NoSQL si la escalabilidad lo exige.

## Modelo de Datos Restrictivo
Cada incidencia respeta estrictamente el siguiente esquema de campos:
- `id`: Identificador unico del caso.
- `categoria`: Tipificacion del incidente o solicitud.
- `equipoAsignado`: Controla la escalacion de niveles (`mesa_de_ayuda`, `area_tecnica`, `supervisor`, `otro`).
- `reportadoPor`, `atendidoPor`, `turno`: Trazabilidad del usuario afectado y el tecnico responsable.
- `estado`: Estado actual del flujo de trabajo (`abierto`, `en_progreso`, `derivado`, `cerrado`).
- `bitacora`: Registro cronologico e inmutable de acciones. Solo permite anexar informacion (`autor`, `texto`, `fecha`, `minutosInvertidos`).
- Metricas calculadas: `tfrMinutos`, `ttrMinutos`, `resueltoEnPrimerContacto`. Estos campos son inmutables manualmente.

## Instalacion y Despliegue
1. Clonar el repositorio.
2. Configurar las variables de entorno en el archivo `.env`:
   - `GEMINI_API_KEY`: Clave de acceso al servicio de IA Generativa.
   - `PORT`: Puerto de escucha del servidor (por defecto 3000).
3. Ejecutar el servidor:
   ```bash
   node server.js
   ```
4. Acceder al sistema mediante `http://localhost:3000`.

## Dictado por voz en el chat

Pulsa el botón de micrófono, permite el acceso y habla en español. Pulsa nuevamente para detener el dictado, revisa el texto y selecciona **Enviar**. También puedes seguir escribiendo.

Requiere un navegador compatible con `SpeechRecognition` y acceso mediante HTTPS o localhost. Si no está disponible, el chat conserva la entrada escrita. El navegador puede utilizar un servicio externo para procesar el audio y requerir conexión a Internet; la aplicación recibe el texto y no almacena grabaciones. Consulta la [documentación de reconocimiento de voz](https://developer.mozilla.org/en-US/docs/Web/API/SpeechRecognition).

## Verificación y entrega

La entrega actual corresponde únicamente al sistema central e incluye `deliverables/manual-usuario.pdf`, `deliverables/memoria-descriptiva.pdf`, `deliverables/anexos-informe.pdf` y el paquete Windows en `deliverables/Chatbox-ITIL-1.0.0-windows-x64/`.

- `npm test`: pruebas unitarias de documentos y conocimiento.
- `npm run test:integration`: backend real con datos aislados.
- `npm run test:ui`: pruebas de navegador; requiere Chrome o Edge.
- `npm run build:exe`: construye el paquete SEA con Node 22.18.0 Windows x64.

El ZIP distribuible no incluye la clave privada de Gemini. Configura `.env` en el equipo de destino y conserva todas las carpetas junto al EXE. Consulta `deliverables/VERIFICACION.md` para los resultados y límites de esta revisión.

## Derechos de Autor y Propiedad Intelectual
Software disenado y estructurado con el fin de cumplir los requerimientos de registro de propiedad intelectual (Indecopi). 
Queda prohibida su reproduccion, distribucion o modificacion sin autorizacion expresa del autor.

**Version:** 1.0.0
**Licencia:** Propietaria. Todos los derechos reservados.
