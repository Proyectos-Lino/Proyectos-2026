from PyQt6.QtWidgets import QGridLayout, QHBoxLayout, QLineEdit, QPushButton, QTextEdit, QVBoxLayout, QWidget

from ..ia_client import EXAMPLES, ai_available, ai_summary, local_answer
from .common import back_button, subtitle, title


class NaturalQueryPage(QWidget):
    def __init__(self, window):
        super().__init__()
        self.window = window
        layout = QVBoxLayout(self)
        layout.setSpacing(14)
        layout.addWidget(back_button(window))
        layout.addWidget(title("Consulta en lenguaje natural"))
        layout.addWidget(subtitle("La consulta local responde conteos y resúmenes en español."))
        self.input = QLineEdit()
        self.input.setMaxLength(300)
        self.input.setAccessibleName("Pregunta para la consulta local")
        self.input.setPlaceholderText("Escribe una pregunta o elige un ejemplo")
        self.input.returnPressed.connect(self.answer)
        layout.addWidget(self.input)
        examples = QGridLayout()
        for index, caption in enumerate(("Total de pacientes", "Etiqueta Alta", "Registros de hoy",
                                         "Solicitudes pendientes", "Solicitudes atendidas", "Resumen local")):
            button = QPushButton(caption)
            button.setObjectName("btnSecondary")
            button.clicked.connect(lambda checked, question=EXAMPLES[index]: self.use_example(question))
            examples.addWidget(button, index // 2, index % 2)
        layout.addLayout(examples)
        actions = QHBoxLayout()
        query = QPushButton("Consultar datos")
        query.clicked.connect(self.answer)
        actions.addWidget(query)
        self.ai = QPushButton("Redactar resumen con IA")
        self.ai.setEnabled(ai_available())
        self.ai.setObjectName("btnSecondary")
        self.ai.clicked.connect(self.use_ai)
        actions.addWidget(self.ai)
        layout.addLayout(actions)
        self.integration_message = subtitle("")
        layout.addWidget(self.integration_message)
        self.refresh_integration()
        self.output = QTextEdit()
        self.output.setReadOnly(True)
        self.output.setAccessibleName("Resultado de la consulta")
        self.output.setPlaceholderText("Aquí verás el resultado.")
        layout.addWidget(self.output, 1)

    def refresh_integration(self):
        available = ai_available()
        self.ai.setEnabled(available)
        self.integration_message.setText("OpenRouter recibe solo conteos agregados." if available
                                         else "Puedes conectar OpenRouter desde Configuración de integraciones.")

    def use_example(self, question):
        self.input.setText(question)
        self.answer()

    def answer(self):
        self.window.invalidate_job("ai")
        self.ai.setEnabled(ai_available())
        self.output.setPlainText(local_answer(self.input.text()))

    def use_ai(self):
        self.ai.setEnabled(False)
        self.output.setPlainText("Preparando resumen con OpenRouter…")
        self.window.background("ai", ai_summary, self.finish_ai)

    def finish_ai(self, result, error):
        self.ai.setEnabled(ai_available())
        self.output.setPlainText(result if not error else "El resumen no está disponible. Usa la consulta local.")
