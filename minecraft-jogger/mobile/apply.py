#!/usr/bin/env python3
"""Apply the reviewable mobile source diff after classic-integrate.py, NOT fov-hotfix.py."""
import base64,hashlib,lzma,pathlib,subprocess,sys
here=pathlib.Path(__file__).resolve().parent
encoded=''.join((here/('patch.%d.b64'%i)).read_text().strip() for i in range(3))
data=base64.b64decode(encoded,validate=True)
if hashlib.sha256(data).hexdigest()!='a65a1c8e2d3aea211c1af8ac3340f18e32d49eb088ee7093e1a6748e300a8cec':
 raise RuntimeError('Mobile source patch checksum mismatch')
patch=lzma.decompress(data)
(here/'mobile-update.patch').write_bytes(patch)
root=pathlib.Path(sys.argv[1]).resolve()
subprocess.run(['git','apply','--check','-'],input=patch,cwd=root,check=True)
subprocess.run(['git','apply','-'],input=patch,cwd=root,check=True)
print('Applied 1.2.0 mobile source update, including the frame-safe 1.1.1 fix.')
