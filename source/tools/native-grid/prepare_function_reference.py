"""Same intrinsic block witnesses, explicitly outside target compatibility/perf."""
import json
from run_profiles import ROOT,sha,write
folder=ROOT/'research/function-qa'
prior=folder/'baseline-bootstrap-plan.json'
failed=folder/'baseline-bootstrap'
assert json.loads((failed/'launch-receipt.json').read_text(encoding='utf-8'))['status']=='FAIL'
assert 'OutOfMemoryError' in (failed/'server-console.log').read_text(encoding='utf-8',errors='replace')
plan=json.loads(prior.read_text(encoding='utf-8'))
plan['output']=str(folder/'baseline-reference')
plan['authoringReferenceOnly']=True
plan['referenceScope']='Intrinsic source block shapes and gameplay only; same source JAR/world/witnesses and4G. WorldEdit and Lithium omitted only here. Full target baseline remains FAIL/OOM. No benchmark or compatibility PASS.'
plan['failedTargetPlanSha256']=sha(prior)
path=folder/'baseline-reference-plan.json'
assert not path.exists() and not (folder/'baseline-reference').exists(),'One bounded intrinsic-reference attempt only'
write(path,plan)
print(str(path))
