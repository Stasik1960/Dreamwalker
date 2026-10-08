"""Read-only v7 baseline and accepted artwork fingerprints before v8 edits."""
from pathlib import Path
import hashlib, json, zipfile
ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-prototype.2.jar'
OUT = ROOT/'reports/user-review-v7/baseline'
OUT.mkdir(parents=True, exist_ok=True)
report = {'schema':'v7-geometry-baseline-1', 'jar_sha256':hashlib.sha256(JAR.read_bytes()).hexdigest(), 'objects':[], 'accepted_art_sha256':{}}
with zipfile.ZipFile(JAR) as archive:
    for path in sorted(archive.namelist()):
        if path.startswith('bloodborne_dw/composite/') and path.endswith('.json'):
            data=archive.read(path); (OUT/Path(path).name).write_bytes(data)
            obj=json.loads(data)
            report['objects'].append({'id':obj['id'], 'registered_states':512,
                'variants':[{'variant':i, 'poses':{k:{'authored_physical_boxes':len(p['collision']), 'authored_selection_boxes':len(p['selection'])} for k,p in v['poses'].items()}} for i,v in enumerate(obj['variants'])]})
        if (path.startswith('assets/bloodborne_dw/models/base/prototype_windows/wood/') or path.startswith('assets/bloodborne_rp/') or path.startswith('assets/bloodborne_dw/models/base/source/minecraft/block/wood_window')):
            report['accepted_art_sha256'][path]=hashlib.sha256(archive.read(path)).hexdigest()
    report['wall_registered_states']=32768
    report['ladder_registered_states']=192
(OUT/'metrics.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
source=ROOT/'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeSpec.java'
target=ROOT/'src/gametest/java/dev/dreamwalker/bloodbornedw/composite/ReviewLegacyCompositeSpec.java'
target.parent.mkdir(parents=True,exist_ok=True)
target.write_text(source.read_text(encoding='utf-8').replace('CompositeSpec','ReviewLegacyCompositeSpec').replace('/bloodborne_dw/composite/', '/bloodborne_dw/review-v7-baseline/'),encoding='utf-8')
for path in OUT.glob('prototype_*.json'):
    dst=ROOT/'src/gametest/resources/bloodborne_dw/review-v7-baseline'/path.name
    dst.parent.mkdir(parents=True,exist_ok=True);dst.write_bytes(path.read_bytes())
print('v7 geometry and accepted artwork baseline preserved from historical JAR.')
