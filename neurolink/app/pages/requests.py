from sqlite3 import Error

from PyQt6.QtWidgets import QHBoxLayout, QPushButton, QVBoxLayout, QWidget

from ..database import attend_request, list_requests
from .common import back_button, display_time, fill_table, subtitle, table, title


class RequestsPage(QWidget):
    def __init__(self, window):
        super().__init__()
        self.status = "pending"
        self.rows = []
        layout = QVBoxLayout(self)
        layout.setSpacing(14)
        layout.addWidget(back_button(window))
        layout.addWidget(title("Bandeja de solicitudes"))
        layout.addWidget(subtitle("Consulta las solicitudes por habitación y marca las atendidas."))
        tabs = QHBoxLayout()
        for name, status in (("Pendientes", "pending"), ("Atendidas", "done"), ("Todas", "all")):
            button = QPushButton(name)
            button.setObjectName("btnSecondary")
            button.clicked.connect(lambda checked, value=status: self.reload(value))
            tabs.addWidget(button)
        layout.addLayout(tabs)
        self.entries = table(("N.º", "Solicitud", "Habitación", "Estado", "Creada", "Atendida"))
        layout.addWidget(self.entries)
        self.entries.itemSelectionChanged.connect(self.update_actions)
        actions = QHBoxLayout()
        for label, action in (("Fila anterior", lambda: self.move(-1)),
                              ("Fila siguiente", lambda: self.move(1)),
                              ("Actualizar", lambda: self.reload())):
            button = QPushButton(label)
            button.setObjectName("btnSecondary")
            button.clicked.connect(action)
            actions.addWidget(button)
        self.attend = QPushButton("Marcar atendida")
        self.attend.setObjectName("btnSuccess")
        self.attend.clicked.connect(self.mark_done)
        actions.addWidget(self.attend)
        layout.addLayout(actions)
        self.message = subtitle("")
        layout.addWidget(self.message)

    def reload(self, status=None):
        if status:
            self.status = status
        try:
            self.rows = list_requests(self.status)
        except Error:
            self.rows = []
            self.message.setText("La bandeja no está disponible. Comprueba la carpeta de datos.")
        else:
            caption = {"pending": "Pendientes", "done": "Atendidas", "all": "Todas"}[self.status]
            self.message.setText(f"{caption} · {len(self.rows)} solicitudes")
        fill_table(self.entries, [
            (row["id"], row["tipo"], row["habitacion"], "Atendida" if row["atendida_en"] else "Pendiente",
             display_time(row["fecha"]), display_time(row["atendida_en"]))
            for row in self.rows
        ])
        if self.rows:
            self.entries.selectRow(0)
        self.update_actions()

    def move(self, step):
        if self.rows:
            self.entries.selectRow(max(0, min(len(self.rows) - 1, self.entries.currentRow() + step)))

    def selected(self):
        index = self.entries.currentRow()
        return self.rows[index] if 0 <= index < len(self.rows) else None

    def update_actions(self):
        request = self.selected()
        self.attend.setEnabled(request is not None and request["atendida_en"] is None)

    def mark_done(self):
        request = self.selected()
        if request is None or request["atendida_en"]:
            return
        try:
            changed = attend_request(request["id"])
        except Error:
            self.message.setText("No se pudo actualizar la solicitud. Reintenta.")
            return
        self.reload()
        self.message.setText(f"Solicitud #{request['id']} atendida." if changed else "La solicitud ya fue atendida.")
