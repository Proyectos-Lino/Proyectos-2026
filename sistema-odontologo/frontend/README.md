# OdontoCare — interfaz

Interfaz adaptable para un odontólogo: pacientes, agenda, atenciones, tratamientos, documentos, finanzas y reportes. React, TypeScript, Vite y TailwindCSS. WhatsApp y el agente IA no forman parte de esta entrega.

## Despliegue en Dokploy

Crear una aplicación mediante Dockerfile, con su propio dominio HTTPS. Si la carpeta del sistema en GitHub es `sistema-odontologo`:

- Build Path: `/`
- Docker File: `sistema-odontologo/frontend/Dockerfile`
- Docker Context Path: `sistema-odontologo/frontend`
- Docker Build Stage: vacío (etapa final).
- Puerto del contenedor: **8080**.

Variable obligatoria de ejecución (Environment, no Build Arguments):

| Variable      | Valor                                |
| ------------- | ------------------------------------ |
| `BACKEND_URL` | `https://api-odontologo.ejemplo.com` |

Usar la URL pública HTTPS real del backend, sin rutas ni credenciales. No usar localhost ni el nombre interno de Dokploy. El contenedor genera la configuración pública al arrancar: cambiar la URL no exige recompilar, pero sí reiniciar/recrear el contenedor desde Dokploy. No se requieren Build Arguments ni claves de WhatsApp/IA.

Asignar el dominio HTTPS de este frontend en el puerto 8080, ruta `/`, sin quitar prefijos. El navegador se comunica directamente con el dominio público de la API; Nginx solamente sirve la interfaz, sin proxy a la API. En el backend, configurar `FRONTEND_URL` con el dominio exacto de esta interfaz.

Primero desplegar PostgreSQL y la API, luego este frontend. `/healthz` comprueba el frontend; comprobar también el acceso al sistema para validar la comunicación entre ambos dominios. Se recomiendan subdominios de una misma base (`odontologo.ejemplo.com` y `api-odontologo.ejemplo.com`) para evitar restricciones del navegador a cookies de terceros; cada uno conserva su propio dominio y configuración en Dokploy.

La imagen usa Node solo para compilar y Nginx sin privilegios para servir la aplicación. No subir node_modules, dist, claves o archivos locales. Para desarrollo: `npm ci`, `npm run dev`; la API debe estar disponible en 127.0.0.1:8080. Para compilar: `npm run build`.

Referencia: [Dockerfile en Dokploy](https://docs.dokploy.com/docs/core/applications/build-type).
