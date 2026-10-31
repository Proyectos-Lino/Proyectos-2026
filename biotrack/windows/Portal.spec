# -*- mode: python ; coding: utf-8 -*-
from pathlib import Path

root = Path(SPECPATH).parent
a = Analysis([str(root / 'portal_exe.py')], pathex=[str(root)],
    binaries=[], datas=[(str(root / 'dist/windows-portal/win-unpacked'), 'electron')],
    hiddenimports=[], excludes=['tkinter','numpy','cv2','dlib','flask','cryptography','playwright'], noarchive=False)
pyz = PYZ(a.pure)
exe = EXE(pyz,a.scripts,a.binaries,a.datas,[],name='BIOTRACK_Portal',debug=False,strip=False,upx=False,console=False)
