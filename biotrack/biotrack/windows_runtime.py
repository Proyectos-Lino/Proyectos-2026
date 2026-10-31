"""Arranque de Windows: una instancia, datos por usuario y portal conectado."""
from __future__ import annotations

import argparse
import hmac
import json
import logging
from logging.handlers import RotatingFileHandler
import os
from pathlib import Path
import queue
import secrets
import subprocess
import sys
import threading
import time
from urllib.request import Request, urlopen

from .bootstrap import build_server
from .facial import FacialService
from .service import AttendanceService, seed_state
from .storage import JsonStore
from .web import Response, WebApplication

APP_ID = "BIOTRACK-WINDOWS-1"
BUILD_VERSION = "1.1.0"


def user_directory(override=None):
    if override:
        return Path(override).resolve()
    base = os.environ.get("LOCALAPPDATA")
    return (Path(base) if base else Path.home() / "AppData" / "Local") / "BIOTRACK"


def initial_state():
    state = seed_state(demo=False)
    state["configuracion"]["facial_habilitado"] = True
    return state


class InstanceLock:
    def __init__(self, path):
        self.path, self.handle = Path(path), None

    def acquire(self):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.handle = self.path.open("a+b")
        self.handle.seek(0, 2)
        if not self.handle.tell():
            self.handle.write(b"0")
            self.handle.flush()
        self.handle.seek(0)
        try:
            if os.name == "nt":
                import msvcrt
                msvcrt.locking(self.handle.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(self.handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            self.handle.close()
            self.handle = None
            return False
        return True

    def close(self):
        if self.handle:
            self.handle.close()
            self.handle = None


class RuntimeControl:
    def __init__(self, transport, directory):
        self.transport, self.directory = transport, Path(directory)
        self.token, self.instance = secrets.token_hex(32), secrets.token_hex(16)
        self.commands = queue.Queue()
        self.portal_seen, self.portal_focus = 0, 0
        self.portal_launch = 0
        self.mutex = threading.Lock()
        self.descriptor = {}
        self.stopping = False

    def __call__(self, environ, start_response):
        path = environ.get("PATH_INFO", "")
        if not path.startswith("/__biotrack__/"):
            return self.transport(environ, start_response)
        token = environ.get("HTTP_X_BIOTRACK_TOKEN", "")
        if not hmac.compare_digest(token, self.token):
            response = Response({"error": "Acceso no autorizado."}, 403)
        elif environ.get("REQUEST_METHOD") != "POST":
            response = Response({"error": "Método no permitido."}, 405)
        elif path == "/__biotrack__/status":
            response = Response({"app": APP_ID, "instance": self.instance, "version": BUILD_VERSION, "stopping": self.stopping})
        elif path == "/__biotrack__/activate":
            self.commands.put("activate")
            response = Response({"ok": True})
        elif path == "/__biotrack__/portal":
            with self.mutex:
                self.portal_seen = time.monotonic()
                response = Response({"instance": self.instance, "focus": self.portal_focus, "stopping": self.stopping})
        else:
            response = Response({"error": "Operación no disponible."}, 404)
        from http import HTTPStatus
        start_response(f"{response.status} {HTTPStatus(response.status).phrase}", response.headers)
        return [response.body]

    def publish(self, url):
        executable = str(Path(sys.executable).resolve())
        self.descriptor = {"app": APP_ID, "version": BUILD_VERSION, "instance": self.instance, "url": url, "token": self.token, "pid": os.getpid(), "desktop": executable}
        path = self.directory / "runtime.json"
        temporary = path.with_suffix(".tmp")
        temporary.write_text(json.dumps(self.descriptor), encoding="utf-8")
        os.replace(temporary, path)

    def remove_descriptor(self):
        self.stopping = True
        path = self.directory / "runtime.json"
        try:
            if json.loads(path.read_text(encoding="utf-8")).get("instance") == self.instance:
                path.unlink()
        except (OSError, ValueError):
            pass

    def open_portal(self):
        with self.mutex:
            if self.portal_seen and time.monotonic() - self.portal_seen < 8:
                self.portal_focus += 1
                return
            if self.portal_launch and time.monotonic() - self.portal_launch < 3:
                return
        executable = Path(sys.executable).resolve().parent / "BIOTRACK_Portal.exe"
        if not executable.is_file():
            from .service import AppError
            raise AppError("Coloque BIOTRACK_Portal.exe junto a BIOTRACK_Escritorio.exe para abrir el portal.")
        self.portal_launch = time.monotonic()
        environment = os.environ.copy()
        # El proceso Electron no debe heredar rutas de DLL del paquete Python.
        environment.pop("PYTHONHOME", None)
        environment.pop("PYTHONPATH", None)
        if os.name == "nt":
            import ctypes
            ctypes.windll.kernel32.SetDllDirectoryW(None)
        try:
            subprocess.Popen([str(executable), "--data-dir", str(self.directory)], env=environment, cwd=str(executable.parent), creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        finally:
            if os.name == "nt" and getattr(sys, "frozen", False):
                ctypes.windll.kernel32.SetDllDirectoryW(sys._MEIPASS)


def activate_existing(directory, timeout=25):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            descriptor = json.loads((directory / "runtime.json").read_text(encoding="utf-8"))
            if descriptor.get("app") != APP_ID or not descriptor.get("url", "").startswith("http://127.0.0.1:"):
                raise ValueError("Descriptor no válido")
            request = Request(descriptor["url"] + "__biotrack__/activate", method="POST", headers={"X-BIOTRACK-Token": descriptor["token"]})
            with urlopen(request, timeout=2) as response:
                if response.status == 200:
                    return True
        except (OSError, ValueError, KeyError):
            time.sleep(.2)
    return False


def main():
    parser = argparse.ArgumentParser(description="BIOTRACK para Windows")
    parser.add_argument("--data-dir", help=argparse.SUPPRESS)
    parser.add_argument("--smoke-test", action="store_true", help=argparse.SUPPRESS)
    parser.add_argument("--diagnostico", action="store_true", help=argparse.SUPPRESS)
    parser.add_argument("--test-models", type=Path, help=argparse.SUPPRESS)
    parser.add_argument("--test-camera", action="store_true", help=argparse.SUPPRESS)
    args = parser.parse_args()
    directory = user_directory(args.data_dir)
    directory.mkdir(parents=True, exist_ok=True)
    logs = directory / "logs"
    logs.mkdir(exist_ok=True)
    handler = RotatingFileHandler(logs / "BIOTRACK.log", maxBytes=2_000_000, backupCount=3, encoding="utf-8")
    logging.basicConfig(level=logging.INFO, handlers=[handler], format="%(asctime)s %(levelname)s %(message)s", force=True)
    lock = InstanceLock(directory / "desktop-instance.lock")
    if not lock.acquire():
        if not activate_existing(directory):
            raise RuntimeError("BIOTRACK ya está iniciándose. Espere un momento y vuelva a intentarlo.")
        return 0
    server, control, application = None, None, None
    try:
        store = JsonStore(directory / "biotrack.json", initial_state)
        service = AttendanceService(store)
        facial = FacialService(service, directory / "biometric.key")
        transport = WebApplication(service, facial)
        control = RuntimeControl(transport, directory)
        # Mantener Flask para el portal y envolverlo con los controles locales.
        from .web import create_flask_app
        flask = create_flask_app(transport)
        control.transport = flask
        server, _ = build_server(transport, port=0)
        server.set_app(control)
        url = f"http://127.0.0.1:{server.server_port}/"
        threading.Thread(target=server.serve_forever, daemon=True).start()
        control.publish(url)
        logging.info("BIOTRACK %s iniciado. Datos: %s", BUILD_VERSION, directory)
        if args.diagnostico or args.test_models or args.test_camera:
            from comprobar_facial import check
            result = check(camera=args.test_camera)
            result.update(app_version=BUILD_VERSION, datos_nuevos=not bool(store.read()["asistencias"]), usuarios=len(store.read()["personal"]))
            if args.test_models:
                from verificar_modelos_faciales import verify_models
                result["modelos_reales"] = verify_models(args.test_models, directory / "prueba-modelos")
            (directory / "diagnostico-ejecutable.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
            return 0 if result["correcto"] else 1
        from .desktop import DesktopApplication
        application = DesktopApplication(service, facial, transport, url, directory, portal_opener=control.open_portal)
        def callback_error(kind, value, traceback):
            logging.error("Error en la interfaz", exc_info=(kind, value, traceback))
            application._error(value, application.root)
        application.root.report_callback_exception = callback_error
        def commands():
            try:
                while True:
                    control.commands.get_nowait()
                    application.root.deiconify()
                    application.root.lift()
                    application.root.focus_force()
            except queue.Empty:
                pass
            if application.root.winfo_exists():
                application.root.after(200, commands)
        application.root.after(200, commands)
        if args.smoke_test:
            application.smoke_test()
            (directory / "interfaz-ejecutable.json").write_text(json.dumps({"correcto": True, "version": BUILD_VERSION}), encoding="utf-8")
        else:
            application.run()
        return 0
    finally:
        if control:
            control.remove_descriptor()
        if server:
            server.shutdown()
            server.server_close()
        lock.close()


def entrypoint():
    try:
        return main()
    except Exception as exc:
        logging.exception("No se pudo iniciar BIOTRACK")
        if any(flag in sys.argv for flag in ("--diagnostico", "--test-models", "--test-camera", "--smoke-test")):
            return 1
        import tkinter as tk
        from tkinter import messagebox
        root = tk.Tk()
        root.withdraw()
        messagebox.showerror("BIOTRACK", f"No se pudo iniciar el sistema.\n\n{exc}", parent=root)
        root.destroy()
        return 1
