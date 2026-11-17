from sqlite3 import Error

from PyQt6.QtCore import QDate
from PyQt6.QtWidgets import QComboBox, QDateEdit, QHBoxLayout, QLineEdit, QPushButton, QVBoxLayout, QWidget

from ..database import list_patients
from .common import back_button, display_time, fill_table, plain, subtitle, table, title


class RecordsPage(QWidget):
    def __init__(self, window):
        super().__init__()
        self.records = []
        self.only_period = False
        layout = QVBoxLayout(self)
        layout.setSpacing(12)
        layout.addWidget(back_button(window))
        layout.addWidget(title("Registros de pacientes"))
        search = QHBoxLayout()
        self.search = QLineEdit()
        self.search.setAccessibleName("Buscar por DNI o nombre")
        self.search.setPlaceholderText("Buscar por DNI o nombre")
        self.search.returnPressed.connect(self.reload)
        search.addWidget(self.search, 1)
        self.priority = QComboBox()
        self.priority.setAccessibleName("Etiqueta")
        self.priority.addItems(["Todas las etiquetas", "Alta", "Normal"])
        search.addWidget(self.priority)
        layout.addLayout(search)

        dates = QHBoxLayout()
        self.period = QPushButton("Usar período")
        self.period.setObjectName("btnSecondary")
        self.period.setCheckable(True)
        self.period.toggled.connect(self.set_period)
        dates.addWidget(self.period)
        today = QDate.currentDate()
        self.start = QDateEdit(today.addDays(-7), calendarPopup=True)
        self.start.setAccessibleName("Fecha desde")
        self.end = QDateEdit(today, calendarPopup=True)
        self.end.setAccessibleName("Fecha hasta")
        for label, field in (("Desde", self.start), ("Hasta", self.end)):
            dates.addWidget(subtitle(label))
            field.setDisplayFormat("dd/MM/yyyy")
            field.setEnabled(False)
            dates.addWidget(field, 1)
        layout.addLayout(dates)

        filters = QHBoxLayout()
        apply = QPushButton("Aplicar filtros")
        apply.clicked.connect(self.reload)
        clear = QPushButton("Limpiar filtros")
        clear.setObjectName("btnSecondary")
        clear.clicked.connect(self.clear)
        filters.addWidget(apply)
        filters.addWidget(clear)
        layout.addLayout(filters)
        self.entries = table(("DNI", "Paciente", "Etiqueta", "Registrado"))
        self.entries.itemSelectionChanged.connect(self.show_details)
        layout.addWidget(self.entries, 1)
        movement = QHBoxLayout()
        for label, delta in (("Fila anterior", -1), ("Fila siguiente", 1)):
            button = QPushButton(label)
            button.setObjectName("btnSecondary")
            button.clicked.connect(lambda checked, step=delta: self.move(step))
            movement.addWidget(button)
        layout.addLayout(movement)
        self.details = plain(subtitle(""))
        layout.addWidget(self.details)
        self.message = subtitle("")
        layout.addWidget(self.message)

    def set_period(self, enabled):
        self.only_period = enabled
        self.start.setEnabled(enabled)
        self.end.setEnabled(enabled)
        self.period.setText("Quitar período" if enabled else "Usar período")

    def clear(self):
        self.search.clear()
        self.priority.setCurrentIndex(0)
        self.period.setChecked(False)
        self.reload()

    def reload(self):
        if self.only_period and self.start.date() > self.end.date():
            self.message.setText("La fecha inicial debe ser anterior o igual a la final.")
            return
        try:
            self.records = list_patients(
                self.search.text(), self.priority.currentText() if self.priority.currentIndex() else "",
                self.start.date().toString("yyyy-MM-dd") if self.only_period else "",
                self.end.date().toString("yyyy-MM-dd") if self.only_period else "",
            )
        except Error:
            self.records = []
            self.message.setText("No se pudieron leer los registros.")
        else:
            self.message.setText(f"{len(self.records)} registros encontrados")
        fill_table(self.entries, [
            (row["dni"], f"{row['nombres']} {row['apellido_paterno']} {row['apellido_materno']}".strip(),
             row["urgencia"], display_time(row["fecha"]))
            for row in self.records
        ])
        if self.records:
            self.entries.selectRow(0)
        self.show_details()

    def move(self, step):
        if self.records:
            self.entries.selectRow(max(0, min(len(self.records) - 1, self.entries.currentRow() + step)))

    def show_details(self):
        index = self.entries.currentRow()
        if not 0 <= index < len(self.records):
            self.details.clear()
            return
        row = self.records[index]
        self.details.setText(f"Edad: {row['edad']} años · Distrito: {row['distrito'] or '—'}\n"
                             f"Síntomas reportados: {row['sintomas']}")
