"""QA/data corrections only; compiled production bytes remain immutable."""
import hashlib,json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
jar=ROOT/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.5-city-review.jar'
assert hashlib.sha256(jar.read_bytes()).hexdigest()=='76690ecea281ecd5ac59d9b00493546af1978b0819bb2923da977b176f16b845'
git='C:/Program Files/Git/cmd/git.exe'
assert not subprocess.check_output([git,'diff','--check'],cwd=ROOT)
subprocess.run([git,'add','--','src/gametest','docs/v12.5-city-review','docs/v12.5-collisions',*[str(p.relative_to(ROOT)).replace('\\','/') for p in (ROOT/'tools').glob('*v125*.py')],'tools/gallery-v125'],cwd=ROOT,check=True)
subprocess.run([git,'commit','-m','V12.5 QA: preserve retired V12 states and seat authored floor surfaces'],cwd=ROOT,check=True)
print(subprocess.check_output([git,'rev-parse','HEAD'],cwd=ROOT,text=True).strip())
