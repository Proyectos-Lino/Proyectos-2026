from __future__ import annotations

import logging
import os
from pathlib import Path
from socketserver import ThreadingMixIn
from wsgiref.simple_server import WSGIRequestHandler, WSGIServer, make_server

from .facial import FacialService
from .service import AttendanceService, seed_state
from .storage import JsonStore
from .web import WebApplication, create_flask_app

ROOT = Path(__file__).resolve().parent.parent


def build(data_dir=None, demo=True):
    directory = Path(data_dir or os.getenv("BIOTRACK_DATA_DIR", ROOT / "data")).resolve()
    directory.mkdir(parents=True, exist_ok=True)
    store = JsonStore(directory / "biotrack.json", lambda: seed_state(demo=demo))
    service = AttendanceService(store)
    facial = FacialService(service, directory / "biometric.key")
    transport = WebApplication(service, facial)
    return service, facial, transport, directory


class ThreadedServer(ThreadingMixIn, WSGIServer):
    daemon_threads = True
    request_queue_size = 32


class QuietHandler(WSGIRequestHandler):
    def log_message(self, format, *args):
        logging.getLogger("biotrack.http").info(format, *args)


def build_server(transport, port=5000, host="127.0.0.1"):
    try:
        application = create_flask_app(transport)
        engine = "Flask"
    except ImportError:
        application = transport
        engine = "WSGI estándar (modo compatible sin dependencias)"
    server = make_server(host, port, application, server_class=ThreadedServer, handler_class=QuietHandler)
    return server, engine
