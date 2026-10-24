#!/usr/bin/env python3
"""Test on QEMU's VMware SVGA II adapter, not just default VGA.
Produces full framebuffer screenshot and checks whether the desktop is actually visible.
"""
from pathlib import Path
import os
import socket
import subprocess
import tempfile
import time

PROJECT = Path(__file__).resolve().parent.parent
ISO = PROJECT / "build/w2k64-amd64.iso"
OUT = PROJECT / "build/vmware-probe"
OUT.mkdir(parents=True, exist_ok=True)

def run(firmware):
    sock = OUT / f"{firmware}.sock"
    screen = OUT / f"{firmware}.ppm"
    log = OUT / f"{firmware}.serial.log"
    for f in (sock, screen, log):
        if f.exists(): f.unlink()
    cmd = ["qemu-system-x86_64","-machine","pc","-cpu","qemu64","-m","512",
           "-vga","vmware","-boot","order=d","-cdrom",str(ISO),
           "-display","none","-net","none","-monitor",f"unix:{sock},server=on,wait=off",
           "-serial",f"file:{log}","-no-reboot","-no-shutdown"]
    if firmware == "uefi":
        choices = ["/usr/share/OVMF/OVMF_CODE_4M.fd","/usr/share/OVMF/OVMF_CODE.fd"]
        firmware_file = next((x for x in choices if os.path.isfile(x)),None)
        if not firmware_file: raise RuntimeError("ovmf is not installed")
        cmd += ["-bios", firmware_file]
    p = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    try:
        for _ in range(100):
            if log.exists() and "W2K64: INPUT_LOOP=1" in log.read_text(errors="replace"):
                break
            if p.poll() is not None:break
            time.sleep(0.15)
        log_text = log.read_text(errors="replace") if log.exists() else ""
        print(f"[{firmware}] Serial last lines:\n"+"\n".join(log_text.splitlines()[-12:]),flush=True)
        if not sock.exists(): raise RuntimeError(f"{firmware}: QEMU monitor socket missing")
        with socket.socket(socket.AF_UNIX,socket.SOCK_STREAM) as s:
            s.settimeout(4)
            s.connect(str(sock))
            try:s.recv(4096)
            except socket.timeout:pass
            s.sendall(f"screendump {screen}\n".encode())
            time.sleep(1.0)
            try: response=s.recv(4096).decode(errors="replace")
            except socket.timeout: response=""
            print(f"[{firmware}] Monitor: {response.strip()}",flush=True)
        if not screen.exists():raise RuntimeError(f"{firmware}: no screenshot produced")
        with screen.open("rb") as f:
            assert f.readline().strip() == b"P6", "Expected binary PPM"
            dims = f.readline()
            while dims.startswith(b"#"):dims=f.readline()
            width,height=map(int,dims.split())
            maxv=int(f.readline())
            if maxv!=255:raise RuntimeError("Unexpected PPM maxval")
            pixels=f.read()
        colored=0
        for i in range(0,len(pixels),3*3):
            r,g,b=pixels[i:i+3]
            if 0 <= r <= 30 and 75 <= g <= 165 and 75 <= b <= 165:
                colored += 1
        ratio = colored / max(1,len(pixels)//9)
        print(f"[{firmware}] visible desktop pixel ratio={ratio:.3f} screen={width}x{height}",flush=True)
        if "W2K64: LONG_MODE=1" not in log_text: raise RuntimeError(f"{firmware}: kernel never booted AMD64")
        if ratio<0.05:raise RuntimeError(f"{firmware}: black/missing graphics (teal pixels ratio too low)")
    finally:
        p.terminate()
        try:p.wait(timeout=2)
        except subprocess.TimeoutExpired:p.kill()

for fw in ("bios","uefi"):run(fw)
print("PASS: VMware-style SVGA desktop visible under legacy BIOS and UEFI")
