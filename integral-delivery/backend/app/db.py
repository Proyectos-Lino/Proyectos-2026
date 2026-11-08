"""SQLite central: transacciones atómicas y datos persistentes fuera del código."""
import json
import os
import sqlite3
from contextlib import contextmanager
from pathlib import Path

DATA_DIR = Path(os.getenv("DATA_DIR", str(Path(__file__).resolve().parents[1] / "data")))
DEFAULT_SETTINGS = {
    "name": "Mi Negocio", "description": "Todo lo que necesitas, hasta tu puerta.",
    "phone": "", "email": "", "address": "", "currency": "PEN", "symbol": "S/",
    "color": "#EC6838", "logo_url": "", "hours": "Lunes a sábado · 9:00 a 20:00",
    "is_open": True, "minimum_cents": 0,
    "zones": [{"id": "centro", "name": "Zona centro", "fee_cents": 500},
              {"id": "alrededores", "name": "Alrededores", "fee_cents": 800}],
    "payment_methods": ["cash", "transfer"],
    "bank_details": "Consulta al negocio los datos para realizar tu transferencia.",
}

def connect():
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    con = sqlite3.connect(DATA_DIR / "integral.sqlite3", timeout=20)
    con.row_factory = sqlite3.Row
    con.execute("PRAGMA foreign_keys=ON")
    con.execute("PRAGMA busy_timeout=20000")
    return con

@contextmanager
def transaction(write=False):
    con = connect()
    try:
        if write:
            con.execute("BEGIN IMMEDIATE")
        yield con
        con.commit()
    except Exception:
        con.rollback()
        raise
    finally:
        con.close()

def settings(con):
    return json.loads(con.execute("SELECT value FROM settings WHERE id=1").fetchone()[0])

