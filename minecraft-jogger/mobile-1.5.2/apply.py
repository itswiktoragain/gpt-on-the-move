#!/usr/bin/env python3
"""Apply the tested Mobile 1.5.2 diff on top of the 1.5.1 launcher."""
from pathlib import Path
import hashlib
import subprocess
import sys
root=Path(sys.argv[1]).resolve()
patch=Path(__file__).with_name("mobile152.patch").read_bytes()
expected="1282861bf8bc840ac1a2d5b1f2951977981f7d7a6f6e8d95dd5e4d457051a9da"
if hashlib.sha256(patch).hexdigest()!=expected:
    raise RuntimeError("Jogger 1.5.2 source integrity failure")
subprocess.run(["git","apply","--check","-"],input=patch,cwd=root,check=True)
subprocess.run(["git","apply","-"],input=patch,cwd=root,check=True)
print("Applied 1.5.2: persistent chat keyboard, inventory click pair, diagonal controls, Bedrock HUD.")
