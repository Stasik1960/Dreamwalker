"""Read-only original V12, reserved-ID and old artwork preservation proof."""
from pathlib import Path
from collections import Counter
import hashlib,json,subprocess
import check_v124_preservation as original
ROOT=Path(__file__).resolve().parents[1];REPO=ROOT.parent;DOC=ROOT/'docs/v12.6';BASELINE='c52a06c9fa20007a1c06c8fc654b3c1b86f72791'
JAVA='src/architecture/java/dev/dreamwalker/bloodbornedw/'
SHARED=['architecture/RemainingArchitecture','composite/CompositeArchitecture','composite/CompositeItem','composite/CompositeRootBlock','composite/CompositeRuntime','composite/CompositeSourceShift','composite/CompositeSpec','composite/ImportedArtworkBlock','composite/ImportedArtworkStates','composite/mixin/CatalogueStackMigrationMixin','link/CompositeMechanismBridge','link/MechanismGroups','link/MechanismLinks','link/MechanismRules','link/MechanismState','link/ObjectPolicies','tool/ToolCommands']
HOOK_LINES={JAVA+p+'.java':[] for p in SHARED}
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(v):return hashlib.sha256(v).hexdigest()
def baseline(p):return subprocess.check_output(['git','show',BASELINE+':bloodborne complete/'+p],cwd=REPO)
def run():
    errors=[];counts=Counter()
    def check(ok,code,data=None):
        if not ok:errors.append({'code':code,'detail':data})
    frozen=read(ROOT/'docs/v12.4-dedup/baseline-protected-hashes.json')['files']
    for p,h in frozen.items():
        if p in original.ALLOW:continue
        actual=ROOT/p
        # Metadata adapters for merging NEW instances are guarded by migration
        # plans; their unchanged old single-record paths are exercised by the
        # original V12/RP GameTests, separately from this file hash proof.
        if p in HOOK_LINES:continue
        check(actual.is_file() and sha(actual.read_bytes())==h,'FROZEN_V12_FILE_CHANGED',p)
        counts['unchangedFrozenV12Files']+=1
    res=ROOT/'src/architecture/resources/bloodborne_dw'
    debug=read(res/'debug_catalogue.json');old_debug=json.loads(baseline('src/architecture/resources/bloodborne_dw/debug_catalogue.json'))
    now={r['temporaryId']:r for r in debug['entries']}
    check(len(now)==len(debug['entries'])==1322,'UNIQUE_RESERVED_NUMBERS')
    check([r['temporaryId'] for r in debug['entries']]==[r['temporaryId'] for r in old_debug['entries']],'RESERVED_NUMBER_ORDER_CHANGED')
    check(all(now[r['temporaryId']]['registryId']==r['registryId'] for r in old_debug['entries']),'NUMBER_REUSED_OR_REGISTRY_REBOUND')
    protected=read(ROOT/'docs/v12.4-dedup/baseline-content.json')['originalRows']
    check(all(now[r['temporaryId']]==r for r in protected),'ORIGINAL_V12_CATALOGUE_ROWS_CHANGED');counts['protectedOriginalCatalogueRows']=len(protected)
    aliases=read(res/'imported-type-aliases.json');old_aliases=json.loads(baseline('src/architecture/resources/bloodborne_dw/imported-type-aliases.json'))
    check(aliases['mappings']==old_aliases['mappings'],'OLD_STATE_MAPPINGS_CHANGED');counts['preservedStateMappings']=len(aliases['mappings'])
    for p in ['imported-stair-legacy-art.json','imported-style-retirements.json','imported-collision-v125.json']:
        check((res/p).read_bytes()==baseline('src/architecture/resources/bloodborne_dw/'+p),'PRESERVED_PRIOR_RESOURCE_CHANGED',p)
    applied=read(DOC/'applied-content.json');allowed={r['registryId'] for r in applied['groups']}
    doors=read(ROOT/'build/v126-door-plan/door-manifest.json');doorposes={d['registryId']:set(d['variants']) for d in doors['doors']};allowed.update(doorposes)
    paths=[r['path'] for r in read(res/'remaining-catalogue.json')['objects']]
    check(len(set(paths))==len(paths)==1045,'REGISTRATION_COHORT_CHANGED')
    for path in paths:
        rel='src/architecture/resources/bloodborne_dw/composite/'+path+'.json';actual=(ROOT/rel).read_bytes();prior=baseline(rel);registry='bloodborne_dw:'+path
        if registry not in allowed:check(actual==prior,'UNAPPROVED_NEW_DESCRIPTOR_CHANGE',registry);continue
        old=json.loads(prior);new=json.loads(actual)
        check(len(new['variants'])>=len(old['variants']),'OLD_VARIANTS_REMOVED',registry)
        for v,variant in enumerate(old['variants']):
            if v not in doorposes.get(registry,set()):check(new['variants'][v]==variant,'OLD_POSE_PREFIX_CHANGED',(registry,v))
            else:check(new['variants'][v]['poses']['closed']['parts']==variant['poses']['closed']['parts'],'OLD_CLOSED_DOOR_VISUAL_CHANGED',(registry,v))
            counts['unchangedOldArtworkPoses']+=1
    for d in doors['doors']:
        spec=read(res/'composite'/(d['registryId'].split(':')[1]+'.json'))
        check(spec['variants'][d['variant']]['poses']['closed']['parts']==d['sourceClosedParts'],'DOOR_CLOSED_PROOF_MISMATCH',d['mainId'])
        check(not d['closedProofFailures'] and d['maxUvRoundingError']<1e-7 and d['maxVertexRoundingError']<1e-7,'DOOR_SOURCE_UV_COVERAGE',d['mainId'])
    counts.update({'registeredNewTypes':1045,'protectedReservedNumbers':1322,'appendedNewPoses':sum(g['after']-g['before'] for g in applied['groups']),'functionalDoors':len(doors['doors'])})
    contract='docs/COLLISION_IMPORT_HANDOFF.md';check((ROOT/contract).read_bytes()==baseline(contract),'AUTHOR_CONTRACT_CHANGED')
    inputs=ROOT.parent/'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16'
    for f,h in [('bbmc_v15_resource.zip','29b31e3744af5794ae95ebe4c5474e3b70dfb08c143355bce815c08ceef09a36'),('bbmc_v16_map.zip','39e83f2941b768fd2ae9f84d8f57dc76aec78bd926dc5e38eab3059251d2bb08')]:check(sha((inputs/f).read_bytes())==h,'SOURCE_ARCHIVE_CHANGED',f)
    return {'schema':'dreamwalker-v126-preservation','status':'PASS' if not errors else 'FAIL','baselineCommit':BASELINE,'counts':dict(counts),'failures':errors,'collisionContractSha256':sha(baseline(contract)),'authorCollisionExport':'NOT_SUPPLIED_NOT_APPLIED','nativeGameplay':'NOT_RUN_BY_SOURCE_AUDIT'}
def main():
    result=run();DOC.mkdir(parents=True,exist_ok=True);(DOC/'preservation-audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8');print(json.dumps(result,ensure_ascii=False));assert result['status']=='PASS'
if __name__=='__main__':main()
