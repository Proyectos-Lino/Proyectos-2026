import sqlite3
from contextlib import contextmanager
from pathlib import Path

from .settings import DATA_DIR


DB_PATH = DATA_DIR / "neuro.db"


@contextmanager
def connect_db():
    Path(DB_PATH).parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(DB_PATH, timeout=5)
    connection.row_factory = sqlite3.Row
    try:
        with connection:
            yield connection
    finally:
        connection.close()


def initialize():
    with connect_db() as connection:
        connection.executescript("""
            CREATE TABLE IF NOT EXISTS pacientes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                dni TEXT UNIQUE NOT NULL,
                nombres TEXT NOT NULL,
                apellido_paterno TEXT NOT NULL,
                apellido_materno TEXT NOT NULL,
                edad INTEGER NOT NULL,
                sintomas TEXT NOT NULL,
                urgencia TEXT NOT NULL,
                direccion TEXT,
                distrito TEXT,
                provincia TEXT,
                fecha TEXT DEFAULT CURRENT_TIMESTAMP
            );
            CREATE TABLE IF NOT EXISTS solicitudes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                habitacion TEXT NOT NULL,
                tipo TEXT NOT NULL,
                fecha TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                atendida_en TEXT
            );
        """)
        # Retira solo los registros creados por las fichas de la versión anterior.
        connection.execute("""
            DELETE FROM pacientes
            WHERE dni GLOB '0000000[1-4]' AND direccion = 'Domicilio de demostración'
        """)


def get_patient_by_dni(dni):
    with connect_db() as connection:
        return connection.execute("SELECT id FROM pacientes WHERE dni = ?", (dni,)).fetchone()


def insert_patient(patient, symptoms, priority):
    with connect_db() as connection:
        cursor = connection.execute("""
            INSERT INTO pacientes (
                dni, nombres, apellido_paterno, apellido_materno, edad, sintomas,
                urgencia, direccion, distrito, provincia
            )
            VALUES (
                :dni, :nombres, :apellido_paterno, :apellido_materno, :edad, :sintomas,
                :urgencia, :direccion, :distrito, :provincia
            )
            ON CONFLICT(dni) DO NOTHING
        """, {**patient, "sintomas": symptoms, "urgencia": priority})
        return cursor.rowcount == 1


def list_patients(search="", priority="", start="", end=""):
    query = "SELECT * FROM pacientes WHERE 1=1"
    params = []
    if search:
        text = search.strip().lower()
        # SQLite lower() only case-folds ASCII. Use Python for the name and DNI search.
    for field, condition in ((priority, "urgencia = ?"), (start, "date(fecha, 'localtime') >= ?"),
                             (end, "date(fecha, 'localtime') <= ?")):
        if field:
            query += " AND " + condition
            params.append(field)
    with connect_db() as connection:
        rows = [dict(row) for row in connection.execute(query + " ORDER BY fecha DESC, id DESC", params)]
    if search:
        rows = [row for row in rows if text in f"{row['dni']} {row['nombres']} {row['apellido_paterno']} {row['apellido_materno']}".lower()]
    return rows


def add_request(room, kind):
    with connect_db() as connection:
        return connection.execute(
            "INSERT INTO solicitudes (habitacion, tipo) VALUES (?, ?)", (room, kind)
        ).lastrowid


def list_requests(status="pending"):
    condition = {"pending": "WHERE atendida_en IS NULL", "done": "WHERE atendida_en IS NOT NULL",
                 "all": ""}[status]
    with connect_db() as connection:
        rows = connection.execute(f"""
            SELECT id, habitacion, tipo, fecha, atendida_en FROM solicitudes
            {condition} ORDER BY (atendida_en IS NOT NULL), fecha DESC, id DESC
        """)
        return [dict(row) for row in rows]


def attend_request(request_id):
    with connect_db() as connection:
        cursor = connection.execute("""
            UPDATE solicitudes SET atendida_en = CURRENT_TIMESTAMP
            WHERE id = ? AND atendida_en IS NULL
        """, (request_id,))
        return cursor.rowcount == 1


def aggregate_snapshot():
    with connect_db() as connection:
        patients = connection.execute("""
            SELECT COUNT(*) AS registros,
                   COUNT(CASE WHEN urgencia = 'Alta' THEN 1 END) AS alta,
                   COUNT(CASE WHEN urgencia = 'Normal' THEN 1 END) AS normal,
                   COUNT(CASE WHEN date(fecha, 'localtime') = date('now', 'localtime') THEN 1 END) AS hoy
            FROM pacientes
        """).fetchone()
        requests = connection.execute("""
            SELECT COUNT(*) AS solicitudes,
                   COUNT(CASE WHEN atendida_en IS NULL THEN 1 END) AS pendientes,
                   COUNT(CASE WHEN atendida_en IS NOT NULL THEN 1 END) AS atendidas
            FROM solicitudes
        """).fetchone()
        return {**dict(patients), **dict(requests)}
