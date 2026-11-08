FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    DATA_DIR=/data \
    APP_ENV=production \
    SEED_DEMO=false

WORKDIR /app
COPY requirements.txt requirements-lock.txt ./
RUN pip install --no-cache-dir --disable-pip-version-check --only-binary=:all: \
        -c requirements-lock.txt -r requirements.txt \
    && groupadd --gid 10001 integral \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin integral \
    && install -d -o integral -g integral -m 0750 /data /data/uploads
COPY app/ ./app/

USER 10001:10001
EXPOSE 8000

HEALTHCHECK --interval=60s --timeout=5s --start-period=20s --retries=3 \
    CMD ["python", "-c", "from urllib.request import urlopen; urlopen('http://127.0.0.1:8000/health', timeout=3).close()"]

# Un proceso conserva en memoria las conexiones WebSocket de todos los perfiles.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", \
     "--workers", "1", "--ws", "websockets-sansio", "--ws-max-size", "65536", \
     "--ws-per-message-deflate", "false", \
     "--no-access-log", "--timeout-graceful-shutdown", "10"]
