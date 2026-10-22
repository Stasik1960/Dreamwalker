"""Prove one actual complete native model can join retained foreign substrate."""
import argparse,hashlib,json,sqlite3
from pathlib import Path
from export_cells import canonical,state_key,read_lines
from export_merge_physics import require,sha
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--fragments',type=Path,required=True);p.add_argument('--compositions',type=Path,required=True);p.add_argument('--raw-sha256',required=True);p.add_argument('--key',required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    require(sha(a.compositions)==a.raw_sha256,'Raw compositions changed before targeted route proof')
    found=[r for r in read_lines(a.compositions) if r['key']==a.key];require(len(found)==1,'Target exact composition is missing or ambiguous');row=found[0]
    require(row.get('baseVisualMode')=='substrateFragments','Unexpected original visual mode')
    require(not row.get('substrateNativeFragments'),'This targeted patch has unexpected native visual references')
    refs=row.get('substrateFragments');require(refs,'Foreign substrate references must remain explicit')
    db=sqlite3.connect('file:'+a.fragments.resolve().as_posix()+'?mode=ro',uri=True);saved=db.execute('SELECT data FROM native WHERE state=?',(state_key(row['baseState']),)).fetchone();require(saved,'Actual native model is not imported')
    native=json.loads(saved[0]);require(native.get('nativeVisualComplete') is True and native.get('renderType')=='MODEL' and native['quads'],'Exact compact MODEL must have a complete nonempty visual')
    vertices=[v for q in native['quads'] for v in q['vertices']];bounds=[min(v[i] for v in vertices) for i in range(3)]+[max(v[i] for v in vertices) for i in range(3)]
    require(all(bounds[i]>=-1e-6 and bounds[i+3]<=1.000001 for i in range(3)),'Native is wide: contained route patch is prohibited; exact clipping/bounded closure required')
    foreign=[]
    for ref in refs:
        source=db.execute('SELECT source FROM poses WHERE key=?',(ref['pose'],)).fetchone();fragment=db.execute('SELECT data FROM fragments WHERE pose=? AND x=? AND y=? AND z=?',(ref['pose'],*ref['cell'])).fetchone();require(source and fragment,'Foreign exact visual reference missing')
        pose=json.loads(source[0])['sourcePose'];require(pose['registryId']!=row['baseState']['registryId'],'Own-source route would duplicate native visual')
        foreign.append({'reference':ref,'sourcePose':pose,'fragmentSha256':hashlib.sha256(fragment[0].encode()).hexdigest(),'quads':len(json.loads(fragment[0])['quads'])})
    db.close();require(sha(a.compositions)==a.raw_sha256,'Raw compositions changed during targeted proof')
    proof={'status':'PASS','compositionKey':a.key,'inputCompositionsSha256':a.raw_sha256,'baseState':row['baseState'],'nativeRowSha256':hashlib.sha256(canonical(native).encode()).hexdigest(),'fragmentsSha256':sha(a.fragments),'nativeVisualComplete':True,'nativeBounds':bounds,'fromMode':'substrateFragments','toMode':'nativePlusSubstrateFragments','retainedForeignRefs':refs,'foreignVisualEvidence':foreign,'nativeQuads':len(native['quads']),'exactNativeShapes':{k:native[k] for k in ('collision','outline','culling','climbing')},'databaseWrites':0,'mapReads':0,'mapWrites':0}
    a.out.parent.mkdir(parents=True,exist_ok=True);a.out.write_text(json.dumps(proof,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps({k:v for k,v in proof.items() if k not in ('foreignVisualEvidence','exactNativeShapes')}))
if __name__=='__main__':main()
