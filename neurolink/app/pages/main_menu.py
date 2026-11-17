from PyQt6.QtWidgets import QGridLayout, QPushButton, QVBoxLayout, QWidget

from .common import subtitle, title


class MainMenuPage(QWidget):
    def __init__(self, window):
        super().__init__()
        layout = QVBoxLayout(self)
        layout.setSpacing(16)
        layout.addWidget(title("NeuroLink"))
        layout.addWidget(subtitle("Asistente clínico sin contacto"))
        layout.addSpacing(6)
        for caption, page in (
            ("Auto-registro y triaje", "dni"),
            ("Asistente de paciente", "assistant"),
            ("Registros de pacientes", "records"),
            ("Consulta en lenguaje natural", "query"),
        ):
            button = QPushButton(caption)
            button.setMinimumHeight(54)
            button.clicked.connect(lambda checked, target=page: window.switch_page(target))
            layout.addWidget(button)

        extra = QGridLayout()
        for i, (caption, page) in enumerate((("Bandeja de solicitudes", "requests"), ("Ayuda de gestos", "help"))):
            button = QPushButton(caption)
            button.setObjectName("btnSecondary")
            button.setMinimumHeight(44)
            button.clicked.connect(lambda checked, target=page: window.switch_page(target))
            extra.addWidget(button, 0, i)
        layout.addLayout(extra)
        integrations = QPushButton("Configuración de integraciones")
        integrations.setObjectName("btnSecondary")
        integrations.clicked.connect(lambda: window.switch_page("integrations"))
        layout.addWidget(integrations)
        layout.addStretch()
