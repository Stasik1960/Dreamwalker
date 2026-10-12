"""Finish the exact native-rescaled panel exception in an already complete plan.

Equivalent to the source_allium branch of the full generator, useful when the
full interior-fit pass began before the explicit panel decision was recorded.
"""
import hashlib
import json
from collections import Counter
from pathlib import Path
from gallery_platform_plan import ModelGeometry
from v125_collision_geometry import distributed_primitive_boxes, oriented, solid_parts
from v125_collision_generate import bounds_union
from simplify_imported_collisions import rotate_clip_boxes
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/architecture/resources';OUT=ROOT/'docs/v12.5-collisions'
preview=ROOT/'build/v125-collision-preview.json';metadata=json.loads(preview.read_text());report=json.loads((OUT/'generation-report.json').read_text());assert len(metadata['objects'])==1045
registry='bloodborne_dw:source_allium';spec=json.loads((RES/'bloodborne_dw/composite/source_allium.json').read_text());models=ModelGeometry();assert len(spec['variants'])==len(metadata['objects'][registry])
indices={json.dumps(table,sort_keys=True,separators=(',',':')):index for index,table in enumerate(metadata['shapes'])}
for variant_index,variant in enumerate(spec['variants']):
 for pose_name in ('closed','open'):
  pose=variant['poses'].get(pose_name,variant['poses']['closed']);assert pose['collision']
  for profile in ('base','alt'):
   solids=solid_parts(pose,models,profile,1.);assert solids;rows=[]
   for yaw in range(8):
    boxes=distributed_primitive_boxes(oriented(solids,yaw),3);assert boxes
    aim=rotate_clip_boxes([bounds_union(pose['selection'])],yaw,crop=False) if pose.get('selection') else []
    rows.append({'collision':boxes,'aim':aim})
    case=next(case for case in report['cases'] if case['registryId']==registry and case['variant']==variant_index and case['pose']==pose_name and case['profile']==profile and case['rotation']==yaw)
    case.update(policy='NATIVE_RESCALE_PANEL_SOURCE_SUPPORTS',boxes=len(boxes))
   key=json.dumps(rows,sort_keys=True,separators=(',',':'))
   if key not in indices:indices[key]=len(metadata['shapes']);metadata['shapes'].append(rows)
   metadata['objects'][registry][variant_index][pose_name+profile.title()]=indices[key]
report['priorDomainExceptions']=['source_allium: native angle0 rescale actual panel supports, old carrier AABB inaccurate']
metadata['priorDomainExceptions']=report['priorDomainExceptions']
for case in report['cases']:
 if 'coveredSafeFraction' in case:case['analysisMaskCoveredSafeFractionBeforeInteriorRefinement']=case.pop('coveredSafeFraction')
raw=(json.dumps(metadata,ensure_ascii=False,separators=(',',':'))+'\n').encode('utf8');preview.write_bytes(raw)
report.update(metadataSha256=hashlib.sha256(raw).hexdigest(),serializedBytes=len(raw),uniqueTables=len(metadata['shapes']),maximumBoxes=max(case['boxes'] for case in report['cases']),policies=dict(Counter(case['policy'] for case in report['cases'])),boxOccurrencesStored=sum(len(row['collision'])+len(row['aim']) for table in metadata['shapes'] for row in table))
(OUT/'generation-report.json').write_bytes((json.dumps(report,ensure_ascii=False,indent=2)+'\n').encode('utf8'))
print(json.dumps({key:value for key,value in report.items() if key!='cases'}))
