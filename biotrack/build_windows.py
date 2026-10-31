"""Construcción reproducible de los dos ejecutables para Windows x64."""
from pathlib import Path
import argparse
import json
import os
import shutil
import subprocess
import sys
import hashlib
import zipfile

ROOT=Path(__file__).resolve().parent

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--solo', choices=('escritorio','portal'))
    parser.add_argument('--dependencias-preparadas', action='store_true', help='Reutilizar node_modules ya instalado con package-lock.json.')
    parser.add_argument('--empaquetar', action='store_true', help='Reunir los EXE ya compilados y generar el ZIP, sin recompilar.')
    args=parser.parse_args()
    if os.name!='nt' or sys.version_info[:2]!=(3,11):
        raise SystemExit('Utilice Python 3.11 de 64 bits en Windows.')
    environment=os.environ.copy()
    temporary=Path(environment['USERPROFILE'])/'AppData/Local/Temp/bt-build'
    temporary.mkdir(exist_ok=True)
    cache=ROOT/'.build'
    cache.mkdir(exist_ok=True)
    environment.update(TEMP=str(temporary),TMP=str(temporary),PYINSTALLER_CONFIG_DIR=str(cache/'pyinstaller-cache'),npm_config_cache=str(cache/'npm-cache'),electron_config_cache=str(cache/'electron-cache'),ELECTRON_CACHE=str(cache/'electron-cache'),ELECTRON_BUILDER_CACHE=str(cache/'electron-builder-cache'))
    redist_root=Path(r'C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Redist\MSVC')
    candidates=sorted(redist_root.glob('*/x64/Microsoft.VC*.CRT'))
    if candidates and not environment.get('BIOTRACK_VC_REDIST'):
        environment['BIOTRACK_VC_REDIST']=str(candidates[-1])
    if not args.empaquetar and args.solo!='portal' and not Path(environment.get('BIOTRACK_VC_REDIST','')).joinpath('msvcp140.dll').is_file():
        raise RuntimeError('Indique BIOTRACK_VC_REDIST con la carpeta de DLL de Visual C++ x64 para incluirlas en el EXE.')
    def run(command,cwd=ROOT):
        subprocess.run(command,cwd=cwd,env=environment,check=True)
    if not args.empaquetar and args.solo!='portal':
        run([sys.executable,'-m','PyInstaller','--noconfirm','--workpath',str(temporary/'biotrack-pyi'),'--distpath',str(ROOT/'dist/windows'),'windows/BIOTRACK.spec'])
    if not args.empaquetar and args.solo!='escritorio':
        npm=shutil.which('npm.cmd')
        if not npm:
            raise RuntimeError('Node.js y npm se requieren únicamente en el equipo de construcción.')
        if not args.dependencias_preparadas:
            run([npm,'ci','--no-audit','--no-fund'],ROOT/'portal_desktop')
        run([shutil.which('node.exe'),'node_modules/electron/install.js'],ROOT/'portal_desktop')
        run([npm,'run','build'],ROOT/'portal_desktop')
        run([sys.executable,'-m','PyInstaller','--noconfirm','--workpath',str(temporary/'biotrack-pyi'),'--distpath',str(ROOT/'dist/windows-portal'),'windows/Portal.spec'])
    desktop=ROOT/'dist/windows/BIOTRACK_Escritorio.exe'
    portal=ROOT/'dist/windows-portal/BIOTRACK_Portal.exe'
    if not desktop.is_file() or not portal.is_file():
        return
    destination=ROOT/'dist/BIOTRACK_Windows_x64'
    destination.mkdir(exist_ok=True)
    for file in (desktop,portal):
        shutil.copy2(file,destination/file.name)
    shutil.copy2(ROOT/'README.md',destination/'README.md')
    hashes={file.name:hashlib.sha256(file.read_bytes()).hexdigest() for file in (destination/desktop.name,destination/portal.name)}
    (destination/'SHA256.json').write_text(json.dumps(hashes,indent=2),encoding='utf-8')
    with zipfile.ZipFile(ROOT/'dist/BIOTRACK_Windows_x64.zip','w',zipfile.ZIP_DEFLATED) as archive:
        for file in destination.iterdir():
            archive.write(file,'BIOTRACK/'+file.name)
    print('Entrega lista:',destination)

if __name__=='__main__':
    main()
