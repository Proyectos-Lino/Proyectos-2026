import configparser
import json
from dataclasses import asdict
from time import monotonic

# Carga las bibliotecas de visión antes de que Qt agregue sus DLL a la búsqueda.
try:
    import cv2
    from .vision import HandDetector
except ImportError:
    HandDetector = None

from PyQt6.QtCore import QPoint, Qt, QThread, QTimer, pyqtSignal
from PyQt6.QtGui import QColor, QImage, QPainter, QPen, QPixmap
from PyQt6.QtWidgets import QFrame, QHBoxLayout, QLabel, QPushButton, QSpinBox, QVBoxLayout, QWidget

from .gestures import Calibration
from .pages.common import subtitle, title
from .settings import DATA_DIR, SOURCE_DIR


def verify_detector():
    if HandDetector is None:
        return False
    import numpy as np

    detector = HandDetector(configparser.ConfigParser())
    try:
        frame = np.zeros((96, 128, 3), dtype=np.uint8)
        image, gesture = detector.process(frame)
        return image.shape == frame.shape and gesture == ("lost", None)
    finally:
        detector.close()


class CameraThread(QThread):
    frame_ready = pyqtSignal(QImage, object)
    camera_error = pyqtSignal(str)

    def __init__(self, index, parent):
        super().__init__(parent)
        self.index = index

    def run(self):
        capture = detector = None
        try:
            if HandDetector is None:
                self.camera_error.emit("El detector de manos no pudo cargarse. Usa ratón o teclado.")
                return
            capture = cv2.VideoCapture(self.index)
            if not capture.isOpened():
                self.camera_error.emit("Cámara no disponible. Revisa la conexión y vuelve a activarla.")
                return
            capture.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
            capture.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)
            config = configparser.ConfigParser()
            config.read((SOURCE_DIR / "config.ini", DATA_DIR / "config.ini"), encoding="utf-8")
            if self.isInterruptionRequested():
                return
            detector = HandDetector(config)
            misses = 0
            interval = config.getint("GestureDetection", "FrameIntervalMs", fallback=33)
            while not self.isInterruptionRequested():
                started = monotonic()
                ok, frame = capture.read()
                if not ok:
                    misses += 1
                    if misses == 10:
                        self.camera_error.emit("Se perdió la imagen de la cámara. Prueba activarla de nuevo.")
                        return
                    self.msleep(65)
                    continue
                misses = 0
                frame, event = detector.process(frame)
                rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                height, width = rgb.shape[:2]
                image = QImage(rgb.data, width, height, rgb.strides[0], QImage.Format.Format_RGB888).copy()
                self.frame_ready.emit(image, event)
                remaining = max(0, max(interval, 25) - int((monotonic() - started) * 1000))
                if remaining:
                    self.msleep(remaining)
        except (ImportError, OSError, RuntimeError, ValueError, configparser.Error) as error:
            self.camera_error.emit(f"No se pudo iniciar el control por gestos ({type(error).__name__}).")
        finally:
            if capture is not None:
                capture.release()
            if detector is not None:
                detector.close()


class Pointer(QWidget):
    def __init__(self, parent):
        super().__init__(parent)
        self.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents)
        self.point = QPoint()
        self.hide()

    def move_to(self, point):
        self.setGeometry(self.parentWidget().rect())
        self.point = point
        self.raise_()
        self.show()
        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        painter.setPen(QPen(QColor("#74edff"), 2))
        painter.setBrush(QColor(75, 214, 244, 48))
        painter.drawEllipse(self.point, 11, 11)
        painter.setBrush(QColor("#e6fcff"))
        painter.drawEllipse(self.point, 3, 3)


