"""Portal WSGI y adaptador Flask con un único controlador de API."""
from __future__ import annotations

import base64
import hmac
import json
import logging
import mimetypes
import os
import re
from http import HTTPStatus
from http.cookies import SimpleCookie
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlsplit

from . import __version__
from .reports import export
from .security import Sessions
from .service import AppError, MANAGERS, ROLES
from .storage import StorageError

MAX_BODY = 3_000_000
LOGGER = logging.getLogger("biotrack")


class Response:
    def __init__(self, data=None, status=200, body=None, content_type="application/json; charset=utf-8", headers=None):
        self.status = status
        self.body = json.dumps(data, ensure_ascii=False, allow_nan=False).encode() if body is None else body
        self.headers = [("Content-Type", content_type), ("Content-Length", str(len(self.body))), ("X-Content-Type-Options", "nosniff"), ("Referrer-Policy", "same-origin"), ("X-Frame-Options", "DENY"), ("Cache-Control", "no-store"), ("Content-Security-Policy", "default-src 'self'; img-src 'self' data:; script-src 'self'; style-src 'self'; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'none'")]
        self.headers += headers or []


class WebApplication:
    def __init__(self, service, facial=None, static_root=None, sessions=None):
        self.service, self.facial = service, facial
        self.sessions = sessions or Sessions()
        self.static_root = (Path(static_root) if static_root else Path(__file__).resolve().parent / "static").resolve()

    def _cookie(self, token, clear=False):
        cookie = SimpleCookie()
        cookie["biotrack_session"] = "" if clear else token
        cookie["biotrack_session"]["path"] = "/"
        cookie["biotrack_session"]["httponly"] = True
        cookie["biotrack_session"]["samesite"] = "Strict"
        cookie["biotrack_session"]["max-age"] = 0 if clear else self.sessions.ttl
        if os.getenv("BIOTRACK_SECURE_COOKIES") == "1":
            cookie["biotrack_session"]["secure"] = True
        return cookie.output(header="").strip()

    def handle(self, method, path, headers=None, body=b"", query="", remote="local"):
        headers = {key.lower(): str(value) for key, value in (headers or {}).items()}
        try:
            if len(body) > MAX_BODY:
                raise AppError("La solicitud supera el tamaño permitido.", 413)
            filters = {key: value[-1] for key, value in parse_qs(query, keep_blank_values=True, max_num_fields=30).items()}
            if not path.startswith("/api/"):
                if method != "GET":
                    raise AppError("Método no permitido.", 405)
                relative = "index.html" if path == "/" else unquote(path.removeprefix("/static/")) if path.startswith("/static/") else ""
                if not relative or "\x00" in relative or "\\" in relative:
                    raise AppError("Página no encontrada.", 404)
                file = (self.static_root / relative).resolve()
                if not file.is_relative_to(self.static_root) or not file.is_file():
                    raise AppError("Página no encontrada.", 404)
                return Response(body=file.read_bytes(), content_type=mimetypes.guess_type(file.name)[0] or "application/octet-stream")
            if method == "GET" and path == "/api/health":
                enabled = self.service.store.read()["configuracion"]["facial_habilitado"]
                return Response({"estado": "ok", "version": __version__, "reconocimiento_facial": "habilitado" if enabled else "desactivado"})
            values = {}
            if method in ("POST", "PATCH", "DELETE"):
                if body:
                    if headers.get("content-type", "").split(";")[0].strip() != "application/json":
                        raise AppError("Use contenido JSON para esta operación.", 415)
                    try:
                        values = json.loads(body)
                    except (ValueError, UnicodeDecodeError):
                        raise AppError("El contenido JSON no es válido.")
                    if not isinstance(values, dict):
                        raise AppError("La solicitud debe contener un objeto JSON.")
                origin = headers.get("origin")
                if origin and urlsplit(origin).netloc != headers.get("host", ""):
                    raise AppError("Origen de solicitud no autorizado.", 403)
            if path == "/api/login" and method == "POST":
                key = remote + ":" + str(values.get("username", "")).lower()[:80]
                if self.sessions.rate_limited(key):
                    raise AppError("Demasiados intentos. Espere cinco minutos e intente nuevamente.", 429)
                try:
                    user = self.service.authenticate(values.get("username", ""), values.get("password", ""))
                except AppError:
                    self.sessions.failure(key)
                    raise
                token, csrf = self.sessions.create(user["id"], key, user["auth_version"])
                return Response({"usuario": user, "csrf": csrf}, headers=[("Set-Cookie", self._cookie(token))])
            cookies = SimpleCookie()
            try:
                cookies.load(headers.get("cookie", ""))
                token = cookies["biotrack_session"].value if "biotrack_session" in cookies else ""
            except Exception:
                token = ""
            session = self.sessions.get(token)
            if not session:
                raise AppError("Inicie sesión para continuar.", 401)
            actor_id = session["employee_id"]
            actor = self.service.actor(actor_id)
            if session["auth_version"] != actor["auth_version"]:
                self.sessions.revoke(token)
                raise AppError("Su cuenta cambió. Inicie sesión nuevamente.", 401)
            if method in ("POST", "PATCH", "DELETE") and not hmac.compare_digest(headers.get("x-csrf-token", ""), session["csrf"]):
                raise AppError("La sesión de seguridad cambió. Actualice la página.", 403)
            if path == "/api/session" and method == "GET":
                return Response({"usuario": actor, "csrf": session["csrf"]})
            if path == "/api/logout" and method == "POST":
                self.sessions.revoke(token)
                return Response({"mensaje": "Sesión cerrada."}, headers=[("Set-Cookie", self._cookie("", True))])
            if path == "/api/password" and method == "POST":
                result = self.service.change_password(actor_id, values)
                self.sessions.revoke_employee(actor_id)
                return Response(result, headers=[("Set-Cookie", self._cookie("", True))])
            if path == "/api/resumen" and method == "GET":
                return Response(self.service.summary(actor_id))
            if path == "/api/actividad" and method == "GET":
                return Response(self.service.history(actor_id)[:8])
            if path == "/api/personal":
                if method == "GET":
                    return Response(self.service.list_staff(actor_id, filters.get("buscar", ""), filters.get("inactivos") == "1"))
                if method == "POST":
                    return Response(self.service.save_employee(actor_id, values), 201)
            match = re.fullmatch(r"/api/personal/([a-zA-Z0-9_-]+)(?:/(estado|rostro))?", path)
            if match:
                employee_id, operation = match.groups()
                if operation == "estado" and method == "POST":
                    result = self.service.set_active(actor_id, employee_id, values.get("activo"))
                    self.sessions.revoke_employee(employee_id)
                    return Response(result)
                if operation == "rostro" and method == "DELETE" and self.facial:
                    return Response(self.facial.remove(actor_id, employee_id))
                if not operation and method == "PATCH":
                    result = self.service.save_employee(actor_id, values, employee_id)
                    self.sessions.revoke_employee(employee_id)
                    return Response(result)
            if path == "/api/marcaciones" and method == "POST":
                return Response(self.service.mark(actor_id, values.get("empleado_id", actor_id), values.get("accion"), values.get("observacion", "")), 201)
            if path == "/api/historial" and method == "GET":
                return Response(self.service.history(actor_id, filters))
            if path == "/api/todos_los_horarios":
                if method == "GET":
                    return Response(self.service.list_shifts(actor_id))
                if method == "POST":
                    return Response(self.service.save_shift(actor_id, values), 201)
            match = re.fullmatch(r"/api/horarios/([a-zA-Z0-9_-]+)", path)
            if match:
                if method == "PATCH":
                    return Response(self.service.save_shift(actor_id, values, match[1]))
                if method == "DELETE":
                    return Response(self.service.delete_shift(actor_id, match[1]))
            if path == "/api/predicciones" and method == "GET":
                return Response(self.service.predictions(actor_id))
            if path == "/api/incidencias":
                if method == "GET":
                    return Response(self.service.incidents(actor_id))
                if method == "POST":
                    return Response(self.service.save_incident(actor_id, values), 201)
            match = re.fullmatch(r"/api/incidencias/([a-zA-Z0-9_-]+)", path)
            if match and method == "PATCH":
                return Response(self.service.save_incident(actor_id, values, match[1]))
            if path == "/api/configuracion":
                if method == "GET":
                    return Response(self.service.settings(actor_id))
                if method == "PATCH":
                    return Response(self.service.update_settings(actor_id, values))
            if path == "/api/auditoria" and method == "GET":
                return Response(self.service.audit(actor_id))
            if path == "/api/backup" and method == "GET":
                content = json.dumps(self.service.backup(actor_id), ensure_ascii=False, indent=2).encode()
                return Response(body=content, headers=[("Content-Disposition", 'attachment; filename="biotrack-respaldo.json"')])
            if path == "/api/reportes" and method == "GET":
                rows = self.service.history(actor_id, filters)
                kind = filters.get("formato", "xlsx")
                content, content_type = export(rows, kind, self.service.settings(actor_id)["hospital"], self.service.clock().strftime("%d/%m/%Y %H:%M"))
                return Response(body=content, content_type=content_type, headers=[("Content-Disposition", f'attachment; filename="BIOTRACK_asistencia.{kind}"')])
            if path in ("/api/facial/enrolar", "/api/facial/marcar") and method == "POST" and self.facial:
                self.facial._enabled()
                try:
                    payload = base64.b64decode(values.get("imagen", ""), validate=True)
                except (ValueError, TypeError):
                    raise AppError("La imagen no es válida.")
                if path.endswith("enrolar"):
                    return Response(self.facial.enroll(actor_id, values.get("empleado_id"), payload))
                employee_id, distance = self.facial.identify(payload)
                return Response(self.service.mark(actor_id, employee_id, values.get("accion"), source="facial", confidence=distance), 201)
            raise AppError("La operación solicitada no existe o no admite ese método.", 404)
        except AppError as exc:
            return Response({"error": str(exc)}, exc.status)
        except StorageError as exc:
            LOGGER.error("Persistencia: %s", exc)
            return Response({"error": str(exc)}, 503)
        except (ValueError, TypeError, KeyError):
            return Response({"error": "Revise los datos enviados en la solicitud."}, 400)
        except Exception:
            LOGGER.exception("Error no controlado en %s %s", method, path)
            return Response({"error": "No fue posible completar la operación. Revise el registro del servidor."}, 500)

    def __call__(self, environ, start_response):
        try:
            length = int(environ.get("CONTENT_LENGTH") or 0)
            if length < 0 or length > MAX_BODY:
                response = Response({"error": "Solicitud demasiado grande."}, 413)
            else:
                headers = {key[5:].replace("_", "-"): value for key, value in environ.items() if key.startswith("HTTP_")}
                headers["Content-Type"] = environ.get("CONTENT_TYPE", "")
                response = self.handle(environ.get("REQUEST_METHOD", "GET"), environ.get("PATH_INFO", "/"), headers, environ["wsgi.input"].read(length), environ.get("QUERY_STRING", ""), environ.get("REMOTE_ADDR", "local"))
        except (ValueError, OSError):
            response = Response({"error": "Solicitud no válida."}, 400)
        start_response(f"{response.status} {HTTPStatus(response.status).phrase}", response.headers)
        return [response.body]


def create_flask_app(transport):
    """Usa Flask cuando está instalado; la lógica coincide con el transporte WSGI."""
    from flask import Flask, Response as FlaskResponse, request
    app = Flask(__name__, static_folder=None)
    app.config["MAX_CONTENT_LENGTH"] = MAX_BODY

    @app.route("/", defaults={"path": ""}, methods=["GET", "POST", "PATCH", "DELETE"])
    @app.route("/<path:path>", methods=["GET", "POST", "PATCH", "DELETE"])
    def dispatch(path):
        result = transport.handle(request.method, "/" + path, dict(request.headers), request.get_data(), request.query_string.decode("utf-8"), request.remote_addr or "local")
        return FlaskResponse(result.body, status=result.status, headers=result.headers)

    @app.errorhandler(413)
    def too_large(error):
        result = Response({"error": "La solicitud supera el tamaño permitido."}, 413)
        return FlaskResponse(result.body, status=413, headers=result.headers)
    return app
