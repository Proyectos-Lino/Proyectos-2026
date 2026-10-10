# Documentos y análisis de video

La extracción de PDF, DOCX y TXT devuelve el texto completo. El formulario conserva ese texto en un `Map` en memoria, separado de Contenido. Cambiar los documentos o abrir otro formulario invalida la extracción. Autocompletar con IA requiere extraer todos los documentos seleccionados y envía su texto completo. La vista previa de Word muestra texto, sin reproducir la paginación ni el formato visual de Word. Los documentos originales conservan el flujo de adjuntos existente.

El análisis de video de Gestión de conocimiento utiliza `@google/genai` 2.24.0 y mantiene `gemini-3.5-flash-lite` y el prompt multimedia existente. Las otras consultas de texto e imagen conservan su integración actual.

```text
Archivo → POST binario /api/knowledge/analyze-video → Files API
        → esperar ACTIVE → URI → generateContent (visual y audio, JSON Schema)
YouTube → POST /api/knowledge/analyze con videoUrl
        → validar dominio e ID → video URI → Interactions (processing: agentic)
        → si excede 30 segundos o falla → generateContent
```

Los videos locales se cargan siempre con `ai.files.upload`, se consultan con `ai.files.get` mientras están en `PROCESSING` y se rechazan si terminan en `FAILED`. El servidor mantiene el archivo en memoria durante la solicitud y solicita su eliminación en Gemini al terminar, incluso si falla el análisis. No se guarda el video como adjunto del conocimiento. La ruta JSON anterior admite `videoBase64` por compatibilidad, pero también lo envía a Files API; el formulario utiliza transporte binario.

El límite propio de la aplicación es 256 MiB por video, con una carga binaria simultánea, 120 segundos de inactividad durante la carga y un plazo global de ocho minutos para subir, procesar y analizar en Gemini. Cada análisis directo tiene un límite efectivo de 90 segundos, con un reintento. Los plazos usan una carrera de promesas además de AbortSignal, porque el SDK puede demorar la cancelación. Se comprueban MIME y firma de MP4, WebM y OGG. Este límite protege la memoria del servidor; no representa el límite de Gemini. Google documenta 2 GB por archivo, 20 GB por proyecto y eliminación automática a las 48 horas en [Files API](https://ai.google.dev/gemini-api/docs/files).

La carga local prioriza generateContent porque en la prueba real el modo agentic tardó más de dos minutos con un clip de cuatro segundos. Se mantiene Files API para todos los tamaños y el modelo principal. Un esquema JSON exige que Contenido sea texto y evita rechazar respuestas por recibir listas en ese campo. El formulario recibe progreso NDJSON con la etapa actual y el tiempo transcurrido; los errores permanecen visibles y el botón se bloquea mientras se analiza.

El resultado contiene `suggestion` y `validacion`. Esta última incluye `visual`, `audio`, `habla` y `temporal`, cada uno con `estado` y `evidencia`, además de `duracionSegundos` y `timestamps`. Las imágenes y los tiempos deben haberse procesado; audio y habla pueden constar como ausentes solo si el modelo declara haber verificado su ausencia. Se rechazan respuestas sin evidencia o sin referencias temporales y se realiza un reintento. Si vuelve a fallar, se devuelve un error con la validación disponible y no se autocompleta Contenido. Esta comprobación valida la declaración estructurada del modelo; no constituye una verificación independiente de lo que vio o escuchó.

Sintaxis contrastada con la [documentación oficial de video](https://ai.google.dev/gemini-api/docs/video-understanding) y los tipos del SDK instalado. No se utiliza URL Context para YouTube.

Pruebas locales, sin consumir Gemini ni modificar los datos de tickets/conocimiento:

```sh
node tests/media-analysis.test.js
node tests/knowledge-routes.test.js
node tests/knowledge-ui-smoke.cjs
```

Las pruebas simulan Gemini y cubren procesamiento, fallo, timeout, eliminación, reintento, URLs, transporte binario, filtros y extracción completa de DOCX y texto. La prueba de interfaz usa Chrome o Edge sin ventana visible, con datos aislados, y comprueba los colores, las duraciones, los filtros, la separación entre extracción y autocompletado y la recepción de progreso y resultado del video.

Validación real del 22 de septiembre de 2026: clip sintético local con dos pantallas, textos PRUEBA 731 y PRUEBA 942 y dos tonos, sin voz. Files API y generateContent reconocieron ambos textos, el cambio de color, el audio y la ausencia de habla. Se verificó también la ruta HTTP binaria con progreso y resultado. Las dos ejecuciones directas demoraron aproximadamente 9 y 43 segundos. No se considera esto una garantía de duración para otros videos ni una validación de enlaces de YouTube.
