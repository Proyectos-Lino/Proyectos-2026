import json
import os
from pathlib import Path
from tempfile import NamedTemporaryFile

from dotenv import dotenv_values


SOURCE_DIR = Path(__file__).resolve().parents[1]
DATA_DIR = Path(
    os.getenv("NEUROLINK_DATA_DIR")
    or Path(os.getenv("LOCALAPPDATA", Path.home() / ".local" / "share")) / "NeuroLink"
)
INTEGRATION_FIELDS = ("API_TOKEN", "OPENROUTER_API_KEY", "OPENROUTER_MODEL")


def read_integrations(path):
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}
    if not isinstance(data, dict):
        return {}
    fields = (*INTEGRATION_FIELDS, "NEUROLINK_DNI_API", "NEUROLINK_OPENROUTER")
    return {key: data[key].strip() for key in fields if isinstance(data.get(key), str)}


def json_path():
    return DATA_DIR / "integraciones.json"


ENV = {
    "NEUROLINK_DNI_API": "true",
    "NEUROLINK_OPENROUTER": "true",
    "OPENROUTER_MODEL": "openrouter/free",
    **read_integrations(SOURCE_DIR / "integraciones-evaluacion.json"),
    **dotenv_values(SOURCE_DIR / ".env", interpolate=False),
    **dotenv_values(DATA_DIR / ".env", interpolate=False),
    **os.environ,
}
LOCAL = read_integrations(json_path())


def setting(name, default=""):
    return (LOCAL.get(name, ENV.get(name)) or default).strip()


def enabled(name):
    return setting(name).lower() in {"1", "true", "yes"}


def save_integrations(values):
    updated = {key: values[key].strip() for key in INTEGRATION_FIELDS}
    if any(any(char.isspace() for char in value) for value in updated.values()):
        raise ValueError("Las claves y el identificador del modelo deben quedar en una sola línea, sin espacios.")
    updated["OPENROUTER_MODEL"] = updated["OPENROUTER_MODEL"] or "openrouter/free"
    # Una configuración guardada dentro de la aplicación activa los servicios con clave.
    updated["NEUROLINK_DNI_API"] = "true"
    updated["NEUROLINK_OPENROUTER"] = "true"
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with NamedTemporaryFile(mode="w", encoding="utf-8", dir=DATA_DIR, delete=False) as file:
            temporary = Path(file.name)
            json.dump(updated, file)
        temporary.replace(json_path())
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)
    LOCAL.update(updated)
