# Sistema de laboratorio

Aplicación para gestionar clientes y productores, órdenes de servicio, muestras,
órdenes de trabajo, ensayos, resultados, validación, informes PDF, indicadores
y auditoría. La organización, los catálogos y los permisos se configuran desde
el sistema.

## Componentes

- `backend/`: API REST con Java 21, Spring Boot, Spring Security, PostgreSQL y
  migraciones Flyway.
- `frontend/`: interfaz con React, TypeScript y Vite. Incluye el código opcional
  para generar una aplicación de escritorio con Electron.

## Configuración y ejecución

Se requieren Java 21, Node.js 22.12 o posterior compatible y una base PostgreSQL.
Los archivos `.env.example` de cada componente contienen ejemplos de configuración,
sin credenciales reales. El backend recibe las variables del entorno de ejecución;
no carga automáticamente un archivo `.env`.

Variables del backend: `DB_URL` (URL JDBC de PostgreSQL), `DB_USERNAME`,
`DB_PASSWORD`, `JWT_SECRET` y `CORS_ALLOWED_ORIGIN` (origen del frontend).
`SERVER_PORT` es opcional. Para despliegue se utiliza
`SPRING_PROFILES_ACTIVE=cloud`. La creación del administrador inicial se
controla con `BOOTSTRAP_ENABLED` y `BOOTSTRAP_ADMIN_PASSWORD`, únicamente
cuando la base aún no tiene usuarios.

Desde `backend/`:

```sh
./mvnw clean verify
java -jar target/labcore-api-0.1.0-SNAPSHOT.jar
```

En Windows, el comando Maven equivalente es `.\mvnw.cmd clean verify`.
Flyway crea y actualiza el esquema al iniciar el backend.

En el frontend, configura `VITE_API_URL` con la URL del backend terminada en
`/api/v1`. Desde `frontend/`:

```sh
npm ci
npm run dev
npm run build
```

La compilación web queda en `frontend/dist/` y debe servirse con soporte para
rutas de una aplicación de página única. El backend y PostgreSQL se ejecutan
como servicios independientes. Las credenciales se configuran en el servidor
y no se guardan en Git.

Para generar el ejecutable portátil en Windows, desde `frontend/`:
`npm run package:win`. Su conexión HTTPS se configura en `desktop/main.cjs`.

El repositorio contiene código, pruebas, migraciones y configuración de
compilación. Los archivos generados y la documentación de desarrollo se
conservan fuera del control de versiones de este repositorio.
