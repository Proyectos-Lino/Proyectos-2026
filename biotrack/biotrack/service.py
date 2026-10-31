"""Reglas compartidas por el escritorio y el portal web."""
from __future__ import annotations

import copy
import math
import os
import re
import unicodedata
import uuid
from datetime import date, datetime, timedelta, timezone

from .security import hash_password, verify_password

PERU = timezone(timedelta(hours=-5), "America/Lima")
ROLES = ("Administrador", "Jefe de Enfermería", "Médico", "Enfermero/a")
MANAGERS = ROLES[:2]
ACTIONS = {"entrada": "Entrada", "salida_refrigerio": "Salida Refrigerio", "entrada_refrigerio": "Entrada Refrigerio", "salida": "Salida"}
SHIFTS = {"Mañana": ("07:00", "13:00"), "Tarde": ("13:00", "19:00"), "Noche": ("19:00", "07:00")}
DEFAULT_SETTINGS = {"hospital": "Hospital Félix Torrealva", "tolerancia_minutos": 15, "jornada_max_horas": 8, "minutos_refrigerio": 60, "umbral_rostro": 0.6, "facial_habilitado": False, "camara_indice": 0, "tema": 0}


class AppError(Exception):
    def __init__(self, message, status=400):
        super().__init__(message)
        self.status = status


def now_peru():
    return datetime.now(PERU)


def identifier():
    return uuid.uuid4().hex


def normalized(value):
    return "".join(c for c in unicodedata.normalize("NFKD", str(value)) if not unicodedata.combining(c)).casefold().strip()


