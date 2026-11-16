"""Run on each target operating system; produces MSI, DMG, or DEB."""
from pathlib import Path
import os
import platform
import shutil
import subprocess
import sys
import tempfile

ROOT=Path(__file__).resolve().parents[1]
os.chdir(ROOT)
RELEASE=ROOT/'release';RELEASE.mkdir(exist_ok=True)
VERSION='1.2.0'


def run(*args):subprocess.run([str(a) for a in args],check=True)

if sys.platform=='linux':
    # Distro-provided Python and Qt avoid bundling a second Linux browser engine.
    with tempfile.TemporaryDirectory() as directory:
        package=Path(directory)
        app=package/'usr/share/tmailer';app.mkdir(parents=True)
        for name in ('backend','desktop','ui'):
            shutil.copytree(ROOT/name,app/name,ignore=shutil.ignore_patterns('__pycache__','downloads'))
        shutil.copy2(ROOT/'README.md',app/'README.md')
        control=package/'DEBIAN';control.mkdir()
        (control/'control').write_text('Package: tmailer\nVersion: 1.2.0\nArchitecture: all\nSection: mail\nPriority: optional\nDepends: python3 (>= 3.10), python3-pyqt6, python3-pyqt6.qtwebengine\nMaintainer: Tmailer Project <tmailer@example.invalid>\nDescription: Tmailer IMAP, POP3 and SMTP mail client\n Native desktop window and direct encrypted mail connections.\n')
        bin=package/'usr/bin';bin.mkdir(parents=True)
        launcher=bin/'tmailer';launcher.write_text('#!/bin/sh\nexec /usr/bin/python3 /usr/share/tmailer/desktop/linux_window.py "$@"\n');launcher.chmod(0o755)
        apps=package/'usr/share/applications';apps.mkdir(parents=True)
        (apps/'tmailer.desktop').write_text('[Desktop Entry]\nType=Application\nName=Tmailer\nComment=IMAP and POP3 email with SMTP sending\nExec=tmailer\nIcon=tmailer\nTerminal=false\nCategories=Network;Email;\nStartupNotify=true\n')
        icons=package/'usr/share/icons/hicolor/scalable/apps';icons.mkdir(parents=True)
        shutil.copy2(ROOT/'ui/logo.svg',icons/'tmailer.svg')
        for p in package.rglob('*'):
            p.chmod(0o755 if p.is_dir() or p==launcher else 0o644)
        run('dpkg-deb','--root-owner-group','--build',package,RELEASE/'Tmailer-1.2.0-linux.deb')
else:
    from PIL import Image
    icon=Image.open(ROOT/'packaging/logo.png').convert('RGBA')
    icon.save(ROOT/'packaging/logo.ico',sizes=[(16,16),(32,32),(48,48),(64,64),(128,128),(256,256)])
    icon.save(ROOT/'packaging/logo.icns')
    args=[sys.executable,'-m','PyInstaller','--noconfirm','--clean','--name','Tmailer','--windowed','--paths','backend','--hidden-import','server','--hidden-import','mailcore','--collect-all','webview','--distpath','build/native','--workpath','build/pyinstaller','--add-data',f'ui{os.pathsep}ui','--add-data',f'backend{os.pathsep}backend']
    if sys.platform=='win32':
        args+=['--onefile','--icon','packaging/logo.ico']
    else:
        args+=['--onedir','--icon','packaging/logo.icns','--osx-bundle-identifier','com.wiktorlaskowski.tmailer']
    run(*args,'desktop/launcher.py')
    if sys.platform=='win32':
        run('build/native/Tmailer.exe','--smoke-test')
        run('wix','build','packaging/windows.wxs','-arch','x64','-out','release/Tmailer-1.2.0-windows-x64.msi')
    else:
        run('build/native/Tmailer.app/Contents/MacOS/Tmailer','--smoke-test')
        image=ROOT/'build/dmg';image.mkdir(parents=True,exist_ok=True)
        shutil.copytree(ROOT/'build/native/Tmailer.app',image/'Tmailer.app',symlinks=True,dirs_exist_ok=True)
        (image/'Applications').symlink_to('/Applications')
        arch='arm64' if platform.machine()=='arm64' else 'x64'
        run('hdiutil','create','-volname','Tmailer','-srcfolder',image,'-ov','-format','UDZO',RELEASE/f'Tmailer-1.2.0-macos-{arch}.dmg')
        run('hdiutil','verify',RELEASE/f'Tmailer-1.2.0-macos-{arch}.dmg')
print('Built:',*[p.name for p in RELEASE.iterdir()])
