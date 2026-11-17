# PyInstaller ejecuta el hook de PyQt6 antes de cargar main.py. Carga visión
# primero para que QtCore no fije una versión incompatible de las DLL de C++.
try:
    import app.vision
except ImportError:
    # La interfaz conserva el acceso con ratón y teclado si falla la visión.
    pass
