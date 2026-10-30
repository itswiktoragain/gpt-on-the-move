#!/usr/bin/env python3
"""Prepare verified upstream Android JRE 8 binpacks for a Jogger build.

The full architecture tarballs are used as the platform binpack. The shared
binpack is intentionally empty: this is supported by MultiRTUtils and preserves
all upstream files, licences and symlinks without repacking the runtime.
"""
from pathlib import Path
import hashlib
import os
import shutil
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
COMPONENT = ROOT / 'app_pojavlauncher/src/main/assets/components/jre'
SOURCES = {
    'arm64': ('https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download/download_jre8/jre8-android-arm64.tar.xz?asset=388613934', '9a59124d9791957d55c68be664ab76831f336cf2e1e1cd4414220c6fdbf0e06d'),
    'x86_64': ('https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download/download_jre8/jre8-android-x86_64.tar.xz', 'b1fbcef4965c17925894febe8216d089c6dd47b37950b5f945a89616443c1d0e'),
}

def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def main():
    COMPONENT.mkdir(parents=True, exist_ok=True)
    for arch, (url, expected) in SOURCES.items():
        target = COMPONENT / ('bin-' + arch + '.tar.xz')
        if not target.exists() or digest(target) != expected:
            temporary = target.with_suffix('.download')
            request = urllib.request.Request(url, headers={'User-Agent': 'MinecraftJogger-build/1.0'})
            try:
                with urllib.request.urlopen(request, timeout=180) as source, temporary.open('wb') as output:
                    shutil.copyfileobj(source, output)
                actual = digest(temporary)
                if actual != expected:
                    raise RuntimeError('JRE 8 ' + arch + ' checksum mismatch: ' + actual)
                temporary.replace(target)
            finally:
                temporary.unlink(missing_ok=True)
        print('Verified Android JRE 8 for ' + arch)
    with tarfile.open(COMPONENT / 'universal.tar.xz', 'w:xz'):
        pass
    h = hashlib.sha256()
    for arch in SOURCES:
        with (COMPONENT / ('bin-' + arch + '.tar.xz')).open('rb') as f:
            for chunk in iter(lambda: f.read(1024 * 1024), b''):
                h.update(chunk)
    (COMPONENT / 'version').write_text(h.hexdigest(), encoding='ascii')

if __name__ == '__main__':
    main()
