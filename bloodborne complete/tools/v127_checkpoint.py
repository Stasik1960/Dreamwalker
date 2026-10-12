"""Commit current scoped source work; binary/output artifacts and old untracked histories are excluded."""
from pathlib import Path
import subprocess,json
ROOT=Path(__file__).resolve().parents[1];REPO=ROOT.parent
raw=subprocess.check_output(['git','ls-files','-m','-o','--exclude-standard','-z'],cwd=REPO).decode().split('\0')
selected=[];excluded_derived=[]
EXCLUDED_DERIVED_DOCS={'docs/v12.7/conversion/source-lighting-coordinate-runs.jsonl.gz'}
MAX_DERIVED_DOC_BYTES=100_000_000
for path in raw:
 if not path.startswith('bloodborne complete/'):continue
 short=path[len('bloodborne complete/'):]
 file=REPO/path
 if short.startswith('docs/v12.7/') and file.is_file() and (short in EXCLUDED_DERIVED_DOCS or file.stat().st_size>MAX_DERIVED_DOC_BYTES):
  excluded_derived.append({'path':short,'bytes':file.stat().st_size,'reason':'Derived binary/oversize documentation artifact: retain for release ZIP, not Git history'});continue
 if short.startswith(('src/','tools/')) or short in ('build.gradle','gradle.properties','settings.gradle','README.md','RELEASE-STATUS.md') or short.startswith('docs/v12.7/') and not any(x in short for x in ('/projections/','/exports/','/captures/')):
  if (REPO/path).is_file():selected.append(path)
if selected:
 # Pass pathspecs through stdin; a complete catalogue exceeds Windows argv limits.
 subprocess.run(['git','add','--pathspec-from-file=-','--pathspec-file-nul'],input=('\0'.join(sorted(set(selected)))+'\0').encode('utf-8'),cwd=REPO,check=True)
 subprocess.run(['git','commit','-m','Checkpoint V12.7 final functionality and conversion preparation'],cwd=REPO,check=True)
print(json.dumps({'status':'PASS','commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=REPO,text=True).strip(),'files':len(selected),'excludedDerivedOutputs':excluded_derived}))
