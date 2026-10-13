"""Normalize only touched/new source text before Git checkpoint, never frozen originals."""
from pathlib import Path
import subprocess,os
ROOT=Path(__file__).resolve().parents[1]
files=subprocess.check_output(['git','status','--porcelain','-z','--untracked-files=all','--','src'],cwd=ROOT).decode().split('\0')
for line in files:
    if not line:continue
    path=ROOT.parent/line[3:]
    if path.suffix not in ('.java','.json'):continue
    data=path.read_bytes();clean=data.replace(b'\r\n',b'\n')
    if clean!=data:
        temporary=path.with_suffix('.v124-lf.tmp');temporary.write_bytes(clean);os.replace(temporary,path)
