"""Persistencia JSON atómica con bloqueo entre procesos."""
from __future__ import annotations

import copy
import json
import os
import tempfile
import threading
import time
from contextlib import contextmanager
from pathlib import Path


class StorageError(RuntimeError):
    pass


class JsonStore:
    def __init__(self, path: str | Path, initializer):
        self.path = Path(path).resolve()
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.lock_path = self.path.with_suffix(".lock")
        self._lock = threading.RLock()
        with self._locked():
            if not self.path.exists():
                self._save(initializer())
            self._load()

    @contextmanager
    def _locked(self):
        with self._lock:
            with self.lock_path.open("a+b") as handle:
                handle.seek(0, 2)
                if handle.tell() == 0:
                    handle.write(b"0")
                    handle.flush()
                deadline = time.monotonic() + 10
                while True:
                    try:
                        handle.seek(0)
                        if os.name == "nt":
                            import msvcrt
                            msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
                        else:
                            import fcntl
                            fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
                        break
                    except (OSError, BlockingIOError) as exc:
                        if time.monotonic() >= deadline:
                            raise StorageError("El archivo está ocupado. Intente nuevamente.") from exc
                        time.sleep(0.03)
                try:
                    yield
                finally:
                    handle.seek(0)
                    if os.name == "nt":
                        import msvcrt
                        msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
                    else:
                        import fcntl
                        fcntl.flock(handle.fileno(), fcntl.LOCK_UN)

    def _load(self):
        try:
            state = json.loads(self.path.read_text(encoding="utf-8"))
            required = {"schema", "personal", "asistencias", "horarios", "incidencias", "configuracion", "auditoria", "rostros"}
            if not isinstance(state, dict) or not required.issubset(state) or state["schema"] != 1:
                raise ValueError("Formato no reconocido")
            return state
        except (ValueError, OSError) as exc:
            raise StorageError("No se puede leer la base de datos. Se conservó el archivo; revise su copia de seguridad.") from exc

    def _save(self, state):
        temporary = None
        try:
            payload = json.dumps(state, ensure_ascii=False, indent=2, allow_nan=False)
            with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=self.path.parent, prefix="biotrack-", suffix=".tmp", delete=False) as handle:
                temporary = Path(handle.name)
                handle.write(payload)
                handle.flush()
                os.fsync(handle.fileno())
            if self.path.exists():
                previous = self.path.with_suffix(".previous.json")
                previous.write_bytes(self.path.read_bytes())
            os.replace(temporary, self.path)
        except (ValueError, OSError) as exc:
            raise StorageError("No fue posible guardar los cambios.") from exc
        finally:
            if temporary and temporary.exists():
                temporary.unlink()

    def read(self):
        with self._locked():
            return copy.deepcopy(self._load())

    def mutate(self, operation):
        with self._locked():
            state = self._load()
            result = operation(state)
            self._save(state)
            return copy.deepcopy(result)
