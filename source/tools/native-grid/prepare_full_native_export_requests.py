"""File-only requests for the exact missing states of the completed FULL raw.

No SQLite, source chunks, census, exporter, build or world is opened. Native
function decisions and actual post-export visual/physical coverage stay separate.
"""
import argparse
import collections
import hashlib
import json
import os
import sys
from pathlib import Path

PROJECT=Path(__file__).resolve().parents[2];ROOT=PROJECT.parent;PLAN=ROOT/'research/native-grid-plan'
COVERAGE_SHA='a7d0173a8d8fad5604e76c0585d9ae13308d58c0f17d17ac3dfa2117c079e7e4'
RAW_SHA='3af36baa112a022b79b4c8834b0523e38be747a5072d7acdd8e17ea450184ec5'
NEEDED_SHA='7baf8c78a96a20c875b48c83ac14dc81767be474ff709e32a60e5228c46f0910'
CAPS_SHA='4631f03fe354733a230dab9d5bbffc339a3cbf3fefb9362544199569685dc461'
FRAGMENTS_SHA='96b0ac0fb72481b6c59c83167e5649a494da3a6ccafedccf3907b219bc424bca'
BASE_SHA='40cecfb7e5cf783a762e350fc1864518c39abff177f6f025cb94892a181fbf51'
PACKAGE='dev.dreamwalker.bloodbornedw.composite.'


def require(value,message):
    if not value:raise ValueError(message)


def canonical(value):return json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(',',':'),allow_nan=False)
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def load(path,wanted):
    payload=Path(path).read_bytes();digest=hashlib.sha256(payload).hexdigest();require(digest==wanted,'Actual input SHA differs: '+str(path))
    return json.loads(payload.decode('utf-8-sig')),{'path':str(Path(path).resolve()),'sha256':digest}


def state(value):
    require(isinstance(value,dict) and isinstance(value.get('registryId'),str) and isinstance(value.get('properties'),dict),'Exact registryId/properties required')
    require(all(isinstance(key,str) and isinstance(item,str) for key,item in value['properties'].items()),'Every actual property must be an explicit string')
    return {'registryId':value['registryId'],'properties':dict(value['properties'])}


def references(row):
    total=0
    for field in ('substrateFragments','substrateNativeFragments'):
        values=row.get(field,[]);require(isinstance(values,list),'Explicit substrate references must be lists')
        for ref in values:
            require(isinstance(ref,dict) and isinstance(ref.get('cell'),list) and len(ref['cell'])==3 and all(type(v) is int for v in ref['cell']),'Incomplete exact substrate cell reference')
            if field=='substrateFragments':require(isinstance(ref.get('pose'),str) and ref['pose'],'Missing exact SourcePose reference')
            else:state(ref['state'])
            total+=1
    return total


