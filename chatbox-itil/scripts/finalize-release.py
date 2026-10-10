"""Reúne los PDF y empaqueta la carpeta portable ya verificada."""
from pathlib import Path
import hashlib
import json
import shutil
import zipfile

root = Path(__file__).resolve().parent.parent
out = root / 'deliverables'
package = out / 'Chatbox-ITIL-1.0.0-windows-x64'
assert json.loads((out / 'verificacion-exe.json').read_text(encoding='utf-8'))['result'] == 'PASS'
assert 'GEMINI_API_KEY=\n' in (package / '.env').read_text(encoding='utf-8')
docs = package / 'documentacion'
docs.mkdir(exist_ok=True)
for name in ['manual-usuario.pdf', 'memoria-descriptiva.pdf', 'anexos-informe.pdf', 'VERIFICACION.md', 'verificacion-exe.json']:
    shutil.copy2(out / name, docs / name)
(package / 'LEEME-ENTREGA.txt').write_text('''CHATBOX ITIL 1.0.0 — ENTREGA VERIFICADA

1. Extraer toda la carpeta. No ejecutar desde el ZIP.
2. Abrir chatbox-itil.exe y mantener su consola abierta.
3. Acceder a http://localhost:3000 si el navegador no se abre.
4. La IA requiere colocar su clave en GEMINI_API_KEY, dentro de .env.
   Este paquete no incorpora la clave privada del equipo de desarrollo.
5. Conservar public, node_modules y data junto al EXE.
6. Los PDF están en documentacion. Consultar VERIFICACION.md para el alcance.

Esta carpeta contiene una copia de los datos locales al preparar la entrega.
Antes de actualizar una instalación existente, respaldar data y .env y no
reemplazarlos por esta copia sin revisar su contenido.

No se requiere instalar Node.js. Plataforma de destino: Windows x64.
Detener el servidor antes de respaldar o restaurar los datos.
''', encoding='utf-8')
files = [out / 'manual-usuario.pdf', out / 'memoria-descriptiva.pdf', out / 'anexos-informe.pdf', package / 'chatbox-itil.exe']
checksums = {str(p.relative_to(out)): hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
(out / 'SHA256.json').write_text(json.dumps(checksums, indent=2), encoding='utf-8')
archive = out / 'Chatbox-ITIL-1.0.0-windows-x64.zip'
with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as z:
    for p in sorted(package.rglob('*')):
        if p.is_file():
            z.write(p, str(p.relative_to(out)))
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    names = z.namelist()
    assert any(n.endswith('/chatbox-itil.exe') for n in names)
    assert sum(n.endswith('.pdf') and '/documentacion/' in n for n in names) == 3
    assert not any(Path(n).name in ['empleado.html', 'empleado.css', 'widget.js', 'widget.css', 'surveys.json'] for n in names)
    assert 'GEMINI_API_KEY=\n' in z.read(package.name + '/.env').decode('utf-8')
print(json.dumps({'zip':str(archive),'sizeMB':round(archive.stat().st_size/1024/1024,1),'files':len(names),'integrity':'PASS'}, indent=2))
