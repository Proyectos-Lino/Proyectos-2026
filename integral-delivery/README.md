# Integral Delivery

Aplicación móvil para gestionar pedidos y entregas en tiempo real. Los clientes consultan el catálogo y siguen sus pedidos. El administrador gestiona el negocio, los productos y las entregas. Los repartidores consultan sus asignaciones y actualizan el estado del reparto.

- `mobile/` contiene el proyecto Flutter para Android. Abre la carpeta, ejecuta `flutter pub get` y usa `flutter run` con un dispositivo conectado.
- `backend/` contiene la API FastAPI, SQLite y las conexiones WebSocket.

Para construir la API, usa `backend/` como contexto de Docker y su `Dockerfile`. En Dokploy configura las variables de ejecución `ADMIN_EMAIL` y `ADMIN_PASSWORD` con una contraseña de al menos diez caracteres. El servicio escucha en el puerto interno `8000` y ofrece `/health` para comprobar su estado. Usa una réplica y monta un volumen persistente en `/data` para la base de datos y las imágenes.

En la pantalla de acceso de la APK, abre la configuración del servidor e introduce el dominio HTTPS asignado al backend.