def write_new(path,value):
    payload=(json.dumps(value,ensure_ascii=False,indent=2,allow_nan=False)+'\n').encode('utf-8')
    with Path(path).open('xb') as stream:stream.write(payload);stream.flush();os.fsync(stream.fileno())
    return {'path':str(Path(path).resolve()),'sha256':hashlib.sha256(payload).hexdigest()}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--coverage',type=Path,default=PLAN/'full/post-activation-initial-coverage.json')
    parser.add_argument('--raw',type=Path,default=PLAN/'compositions.jsonl')
    parser.add_argument('--needed',type=Path,default=PLAN/'native-states-needed.json')
    parser.add_argument('--capabilities',type=Path,default=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW-ordinary-city-pilot-20261020\tools\ordinary-stage1\source-capabilities.json'))
    parser.add_argument('--out',type=Path,default=PLAN/'full/native-export-inputs')
    args=parser.parse_args();require(not args.out.exists(),'Use a fresh immutable request/proof directory')
    coverage,coverage_ref=load(args.coverage,COVERAGE_SHA);needed,needed_ref=load(args.needed,NEEDED_SHA);capabilities,caps_ref=load(args.capabilities,CAPS_SHA)
    require(coverage.get('scope')=='FULL_CITY' and coverage.get('kind')=='ACTUAL_POST_ACTIVATION_NATIVE_INPUT_GATE','Need the actual FULL post-activation coverage')
    require(coverage['compositionsSha256']==coverage['compositionGeneration']['compositionsSha256']==RAW_SHA and coverage['nativeStatesNeededSha256']==NEEDED_SHA,'Actual FULL generation changed')
    require(coverage['fragmentBinding']['sha256']==coverage['compositionGeneration']['sourceFragments']['sha256']==FRAGMENTS_SHA and coverage['baselineJar']['sha256']==BASE_SHA,'Original fragment/JAR binding changed')
    required={};missing={};caps={}
    for raw in needed['nativeStates']:
        value=state(raw);key=canonical(value);require(key not in required,'Duplicate exact native-needed tuple');required[key]=value
    for raw in coverage['missingNativeExports']:
        value=state(raw);key=canonical(value);require(key in required and key not in missing,'Missing export is duplicated or not actually needed');missing[key]=value
    require(len(required)==coverage['actualNeededStates']==4855 and len(missing)==4047,'Actual needed/missing finite scope changed')
    for cap in capabilities['states']:
        rid=cap['registryId'];require(rid not in caps,'Ambiguous registry capability');caps[rid]=cap
    records={};problems=[];problem_count=0
    for key,value in sorted(missing.items()):
        rid=value['registryId'];cap=caps.get(rid);clazz=cap.get('className') if cap else None
        if cap:require(cap.get('registryPresent') is True and isinstance(clazz,str) and clazz,'Missing exact registry/class capability: '+key)
        if clazz==PACKAGE+'StaticArchitectureBlock':route='physics_only_explicit_source_visuals'
        elif rid.startswith('minecraft:'):route='full_actual_vanilla_native'
        elif clazz and clazz.startswith(PACKAGE+'ImportedCompactBlock'):
            require(value['properties'].get('compact')=='true' and value['properties'].get('rotation') in ('0','2','4','6'),'Native compact export requires its actual compact/even-yaw branch: '+key);route='full_actual_compact_native'
        elif clazz==PACKAGE+'SourceFallbackCarrierV1271':route='full_actual_fallback_native'
        elif clazz and clazz.startswith(PACKAGE+'MaterialSlabs$'):route='full_actual_material_slab_native'
        else:raise ValueError('Unknown finite native exporter route; no default or function inference: '+key+' class='+str(clazz))
        records[key]={'state':value,'className':clazz,'route':route,'rawUses':0,'baseVisualModes':collections.Counter(),'firstCompositionKeys':[],'proofDigest':hashlib.sha256()}
    before=args.raw.stat();raw_hash=hashlib.sha256();rows=0
    with args.raw.open('rb') as stream:
        for line in stream:
            raw_hash.update(line)
            if not line.strip():continue
            row=json.loads(line.decode('utf-8-sig'));rows+=1;base=row.get('baseState')
            if base is None:continue
            key=canonical(state(base))
            if key not in records:continue
            record=records[key];mode=row.get('baseVisualMode','native');require(mode in ('native','substrateFragments','nativePlusSubstrateFragments'),'Unknown actual base visual mode')
            record['rawUses']+=1;record['baseVisualModes'][mode]+=1
            if len(record['firstCompositionKeys'])<4:record['firstCompositionKeys'].append(row['key'])
            proof={'key':row['key'],'baseVisualMode':mode,'substrateFragments':row.get('substrateFragments',[]),'substrateNativeFragments':row.get('substrateNativeFragments',[])}
            record['proofDigest'].update((canonical(proof)+'\n').encode('utf-8'))
            if record['route']=='physics_only_explicit_source_visuals' and not(mode=='substrateFragments' and references(row)>0):
                problem_count+=1
                if len(problems)<64:problems.append({'state':record['state'],'compositionKey':row['key'],'baseVisualMode':mode,'reason':'StaticArchitecture physics-only requires nonempty exact explicit source/native substrate references in EVERY actual raw use'})
    after=args.raw.stat();require((before.st_size,before.st_mtime_ns)==(after.st_size,after.st_mtime_ns) and raw_hash.hexdigest()==RAW_SHA,'Pinned raw changed or differs from actual FULL generation')
    for record in records.values():
        if not record['rawUses']:
            problem_count+=1
            if len(problems)<64:problems.append({'state':record['state'],'reason':'A missing-needed state has no actual raw base usage'})
    bindings={'coverage':coverage_ref,'raw':{'path':str(args.raw.resolve()),'sha256':RAW_SHA},'nativeStatesNeeded':needed_ref,'sourceCapabilities':caps_ref}
    proof_rows=[{**record,'baseVisualModes':dict(record['baseVisualModes']),'rawRouteProofSha256':record['proofDigest'].hexdigest()} for record in records.values()]
    for record in proof_rows:del record['proofDigest']
    common={'schemaVersion':1,'poses':[],'sourceBinding':coverage['sourceBinding'],'sourceJarSha256':BASE_SHA,'inputBindings':bindings,'sourcePosesRequested':0}
    physics=[{**record['state'],'physicsOnly':True} for record in records.values() if record['route']=='physics_only_explicit_source_visuals']
    visual=[{**record['state'],'physicsOnly':False} for record in records.values() if record['route']!='physics_only_explicit_source_visuals']
    require(len(physics)+len(visual)==4047,'Finite split lost an actual state')
    report={'schemaVersion':1,'status':'BLOCKED_MISSING_EXACT_VISUAL_ROUTE' if problem_count else 'FINITE_NATIVE_EXPORT_REQUESTS_PREPARED_NOT_EXPORTED_OR_COMPILED','kind':'ACTUAL_FULL_MISSING_NATIVE_EXPORT_REQUESTS','sourceBinding':coverage['sourceBinding'],'inputBindings':bindings,'historicalFragmentsSha256':FRAGMENTS_SHA,'actualNeededStates':len(required),'missingNativeStates':len(missing),'physicsOnlyStates':len(physics),'fullVisualStates':len(visual),'rawCompositionsRead':rows,'stateRouteProofs':proof_rows,'problemCount':problem_count,'problems':problems,'problemDetailsTruncated':problem_count>len(problems),'producer':{'path':str(Path(__file__).resolve()),'sha256':sha(__file__)},'sqlQueries':0,'databaseWrites':0,'worldReads':0,'worldWrites':0,'gameProcessesStarted':0,'functionProofClaimed':False,'nativeExportCoverageClaimed':False,'sourceGeometryArchiveRepeated':False,'mandatoryNext':'Actual strict8ebb exports/clean launch/append lineage; new complete visuals need exact bounds and actual composition route/closure coverage before compiler.'}
    args.out.mkdir(parents=True)
    if not problem_count:
        report['requests']={'physics':write_new(args.out/'physics-request.json',{**common,'nativeStates':physics}),'visual':write_new(args.out/'visual-request.json',{**common,'nativeStates':visual})}
    write_new(args.out/'preparation.json',report)
    print(canonical({key:report[key] for key in ('status','actualNeededStates','missingNativeStates','physicsOnlyStates','fullVisualStates','problemCount')}))
    return 2 if problem_count else 0


if __name__=='__main__':sys.exit(main())
