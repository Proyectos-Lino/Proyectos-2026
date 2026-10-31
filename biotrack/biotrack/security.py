"""Contraseñas PBKDF2 y sesiones revocables en memoria."""
from __future__ import annotations

import hashlib
import hmac
import secrets
import threading
import time

ITERATIONS = 260_000


def hash_password(password: str) -> str:
    salt = secrets.token_hex(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt), ITERATIONS).hex()
    return f"pbkdf2_sha256${ITERATIONS}${salt}${digest}"


def verify_password(password: str, encoded: str) -> bool:
    try:
        algorithm, count, salt, expected = encoded.split("$")
        if algorithm != "pbkdf2_sha256" or not 100_000 <= int(count) <= 2_000_000:
            return False
        digest = hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt), int(count)).hex()
        return hmac.compare_digest(digest, expected)
    except (ValueError, TypeError, AttributeError):
        return False


class Sessions:
    def __init__(self, ttl=8 * 3600, clock=time.monotonic):
        self.ttl, self.clock = ttl, clock
        self._sessions, self._attempts = {}, {}
        self._lock = threading.RLock()

    def rate_limited(self, key):
        with self._lock:
            now = self.clock()
            self._attempts = {k: [v for v in values if now - v < 300] for k, values in self._attempts.items() if values and now - values[-1] < 300}
            return len(self._attempts.get(key, [])) >= 5

    def failure(self, key):
        with self._lock:
            self._attempts.setdefault(key, []).append(self.clock())

    def create(self, employee_id, key="", auth_version=0):
        with self._lock:
            now = self.clock()
            self._sessions = {k: v for k, v in self._sessions.items() if v["expires"] > now}
            token, csrf = secrets.token_urlsafe(32), secrets.token_urlsafe(24)
            self._sessions[token] = {"employee_id": employee_id, "csrf": csrf, "expires": now + self.ttl, "auth_version": auth_version}
            self._attempts.pop(key, None)
            return token, csrf

    def get(self, token):
        with self._lock:
            session = self._sessions.get(token)
            if not session or session["expires"] <= self.clock():
                self._sessions.pop(token, None)
                return None
            return dict(session)

    def revoke(self, token):
        with self._lock:
            self._sessions.pop(token, None)

    def revoke_employee(self, employee_id):
        with self._lock:
            self._sessions = {key: value for key, value in self._sessions.items() if value["employee_id"] != employee_id}
