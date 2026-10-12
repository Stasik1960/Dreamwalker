"""Commit current scoped source work; binary/output artifacts and old untracked histories are excluded."""
from pathlib import Path
import subprocess,json
ROOT=Path(__file__).resolve().parents[1];REPO=ROOT.parent
raw=subprocess.check_output(['git','ls-files','-m','-o','--exclude-standard','-z'],cwd=REPO).decode().split('\0')
selected=[]
for path in raw:
 if not path.startswith('bloodborne complete/'):continue
 short=path[len('bloodborne complete/'):]
 if short.startswith(('src/','tools/')) or short in ('build.gradle','gradle.properties','settings.gradle','README.md','RELEASE-STATUS.md') or short.startswith('docs/v12.7/') and not any(x in short for x in ('/projections/','/exports/','/captures/')):
  if (REPO/path).is_file():selected.append(path)
if selected:
 # Pass pathspecs through stdin; a complete catalogue exceeds Windows argv limits.
 subprocess.run(['git','add','--pathspec-from-file=-','--pathspec-file-nul'],input=('\0'.join(sorted(set(selected)))+'\0').encode('utf-8'),cwd=REPO,check=True)
 subprocess.run(['git','commit','-m','Checkpoint V12.7 final functionality and conversion preparation'],cwd=REPO,check=True)
print(json.dumps({'status':'PASS','commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=REPO,text=True).strip(),'files':len(selected)}))