def iso_date(value, name="Fecha"):
    try:
        if not isinstance(value, str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
            raise ValueError()
        return date.fromisoformat(value)
    except (ValueError, TypeError):
        raise AppError(f"{name}: use una fecha válida con formato AAAA-MM-DD.")


def parse_time(value):
    if not isinstance(value, str) or not re.fullmatch(r"(?:[01]\d|2[0-3]):[0-5]\d", value):
        raise AppError("La hora debe tener formato HH:MM de 24 horas.")
    return value


def shift_interval(shift):
    start = datetime.fromisoformat(f"{shift['fecha']}T{shift['inicio']}").replace(tzinfo=PERU)
    end = datetime.fromisoformat(f"{shift['fecha']}T{shift['fin']}").replace(tzinfo=PERU)
    if end <= start:
        end += timedelta(days=1)
    return start, end


def seed_state(clock=now_peru, demo=True):
    now = clock()
    stamp = now.isoformat()
    staff = []
    entries = [("admin", "admin", "Administrador", "BIOTRACK", 30, "Recursos Humanos", ROLES[0], os.getenv("BIOTRACK_ADMIN_PASSWORD", "BioTrack2026!"))]
    if demo:
        entries += [("medico-demo", "javier_mendoza", "Javier", "Mendoza", 35, "Medicina general", ROLES[2], "Demo2026!"), ("enfermera-demo", "lucia_rojas", "Lucía", "Rojas", 28, "Enfermería", ROLES[3], "Demo2026!"), ("jefe-demo", "rosa_salazar", "Rosa", "Salazar", 42, "Supervisión de enfermería", ROLES[1], "Demo2026!")]
    for eid, username, name, surname, age, occupation, role, password in entries:
        staff.append({"id": eid, "username": username, "nombre": name, "apellido": surname, "edad": age, "ocupacion": occupation, "cargo": role, "activo": True, "password_hash": hash_password(password), "creado": stamp, "actualizado": stamp, "consentimiento": False, "demo": True})
    state = {"schema": 1, "personal": staff, "asistencias": [], "horarios": [], "incidencias": [], "rostros": {}, "configuracion": dict(DEFAULT_SETTINGS), "auditoria": [], "creado": stamp}
    if demo:
        for days_ago in range(6, 0, -1):
            day = (now.date() - timedelta(days=days_ago)).isoformat()
            for employee in staff[1:3]:
                state["horarios"].append({"id": identifier(), "empleado_id": employee["id"], "fecha": day, "turno": "Mañana", "inicio": "07:00", "fin": "13:00"})
                for action, hour in [("entrada", "07:05"), ("salida_refrigerio", "10:00"), ("entrada_refrigerio", "10:30"), ("salida", "13:00")]:
                    state["asistencias"].append({"id": identifier(), "empleado_id": employee["id"], "fecha_hora": f"{day}T{hour}:00-05:00", "accion": action, "origen": "demostracion", "observacion": "Registro ficticio de ejemplo", "actor_id": "admin", "puntual": True if action == "entrada" else None})
        for employee in staff[1:3]:
            state["horarios"].append({"id": identifier(), "empleado_id": employee["id"], "fecha": now.date().isoformat(), "turno": "Mañana", "inicio": "07:00", "fin": "13:00"})
    return state


class AttendanceService:
    def __init__(self, store, clock=now_peru):
        self.store, self.clock = store, clock

    def _actor(self, state, actor_id, roles=None):
        actor = next((p for p in state["personal"] if p["id"] == actor_id and p["activo"]), None)
        if not actor:
            raise AppError("La sesión no es válida. Inicie sesión nuevamente.", 401)
        if roles and actor["cargo"] not in roles:
            raise AppError("No tiene permiso para realizar esta operación.", 403)
        return actor

    def _employee(self, state, employee_id):
        employee = next((p for p in state["personal"] if p["id"] == employee_id), None)
        if not employee:
            raise AppError("No se encontró al empleado.", 404)
        return employee

    def _public(self, state, employee):
        result = {k: v for k, v in employee.items() if k != "password_hash"}
        result["rostro_registrado"] = employee["id"] in state["rostros"]
        result["estado_asistencia"] = self._presence(state, employee["id"])
        result["auth_version"] = employee.get("auth_version", 0)
        return result

    def _audit(self, state, actor_id, action, detail):
        state["auditoria"].append({"id": identifier(), "fecha_hora": self.clock().isoformat(), "actor_id": actor_id, "accion": action, "detalle": detail})
        state["auditoria"] = state["auditoria"][-5000:]

    def authenticate(self, username, password):
        if not isinstance(username, str) or not isinstance(password, str) or len(username) > 80 or len(password) > 256:
            raise AppError("Usuario o contraseña incorrectos.", 401)
        state = self.store.read()
        employee = next((p for p in state["personal"] if normalized(p["username"]) == normalized(username) and p["activo"]), None)
        # El hash de reserva iguala el trabajo cuando el usuario no existe.
        fallback = state["personal"][0]["password_hash"] if state["personal"] else ""
        valid = verify_password(password, employee["password_hash"] if employee else fallback)
        if not employee or not valid:
            raise AppError("Usuario o contraseña incorrectos.", 401)
        return self._public(state, employee)

    def actor(self, actor_id):
        state = self.store.read()
        return self._public(state, self._actor(state, actor_id))

    def list_staff(self, actor_id, query="", include_inactive=False):
        state = self.store.read()
        actor = self._actor(state, actor_id)
        people = state["personal"] if actor["cargo"] in MANAGERS else [actor]
        return [self._public(state, p) for p in people if (p["activo"] or include_inactive) and (not query or normalized(query) in normalized(f"{p['username']} {p['nombre']} {p['apellido']} {p['cargo']} {p['ocupacion']}"))]

    def _validate_employee(self, values, previous=None):
        previous = previous or {}
        result = {}
        for field, limit in [("nombre", 80), ("apellido", 80), ("ocupacion", 120)]:
            value = values.get(field, previous.get(field, ""))
            if not isinstance(value, str) or not 1 <= len(value.strip()) <= limit or any(ord(c) < 32 for c in value):
                raise AppError(f"El campo {field} es obligatorio y admite hasta {limit} caracteres.")
            result[field] = value.strip()
        age = values.get("edad", previous.get("edad"))
        if isinstance(age, bool) or not str(age).isdigit() or not 18 <= int(age) <= 100:
            raise AppError("La edad debe ser un número entero entre 18 y 100.")
        result["edad"] = int(age)
        result["cargo"] = values.get("cargo", previous.get("cargo", ROLES[3]))
        if result["cargo"] not in ROLES:
            raise AppError("Seleccione un cargo válido.")
        username = values.get("username", previous.get("username", ""))
        if not username:
            username = re.sub(r"[^a-z0-9_]+", "_", normalized(result["nombre"] + "_" + result["apellido"])).strip("_")
        if not isinstance(username, str) or not re.fullmatch(r"[a-zA-Z0-9_][a-zA-Z0-9_.-]{2,49}", username):
            raise AppError("El usuario debe tener entre 3 y 50 letras, números, puntos, guiones o guiones bajos.")
        result["username"] = username.lower()
        password = values.get("password", "")
        if not isinstance(password, str) or (password and not 8 <= len(password) <= 128) or (not previous and not password):
            raise AppError("La contraseña debe tener entre 8 y 128 caracteres.")
        if password:
            result["password_hash"] = hash_password(password)
        consent = values.get("consentimiento", previous.get("consentimiento", False))
        if not isinstance(consent, bool):
            raise AppError("El consentimiento debe ser verdadero o falso.")
        result["consentimiento"] = consent
        return result

    def save_employee(self, actor_id, values, employee_id=None):
        def operation(state):
            self._actor(state, actor_id, (ROLES[0],))
            previous = self._employee(state, employee_id) if employee_id else None
            result = self._validate_employee(values, previous)
            for employee in state["personal"]:
                if employee["id"] == employee_id:
                    continue
                if normalized(employee["username"]) == normalized(result["username"]):
                    raise AppError("Ya existe un empleado con ese nombre de usuario.", 409)
                if normalized(employee["nombre"] + " " + employee["apellido"]) == normalized(result["nombre"] + " " + result["apellido"]):
                    raise AppError("Ya existe una persona con ese nombre y apellido.", 409)
            if previous:
                if previous["cargo"] == ROLES[0] and result["cargo"] != ROLES[0] and sum(p["activo"] and p["cargo"] == ROLES[0] for p in state["personal"]) <= 1:
                    raise AppError("Debe mantenerse al menos un administrador activo.")
                previous.update(result, actualizado=self.clock().isoformat(), auth_version=previous.get("auth_version", 0) + 1)
                employee = previous
            else:
                employee = dict(result, id=identifier(), activo=True, creado=self.clock().isoformat(), actualizado=self.clock().isoformat(), demo=False)
                state["personal"].append(employee)
            if not employee["consentimiento"]:
                state["rostros"].pop(employee["id"], None)
            self._audit(state, actor_id, "editar_personal" if previous else "crear_personal", employee["username"])
            return self._public(state, employee)
        return self.store.mutate(operation)

    def set_active(self, actor_id, employee_id, active):
        if not isinstance(active, bool):
            raise AppError("Estado inválido.")
        def operation(state):
            self._actor(state, actor_id, (ROLES[0],))
            employee = self._employee(state, employee_id)
            if employee_id == actor_id and not active:
                raise AppError("No puede dar de baja su propia cuenta.")
            if not active and employee["cargo"] == ROLES[0] and sum(p["activo"] and p["cargo"] == ROLES[0] for p in state["personal"]) <= 1:
                raise AppError("Debe mantenerse al menos un administrador activo.")
            employee.update(activo=active, actualizado=self.clock().isoformat(), auth_version=employee.get("auth_version", 0) + 1)
            if not active:
                state["rostros"].pop(employee_id, None)
            self._audit(state, actor_id, "reactivar_personal" if active else "baja_personal", employee["username"])
            return self._public(state, employee)
        return self.store.mutate(operation)

    def change_password(self, actor_id, values):
        if not isinstance(values.get("nueva"), str) or not 8 <= len(values["nueva"]) <= 128:
            raise AppError("La nueva contraseña debe tener entre 8 y 128 caracteres.")
        def operation(state):
            actor = self._actor(state, actor_id)
            if not verify_password(str(values.get("actual", "")), actor["password_hash"]):
                raise AppError("La contraseña actual es incorrecta.", 403)
            actor["password_hash"] = hash_password(values["nueva"])
            actor["auth_version"] = actor.get("auth_version", 0) + 1
            self._audit(state, actor_id, "cambiar_contraseña", "Cambio de contraseña propia")
            return {"mensaje": "Contraseña actualizada. Inicie sesión nuevamente."}
        return self.store.mutate(operation)

    def _last_event(self, state, employee_id):
        now = self.clock()
        events = [(index, e) for index, e in enumerate(state["asistencias"]) if e["empleado_id"] == employee_id and datetime.fromisoformat(e["fecha_hora"]) <= now]
        return max(events, key=lambda item: (item[1]["fecha_hora"], item[0]))[1] if events else None

    def _presence(self, state, employee_id):
        last = self._last_event(state, employee_id)
        if not last or last["accion"] == "salida":
            return "Ausente"
        return "En refrigerio" if last["accion"] == "salida_refrigerio" else "Presente"

    def mark(self, actor_id, employee_id, action, observation="", source="manual", confidence=None):
        if action not in ACTIONS:
            raise AppError("Tipo de marcación inválido.")
        if not isinstance(observation, str) or len(observation) > 500:
            raise AppError("La observación admite hasta 500 caracteres.")
        if source not in ("manual", "facial"):
            raise AppError("Origen de marcación inválido.")
        def operation(state):
            actor = self._actor(state, actor_id)
            employee = self._employee(state, employee_id)
            if not employee["activo"]:
                raise AppError("El empleado está dado de baja.")
            if actor_id != employee_id and actor["cargo"] not in MANAGERS:
                raise AppError("Solo puede registrar su propia asistencia.", 403)
            if actor_id != employee_id and source == "manual" and len(observation.strip()) < 5:
                raise AppError("Indique el motivo de la marcación administrativa (mínimo 5 caracteres).")
            if source == "facial" and (not state["configuracion"]["facial_habilitado"] or employee_id not in state["rostros"]):
                raise AppError("El reconocimiento facial todavía no está habilitado para este empleado.", 409)
            last = self._last_event(state, employee_id)
            previous = last["accion"] if last else "salida"
            allowed = {"salida": {"entrada"}, "entrada": {"salida_refrigerio", "salida"}, "salida_refrigerio": {"entrada_refrigerio", "salida"}, "entrada_refrigerio": {"salida_refrigerio", "salida"}}
            if action not in allowed[previous]:
                raise AppError(f"No corresponde registrar {ACTIONS[action].lower()}. Último estado: {ACTIONS[previous].lower()}.", 409)
            current = self.clock()
            punctual = None
            if action == "entrada":
                candidates = [s for s in state["horarios"] if s["empleado_id"] == employee_id and abs((shift_interval(s)[0] - current).total_seconds()) <= 12 * 3600]
                if candidates:
                    assigned = min(candidates, key=lambda s: abs((shift_interval(s)[0] - current).total_seconds()))
                    late = current > shift_interval(assigned)[0] + timedelta(minutes=state["configuracion"]["tolerancia_minutos"])
                    punctual = not late
                    if late:
                        state["incidencias"].append({"id": identifier(), "empleado_id": employee_id, "fecha": current.date().isoformat(), "tipo": "Tardanza", "detalle": "Ingreso posterior a la tolerancia del turno programado.", "estado": "Pendiente", "resolucion": "", "creado": current.isoformat()})
            event = {"id": identifier(), "empleado_id": employee_id, "fecha_hora": current.isoformat(), "accion": action, "origen": source, "observacion": observation.strip(), "actor_id": actor_id, "puntual": punctual, "nombre": employee["nombre"], "apellido": employee["apellido"], "cargo": employee["cargo"], "username": employee["username"]}
            if confidence is not None:
                event["distancia_facial"] = round(float(confidence), 4)
            state["asistencias"].append(event)
            self._audit(state, actor_id, "marcacion", f"{employee['username']}: {ACTIONS[action]} ({source})")
            return self._event_row(state, event)
        return self.store.mutate(operation)

    def _event_row(self, state, event):
        employee = self._employee(state, event["empleado_id"])
        moment = datetime.fromisoformat(event["fecha_hora"]).astimezone(PERU)
        return dict(event, fecha=moment.date().isoformat(), hora=moment.strftime("%H:%M:%S"), nombre=event.get("nombre", employee["nombre"]), apellido=event.get("apellido", employee["apellido"]), cargo=event.get("cargo", employee["cargo"]), username=event.get("username", employee["username"]), accion_texto=ACTIONS[event["accion"]])

    def history(self, actor_id, filters=None):
        filters = filters or {}
        state = self.store.read()
        actor = self._actor(state, actor_id)
        start = iso_date(filters["desde"], "Desde") if filters.get("desde") else None
        end = iso_date(filters["hasta"], "Hasta") if filters.get("hasta") else None
        if start and end and start > end:
            raise AppError("La fecha inicial no puede ser posterior a la final.")
        if start and end and (end - start).days > 366:
            raise AppError("El rango máximo del reporte es de 366 días.")
        action = filters.get("accion")
        if action and action not in ACTIONS:
            raise AppError("Acción de filtro inválida.")
        people = {p["id"] for p in state["personal"]}
        employee_id = filters.get("empleado_id")
        if employee_id and employee_id not in people:
            raise AppError("No se encontró al empleado del filtro.", 404)
        if actor["cargo"] not in MANAGERS:
            if employee_id and employee_id != actor_id:
                raise AppError("Solo puede consultar su propio historial.", 403)
            employee_id = actor_id
        rows = []
        for event in reversed(state["asistencias"]):
            row = self._event_row(state, event)
            day = date.fromisoformat(row["fecha"])
            if employee_id and row["empleado_id"] != employee_id or action and row["accion"] != action or start and day < start or end and day > end:
                continue
            if filters.get("buscar") and normalized(filters["buscar"]) not in normalized(f"{row['nombre']} {row['apellido']} {row['cargo']} {row['accion_texto']}"):
                continue
            rows.append(row)
        return sorted(rows, key=lambda e: e["fecha_hora"], reverse=True)

    def list_shifts(self, actor_id):
        state = self.store.read()
        actor = self._actor(state, actor_id)
        rows = []
        for shift in state["horarios"]:
            if actor["cargo"] not in MANAGERS and shift["empleado_id"] != actor_id:
                continue
            employee = self._employee(state, shift["empleado_id"])
            rows.append(dict(shift, nombre=employee["nombre"] + " " + employee["apellido"], cargo=employee["cargo"], activo=employee["activo"]))
        return sorted(rows, key=lambda s: (s["fecha"], s["inicio"]), reverse=True)

    def save_shift(self, actor_id, values, shift_id=None):
        def operation(state):
            self._actor(state, actor_id, MANAGERS)
            previous = next((s for s in state["horarios"] if s["id"] == shift_id), None) if shift_id else None
            if shift_id and not previous:
                raise AppError("No se encontró el turno.", 404)
            employee = self._employee(state, values.get("empleado_id"))
            if not employee["activo"]:
                raise AppError("No puede asignar turnos a un empleado dado de baja.")
            day = iso_date(values.get("fecha"))
            shift_type = values.get("turno", "Mañana")
            if shift_type not in SHIFTS and shift_type != "Personalizado":
                raise AppError("Tipo de turno inválido.")
            hours = SHIFTS.get(shift_type, (values.get("inicio"), values.get("fin")))
            result = {"id": shift_id or identifier(), "empleado_id": employee["id"], "fecha": day.isoformat(), "turno": shift_type, "inicio": parse_time(hours[0]), "fin": parse_time(hours[1])}
            if result["inicio"] == result["fin"]:
                raise AppError("Las horas de inicio y fin deben ser diferentes.")
            start, end = shift_interval(result)
            if (end - start).total_seconds() > 16 * 3600:
                raise AppError("Un turno no puede superar las 16 horas.")
            for other in state["horarios"]:
                if other["id"] == shift_id or other["empleado_id"] != employee["id"]:
                    continue
                a, b = shift_interval(other)
                if start < b and a < end:
                    raise AppError("El turno se superpone con otro horario del empleado.", 409)
            if previous:
                previous.update(result)
            else:
                state["horarios"].append(result)
            self._audit(state, actor_id, "guardar_turno", f"{employee['username']} {day}")
            return result
        return self.store.mutate(operation)

    def delete_shift(self, actor_id, shift_id):
        def operation(state):
            self._actor(state, actor_id, MANAGERS)
            previous = next((s for s in state["horarios"] if s["id"] == shift_id), None)
            if not previous:
                raise AppError("No se encontró el turno.", 404)
            state["horarios"].remove(previous)
            self._audit(state, actor_id, "eliminar_turno", previous["fecha"])
            return {"mensaje": "Turno eliminado."}
        return self.store.mutate(operation)

    def _incidents(self, state):
        rows = copy.deepcopy(state["incidencias"])
        today = self.clock().date()
        for shift in state["horarios"]:
            start, end = shift_interval(shift)
            employee = self._employee(state, shift["empleado_id"])
            if not employee["activo"] or start.date() != today or self.clock() <= start + timedelta(minutes=state["configuracion"]["tolerancia_minutos"]):
                continue
            attended = any(e["empleado_id"] == employee["id"] and e["accion"] == "entrada" and start - timedelta(hours=2) <= datetime.fromisoformat(e["fecha_hora"]) <= end for e in state["asistencias"])
            generated_id = "ausencia-" + shift["id"]
            if not attended and not any(i["id"] == generated_id for i in rows):
                rows.append({"id": generated_id, "empleado_id": employee["id"], "fecha": shift["fecha"], "tipo": "Ausencia", "detalle": "No hay ingreso registrado para el turno programado de hoy.", "estado": "Pendiente", "resolucion": "", "creado": start.isoformat(), "automatica": True})
        for employee in state["personal"]:
            last = self._last_event(state, employee["id"]) if employee["activo"] else None
            if not last or last["accion"] != "salida_refrigerio":
                continue
            elapsed = (self.clock() - datetime.fromisoformat(last["fecha_hora"])).total_seconds() / 60
            generated_id = "refrigerio-" + last["id"]
            if elapsed > state["configuracion"]["minutos_refrigerio"] and not any(i["id"] == generated_id for i in rows):
                rows.append({"id": generated_id, "empleado_id": employee["id"], "fecha": self.clock().date().isoformat(), "tipo": "Refrigerio prolongado", "detalle": f"Refrigerio de {int(elapsed)} minutos; supera el límite configurado.", "estado": "Pendiente", "resolucion": "", "creado": last["fecha_hora"], "automatica": True})
        for incident in rows:
            employee = self._employee(state, incident["empleado_id"])
            incident["nombre"] = employee["nombre"] + " " + employee["apellido"]
            incident["cargo"] = employee["cargo"]
        return sorted(rows, key=lambda i: i["creado"], reverse=True)

    def incidents(self, actor_id):
        state = self.store.read()
        actor = self._actor(state, actor_id)
        return [i for i in self._incidents(state) if actor["cargo"] in MANAGERS or i["empleado_id"] == actor_id]

    def save_incident(self, actor_id, values, incident_id=None):
        def operation(state):
            self._actor(state, actor_id, MANAGERS)
            if incident_id:
                record = next((i for i in state["incidencias"] if i["id"] == incident_id), None)
                if not record:
                    generated = next((i for i in self._incidents(state) if i["id"] == incident_id), None)
                    if not generated:
                        raise AppError("No se encontró la incidencia.", 404)
                    record = {k: v for k, v in generated.items() if k not in ("nombre", "cargo")}
                    state["incidencias"].append(record)
                status = values.get("estado", "Resuelta")
                resolution = values.get("resolucion", "")
                if status not in ("Pendiente", "Resuelta", "Justificada") or not isinstance(resolution, str) or len(resolution) > 1000:
                    raise AppError("Estado o resolución inválidos.")
                if status != "Pendiente" and len(resolution.strip()) < 5:
                    raise AppError("Describa la resolución con al menos 5 caracteres.")
                record.update(estado=status, resolucion=resolution.strip())
            else:
                self._employee(state, values.get("empleado_id"))
                detail = values.get("detalle", "")
                kind = values.get("tipo", "Otra")
                if kind not in ("Tardanza", "Ausencia", "Reconocimiento fallido", "Fuera de turno", "Otra") or not isinstance(detail, str) or not 5 <= len(detail.strip()) <= 1000:
                    raise AppError("Indique un tipo válido y un detalle de 5 a 1000 caracteres.")
                record = {"id": identifier(), "empleado_id": values["empleado_id"], "fecha": iso_date(values.get("fecha", self.clock().date().isoformat())).isoformat(), "tipo": kind, "detalle": detail.strip(), "estado": "Pendiente", "resolucion": "", "creado": self.clock().isoformat()}
                state["incidencias"].append(record)
            self._audit(state, actor_id, "guardar_incidencia", record["tipo"])
            return record
        return self.store.mutate(operation)

    def summary(self, actor_id):
        state = self.store.read()
        actor = self._actor(state, actor_id)
        people = [p for p in state["personal"] if p["activo"] and (actor["cargo"] in MANAGERS or p["id"] == actor_id)]
        today = self.clock().date().isoformat()
        ids = {p["id"] for p in people}
        entries = [e for e in state["asistencias"] if e["empleado_id"] in ids and e["accion"] == "entrada" and e["fecha_hora"].startswith(today) and e.get("puntual") is not None]
        incidents = [i for i in self._incidents(state) if i["empleado_id"] in ids and i["estado"] == "Pendiente"]
        return {"personal_presente": sum(self._presence(state, p["id"]) == "Presente" for p in people), "en_refrigerio": sum(self._presence(state, p["id"]) == "En refrigerio" for p in people), "tasa_puntualidad": round(100 * sum(e["puntual"] for e in entries) / len(entries)) if entries else 0, "alertas_activas": len(incidents), "personal_activo": len(people), "hora_servidor": self.clock().isoformat(), "facial_habilitado": state["configuracion"]["facial_habilitado"]}

    def predictions(self, actor_id):
        state = self.store.read()
        actor = self._actor(state, actor_id)
        now = self.clock()
        people = [p for p in state["personal"] if p["activo"] and (actor["cargo"] in MANAGERS or p["id"] == actor_id)]
        ids = {p["id"] for p in people}
        coverage = []
        for shift in state["horarios"]:
            if shift["empleado_id"] not in ids or shift["fecha"] != now.date().isoformat():
                continue
            employee = self._employee(state, shift["empleado_id"])
            start, end = shift_interval(shift)
            coverage.append({"nombre": employee["nombre"] + " " + employee["apellido"], "turno": shift["turno"], "horario": shift["inicio"] + " – " + shift["fin"], "estado": self._presence(state, employee["id"]) if start <= now <= end else ("Finalizado" if now > end else "Programado")})
        overtime = []
        for employee in people:
            if self._presence(state, employee["id"]) == "Ausente":
                continue
            events = sorted([e for e in state["asistencias"] if e["empleado_id"] == employee["id"]], key=lambda e: e["fecha_hora"], reverse=True)
            entry = next((e for e in events if e["accion"] == "entrada"), None)
            if entry:
                hours = (now - datetime.fromisoformat(entry["fecha_hora"])).total_seconds() / 3600
                if hours >= state["configuracion"]["jornada_max_horas"]:
                    overtime.append({"nombre": employee["nombre"] + " " + employee["apellido"], "horas": round(hours, 1), "detalle": "Jornada superior al límite configurado"})
        by_role = [{"cargo": role, "presente": sum(p["cargo"] == role and self._presence(state, p["id"]) == "Presente" for p in people), "refrigerio": sum(p["cargo"] == role and self._presence(state, p["id"]) == "En refrigerio" for p in people), "ausente": sum(p["cargo"] == role and self._presence(state, p["id"]) == "Ausente" for p in people)} for role in ROLES]
        return {"cobertura": coverage, "sobretiempo": overtime, "disponibilidad": by_role, "metodo": "Reglas de turnos y duración de jornada"}

    def settings(self, actor_id):
        state = self.store.read()
        self._actor(state, actor_id)
        return dict(DEFAULT_SETTINGS, **state["configuracion"])

    def update_settings(self, actor_id, values):
        def operation(state):
            self._actor(state, actor_id, (ROLES[0],))
            settings = state["configuracion"]
            for name, low, high in [("tolerancia_minutos", 0, 120), ("jornada_max_horas", 1, 24), ("minutos_refrigerio", 5, 180), ("tema", 0, 99), ("camara_indice", 0, 9)]:
                if name in values:
                    value = values[name]
                    if isinstance(value, bool) or not str(value).isdigit() or not low <= int(value) <= high:
                        raise AppError(f"{name}: ingrese un número entero entre {low} y {high}.")
                    settings[name] = int(value)
            if "hospital" in values:
                text = values["hospital"]
                if not isinstance(text, str) or not 3 <= len(text.strip()) <= 100:
                    raise AppError("El nombre del hospital debe tener entre 3 y 100 caracteres.")
                settings["hospital"] = text.strip()
            if "umbral_rostro" in values:
                try:
                    number = float(values["umbral_rostro"])
                except (TypeError, ValueError):
                    raise AppError("Umbral facial inválido.")
                if not math.isfinite(number) or not 0.3 <= number <= 0.65:
                    raise AppError("El umbral facial debe estar entre 0.30 y 0.65.")
                settings["umbral_rostro"] = number
            if "facial_habilitado" in values:
                if not isinstance(values["facial_habilitado"], bool):
                    raise AppError("Estado facial inválido.")
                settings["facial_habilitado"] = values["facial_habilitado"]
            self._audit(state, actor_id, "configuracion", "Actualización de parámetros")
            return settings
        return self.store.mutate(operation)

    def audit(self, actor_id):
        state = self.store.read()
        self._actor(state, actor_id, (ROLES[0],))
        names = {p["id"]: p["username"] for p in state["personal"]}
        return [dict(row, usuario=names.get(row["actor_id"], "Sistema")) for row in reversed(state["auditoria"][-300:])]

    def backup(self, actor_id):
        state = self.store.read()
        self._actor(state, actor_id, (ROLES[0],))
        return state
