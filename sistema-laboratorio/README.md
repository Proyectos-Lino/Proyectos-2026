# LabFlow · Plataforma integral de gestión de laboratorios

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

## Despliegue manual de demostración en Dokploy

Crear tres servicios en el mismo entorno: PostgreSQL, una Application para
el backend y otra Application para el frontend. No se configura AutoDeploy
ni tareas de respaldo. Los datos de PostgreSQL sí necesitan un volumen
persistente para conservarse al reiniciar o actualizar el servicio.

En las dos Applications, seleccionar el proveedor Git, el repositorio
`https://github.com/Proyectos-Lino/Proyectos-2026.git`, la rama `main`,
`Build Path: /` y `Build Type: Dockerfile`. Usar las siguientes rutas,
relativas a la raíz del repositorio central:

| Campo | Backend | Frontend |
| --- | --- | --- |
| Dockerfile Path | `sistema-laboratorio/backend/Dockerfile` | `sistema-laboratorio/frontend/Dockerfile` |
| Docker Context Path | `sistema-laboratorio/backend` | `sistema-laboratorio/frontend` |
| Container Port en Domains | `8080` | `8080` |
| Docker Build Stage | vacío | vacío |

Mantener AutoDeploy desactivado y compilar los servicios uno por uno. Ambos
usan el puerto interno 8080 en contenedores independientes; no se publican
directamente como puertos del servidor. Domains los conecta mediante Traefik.

### PostgreSQL

Crear una Database PostgreSQL, inicialmente con la misma versión mayor de la
base que se vaya a trasladar. Para una base nueva puede usarse `postgres:16-alpine`.
Definir el nombre `labcore`, el usuario y una contraseña propia. Mantener
desactivado el acceso externo y usar las Internal Credentials de Dokploy.
El backend y la base deben compartir la red interna de Dokploy.

Mantener el comando de inicio original de la imagen de PostgreSQL, que crea
la base y el usuario durante su primer arranque. El pool reducido del backend
evita reservar conexiones mínimas cuando no hay actividad.

### Backend

En Environment, configurar los valores reales; los marcadores siguientes
no son contraseñas ni direcciones utilizables:

```dotenv
DB_URL=jdbc:postgresql://HOST_INTERNO:5432/labcore
DB_USERNAME=USUARIO_DE_LA_BASE
DB_PASSWORD=CONTRASENA_DE_LA_BASE
JWT_SECRET=CLAVE_ALEATORIA_DE_AL_MENOS_32_CARACTERES
CORS_ALLOWED_ORIGIN=https://laboratorio.tudominio.com
BOOTSTRAP_ENABLED=false
```

La imagen ya establece `SPRING_PROFILES_ACTIVE=cloud` y `SERVER_PORT=8080`.
No utilizar `localhost` como host de la base: corresponde al propio contenedor.
El origen de CORS debe coincidir con el frontend, sin ruta ni barra final.

Si se restauran datos existentes, hacerlo antes de iniciar la API y mantener
`BOOTSTRAP_ENABLED=false`. Para una base vacía, usar temporalmente
`BOOTSTRAP_ENABLED=true` y una `BOOTSTRAP_ADMIN_PASSWORD` de al menos 12 caracteres.
El administrador inicial será `admin@labcore.local`. Después del primer arranque,
desactivar el bootstrap. Flyway aplica las migraciones al iniciar la aplicación.

La JVM arranca con 64 MiB de heap y permite crecer hasta 320 MiB; además se
limitan los hilos y el pool a cuatro conexiones, sin conexiones mínimas
reservadas. Estos valores se pueden cambiar mediante las variables de entorno
de Dokploy si las pruebas muestran que es necesario.

### Frontend

En Environment / Build Time Arguments, definir antes de compilar:

```dotenv
VITE_API_URL=https://api-laboratorio.tudominio.com/api/v1
```

El argumento debe estar en Build Time Arguments, no solamente en las variables
de ejecución. Si cambia la URL, reconstruir la imagen. El build falla cuando
el argumento falta. Los nombres de dominio del ejemplo deben reemplazarse.

La imagen final contiene Nginx y los archivos de `dist`, con un solo worker;
no ejecuta Node.js, Vite ni Electron. La configuración conserva las rutas de
React al recargar, comprime las respuestas y evita guardar logs de acceso.

