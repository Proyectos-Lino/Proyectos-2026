# Guía de Despliegue de Admissio en Dokploy

Este proyecto es una aplicación fullstack compuesta por:
1. **Base de Datos:** PostgreSQL 17 (con las tablas, cursos, categorías y 364 preguntas iniciales).
2. **Backend:** Spring Boot (Java 21) + scripts de Python (Google Gemini IA, Cloudinary, PyMuPDF).
3. **Frontend:** React + Vite + TypeScript servido a través de Nginx.

---

## ¿Es necesario crear una Base de Datos en Dokploy?

**SÍ, es indispensable.**
El Frontend no funciona solo: únicamente muestra la interfaz de usuario en el navegador del cliente. Todas las funcionalidades (autenticación de Cloudinary/Gemini, lectura/creación de preguntas, asignación de cursos, configuración de categorías y generación de exámenes en PDF) son procesadas por el **Backend**, el cual requiere una base de datos **PostgreSQL** activa.

---

## Opción 1: Despliegue con Docker Compose (Recomendada en Dokploy)

Dokploy soporta despliegues de múltiples contenedores usando Docker Compose con 1 solo clic.

1. En tu panel de Dokploy, ve a tu proyecto y crea un nuevo servicio de tipo **Compose**.
2. Conecta tu repositorio de GitHub:
   - **Repository:** `Proyectos-Lino/Proyectos-2026`
   - **Branch:** `main`
   - **Compose Path:** `./examen-admision/docker-compose.yml`
3. Configura las variables de entorno en la pestaña **Environment**:
   ```env
   DB_PASSWORD=tu_contraseña_segura_aqui
   IMAGENES_ALMACENAMIENTO=local
   CLOUDINARY_CLOUD_NAME=tu_cloud_name
   CLOUDINARY_API_KEY=tu_api_key
   CLOUDINARY_API_SECRET=tu_api_secret
   GEMINI_API_KEY=tu_clave_de_gemini
   VITE_API_BASE_URL=/api/v1
   ```
4. Haz clic en **Deploy**.
   - El servicio `admissio-postgres` se iniciará e importará automáticamente `bd_limpia_con_preguntas.sql` (todas las preguntas, cursos y configuraciones limpias sin historial de exámenes).
   - El servicio `admissio-backend` compilará la app de Spring Boot y configurará Python para los scripts.
   - El servicio `frontend` compilará la SPA y la servirá con Nginx.
5. **Asignar tu único dominio público con SSL (Let's Encrypt):**
   - Ve a la pestaña **Domains** en Dokploy.
   - Apunta tu dominio (ej. `tudominio.com`) al servicio `frontend` en el puerto `80`.
   - ¡Listo! No necesitas exponer el puerto 8080 del backend ni crear un subdominio para la API; Nginx se encarga de servir el frontend y redirigir internamente `/api/` hacia Spring Boot (servicio `admissio-backend`) en la misma red privada de Docker.

> Si la base de datos ya existía con otra contraseña, Postgres no la cambia al modificar `DB_PASSWORD`: use la contraseña original o elimine el volumen `postgres_data` (se perderán los datos).

---

## Opción 2: Despliegue con Servicios Independientes en Dokploy

Si prefieres administrar cada servicio por separado:

### 1. Base de Datos (PostgreSQL)
1. En Dokploy, ve a **Databases** -> **Create Database** -> Selecciona **PostgreSQL**.
2. Asigna un nombre a la base de datos: `bd_examen_admi`.
3. Anota el host interno (ej. `examen-db`), usuario (`postgres`), puerto (`5432`) y la contraseña generada.
4. Conéctate a la base de datos (mediante la terminal de Dokploy o un cliente como DBeaver/pgAdmin) y ejecuta el script `bd_limpia_con_preguntas.sql` para cargar todas las preguntas y tablas iniciales.

### 2. Backend (Spring Boot)
1. En Dokploy, crea una **Application** desde GitHub.
2. **Build Type:** Dockerfile.
3. **Dockerfile Path:** `examen-admision/Backend/Dockerfile`.
4. **Context Path:** `examen-admision/Backend`.
5. En **Environment Variables**, agrega:
   ```env
   SPRING_DATASOURCE_URL=jdbc:postgresql://<host_de_tu_bd_postgres>:5432/bd_examen_admi
   SPRING_DATASOURCE_USERNAME=postgres
   SPRING_DATASOURCE_PASSWORD=<contraseña_de_postgres>
   CLOUDINARY_CLOUD_NAME=tu_cloud_name
   CLOUDINARY_API_KEY=tu_api_key
   CLOUDINARY_API_SECRET=tu_api_secret
   GEMINI_API_KEY=tu_clave_de_gemini
   ```
6. En **Ports**, mapea el puerto `8080`.
7. Asigna un dominio para la API (por ejemplo `api.tudominio.com`).
8. Haz clic en **Deploy**.

### 3. Frontend (React + Vite)
1. En Dokploy, crea una nueva **Application** desde GitHub.
2. **Build Type:** Dockerfile.
3. **Dockerfile Path:** `examen-admision/Frontend/Dockerfile`.
4. **Context Path:** `examen-admision/Frontend`.
5. En **Build Arguments** o **Environment Variables**:
   ```env
   VITE_API_BASE_URL=https://api.tudominio.com/api/v1
   ```
6. En **Ports**, mapea el puerto `80`.
7. Asigna tu dominio principal (por ejemplo `tudominio.com`).
8. Haz clic en **Deploy**.

---

## Verificación
* Abre tu frontend en `https://tudominio.com`.
* El Banco de Preguntas mostrará las 364 preguntas cargadas.
* El Historial estará limpio y listo para generar nuevos exámenes.

