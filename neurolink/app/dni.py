import re

import requests

from .settings import enabled, setting


def lookup_dni(dni, *, token=None):
    if not re.fullmatch(r"[0-9]{8}", dni):
        raise ValueError("Ingresa un DNI de ocho dígitos.")
    if token is None:
        token = setting("API_TOKEN") if enabled("NEUROLINK_DNI_API") else ""
    if not token:
        raise ValueError("Agrega la clave de MiAPI Cloud en Configuración de integraciones.")
    try:
        response = requests.get(
            f"https://miapi.cloud/v1/dni/{dni}",
            headers={"Authorization": f"Bearer {token}"},
            timeout=(4, 8),
        )
        if response.status_code in (401, 403):
            raise ValueError("MiAPI Cloud rechazó la clave o esta no tiene acceso al servicio.")
        if response.status_code == 404:
            raise ValueError("El DNI no fue encontrado por el proveedor.")
        if response.status_code in (402, 429):
            raise ValueError("Consulta la cuota disponible de MiAPI Cloud y vuelve a intentar.")
        response.raise_for_status()
        payload = response.json()
    except requests.RequestException as exc:
        raise ValueError("No se pudo contactar a MiAPI Cloud. Revisa la conexión y vuelve a intentar.") from exc
    data = payload.get("datos") if isinstance(payload, dict) else None
    if not isinstance(data, dict) or not data.get("nombres"):
        raise ValueError("El proveedor no devolvió datos para ese DNI.")
    address = data.get("domiciliado")
    address = address if isinstance(address, dict) else {}
    return {
        "dni": dni, "nombres": data["nombres"], "apellido_paterno": data.get("ape_paterno") or "",
        "apellido_materno": data.get("ape_materno") or "", "edad": None,
        "direccion": address.get("direccion") or "", "distrito": address.get("distrito") or "",
        "provincia": address.get("provincia") or "",
    }
