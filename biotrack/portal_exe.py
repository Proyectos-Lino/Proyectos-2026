"""Contenedor portátil de Electron para Windows x64, sin instalador externo."""
from pathlib import Path
import ctypes
import os
import subprocess
import sys


def main():
    home = Path(sys.executable).resolve().parent
    bundle = Path(sys._MEIPASS) / 'electron'
    environment = os.environ.copy()
    for variable in ('PYTHONHOME', 'PYTHONPATH', 'ELECTRON_RUN_AS_NODE', 'NODE_OPTIONS'):
        environment.pop(variable, None)
    environment['BIOTRACK_PORTAL_HOME'] = str(home)
    arguments = sys.argv[1:]
    position = arguments.index('--data-dir') if '--data-dir' in arguments else -1
    directory = Path(arguments[position + 1]) if position >= 0 else Path(environment.get('LOCALAPPDATA', str(Path.home() / 'AppData/Local'))) / 'BIOTRACK'
    logs = directory / 'logs'
    logs.mkdir(parents=True, exist_ok=True)
    log = logs / 'portal.log'
    if log.exists() and log.stat().st_size > 2_000_000:
        log.replace(log.with_suffix('.anterior.log'))
    ctypes.windll.kernel32.SetDllDirectoryW(None)
    try:
        with log.open('ab') as output:
            result = subprocess.run([str(bundle / 'BIOTRACK Portal.exe'), *arguments], cwd=str(bundle), env=environment, stdout=output, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
        if result.returncode:
            raise RuntimeError(f'La ventana Electron terminó al iniciarse (código {result.returncode & 0xffffffff:08X}). Abra el portal desde su sesión normal de Windows. Registro: {log}')
        return 0
    finally:
        ctypes.windll.kernel32.SetDllDirectoryW(sys._MEIPASS)


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Exception as error:
        if '--test-output' in sys.argv:
            import json
            target = Path(sys.argv[sys.argv.index('--test-output') + 1])
            if not target.exists():
                target.write_text(json.dumps({'correcto':False,'error':str(error)}),encoding='utf-8')
            raise SystemExit(1)
        ctypes.windll.user32.MessageBoxW(None, 'No se pudo iniciar el portal.\n\n' + str(error), 'BIOTRACK', 0x10)
        raise SystemExit(1)
