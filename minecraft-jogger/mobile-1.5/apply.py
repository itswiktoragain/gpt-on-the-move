#!/usr/bin/env python3
"""Apply the Jogger Mobile 1.5 update to the fully reconstructed 1.4 source."""
from pathlib import Path
import base64
import hashlib
import lzma
import subprocess
import sys

here = Path(__file__).resolve().parent
encoded = "".join((here / ("patch.%d.b64" % i)).read_text().strip() for i in range(5))
assert len(encoded) == 10316, "Jogger 1.5 source payload is incomplete"
patch = lzma.decompress(base64.b64decode(encoded, validate=True))
expected = "fec7311b6d966c11a95c971acee2b6bbff5f37f774626622675512e8e575eb21"
if hashlib.sha256(patch).hexdigest() != expected:
    raise RuntimeError("Jogger 1.5 source payload integrity check failed")
root = Path(sys.argv[1]).resolve()
subprocess.run(["git", "apply", "--check", "-"], input=patch, cwd=root, check=True)
subprocess.run(["git", "apply", "-"], input=patch, cwd=root, check=True)
(here / "mobile15.patch").write_bytes(patch)
print("Applied Minecraft Jogger 1.5: tap text fields, use items in air, fixed orientation.")
