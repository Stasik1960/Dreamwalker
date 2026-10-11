"""Independent light source-art normalization proof; no Java, rendering or worlds."""
from __future__ import annotations
import argparse
import copy
import hashlib
import json
from pathlib import Path
import zipfile
from gallery_platform_plan import ModelGeometry, rotate
from inspect_imported_collision_semantics import element_boxes

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/architecture/resources'
DOC=ROOT/'docs/v12.4-dedup/stair-review'
def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def points(parts,models,alt):
    parts=copy.deepcopy(parts)
    if alt:
        for part in parts:part['model']=part.get('altModel',part['model'])
    return [point for box in element_boxes({'parts':parts},models,include_points=True) for point in box['points']]
def world(point,yaw,shift):
    moved=rotate(point,1,-yaw*45,[8,8,8]);offset=rotate([s*16 for s in shift],1,-yaw*45,[0,0,0])
    return [moved[i]+offset[i] for i in range(3)]
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--production-jar',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    table=read(RES/'bloodborne_dw/imported-stair-legacy-art.json');fragment=read(DOC/'functional-type-fragment.json');originals=read(DOC/'original-functional-descriptors.json')
    originals={row['id']:row for row in originals.values()}
    mappings={(m['oldRegistryId'],m['oldVariant']):m for m in fragment['mappings']}
    models=ModelGeometry(vanilla_jar=Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'))
    shift=[.3125,-.1875,.15625];checks=0;max_error=0;failures=[];refs=set();pose_count=0
    with zipfile.ZipFile(args.production_jar) as archive:
        for row in table['states']:
            mapping=mappings[(row['oldRegistryId'],row['oldVariant'])];old=originals[row['oldRegistryId']]['variants'][row['oldVariant']]
            if row['key']!=row['oldRegistryId']+'|'+str(row['oldVariant']) or row['registryId']!=mapping['newRegistryId'] or row['variant']!=mapping['newVariant']:failures.append('Unexpected registry/shape key '+row['key'])
            if any(mapping['sourceShift']):failures.append('Unexpected stair mapping shift '+row['key'])
            delta=mapping['rotationDelta'];new_shift=rotate(shift,1,delta*45,[0,0,0])
            for pose_name,table_name in [('closed','parts'),('open','openParts')]:
                if pose_name not in old['poses']:continue
                before=old['poses'][pose_name]['parts'];after=row.get(table_name,row['parts'])
                if len(before)!=len(after):failures.append('Part count '+row['key']);continue
                for a,b in zip(before,after):
                    for field in ('model','altModel','pitch','pivot','extraYaw','extraPivot'):
                        if a.get(field)!=b.get(field):failures.append('Authored part/UV metadata changed '+row['key']+' '+field)
                    refs.update([a['model'],a.get('altModel',a['model'])])
                for alt in [False,True]:
                    a=points(before,models,alt);b=points(after,models,alt)
                    if len(a)!=len(b):failures.append('Source vertex count '+row['key']);continue
                    for yaw in range(8):
                        error=max((abs(x-y) for p,q in zip(a,b) for x,y in zip(world(p,yaw,shift),world(q,(yaw+delta)%8,new_shift))),default=0)
                        max_error=max(error,max_error);checks+=1
                        if error>1e-6:failures.append('Normalized appearance drift '+row['key']+' yaw='+str(yaw))
        for identifier in sorted(refs):
            namespace,path=identifier.split(':',1);name='assets/'+namespace+'/models/'+path+'.json';source=RES/name
            if source.read_bytes()!=archive.read(name):failures.append('Authored source JSON changed '+identifier)
        for group in fragment['groups']:
            if group['kind']!='stairs':continue
            path=group['primaryRegistryId'].split(':',1)[1];name='bloodborne_dw/composite/'+path+'.json'
            current=read(RES/name);previous=json.loads(archive.read(name))
            if len(current['variants'])!=len(previous['variants']):failures.append('Physical pose count changed '+path)
            for a,b in zip(current['variants'],previous['variants']):
                pose_count+=1
                for field in ['collision','selection','simpleCollisionByRotation']:
                    if a['poses']['closed'].get(field)!=b['poses']['closed'].get(field):failures.append('Simple physical/outline data changed '+path+' '+field)
    result={'status':'FAIL' if failures else 'PASS','nativeCapture':'NOT_RUN','scope':'Exact reviewed source part/model/UV references and normalized vertices; this is not a native screenshot or gameplay pass',
        'appearanceRows':len(table['states']),'sourceModelJsonsUnchanged':len(refs),'normalizedYawProfileChecks':checks,'nonzeroSourceShiftBlocks':shift,'maxModelUnitVertexError':max_error,
        'canonicalPhysicalAndOutlinePosesUnchanged':pose_count,'mappingRows':len(fragment['mappings']),'failures':failures,'tableSha256':hashlib.sha256((RES/'bloodborne_dw/imported-stair-legacy-art.json').read_bytes()).hexdigest()}
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_bytes((json.dumps(result,ensure_ascii=False,indent=2)+'\n').encode('utf8'));print(json.dumps(result,ensure_ascii=False))
    if failures:raise SystemExit(1)
if __name__=='__main__':main()
