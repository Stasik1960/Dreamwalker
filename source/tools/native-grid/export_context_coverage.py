"""Read existing eligible-owner lists and cached palettes; no map scan/export."""
import argparse,collections,hashlib,json,sqlite3,sys
from pathlib import Path
PROJECT=Path(__file__).resolve().parents[2];ROOT=PROJECT.parent
sys.path.insert(0,str(PROJECT/'tools'))
from world_io import Tag,compound,decode_nbt
from export_cells import canonical,state_key,read_lines
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def read(path):return json.loads(Path(path).read_text(encoding='utf-8-sig'))
def ro(path):return sqlite3.connect('file:'+Path(path).absolute().as_posix()+'?mode=ro',uri=True)
def state(tag):
    t=compound(tag);return {'registryId':t['Name'].value,'properties':{k:v.value for k,v in t.get('Properties',Tag(10,{})).value.items()}}
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,default=ROOT/'research/native-grid-plan/context-extension/asset-coverage-before-compose.json');a=p.parse_args()
    folder=ROOT/'research/native-grid-plan/context-extension';inventory=read(folder/'inventory.json');eligible=folder/'eligible-owners.jsonl';start=sha(eligible)
    source=read(ROOT/'research/native-grid-plan/source-binding.json');old=ro(Path(source['oldPlan'])/'plan.sqlite');frags=ro(ROOT/'research/fragments.sqlite')
    pose_keys={r[0] for r in frags.execute('SELECT key FROM poses')};required=collections.Counter(r['pose'] for r in read_lines(eligible));missing=sorted(set(required)-pose_keys)
    native={key:json.loads(data) for key,data in frags.execute('SELECT state,data FROM native')};cap=read(Path(source['oldPlan'])/'source-capabilities-pin.json');native_types=set(cap['nativeStateTypes'])
    palettes={};chunks=[]
    for cx,cz in inventory['mainChunks']:
        row=old.execute("SELECT region_path,region_hash,compression,payload FROM chunk_cache WHERE dim='.' AND x=? AND z=?",(cx,cz)).fetchone()
        if row is None:raise ValueError('Missing existing cached main chunk '+str((cx,cz)))
        region,digest,compression,payload=row;root=compound(decode_nbt(payload,compressed={1:'gzip',2:'zlib',3:None}[compression]).root)
        for raw in root.get('sections',Tag(9,[],10)).value:
            section=compound(raw);container=section.get('block_states')
            if container is None:continue
            for tag in compound(container).get('palette',Tag(9,[],10)).value:
                value=state(tag);palettes[state_key(value)]=value
        chunks.append({'chunk':[cx,cz],'region':region,'boundRegionSha256':digest,'cachedCompressedPayloadSha256':hashlib.sha256(payload).hexdigest()})
    candidate={key:value for key,value in palettes.items() if value['registryId'].startswith('minecraft:') or value['registryId'] in native_types or value['properties'].get('compact')=='true'}
    absent=[candidate[key] for key in sorted(set(candidate)-set(native))]
    physics_only=[value for key,value in candidate.items() if key in native and not native[key].get('nativeVisualComplete',True)]
    wide=[value for key,value in candidate.items() if key in native and native[key].get('nativeVisualComplete',True) and any(v[i]<-1e-6 or v[i]>1.000001 for q in native[key].get('quads',[]) for v in q['vertices'] for i in range(3))]
    if sha(eligible)!=start:raise ValueError('Eligible list changed during coverage inventory')
    result={'status':'SOURCE_POSES_COVERED_NATIVE_PALETTE_SUPERSET_PENDING_FINAL_COMPOSITIONS' if not missing else 'MISSING_SOURCE_POSES','mode':'READ_ONLY_EXISTING_CACHE','eligibleOwners':sum(required.values()),'eligibleOwnerListSha256':start,'requiredUniqueSourcePoses':len(required),'exportedSourcePoses':len(pose_keys),'missingSourcePoses':missing,'nativeStateExports':len(native),'mainChunks':len(chunks),'rootBounds':inventory['rootBounds'],'cachedPaletteStates':len(palettes),'candidateNativePaletteStates':len(candidate),'coveredNativePaletteStates':len(set(candidate)&set(native)),'missingCandidateNativeStates':absent,'physicsOnlyCandidateNativeStates':physics_only,'wideCompleteCandidateNativeStates':wide,'cachedChunks':chunks,'mapReads':0,'mapWrites':0,'databaseWrites':0,'exportsLaunched':0,'coverageLimit':'Native palette entries are a finite superset, not actual final composition requirements. Source/BE/protected functional states may remain untouched. Definitive native/source fragment coverage requires the frozen phase2 compositions and compiler preflight.'}
    a.out.parent.mkdir(parents=True,exist_ok=True);a.out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in result.items() if k not in ('cachedChunks','missingCandidateNativeStates','physicsOnlyCandidateNativeStates','wideCompleteCandidateNativeStates')}|{'missingCandidateNativeStates':len(absent),'physicsOnlyCandidateNativeStates':len(physics_only),'wideCompleteCandidateNativeStates':len(wide),'out':str(a.out)}))
if __name__=='__main__':main()
