# -*- mode: python ; coding: utf-8 -*-
from pathlib import Path
import os
from PyInstaller.utils.hooks import collect_data_files, copy_metadata

root = Path(SPECPATH).parent
datas = [(str(root / 'biotrack' / 'static'), 'biotrack/static')]
for package in ('face_recognition_models', 'ttkbootstrap'):
    datas += collect_data_files(package)
for distribution in ('Flask','ttkbootstrap','Pillow','numpy','opencv-python','dlib','face-recognition','face-recognition-models','cryptography','setuptools'):
    datas += copy_metadata(distribution)
binaries = []
redist = Path(os.environ.get('BIOTRACK_VC_REDIST', ''))
if redist.is_dir():
    binaries += [(str(file), '.') for file in redist.glob('*.dll')]

a = Analysis(
    [str(root / 'desktop_exe.py')],
    pathex=[str(root)], binaries=binaries, datas=datas,
    hiddenimports=['cv2','dlib','_dlib_pybind11','face_recognition','face_recognition_models','PIL.ImageTk','ttkbootstrap','flask','cryptography.hazmat.primitives.ciphers.aead'],
    excludes=['playwright','PyQt5','PyQt6','PySide2','PySide6','matplotlib','IPython','pytest','numpy.tests','numpy.testing'],
    noarchive=False,
)
pyz = PYZ(a.pure)
exe = EXE(pyz,a.scripts,a.binaries,a.datas,[],name='BIOTRACK_Escritorio',debug=False,strip=False,upx=False,console=False,disable_windowed_traceback=False)
