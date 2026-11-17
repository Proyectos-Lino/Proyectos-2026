import json
import sys
from pathlib import Path

from dotenv import dotenv_values


def main():
    source, destination = (Path(arg) for arg in sys.argv[1:])
    values = dotenv_values(source, interpolate=False)
    keys = {name: (values.get(name) or "").strip()
            for name in ("API_TOKEN", "OPENROUTER_API_KEY", "OPENROUTER_MODEL")}
    if not keys["API_TOKEN"] or not keys["OPENROUTER_API_KEY"]:
        raise SystemExit("El archivo de configuración debe contener ambas claves.")
    keys["OPENROUTER_MODEL"] = keys["OPENROUTER_MODEL"] or "openrouter/free"
    destination.write_text(json.dumps(keys), encoding="utf-8")


if __name__ == "__main__":
    main()
