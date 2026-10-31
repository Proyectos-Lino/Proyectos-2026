# BIOTRACK

Sistema de control de asistencia hospitalaria con aplicación de escritorio y portal administrativo. Permite gestionar personal, registrar entrada, salida a refrigerio, retorno de refrigerio y salida; programar turnos, consultar indicadores, atender incidencias y generar reportes Excel, PDF y CSV.

El reconocimiento facial permite capturar imágenes con una cámara, enrolar empleados e identificar su rostro para registrar asistencia. Las plantillas biométricas se cifran con AES-256-GCM. Incluye autenticación, permisos por cargo, configuración, auditoría y respaldos.

## Tecnologías

Python 3.11, Tkinter, ttkbootstrap, Flask, HTML, CSS y JavaScript. OpenCV, dlib, face_recognition y cryptography para las funciones faciales. Persistencia local en JSON. Electron y PyInstaller para distribuir dos ejecutables de Windows que utilizan un único backend local y la misma carpeta de datos.

## Organización

- `biotrack/`: lógica del sistema, almacenamiento, seguridad, servicios faciales, cámara, escritorio y backend.
- `biotrack/static/`: interfaz web y recursos visuales utilizados por el portal.
- `portal_desktop/`: contenedor Electron del portal y definición de dependencias.
- `run.py`: inicio del escritorio y backend; `--web` inicia solamente el portal.
- `desktop_exe.py`, `portal_exe.py`, `windows/` y `build_windows.py`: entradas y configuración de los ejecutables.
- `comprobar_facial.py` y `verificar_modelos_faciales.py`: diagnósticos utilizados también por el ejecutable.
- `requirements*.txt`: dependencias de Python; `package-lock.json` fija las dependencias del contenedor Electron.

## Ejecución desde el código fuente

Preparar Python 3.11 y las dependencias de `requirements-facial.txt`; después ejecutar `python run.py --sin-demo`. El portal se sirve en `http://127.0.0.1:5000/` mientras el escritorio permanece abierto. En una base nueva, el administrador inicial es `admin` con contraseña `BioTrack2026!`; se puede definir otra contraseña inicial mediante `BIOTRACK_ADMIN_PASSWORD`. La función facial se habilita en Configuración y la cámara se abre por solicitud.

Para construir los EXE de Windows x64 se utilizan `requirements-build.txt`, Node.js y `python build_windows.py`. La compilación de dlib requiere las herramientas de C++ y CMake. Las DLL redistribuibles de Visual C++ se localizan mediante `BIOTRACK_VC_REDIST` o la instalación de Build Tools configurada en el script. La entrega compilada inicia con el administrador y guarda sus datos en `%LOCALAPPDATA%\BIOTRACK`.

Este repositorio contiene código, configuración de dependencias y recursos necesarios de la interfaz. Los datos de usuarios, claves, plantillas faciales, bibliotecas instaladas y ejecutables se generan o instalan por separado.
