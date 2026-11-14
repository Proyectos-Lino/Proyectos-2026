# Admissio

Plataforma web para elaborar y generar exámenes de admisión: banco de preguntas por curso, distribución por área y modalidad, carátulas personalizadas y generación de cuadernillos PDF con varias versiones.

- **Sistema publicado:** https://admissio.camiluxlym.com/
- **Documentación:** [Manual de usuario](docs/Admissio_Manual_de_Usuario.pdf) · [Memoria descriptiva](docs/Admissio_Memoria_Descriptiva.pdf)
- **Lanzador para Windows:** [Ejecutable/Admissio.exe](Ejecutable/)
- **Entregables:** https://drive.google.com/drive/folders/1lVJ96Xk4Wt5RaYXSmIdStM2s4EAWJ62s?usp=sharing

Admissio es un sistema genérico, adaptable a cualquier universidad, instituto o centro preuniversitario: áreas, cursos, modalidades, cantidades y carátulas son configurables.

> Para validar la distribución de preguntas por áreas y modalidades se usó como caso de prueba la estructura de un examen de admisión de la Universidad Nacional San Luis Gonzaga de Ica. Es solo una referencia ilustrativa: el sistema no fue desarrollado por encargo de esa universidad ni está vinculado a ella.

## Funciones

- Gestión de áreas de admisión, cursos y banco de preguntas.
- Editor enriquecido: fórmulas (LaTeX), subíndices, superíndices, símbolos, tablas e imágenes.
- Importación desde PDF/TXT, texto pegado, imágenes (con IA o con OCR local sin IA) y escáner.
- Carátulas por área y modalidad.
- Generación de exámenes en dos pasos con validación de la distribución y múltiples versiones.
- Historial y descarga de PDF.

## Tecnologías

| Capa | Tecnología |
|---|---|
| Frontend | React 19, TypeScript, Vite, Tailwind CSS, TipTap, MathLive, Tesseract.js, PDF.js |
| Backend | Java, Spring Boot 4, OpenPDF, JLaTeXMath, scripts Python |
| Datos | PostgreSQL 17 |
| Infraestructura | Docker Compose, Nginx |

## Puesta en marcha

```bash
cp .env.example .env      # completar las variables
docker compose up -d --build
```

El frontend queda expuesto en el puerto 80 del servicio `frontend`; publíquelo en su dominio (por ejemplo, con Dokploy, ver [DOKPLOY_DEPLOY.md](DOKPLOY_DEPLOY.md)).

| Variable | Descripción |
|---|---|
| `DB_PASSWORD` | Contraseña de PostgreSQL |
| `IMAGENES_ALMACENAMIENTO` | `local` (por defecto) o `cloudinary` |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | Solo si se usa Cloudinary |
| `GEMINI_API_KEY` | Solo para la importación "Con IA" |
| `VITE_API_BASE_URL` | Ruta de la API (por defecto `/api/v1`) |

## Estructura

```
Backend/        API Spring Boot y scripts Python
Frontend/       Aplicación React y configuración de Nginx
Preguntas/      Material fuente del banco de preguntas de ejemplo
docs/           Manual de usuario, memoria descriptiva y referencia de la API
Ejecutable/     Admissio.exe (lanzador para Windows) y su código fuente
bd_limpia_con_preguntas.sql   Base de datos inicial con preguntas de ejemplo
docker-compose.yml
```
