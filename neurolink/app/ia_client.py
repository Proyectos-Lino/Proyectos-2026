import json
import re
import unicodedata

import requests

from .database import aggregate_snapshot
from .settings import enabled, setting


EXAMPLES = (
    "¿Cuántos pacientes están registrados?",
    "¿Cuántos registros de urgencia alta hay?",
    "¿Cuántos registros se guardaron hoy?",
    "¿Cuántas solicitudes están pendientes?",
    "¿Cuántas solicitudes fueron atendidas?",
    "Dame un resumen",
)


def summary(data):
    return (
        f"{data['registros']} registros en total: {data['alta']} con etiqueta Alta y "
        f"{data['normal']} con etiqueta Normal. Hoy se guardaron {data['hoy']}. "
        f"Solicitudes: {data['pendientes']} pendientes y {data['atendidas']} atendidas."
    )


def local_answer(question):
    text = unicodedata.normalize("NFKD", question.casefold())
    words = set(re.findall(r"[a-z]+", "".join(c for c in text if not unicodedata.combining(c))))
    if not words:
        return "Escribe una pregunta o elige un ejemplo."
    data = aggregate_snapshot()
    if "resumen" in words:
        return summary(data)
    if words & {"solicitud", "solicitudes", "pedido", "pedidos"}:
        if "pendientes" in words or "pendiente" in words:
            return f"Hay {data['pendientes']} solicitudes pendientes."
        if words & {"atendida", "atendidas", "atendido", "atendidos"}:
            return f"Hay {data['atendidas']} solicitudes atendidas."
        if words & {"cuanto", "cuanta", "cuantas", "cuantos", "total"}:
            return f"Hay {data['solicitudes']} solicitudes registradas."
    if words & {"dni", "nombre", "domicilio", "direccion", "edad", "distrito", "provincia",
                "tratamiento", "diagnostico", "sintoma", "sintomas", "ayer", "semana", "mes"}:
        return "La consulta local admite conteos generales. Elige uno de los ejemplos."
    if words & {"registro", "registros", "paciente", "pacientes", "urgencia"}:
        if "hoy" in words and not words & {"alta", "normal"}:
            return f"Hoy se guardaron {data['hoy']} registros."
        if "hoy" not in words and ("alta" in words or "normal" in words):
            key = "alta" if "alta" in words else "normal"
            return f"Hay {data[key]} registros con etiqueta {key.capitalize()}."
        if words & {"cuanto", "cuanta", "cuantas", "cuantos", "total"} and "hoy" not in words:
            return f"Hay {data['registros']} pacientes registrados."
    return "Prueba con un conteo de pacientes, una etiqueta, los registros de hoy o las solicitudes."


def ai_available():
    return enabled("NEUROLINK_OPENROUTER") and bool(setting("OPENROUTER_API_KEY")) and bool(setting("OPENROUTER_MODEL"))


def request_completion(api_key, model, messages):
    if not api_key or not model:
        raise ValueError("Indica la clave y el modelo de OpenRouter.")
    try:
        response = requests.post(
            "https://openrouter.ai/api/v1/chat/completions",
            headers={"Authorization": f"Bearer {api_key}"},
            json={"model": model, "max_tokens": 220, "messages": messages},
            timeout=(4, 12),
        )
        if response.status_code in (401, 403):
            raise ValueError("OpenRouter rechazó la clave o el acceso al modelo.")
        if response.status_code in (402, 429):
            raise ValueError("Revisa el saldo o los límites de uso de la clave de OpenRouter.")
        if response.status_code == 404:
            raise ValueError("El modelo de OpenRouter no está disponible.")
        response.raise_for_status()
    except requests.RequestException as exc:
        raise ValueError("No se pudo conectar con OpenRouter.") from exc
    try:
        content = response.json()["choices"][0]["message"]["content"]
    except (ValueError, KeyError, IndexError, TypeError) as exc:
        raise ValueError("OpenRouter envió una respuesta inválida.") from exc
    if not isinstance(content, str) or not content.strip():
        raise ValueError("OpenRouter no devolvió texto. Prueba con otro modelo.")
    return content.strip()


def ai_summary():
    if not ai_available():
        return "OpenRouter está desactivado. Usa la consulta local."
    counts = aggregate_snapshot()
    try:
        return request_completion(
            setting("OPENROUTER_API_KEY"),
            setting("OPENROUTER_MODEL"),
            [
                {"role": "system", "content": "Redacta un resumen administrativo corto en español. Usa solo los conteos agregados y conserva sus cifras."},
                {"role": "user", "content": json.dumps(counts)},
            ],
        )
    except ValueError:
        return "OpenRouter no está disponible. Resultado local: " + summary(counts)
