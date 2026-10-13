"""Apply only an audited immutable default-physics resource, never model/source data."""
import json
import hashlib
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/architecture/resources'
preview=ROOT/'build/v125-collision-preview.json'
raw=preview.read_bytes()
document=json.loads(raw)
report=json.loads((ROOT/'docs/v12.5-collisions/generation-report.json').read_text(encoding='utf8'))
proof=json.loads((ROOT/'docs/v12.5-collisions/offline-proof.json').read_text(encoding='utf8'))
catalogue=json.loads((RES/'bloodborne_dw/remaining-catalogue.json').read_text(encoding='utf8'))
registered={'bloodborne_dw:'+row['path'] for row in catalogue['objects']}
assert document['objects'].keys()==registered and len(registered)==1045
assert report['registeredObjects']==1045 and report['metadataSha256']==hashlib.sha256(raw).hexdigest()
assert proof['status']=='PASS_OFFLINE_RESOURCE_AND_GEOMETRY' and proof['metadataSha256']==hashlib.sha256(raw).hexdigest()
assert proof['selectorsChecked']==report['selectorPoseProfileRotations'] and not proof['failures'] and not proof['emptyFormerlySolidOrientations']
assert report['maximumBoxes']<=12 and document['authorCollisionExport']=='NOT_SUPPLIED_NOT_APPLIED'
assert not report['runtimeRaster'] and not report['outwardCellBoundarySnapping']
target=RES/'bloodborne_dw/imported-collision-v125.json'
target.write_bytes(raw)
print(json.dumps({'status':'APPLIED_IMMUTABLE_NEW_DEFAULT_RESOURCE','types':1045,
                  'uniqueEightOrientationTables':len(document['shapes']),'bytes':len(raw),
                  'sha256':hashlib.sha256(raw).hexdigest(),'originalV12Changed':False}))
