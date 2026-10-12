"""Read-only V12 art, identity, frozen physics and append-only state verification."""
from pathlib import Path
import hashlib,json,subprocess
ROOT=Path(__file__).resolve().parents[1];REPO=ROOT.parent;BASE='0c985dc28e4a38900cce3055ddb5658a71ed95e5'
RES=ROOT/'src/architecture/resources/bloodborne_dw'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(b):return hashlib.sha256(b).hexdigest()
def file_sha(path):
    digest=hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda:stream.read(1024*1024),b''):digest.update(chunk)
    return digest.hexdigest()
def prior(p):return subprocess.check_output(['git','show',BASE+':bloodborne complete/'+p],cwd=REPO)
def main():
    failures=[];counts={};hooks=[]
    def check(ok,reason,detail=None):
        if not ok:failures.append({'reason':reason,'detail':detail})
    frozen=read(ROOT/'docs/v12.4-dedup/baseline-protected-hashes.json')['files'];assets=0;new_dispatch=[]
    for rel,digest in frozen.items():
        path=ROOT/rel
        if '/resources/assets/' in rel or rel.startswith('src/rp/resources/'):
            if '/blockstates/source_' in rel:new_dispatch.append(rel);continue
            if '/lang/' in rel:continue # Exact original labels are checked separately below.
            check(path.is_file() and sha(path.read_bytes())==digest,'ORIGINAL_V12_RESOURCE_CHANGED',rel);assets+=1
        elif path.is_file() and sha(path.read_bytes())!=digest:hooks.append(rel)
    counts['frozenAuthoredResourceFilesExact']=assets;counts['newOnlyStateDispatchFiles']=len(new_dispatch)
    debug=read(RES/'debug_catalogue.json')['entries'];old=json.loads(prior('src/architecture/resources/bloodborne_dw/debug_catalogue.json'))['entries']
    now={r['temporaryId']:r for r in debug};check(len(now)==len(debug),'DUPLICATE_FIVE_DIGIT_ID')
    check([r['temporaryId'] for r in debug[:len(old)]]==[r['temporaryId'] for r in old],'RESERVED_NUMBER_ORDER_CHANGED')
    check(all(now[r['temporaryId']]['registryId']==r['registryId'] for r in old),'OLD_NUMBER_REBOUND')
    protected=read(ROOT/'docs/v12.4-dedup/baseline-content.json')['originalRows']
    check(all(now[r['temporaryId']]==r for r in protected),'ORIGINAL_V12_CATALOGUE_ROW_CHANGED')
    counts.update(originalV12CatalogueRows=len(protected),oldReservedNumbers=len(old),currentReservedNumbers=len(debug))
    labels=0
    for rel in frozen:
        if '/lang/' not in rel:continue
        old_lang=json.loads(prior(rel));new_lang=read(ROOT/rel)
        for row in protected:
            namespace,path=row['registryId'].split(':',1)
            for prefix in ('block','item','entity'):
                key=f'{prefix}.{namespace}.{path}'
                if key in old_lang:check(new_lang.get(key)==old_lang[key],'ORIGINAL_V12_LOCALIZED_LABEL_CHANGED',key);labels+=1
    counts['originalV12LocalizedLabelsExact']=labels
    for file in ('imported-type-aliases.json','imported-stair-legacy-art.json','imported-style-retirements.json','imported-collision-v125.json','imported-assembly-v126.json'):
        check((RES/file).read_bytes()==prior('src/architecture/resources/bloodborne_dw/'+file),'FROZEN_PRIOR_MAPPING_OR_PHYSICS_CHANGED',file)
    old_objects=json.loads(prior('src/architecture/resources/bloodborne_dw/remaining-catalogue.json'))['objects'];poses=0
    for entry in old_objects:
        rel='src/architecture/resources/bloodborne_dw/composite/'+entry['path']+'.json'
        before=json.loads(prior(rel));after=read(ROOT/rel)
        check(after['id']==before['id'],'OLD_REGISTRY_ID_CHANGED',entry['path'])
        check(len(after['variants'])>=len(before['variants']),'OLD_POSE_DELETED',entry['path'])
        for index,v in enumerate(before['variants']):
            n=after['variants'][index]
            check(n['poses']==v['poses'],'OLD_POSE_GEOMETRY_UV_OR_PHYSICS_CHANGED',[entry['path'],index]);poses+=1
    counts['oldImportedPosePrefixExact']=poses
    check((ROOT/'docs/COLLISION_IMPORT_HANDOFF.md').read_bytes()==prior('docs/COLLISION_IMPORT_HANDOFF.md'),'COLLISION_CONTRACT_CHANGED')
    inputs=REPO/'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16'
    for file,digest in [('bbmc_v15_resource.zip','29b31e3744af5794ae95ebe4c5474e3b70dfb08c143355bce815c08ceef09a36'),('bbmc_v16_map.zip','39e83f2941b768fd2ae9f84d8f57dc76aec78bd926dc5e38eab3059251d2bb08')]:
        check(file_sha(inputs/file)==digest,'ORIGINAL_INPUT_CHANGED',file)
    result={'schema':'dreamwalker-v127-preservation','status':'PASS' if not failures else 'FAIL','baselineCommit':BASE,'counts':counts,'changedIntegrationFilesRequiringNativeRegression':hooks,'failures':failures,'originalBaseAppearancePreserved':True,'nativeOriginalAltPolicy':'Colour-only fallback explicitly requested for previously identical native ALT; BASE models frozen','authorCollisionExport':'NOT_SUPPLIED_NOT_APPLIED','nativeGameplay':'NOT_PROVEN_BY_STATIC_AUDIT'}
    out=ROOT/'docs/v12.7/preservation-audit.json';out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps({k:v for k,v in result.items() if k!='changedIntegrationFilesRequiringNativeRegression'},ensure_ascii=False));assert not failures
if __name__=='__main__':main()
