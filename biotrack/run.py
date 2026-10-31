"""Punto de entrada del escritorio y del portal administrativo."""
from __future__ import annotations

import argparse
import logging
import sys
import threading
import webbrowser

from biotrack.bootstrap import build, build_server


def main():
    parser = argparse.ArgumentParser(description="BIOTRACK · Sistema de control de asistencia hospitalaria")
    parser.add_argument("--web", action="store_true", help="Ejecutar solo el portal web")
    parser.add_argument("--port", type=int, default=5000, help="Puerto del portal, por defecto 5000")
    parser.add_argument("--data-dir", help="Directorio de datos independiente")
    parser.add_argument("--sin-demo", action="store_true", help="Crear solo el administrador al inicializar una base nueva")
    parser.add_argument("--abrir", action="store_true", help="Abrir el portal en el navegador")
    parser.add_argument("--smoke-test", action="store_true", help="Comprobar la construcción de ventanas sin usar la cámara")
    args = parser.parse_args()
    if not 0 <= args.port <= 65535:
        parser.error("El puerto debe estar entre 0 y 65535.")
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    service, facial, transport, directory = build(args.data_dir, not args.sin_demo)
    server = None
    try:
        server, engine = build_server(transport, args.port)
        url = f"http://127.0.0.1:{server.server_port}/"
        print(f"BIOTRACK · Portal: {url}", flush=True)
        facial_state = 'habilitado' if service.store.read()['configuracion']['facial_habilitado'] else 'desactivado'
        print(f"Motor: {engine}\nDatos: {directory}\nReconocimiento facial: {facial_state}; la cámara se inicia por solicitud.", flush=True)
        if args.web:
            if args.abrir:
                threading.Timer(0.5, lambda: webbrowser.open(url)).start()
            server.serve_forever()
        else:
            threading.Thread(target=server.serve_forever, daemon=True).start()
            from biotrack.desktop import DesktopApplication
            application = DesktopApplication(service, facial, transport, url, directory)
            if args.smoke_test:
                application.smoke_test()
            else:
                application.run()
    except KeyboardInterrupt:
        print("\nBIOTRACK finalizado.")
    except OSError as exc:
        print(f"No se pudo iniciar BIOTRACK: {exc}\nSi el puerto está ocupado, pruebe: python run.py --port 5001", file=sys.stderr)
        return 1
    finally:
        if server:
            if not args.web:
                server.shutdown()
            server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
