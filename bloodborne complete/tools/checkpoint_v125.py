"""Save the reviewed V12.5 source checkpoint before build and runtime tests."""
from pathlib import Path
import json,subprocess
ROOT=Path(__file__).resolve().parents[1]
git='C:/Program Files/Git/cmd/git.exe'
def call(*args):return subprocess.check_output([git,*args],cwd=ROOT,text=True,encoding='utf8').strip()
assert call('rev-parse','HEAD')=='653ae307ba4c44392ee08969f3890321e95659bb'
assert call('branch','--show-current')=='codex/bb-v12-tool'
assert not call('diff','--check')
targets=['src','gradle.properties','docs/v12.5-city-review','docs/v12.5-collisions']
targets.extend(str(p.relative_to(ROOT)).replace('\\','/') for p in (ROOT/'tools').glob('*v125*.py'))
targets.append('tools/gallery-v125')
subprocess.run([git,'add','--',*targets],cwd=ROOT,check=True)
subprocess.run([git,'commit','-m','V12.5: retire vanilla imports, shrink new collisions, prepare compact source review gallery'],cwd=ROOT,check=True)
result={'status':'SOURCE_CHECKPOINT_SAVED_BEFORE_BUILD','commit':call('rev-parse','HEAD'),'branch':call('branch','--show-current'),'baselineCommit':'653ae307ba4c44392ee08969f3890321e95659bb'}
path=ROOT/'docs/v12.5-city-review/source-checkpoint.json';path.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps(result))