class CameraPanel(QFrame):
    gesture = pyqtSignal(str, object)
    calibration_changed = pyqtSignal()

    def __init__(self, auto_start=True):
        super().__init__(objectName="cameraPanel")
        self.thread = None
        self.error = False
        self.latest = None
        self.calibration = Calibration()
        self.calibrating = []
        self.calibration_active = False
        self.last_point = None
        self.timer = QTimer(self, interval=1000)
        self.timer.timeout.connect(self.calibration_tick)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(14, 14, 14, 14)
        layout.setSpacing(11)
        layout.addWidget(title("Cámara y gestos"))
        self.state = subtitle("Cámara desactivada")
        layout.addWidget(self.state)
        self.preview = QLabel("Vista de cámara")
        self.preview.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.preview.setStyleSheet("background: #0b1424; border-radius: 8px;")
        self.preview.setMinimumSize(180, 165)
        layout.addWidget(self.preview, 1)
        self.instructions = subtitle("Índice para apuntar · Palma abierta para pausar · Dobla el pulgar para hacer clic · V para volver")
        layout.addWidget(self.instructions)
        controls = QHBoxLayout()
        self.index = QSpinBox()
        self.index.setRange(0, 9)
        self.index.setPrefix("Cámara ")
        self.index.setAccessibleName("Número de cámara")
        self.index.valueChanged.connect(self.load_calibration)
        controls.addWidget(self.index)
        self.toggle = QPushButton("Activar")
        self.toggle.clicked.connect(self.toggle_camera)
        controls.addWidget(self.toggle)
        layout.addLayout(controls)
        self.calibrate = QPushButton("Calibrar puntero")
        self.calibrate.setObjectName("btnSecondary")
        self.calibrate.setEnabled(False)
        self.calibrate.clicked.connect(self.toggle_calibration)
        layout.addWidget(self.calibrate)
        self.load_calibration()
        if auto_start:
            QTimer.singleShot(150, self.start)

    def load_calibration(self):
        self.calibration = Calibration()
        try:
            data = json.loads((DATA_DIR / f"camara-{self.index.value()}.json").read_text(encoding="utf-8"))
            saved = Calibration(**data)
            if saved.valid():
                self.calibration = saved
        except (OSError, ValueError, TypeError):
            pass
        self.calibration_changed.emit()

    def start(self):
        if self.thread is not None:
            return
        thread = CameraThread(self.index.value(), self)
        self.thread = thread
        self.error = False
        self.state.setText("Iniciando cámara…")
        self.toggle.setText("Desactivar")
        self.toggle.setEnabled(True)
        self.index.setEnabled(False)
        thread.frame_ready.connect(self.show_frame)
        thread.camera_error.connect(self.show_error)
        thread.finished.connect(self.thread_stopped)
        thread.start()

    def toggle_camera(self):
        if self.thread is None:
            self.start()
        else:
            self.stop()

    def stop(self):
        self.cancel_calibration()
        self.gesture.emit("lost", None)
        if self.thread is not None:
            self.thread.requestInterruption()
            self.toggle.setEnabled(False)
            self.state.setText("Cerrando cámara…")

    def thread_stopped(self):
        thread, self.thread = self.thread, None
        if thread is not None:
            thread.deleteLater()
        if not self.error:
            self.state.setText("Cámara desactivada")
        self.latest = None
        self.preview.clear()
        self.preview.setText("Vista de cámara")
        self.index.setEnabled(True)
        self.toggle.setEnabled(True)
        self.toggle.setText("Activar")
        self.calibrate.setEnabled(False)
        self.cancel_calibration()
        self.gesture.emit("lost", None)

    def show_error(self, message):
        self.error = True
        self.state.setText(message)
        self.gesture.emit("lost", None)

    def show_frame(self, image, event):
        if self.thread is None or self.thread.isInterruptionRequested():
            return
        self.latest = QPixmap.fromImage(image)
        self.preview.setPixmap(self.latest.scaled(self.preview.size(), Qt.AspectRatioMode.KeepAspectRatio,
                                                  Qt.TransformationMode.SmoothTransformation))
        kind, info = event
        state = "Cámara activa · Busca una mano" if kind == "lost" else "Cámara activa · Mano detectada"
        if self.state.text() != state:
            self.state.setText(state)
        self.calibrate.setEnabled(True)
        if self.calibration_active:
            if kind == "point":
                self.last_point = info, monotonic()
        else:
            self.gesture.emit(kind, info)

    def toggle_calibration(self):
        if self.calibration_active:
            self.cancel_calibration()
            self.instructions.setText("Calibración cancelada. Sigue disponible la configuración anterior.")
            return
        self.calibration_active = True
        self.calibrating.clear()
        self.calibrate.setText("Cancelar calibración")
        self.gesture.emit("lost", None)
        self.begin_corner()

    def begin_corner(self):
        self.seconds = 5
        self.last_point = None
        self.show_prompt()
        self.timer.start()

    def show_prompt(self):
        corner = "superior izquierda" if not self.calibrating else "inferior derecha"
        self.instructions.setText(f"Índice en la parte {corner} de tu alcance. Captura en {self.seconds} s.")

    def calibration_tick(self):
        self.seconds -= 1
        if self.seconds:
            self.show_prompt()
            return
        self.timer.stop()
        if self.last_point is None or monotonic() - self.last_point[1] > 0.8:
            self.cancel_calibration()
            self.instructions.setText("No se detectó el índice al capturar. Puedes volver a calibrar.")
            return
        self.calibrating.append(self.last_point[0])
        if len(self.calibrating) == 1:
            self.begin_corner()
            return
        first, last = self.calibrating
        value = Calibration(*first, *last)
        self.cancel_calibration()
        if not value.valid():
            self.instructions.setText("Amplía la distancia entre las dos posiciones y repite.")
            return
        self.calibration = value
        self.calibration_changed.emit()
        try:
            DATA_DIR.mkdir(parents=True, exist_ok=True)
            path = DATA_DIR / f"camara-{self.index.value()}.json"
            temporary = path.with_suffix(".tmp")
            temporary.write_text(json.dumps(asdict(value)), encoding="utf-8")
            temporary.replace(path)
        except OSError:
            self.instructions.setText("Calibración aplicada durante esta sesión.")
        else:
            self.instructions.setText("Puntero calibrado. Apunta con el índice dentro del área definida.")

    def cancel_calibration(self):
        self.timer.stop()
        self.calibration_active = False
        self.calibrating.clear()
        self.calibrate.setText("Calibrar puntero")
