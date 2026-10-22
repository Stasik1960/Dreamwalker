"""Bind the single corrected-bootstrap functional attempt; keep the OOM intact."""
import json,shutil
from pathlib import Path
from run_profiles import ROOT,sha,write
folder=ROOT/'research/function-qa'
original=folder/'baseline-plan.json'
failed=folder/'baseline'
binding=json.loads((failed/'binding.json').read_text(encoding='utf-8'))
assert sha(original)==binding['planSha256'],'Original failed input changed'
assert json.loads((failed/'launch-receipt.json').read_text(encoding='utf-8'))['status']=='FAIL'
assert 'OutOfMemoryError' in (failed/'server-console.log').read_text(encoding='utf-8',errors='replace')
repair=ROOT/'research/pilot/bootstrap-repair/receipt.json'
receipt=json.loads(repair.read_text(encoding='utf-8'))
assert receipt['status']=='PASS' and receipt['kind']=='BOUNDED_PILOT_BOOTSTRAP_REPAIR'
assert receipt['terrainRegionWrites']==0 and receipt['immutableSourceUnchanged']
plan=json.loads(original.read_text(encoding='utf-8'))
assert receipt['worlds']['baseline']['target']==plan['targetWorldPath']
archive=failed/'input-plan.json'
if archive.exists():assert sha(archive)==sha(original)
else:shutil.copyfile(original,archive)
plan['output']=str(folder/'baseline-bootstrap')
assert not Path(plan['output']).exists(),'Only one corrected-bootstrap attempt is allowed'
plan['bootstrapCorrection']={'receipt':str(repair),'sha256':sha(repair),'previousFailedRun':str(failed),'heapUnchanged':'4G'}
retry=folder/'baseline-bootstrap-plan.json'
assert not retry.exists(),'Corrected input is already bound; inspect and run it'
write(retry,plan)
print(str(retry))
