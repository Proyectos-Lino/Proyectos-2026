"""Integración facial diferida: no importa modelos ni abre cámaras al arrancar."""
from __future__ import annotations

import base64
import json
import math
import secrets
from pathlib import Path

from .service import AppError, ROLES


class FacialService:
    def __init__(self, service, key_path):
        self.service, self.key_path = service, Path(key_path)
        self._modules = None

    def _enabled(self):
        if not self.service.store.read()["configuracion"]["facial_habilitado"]:
            raise AppError("El reconocimiento facial está desactivado y pendiente de validación. Puede continuar con las marcaciones manuales.", 409)

    def _load(self):
        self._enabled()
        if self._modules is None:
            try:
                import cv2
                import face_recognition
                import numpy
                from cryptography.hazmat.primitives.ciphers.aead import AESGCM
            except (ImportError, OSError, RuntimeError, SystemExit) as exc:
                raise AppError(f"No se pudieron cargar las dependencias o modelos faciales: {exc}. Ejecute comprobar_facial.py con el Python del proyecto.", 503) from exc
            self._modules = cv2, face_recognition, numpy, AESGCM
        return self._modules

    def _cipher(self, create=False):
        AESGCM = (self._modules or self._load())[3]
        self.key_path.parent.mkdir(parents=True, exist_ok=True)
        if create:
            try:
                with self.key_path.open("xb") as output:
                    output.write(secrets.token_bytes(32))
            except FileExistsError:
                pass
        try:
            return AESGCM(self.key_path.read_bytes())
        except (OSError, ValueError) as exc:
            raise AppError("Falta la clave biométrica o no es válida. Recupere biometric.key de su respaldo; no se reemplazará la clave de los rostros existentes.", 503) from exc

    def _decode(self, payload):
        cv2, recognition, numpy, _ = self._load()
        if not isinstance(payload, bytes) or not payload:
            raise AppError("La imagen está vacía o no es válida.")
        if len(payload) > 2_000_000:
            raise AppError("La imagen supera el tamaño permitido.")
        try:
            image = cv2.imdecode(numpy.frombuffer(payload, dtype=numpy.uint8), cv2.IMREAD_COLOR)
        except cv2.error as exc:
            raise AppError("No se pudo leer la imagen.") from exc
        if image is None:
            raise AppError("No se pudo leer la imagen.")
        height, width = image.shape[:2]
        if max(height, width) > 1920:
            image = cv2.resize(image, (int(width * 1920 / max(height, width)), int(height * 1920 / max(height, width))))
        rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
        locations = recognition.face_locations(rgb, model="hog")
        if len(locations) != 1:
            raise AppError("Debe aparecer exactamente un rostro en la imagen.")
        encodings = recognition.face_encodings(rgb, locations)
        if not encodings:
            raise AppError("No se pudieron extraer las características del rostro.")
        return encodings[0]

    def _decrypt(self, record):
        try:
            blob = base64.b64decode(record["cifrado"], validate=True)
            vector = json.loads(self._cipher().decrypt(blob[:12], blob[12:], b"BIOTRACK-v1"))
            if not isinstance(vector, list) or len(vector) != 128 or any(isinstance(n, bool) or not isinstance(n, (int, float)) or not math.isfinite(n) for n in vector):
                raise ValueError("Plantilla facial inválida")
            return vector
        except Exception as exc:
            raise AppError("No se pudo leer el registro facial. Compruebe que conserva la clave biométrica.", 503) from exc

    def enroll(self, actor_id, employee_id, image_bytes):
        self._enabled()
        state = self.service.store.read()
        self.service._actor(state, actor_id, (ROLES[0],))
        employee = self.service._employee(state, employee_id)
        if not employee["activo"] or not employee.get("consentimiento"):
            raise AppError("El empleado debe estar activo y autorizar el registro de datos faciales.")
        encoding = self._decode(image_bytes)
        _, recognition, numpy, _ = self._load()
        def operation(state):
            self.service._actor(state, actor_id, (ROLES[0],))
            employee = self.service._employee(state, employee_id)
            if not employee["activo"] or not employee.get("consentimiento"):
                raise AppError("El empleado ya no está habilitado para enrolamiento.")
            if not state["configuracion"]["facial_habilitado"]:
                raise AppError("El reconocimiento facial fue desactivado durante el enrolamiento.", 409)
            # El bloqueo del almacén serializa también la creación inicial de la clave.
            cipher = self._cipher(create=not bool(state["rostros"]))
            for other_id, record in state["rostros"].items():
                if other_id != employee_id and recognition.face_distance([numpy.array(self._decrypt(record))], encoding)[0] <= state["configuracion"]["umbral_rostro"]:
                    raise AppError("Este rostro ya está registrado para otro empleado.", 409)
            nonce = secrets.token_bytes(12)
            encrypted = cipher.encrypt(nonce, json.dumps(encoding.tolist()).encode(), b"BIOTRACK-v1")
            state["rostros"][employee_id] = {"cifrado": base64.b64encode(nonce + encrypted).decode(), "registrado": self.service.clock().isoformat(), "algoritmo": "dlib-128/AES-256-GCM"}
            self.service._audit(state, actor_id, "enrolar_rostro", employee["username"])
            return {"mensaje": "Rostro registrado correctamente."}
        return self.service.store.mutate(operation)

    def identify(self, image_bytes):
        encoding = self._decode(image_bytes)
        _, recognition, numpy, _ = self._load()
        state = self.service.store.read()
        candidates = [(eid, record) for eid, record in state["rostros"].items() if self.service._employee(state, eid)["activo"] and self.service._employee(state, eid).get("consentimiento")]
        if not candidates:
            raise AppError("No hay rostros registrados.", 409)
        distances = recognition.face_distance([numpy.array(self._decrypt(record)) for _, record in candidates], encoding)
        closest = int(numpy.argmin(distances))
        distance = float(distances[closest])
        if distance > state["configuracion"]["umbral_rostro"]:
            raise AppError("Rostro no reconocido. Intente nuevamente.", 404)
        if sum(float(value) <= state["configuracion"]["umbral_rostro"] for value in distances) > 1:
            raise AppError("La imagen coincide con más de un registro. Revise los enrolamientos o reduzca el umbral antes de marcar.", 409)
        return candidates[closest][0], distance

    def remove(self, actor_id, employee_id):
        def operation(state):
            self.service._actor(state, actor_id, (ROLES[0],))
            employee = self.service._employee(state, employee_id)
            state["rostros"].pop(employee_id, None)
            self.service._audit(state, actor_id, "eliminar_rostro", employee["username"])
            return {"mensaje": "Registro facial eliminado. Puede volver a enrolar al empleado."}
        return self.service.store.mutate(operation)
