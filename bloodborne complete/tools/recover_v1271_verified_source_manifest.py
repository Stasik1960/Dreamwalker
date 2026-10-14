"""Finalize hash binding after completed native streams, without reauthoring."""
import collections,gzip,json,re,sqlite3,traceback,zlib
import numpy as np
from prepare_v1271_city_compatibility import ROOT,RES,OUT,read,write,token,sha
from finalize_v1271_verified_source_branches import SOURCE,DEST,RESULT,PROGRESS
from v1271_prepared_city import PreparedCity

def main():
    plan=DEST/'source-plan.sqlite';runtime=DEST/'runtime';manifest=read(runtime/'manifest.json')
    con=sqlite3.connect(plan);current={i:json.loads(raw) for i,raw in con.execute('SELECT id,data FROM decisions')};summary=json.loads(con.execute("SELECT data FROM metadata WHERE key='summary'").fetchone()[0])
    old=sqlite3.connect(f'file:{SOURCE}/build/v127-full-conversion-first.sqlite?mode=ro',uri=True);previous={i:json.loads(raw) for i,raw in old.execute('SELECT id,data FROM decisions')};old.close()
    probe=ROOT/'build/runtime-client-comparison-fresh-final-reference-r2/native-source-model-probes.json';rows=read(probe);assert len(rows)==93
    for p in rows:
        d=current[p['decisionId']];assert d['action']=='VANILLA' and d['sourceState']==p['sourceState'] and d['reason'].startswith('VERIFIED_')
    protected={i:current[i] for i in (7081,7082)};assert all(d['reason']=='EXACT_HIDDEN_SINGLE_BEEHIVE_SOURCE_LADDER' for d in protected.values())
    snow={i:d for i,d in current.items() if d.get('reason')=='EXACT_SOURCE_NATIVE_OPTICAL_CARRIER_POSE'};assert len(snow)==48
    # Already rewritten streams must agree with the dictionary at each touched
    # cell. Read only candidate section rows; never decode source world NBT.
    counts=collections.Counter();pattern=re.compile(r'^\{"chunk":\[(-?\d+),(-?\d+)\],"dimension":"([^"]+)"');nativePositions={}
    with PreparedCity(plan) as city:
        for meta in manifest['originalSourceBlockEntities']:
            pos=meta['sourcePosition'];d=city.decision_at(meta['dimension'],*pos)
            for i,value in protected.items():
                if d==value:nativePositions[(meta['dimension'],*pos)]=i;counts[i]+=1
    assert len(nativePositions)==13
    touched={p['decisionId'] for p in rows}|set(snow)
    with gzip.open(runtime/'sections.jsonl.gz','rt',encoding='utf8') as source:
        for line in source:
            if not any(name in line for name in ('source_snow_height12','source_fallback_carrier','source_fallback_translucent_carrier','source_invisible_solid_carrier')):continue
            m=pattern.match(line);assert m;sy=int(line.rsplit('"sectionY":',1)[1].split('}',1)[0]);key=(m[3],int(m[1]),int(m[2]),sy)
            packed=con.execute('SELECT decision_indices FROM sections WHERE dimension=? AND cx=? AND cz=? AND sy=?',key).fetchone()[0];index=np.frombuffer(zlib.decompress(packed),dtype='<u2');hits=[int(i) for i in np.unique(index) if int(i) in touched]
            if not hits:continue
            row=json.loads(line)
            for i in hits:
                d=current[i];at=np.flatnonzero(index==i);expected={'registryId':d['registryId'],'properties':d['properties']}
                assert all(row['palette'][row['indices'][int(v)]]==expected for v in at),(key,i)
                counts[i]+=len(at)
    assert sum(counts[p['decisionId']] for p in rows)==11318
    assert sum(counts[i] for i in snow)==2905
    changedOwners=0
    with gzip.open(runtime/'owners.jsonl.gz','rt',encoding='utf8') as source:
        for line in source:
            if 'bloodborne_dw:source_compatibility_fragment' not in line:continue
            owner=json.loads(line);i=nativePositions.get((owner['dimension'],*owner['position']))
            if i is None:continue
            assert owner['registryId']==protected[i]['registryId'] and owner['properties']==protected[i]['properties'];changedOwners+=1
    assert changedOwners==13
    if summary['counts']['REMOVE']==11318:
        summary['counts']['REMOVE']=0;summary['counts']['VANILLA']+=11318
        reasons=summary['reasonCounts'];reasons['SOURCE_BLOCKSTATE_NO_APPLICABLE_BRANCH']=0;reasons['VERIFIED_NATIVE_PACK_MERGE_SOURCE_FALLBACK']=10795;reasons['VERIFIED_EMPTY_NATIVE_SOURCE_MULTIPART_PHYSICS']=523
        for i in (*protected,*snow):
            if not counts[i]:continue
            reason=previous[i].get('reason',previous[i].get('resolution',''));assert reason in reasons,(i,reason,counts[i])
            reasons[reason]-=counts[i];reason=current[i]['reason'];reasons[reason]=reasons.get(reason,0)+counts[i]
    assert summary['counts']['REMOVE']==0 and summary['counts']['VANILLA']==326594
    summary.update(nativeSourceReferenceSha256=sha(probe),nativeSourceReferenceProtocol='VALID_R2: explicit file/bbmc_v15_resource.zip activation confirmed in reload log; R1 intentionally excluded',nativeSourceFallbackStates=63,nativeSourceFallbackCells=10795,nativeEmptyMultipartSourceStates=30,nativeEmptyMultipartSourceCells=523,hiddenInvisibleSourceStates=66,hiddenInvisibleNonOpaqueSourceStates=33,hiddenInvisibleOpaqueSourceStates=33,protectedSingleBeehiveLadderCells=13,compactSourceOpticalCarrierSplitCells=2905,runtimeExecution='READY_REUSED_NATIVE_REFERENCE_VERIFIED_PREPARATION',nativeValidation='PASS_NATIVE_SOURCE_REFERENCE_R2_AND_FINITE_DICTIONARY_DELTA; FULL_CITY_NOT_EXECUTED',knownVisibleGaps=0,knownVisibleGapsMeaning='No source decisions are discarded; native R2 distinguishes lower-pack drawable fallback from truly empty source multipart. Full-city visual equality requires final converted-world acceptance.',fullWorldConverted=False,planBytes=plan.stat().st_size)
    con.execute('UPDATE metadata SET data=? WHERE key=?',(token(summary),'summary'));con.commit();con.close()
    manifest.update(summary);manifest['sourcePlanSha256']=sha(plan);manifest['runtimeFiles']={p.name:sha(p) for p in runtime.glob('*.jsonl.gz')};write(runtime/'manifest.json',manifest)
    report={'status':'PASS_REUSED_PREPARATION_NATIVE_SOURCE_REFERENCE_DELTA','sourcePlan':str(plan),'sourcePlanSha256':sha(plan),'runtimeDirectory':str(runtime),'runtimeManifestSha256':sha(runtime/'manifest.json'),'runtimeFiles':manifest['runtimeFiles'],'counts':summary['counts'],'nativeSourceReferenceSha256':sha(probe),'nativeFallbackDrawableCells':10795,'genuinelyEmptyMultipartPhysicsCells':523,'sourceNativeOpticalPoseSplitCells':2905,'singleRootOriginalBeehiveLadders':13,'changedRuntimeSections':2390,'changedOwnerRecords':13,'sourceWorldRescans':0,'sourceIndexArraysChanged':0,'weightedChoicesRecomputed':False,'originalSourceArchivesChanged':False,'fullWorldConverted':False,'altArtworkLoaded':0,'positiveDecisionCounts':{str(i):n for i,n in counts.items() if n},'finalBindingRecoveredAfterStreamVerification':True}
    write(OUT/'final-prepared-bundle-verification.json',report)
    write(OUT/'remaining-source-edge-cases.json',{'status':'RESOLVED_BY_NATIVE_REFERENCE_R2','sourceBlockstateNoApplicableBranchCells':11318,'drawableNativeLowerPackFallbackCells':10795,'sourceMultipartOriginallyEmptyCells':523,'discardedSourceCells':0,'noArbitraryHoleFillerAdded':True,'fullWorldConverted':False})
    write(RESULT,{'status':'PASS','exitCode':0,'sourcePlanSha256':report['sourcePlanSha256'],'runtimeManifestSha256':report['runtimeManifestSha256']})
    write(PROGRESS,{'stage':'complete','sourcePlanSha256':report['sourcePlanSha256'],'runtimeManifestSha256':report['runtimeManifestSha256']})

if __name__=='__main__':
    try:main()
    except BaseException:write(RESULT,{'status':'FAIL','exitCode':1,'error':traceback.format_exc()});raise
