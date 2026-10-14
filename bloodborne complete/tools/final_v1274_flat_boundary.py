"""Exact corrective plan for native bounds in the already applied EPS proposal.

Default audit. Apply requires its exact digest. Only an original rendered single
plane translated outward past native [-16,32] can have its tiny direction reversed.
"""
import argparse,copy,json
from pathlib import Path
import final_v1274_surface_caps as cap
import final_v1274_flat_layers as layer

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply',action='store_true')
    parser.add_argument('--expected-plan-sha256')
    parser.add_argument('--output',type=Path,default=cap.ROOT/'build/gallery-followups-v1274/flat-boundary')
    args=parser.parse_args();out=args.output.resolve()
    if not out.is_relative_to((cap.ROOT/'build').resolve()):raise ValueError('Report must stay under build/')
    previous=json.loads((cap.ROOT/'build/gallery-followups-v1274/flat-layers/apply-report.json').read_text(encoding='utf8'))
    git=cap.GitObjects();plans=[]
    try:
        for row in previous['plans']:
            raw=(cap.REPO/row['path']).read_bytes();current=json.loads(raw)
            if all(layer.native_bounds_legal(e) for e in current.get('elements',[])):continue
            if cap.digest(raw)!=row['outputSha256']:raise ValueError('Invalid input no longer matches approved EPS proposal')
            original=cap.resolve_elements(json.loads(git.get(cap.BASELINE,row['path'])),cap.BASELINE,git)
            proposed=copy.deepcopy(current);actions=[]
            for index,actual in enumerate(current['elements']):
                if layer.native_bounds_legal(actual):continue
                prior=[a for a in row['actions'] if a['element']==index]
                if len(prior)!=1:raise ValueError('Invalid element must have exactly one original EPS action')
                action=prior[0];side=action['side'];axis,sign=cap.SIDES[side];old=original[index]
                if layer.single_plane(old)!=(axis,action['originalPlane'],[side]):raise ValueError('Only original single rendered planes may be repaired')
                if actual!=layer.shift(old,axis,action['translationModelUnits'],[side]):raise ValueError('Element differs beyond exact approved EPS translation')
                requested=action['translationModelUnits'];delta=layer.legal_normal_shift(old,axis,requested,[side])
                if delta!=-requested:raise ValueError('Correction must only reverse the same tiny offset')
                proposed['elements'][index]=layer.shift(old,axis,delta,[side])
                actions.append({**action,'previousTranslationModelUnits':requested,'translationModelUnits':delta,'translationBlocks':delta/16,
                                'nativeBoundaryDirection':'inward','onlyTinyNormalDirectionReversed':True})
            if not actions or not all(layer.native_bounds_legal(e) for e in proposed['elements']):raise ValueError('Native model bounds remain invalid')
            newline='\r\n' if b'\r\n' in raw else '\n'
            output=(json.dumps(proposed,ensure_ascii=False,indent=2)+'\n').replace('\n',newline).encode('utf8')
            plans.append({'path':row['path'],'inputSha256':cap.digest(raw),'outputSha256':cap.digest(output),'actions':actions,'output':output})
    finally:git.close()
    public=[{k:v for k,v in row.items() if k!='output'} for row in plans];digest=cap.digest(cap.canonical(public))
    if args.apply:
        if args.expected_plan_sha256!=digest:raise ValueError('Exact corrective digest required: '+digest)
        for row in plans:
            if cap.digest((cap.REPO/row['path']).read_bytes())!=row['inputSha256']:raise ValueError('Input changed')
        for row in plans:(cap.REPO/row['path']).write_bytes(row['output'])
    out.mkdir(parents=True,exist_ok=True)
    for row in plans:
        path=out/'proposed'/row['path'].split('/assets/',1)[1];path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(row['output'])
    report={'status':'APPLIED_NATIVE_BOUNDARY_TINY_DIRECTION_FIX' if args.apply else 'AUDIT_ONLY_NO_PRODUCTION_CHANGES',
            'planSha256':digest,'previousEpsPlanSha256':previous['planSha256'],'modelsChanged':len(plans),
            'normalDirectionsReversed':sum(len(r['actions']) for r in plans),'oldFacesUvTexturesFlagsAndBboxDimensionsPreserved':True,
            'scope':'Only four exact single-rendered-plane offsets at native -16/32 limits; same tiny magnitude, inward direction. No runtime code or PNG changes.',
            'plans':public}
    (out/('apply-report.json' if args.apply else 'audit-report.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    (out/'summary.json').write_text(json.dumps({k:v for k,v in report.items() if k!='plans'},ensure_ascii=False,indent=2)+'\n',encoding='utf8')

if __name__=='__main__':main()
