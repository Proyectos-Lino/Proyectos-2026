INSTRUCCIONES DE USO - CHATBOX ITIL
===================================

Bienvenido al sistema de Mesa de Ayuda Interna (Chatbox ITIL). Por favor, lee detenidamente estas instrucciones antes de usar el sistema.

1. DESCOMPRIMIR ANTES DE USAR
   Si has descargado este sistema en un archivo ZIP, es OBLIGATORIO que extraigas (descomprimas) TODA la carpeta en una ubicacion de tu disco duro (por ejemplo, en el Escritorio o Documentos) ANTES de ejecutar nada. 
   NO ejecutes el archivo .exe directamente desde el interior del archivo comprimido, ya que el sistema necesita crear y leer carpetas de datos que no funcionaran correctamente dentro de un ZIP.

2. COMO INICIAR EL SISTEMA
   Haz doble clic sobre el archivo "chatbox-itil.exe". 
   - Al hacerlo, se abrira una ventana negra de consola (simbolo del sistema). Esto es completamente NORMAL, es el "motor" del servidor ejecutandose en segundo plano. NO CIERRES esta ventana mientras estes usando el sistema.
   - Inmediatamente despues, tu navegador web predeterminado se abrira automaticamente y te llevara a la direccion http://localhost:3000 donde veras la interfaz del Chatbox.

3. ADVERTENCIA DE SMARTSCREEN (WINDOWS DEFENDER)
   Como este ejecutable no cuenta con un certificado comercial firmado, Windows podria mostrarte una pantalla azul indicando "Windows protegio su PC".
   Si esto ocurre:
   - Haz clic en el enlace "Mas informacion".
   - Aparecera un boton que dice "Ejecutar de todas formas". Haz clic ahi para iniciar el servidor.

4. PERMISOS DE FIREWALL
   La primera vez que ejecutes el sistema, es posible que el Firewall de Windows te muestre una alerta de seguridad preguntando por permisos de red.
   - Debes hacer clic en "Permitir acceso" para que el servidor local pueda funcionar correctamente.

5. ERROR DE PUERTO OCUPADO (EADDRINUSE)
   Si la consola negra se abre e inmediatamente muestra un mensaje de error que contiene "EADDRINUSE" o "port is already in use", significa que ya hay otro programa en tu computadora utilizando el puerto 3000.
   - En este caso, verifica si ya tienes otra instancia del chatbox abierta en segundo plano y cierrala, o avisa a soporte tecnico para cambiar el puerto de ejecucion.

6. COMO CERRAR EL SISTEMA
   Para apagar el sistema por completo de forma segura, simplemente:
   - Cierra la ventana negra de la consola pulsando en la "X".
   - Alternativamente, puedes hacer clic en la consola negra y presionar las teclas "Ctrl + C" en tu teclado.
