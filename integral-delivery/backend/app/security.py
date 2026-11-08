import hashlib
import hmac
import secrets
from datetime import datetime, timezone

def now():
    return datetime.now(timezone.utc).isoformat()

def hash_password(password):
    salt = secrets.token_hex(16)
    digest = hashlib.pbkdf2_hmac('sha256',password.encode(),bytes.fromhex(salt),260000).hex()
    return f"pbkdf2_sha256$260000${salt}${digest}"

def verify_password(password, stored):
    try:
        _, rounds, salt, expected = stored.split('$')
        actual = hashlib.pbkdf2_hmac('sha256',password.encode(),bytes.fromhex(salt),int(rounds)).hex()
        return hmac.compare_digest(actual,expected)
    except (ValueError,TypeError):
        return False

def token_hash(token):
    return hashlib.sha256(token.encode()).hexdigest()