### Recursos y comprobación

Como límites iniciales de memoria en Dokploy: backend 768 MiB, PostgreSQL
384 MiB y frontend 64 MiB. Son topes, no memoria reservada ni una medida de
consumo real. Ajustarlos después de observar Monitoring durante una prueba
completa; la JVM también consume memoria fuera del heap.

Configurar los dominios con HTTPS: uno para la API y otro para la interfaz.
Los chequeos internos son `/actuator/health` en la API y `/healthz` en la web.
Comprobar inicio de sesión, OS, OT, resultados, validación, PDF, exportaciones
y recarga de rutas. El ejecutable se conecta al backend configurado en
`frontend/desktop/main.cjs`; se debe regenerar si cambia esa dirección.

Para compilar las imágenes localmente desde esta carpeta:

```sh
docker build -t labcore-api:demo ./backend
docker build --build-arg VITE_API_URL=https://api-laboratorio.tudominio.com/api/v1 -t labcore-web:demo ./frontend
```

## Registro y correcciones con trazabilidad

En la OS, producto y variedad se seleccionan desde catálogos relacionados.
Administración y comercial pueden registrar nuevos productos o variedades
desde el formulario de muestras. Se puede navegar directamente entre muestras
y copiar grupos de datos comunes, conservando sus identificaciones individuales.
La copia respeta los valores existentes salvo que se marque reemplazarlos.
Los ensayos del servicio se preseleccionan y se pueden quitar por muestra;
debe quedar al menos un ensayo con método. Yemas y cargadores admiten enteros
no negativos, tanto en la interfaz como en la API.

Las devoluciones técnicas requieren un motivo y permiso de administración o
del responsable técnico asignado a la OT. Los roles que registran o validan
resultados mantienen sus permisos habituales. Una persona no puede validar
un resultado que haya registrado o corregido ella misma.

| Operación vigente | Devolución |
| --- | --- |
| OT cerrada | Validada, para revisar el cierre |
| OT validada | Resultados registrados, para revisar la validación |
| OT con resultados registrados | En ejecución, con corrección solicitada al analista |
| OT en ejecución | En espera, para corregir fecha y analistas |
| Resultado validado | Registrado, con una nueva revisión pendiente de validar |
| Resultado registrado o corregido | Corrección solicitada al analista |
| Informe emitido | Se conserva y anula la versión emitida; se crea un nuevo borrador |
| Informe en borrador | Revisión técnica de sus resultados; se anula el borrador |

La corrección comercial sigue permitiendo devolver la OS desde sus OT,
editar sus datos y solicitar aprobación técnica. Se conservan OT y resultados
compatibles; los cambios incompatibles muestran su impacto antes de confirmar.
Los informes afectados se anulan y deben prepararse y emitirse nuevamente.
Un informe que comprende varias OT solo puede ser devuelto por administración
o por un responsable asignado a todas ellas.

OS, OT y resultados disponen de versiones conservadas de solo consulta con
usuario, rol, fecha, acción y motivo. Los PDF emitidos y sus validaciones
anteriores se conservan. Las vistas técnicas mantienen la anonimización;
las copias de la OS requieren permiso comercial. La conservación de copias
completas comienza al avanzar o corregir operaciones con esta versión; no
reconstruye datos históricos que nunca se guardaron.

Para actualizar una instalación existente, desplegar primero el backend.
Flyway aplica V22–V24: relaciona productos y variedades históricos solo si
la coincidencia de nombres es única, agrega las versiones conservadas y
permite validaciones independientes por revisión. Mantiene los nombres
históricos y no elimina resultados. Después reconstruir el frontend.
No se agregan variables de entorno obligatorias.

## Comprobaciones

- Backend: `./mvnw spotless:check verify` (en Windows: `.\mvnw.cmd`).
- Frontend: `npm test`, `npm run lint` y `npm run build`.
- Flujo integral local: `node backend/scripts/verify-correction-flow.mjs`.
  Requiere una instancia local de prueba en `http://127.0.0.1:8081/api/v1`
  y `LABFLOW_TEST_PASSWORD` con la contraseña del administrador de esa
  instancia. Crea registros ficticios; no debe usarse sobre datos de trabajo.
  Comprueba permisos, selección de catálogos, devoluciones, versiones,
  resultados escalares, lecturas calculadas, matrices y documentos PDF.
