#!/bin/sh
set -eu
case "${BACKEND_URL:-}" in
    *[!a-zA-Z0-9.:/-]*)
        echo >&2 'BACKEND_URL contiene caracteres no permitidos.'
        exit 1
        ;;
esac
if ! printf '%s' "${BACKEND_URL:-}" | grep -Eq '^https://[a-zA-Z0-9][a-zA-Z0-9.-]*(:[0-9]{1,5})?/?$'; then
    echo >&2 'Configura BACKEND_URL con el dominio HTTPS público de la API, sin ruta ni credenciales.'
    exit 1
fi
printf 'window.__ODONTOCARE_CONFIG__ = {"apiBaseUrl":"%s"};\n' "${BACKEND_URL%/}" \
    > /usr/share/nginx/html/runtime-config.js
