"""Independent native-rule vertex/UV proof of reviewed yaw/source mappings."""
from __future__ import annotations
import json, sys, traceback
import numpy as np
from v124_visual_audit import ROOT,OUT,Visuals,pose_data,rot,signature

def main():
    baseline=json.loads((OUT/'baseline-content.json').read_text(encoding='utf-8'))['descriptors']
    plan=json.loads((OUT/'general-functional-groups.json').read_text(encoding='utf-8'))
    # Explicit task-local C cache; never read paused E copies.
    from pathlib import Path
    visuals=Visuals(vanilla_jar=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar')
    by_key={}
    def points(registry,variant):
        key=registry,variant
        if key not in by_key:
            spec=baseline[registry.split(':',1)[1]]
            by_key[key]=np.asarray([q[0] for q in pose_data(visuals,spec,spec['variants'][variant])]).reshape(-1,3)
        return by_key[key]
    def canonical(p):return sorted({tuple(round(float(v),3) for v in row) for row in p})
    def surfaces(registry,variant,pose,profile):
        spec=baseline[registry.split(':',1)[1]];poses=spec['variants'][variant]['poses']
        selected=poses.get(pose,poses['closed']);parts=[]
        for raw in selected['parts']:
            part=dict(raw)
            if profile=='alt':part['model']=part.get('altModel',part['model'])
            parts.extend(visuals.quads(part))
        return parts
    rows=[];failures=[];count=0;surface_count=0
    old_shift=np.array([.375,.125,-.625])
    for group in plan['groups']:
        for mapping in group['mappings']:
            a=points(mapping['oldRegistryId'],mapping['oldVariant']);b=points(mapping['representativeRegistryId'],mapping['representativeVariant'])
            delta=mapping['yawDeltaSteps'];local_delta=np.asarray(mapping['sourceShiftDelta'])
            transformed=rot(b,1,-delta*45,[8,8,8])+rot(local_delta,1,-delta*45,[0,0,0])*16
            ok=canonical(a)==canonical(transformed)
            if not ok:failures.append({'mapping':mapping,'reason':'Transformed original vertices differ'})
            surface_ok=True
            for pose in ('closed','open'):
                for profile in ('base','alt'):
                    qa=surfaces(mapping['oldRegistryId'],mapping['oldVariant'],pose,profile)
                    qb=surfaces(mapping['representativeRegistryId'],mapping['representativeVariant'],pose,profile)
                    transformed_quads=[(rot(q[0],1,-delta*45,[8,8,8])+rot(local_delta,1,-delta*45,[0,0,0])*16,*q[1:]) for q in qb]
                    match=signature(visuals,qa,0)[0]==signature(visuals,transformed_quads,0)[0];surface_count+=1;surface_ok&=match
                    if not match:failures.append({'mapping':mapping,'pose':pose,'profile':profile,'reason':'Native-rule visible vertices and vertex-associated UV/texture samples differ'})
            for yaw in range(8):
                original=rot(a,1,-yaw*45,[8,8,8])+rot(old_shift,1,-yaw*45,[0,0,0])*16
                new_local=rot(old_shift,1,delta*45,[0,0,0])+local_delta
                merged=rot(b,1,-(yaw+delta)*45,[8,8,8])+rot(new_local,1,-(yaw+delta)*45,[0,0,0])*16
                matched=canonical(original)==canonical(merged);count+=1
                if not matched:failures.append({'mapping':mapping,'yaw':yaw,'reason':'Existing typed SourceShift preservation differs'})
            rows.append({'oldId':mapping['oldId'],'oldVariant':mapping['oldVariant'],'mainId':mapping['mainId'],
                         'newVariant':mapping['variant'],'exactVisibleVerticesPreserved':ok,'exactVisibleSurfaceUvPreserved':surface_ok})
    result={'schemaVersion':1,'status':'PASS' if not failures else 'FAIL','checkedOldStates':len(rows),
            'allYawWithTypedNonzeroSourceShiftChecks':count,'coordinateToleranceModelUnits':.001,
            'baseAltClosedOpenSurfaceUvChecks':surface_count,'nativeRescaleRule':'abs(angle)==22.5 uses22.5factor; all other rescale:true elements use45factor, including angle0',
            'surfaceComparison':'Canonical vertex cycle retains associated UV sampling orientation and rendered RGBA; symmetric polygon geometry alone cannot prove textured equality.',
            'nativeGameplay':'NOT_RUN_BY_THIS_OFFLINE_PROOF','failures':failures,'states':rows}
    (OUT/'visual-mapping-vertex-proof.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(result['status'],len(rows),count,len(failures),flush=True)
    if failures:raise SystemExit(1)

if __name__=='__main__':
    with (OUT/'visual-mapping-console.txt').open('w',encoding='utf-8') as stream:
        sys.stdout=sys.stderr=stream
        try:main()
        except BaseException:traceback.print_exc();raise
