"""Apertura explícita de cámaras; importar este módulo no activa dispositivos."""
from __future__ import annotations

import os

from .service import AppError


def open_camera(index=0, cv2_module=None):
    if isinstance(index, bool) or not isinstance(index, int) or not 0 <= index <= 9:
        raise AppError("El índice de cámara debe ser un entero entre 0 y 9.")
    if cv2_module is None:
        try:
            import cv2 as cv2_module
        except ImportError as exc:
            raise AppError("Falta OpenCV. Instale requirements-facial.txt.", 503) from exc
    backends = [cv2_module.CAP_DSHOW, cv2_module.CAP_ANY] if os.name == "nt" else [cv2_module.CAP_ANY]
    for backend in backends:
        capture = cv2_module.VideoCapture(index, backend)
        if capture.isOpened():
            capture.set(cv2_module.CAP_PROP_FRAME_WIDTH, 640)
            capture.set(cv2_module.CAP_PROP_FRAME_HEIGHT, 480)
            return capture
        capture.release()
    raise AppError(f"No se pudo abrir la cámara {index}. Cierre Cámara, Teams y otras aplicaciones que la usen; revise los permisos de Windows o pruebe otro índice.", 503)
