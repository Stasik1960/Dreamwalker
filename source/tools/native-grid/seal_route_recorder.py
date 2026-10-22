"""Freeze the same recorder for the still-unattempted matched pair."""
import json,zipfile
from run_profiles import P,ROOT,sha,write

for variant in ('baseline','candidate'):
    assert not (ROOT/'research/pilot'/('performance-'+variant)).exists()
proof=ROOT/'research/profiles/measurement-vm-lifecycle/source-transition.json'
transition=json.loads(proof.read_text(encoding='utf-8'))
source=P/'src/routeqa/java/dev/dreamwalker/bloodbornedw/routeqa/RouteServer.java'
helper=P/'build/libs/dreamwalker-native-route-qa-0.1.0-v12.7.4-native-grid.1.jar'
assert sha(source)==transition['afterSourceSha256']
assert sha(helper)=='24ea32ff3b9d2b83011fe207ce58eb4a86c47df63ecf9327c756f7f9fcf8e676'
assert sha(ROOT/'research/profiles/profile-lock.json')==transition['profileLockSha256']
with zipfile.ZipFile(helper) as jar:identity=json.loads(jar.read('fabric.mod.json'))
receipt={'status':'FROZEN_BEFORE_FIRST_PAIR','helper':{'path':str(helper),'sha256':sha(helper),'id':identity['id'],'version':identity['version']},'profileLockSha256':transition['profileLockSha256'],'sameHelperForBaselineAndCandidate':True,'measurementPhasesUnchanged':True,'sourceTransition':{'path':str(proof),'sha256':sha(proof)},'causeEvidence':{'path':transition['actualCauseEvidence'],'sha256':sha(P.parent/'research/function-qa/candidate/post-save-threads.txt')},'sourceFiles':{str(p.relative_to(P)):sha(p) for p in sorted((P/'src/routeqa').rglob('*')) if p.is_file()}}
target=ROOT/'research/profiles/benchmark-helper.json'
if target.exists():assert json.loads(target.read_text(encoding='utf-8'))==receipt
else:write(target,receipt)
