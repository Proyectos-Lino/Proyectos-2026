"""Aplicación de escritorio basada en las pantallas de la grabación."""
from __future__ import annotations

import colorsys
import json
import logging
import queue
import threading
import time
import tkinter as tk
import webbrowser
from pathlib import Path
from tkinter import filedialog, messagebox, ttk

from .reports import export
from .service import ACTIONS, MANAGERS, ROLES, SHIFTS, AppError
from .storage import StorageError

FONT = "Segoe UI"
LOGGER = logging.getLogger("biotrack.desktop")


class CameraView:
    """La cámara solo se abre por solicitud y con la función habilitada."""
    def __init__(self, parent, facial):
        self.parent, self.facial = parent, facial
        # Reservar primero los controles: la vista se adapta al espacio restante.
        self.controls = ttk.Frame(parent)
        self.controls.pack(side="top", fill="x", padx=8, pady=7)
        self.status = ttk.Label(parent, text="La cámara está apagada.", wraplength=340)
        self.status.pack(side="bottom", fill="x", padx=8, pady=(2, 8))
        self.preview = tk.Canvas(parent, width=320, height=220, bg="#34485c", highlightthickness=0, cursor="hand2")
        self.preview.pack(fill="both", expand=True, padx=7, pady=7)
        self.last_rgb = None
        self.preview.bind("<Configure>", lambda event: self._draw_preview())
        self.preview.bind("<Button-1>", lambda event: self.start() if not self.running else None)
        self.messages, self.running, self.frame, self.photo = queue.Queue(maxsize=2), False, None, None
        self.after_id = None
        self.worker = None
        self.stop_event = threading.Event()

    def _draw_preview(self):
        width, height = max(1, self.preview.winfo_width()), max(1, self.preview.winfo_height())
        self.preview.delete("all")
        if self.last_rgb is None:
            self.preview.create_text(width // 2, height // 2, text="Vista de cámara\n\nPulse Iniciar cámara", font=(FONT, 15), fill="#dde7f3", width=max(1, width - 20), justify="center")
        else:
            image = self.Image.fromarray(self.last_rgb)
            image.thumbnail((max(1, width - 8), max(1, height - 8)))
            self.photo = self.ImageTk.PhotoImage(image, master=self.preview)
            self.preview.create_image(width // 2, height // 2, image=self.photo)

    def start(self):
        try:
            self.facial._enabled()
            import cv2
            from PIL import Image, ImageTk
        except (AppError, ImportError) as exc:
            messagebox.showinfo("Cámara", str(exc), parent=self.parent)
            return
        if self.running:
            return
        if self.worker and self.worker.is_alive():
            self.status.configure(text="Espere un momento a que se libere la cámara antes de reiniciarla.")
            return
        self.running = True
        self.frame = None
        self.last_rgb = None
        self.messages = queue.Queue(maxsize=2)
        self.stop_event = threading.Event()
        self.cv2, self.Image, self.ImageTk = cv2, Image, ImageTk
        self.status.configure(text="Iniciando cámara…")
        self.worker = threading.Thread(target=self._worker, daemon=True)
        self.worker.start()
        self._poll()

    def _worker(self):
        from .camera import open_camera
        camera = None
        try:
            index = self.facial.service.store.read()["configuracion"].get("camara_indice", 0)
            camera = open_camera(index, self.cv2)
            detector = self.cv2.CascadeClassifier(self.cv2.data.haarcascades + "haarcascade_frontalface_default.xml")
            while not self.stop_event.is_set():
                valid, frame = camera.read()
                if self.stop_event.is_set():
                    break
                if not valid:
                    self._send(("error", "No se pudo obtener una imagen de la cámara."))
                    break
                self.frame = frame.copy()
                display = frame.copy()
                if not detector.empty():
                    faces = detector.detectMultiScale(self.cv2.cvtColor(frame, self.cv2.COLOR_BGR2GRAY), 1.2, 5)
                    for x, y, width, height in faces:
                        self.cv2.rectangle(display, (x, y), (x + width, y + height), (40, 230, 80), 2)
                self._send(("frame", self.cv2.cvtColor(display, self.cv2.COLOR_BGR2RGB)))
                self.stop_event.wait(0.05)
        except Exception as exc:
            self._send(("error", str(exc)))
        finally:
            if camera is not None:
                camera.release()

    def _send(self, value):
        try:
            self.messages.put_nowait(value)
        except queue.Full:
            if value[0] == "error":
                try:
                    self.messages.get_nowait()
                    self.messages.put_nowait(value)
                except (queue.Empty, queue.Full):
                    pass

    def _poll(self):
        if not self.running or not self.parent.winfo_exists():
            return
        try:
            kind, value = self.messages.get_nowait()
            if kind == "error":
                self.status.configure(text=value)
                self.stop()
                return
            self.last_rgb = value
            self._draw_preview()
            self.status.configure(text="Mire directamente a la cámara. Una persona a la vez.")
        except queue.Empty:
            pass
        self.after_id = self.parent.after(75, self._poll)

    def capture(self):
        if self.frame is None or not self.running:
            raise AppError("Primero inicie la cámara y espere una imagen válida.")
        ok, jpeg = self.cv2.imencode(".jpg", self.frame)
        if not ok:
            raise AppError("No se pudo capturar la imagen.")
        return jpeg.tobytes()

    def stop(self):
        self.stop_event.set()
        self.running, self.frame = False, None
        self.last_rgb, self.photo = None, None
        try:
            if self.preview.winfo_exists():
                self._draw_preview()
        except tk.TclError:
            pass
        if self.after_id:
            try:
                self.parent.after_cancel(self.after_id)
            except tk.TclError:
                pass
            self.after_id = None


class DesktopApplication:
    def __init__(self, service, facial, transport, portal_url, data_dir, portal_opener=None):
        self.service, self.facial, self.transport = service, facial, transport
        self.portal_url, self.data_dir = portal_url, Path(data_dir)
        self.portal_opener = portal_opener or (lambda: webbrowser.open(self.portal_url))
        try:
            import ttkbootstrap
            self.root = ttkbootstrap.Window(themename="darkly")
            self.bootstrap_available = True
        except ImportError:
            self.root = tk.Tk()
            self.bootstrap_available = False
        self.root.title("Sistema de Control de Asistencia Hospitalaria · BIOTRACK")
        self.root.geometry("1280x800")
        self.root.minsize(1020, 680)
        self.root.option_add("*Font", (FONT, 11))
        self.style = ttk.Style(self.root)
        if not self.bootstrap_available:
            self.style.theme_use("clam")
        self.actor_id, self.authenticated_at = None, 0
        self.actor_version = 0
        self.windows, self.cameras, self.action_buttons = [], [], []
        self.preference_file = self.data_dir / "preferencias.json"
        self.theme_index = 0
        try:
            self.theme_index = int(json.loads(self.preference_file.read_text(encoding="utf-8")).get("tema", 0)) % 100
        except (OSError, ValueError, TypeError):
            pass
        self._palette(self.theme_index)
        self._build()
        self.set_theme(self.theme_index, persist=False)
        self._refresh()
        self.root.protocol("WM_DELETE_WINDOW", self.close)

    def _palette(self, index):
        if index == 0:
            self.colors = {"bg": "#2e2626", "panel": "#372e2e", "accent": "#ad5555", "blue": "#354b5e", "border": "#5785aa", "text": "#e5e3e3", "muted": "#b2adae", "green": "#60ae62"}
        else:
            hue = (index * 0.0618) % 1
            def color(light, saturation):
                rgb = colorsys.hls_to_rgb(hue, light, saturation)
                return "#" + "".join(f"{round(c * 255):02x}" for c in rgb)
            self.colors = {"bg": color(.13, .16), "panel": color(.17, .17), "accent": color(.46, .47), "blue": color(.25, .2), "border": color(.59, .44), "text": "#ececf0", "muted": "#b6b9c3", "green": "#59ae79"}

    def _build(self):
        c = self.colors
        self.root.configure(bg=c["bg"])
        self.root.rowconfigure(1, weight=1)
        self.root.columnconfigure(0, weight=1)
        header = tk.Frame(self.root, bg=c["bg"], padx=24, pady=17)
        header.grid(row=0, column=0, sticky="ew")
        tk.Label(header, text="✚", bg=c["bg"], fg=c["text"], font=(FONT, 38)).pack(side="left", padx=(0, 20))
        titles = tk.Frame(header, bg=c["bg"])
        titles.pack(side="left", fill="x", expand=True)
        tk.Label(titles, text="Sistema de Control de Asistencia", bg=c["bg"], fg=c["text"], font=(FONT, 29, "bold")).pack(anchor="w")
        self.clock_label = tk.Label(titles, bg=c["bg"], fg=c["muted"], font=(FONT, 11))
        self.clock_label.pack(anchor="w", pady=(8, 0))
        themes = ttk.Frame(header)
        themes.pack(side="right", padx=(12, 0), anchor="n", pady=8)
        ttk.Button(themes, text="◀", width=3, command=lambda: self.set_theme((self.theme_index - 1) % 100)).pack(side="left", padx=3)
        self.theme_var = tk.StringVar(value=f"Tema #{self.theme_index + 1}")
        self.theme_combo = ttk.Combobox(themes, textvariable=self.theme_var, values=[f"Tema #{n + 1}" for n in range(100)], state="readonly", width=13)
        self.theme_combo.pack(side="left")
        self.theme_combo.bind("<<ComboboxSelected>>", lambda event: self.set_theme(self.theme_combo.current()))
        ttk.Button(themes, text="▶", width=3, command=lambda: self.set_theme((self.theme_index + 1) % 100)).pack(side="left", padx=3)
        main = tk.Frame(self.root, bg=c["bg"])
        main.grid(row=1, column=0, sticky="nsew", padx=16)
        main.columnconfigure(1, weight=1)
        main.rowconfigure(0, weight=1)
        side = tk.Frame(main, bg=c["bg"], width=315)
        side.grid(row=0, column=0, sticky="ns", padx=(0, 18))
        side.grid_propagate(False)
        side.rowconfigure(0, weight=1)
        side.columnconfigure(0, weight=1)
        canvas = tk.Canvas(side, bg=c["bg"], highlightthickness=0, width=303)
        scrollbar = ttk.Scrollbar(side, orient="vertical", command=canvas.yview)
        canvas.configure(yscrollcommand=scrollbar.set)
        canvas.grid(row=0, column=0, sticky="nsew")
        scrollbar.grid(row=0, column=1, sticky="ns")
        inner = tk.Frame(canvas, bg=c["bg"])
        window = canvas.create_window((0, 0), window=inner, anchor="nw", width=295)
        inner.bind("<Configure>", lambda event: canvas.configure(scrollregion=canvas.bbox("all")))
        canvas.bind("<Configure>", lambda event: canvas.itemconfigure(window, width=event.width - 4))
        tk.Label(inner, text="Acciones Diarias", bg=c["bg"], fg=c["text"], font=(FONT, 14, "bold")).pack(anchor="w", pady=(6, 14))
        for symbol, action in [("✔", "entrada"), ("◉", "salida_refrigerio"), ("♜", "entrada_refrigerio"), ("✕", "salida")]:
            label = {"entrada": "Registrar Entrada", "salida_refrigerio": "Salida a Refrigerio", "entrada_refrigerio": "Entrada de Refrigerio", "salida": "Registrar Salida"}[action]
            button = tk.Button(inner, text=f"{symbol}   {label}", command=lambda selected=action: self.show_marking(selected), bg=c["accent"], fg="white", activebackground=c["border"], activeforeground="white", relief="solid", bd=1, highlightbackground=c["border"], highlightthickness=1, font=(FONT, 12, "bold"), pady=13, cursor="hand2")
            button.pack(fill="x", pady=(0, 9), padx=1)
            self.action_buttons.append(button)
        ttk.Separator(inner).pack(fill="x", pady=12)
        tk.Label(inner, text="Administración", bg=c["bg"], fg=c["text"], font=(FONT, 14, "bold")).pack(anchor="w", pady=(0, 13))
        options = [("♙  Registrar Personal", self.show_employee, (ROLES[0],)), ("♟  Ver Personal", self.show_staff, MANAGERS), ("▤  Ver Registros", self.show_records, MANAGERS), ("▥  Ver Predicciones", self.show_predictions, MANAGERS), ("▦  Gestionar Horarios", self.show_schedules, MANAGERS), ("⇩  Reportes", lambda: self.show_records(report_mode=True), MANAGERS), ("⚑  Incidencias", self.show_incidents, MANAGERS), ("⚙  Configuración", self.show_settings, (ROLES[0],))]
        for label, callback, roles in options:
            ttk.Button(inner, text=label, style="Admin.TButton", command=lambda fn=callback, allowed=roles: self._protected(fn, allowed)).pack(fill="x", pady=(0, 7), ipady=3)
        ttk.Separator(inner).pack(fill="x", pady=10)
        ttk.Button(inner, text="Abrir portal web  ↗", command=lambda: self._run_safe(self.portal_opener, self.root)).pack(fill="x", pady=6, ipady=3)
        ttk.Button(inner, text="Bloquear administración", command=self.lock).pack(fill="x", pady=6, ipady=3)
        body = tk.Frame(main, bg=c["bg"])
        body.grid(row=0, column=1, sticky="nsew")
        body.columnconfigure(0, weight=1)
        stats = tk.Frame(body, bg=c["bg"])
        stats.grid(row=0, column=0, sticky="ew", pady=(4, 12))
        self.stat_vars = {}
        for index, (label, key, symbol) in enumerate([("Personal presente", "personal_presente", "♙"), ("Tasa de puntualidad", "tasa_puntualidad", "◷"), ("Alertas activas", "alertas_activas", "♧")]):
            stats.columnconfigure(index, weight=1)
            card = tk.Frame(stats, bg=c["panel"], padx=12, pady=12)
            card.grid(row=0, column=index, sticky="nsew", padx=(0, 9 if index < 2 else 0))
            tk.Label(card, text=symbol, bg=c["panel"], fg=c["text"], font=(FONT, 24)).pack(side="left", padx=(0, 9))
            words = tk.Frame(card, bg=c["panel"])
            words.pack(side="left", fill="x", expand=True)
            tk.Label(words, text=label, bg=c["panel"], fg=c["muted"], font=(FONT, 10)).pack(anchor="w")
            self.stat_vars[key] = tk.StringVar(value="0%" if key == "tasa_puntualidad" else "0")
            tk.Label(words, textvariable=self.stat_vars[key], bg=c["panel"], fg=c["text"], font=(FONT, 21, "bold")).pack(anchor="w")
        tips = tk.LabelFrame(body, text="Sugerencias rápidas", bg=c["blue"], fg=c["border"], highlightbackground=c["border"], highlightthickness=1, bd=0, padx=12, pady=12, font=(FONT, 11))
        tips.grid(row=1, column=0, sticky="ew")
        for text in ["• Usa el selector para cambiar entre los 100 temas.", "• Doble clic en las listas abre los detalles del registro.", "• Las estadísticas se actualizan automáticamente."]:
            tk.Label(tips, text=text, bg=c["blue"], fg=c["text"], font=(FONT, 11), anchor="w", justify="left").pack(anchor="w", pady=3)
        self.facial_status = tk.Label(body, text="", bg=c["bg"], fg=c["muted"], font=(FONT, 10), anchor="w", justify="left")
        self.facial_status.grid(row=2, column=0, sticky="ew", pady=18)
        body.rowconfigure(3, weight=1)
        footer = tk.Frame(self.root, bg=c["bg"], padx=17, pady=9)
        footer.grid(row=2, column=0, sticky="ew")
        self.brand_label = tk.Label(footer, text="Hospital Félix Torrealva © 2026", bg=c["bg"], fg=c["muted"], font=(FONT, 10))
        self.brand_label.pack(side="left")
        self.session_label = tk.Label(footer, text="Administración bloqueada", bg=c["bg"], fg=c["muted"], font=(FONT, 10))
        self.session_label.pack(side="left", padx=20)
        self.theme_label = tk.Label(footer, text="", bg=c["bg"], fg=c["muted"], font=(FONT, 10))
        self.theme_label.pack(side="right")

    def set_theme(self, index, persist=True):
        previous = dict(self.colors)
        self.theme_index = index
        self._palette(index)
        colors = self.colors
        mapping = {value: colors[key] for key, value in previous.items()}
        def recolor(widget):
            try:
                if widget.winfo_class() in ("Frame", "Label", "Labelframe", "Canvas", "Toplevel", "Tk"):
                    old = widget.cget("background")
                    if old in mapping:
                        widget.configure(background=mapping[old])
                for child in widget.winfo_children():
                    recolor(child)
            except tk.TclError:
                pass
        recolor(self.root)
        self.root.configure(bg=colors["bg"])
        self.style.configure("TFrame", background=colors["bg"])
        self.style.configure("TLabel", background=colors["bg"], foreground=colors["text"], font=(FONT, 10))
        self.style.configure("TLabelframe", background=colors["bg"], bordercolor=colors["border"])
        self.style.configure("TLabelframe.Label", background=colors["bg"], foreground=colors["text"])
        self.style.configure("TEntry", padding=7, fieldbackground=colors["blue"], foreground=colors["text"])
        self.style.configure("TCombobox", padding=5)
        self.style.configure("TButton", padding=7, background=colors["blue"], foreground=colors["text"], bordercolor=colors["border"])
        self.style.map("TButton", background=[("active", colors["border"]), ("disabled", colors["panel"])], foreground=[("disabled", colors["muted"])])
        self.style.configure("Admin.TButton", padding=7)
        self.style.configure("TCheckbutton", background=colors["bg"], foreground=colors["text"])
        self.style.map("TCheckbutton", background=[("active", colors["panel"])])
        self.style.configure("TNotebook", background=colors["bg"])
        self.style.configure("TNotebook.Tab", padding=(15, 8), background=colors["panel"], foreground=colors["text"])
        self.style.map("TNotebook.Tab", background=[("selected", colors["blue"])])
        self.style.configure("Treeview", background=colors["panel"], fieldbackground=colors["panel"], foreground=colors["text"], rowheight=29, font=(FONT, 10))
        self.style.configure("Treeview.Heading", font=(FONT, 10, "bold"), padding=9, background=colors["blue"], foreground=colors["text"])
        self.style.map("Treeview", background=[("selected", colors["blue"])])
        for button in self.action_buttons:
            button.configure(bg=colors["accent"], activebackground=colors["border"])
        self.theme_var.set(f"Tema #{index + 1}")
        self.theme_label.configure(text=f"Tema activo: Tema #{index + 1}")
        if persist:
            try:
                self.preference_file.write_text(json.dumps({"tema": index}), encoding="utf-8")
            except OSError:
                LOGGER.warning("No se pudo conservar la preferencia de tema.")

    def _refresh(self):
        try:
            moment = self.service.clock()
            days = ("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
            months = ("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
            self.clock_label.configure(text=f"{days[moment.weekday()]} {moment.day} de {months[moment.month - 1]}, {moment:%H:%M:%S}")
            state = self.service.store.read()
            admin = next(p for p in state["personal"] if p["activo"] and p["cargo"] == ROLES[0])
            summary = self.service.summary(admin["id"])
            for name, variable in self.stat_vars.items():
                variable.set(str(summary[name]) + ("%" if name == "tasa_puntualidad" else ""))
            self.brand_label.configure(text=state["configuracion"]["hospital"] + " © 2026")
            self.facial_status.configure(text="Reconocimiento facial habilitado. La cámara solo se inicia por solicitud." if state["configuracion"]["facial_habilitado"] else "Reconocimiento facial pendiente de validación.\nPuede registrar asistencia manualmente con su usuario y contraseña.")
            if self.actor_id:
                actor = self.service.actor(self.actor_id)
                if time.monotonic() - self.authenticated_at > 900 or self.actor_version != actor["auth_version"]:
                    self.lock()
                else:
                    self.session_label.configure(text=f"Administración: {actor['nombre']} {actor['apellido']}")
        except (AppError, StopIteration):
            self.lock()
        except StorageError as exc:
            self.facial_status.configure(text=str(exc))
        self.refresh_id = self.root.after(2000, self._refresh)

    def _window(self, title, width=920, height=610, private=True):
        window = tk.Toplevel(self.root)
        window.title(title)
        window.configure(bg=self.colors["bg"])
        width = min(width, self.root.winfo_screenwidth() - 80)
        height = min(height, self.root.winfo_screenheight() - 100)
        window.geometry(f"{width}x{height}+{max(20, (self.root.winfo_screenwidth() - width) // 2)}+{max(25, (self.root.winfo_screenheight() - height) // 2)}")
        window.minsize(min(width, 620), min(height, 400))
        window.transient(self.root)
        window.biotrack_private = private
        self.windows.append(window)
        return window

    def _heading(self, parent, text):
        ttk.Label(parent, text=text, font=(FONT, 18, "bold")).pack(pady=(18, 15))

    def _table(self, parent, columns):
        container = ttk.Frame(parent)
        container.pack(fill="both", expand=True, padx=15, pady=12)
        container.columnconfigure(0, weight=1)
        container.rowconfigure(0, weight=1)
        table = ttk.Treeview(container, columns=[name for name, _, _ in columns], show="headings", selectmode="browse")
        for name, label, width in columns:
            table.heading(name, text=label)
            table.column(name, width=width, minwidth=65, anchor="center" if name in ("fecha", "hora", "edad", "estado", "turno") else "w")
        vertical = ttk.Scrollbar(container, orient="vertical", command=table.yview)
        horizontal = ttk.Scrollbar(container, orient="horizontal", command=table.xview)
        table.configure(yscrollcommand=vertical.set, xscrollcommand=horizontal.set)
        table.grid(row=0, column=0, sticky="nsew")
        vertical.grid(row=0, column=1, sticky="ns")
        horizontal.grid(row=1, column=0, sticky="ew")
        return table

    def _error(self, exc, parent=None):
        messagebox.showerror("BIOTRACK", str(exc), parent=parent or self.root)

    def _run_safe(self, function, parent=None):
        try:
            return function()
        except (AppError, StorageError, OSError, ValueError) as exc:
            self._error(exc, parent)
        return None

    def lock(self):
        self.actor_id, self.authenticated_at = None, 0
        self.session_label.configure(text="Administración bloqueada")
        for window in list(self.windows):
            try:
                if window.winfo_exists() and window.biotrack_private:
                    for camera in self.cameras:
                        if camera.parent.winfo_exists() and camera.parent.winfo_toplevel() == window:
                            camera.stop()
                    window.destroy()
            except tk.TclError:
                pass

    def _protected(self, callback, roles=MANAGERS):
        if self.actor_id and time.monotonic() - self.authenticated_at < 900:
            try:
                actor = self.service.actor(self.actor_id)
                if actor["cargo"] in roles and actor["auth_version"] == self.actor_version:
                    self.authenticated_at = time.monotonic()
                    callback()
                    return
            except AppError:
                self.lock()
        self.show_login(callback, roles)

    def show_login(self, callback, roles=MANAGERS):
        window = self._window("Acceso Restringido", 410, 490, private=False)
        window.resizable(False, False)
        window.grab_set()
        panel = ttk.Frame(window, padding=30)
        panel.pack(fill="both", expand=True)
        ttk.Label(panel, text="▣", font=(FONT, 58)).pack(pady=(3, 12))
        ttk.Label(panel, text="Acceso Restringido", font=(FONT, 21, "bold")).pack(pady=(0, 25))
        username, password = tk.StringVar(), tk.StringVar()
        ttk.Label(panel, text="Usuario").pack(anchor="w")
        first = ttk.Entry(panel, textvariable=username)
        first.pack(fill="x", pady=(3, 14))
        ttk.Label(panel, text="Contraseña").pack(anchor="w")
        ttk.Entry(panel, textvariable=password, show="●").pack(fill="x", pady=(3, 14))
        error = ttk.Label(panel, text="", foreground="#ef9b9b", wraplength=320)
        error.pack(fill="x", pady=8)
        def submit(event=None):
            try:
                key = "desktop:" + username.get().lower()
                if self.transport.sessions.rate_limited(key):
                    raise AppError("Demasiados intentos. Espere cinco minutos.")
                try:
                    actor = self.service.authenticate(username.get(), password.get())
                    if actor["cargo"] not in roles:
                        raise AppError("Esta función requiere permisos de administración.", 403)
                except AppError:
                    self.transport.sessions.failure(key)
                    raise
                self.actor_id, self.authenticated_at = actor["id"], time.monotonic()
                self.actor_version = actor["auth_version"]
                password.set("")
                window.destroy()
                callback()
            except (AppError, StorageError) as exc:
                error.configure(text=str(exc))
        ttk.Button(panel, text="Ingresar", command=submit).pack(fill="x", ipady=8)
        window.bind("<Return>", submit)
        first.focus_set()
        return window

    def show_marking(self, action):
        window = self._window(ACTIONS[action], 660, 590, private=False)
        self._heading(window, {"entrada": "Registrar Entrada", "salida_refrigerio": "Salida a Refrigerio", "entrada_refrigerio": "Entrada de Refrigerio", "salida": "Registrar Salida"}[action])
        notebook = ttk.Notebook(window)
        notebook.pack(fill="both", expand=True, padx=22, pady=(0, 20))
        manual, facial_tab = ttk.Frame(notebook, padding=25), ttk.Frame(notebook, padding=10)
        notebook.add(manual, text="Registro manual")
        notebook.add(facial_tab, text="Reconocimiento facial")
        ttk.Label(manual, text="Identifique su cuenta para registrar la asistencia.\nLa marcación se guardará con origen manual.", wraplength=520, justify="left").pack(anchor="w", pady=(0, 20))
        username, password, note = tk.StringVar(), tk.StringVar(), tk.StringVar()
        for label, variable, hidden in [("Usuario", username, False), ("Contraseña", password, True), ("Observación (opcional)", note, False)]:
            ttk.Label(manual, text=label).pack(anchor="w")
            ttk.Entry(manual, textvariable=variable, show="●" if hidden else "").pack(fill="x", pady=(4, 14))
        error = ttk.Label(manual, text="", wraplength=500, foreground="#ef9b9b")
        error.pack(pady=5)
        def mark_manual():
            try:
                key = "desktop-mark:" + username.get().lower()
                if self.transport.sessions.rate_limited(key):
                    raise AppError("Demasiados intentos. Espere cinco minutos.")
                try:
                    employee = self.service.authenticate(username.get(), password.get())
                except AppError:
                    self.transport.sessions.failure(key)
                    raise
                result = self.service.mark(employee["id"], employee["id"], action, note.get())
                password.set("")
                camera.stop()
                window.destroy()
                messagebox.showinfo("Registro exitoso", f"{result['accion_texto']} registrada para {result['nombre']} {result['apellido']}.\nHora: {result['hora']}", parent=self.root)
            except (AppError, StorageError) as exc:
                error.configure(text=str(exc))
        ttk.Button(manual, text="Registrar " + ACTIONS[action], command=mark_manual).pack(fill="x", ipady=7, pady=12)
        camera = CameraView(facial_tab, self.facial)
        self.cameras.append(camera)
        controls = camera.controls
        enabled = self.service.store.read()["configuracion"]["facial_habilitado"]
        ttk.Button(controls, text="Iniciar cámara", command=camera.start, state="normal" if enabled else "disabled").pack(side="left", padx=5)
        def recognize():
            try:
                image = camera.capture()
            except AppError as exc:
                self._error(exc, window)
                return
            def task():
                employee_id, distance = self.facial.identify(image)
                return self.service.mark(employee_id, employee_id, action, source="facial", confidence=distance)
            def success(row):
                camera.stop()
                window.destroy()
                messagebox.showinfo("Reconocimiento exitoso", f"{row['accion_texto']} registrada para {row['nombre']} {row['apellido']}.", parent=self.root)
            self._background(task, success, window)
        ttk.Button(controls, text="Reconocer y registrar", command=recognize, state="normal" if enabled else "disabled").pack(side="left", padx=5)
        def close():
            camera.stop()
            window.destroy()
        window.protocol("WM_DELETE_WINDOW", close)
        return window

    def _background(self, task, success, parent):
        result = queue.Queue()
        progress = self._window("Procesando", 400, 180, private=False)
        progress.grab_set()
        ttk.Label(progress, text="Procesando imagen. Espere un momento…", padding=25).pack()
        bar = ttk.Progressbar(progress, mode="indeterminate")
        bar.pack(fill="x", padx=30)
        bar.start()
        progress.protocol("WM_DELETE_WINDOW", lambda: None)
        def worker():
            try:
                result.put((True, task()))
            except Exception as exc:
                result.put((False, exc))
        def poll():
            try:
                valid, value = result.get_nowait()
            except queue.Empty:
                progress.after(100, poll)
                return
            progress.destroy()
            if valid:
                success(value)
            else:
                self._error(value, parent)
        threading.Thread(target=worker, daemon=True).start()
        progress.after(100, poll)

    def show_employee(self, employee=None):
        saved_id = employee["id"] if employee else None
        window = self._window("Editar Personal" if employee else "Registrar Nuevo Personal", 940, 720)
        outer = ttk.Frame(window, padding=18)
        outer.pack(fill="both", expand=True)
        outer.columnconfigure(0, weight=1)
        outer.columnconfigure(1, weight=1)
        outer.rowconfigure(0, weight=1)
        form = ttk.LabelFrame(outer, text="Datos del Personal", padding=15)
        form.grid(row=0, column=0, sticky="nsew", padx=(0, 12))
        form.columnconfigure(1, weight=1)
        variables = {}
        fields = [("nombre", "Nombre"), ("apellido", "Apellido"), ("edad", "Edad"), ("ocupacion", "Ocupación"), ("username", "Usuario"), ("password", "Nueva contraseña" if employee else "Contraseña")]
        for index, (key, label) in enumerate(fields):
            variables[key] = tk.StringVar(value=str(employee.get(key, "")) if employee and key != "password" else "")
            ttk.Label(form, text=label + ":").grid(row=index, column=0, sticky="w", padx=(0, 10), pady=9)
            ttk.Entry(form, textvariable=variables[key], show="●" if key == "password" else "").grid(row=index, column=1, sticky="ew", pady=9)
        role = tk.StringVar(value=employee["cargo"] if employee else ROLES[2])
        ttk.Label(form, text="Cargo / Rol:").grid(row=6, column=0, sticky="w", pady=10)
        ttk.Combobox(form, textvariable=role, values=ROLES, state="readonly").grid(row=6, column=1, sticky="ew", pady=10)
        consent = tk.BooleanVar(value=employee.get("consentimiento", False) if employee else False)
        ttk.Checkbutton(form, text="Autoriza el uso del rostro para asistencia", variable=consent).grid(row=7, column=0, columnspan=2, sticky="w", pady=13)
        ttk.Label(form, text="Usuario vacío: se genera con nombre_apellido.\nContraseña: mínimo 8 caracteres." + ("\nDeje la contraseña vacía para conservar la actual." if employee else ""), wraplength=345, font=(FONT, 9)).grid(row=8, column=0, columnspan=2, sticky="w", pady=7)
        camera_box = ttk.LabelFrame(outer, text="Vista de Cámara", padding=5)
        camera_box.grid(row=0, column=1, sticky="nsew")
        camera = CameraView(camera_box, self.facial)
        self.cameras.append(camera)
        enabled = self.service.store.read()["configuracion"]["facial_habilitado"]
        ttk.Button(camera.controls, text="Iniciar cámara", command=camera.start, state="normal" if enabled else "disabled").pack(fill="x", pady=(0, 8))
        capture_after_save = tk.BooleanVar(value=False)
        ttk.Checkbutton(camera.controls, text="Enrolar rostro al guardar", variable=capture_after_save, state="normal" if enabled else "disabled").pack(anchor="w", pady=7)
        if employee and employee["rostro_registrado"]:
            ttk.Label(camera.controls, text="Este empleado tiene un rostro registrado.").pack(pady=5)
            def remove_face():
                if messagebox.askyesno("Registro facial", "¿Eliminar el registro facial para volver a enrolar?", parent=window):
                    self._run_safe(lambda: self.facial.remove(self.actor_id, employee["id"]), window)
            ttk.Button(camera.controls, text="Eliminar registro facial", command=remove_face).pack(pady=5)
        error = ttk.Label(outer, text="", foreground="#ef9b9b", wraplength=800)
        error.grid(row=1, column=0, columnspan=2, sticky="w", pady=8)
        def finish(record, message):
            camera.stop()
            window.destroy()
            self.transport.sessions.revoke_employee(record["id"])
            if record["id"] == self.actor_id and record["cargo"] not in MANAGERS:
                self.lock()
            messagebox.showinfo("Personal", message, parent=self.root)
        def save():
            nonlocal saved_id
            try:
                values = {key: variable.get() for key, variable in variables.items()}
                values.update(cargo=role.get(), consentimiento=consent.get())
                payload = camera.capture() if capture_after_save.get() else None
                if saved_id == self.actor_id and payload and role.get() != ROLES[0]:
                    raise AppError("Conserve el cargo Administrador mientras registra su propio rostro.")
                record = self.service.save_employee(self.actor_id, values, saved_id)
                saved_id = record["id"]
                if record["id"] == self.actor_id:
                    self.actor_version = record["auth_version"]
                variables["password"].set("")
                error.configure(text="Datos guardados. Si el enrolamiento falla, ajuste la imagen y pulse Guardar Registro para reintentar.")
                if payload:
                    def on_success(result):
                        finish(record, "Datos y rostro guardados correctamente.")
                    self._background(lambda: self.facial.enroll(self.actor_id, record["id"], payload), on_success, window)
                else:
                    finish(record, "Datos guardados correctamente. El enrolamiento facial puede realizarse más adelante.")
            except (AppError, StorageError) as exc:
                error.configure(text=str(exc))
        ttk.Button(outer, text="Guardar Registro", command=save).grid(row=2, column=0, columnspan=2, sticky="ew", ipady=10, pady=(5, 0))
        def close():
            camera.stop()
            window.destroy()
        window.protocol("WM_DELETE_WINDOW", close)
        return window

    def show_staff(self):
        window = self._window("Personal Registrado", 1120, 660)
        self._heading(window, "Lista de Personal Registrado")
        toolbar = ttk.Frame(window, padding=(15, 0))
        toolbar.pack(fill="x")
        search, inactive = tk.StringVar(), tk.BooleanVar()
        ttk.Label(toolbar, text="Buscar:").pack(side="left", padx=(0, 7))
        ttk.Entry(toolbar, textvariable=search, width=32).pack(side="left")
        ttk.Checkbutton(toolbar, text="Mostrar dados de baja", variable=inactive).pack(side="left", padx=17)
        columns = [("username", "Usuario", 145), ("nombre", "Nombre", 120), ("apellido", "Apellido", 120), ("edad", "Edad", 65), ("ocupacion", "Ocupación", 150), ("cargo", "Cargo / Rol", 155), ("rostro", "Rostro", 95), ("estado", "Estado", 95)]
        table = self._table(window, columns)
        people = {}
        def load(*args):
            try:
                rows = self.service.list_staff(self.actor_id, search.get(), inactive.get())
                table.delete(*table.get_children())
                people.clear()
                for person in rows:
                    people[person["id"]] = person
                    table.insert("", "end", iid=person["id"], values=[person["username"], person["nombre"], person["apellido"], person["edad"], person["ocupacion"], person["cargo"], "Registrado" if person["rostro_registrado"] else "Pendiente", "Activo" if person["activo"] else "De baja"])
            except (AppError, StorageError) as exc:
                self._error(exc, window)
        def selected():
            ids = table.selection()
            if not ids:
                messagebox.showinfo("Personal", "Seleccione un empleado de la lista.", parent=window)
                return None
            return people[ids[0]]
        def edit():
            person = selected()
            if person:
                self._protected(lambda: self.show_employee(person), (ROLES[0],))
        def active():
            person = selected()
            if not person:
                return
            text = f"¿{'Dar de baja' if person['activo'] else 'Reactivar'} a {person['nombre']} {person['apellido']}?"
            if person["activo"]:
                text += "\nSe conservará su historial y se eliminará su registro facial."
            if messagebox.askyesno("Estado del personal", text, parent=window):
                result = self._run_safe(lambda: self.service.set_active(self.actor_id, person["id"], not person["activo"]), window)
                if result:
                    self.transport.sessions.revoke_employee(person["id"])
                    load()
        buttons = ttk.Frame(window, padding=15)
        buttons.pack(fill="x")
        if self.service.actor(self.actor_id)["cargo"] == ROLES[0]:
            ttk.Button(buttons, text="Registrar nuevo", command=lambda: self.show_employee()).pack(side="left", padx=4)
            ttk.Button(buttons, text="Editar / Enrolar rostro", command=edit).pack(side="left", padx=4)
            ttk.Button(buttons, text="Dar de baja / Reactivar", command=active).pack(side="left", padx=4)
            table.bind("<Double-1>", lambda event: edit())
        ttk.Button(buttons, text="Actualizar", command=load).pack(side="right", padx=4)
        search.trace_add("write", load)
        inactive.trace_add("write", load)
        load()
        return window

    def show_records(self, report_mode=False):
        window = self._window("Reportes de Asistencia" if report_mode else "Registros de Asistencia", 1130, 680)
        self._heading(window, "Reportes de Asistencia" if report_mode else "Historial Completo de Asistencias")
        form = ttk.Frame(window, padding=(15, 0))
        form.pack(fill="x")
        today = self.service.clock().date()
        from datetime import timedelta
        start, end = tk.StringVar(value=(today - timedelta(days=7)).isoformat()), tk.StringVar(value=today.isoformat())
        people = self.service.list_staff(self.actor_id, include_inactive=True)
        names = {"Todo el personal": ""}
        names.update({f"{p['nombre']} {p['apellido']} ({p['username']})": p["id"] for p in people})
        person, action = tk.StringVar(value="Todo el personal"), tk.StringVar(value="Todas las acciones")
        for label, variable, values, width in [("Desde (AAAA-MM-DD)", start, None, 15), ("Hasta", end, None, 15), ("Empleado", person, list(names), 27), ("Acción", action, ["Todas las acciones"] + list(ACTIONS.values()), 22)]:
            box = ttk.Frame(form)
            box.pack(side="left", padx=(0, 12))
            ttk.Label(box, text=label).pack(anchor="w", pady=(0, 5))
            if values:
                ttk.Combobox(box, textvariable=variable, values=values, state="readonly", width=width).pack()
            else:
                ttk.Entry(box, textvariable=variable, width=width).pack()
        columns = [("fecha", "Fecha", 105), ("hora", "Hora", 85), ("accion", "Acción", 155), ("nombre", "Nombre", 130), ("apellido", "Apellido", 130), ("cargo", "Cargo", 160), ("origen", "Origen", 110)]
        table = self._table(window, columns)
        records = {}
        footer = ttk.Frame(window, padding=15)
        footer.pack(fill="x")
        count_label = ttk.Label(footer, text="")
        count_label.pack(side="left")
        def filters():
            return {"desde": start.get(), "hasta": end.get(), "empleado_id": names.get(person.get(), ""), "accion": next((key for key, value in ACTIONS.items() if value == action.get()), "")}
        def load():
            try:
                rows = self.service.history(self.actor_id, filters())
                table.delete(*table.get_children())
                records.clear()
                for row in rows:
                    records[row["id"]] = row
                    table.insert("", "end", iid=row["id"], values=[row["fecha"], row["hora"], row["accion_texto"], row["nombre"], row["apellido"], row["cargo"], "Ejemplo" if row["origen"] == "demostracion" else row["origen"].capitalize()])
                count_label.configure(text=f"{len(rows)} registros · Hora de Perú")
            except (AppError, StorageError) as exc:
                self._error(exc, window)
        ttk.Button(form, text="Filtrar", command=load).pack(side="left", pady=(20, 0))
        def save_report(kind):
            try:
                rows = self.service.history(self.actor_id, filters())
                content, _ = export(rows, kind, self.service.settings(self.actor_id)["hospital"], self.service.clock().strftime("%d/%m/%Y %H:%M"))
                report_dir = self.data_dir.parent / "reportes"
                report_dir.mkdir(parents=True, exist_ok=True)
                path = filedialog.asksaveasfilename(parent=window, initialdir=str(report_dir), initialfile=f"BIOTRACK_asistencia.{kind}", defaultextension=f".{kind}", filetypes=[(kind.upper(), "*." + kind)])
                if path:
                    Path(path).write_bytes(content)
                    messagebox.showinfo("Reporte", "Reporte guardado correctamente.", parent=window)
            except (AppError, StorageError, OSError) as exc:
                self._error(exc, window)
        for kind, label in [("csv", "Exportar CSV"), ("pdf", "Exportar PDF"), ("xlsx", "Exportar Excel")]:
            ttk.Button(footer, text=label, command=lambda value=kind: save_report(value)).pack(side="right", padx=4)
        def details(event=None):
            if table.selection():
                row = records[table.selection()[0]]
                messagebox.showinfo("Detalle de asistencia", f"{row['nombre']} {row['apellido']}\n{row['fecha']} · {row['hora']}\n{row['accion_texto']} · {row['origen']}\n\nObservación: {row.get('observacion') or 'Sin observación'}", parent=window)
        table.bind("<Double-1>", details)
        load()
        return window

    def show_schedules(self):
        window = self._window("Gestión de Horarios", 1090, 670)
        self._heading(window, "Programación de Turnos")
        people = self.service.list_staff(self.actor_id)
        names = {f"{p['nombre']} {p['apellido']} ({p['username']})": p["id"] for p in people}
        selected_person = tk.StringVar(value=next(iter(names), ""))
        day, kind = tk.StringVar(value=self.service.clock().date().isoformat()), tk.StringVar(value="Mañana")
        start, end = tk.StringVar(value="07:00"), tk.StringVar(value="13:00")
        form = ttk.LabelFrame(window, text="Añadir / Editar Turno", padding=15)
        form.pack(fill="x", padx=15, pady=(0, 5))
        for index, (label, variable, values, width) in enumerate([("Empleado", selected_person, list(names), 30), ("Fecha AAAA-MM-DD", day, None, 16), ("Tipo de turno", kind, list(SHIFTS) + ["Personalizado"], 18), ("Inicio", start, None, 9), ("Fin", end, None, 9)]):
            ttk.Label(form, text=label).grid(row=0, column=index, sticky="w", padx=5, pady=(0, 5))
            widget = ttk.Combobox(form, textvariable=variable, values=values, state="readonly", width=width) if values else ttk.Entry(form, textvariable=variable, width=width)
            widget.grid(row=1, column=index, sticky="ew", padx=5)
        def change_hours(*args):
            if kind.get() in SHIFTS:
                start.set(SHIFTS[kind.get()][0])
                end.set(SHIFTS[kind.get()][1])
        kind.trace_add("write", change_hours)
        table = self._table(window, [("fecha", "Fecha", 100), ("nombre", "Empleado", 190), ("cargo", "Cargo", 150), ("turno", "Turno", 100), ("inicio", "Inicio", 90), ("fin", "Fin", 90)])
        rows = {}
        current = {"id": None}
        def load():
            try:
                items = self.service.list_shifts(self.actor_id)
                table.delete(*table.get_children())
                rows.clear()
                for row in items:
                    rows[row["id"]] = row
                    table.insert("", "end", iid=row["id"], values=[row["fecha"], row["nombre"], row["cargo"], row["turno"], row["inicio"], row["fin"]])
            except (AppError, StorageError) as exc:
                self._error(exc, window)
        footer = ttk.Frame(window, padding=15)
        footer.pack(fill="x")
        status = ttk.Label(footer, text="Nuevo turno")
        status.pack(side="left")
        def reset():
            current["id"] = None
            status.configure(text="Nuevo turno")
            if table.selection():
                table.selection_remove(*table.selection())
        def select(event=None):
            if table.selection():
                row = rows[table.selection()[0]]
                current["id"] = row["id"]
                selected_person.set(next((label for label, eid in names.items() if eid == row["empleado_id"]), ""))
                day.set(row["fecha"])
                kind.set(row["turno"])
                start.set(row["inicio"])
                end.set(row["fin"])
                status.configure(text="Editando turno seleccionado")
        def save():
            values = {"empleado_id": names.get(selected_person.get()), "fecha": day.get(), "turno": kind.get(), "inicio": start.get(), "fin": end.get()}
            if self._run_safe(lambda: self.service.save_shift(self.actor_id, values, current["id"]), window):
                reset()
                load()
        def delete():
            if not table.selection():
                messagebox.showinfo("Horarios", "Seleccione un turno.", parent=window)
                return
            row_id = table.selection()[0]
            if messagebox.askyesno("Horarios", "¿Eliminar este turno programado?", parent=window):
                if self._run_safe(lambda: self.service.delete_shift(self.actor_id, row_id), window):
                    reset()
                    load()
        ttk.Button(footer, text="Guardar turno", command=save).pack(side="right", padx=4)
        ttk.Button(footer, text="Eliminar seleccionado", command=delete).pack(side="right", padx=4)
        ttk.Button(footer, text="Nuevo", command=reset).pack(side="right", padx=4)
        table.bind("<<TreeviewSelect>>", select)
        load()
        return window

    def show_predictions(self):
        window = self._window("Predicciones del Sistema", 930, 650)
        self._heading(window, "Análisis Predictivo de Asistencia")
        body = ttk.Frame(window, padding=20)
        body.pack(fill="both", expand=True)
        data = self.service.predictions(self.actor_id)
        sections = [("◷  Cobertura de Turnos (Hoy)", "\n".join(f"{r['nombre']} · {r['turno']} · {r['horario']} · {r['estado']}" for r in data["cobertura"]) or "No hay turnos programados para hoy."), ("◈  Sobretiempo y Fatiga", "\n".join(f"{r['nombre']}: {r['horas']} horas · {r['detalle']}" for r in data["sobretiempo"]) or "No se detectan jornadas superiores al límite configurado."), ("♟  Disponibilidad de Personal por Rol", "\n".join(f"{r['cargo']}: {r['presente']} presente(s), {r['refrigerio']} en refrigerio, {r['ausente']} ausente(s)" for r in data["disponibilidad"]))]
        for title, text in sections:
            box = ttk.LabelFrame(body, text=title, padding=15)
            box.pack(fill="x", pady=(0, 15))
            ttk.Label(box, text=text, wraplength=820, justify="left").pack(anchor="w")
        ttk.Label(body, text="Análisis orientativo basado en turnos y duración de jornada.", font=(FONT, 9)).pack(anchor="w", pady=5)
        return window

    def show_incidents(self):
        window = self._window("Incidencias de Asistencia", 1100, 650)
        self._heading(window, "Registro de Incidencias")
        table = self._table(window, [("fecha", "Fecha", 105), ("nombre", "Personal", 170), ("tipo", "Tipo", 150), ("detalle", "Detalle", 340), ("estado", "Estado", 100)])
        items = {}
        footer = ttk.Frame(window, padding=15)
        footer.pack(fill="x")
        def load():
            try:
                rows = self.service.incidents(self.actor_id)
                items.clear()
                table.delete(*table.get_children())
                for row in rows:
                    items[row["id"]] = row
                    table.insert("", "end", iid=row["id"], values=[row["fecha"], row["nombre"], row["tipo"], row["detalle"], row["estado"]])
            except (AppError, StorageError) as exc:
                self._error(exc, window)
        def form(existing=None):
            dialog = self._window("Revisar incidencia" if existing else "Registrar incidencia", 620, 560)
            frame = ttk.Frame(dialog, padding=25)
            frame.pack(fill="both", expand=True)
            people = self.service.list_staff(self.actor_id)
            names = {f"{p['nombre']} {p['apellido']}": p["id"] for p in people}
            variables = {"empleado": tk.StringVar(value=next(iter(names), "")), "fecha": tk.StringVar(value=self.service.clock().date().isoformat()), "tipo": tk.StringVar(value="Otra"), "estado": tk.StringVar(value=existing["estado"] if existing else "Pendiente")}
            if existing:
                ttk.Label(frame, text=existing["nombre"] + " · " + existing["tipo"], font=(FONT, 13, "bold"), wraplength=550).pack(anchor="w", pady=10)
                ttk.Label(frame, text=existing["detalle"], wraplength=540).pack(anchor="w", pady=10)
                ttk.Label(frame, text="Estado").pack(anchor="w")
                ttk.Combobox(frame, textvariable=variables["estado"], values=("Pendiente", "Resuelta", "Justificada"), state="readonly").pack(fill="x", pady=10)
            else:
                for label, key, values in [("Empleado", "empleado", list(names)), ("Fecha AAAA-MM-DD", "fecha", None), ("Tipo", "tipo", ("Tardanza", "Ausencia", "Reconocimiento fallido", "Fuera de turno", "Otra"))]:
                    ttk.Label(frame, text=label).pack(anchor="w")
                    widget = ttk.Combobox(frame, textvariable=variables[key], values=values, state="readonly") if values else ttk.Entry(frame, textvariable=variables[key])
                    widget.pack(fill="x", pady=(4, 12))
            ttk.Label(frame, text="Resolución / Justificación" if existing else "Detalle").pack(anchor="w", pady=(8, 4))
            detail = tk.Text(frame, height=5, wrap="word", font=(FONT, 11))
            detail.pack(fill="both", expand=True)
            if existing:
                detail.insert("1.0", existing["resolucion"])
            def save():
                values = {"estado": variables["estado"].get(), "resolucion": detail.get("1.0", "end-1c")} if existing else {"empleado_id": names.get(variables["empleado"].get()), "fecha": variables["fecha"].get(), "tipo": variables["tipo"].get(), "detalle": detail.get("1.0", "end-1c")}
                if self._run_safe(lambda: self.service.save_incident(self.actor_id, values, existing["id"] if existing else None), dialog):
                    dialog.destroy()
                    load()
            ttk.Button(frame, text="Guardar", command=save).pack(fill="x", pady=15, ipady=5)
        def resolve():
            if table.selection():
                form(items[table.selection()[0]])
            else:
                messagebox.showinfo("Incidencias", "Seleccione una incidencia.", parent=window)
        ttk.Button(footer, text="Registrar incidencia", command=form).pack(side="left", padx=4)
        ttk.Button(footer, text="Revisar / Resolver", command=resolve).pack(side="left", padx=4)
        ttk.Button(footer, text="Actualizar", command=load).pack(side="right", padx=4)
        table.bind("<Double-1>", lambda event: resolve())
        load()
        return window

    def show_settings(self):
        window = self._window("Configuración del Sistema", 740, 760)
        self._heading(window, "Configuración y Seguridad")
        settings = self.service.settings(self.actor_id)
        frame = ttk.Frame(window, padding=25)
        frame.pack(fill="both", expand=True)
        frame.columnconfigure(1, weight=1)
        variables = {}
        for index, (key, label) in enumerate([("hospital", "Nombre del hospital"), ("tolerancia_minutos", "Tolerancia de tardanza (min)"), ("jornada_max_horas", "Jornada máxima (horas)"), ("minutos_refrigerio", "Refrigerio (minutos)"), ("umbral_rostro", "Umbral facial (0.30 a 0.65)"), ("camara_indice", "Índice de cámara (0 a 9)")]):
            variables[key] = tk.StringVar(value=str(settings[key]))
            ttk.Label(frame, text=label).grid(row=index, column=0, sticky="w", padx=(0, 15), pady=10)
            ttk.Entry(frame, textvariable=variables[key]).grid(row=index, column=1, sticky="ew", pady=10)
        facial = tk.BooleanVar(value=settings["facial_habilitado"])
        ttk.Checkbutton(frame, text="Habilitar reconocimiento facial", variable=facial).grid(row=6, column=0, columnspan=2, sticky="w", pady=12)
        ttk.Label(frame, text="Cámara 0: dispositivo principal. Pruebe 1 si utiliza otra cámara.\nHabilitar la función no abre la cámara automáticamente.", wraplength=650, font=(FONT, 10)).grid(row=7, column=0, columnspan=2, sticky="w", pady=6)
        def save():
            if facial.get() and not settings["facial_habilitado"] and not messagebox.askyesno("Reconocimiento facial", "¿Habilitar la función para realizar las pruebas de cámara y enrolamiento?", parent=window):
                return
            values = {key: variable.get() for key, variable in variables.items()}
            values["facial_habilitado"] = facial.get()
            if self._run_safe(lambda: self.service.update_settings(self.actor_id, values), window):
                messagebox.showinfo("Configuración", "Parámetros guardados.", parent=window)
        ttk.Button(frame, text="Guardar configuración", command=save).grid(row=8, column=0, columnspan=2, sticky="ew", pady=10, ipady=7)
        ttk.Separator(frame).grid(row=9, column=0, columnspan=2, sticky="ew", pady=6)
        def backup():
            try:
                state = self.service.backup(self.actor_id)
                path = filedialog.asksaveasfilename(parent=window, initialfile="BIOTRACK_respaldo.json", defaultextension=".json", filetypes=[("JSON", "*.json")])
                if path:
                    Path(path).write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")
                    messagebox.showinfo("Respaldo", "Copia guardada. Si utiliza plantillas faciales, conserve también biometric.key.", parent=window)
            except (AppError, StorageError, OSError) as exc:
                self._error(exc, window)
        ttk.Button(frame, text="Guardar copia de seguridad", command=backup).grid(row=10, column=0, columnspan=2, sticky="ew", pady=6)
        ttk.Button(frame, text="Cambiar mi contraseña", command=self.show_password).grid(row=11, column=0, columnspan=2, sticky="ew", pady=6)
        ttk.Button(frame, text="Ver auditoría", command=self.show_audit).grid(row=12, column=0, columnspan=2, sticky="ew", pady=6)
        return window

    def show_password(self):
        window = self._window("Cambiar Contraseña", 470, 350)
        frame = ttk.Frame(window, padding=25)
        frame.pack(fill="both", expand=True)
        current, new = tk.StringVar(), tk.StringVar()
        for label, variable in [("Contraseña actual", current), ("Nueva contraseña (mínimo 8 caracteres)", new)]:
            ttk.Label(frame, text=label).pack(anchor="w")
            ttk.Entry(frame, textvariable=variable, show="●").pack(fill="x", pady=(5, 20))
        def save():
            actor_id = self.actor_id
            if self._run_safe(lambda: self.service.change_password(actor_id, {"actual": current.get(), "nueva": new.get()}), window):
                self.transport.sessions.revoke_employee(actor_id)
                self.lock()
                if window.winfo_exists():
                    window.destroy()
                messagebox.showinfo("Contraseña", "Contraseña actualizada. Inicie sesión nuevamente.", parent=self.root)
        ttk.Button(frame, text="Actualizar contraseña", command=save).pack(fill="x", ipady=7)
        return window

    def show_audit(self):
        window = self._window("Auditoría", 1020, 620)
        self._heading(window, "Registro de Auditoría")
        table = self._table(window, [("fecha", "Fecha / Hora", 200), ("usuario", "Usuario", 150), ("accion", "Operación", 180), ("detalle", "Detalle", 400)])
        for row in self.service.audit(self.actor_id):
            table.insert("", "end", values=[row["fecha_hora"][:19].replace("T", " "), row["usuario"], row["accion"].replace("_", " "), row["detalle"]])
        return window

    def smoke_test(self):
        """Construye todas las ventanas sin capturas, cambios de datos ni diálogos bloqueantes."""
        self.root.update()
        state = self.service.store.read()
        self.actor_id = next(p["id"] for p in state["personal"] if p["activo"] and p["cargo"] == ROLES[0])
        self.authenticated_at = time.monotonic()
        checks = [self.show_employee, self.show_staff, self.show_records, self.show_schedules, self.show_predictions, self.show_incidents, self.show_settings, self.show_audit, self.show_password, lambda: self.show_marking("entrada"), lambda: self.show_login(lambda: None)]
        for check in checks:
            window = check()
            self.root.update_idletasks()
            print(f"VENTANA_OK: {window.title()}", flush=True)
            for camera in self.cameras:
                camera.stop()
            window.destroy()
        self.close()

    def close(self):
        for camera in self.cameras:
            camera.stop()
        try:
            self.root.after_cancel(self.refresh_id)
        except (tk.TclError, AttributeError):
            pass
        self.root.destroy()

    def run(self):
        try:
            self.root.state("zoomed")
        except tk.TclError:
            pass
        self.root.mainloop()
