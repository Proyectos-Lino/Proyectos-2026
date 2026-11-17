from sqlite3 import Error

from PyQt6.QtWidgets import QComboBox, QHBoxLayout, QPushButton, QVBoxLayout, QWidget

from ..database import add_request
from .common import back_button, subtitle, title


class AssistantPage(QWidget):
    def __init__(self, window):
        super().__init__()
        self.kind = None
        layout = QVBoxLayout(self)
        layout.setSpacing(15)
        layout.addWidget(back_button(window))
        layout.addWidget(title("Asistente de paciente"))
        layout.addWidget(subtitle("Selecciona una solicitud para la bandeja local del personal."))
        rooms = QHBoxLayout()
        self.room = QComboBox()
        self.room.setAccessibleName("Habitación")
        self.room.addItems(["Habitación 101", "Habitación 102", "Habitación 103", "Habitación 104"])
        rooms.addWidget(self.room, 1)
        for text, delta in (("← Habitación anterior", -1), ("Siguiente habitación →", 1)):
            button = QPushButton(text)
            button.setObjectName("btnSecondary")
            button.clicked.connect(lambda checked, step=delta: self.step_room(step))
            rooms.addWidget(button)
        layout.addLayout(rooms)
        self.actions = []
        for text, value in (("Pedir agua", "Agua"), ("Pedir ayuda para ir al baño", "Ayuda para ir al baño"),
                            ("Solicitar un analgésico", "Solicitud de analgésico"),
                            ("Llamar a enfermería", "Llamado a enfermería")):
            button = QPushButton(text)
            button.setMinimumHeight(47)
            button.clicked.connect(lambda checked, kind=value: self.ask(kind))
            layout.addWidget(button)
            self.actions.append(button)

        self.message = subtitle("")
        self.room.currentTextChanged.connect(self.update_prompt)
        layout.addWidget(self.message)
        self.confirmation = QWidget()
        row = QHBoxLayout(self.confirmation)
        row.setContentsMargins(0, 0, 0, 0)
        confirm = QPushButton("Confirmar solicitud")
        confirm.setObjectName("btnSuccess")
        confirm.clicked.connect(self.submit)
        cancel = QPushButton("Cancelar")
        cancel.setObjectName("btnSecondary")
        cancel.clicked.connect(self.reset)
        row.addWidget(confirm)
        row.addWidget(cancel)
        layout.addWidget(self.confirmation)
        layout.addStretch()
        self.reset()

    def step_room(self, step):
        self.room.setCurrentIndex((self.room.currentIndex() + step) % self.room.count())

    def ask(self, kind):
        self.kind = kind
        self.confirmation.show()
        self.update_prompt()

    def update_prompt(self):
        if self.kind:
            self.message.setText(f"Confirma «{self.kind}» para {self.room.currentText().lower()}.")

    def submit(self):
        if not self.kind:
            return
        try:
            number = add_request(self.room.currentText(), self.kind)
        except Error:
            self.message.setText("No se pudo guardar la solicitud. Reintenta.")
            return
        self.reset()
        self.message.setText(f"Solicitud #{number} guardada como pendiente en la bandeja local.")

    def reset(self):
        self.kind = None
        self.confirmation.hide()
        self.message.clear()
