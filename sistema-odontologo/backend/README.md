# OdontoCare — API

Sistema odontológico para un profesional: pacientes, agenda, historia clínica, odontograma, documentos, tratamientos, cobros, caja y reportes. Java 21, Spring Boot y PostgreSQL. Esta entrega no habilita WhatsApp ni inteligencia artificial.

## Despliegue en Dokploy

Crear PostgreSQL (versión 16 o posterior) con almacenamiento persistente. Crear la aplicación API mediante Dockerfile y conectarla a la misma red interna. Si la carpeta del sistema en GitHub es `sistema-odontologo`:

- Build Path: `/`
- Docker File: `sistema-odontologo/backend/Dockerfile`
- Docker Context Path: `sistema-odontologo/backend`
- Docker Build Stage: vacío (etapa final).
- Puerto interno: **8080**. Asignar su propio dominio HTTPS, ruta `/`, sin quitar prefijos.

Variables de ejecución obligatorias (Environment):

| Variable       | Valor                                                                    |
| -------------- | ------------------------------------------------------------------------ |
| `DB_URL`       | `jdbc:postgresql://NOMBRE_INTERNO_POSTGRES:5432/BASE_DEL_CONSULTORIO`    |
| `DB_USERNAME`  | Usuario de esa base                                                      |
| `DB_PASSWORD`  | Contraseña de esa base                                                   |
| `FRONTEND_URL` | `https://odontologo.ejemplo.com` (origen exacto del frontend, sin rutas) |

El contenedor selecciona `prod`, escucha en todas las interfaces y expone `/actuator/health` para comprobar su salud. No configurar variables ni claves de mensajería o IA. Las credenciales se agregan en Dokploy, nunca al código ni como argumentos de compilación.

Flyway instala las tablas al iniciar; Hibernate valida el esquema. Usar una base exclusiva para esta variante. No mezclar las migraciones de la rama main con las de esta entrega. Los documentos, imágenes y PDF se almacenan en PostgreSQL; respaldar la base completa. La primera visita al frontend permite crear la cuenta administradora; no existe contraseña predeterminada. Completar luego la identidad y el profesional en Configuración. No publicar la instalación antes de crear la cuenta inicial.

Configurar una sola réplica de API: las sesiones de acceso se conservan en memoria y se renuevan al reiniciar. Frontend y API tienen dominios HTTPS propios. CORS permite exclusivamente `FRONTEND_URL`, con credenciales y protección CSRF conservadas. Configurar el límite de carga del proxy del dominio de API en al menos 64 MiB para permitir archivos.

Preferir subdominios de una misma base, como `odontologo.ejemplo.com` y `api-odontologo.ejemplo.com`. Son orígenes distintos y evitan depender de cookies de terceros. Las cookies de producción son Secure/HttpOnly/SameSite=None. Con dominios de bases diferentes, los navegadores pueden bloquear cookies de terceros; verificar el acceso en los dispositivos reales.

Referencia: [Dockerfile en Dokploy](https://docs.dokploy.com/docs/core/applications/build-type).
