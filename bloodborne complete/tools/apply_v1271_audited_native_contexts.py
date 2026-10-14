"""Apply the native-audited80 fixed context decisions to reused preparation."""
import collections,copy,gzip,json,re,shutil,sqlite3,traceback,zlib
import numpy as np
from prepare_v1271_city_compatibility import ROOT,RES,OUT,read,write,sha,token

DEST=ROOT/'build/v1271-city-preparation';RESULT=ROOT/'build/v1271-audited-contexts-apply-result.json'

def main():
    plan=DEST/'source-plan.sqlite';runtime=DEST/'runtime';mappingPath=ROOT/'build/final-city-fixes/planned-native-optical-context-mapping.json';planned=read(mappingPath)
    auditPath=ROOT/'build/gametest-final-city-fixes-r2/world/v1271-compact-native-flag-proof.json';audit=read(auditPath)
    assert audit['status'].startswith('PASS_ALL_REACHABLE_COMPACT_NATIVE_') and audit['targetCases']==1814
    assert audit['nativeDecodeFailures']==0 and audit['criticalDifferenceGroups']==0 and audit['criticalDifferenceOccurrences']==0
    assert audit['input']['plannedMappingSha256']==sha(mappingPath) and audit['input']['sourcePlanSha256']==planned['preparedSourcePlanSha256']==sha(plan)
    assert planned['sourceConfigurationSha256']==sha(RES/'bloodborne_dw/source-fallback-carriers-v1271.json') and planned['sourceCells']==348528
    frequency=read(DEST/'decision-frequency-proof.json')['decisionCellCounts'];con=sqlite3.connect(plan);decisions={i:json.loads(raw) for i,raw in con.execute('SELECT id,data FROM decisions')};summary=json.loads(con.execute("SELECT data FROM metadata WHERE key='summary'").fetchone()[0]);manifest=read(runtime/'manifest.json')
    changes={};previous={};prefix=[]
    for rawId,target in planned['mappings'].items():
        i=int(rawId);old=decisions[i];assert old['action']=='COMPACT' and old['registryId']==target['previousRegistryId'] and old['properties']==target['previousProperties']
        assert old['sourceApplications']==target['sourceApplications'] and old['sourceState']==target['sourceState']
        d=copy.deepcopy(old);d.update(action='VANILLA',registryId=target['registryId'],properties=target['properties'],payload={},reason=target['reason'],previousPreparationReason=old.get('reason',old.get('resolution','')),sourceContextIndex=target['sourceContextIndex']);changes[i]=d;previous[i]=old
    assert len(changes)==80 and sum(frequency[str(i)] for i in changes)==348528
    # Save existing source indices explicitly in bank0. Legacy palettes without
    # the new property still decode to bank0; this normalization makes all
    # prepared native target states match the final fixture/property contract.
    for i,d in decisions.items():
        if str(d.get('registryId','')).startswith('bloodborne_dw:source_fallback_'):
            assert int(d['properties']['source'])<77 and 'source_bank' not in d['properties']
            d=copy.deepcopy(d);d['properties']['source_bank']='0';con.execute('UPDATE decisions SET data=? WHERE id=?',(token(d),i));decisions[i]=d;prefix.append(i)
    assert len(prefix)==77
    for i,d in changes.items():con.execute('UPDATE decisions SET data=? WHERE id=?',(token(d),i))
    con.commit();decisions.update(changes)
    names=tuple({d['registryId'] for d in previous.values()});header=re.compile(r'^\{"chunk":\[(-?\d+),(-?\d+)\],"dimension":"([^"]+)"')
    bank0=re.compile(r'(\{"properties":\{"profile":"(?:base|alt)","source":"\d+")(\},"registryId":"bloodborne_dw:source_fallback_[^"]+"\})')
    target=runtime/'sections.fixed-native-contexts.jsonl.gz';counts=collections.Counter();changed=0;normalized=0
    with gzip.open(runtime/'sections.jsonl.gz','rt',encoding='utf8') as src,gzip.open(target,'wt',encoding='utf8') as out:
        for line in src:
            if 'bloodborne_dw:source_fallback_' in line:
                line,n=bank0.subn(r'\1,"source_bank":"0"\2',line);normalized+=n
            if not any(name in line for name in names):out.write(line);continue
            m=header.match(line);assert m;sy=int(line.rsplit('"sectionY":',1)[1].split('}',1)[0]);key=(m[3],int(m[1]),int(m[2]),sy)
            packed=con.execute('SELECT decision_indices FROM sections WHERE dimension=? AND cx=? AND cz=? AND sy=?',key).fetchone()[0];indices=np.frombuffer(zlib.decompress(packed),dtype='<u2');hits=[int(i) for i in np.unique(indices) if int(i) in changes]
            if not hits:out.write(line);continue
            row=json.loads(line);pids={token(state):n for n,state in enumerate(row['palette'])}
            for i in hits:
                d=changes[i];at=np.flatnonzero(indices==i);oldState={'registryId':previous[i]['registryId'],'properties':previous[i]['properties']};assert all(row['palette'][row['indices'][int(p)]]==oldState for p in at),(key,i)
                state={'registryId':d['registryId'],'properties':d['properties']};text=token(state)
                if text not in pids:pids[text]=len(row['palette']);row['palette'].append(state)
                for p in at:row['indices'][int(p)]=pids[text]
                counts[i]+=len(at)
            changed+=1;out.write(token(row)+'\n')
    assert sum(counts.values())==348528 and all(counts[i]==frequency[str(i)] for i in changes),counts
    target.replace(runtime/'sections.jsonl.gz')
    # Independent all-ID frequencies include zero-use historical dictionary
    # records; classification here cannot turn an unused REMOVE into a hole.
    actions=collections.Counter();reasons=collections.Counter();types=collections.Counter()
    for i,d in decisions.items():
        n=frequency[str(i)];actions[d['action']]+=n;reasons[d.get('reason',d.get('resolution',''))]+=n
        if d['action'] in {'COMPACT','OWNER','VANILLA'} and d.get('registryId'):types[d['registryId']]+=n
    assert actions['REMOVE']==0 and actions['COMPACT']==54779799 and actions['VANILLA']==715257 and sum(actions.values())==3528130560
    summary.update(counts=dict(actions),reasonCounts=dict(reasons),targetTypeCounts=dict(types),nativeSourceFallbackStates=156,nativeSourceContextPrefix=77,nativeSourceBankSize=128,nativeSourceBankCount=2,nativeFrozenOpticalContextStates=79,nativeFrozenOpticalContextCells=348528,nativeFrozenOpticalContextDecisionRecords=80,nativeFrozenOpticalContextAuditStatus='PASS',nativeCompactFlagAuditTargets=1814,nativeCompactFlagAuditCriticalDifferences=0,nativeCompactFlagAuditDecodeFailures=0,nativeCompactFlagAuditSha256=sha(auditPath),nativeCompactFlagAuditOriginalPlanSha256=planned['preparedSourcePlanSha256'],nativeCompactFlagAuditPlannedMappingSha256=sha(mappingPath),nativeSourceFallbackConfigurationSha256=sha(RES/'bloodborne_dw/source-fallback-carriers-v1271.json'),allDecisionFrequencyProofSha256=sha(DEST/'decision-frequency-proof.json'),allDecisionIndexArraysSha256=read(DEST/'decision-frequency-proof.json')['decisionIndexArraysSha256'],nativeValidation='PASS_VALID_ORIGINAL_V15_NATIVE_R2_SOURCE_MODELS_AND_EXHAUSTIVE_1814_NATIVE_CRITICAL_FLAGS; FULL_CITY_NOT_EXECUTED',runtimeExecution='READY_REUSED_NATIVE_REFERENCE_VERIFIED_PREPARATION',planBytes=plan.stat().st_size)
    summary['knownVisibleGapsMeaning']='No actually used source decision is discarded. Native original v15 merge/critical optics are audited; documented current physical/culling simplifications remain. Full-city converted-world visual acceptance is a separate stage.'
    con.execute('UPDATE metadata SET data=? WHERE key=?',(token(summary),'summary'));con.commit();con.close();manifest.update(summary);manifest['sourcePlanSha256']=sha(plan);manifest['runtimeFiles']={p.name:sha(p) for p in runtime.glob('*.jsonl.gz')};write(runtime/'manifest.json',manifest)
    shutil.copyfile(auditPath,OUT/'native-compact-flag-proof.json')
    report=read(OUT/'final-prepared-bundle-verification.json');report.update(sourcePlanSha256=sha(plan),runtimeManifestSha256=sha(runtime/'manifest.json'),runtimeFiles=manifest['runtimeFiles'],counts=dict(actions),nativeFallbackSourceStates=156,nativeFrozenOpticalContextCells=348528,nativeFrozenOpticalContextStates=79,nativeFrozenOpticalContextDecisionRecords=80,nativeFrozenOpticalContextSectionRowsChanged=changed,explicitBank0PaletteEntriesNormalized=normalized,nativeCompactFlagAuditTargets=1814,nativeCompactFlagAuditCriticalDifferences=0,nativeCompactFlagAuditSha256=sha(auditPath),sourceFallbackConfigurationSha256=summary['nativeSourceFallbackConfigurationSha256'],allDecisionIndexArraysSha256=summary['allDecisionIndexArraysSha256'],nativeShapeSimplificationsRecordedIn='native-compact-flag-proof.json',additionalOwnerRecordsFromOpticalContexts=0,additionalBlockEntitiesFromOpticalContexts=0);write(OUT/'final-prepared-bundle-verification.json',report)
    write(OUT/'audited-fixed-native-contexts-applied.json',{'status':'PASS_NATIVE_AUDITED_FIXED_CONTEXT_DELTA_REUSED_SOURCE_PREPARATION','sourceCells':348528,'nativeSourceContexts':156,'appendedContexts':79,'preparedDecisionRecords':80,'sourcePlanSha256':sha(plan),'runtimeManifestSha256':sha(runtime/'manifest.json'),'nativeAuditSha256':sha(auditPath),'nativeAuditTargets':1814,'criticalDifferences':0,'sourceWorldRescans':0,'decisionArraysChanged':0,'weightedChoicesRecomputed':False,'newOwnerRecords':0,'newBlockEntities':0,'physicalAndOutlinePolicy':'Preserve copied previous COMPACT boxes; native source optics and culling delegate separately','sourceArchiveBytesChanged':False,'fullCityConverted':False})
    write(RESULT,{'status':'PASS','sourcePlanSha256':sha(plan),'runtimeManifestSha256':sha(runtime/'manifest.json'),'counts':dict(actions),'sourceCells':348528})

if __name__=='__main__':
    try:main()
    except BaseException:write(RESULT,{'status':'FAIL','error':traceback.format_exc()});raise
