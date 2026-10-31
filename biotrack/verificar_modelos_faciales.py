"""Prueba dlib con fotos de ejemplo de face_recognition y datos aislados; no abre cámaras."""
from __future__ import annotations

import argparse
import json
import uuid
from pathlib import Path
from urllib.request import urlopen

from biotrack.bootstrap import ROOT, build
from biotrack.facial import FacialService
from biotrack.service import ACTIONS, ROLES, AppError

EXAMPLE_URL = "https://raw.githubusercontent.com/ageitgey/face_recognition/master/examples/"
EXAMPLE_FILES = ("obama.jpg", "obama2.jpg", "biden.jpg")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ejemplos-dir", type=Path, default=ROOT / ".test-data" / "facial-examples")
    parser.add_argument("--descargar-ejemplos", action="store_true", help="Descargar tres JPEG del repositorio oficial de la biblioteca")
    args = parser.parse_args()
    examples = args.ejemplos_dir.resolve()
    if args.descargar_ejemplos:
        examples.mkdir(parents=True, exist_ok=True)
        for name in EXAMPLE_FILES:
            target = examples / name
            if target.is_file():
                continue
            with urlopen(EXAMPLE_URL + name, timeout=30) as response:
                payload = response.read(2_000_001)
            if len(payload) > 2_000_000 or not payload.startswith(b"\xff\xd8"):
                raise RuntimeError("El ejemplo descargado no es un JPEG válido: " + name)
            target.write_bytes(payload)
    missing = [name for name in EXAMPLE_FILES if not (examples / name).is_file()]
    if missing:
        parser.error("Faltan ejemplos: " + ", ".join(missing) + ". Use --descargar-ejemplos o --ejemplos-dir.")
    directory = ROOT / ".test-data" / ("modelos-faciales-" + uuid.uuid4().hex[:8])
    result = verify_models(examples, directory)
    print(json.dumps(result, ensure_ascii=False, indent=2))


def verify_models(examples, directory):
    examples, directory = Path(examples), Path(directory)
    service, facial, _, _ = build(directory, demo=False)
    service.update_settings("admin", {"facial_habilitado": True})
    cv2, _, numpy, _ = facial._load()
    photos = [(examples / name).read_bytes() for name in EXAMPLE_FILES]
    checks = []

    def person(suffix):
        return service.save_employee("admin", {"nombre": "Ejemplo", "apellido": suffix, "username": "ejemplo_" + suffix.lower(), "edad": 30, "ocupacion": "Prueba de integración", "cargo": ROLES[3], "password": "Prueba2026!", "consentimiento": True})

    def rejected(task, status, message):
        try:
            task()
        except AppError as exc:
            if exc.status != status or message not in str(exc):
                raise AssertionError(f"Rechazo inesperado: {exc.status} / {exc}") from exc
        else:
            raise AssertionError("Se esperaba un rechazo: " + message)

    first, second = person("A"), person("B")
    facial.enroll("admin", first["id"], photos[0])
    checks.append("Enrolamiento real con dlib y plantilla AES-256-GCM")
    identified, distance = facial.identify(photos[1])
    assert identified == first["id"], "No reconoció una segunda foto de la misma persona"
    checks.append("Reconocimiento usando otra fotografía de la misma persona")
    rejected(lambda: facial.identify(photos[2]), 404, "Rostro no reconocido")
    checks.append("Rechazo de un rostro diferente sin enrolar")
    rejected(lambda: facial.enroll("admin", second["id"], photos[1]), 409, "ya está registrado")
    assert second["id"] not in service.store.read()["rostros"]
    checks.append("Rechazo de duplicado sin crear una segunda plantilla")
    facial.enroll("admin", second["id"], photos[2])
    assert facial.identify(photos[2])[0] == second["id"]
    checks.append("Identificación entre dos personas enroladas")
    vector = facial._decode(photos[0])
    assert vector.shape == (128,) and numpy.isfinite(vector).all()
    checks.append("Vector real de 128 dimensiones finitas")
    _, blank = cv2.imencode(".jpg", numpy.zeros((480, 640, 3), dtype=numpy.uint8))
    rejected(lambda: facial.identify(blank.tobytes()), 400, "exactamente un rostro")
    for invalid in (b"", b"contenido que no es una imagen"):
        rejected(lambda value=invalid: facial.identify(value), 400, "imagen")
    checks.append("Rechazo de fondo sin rostro, imagen vacía y archivo inválido")
    source = cv2.imdecode(numpy.frombuffer(photos[0], dtype=numpy.uint8), cv2.IMREAD_COLOR)
    source = cv2.resize(source, (int(source.shape[1] * 480 / source.shape[0]), 480))
    _, multiple = cv2.imencode(".jpg", numpy.concatenate((source, source), axis=1))
    rejected(lambda: facial.identify(multiple.tobytes()), 400, "exactamente un rostro")
    checks.append("Rechazo de una imagen con dos rostros")
    restored = FacialService(service, directory / "biometric.key")
    assert restored.identify(photos[1])[0] == first["id"]
    checks.append("Recarga de la plantilla y clave desde disco")
    for action in ACTIONS:
        employee_id, score = restored.identify(photos[1])
        row = service.mark(employee_id, employee_id, action, source="facial", confidence=score)
        assert row["origen"] == "facial" and row["distancia_facial"] <= .6
    assert len(service.history("admin", {"empleado_id": first["id"]})) == 4
    checks.append("Cuatro marcaciones faciales con los modelos reales")
    result = {"correcto": True, "comprobaciones": checks, "distancia_misma_persona": round(distance, 4), "umbral": .6, "camara": "No utilizada; pendiente de prueba con el usuario", "directorio_datos_aislados": str(directory), "fuente_ejemplos": EXAMPLE_URL}
    (directory / "resultado.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    return result


if __name__ == "__main__":
    main()
