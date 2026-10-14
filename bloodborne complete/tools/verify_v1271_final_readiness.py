"""Read-only binding/asset gates for the reused final-city preparation.

This verifies prepared data and the packaged production resources. It neither
converts the city nor certifies final pixel equality/FPS; native gold acceptance
and the production-only gallery reopen are separate requirements.
"""
from __future__ import annotations
import argparse, collections, copy, hashlib, json, math, sqlite3, stat, traceback, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
VANILLA=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'
SCHEMA='dreamwalker-v1271-prepared-readiness'

def read(path):return json.loads(Path(path).read_text(encoding='utf-8-sig'))
def sha(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as f:
        for b in iter(lambda:f.read(1<<20),b''):h.update(b)
    return h.hexdigest()
def ordinary(path):
    path=Path(path).absolute()
    assert path.drive.upper()=='C:', 'Current storage binding is LocalC'
    for parent in (path,*path.parents):
        if parent.exists():
            info=parent.lstat()
            assert not stat.S_ISLNK(info.st_mode) and not getattr(info,'st_file_attributes',0)&0x400, ('Reparsed input/output path',str(parent))
    return path
def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
def identifier(value):return value if ':' in value else 'minecraft:'+value

class ModelReferences:
    """Resolve actual native fallback parent/face texture graph from this JAR."""
    def __init__(self,jar,vanilla):self.jar,self.vanilla=jar,vanilla;self.jar_names=set(jar.namelist());self.vanilla_names=set(vanilla.namelist());self.cache={};self.models=set();self.textures=set()
    def document(self,name):
        name=identifier(name);namespace,path=name.split(':',1);relative=f'assets/{namespace}/models/{path}.json'
        archive=self.jar if relative in self.jar_names else self.vanilla
        assert relative in (self.jar_names if archive==self.jar else self.vanilla_names),('Missing model resource',name)
        self.models.add(name);return json.loads(archive.read(relative))
    def resolved(self,name,parents=()):
        name=identifier(name)
        assert name not in parents,('Model parent cycle',name,parents)
        if name in self.cache:return self.cache[name]
        row=self.document(name);base={};parent=row.get('parent','')
        if parent and not parent.startswith(('builtin/','minecraft:builtin/')):base=copy.deepcopy(self.resolved(parent,(*parents,name)))
        textures={**base.get('textures',{}),**row.get('textures',{})};base.update(row);base['textures']=textures;base.pop('parent',None)
        self.cache[name]=base;return base
    def check(self,name):
        row=self.resolved(name);textures=row.get('textures',{});faces=0
        for element in row.get('elements',[]):
            for face in element.get('faces',{}).values():
                value=face['texture'];seen=set()
                while value.startswith('#'):
                    assert value not in seen and value[1:] in textures,('Undefined/cyclic visible texture alias',name,value)
                    seen.add(value);value=textures[value[1:]]
                value=identifier(value);namespace,path=value.split(':',1);relative=f'assets/{namespace}/textures/{path}.png'
                archive=self.jar if relative in self.jar_names else self.vanilla
                assert relative in (self.jar_names if archive==self.jar else self.vanilla_names),('Missing visible texture',name,value)
                if value not in self.textures:assert archive.read(relative).startswith(b'\x89PNG\r\n\x1a\n'),('Invalid texture PNG',value)
                self.textures.add(value);faces+=1
        assert faces,('Drawable fallback lacks authored faces',name)
        return faces

def check_resources(jar,vanilla):
    native=json.loads(jar.read('bloodborne_dw/source-fallback-carriers-v1271.json'))
    invisible=json.loads(jar.read('bloodborne_dw/invisible-source-carriers-v1271.json'))
    count=len(native['sourceStates']);assert count==156 and len(set(native['sourceStates'][:77]))==77
    assert len(native['targetRegistryIds'])==len(native['sourceApplicationSets'])==len(native['sourceModelApplications'])==len(native['sourceNativeStateContexts'])==len(native['physicalOverrides'])==count
    assert native['sourceBankSize']==128 and native['sourceBankCount']==2 and native['finiteContextBudget']==256
    assert native['sourceContextPrefixPreserved']==77 and native['frozenOpticalContextAppendStates']==79 and native['frozenOpticalContextAppendCells']==348528
    assert native['opticalSourceCompatibilityAppendCells']==40135
    assert native['physicalOverrides'][:77]==[None]*77
    for n,row in enumerate(native['physicalOverrides'][77:],77):
        assert row['units']==16 and native['sourceStates'][n] in native['sourceNativeStateContexts'][n]
        for name in ('collision','outline'):
            assert len(row[name])<=128
            for box in row[name]:
                assert len(box['from'])==len(box['to'])==3
                assert all(math.isfinite(v) and -32<=v<=48 for v in box['from']+box['to'])
                assert all(a<b for a,b in zip(box['from'],box['to']))
    assert len(invisible['sourceStates'])==len(set(invisible['sourceStates']))==66
    refs=ModelReferences(jar,vanilla);pairs=0;faces=0;inactive=0;union_pairs=0;union_branches=0;multipart_stats={}
    empty={'model':'bloodborne_dw:block/source_invisible_carrier_empty'}
    multipart_ids={'bloodborne_dw:source_fallback_multipart_carrier','bloodborne_dw:source_fallback_solid_multipart_carrier'}
    assert set(native['targetRegistryIds'])==multipart_ids|{'bloodborne_dw:source_fallback_carrier','bloodborne_dw:source_fallback_translucent_carrier'}
    for name in ('source_fallback_carrier','source_fallback_translucent_carrier'):
        states=json.loads(jar.read(f'assets/bloodborne_dw/blockstates/{name}.json'))['variants']
        assert len(states)==512
        for n in range(256):
            source,bank=n%128,n//128;base=states[f'profile=base,source={source},source_bank={bank}'];alt=states[f'profile=alt,source={source},source_bank={bank}']
            assert base==alt,('Unexpected ALT artwork in native carrier',name,source,bank)
            assert set(base)<={'model','x','y','uvlock'},('Unverified weighted/native application',name,source,bank,base)
            if n<count and native['targetRegistryIds'][n] not in multipart_ids:faces+=refs.check(base['model'])
            else:assert base==empty,('Unused native state must remain an explicit empty model',name,source,bank);inactive+=2
            pairs+=1
    for registry in sorted(multipart_ids):
        document=json.loads(jar.read('assets/bloodborne_dw/blockstates/'+registry.split(':')[1]+'.json'));unions=document['multipart'];unused=document['variants']
        slots=[i for i,target in enumerate(native['targetRegistryIds']) if target==registry]
        assert slots and len(unused)==2*(256-len(slots))
        for row in unions:
            assert set(row['when'])=={'source','source_bank'}, 'PROFILE-independent fixed union required'
            source,bank=int(row['when']['source']),int(row['when']['source_bank']);assert 0<=source<128 and 0<=bank<2 and source+bank*128 in slots
            assert isinstance(row['apply'],dict) and set(row['apply'])<={'model','x','y','uvlock'}, 'Array apply would create a weighted model rather than a union'
        for n in range(256):
            source,bank=n%128,n//128;branches=[r['apply'] for r in unions if r['when']=={'source':str(source),'source_bank':str(bank)}]
            if n in slots:
                assert len(branches)==len(native['sourceApplicationSets'][n]) and len(branches)>1
                assert f'profile=base,source={source},source_bank={bank}' not in unused
                assert f'profile=alt,source={source},source_bank={bank}' not in unused
                for branch in branches:faces+=refs.check(branch['model'])
                union_pairs+=1;union_branches+=len(branches)
            else:
                assert not branches and unused[f'profile=base,source={source},source_bank={bank}']==unused[f'profile=alt,source={source},source_bank={bank}']==empty
                inactive+=2
        multipart_stats[registry]={'assignedContexts':len(slots),'fixedApplications':len(unions),'safeInactiveStateRows':len(unused)}
    assert multipart_stats['bloodborne_dw:source_fallback_multipart_carrier']['assignedContexts']==5 and multipart_stats['bloodborne_dw:source_fallback_multipart_carrier']['fixedApplications']==25
    remaining=json.loads(jar.read('bloodborne_dw/remaining-catalogue.json'))['objects']
    hidden=next(r for r in remaining if r['path']=='source_compatibility_fragment')
    hidden_spec=json.loads(jar.read('bloodborne_dw/composite/source_compatibility_fragment.json'))
    assert hidden.get('conversionOnly') and len(hidden['variants'])==len(hidden_spec['variants'])==115
    snow=json.loads(jar.read('bloodborne_dw/composite/source_snow_height12.json'))
    compact=json.loads(jar.read('bloodborne_dw/imported-compact-v127.json'))['types']
    optics=next(r for r in compact if r['registryId']=='bloodborne_dw:source_snow_height12')['nativeSourceOptics']
    assert len(snow['variants'])==28 and set(optics)=={str(i) for i in range(4,28)}
    for pose in range(4,28):
        assert snow['variants'][pose]['conversionOnly']
        assert snow['variants'][pose]['nativeSourceOptics']==optics[str(pose)]['sourceState']
    return {'nativeFallbackContexts':count,'nativeFallbackSingleModelContexts':count-union_pairs,'nativeFallbackMultipartContexts':union_pairs,'nativeSourceBanks':2,'nativeBankSize':128,'nativeRegisteredCarrierStateRows':2048,'nativeFallbackBaseAltAliasPairs':pairs,'nativeMultipartImplicitBaseAltAliasPairs':union_pairs,'nativeMultipartFixedApplicationBranches':union_branches,'nativeMultipartFamilies':multipart_stats,'safeInactiveNativeModelStateRows':inactive,'nativeInvisibleSourceStates':66,
            'hiddenCompatibilityPoses':115,'hiddenCompatibilityConversionOnly':True,'hiddenOpticalPoses':24,
            'resolvedNativeModelFiles':len(refs.models),'resolvedNativeTextureFiles':len(refs.textures),'nativeFallbackFaceReferencesChecked':faces,
            'nativeSourceReferenceSha256':native['nativeReferenceSha256'],'emptyMultipartReferenceSha256':invisible['nativeMultipartReferenceSha256']}

def verify(production_jar,preparation,proof_path,usage_path=None):
    jar_path=ordinary(production_jar);prep=ordinary(preparation);plan=ordinary(prep/'source-plan.sqlite');manifest_path=ordinary(prep/'runtime/manifest.json')
    before={str(p):sha(p) for p in (jar_path,plan,manifest_path)};manifest=read(manifest_path);proof=read(ordinary(proof_path))
    usage_path=ordinary(usage_path or prep/'decision-frequency-proof.json');usage=read(usage_path)
    assert usage['status']=='PASS_READ_ONLY_COMPLETE_PREPARED_DECISION_COUNTS' and usage['preparedDatabaseUnchanged']
    assert usage['sourcePlanSha256AtAudit']==before[str(plan)], 'Run the independent read-only decision-count audit on this final SQL binding'
    assert usage['originalWorldChunksRead']==0 and not usage['weightedChoicesRecomputed'] and not usage['jvmStarted']
    assert manifest['runtimeExecution']=='READY_REUSED_NATIVE_REFERENCE_VERIFIED_PREPARATION'
    assert manifest['nativeValidation']=='PASS_VALID_ORIGINAL_V15_NATIVE_R2_SOURCE_MODELS_AND_EXHAUSTIVE_1814_NATIVE_CRITICAL_FLAGS; FULL_CITY_NOT_EXECUTED'
    assert manifest['counts']['REMOVE']==0 and not manifest['fullWorldConverted']
    assert all(manifest['counts'].get(action,0)==0 for action in ('WHOLE_CANDIDATE','UNRESOLVED_VISIBLE','ORPHAN_ACACIA_REVIEW')), 'Unresolved visible source decisions cannot be called ready'
    assert manifest['sourcePlanReusedWithoutWorldRescan'] and manifest['sourceArchivesModified'] is False
    assert manifest['sourcePlanSha256']==before[str(plan)]==proof['sourcePlanSha256']
    assert before[str(manifest_path)]==proof['runtimeManifestSha256']
    assert proof['status']=='PASS_REUSED_PREPARATION_NATIVE_SOURCE_REFERENCE_DELTA'
    assert proof['sourceWorldRescans']==proof['sourceIndexArraysChanged']==0
    assert not proof['weightedChoicesRecomputed'] and not proof['originalSourceArchivesChanged'] and not proof['fullWorldConverted']
    audit_path=ordinary(proof_path.parent/'native-compact-flag-proof.json');audit=read(audit_path)
    assert sha(audit_path)==manifest['nativeCompactFlagAuditSha256']==proof['nativeCompactFlagAuditSha256']
    assert audit['status']=='PASS_ALL_REACHABLE_COMPACT_NATIVE_AO_FULLCUBE_LIGHT_FLAGS_WITH_REPORTED_NONCRITICAL_SHAPE_DIFFERENCES'
    assert audit['targetCases']==manifest['nativeCompactFlagAuditTargets']==proof['nativeCompactFlagAuditTargets']==1814
    assert audit['nativeDecodeFailures']==manifest['nativeCompactFlagAuditDecodeFailures']==0
    assert audit['criticalDifferenceGroups']==manifest['nativeCompactFlagAuditCriticalDifferences']==0 and audit['criticalDifferenceOccurrences']==0
    assert manifest['nativeFrozenOpticalContextAuditStatus']=='PASS'
    assert usage['decisionIndexArraysSha256']==proof['allDecisionIndexArraysSha256']
    con=sqlite3.connect(f'file:{plan}?mode=ro',uri=True)
    try:
        summary=json.loads(con.execute("SELECT data FROM metadata WHERE key='summary'").fetchone()[0])
        decisions={i:json.loads(data) for i,data in con.execute('SELECT id,data FROM decisions')}
        assert summary['counts']==manifest['counts']==proof['counts']
        assert summary['nativeSourceReferenceSha256']==manifest['nativeSourceReferenceSha256']==proof['nativeSourceReferenceSha256']
        assert len(decisions)==manifest['decisionDictionaryCount']
        frequencies={int(i):n for i,n in usage['decisionCellCounts'].items()};assert set(frequencies)==set(decisions)
        actual_counts={action:sum(frequencies[i] for i,d in decisions.items() if d['action']==action) for action in manifest['counts']}
        assert actual_counts==manifest['counts'] and actual_counts['REMOVE']==0,('Final dictionary differs from actual prepared cell counts',actual_counts)
        removed_dictionary={i:d for i,d in decisions.items() if d['action']=='REMOVE'}
        assert all(frequencies[i]==0 for i in removed_dictionary), 'A used discarded dictionary row cannot be called ready'
        assert sum(manifest['counts'].values())==manifest['allPaletteCells']
        assert con.execute('SELECT COUNT(*) FROM sections').fetchone()[0]==manifest['sections']==usage['sections']==861360
    finally:con.close()
    cohorts={'nativeSourceFallbackCells':10795,'nativeEmptyMultipartSourceCells':523,'protectedSingleBeehiveLadderCells':13,'compactSourceOpticalCarrierSplitCells':2905,'nativeGlassOpticalCompatibilityCells':40135,'nativeTintedGlassCompatibilityCells':18175,'nativeFrozenOpticalContextCells':348528,'nativeFrozenOpticalContextStates':79,'nativeFrozenOpticalContextDecisionRecords':80}
    for name,value in cohorts.items():assert manifest[name]==value,(name,manifest.get(name))
    runtime_hashes={}
    for name,digest in manifest['runtimeFiles'].items():
        path=ordinary(prep/'runtime'/name);assert path.parent==prep/'runtime' and path.name==name
        runtime_hashes[name]=sha(path);assert runtime_hashes[name]==digest==proof['runtimeFiles'][name]
    with zipfile.ZipFile(jar_path) as jar,zipfile.ZipFile(ordinary(VANILLA)) as vanilla:
        assert jar.testzip() is None
        meta=json.loads(jar.read('fabric.mod.json'));assert meta['id']=='bloodborne_dw' and 'v12.7.1' in meta['version']
        resources=check_resources(jar,vanilla)
        assert hashlib.sha256(jar.read('bloodborne_dw/source-fallback-carriers-v1271.json')).hexdigest()==manifest['nativeSourceFallbackConfigurationSha256']==proof['sourceFallbackConfigurationSha256']
        assert resources['nativeSourceReferenceSha256']==resources['emptyMultipartReferenceSha256']==manifest['nativeSourceReferenceSha256']
        assert not any('galleryqa/' in n or '/gallery/FlatGalleryV127' in n for n in jar.namelist()), 'Production contains authoring QA classes'
        config=json.loads(jar.read('bloodborne_dw/source-fallback-carriers-v1271.json'))
        used_contexts=set();fixed_context_cells=0;fixed_context_ids=set()
        for decision_id,d in decisions.items():
            if d['action']=='VANILLA' and d['registryId'].startswith('bloodborne_dw:source_fallback'):
                source=int(d['properties']['source']);bank=int(d['properties'].get('source_bank','0'));context=source+bank*128
                assert d['properties']['profile']=='base' and 0<=source<128 and 0<=bank<2 and 0<=context<156
                assert config['targetRegistryIds'][context]==d['registryId'] and d['sourceState'] in config['sourceNativeStateContexts'][context]
                if frequencies[decision_id]:used_contexts.add(context)
                if d.get('reason')=='EXACT_FIXED_SOURCE_NATIVE_OPTICAL_CONTEXT':fixed_context_cells+=frequencies[decision_id];fixed_context_ids.add(decision_id)
        assert used_contexts==set(range(156)),('Planned native contexts were not completely applied to actual prepared cells',sorted(set(range(156))-used_contexts))
        assert fixed_context_cells==348528 and len(fixed_context_ids)==80,('Fixed native source optics delta incomplete',fixed_context_cells,len(fixed_context_ids))
    assert all(sha(Path(p))==digest for p,digest in before.items()), 'Verification inputs changed'
    return {'schema':SCHEMA,'status':'PASS_READ_ONLY_REUSED_PREPARATION_AND_NATIVE_RESOURCE_BINDING','productionJar':str(jar_path),
            'productionJarSha256':before[str(jar_path)],'preparation':str(prep),'sourcePlanSha256':before[str(plan)],
            'runtimeManifestSha256':before[str(manifest_path)],'runtimeFiles':runtime_hashes,'counts':manifest['counts'],
            'decisionDictionaryEntries':len(decisions),'nativeProvenSourceCohorts':cohorts,'resources':resources,
            'independentDecisionFrequencyProofSha256':sha(usage_path),'preparedDecisionIndexArraysSha256':usage['decisionIndexArraysSha256'],
            'zeroCellHistoricalRemoveDictionaryRows':len(removed_dictionary),'actualCountsFromIndependentPreparedArrayScan':actual_counts,
            'usedNativeSourceContexts':len(used_contexts),'fixedNativeOpticalContextDecisionRecords':len(fixed_context_ids),'fixedNativeOpticalContextCells':fixed_context_cells,
            'nativeCompactFlagAuditSha256':manifest['nativeCompactFlagAuditSha256'],'nativeCompactFlagAuditTargets':1814,'nativeCompactFlagAuditCriticalDifferences':0,
            'recordedNoncriticalNativeFlagDifferences':audit['differenceOccurrencesByField'],
            'discardedSourceCells':0,'inputFilesUnchanged':True,'originalSourceArchivesModified':False,
            'sourcePreparationRecomputed':False,'fullWorldConverted':False,'jvmStarted':False,
            'nativeGoldVisualAndPerformanceAcceptance':'REQUIRED_SEPARATELY','galleryAuthorAndProductionReopen':'REQUIRED_SEPARATELY'}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--production-jar',type=Path,required=True)
    p.add_argument('--preparation',type=Path,default=ROOT/'build/v1271-city-preparation')
    p.add_argument('--preparation-proof',type=Path,default=ROOT/'docs/v12.7.1/conversion/final-prepared-bundle-verification.json')
    p.add_argument('--decision-frequency-proof',type=Path)
    p.add_argument('--resources-only',action='store_true',help='Audit native model/bank/ALT/unused resource graph only; never claim full preparation readiness')
    p.add_argument('--report',type=Path,default=ROOT/'build/v1271-final-readiness.json');a=p.parse_args()
    try:
        if a.resources_only:
            path=ordinary(a.production_jar);digest=sha(path)
            with zipfile.ZipFile(path) as jar,zipfile.ZipFile(ordinary(VANILLA)) as vanilla:
                assert jar.testzip() is None
                result={'schema':SCHEMA,'status':'PASS_STATIC_NATIVE_RESOURCE_GRAPH_ONLY_REQUIRES_PREPARATION_AND_NATIVE_ACCEPTANCE','productionJarSha256':digest,'resources':check_resources(jar,vanilla),'jvmStarted':False,'fullPreparationReadiness':False}
            assert sha(path)==digest,'JAR changed during resource inspection'
        else:result=verify(a.production_jar,a.preparation,a.preparation_proof,a.decision_frequency_proof)
    except BaseException:
        write(ordinary(a.report),{'schema':SCHEMA,'status':'FAIL','error':traceback.format_exc()});raise
    write(ordinary(a.report),result)

if __name__=='__main__':main()
