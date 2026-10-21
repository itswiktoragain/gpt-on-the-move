#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build
log="build/qemu-serial.log"
rm -f "$log"
set +e
timeout 35s qemu-system-x86_64 \
  -machine pc -cpu qemu64 -m 512 -smp 2 \
  -boot order=d -cdrom build/w2k64-amd64.iso \
  -serial "file:$PWD/$log" \
  -display none -monitor none -no-reboot -no-shutdown -net none
code=$?
set -e
if [[ $code != 0 && $code != 124 ]]; then
  echo "QEMU exited with code $code"
  [[ -f "$log" ]] && cat "$log"
  exit 1
fi
echo "---- QEMU serial log ----"
[[ -f "$log" ]] && cat "$log"
grep -Fq 'W2K64: LONG_MODE=1' "$log"
grep -Fq 'DOS16: Hello from 16-bit DOS!' "$log"
grep -Fq 'W2K64: INPUT_LOOP=1' "$log"
echo "PASS: native long-mode boot, BIOS/QEMU input loop, 16-bit DOS interpreter"
