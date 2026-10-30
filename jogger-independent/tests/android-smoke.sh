#!/usr/bin/env bash
set -euo pipefail
mkdir -p out/emulator
APK=jogger-independent/app/build/outputs/apk/debug/app-debug.apk
adb install -r "$APK"
adb logcat -c
adb shell am start -W -n com.wiktor.jogger/.JoggerActivity
sleep 4
adb exec-out screencap -p > out/emulator/launcher.png
adb shell am force-stop com.wiktor.jogger
adb shell am start -W -n com.wiktor.jogger/.JoggerActivity --ez probe true
sleep 25
adb logcat -d > out/emulator/probe-logcat.txt
adb exec-out run-as com.wiktor.jogger cat files/jogger/engine-check/latest-jogger.log > out/emulator/probe-engine.txt || true
adb exec-out screencap -p > out/emulator/probe.png
cat out/emulator/probe-engine.txt
grep -q 'JOGGER_PROBE_PIXEL_OK' out/emulator/probe-engine.txt
grep -q 'JOGGER_PROBE_RENDER_OK' out/emulator/probe-engine.txt
printf '%s\n' 'Java/OpenGL rendering and pixel-readback probe passed.' > out/emulator/RESULT.txt
adb shell am force-stop com.wiktor.jogger
adb shell am start -W -n com.wiktor.jogger/.JoggerActivity --ez smokeDemo true
for i in $(seq 1 48); do
  sleep 5
  LOGPATH=$(adb shell run-as com.wiktor.jogger find files/jogger/instances -name latest-jogger.log | tr -d '\r' | head -n 1)
  if [ -n "$LOGPATH" ]; then
    adb exec-out run-as com.wiktor.jogger cat "$LOGPATH" > out/emulator/demo-engine.txt || true
    if grep -q 'Game crashed!\|Minecraft Crash Report\|A fatal error' out/emulator/demo-engine.txt; then break; fi
    if grep -q 'Backend library: LWJGL' out/emulator/demo-engine.txt; then sleep 45; break; fi
  fi
done
adb logcat -d > out/emulator/demo-logcat.txt
if [ -n "${LOGPATH:-}" ]; then adb exec-out run-as com.wiktor.jogger cat "$LOGPATH" > out/emulator/demo-engine.txt || true; fi
adb exec-out screencap -p > out/emulator/demo.png
if [ -f out/emulator/demo-engine.txt ]; then tail -n 160 out/emulator/demo-engine.txt; fi
printf '%s\n' 'Official demo startup attempted; inspect screenshot/log separately from the engine probe.' >> out/emulator/RESULT.txt
if [ -f out/emulator/demo-engine.txt ] && grep -q 'Game crashed!\|Minecraft Crash Report\|A fatal error' out/emulator/demo-engine.txt; then
  printf '%s\n' 'FAILED: Minecraft startup crashed; see demo-engine.txt.' >> out/emulator/RESULT.txt
  exit 1
fi
