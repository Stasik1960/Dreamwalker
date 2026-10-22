"""Preserve the current compatible production JAR before later full-city builds."""
from pathlib import Path
import hashlib,json,shutil
P=Path(__file__).resolve().parents[2];R=P.parent/'research'
checkpoint=R/'checkpoints/20261022T062013Z'
assert checkpoint.is_dir() and not (checkpoint/'pair.json').exists()
expected='3477fa1f016126eb58ea294a37a0ba06ad711f7e137ae72b103919df45e35bd3'
jar=P/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-native-grid.1.jar'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(jar)==expected
out=checkpoint/'game';out.mkdir()
shutil.copy2(jar,out/jar.name);assert sha(out/jar.name)==expected
mapping=R/'type-mapping.jsonl';shutil.copy2(mapping,checkpoint/mapping.name)
doc={'status':'SAVED_COMPATIBLE_PILOT_PAIR_NOT_FINAL_READY','production':{'path':str(out/jar.name),'sha256':expected},'world':str(R/'pilot/candidate'),'worldCopied':False,'worldMarkerSha256':sha(R/'pilot/candidate/NATIVE-GRID-MIGRATION.json'),'catalogSha256':'638d84a879a7823fd4ed866b2109fbe864cda4690383745fbc5a53bc940fa550','mappingSha256':sha(checkpoint/mapping.name),'pendingNativeLight':0,'ready':False,'remaining':'Whole assemblies reconciliation, affected bridge QA, matched pair, full city, full light, final route/save/reopen'}
(checkpoint/'pair.json').write_text(json.dumps(doc,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(doc,ensure_ascii=False))
