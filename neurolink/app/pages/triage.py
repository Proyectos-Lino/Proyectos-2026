from PyQt6.QtWidgets import QHBoxLayout, QProgressBar, QPushButton, QSpinBox, QVBoxLayout, QWidget
from sqlite3 import Error

from ..database import insert_patient
from .common import back_button, plain, subtitle, title


QUESTIONS = (
    ("¿Tiene fiebre alta (más de 38 °C)?", "Fiebre alta"),
    ("¿Tiene dificultad para respirar?", "Dificultad para respirar"),
    ("¿Siente dolor en el pecho?", "Dolor en el pecho"),
    ("¿Ha perdido el conocimiento en las últimas 24 horas?", "Pérdida de conocimiento reportada"),
    ("¿Tiene algún sangrado inusual?", "Sangrado inusual"),
)


class TriagePage(QWidget):
    def __init__(self, window):
        super().__init__()
        self.window = window
        self.patient = None
        self.answers = []
        self.index = 0
        layout = QVBoxLayout(self)
        layout.setSpacing(17)
        layout.addWidget(back_button(window))
        layout.addWidget(title("Cuestionario de triaje"))
        self.patient_label = plain(subtitle(""))
        layout.addWidget(self.patient_label)
        layout.addWidget(subtitle("El resultado queda disponible para revisión del personal."))
        self.age = QSpinBox()
        self.age.setRange(-1, 130)
        self.age.setSpecialValueText("Indicar edad")
        self.age.setPrefix("Edad en años: ")
        self.age.setAccessibleName("Edad en años")
        layout.addWidget(self.age)
        self.progress = QProgressBar()
        self.progress.setRange(0, len(QUESTIONS))
        self.progress.setFormat("%v de %m respuestas")
        layout.addWidget(self.progress)
        self.step = subtitle("")
        self.question = title("")
        layout.addWidget(self.step)
        layout.addWidget(self.question)
        row = QHBoxLayout()
        self.yes = QPushButton("Sí")
        self.yes.setObjectName("btnSuccess")
        self.no = QPushButton("No")
        self.no.setObjectName("btnDanger")
        self.yes.clicked.connect(lambda: self.answer(True))
        self.no.clicked.connect(lambda: self.answer(False))
        row.addWidget(self.yes)
        row.addWidget(self.no)
        layout.addLayout(row)
        row = QHBoxLayout()
        self.previous = QPushButton("← Anterior")
        self.previous.setObjectName("btnSecondary")
        self.previous.clicked.connect(self.previous_question)
        self.next = QPushButton("Siguiente →")
        self.next.setObjectName("btnSecondary")
        self.next.clicked.connect(self.next_question)
        self.save = QPushButton("Finalizar y guardar")
        self.save.setObjectName("btnSuccess")
        self.save.clicked.connect(self.save_results)
        for button in (self.previous, self.next, self.save):
            row.addWidget(button)
        layout.addLayout(row)
        self.message = subtitle("")
        layout.addWidget(self.message)
        layout.addStretch()

    def start(self, patient):
        self.patient = patient
        self.index = 0
        self.answers = [None] * len(QUESTIONS)
        self.patient_label.setText(f"{patient['nombres']} {patient['apellido_paterno']} · DNI {patient['dni']}")
        self.age.setValue(patient["edad"] if patient["edad"] is not None else -1)
        self.age.setVisible(patient["edad"] is None)
        self.show_question()

    def show_question(self):
        finished = self.index == len(QUESTIONS)
        answered = sum(answer is not None for answer in self.answers)
        self.progress.setValue(answered)
        self.step.setText("Revisión final" if finished else f"Pregunta {self.index + 1} de {len(QUESTIONS)}")
        self.question.setText("Cuestionario completo" if finished else QUESTIONS[self.index][0])
        if finished:
            count = sum(answer is True for answer in self.answers)
            current = f"Respuestas afirmativas: {count}. Puedes volver a revisar antes de guardar."
        else:
            answer = self.answers[self.index]
            current = "" if answer is None else "Respuesta actual: " + ("Sí" if answer else "No")
        self.message.setText(current)
        self.yes.setVisible(not finished)
        self.no.setVisible(not finished)
        self.next.setVisible(not finished)
        self.previous.setEnabled(self.index > 0)
        self.next.setEnabled(not finished and self.answers[self.index] is not None)
        self.save.setVisible(finished)
        self.save.setEnabled(finished and answered == len(QUESTIONS))

    def answer(self, value):
        if self.index < len(QUESTIONS):
            self.answers[self.index] = value
            self.next_question()

    def next_question(self):
        if self.index < len(QUESTIONS) and self.answers[self.index] is not None:
            self.index += 1
            self.show_question()
        elif self.index < len(QUESTIONS):
            self.message.setText("Responde la pregunta antes de avanzar.")

    def previous_question(self):
        if self.index > 0:
            self.index -= 1
            self.show_question()

    def save_results(self):
        if self.patient is None or any(answer is None for answer in self.answers):
            self.message.setText("Completa el cuestionario antes de guardar.")
            return
        if self.age.value() < 0:
            self.message.setText("Indica la edad en años antes de guardar.")
            self.age.setFocus()
            return
        patient = {**self.patient, "edad": self.age.value()}
        symptoms = ", ".join(item[1] for item, answer in zip(QUESTIONS, self.answers) if answer)
        symptoms = symptoms or "Ningún síntoma reportado"
        label = "Alta" if any(self.answers) else "Normal"
        try:
            saved = insert_patient(patient, symptoms, label)
        except Error:
            self.message.setText("No se pudo guardar. Revisa el acceso a la carpeta de datos y reintenta.")
            return
        if not saved:
            self.message.setText("Este DNI ya tiene un registro.")
            return
        self.window.switch_page("report", patient=patient, label=label, symptoms=symptoms)
