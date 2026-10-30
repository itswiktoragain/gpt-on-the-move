#!/usr/bin/env python3
import pathlib,urllib.request,subprocess,tarfile,shutil,hashlib,json
p=pathlib.Path(__file__).resolve().parent;d=p/'dependencies';d.mkdir(exist_ok=True)
f=d/'libffi'
if not (f/'.git').exists():subprocess.run(['git','clone','--depth=1','--branch','v3.4.4','https://github.com/libffi/libffi.git',str(f)],check=True)
a=d/'libffi-release.tar.gz'
if not a.exists():
 with urllib.request.urlopen('https://github.com/libffi/libffi/releases/download/v3.4.4/libffi-3.4.4.tar.gz',timeout=120) as r,a.open('wb') as o:shutil.copyfileobj(r,o)
with tarfile.open(a) as t:
 for m in t.getmembers():
  parts=pathlib.PurePosixPath(m.name).parts[1:]
  if not parts or '..' in parts:continue
  dest=f.joinpath(*parts)
  if m.isdir():dest.mkdir(parents=True,exist_ok=True)
  elif m.isfile():
   dest.parent.mkdir(parents=True,exist_ok=True)
   with t.extractfile(m) as i,dest.open('wb') as o:shutil.copyfileobj(i,o)
   dest.chmod(m.mode)
(p/'dependency-output').mkdir(exist_ok=True)
(p/'dependency-output/libffi-release.json').write_text(json.dumps({'url':'https://github.com/libffi/libffi/releases/download/v3.4.4/libffi-3.4.4.tar.gz','sha256':hashlib.sha256(a.read_bytes()).hexdigest()}))
