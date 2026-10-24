#!/bin/sh
set -eu
cd "$(dirname "$0")"
BASE="../AlphaModern-776-v0.8.0.jar"
OUT="../AlphaModern-776-v0.8.1.jar"
mkdir -p build/classes
javac -source 8 -target 8 -cp "$BASE" -d build/classes AlphaModernShim.java WorldLightPatch.java WorldLightGuard.java BridgeSocket.java
cp "$BASE" "$OUT"
jar uf "$OUT" -C build/classes alphamodern
cat > build/manifest.mf <<'MANIFEST'
Premain-Class: alphamodern.AlphaModernShim
Main-Class: alphamodern.AlphaModernShim
Implementation-Version: 0.8.1-light-stack-guard

MANIFEST
jar ufm "$OUT" build/manifest.mf
unzip -t "$OUT" >/dev/null
unzip -p "$OUT" META-INF/MANIFEST.MF | grep -q 'Premain-Class: alphamodern.AlphaModernShim'
mkdir -p build/source
unzip -qo ../AlphaModern-v0.8.0-source.zip -d build/source
cp AlphaModernShim.java WorldLightPatch.java WorldLightGuard.java BridgeSocket.java build.sh build/source/
(cd build/source && zip -qr ../../AlphaModern-v0.8.1-source.zip .)
echo "Verified AlphaModern-776-v0.8.1.jar and source ZIP"
