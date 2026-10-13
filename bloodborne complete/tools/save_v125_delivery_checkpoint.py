"""Save QA, native evidence and delivery tooling; production already compiled."""
import hashlib,json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
jar=ROOT/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.5-city-review.jar'
assert hashlib.sha256(jar.read_bytes()).hexdigest()=='76690ecea281ecd5ac59d9b00493546af1978b0819bb2923da977b176f16b845'
git='C:/Program Files/Git/cmd/git.exe'
assert not subprocess.check_output([git,'diff','--check'],cwd=ROOT)
targets=['docs/v12.5-city-review','docs/v12.5-collisions',*[str(p.relative_to(ROOT)).replace('\\','/') for p in (ROOT/'tools').glob('*v125*.py')]]
subprocess.run([git,'add','--',*targets],cwd=ROOT,check=True)
subprocess.run([git,'commit','-m','V12.5: verify compact gallery, source assemblies, doors and converted city'],cwd=ROOT,check=True)
print(subprocess.check_output([git,'rev-parse','HEAD'],cwd=ROOT,text=True).strip())
