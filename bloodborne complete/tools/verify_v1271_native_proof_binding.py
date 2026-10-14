"""Bind a native model proof across a reviewed client cache-only change.

This does not claim that the source proof ran the target JAR. Every resource
and every class outside CompositeWorldMesh must be byte-identical. Final
native cache, unload, pixel and performance checks remain separate.
"""
import argparse, hashlib, json, zipfile
from pathlib import Path
from verify_v1271_final_readiness import ROOT, ordinary, read, sha, write

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source-production-jar',type=Path,required=True)
    p.add_argument('--production-jar',type=Path,required=True)
    p.add_argument('--native-source-model-proof',type=Path,required=True)
    p.add_argument('--source-run-result',type=Path,required=True)
    p.add_argument('--report',type=Path,default=ROOT/'build/final-city-fixes/native-model-proof-final-binding.json')
    a=p.parse_args()
    source=ordinary(a.source_production_jar);target=ordinary(a.production_jar)
    proof=ordinary(a.native_source_model_proof);run=read(ordinary(a.source_run_result));data=read(proof)
    source_sha=sha(source);target_sha=sha(target)
    assert run['status']=='PASS_NATIVE_FALLBACK_MODEL_REFERENCE_THEN_EXTERNAL_PACK_DISABLED'
    assert run['exitCode']==0 and run['productionJarSha256']==source_sha
    assert data['status']==run['status'] and data['sourceStates']==156
    assert data['texturePixelsRepainted'] is False and data['verifiedVerticesPerProfile']==12324
    changed=[];verified=[]
    allowed='dev/dreamwalker/bloodbornedw/composite/CompositeWorldMesh'
    with zipfile.ZipFile(source) as old,zipfile.ZipFile(target) as new:
        old_names=set(old.namelist());new_names=set(new.namelist())
        assert old_names==new_names,'Production members changed beyond a cache-only edit'
        for name in sorted(old_names):
            if name.endswith('/'):continue
            old_data=old.read(name);new_data=new.read(name)
            old_hash=hashlib.sha256(old_data).hexdigest();new_hash=hashlib.sha256(new_data).hexdigest()
            row={'path':name,'sourceSha256':old_hash,'targetSha256':new_hash}
            if old_data!=new_data:
                assert name.startswith(allowed) and name.endswith('.class'),('Non-cache proof domain changed',name)
                changed.append(row)
            else:verified.append(row)
    result={'schema':'dreamwalker-native-model-proof-component-binding-v1','status':'PASS',
        'sourceProductionJar':str(source),'sourceProductionJarSha256':source_sha,
        'targetProductionJar':str(target),'targetProductionJarSha256':target_sha,
        'nativeSourceModelProof':str(proof),'nativeSourceModelProofSha256':sha(proof),
        'sourceStates':156,'relevantDomainsUnchanged':True,'sameJarNativeExecution':source_sha==target_sha,
        'proofScope':'Native BakedModel geometry/UV/texture pixels/RGBA/tint/face/shade/winding and BASE/ALT; all resources and non-mesh classes identical',
        'excludedScope':'CompositeWorldMesh cache behaviour; final target native cache/unload/pixel/FPS tests are separate',
        'changedClientCacheMembers':changed,'byteIdenticalMemberCount':len(verified),
        'resourceMemberCount':sum(row['path'].startswith('assets/') for row in verified),
        'byteIdenticalMembers':verified}
    write(ordinary(a.report),result)
    print(json.dumps({k:v for k,v in result.items() if k!='byteIdenticalMembers'}))

if __name__=='__main__':main()
