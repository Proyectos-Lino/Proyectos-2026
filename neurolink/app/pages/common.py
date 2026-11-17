from datetime import timezone, datetime

from PyQt6.QtCore import Qt
from PyQt6.QtWidgets import QLabel, QPushButton, QTableWidget, QHeaderView, QAbstractItemView, QTableWidgetItem


def title(text):
    label = QLabel(text)
    label.setObjectName("title")
    label.setWordWrap(True)
    return label


def subtitle(text):
    label = QLabel(text)
    label.setObjectName("subtitle")
    label.setWordWrap(True)
    return label


def back_button(window):
    button = QPushButton("← Menú principal")
    button.setObjectName("btnSecondary")
    button.clicked.connect(window.back)
    return button


def table(headers):
    widget = QTableWidget(0, len(headers))
    widget.setHorizontalHeaderLabels(headers)
    widget.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
    widget.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
    widget.setSelectionMode(QAbstractItemView.SelectionMode.SingleSelection)
    widget.setAlternatingRowColors(True)
    widget.verticalHeader().hide()
    widget.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.ResizeToContents)
    widget.horizontalHeader().setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)
    return widget


def fill_table(widget, rows):
    widget.setRowCount(len(rows))
    for row, values in enumerate(rows):
        for column, value in enumerate(values):
            widget.setItem(row, column, QTableWidgetItem(str(value)))


def display_time(value):
    if not value:
        return "—"
    try:
        parsed = datetime.fromisoformat(value)
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=timezone.utc)
        return parsed.astimezone().strftime("%d/%m/%Y %H:%M")
    except ValueError:
        return value


def plain(label):
    label.setTextFormat(Qt.TextFormat.PlainText)
    return label
