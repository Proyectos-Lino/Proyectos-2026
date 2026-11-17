import os
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from sqlite3 import Error
from time import monotonic

from .camera import CameraPanel, Pointer, verify_detector

from PyQt6.QtCore import QPoint, QTimer
from PyQt6.QtGui import QFont, QFontDatabase, QKeySequence, QShortcut
from PyQt6.QtWidgets import QApplication, QFrame, QHBoxLayout, QMainWindow, QMessageBox, QPushButton, QStackedWidget, QVBoxLayout, QWidget

from .database import get_patient_by_dni, initialize
from .dni import lookup_dni
from .pages.assistant import AssistantPage
from .pages.common import subtitle
from .pages.dni_input import DniInputPage
from .pages.help import HelpPage
from .pages.integrations import IntegrationsPage
from .pages.main_menu import MainMenuPage
from .pages.natural_query import NaturalQueryPage
from .pages.records import RecordsPage
from .pages.report import ReportPage
from .pages.requests import RequestsPage
from .pages.triage import TriagePage
from .settings import enabled, setting


STYLE = """
QWidget { color: #e8f0ff; font: 14px 'Segoe UI'; }
QMainWindow, QWidget#central { background: #101a2c; }
QFrame#workspace, QFrame#cameraPanel { background: #18263c; border: 1px solid #354865; border-radius: 14px; }
QLabel#title { color: #e8f0ff; font: 700 27px 'Segoe UI'; }
QLabel#subtitle { color: #b8cae6; }
QPushButton { background: #275dc4; border: 2px solid transparent; color: white; border-radius: 8px;
    min-height: 29px; padding: 6px 10px; font-weight: 600; }
QPushButton:hover { background: #3471e5; }
QPushButton#btnSecondary { background: #324560; }
QPushButton#btnSecondary:hover { background: #415b7d; }
QPushButton#btnSuccess { background: #08634e; }
QPushButton#btnSuccess:hover { background: #117c64; }
QPushButton#btnDanger { background: #a63248; }
QPushButton#btnDanger:hover { background: #b94b5e; }
QPushButton:focus { border: 2px solid #a9d6ff; }
QPushButton[gestureTarget="true"] { border: 2px solid #ffcf5c; background: #2b4d7d; }
QPushButton:checked { border: 2px solid #a9d6ff; }
QPushButton:disabled { color: #9aabc5; background: #2a374d; }
QLineEdit, QTextEdit, QComboBox, QDateEdit, QSpinBox, QTableWidget, QCalendarWidget {
    background: #0f1b2e; color: #e8f0ff; border: 1px solid #4d6486; border-radius: 6px;
    padding: 6px; selection-background-color: #275dc4; }
QLineEdit:focus, QTextEdit:focus, QComboBox:focus { border: 1px solid #a9d6ff; }
QTableWidget { alternate-background-color: #182943; gridline-color: #344760; }
QHeaderView::section { background: #243b5b; color: #e8f0ff; border: none; padding: 8px; }
QProgressBar { background: #0f1b2e; color: #e8f0ff; border: 1px solid #4d6486; border-radius: 5px; text-align: center; }
QProgressBar::chunk { background: #275dc4; }
QMessageBox { background: #18263c; }
"""


