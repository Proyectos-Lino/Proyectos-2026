from PyQt6.QtWidgets import QFormLayout, QPushButton, QVBoxLayout, QWidget

from .common import back_button, plain, subtitle, title


class ReportPage(QWidget):
    def __init__(self, window):
        super().__init__()
        layout = QVBoxLayout(self)
        layout.setSpacing(15)
        layout.addWidget(back_button(window))
        layout.addWidget(title("Reporte de registro"))
        layout.addWidget(subtitle("Registro guardado en esta computadora."))
        details = QFormLayout()
        self.values = {}
        for key, caption in (
            ("dni", "DNI"), ("name", "Paciente"), ("age", "Edad"),
            ("district", "Distrito y provincia"), ("symptoms", "Síntomas reportados"),
            ("urgency", "Etiqueta del cuestionario"),
        ):
            value = plain(subtitle(""))
            self.values[key] = value
            details.addRow(caption, value)
        layout.addLayout(details)
        layout.addWidget(subtitle("Resultado del cuestionario · Revisión del personal"))
        records = QPushButton("Ver registros")
        records.clicked.connect(lambda: window.switch_page("records"))
        layout.addWidget(records)
        layout.addStretch()

    def show_report(self, patient, label, symptoms):
        info = {
            "dni": patient["dni"],
            "name": " ".join(patient[key] for key in ("nombres", "apellido_paterno", "apellido_materno")).strip(),
            "age": f"{patient['edad']} años",
            "district": " · ".join(x for x in (patient["distrito"], patient["provincia"]) if x) or "—",
            "symptoms": symptoms,
            "urgency": label,
        }
        for key, value in info.items():
            self.values[key].setText(value)
