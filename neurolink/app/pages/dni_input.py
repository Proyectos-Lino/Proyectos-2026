from PyQt6.QtCore import pyqtSignal
from PyQt6.QtGui import QRegularExpressionValidator
from PyQt6.QtCore import QRegularExpression
from PyQt6.QtWidgets import QGridLayout, QLineEdit, QPushButton, QVBoxLayout, QWidget

from .common import back_button, subtitle, title


class DniInputPage(QWidget):
    dni_submitted = pyqtSignal(str)

    def __init__(self, window):
        super().__init__()
        self.busy = False
        layout = QVBoxLayout(self)
        layout.setSpacing(16)
        layout.addWidget(back_button(window))
        layout.addWidget(title("Auto-registro y triaje"))
        layout.addWidget(subtitle("Ingresa el DNI de ocho dígitos del paciente."))
        self.display = QLineEdit()
        self.display.setAccessibleName("DNI")
        self.display.setPlaceholderText("DNI de 8 dígitos")
        self.display.setMaxLength(8)
        self.display.setValidator(QRegularExpressionValidator(QRegularExpression("[0-9]{0,8}")))
        self.display.returnPressed.connect(self.submit)
        layout.addWidget(self.display)

        grid = QGridLayout()
        for digit in range(1, 10):
            button = QPushButton(str(digit))
            button.setMinimumHeight(46)
            button.clicked.connect(lambda checked, n=digit: self.add_digit(str(n)))
            grid.addWidget(button, (digit - 1) // 3, (digit - 1) % 3)
        delete = QPushButton("Borrar")
        delete.setObjectName("btnSecondary")
        delete.clicked.connect(self.delete_digit)
        zero = QPushButton("0")
        zero.clicked.connect(lambda: self.add_digit("0"))
        self.confirm = QPushButton("Confirmar DNI")
        self.confirm.setObjectName("btnSuccess")
        self.confirm.clicked.connect(self.submit)
        grid.addWidget(delete, 3, 0)
        grid.addWidget(zero, 3, 1)
        grid.addWidget(self.confirm, 3, 2)
        layout.addLayout(grid)
        self.message = subtitle("")
        layout.addWidget(self.message)
        layout.addStretch()

    def add_digit(self, digit):
        if not self.busy and len(self.display.text()) < 8:
            self.display.setText(self.display.text() + digit)

    def delete_digit(self):
        if not self.busy:
            self.display.setText(self.display.text()[:-1])

    def submit(self):
        if self.busy:
            return
        if len(self.display.text()) != 8:
            self.message.setText("Completa los ocho dígitos.")
            return
        self.dni_submitted.emit(self.display.text())

    def set_busy(self, busy):
        self.busy = busy
        self.display.setReadOnly(busy)
        self.confirm.setEnabled(not busy)
        self.message.setText("Consultando DNI…" if busy else "")

    def reset(self):
        self.set_busy(False)
        self.display.clear()
