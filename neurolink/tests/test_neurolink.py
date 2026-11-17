import os
import tempfile
import unittest
from pathlib import Path
from sqlite3 import connect
from time import monotonic
from unittest.mock import patch

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from app.ui import MainWindow

from PyQt6.QtCore import Qt
from PyQt6.QtTest import QTest
from PyQt6.QtWidgets import QApplication, QLineEdit, QPushButton

from app import database, settings
from app.dni import lookup_dni
from app.gestures import BACK, CLICK, OPEN, POINT, Calibration, GestureDetector
from app.ia_client import ai_summary, local_answer
from app.pages.triage import QUESTIONS


def patient(dni="12345678", name="Lucía"):
    return {"dni": dni, "nombres": name, "apellido_paterno": "Apellidos", "apellido_materno": "",
            "edad": 30, "direccion": "Domicilio", "distrito": "", "provincia": ""}


class IsolatedDatabase(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.file = Path(directory.name) / "neuro.db"
        db = patch.object(database, "DB_PATH", self.file)
        db.start()
        self.addCleanup(db.stop)
        database.initialize()


class TestPersistence(IsolatedDatabase):
    def test_dni_duplicates_and_record_filters(self):
        record = patient()
        self.assertTrue(database.insert_patient(record, "Registro", "Normal"))
        self.assertFalse(database.insert_patient(record, "Registro", "Alta"))
        self.assertEqual(len(database.list_patients("LUCÍA", "Normal")), 1)
        self.assertEqual(database.list_patients("12345678", "Alta"), [])
        self.assertEqual(database.list_patients(start="2999-01-01"), [])
        database.initialize()
        self.assertIsNotNone(database.get_patient_by_dni("12345678"))

    def test_migration_removes_only_legacy_demo_records(self):
        database.insert_patient({**patient("00000001"), "direccion": "Domicilio de demostración"}, "Registro", "Alta")
        database.insert_patient(patient("00000002"), "Registro", "Alta")
        database.insert_patient(patient(), "Registro", "Normal")
        database.initialize()
        database.initialize()
        self.assertIsNone(database.get_patient_by_dni("00000001"))
        self.assertIsNotNone(database.get_patient_by_dni("00000002"))
        self.assertIsNotNone(database.get_patient_by_dni("12345678"))

    def test_assistance_lifecycle(self):
        number = database.add_request("Habitación 102", "Agua")
        self.assertEqual(database.list_requests("pending")[0]["id"], number)
        self.assertTrue(database.attend_request(number))
        self.assertFalse(database.attend_request(number))
        self.assertEqual(database.list_requests("pending"), [])
        self.assertEqual(database.list_requests("done")[0]["tipo"], "Agua")

    def test_local_and_external_aggregate_queries(self):
        database.insert_patient(patient("23456789", "Mateo"), "Registro", "Alta")
        database.add_request("Habitación 101", "Agua")
        self.assertIn("1 pacientes", local_answer("¿Cuántos pacientes están registrados?"))
        self.assertIn("1 registros", local_answer("¿Cuántos registros de urgencia alta hay?"))
        self.assertIn("1 solicitudes", local_answer("¿Cuántas solicitudes están pendientes?"))
        self.assertIn("ejemplos", local_answer("¿Cuál es el DNI del paciente?"))
        credentials = {"OPENROUTER_API_KEY": "test-key", "OPENROUTER_MODEL": "openrouter/free",
                       "NEUROLINK_OPENROUTER": "true"}
        with patch.dict(settings.LOCAL, credentials), patch("app.ia_client.requests.post") as http:
            http.return_value.json.return_value = {"choices": [{"message": {"content": "Resumen de prueba."}}]}
            self.assertEqual(ai_summary(), "Resumen de prueba.")
            payload = str(http.call_args.kwargs["json"])
            self.assertNotIn("23456789", payload)
            self.assertNotIn("Mateo", payload)
            self.assertNotIn("Habitación 101", payload)
            self.assertIn("pendientes", payload)


class TestGestures(unittest.TestCase):
    def test_mapping_and_sustained_click(self):
        calibration = Calibration()
        self.assertTrue(calibration.valid())
        self.assertEqual(calibration.map(0, 1), (0, 1))
        self.assertFalse(Calibration(0.4, 0.13, 0.5, 0.87).valid())
        detector = GestureDetector()
        events = [detector.update(CLICK, (0.4, 0.4), (0.4, 0.4))[0] for _ in range(35)]
        self.assertEqual(events.count("click"), 1)
        for _ in range(3):
            detector.update(POINT, (0.4, 0.4), (0.4, 0.4))
        events = [detector.update(CLICK, (0.4, 0.4), (0.4, 0.4))[0] for _ in range(7)]
        self.assertEqual(events.count("click"), 1)

    def test_back_swipe_and_loss(self):
        detector = GestureDetector()
        events = [detector.update(BACK, (0.3, 0.3), (0.3, 0.3))[0] for _ in range(22)]
        self.assertEqual(events.count("back"), 1)
        events = [detector.update(OPEN, None, (0.15 + i * 0.05, 0.5), now=i * 0.04)
                  for i in range(9)]
        self.assertIn(("swipe", "right"), events)
        self.assertEqual(detector.update(None, None, None), ("lost", None))
        self.assertEqual(len(detector.palms), 0)


class TestDesktop(IsolatedDatabase):
    @classmethod
    def setUpClass(cls):
        cls.app = QApplication.instance() or QApplication([])

    def setUp(self):
        super().setUp()
        self.window = MainWindow(auto_camera=False)
        self.window.show()
        self.app.processEvents()
        self.addCleanup(self.window.close)

    def wait_for_page(self, page):
        for _ in range(35):
            if self.window.stack.currentWidget() is page:
                return
            QTest.qWait(30)
        self.fail("La ventana no llegó a la página solicitada")

    @patch("app.ui.lookup_dni", return_value=patient())
    def test_registration_workflow(self, provider):
        self.window.switch_page("dni")
        dni = self.window.pages["dni"]
        QTest.keyClicks(dni.display, "123456")
        QTest.mouseClick(dni.confirm, Qt.MouseButton.LeftButton)
        self.assertIn("ocho", dni.message.text())
        QTest.keyClicks(dni.display, "78")
        QTest.keyClick(dni.display, Qt.Key.Key_Return)
        triage = self.window.pages["triage"]
        self.wait_for_page(triage)
        provider.assert_called_once_with("12345678")
        self.window.handle_gesture("swipe", "right")
        self.assertEqual(triage.index, 0)
        for _ in QUESTIONS:
            QTest.mouseClick(triage.no, Qt.MouseButton.LeftButton)
        self.assertEqual(triage.progress.value(), len(QUESTIONS))
        self.window.handle_gesture("swipe", "left")
        QTest.mouseClick(triage.yes, Qt.MouseButton.LeftButton)
        QTest.mouseClick(triage.save, Qt.MouseButton.LeftButton)
        report = self.window.pages["report"]
        self.assertIs(self.window.stack.currentWidget(), report)
        self.assertEqual(report.values["urgency"].text(), "Alta")
        self.window.switch_page("records")
        records = self.window.pages["records"]
        self.assertEqual(records.entries.rowCount(), 1)
        records.search.setText("Sin coincidencia")
        records.reload()
        self.assertEqual(records.entries.rowCount(), 0)
        self.window.switch_page("dni")
        dni.display.setText("12345678")
        QTest.mouseClick(dni.confirm, Qt.MouseButton.LeftButton)
        self.assertIn("ya está registrado", dni.message.text())

        self.window.switch_page("assistant")
        assistant = self.window.pages["assistant"]
        assistant.step_room(1)
        QTest.mouseClick(assistant.actions[0], Qt.MouseButton.LeftButton)
        self.assertEqual(database.list_requests(), [])
        assistant.submit()
        self.window.switch_page("requests")
        requests = self.window.pages["requests"]
        self.assertEqual(requests.entries.rowCount(), 1)
        QTest.mouseClick(requests.attend, Qt.MouseButton.LeftButton)
        requests.reload("done")
        self.assertEqual(requests.entries.item(0, 3).text(), "Atendida")
        self.window.switch_page("query")
        query = self.window.pages["query"]
        query.use_example("¿Cuántas solicitudes fueron atendidas?")
        self.assertIn("1 solicitudes atendidas", query.output.toPlainText())

    def test_masked_keys_apply_to_subsequent_calls(self):
        defaults = {"NEUROLINK_DNI_API": "false", "NEUROLINK_OPENROUTER": "false",
                    "API_TOKEN": "old-dni", "OPENROUTER_API_KEY": "old-ai", "OPENROUTER_MODEL": "openrouter/free"}
        with (patch.object(settings, "DATA_DIR", self.file.parent),
              patch.dict(settings.ENV, defaults, clear=True),
              patch.dict(settings.LOCAL, {}, clear=True),
              patch("app.dni.requests.get") as dni_call,
              patch("app.ia_client.requests.post") as ai_call):
            self.window.switch_page("integrations")
            page = self.window.pages["integrations"]
            self.assertEqual(page.inputs["API_TOKEN"].echoMode(), QLineEdit.EchoMode.Password)
            self.assertEqual(page.inputs["OPENROUTER_API_KEY"].echoMode(), QLineEdit.EchoMode.Password)
            self.assertEqual(page.inputs["API_TOKEN"].text(), "old-dni")
            page.inputs["API_TOKEN"].setText("new-dni")
            page.inputs["OPENROUTER_API_KEY"].setText("new-ai")
            page.save()
            self.assertIn("Configuración guardada", page.message.text())
            self.assertEqual(settings.read_integrations(settings.json_path())["NEUROLINK_DNI_API"], "true")
            dni_call.return_value.status_code = 200
            dni_call.return_value.json.return_value = {"datos": {"nombres": "Registro"}}
            self.assertEqual(lookup_dni("98765432")["nombres"], "Registro")
            self.assertEqual(dni_call.call_args.kwargs["headers"]["Authorization"], "Bearer new-dni")
            ai_call.return_value.status_code = 200
            ai_call.return_value.json.return_value = {"choices": [{"message": {"content": "Respuesta"}}]}
            self.assertEqual(ai_summary(), "Respuesta")
            self.assertEqual(ai_call.call_args.kwargs["headers"]["Authorization"], "Bearer new-ai")
            self.assertTrue(self.window.pages["query"].ai.isEnabled())

    def test_open_hand_preserves_target_and_rearms_thumb_click(self):
        self.window.switch_page("dni")
        button = next(widget for widget in self.window.pages["dni"].findChildren(QPushButton)
                      if widget.text() == "1")
        clicks = []
        button.clicked.connect(lambda: clicks.append(1))
        center = button.mapTo(self.window.stack, button.rect().center())
        bounds = self.window.camera.calibration
        point = (bounds.left + (bounds.right - bounds.left) * center.x() / (self.window.stack.width() - 1),
                 bounds.top + (bounds.bottom - bounds.top) * center.y() / (self.window.stack.height() - 1))
        detector = GestureDetector()
        with patch("app.ui.monotonic", return_value=0.0) as clock:
            def feed(gesture, frames, position=point):
                for _ in range(frames):
                    clock.return_value += 0.05
                    event = detector.update(gesture, position, (0.5, 0.5), now=clock.return_value)
                    self.window.handle_gesture(*event)

            feed(POINT, 5)
            pointer = self.window.pointer.point
            feed(OPEN, 100, (0.85, 0.8))
            self.assertIs(self.window.target, button)
            self.assertTrue(self.window.pointer.isVisible())
            self.assertEqual(self.window.pointer.point, pointer)
            feed(CLICK, 25)
            self.assertEqual(clicks, [1])
            feed(OPEN, 25)
            feed(CLICK, 25)
            self.assertEqual(clicks, [1, 1])
            feed(None, 1)
            self.assertFalse(self.window.pointer.isVisible())
            feed(OPEN, 5)
            feed(CLICK, 10)
            self.assertEqual(clicks, [1, 1])

    def test_pointer_stability_and_clear_on_page_change(self):
        page = self.window.pages["menu"]
        button = next(widget for widget in page.findChildren(QPushButton)
                      if widget.text() == "Auto-registro y triaje")
        center = button.mapTo(self.window.stack, button.rect().center())
        margin = self.window.camera.calibration
        point = (margin.left + (margin.right - margin.left) * center.x() / (self.window.stack.width() - 1),
                 margin.top + (margin.bottom - margin.top) * center.y() / (self.window.stack.height() - 1))
        self.window.handle_gesture("point", point)
        self.assertIs(self.window.target, button)
        self.window.handle_gesture("click", None)
        self.assertIs(self.window.stack.currentWidget(), page)
        self.window.handle_gesture("point", point)
        self.window.target_since = monotonic() - 0.25
        self.window.handle_gesture("click", None)
        self.assertIs(self.window.stack.currentWidget(), self.window.pages["dni"])
        self.assertIsNone(self.window.target)
        self.window.handle_gesture("back", None)
        self.assertIs(self.window.stack.currentWidget(), page)
