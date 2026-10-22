# OdontoCare

Sistema de gestión para consultorios odontológicos: pacientes, agenda por profesional, historia clínica, documentos, planes de tratamiento, cargos, pagos, caja y reportes. Incluye conversaciones de WhatsApp y un agente de IA que consulta disponibilidad y gestiona citas con confirmación del paciente y supervisión humana.

Esta distribución procede de la versión principal del producto y se prepara en la rama `despliegue-odontocare`. Conserva los roles, permisos, auditoría y funcionalidades de esa versión.

## Componentes

- `backend`: Spring Boot con Java 21, PostgreSQL y migraciones Flyway. Incluye la API, el webhook de Kapso y el agente. Dockerfile de varias etapas; puerto interno 8080.
- `frontend`: React, TypeScript, Vite y Tailwind CSS. Nginx sirve la aplicación; puerto interno 8080.
- PostgreSQL se despliega como un servicio independiente. Flyway crea el esquema; una instalación nueva permite registrar la primera cuenta administradora desde la interfaz.

## Configuración en Dokploy

Cada componente utiliza su propio dominio HTTPS. Si la carpeta se agrega como `odontocare` en la raíz del repositorio compartido, usar Build Path `/` y estos valores:

| Componente | Docker File | Docker Context Path |
| --- | --- | --- |
| Backend | `odontocare/backend/Dockerfile` | `odontocare/backend` |
| Frontend | `odontocare/frontend/Dockerfile` | `odontocare/frontend` |

Variables del backend:

- `DB_URL`: URL JDBC de PostgreSQL, con su nombre interno de servicio y base.
- `DB_USERNAME` y `DB_PASSWORD`: credenciales de esa base, configuradas en Dokploy.
- `FRONTEND_URL`: origen HTTPS exacto del frontend, sin ruta. Permite únicamente ese origen y conserva sesiones y protección CSRF.
- `INTEGRATIONS_ENCRYPTION_KEY`: clave privada de la instalación, de 32 bytes aleatorios codificados en Base64. Conservarla estable y respaldarla de forma segura, separada de la base; permite guardar las credenciales de proveedores desde la interfaz.
- `WHATSAPP_WEBHOOK_BASE_URL`: opcional; su valor predeterminado es `https://api-odontocare.camiluxlym.com`.

Variable del frontend:

- `BACKEND_URL`: origen HTTPS del backend. Su valor predeterminado es `https://api-odontocare.camiluxlym.com`. Se aplica al iniciar el contenedor, sin reconstruir la imagen.

Los archivos `.env.example` sirven como referencia para introducir los valores en Dokploy; no contienen secretos ni se cargan automáticamente. El Dockerfile selecciona el perfil `prod`, la escucha de red y el puerto 8080. Mantener una sola réplica de backend en esta instalación.

## WhatsApp y agente

Endpoint para copiar en el webhook de Kapso:

```text
https://api-odontocare.camiluxlym.com/api/v1/integrations/kapso/events
```

La recepción forma parte del backend Java; no se ejecuta un receptor Node.js adicional. WhatsApp y el agente arrancan desactivados en una base nueva. Un administrador configura Kapso y GroqCloud en **Configuración → Integraciones**, registra en Kapso el endpoint y el secreto de firma coincidente y activa los servicios cuando los datos estén completos. Las claves de los proveedores se guardan cifradas y pueden actualizarse sin reiniciar la aplicación.

Esta carpeta contiene código y archivos de construcción/configuración. No contiene datos del consultorio, credenciales locales, documentación de desarrollo, evidencias, directorios Git, dependencias instaladas ni resultados compilados.