class MainWindow(QMainWindow):
    def __init__(self, auto_camera=True):
        super().__init__()
        self.setWindowTitle("NeuroLink · Asistente clínico sin contacto")
        self.resize(1200, 770)
        self.setMinimumSize(900, 680)
        self.setStyleSheet(STYLE)
        self.jobs = {}
        self.job_ids = {}
        self.executor = ThreadPoolExecutor(max_workers=2)
        self.job_timer = QTimer(self, interval=85)
        self.job_timer.timeout.connect(self.collect_jobs)

        main = QWidget(objectName="central")
        column = QVBoxLayout(main)
        column.setContentsMargins(20, 15, 20, 12)
        self.header = subtitle("NeuroLink   /   Atención y gestión")
        column.addWidget(self.header)
        body = QHBoxLayout()
        body.setSpacing(15)
        self.work = QFrame(objectName="workspace")
        work_layout = QVBoxLayout(self.work)
        work_layout.setContentsMargins(18, 14, 18, 14)
        self.stack = QStackedWidget()
        work_layout.addWidget(self.stack)
        body.addWidget(self.work, 7)
        self.camera = CameraPanel(auto_start=auto_camera)
        self.pointer = Pointer(self.work)
        self.target = None
        self.target_since = 0.0
        self.last_pointer_activity = float("-inf")
        self.smoothed = None
        self.camera.gesture.connect(self.handle_gesture)
        self.camera.calibration_changed.connect(self.clear_pointer)
        body.addWidget(self.camera, 3)
        column.addLayout(body, 1)
        self.status = subtitle("Puedes navegar con ratón y teclado.")
        column.addWidget(self.status)
        self.setCentralWidget(main)

        self.pages = {
            "menu": MainMenuPage(self), "dni": DniInputPage(self), "triage": TriagePage(self),
            "report": ReportPage(self), "assistant": AssistantPage(self), "requests": RequestsPage(self),
            "records": RecordsPage(self), "query": NaturalQueryPage(self), "help": HelpPage(self),
            "integrations": IntegrationsPage(self),
        }
        for page in self.pages.values():
            self.stack.addWidget(page)
        self.pages["dni"].dni_submitted.connect(self.start_dni)
        self.back_shortcut = QShortcut(QKeySequence("Esc"), self, activated=self.back)
        self.switch_page("menu")

    def switch_page(self, key, **context):
        self.clear_pointer()
        old = self.stack.currentWidget()
        if old is self.pages["dni"]:
            self.invalidate_job("dni")
        elif old is self.pages["integrations"]:
            self.invalidate_job("integration-check")
        elif old is self.pages["triage"] and key != "triage":
            old.patient = None
        elif old is self.pages["report"] and key != "report":
            for field in old.values.values():
                field.clear()
        page = self.pages[key]
        if key == "dni":
            page.reset()
        elif key == "triage":
            page.start(context["patient"])
        elif key == "report":
            page.show_report(**context)
        elif key in {"records", "requests", "integrations"}:
            page.reload()
        elif key == "assistant":
            page.reset()
        self.stack.setCurrentWidget(page)

    def back(self):
        self.switch_page("menu")

    def set_target(self, button, now):
        if self.target is button:
            return
        for widget, highlighted in ((self.target, False), (button, True)):
            if widget is not None:
                widget.setProperty("gestureTarget", highlighted)
                widget.style().unpolish(widget)
                widget.style().polish(widget)
        self.target = button
        self.target_since = now

    def clear_pointer(self):
        self.set_target(None, 0.0)
        self.last_pointer_activity = float("-inf")
        self.smoothed = None
        self.pointer.hide()

    def handle_gesture(self, kind, value):
        now = monotonic()
        if kind == "point":
            self.aim(value, now)
        elif kind == "hold":
            if now - self.last_pointer_activity <= 1.0:
                self.last_pointer_activity = now
            else:
                self.clear_pointer()
        elif kind == "click":
            button = self.target
            page = self.stack.currentWidget()
            if (button is not None and now - self.target_since >= 0.16 and
                    now - self.last_pointer_activity <= 1.0 and page.isAncestorOf(button) and
                    button.isVisible() and button.isEnabled()):
                self.last_pointer_activity = now
                self.status.setText("Selección confirmada por gesto.")
                button.click()
            else:
                self.clear_pointer()
        elif kind == "back":
            self.back()
            self.status.setText("Volviste al menú con el gesto de V.")
        elif kind == "swipe":
            self.clear_pointer()
            self.navigate_by_swipe(value)
        elif kind == "lost" or now - self.last_pointer_activity > 1.0:
            self.clear_pointer()

    def aim(self, value, now):
        x, y = self.camera.calibration.map(*value)
        if self.smoothed is not None:
            previous_x, previous_y = self.smoothed
            x, y = 0.48 * x + 0.52 * previous_x, 0.48 * y + 0.52 * previous_y
        self.smoothed = x, y
        self.last_pointer_activity = now
        rect = self.stack.geometry()
        relative = QPoint(round(x * max(0, rect.width() - 1)), round(y * max(0, rect.height() - 1)))
        widget = self.stack.childAt(relative)
        while widget is not None and widget is not self.stack and not isinstance(widget, QPushButton):
            widget = widget.parentWidget()
        self.set_target(widget if isinstance(widget, QPushButton) and widget.isEnabled() else None, now)
        self.pointer.move_to(rect.topLeft() + relative)

    def navigate_by_swipe(self, direction):
        page = self.stack.currentWidget()
        forward = direction == "right"
        if page is self.pages["triage"]:
            if forward:
                page.next_question()
            else:
                page.previous_question()
        elif page in (self.pages["requests"], self.pages["records"]):
            page.move(1 if forward else -1)
        elif page is self.pages["help"]:
            page.scroll_gestures(1 if forward else -1)

    def start_dni(self, dni):
        page = self.pages["dni"]
        try:
            if get_patient_by_dni(dni):
                page.message.setText("Este DNI ya está registrado. Puedes consultarlo en Registros.")
                return
        except Error:
            page.message.setText("No se pudo leer la base de datos.")
            return
        page.set_busy(True)
        self.background("dni", lambda: lookup_dni(dni), self.finish_dni)

    def finish_dni(self, result, error):
        page = self.pages["dni"]
        page.set_busy(False)
        if error:
            page.message.setText(str(error) if isinstance(error, ValueError) else "No se pudo consultar el DNI.")
        else:
            self.switch_page("triage", patient=result)

    def background(self, key, work, on_complete):
        self.invalidate_job(key)
        future = self.executor.submit(work)
        self.jobs[future] = (key, self.job_ids[key], on_complete)
        self.job_timer.start()

    def invalidate_job(self, key):
        self.job_ids[key] = self.job_ids.get(key, 0) + 1
        for future, (name, _, _) in self.jobs.items():
            if name == key:
                future.cancel()

    def collect_jobs(self):
        for future, (key, version, callback) in list(self.jobs.items()):
            if not future.done():
                continue
            del self.jobs[future]
            if future.cancelled() or version != self.job_ids[key]:
                continue
            error = future.exception()
            callback(future.result() if error is None else None, error)
        if not self.jobs:
            self.job_timer.stop()

    def closeEvent(self, event):
        self.camera.stop()
        if self.camera.thread is not None and not self.camera.thread.wait(1200):
            self.status.setText("La cámara está cerrando. Vuelve a cerrar la ventana en un momento.")
            event.ignore()
            return
        self.executor.shutdown(wait=False, cancel_futures=True)
        super().closeEvent(event)


