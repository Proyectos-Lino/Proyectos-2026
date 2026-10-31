"""Diagnóstico explícito de modelos y cámara. No guarda imágenes ni modifica datos."""
from __future__ import annotations

import argparse
import importlib
import importlib.metadata
import json
import sys
import struct
import time
from pathlib import Path

from biotrack.camera import open_camera
from biotrack.service import now_peru


def check(camera=False, index=0, seconds=5, camera_only=False):
    result = {"fecha": now_peru().isoformat(), "interprete": sys.executable, "python": sys.version.split()[0], "bits": struct.calcsize("P") * 8, "dependencias": {}, "modelos": "No comprobados", "camara": "No solicitada", "errores": []}
    modules = {}
    names = [("cv2", "opencv-python")]
    if not camera_only:
        names += [("flask", "Flask"), ("ttkbootstrap", "ttkbootstrap"), ("PIL.ImageTk", "Pillow"), ("numpy", "numpy"), ("cryptography.hazmat.primitives.ciphers.aead", "cryptography"), ("dlib", "dlib"), ("face_recognition", "face-recognition")]
    for name, package in names:
        try:
            modules[name] = importlib.import_module(name)
            result["dependencias"][package] = importlib.metadata.version(package)
        except (Exception, SystemExit) as exc:
            result["dependencias"][package] = "ERROR"
            result["errores"].append(f"{package}: {exc}")
    if "face_recognition" in modules and "numpy" in modules:
        try:
            image = modules["numpy"].zeros((160, 160, 3), dtype="uint8")
            modules["face_recognition"].face_locations(image, model="hog")
            result["modelos"] = "Importados y detector HOG ejecutado con una imagen sintética; no prueba identidad"
        except Exception as exc:
            result["errores"].append(f"Modelos: {exc}")
    if camera:
        capture = None
        try:
            cv2 = modules.get("cv2")
            if cv2 is None:
                raise RuntimeError("OpenCV no está disponible")
            capture = open_camera(index, cv2)
            deadline, count, last = time.monotonic() + seconds, 0, None
            while time.monotonic() < deadline:
                valid, frame = capture.read()
                if not valid:
                    raise RuntimeError("La cámara abrió pero no entregó una imagen válida")
                count, last = count + 1, frame
                time.sleep(0.05)
            if last is None:
                raise RuntimeError("No se recibió ningún fotograma")
            result["camara"] = {"indice": index, "fotogramas": count, "resolucion": f"{last.shape[1]}x{last.shape[0]}", "imagen_guardada": False}
            if "face_recognition" in modules:
                rgb = cv2.cvtColor(last, cv2.COLOR_BGR2RGB)
                result["camara"]["rostros_detectados_ultima_imagen"] = len(modules["face_recognition"].face_locations(rgb, model="hog"))
        except Exception as exc:
            result["camara"] = "ERROR"
            result["errores"].append(f"Cámara: {exc}")
        finally:
            if capture is not None:
                capture.release()
    result["correcto"] = not result["errores"]
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--camara", action="store_true", help="Activar la cámara unos segundos; no guarda imágenes")
    parser.add_argument("--solo-camara", action="store_true", help="Probar captura sin cargar modelos faciales")
    parser.add_argument("--indice", type=int, choices=range(10), default=0)
    parser.add_argument("--segundos", type=int, choices=range(1, 21), default=5)
    parser.add_argument("--salida", type=Path, help="Guardar únicamente el resultado de diagnóstico en JSON, sin fotografías")
    args = parser.parse_args()
    result = check(args.camara or args.solo_camara, args.indice, args.segundos, args.solo_camara)
    if args.salida:
        args.salida.parent.mkdir(parents=True, exist_ok=True)
        args.salida.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["correcto"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
