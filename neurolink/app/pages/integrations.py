from PyQt6.QtWidgets import QFormLayout, QHBoxLayout, QLineEdit, QPushButton, QVBoxLayout, QWidget

from ..dni import lookup_dni
from ..ia_client import request_completion
from ..settings import INTEGRATION_FIELDS, save_integrations, setting
from .common import back_button, subtitle, title


class IntegrationsPage(QWidget):
    def __init__(self, window):
        super().__init__()
        self.window = window
        layout = QVBoxLayout(self)
        layout.setSpacing(16)
        layout.addWidget(back_button(window))
        layout.addWidget(title("Configuración de integraciones"))
        layout.addWidget(subtitle("Actualiza las claves de MiAPI Cloud y OpenRouter. Los cambios se aplican al guardar."))
        fields = QFormLayout()
        self.inputs = {}
        for key, caption in zip(INTEGRATION_FIELDS, ("Clave de MiAPI Cloud", "Clave de OpenRouter", "Modelo de OpenRouter")):
            field = QLineEdit()
            field.setAccessibleName(caption)
            if key != "OPENROUTER_MODEL":
                field.setEchoMode(QLineEdit.EchoMode.Password)
            self.inputs[key] = field
            fields.addRow(caption, field)
        layout.addLayout(fields)

        save = QPushButton("Guardar cambios")
        save.clicked.connect(self.save)
        layout.addWidget(save)

        row = QHBoxLayout()
        self.dni = QLineEdit()
        self.dni.setAccessibleName("DNI autorizado para verificar MiAPI Cloud")
        self.dni.setPlaceholderText("DNI autorizado para verificar MiAPI")
        self.dni.setMaxLength(8)
        row.addWidget(self.dni)
        for caption, provider in (("Probar MiAPI", "dni"), ("Probar OpenRouter", "ai")):
            button = QPushButton(caption)
            button.setObjectName("btnSecondary")
            button.clicked.connect(lambda checked, name=provider: self.test(name))
            row.addWidget(button)
        layout.addLayout(row)
        self.message = subtitle("")
        layout.addWidget(self.message)
        layout.addStretch()

    def reload(self):
        self.window.invalidate_job("integration-check")
        for key, field in self.inputs.items():
            field.setText(setting(key))
        self.dni.clear()
        self.message.clear()

    def values(self):
        return {key: field.text().strip() for key, field in self.inputs.items()}

    def save(self):
        self.window.invalidate_job("integration-check")
        try:
            save_integrations(self.values())
        except (OSError, ValueError) as error:
            self.message.setText(str(error) if isinstance(error, ValueError) else "No se pudo guardar la configuración local.")
            return
        self.reload()
        self.window.pages["query"].refresh_integration()
        self.message.setText("Configuración guardada. Las siguientes consultas usarán las nuevas claves.")

    def test(self, provider):
        config = self.values()
        self.message.setText("Comprobando la conexión…")
        if provider == "dni":
            dni = self.dni.text().strip()
            self.dni.clear()

            def work():
                lookup_dni(dni, token=config["API_TOKEN"])
                return "MiAPI Cloud respondió correctamente."
        else:
            def work():
                request_completion(config["OPENROUTER_API_KEY"], config["OPENROUTER_MODEL"],
                                   [{"role": "user", "content": "Responde con la palabra OK."}])
                return "OpenRouter respondió correctamente."
        self.window.background("integration-check", work, self.finish_test)

    def finish_test(self, result, error):
        if error:
            self.message.setText(str(error) if isinstance(error, ValueError) else "No se pudo verificar la conexión.")
        else:
            self.message.setText(result)