def configure_font(app):
    if sys.platform != "win32" or "Segoe UI" in QFontDatabase.families():
        return
    fonts = Path(os.getenv("WINDIR", r"C:\Windows")) / "Fonts"
    for file_name in ("segoeui.ttf", "segoeuib.ttf", "seguiemj.ttf"):
        QFontDatabase.addApplicationFont(str(fonts / file_name))
    app.setFont(QFont("Segoe UI", 10))


def run():
    if "--verificar-integraciones" in sys.argv:
        configured = (enabled("NEUROLINK_DNI_API") and setting("API_TOKEN")
                      and enabled("NEUROLINK_OPENROUTER") and setting("OPENROUTER_API_KEY")
                      and setting("OPENROUTER_MODEL"))
        return 0 if configured else 1
    if "--verificar-detector" in sys.argv:
        return 0 if verify_detector() else 1
    command_line = [arg for arg in sys.argv if arg != "--sin-camara"]
    app = QApplication(command_line)
    app.setApplicationName("NeuroLink")
    configure_font(app)
    try:
        initialize()
    except (Error, OSError):
        QMessageBox.critical(None, "NeuroLink", "No se pudo abrir la carpeta de datos. Revisa los permisos de escritura.")
        return 1
    window = MainWindow(auto_camera="--sin-camara" not in sys.argv)
    window.show()
    return app.exec()


if __name__ == "__main__":
    sys.exit(run())
