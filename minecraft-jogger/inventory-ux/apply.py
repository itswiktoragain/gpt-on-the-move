#!/usr/bin/env python3
"""Apply verified 1.4 update atop Mobile 1.3 (after keyboard-icons/apply.py)."""
from pathlib import Path
import hashlib
import subprocess
import sys
root=Path(sys.argv[1]).resolve()
patch=Path(__file__).with_name("mobile14.patch").read_bytes()
expected="7d22a8d4bcd1fd66db865e84334ac1d9f5eb413632e82cfbbcbbed4f08bf1702"
if hashlib.sha256(patch).hexdigest()!=expected:
    raise SystemExit("Jogger 1.4 patch integrity check failed")
subprocess.run(["git","apply","--check","-"],input=patch,cwd=root,check=True)
subprocess.run(["git","apply","-"],input=patch,cwd=root,check=True)
print("Applied 1.4: no cut-off dots, proportional 8-pixel glyphs, tapped fields only, inventory charge/creative trash.")