def initialize(hash_password, now):
    with transaction() as c:
        c.execute("PRAGMA journal_mode=WAL")
        c.executescript("""
        CREATE TABLE IF NOT EXISTS settings(id INTEGER PRIMARY KEY CHECK(id=1),value TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS users(
          id INTEGER PRIMARY KEY, name TEXT NOT NULL,email TEXT NOT NULL UNIQUE COLLATE NOCASE,
          phone TEXT NOT NULL DEFAULT '',password TEXT NOT NULL,role TEXT NOT NULL,
          active INTEGER NOT NULL DEFAULT 1,created_at TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS sessions(
          token TEXT PRIMARY KEY,user_id INTEGER REFERENCES users(id),expires_at TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS categories(
          id INTEGER PRIMARY KEY,name TEXT NOT NULL UNIQUE COLLATE NOCASE,icon TEXT NOT NULL DEFAULT 'shop',active INTEGER NOT NULL DEFAULT 1);
        CREATE TABLE IF NOT EXISTS products(
          id INTEGER PRIMARY KEY,category_id INTEGER REFERENCES categories(id),name TEXT NOT NULL,
          description TEXT NOT NULL DEFAULT '',price_cents INTEGER NOT NULL CHECK(price_cents>=0),
          stock INTEGER NOT NULL CHECK(stock>=0),image_url TEXT NOT NULL DEFAULT '',active INTEGER NOT NULL DEFAULT 1);
        CREATE TABLE IF NOT EXISTS addresses(
          id INTEGER PRIMARY KEY,user_id INTEGER REFERENCES users(id),label TEXT NOT NULL,
          street TEXT NOT NULL,district TEXT NOT NULL DEFAULT '',reference TEXT NOT NULL DEFAULT '');
        CREATE TABLE IF NOT EXISTS orders(
          id INTEGER PRIMARY KEY,code TEXT NOT NULL UNIQUE,customer_id INTEGER REFERENCES users(id),
          driver_id INTEGER REFERENCES users(id),status TEXT NOT NULL DEFAULT 'pending',
          payment_method TEXT NOT NULL,payment_status TEXT NOT NULL DEFAULT 'pending',
          subtotal_cents INTEGER NOT NULL,delivery_cents INTEGER NOT NULL,total_cents INTEGER NOT NULL,
          address TEXT NOT NULL,zone TEXT NOT NULL,notes TEXT NOT NULL DEFAULT '',
          created_at TEXT NOT NULL,updated_at TEXT NOT NULL,idempotency_key TEXT NOT NULL,
          UNIQUE(customer_id,idempotency_key));
        CREATE TABLE IF NOT EXISTS order_items(
          id INTEGER PRIMARY KEY,order_id INTEGER REFERENCES orders(id),product_id INTEGER REFERENCES products(id),
          name TEXT NOT NULL,quantity INTEGER NOT NULL,price_cents INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS order_events(
          id INTEGER PRIMARY KEY,order_id INTEGER REFERENCES orders(id),user_id INTEGER REFERENCES users(id),
          kind TEXT NOT NULL,message TEXT NOT NULL,created_at TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS audit(
          id INTEGER PRIMARY KEY,user_id INTEGER REFERENCES users(id),action TEXT NOT NULL,created_at TEXT NOT NULL);
        CREATE INDEX IF NOT EXISTS orders_customer ON orders(customer_id,created_at);
        CREATE INDEX IF NOT EXISTS orders_driver ON orders(driver_id,status);
        CREATE INDEX IF NOT EXISTS events_order ON order_events(order_id,id);
        PRAGMA user_version=1;
        """)
        c.execute("INSERT OR IGNORE INTO settings VALUES(1,?)", (json.dumps(DEFAULT_SETTINGS),))
        if not c.execute("SELECT 1 FROM users LIMIT 1").fetchone():
            production = os.getenv("APP_ENV", "development") == "production"
            password = os.getenv("ADMIN_PASSWORD", "" if production else "Admin2026!")
            if len(password) < 10:
                raise RuntimeError("Define ADMIN_PASSWORD con al menos 10 caracteres antes de iniciar en producción.")
            c.execute("INSERT INTO users(name,email,password,role,created_at) VALUES(?,?,?,?,?)",
                      ("Administrador", os.getenv("ADMIN_EMAIL", "admin@integral.local"),hash_password(password),"admin",now()))
            if os.getenv("SEED_DEMO", "false" if production else "true").lower() == "true":
                for name,email,pw,role,phone in [
                    ("Valeria Torres","cliente@demo.local","Cliente2026!","customer","999111222"),
                    ("Diego Ramos","repartidor@demo.local","Reparto2026!","driver","999333444")]:
                    c.execute("INSERT INTO users(name,email,password,role,phone,created_at) VALUES(?,?,?,?,?,?)",(name,email,hash_password(pw),role,phone,now()))
                for name,icon in [("Despensa","basket"),("Hogar","home"),("Tecnología","devices"),("Cuidado personal","spa")]:
                    c.execute("INSERT INTO categories(name,icon) VALUES(?,?)",(name,icon))
                for cat,name,desc,price,stock in [
                    (1,"Café de especialidad","Café tostado y molido · presentación de 250 g",2250,25),
                    (1,"Miel natural","Selección artesanal · frasco de 500 g",1800,18),
                    (1,"Granola con frutos secos","Una buena forma de comenzar tu mañana · 400 g",1450,30),
                    (2,"Botella térmica","Acero inoxidable · capacidad de 750 ml",3900,12),
                    (2,"Kit de organización","Tres contenedores prácticos para tu hogar",2800,9),
                    (3,"Audífonos inalámbricos","Con estuche de carga y conexión Bluetooth",6990,8),
                    (3,"Cable USB-C","Carga y conexión · longitud de 1 metro",1590,22),
                    (4,"Jabón artesanal","Cuidado diario con aroma suave · 100 g",950,35)]:
                    c.execute("INSERT INTO products(category_id,name,description,price_cents,stock) VALUES(?,?,?,?,?)",(cat,name,desc,price,stock))
                c.execute("INSERT INTO addresses(user_id,label,street,district,reference) VALUES(2,'Casa','Av. Los Maestros 120','Ica','Puerta blanca')")
