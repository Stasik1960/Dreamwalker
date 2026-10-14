"""Apply native-reference-proven deltas to the reused V12.7 preparation.

No source world rescan, source choice recomputation, source archive mutation,
or per-cell block entities for native fallback/empty multipart states.
"""
import collections,copy,gzip,json,re,sqlite3,time,traceback,zlib
from pathlib import Path
import numpy as np
from prepare_v1271_city_compatibility import ROOT,RES,OUT,COMPAT,read,write,sha,token,resources
from v1271_prepared_city import PreparedCity

SOURCE=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete')
DEST=ROOT/'build/v1271-city-preparation'
RESULT=ROOT/'build/v1271-verified-branches-result.json'
PROGRESS=ROOT/'build/v1271-verified-branches-progress.json'

def progress(stage,**kw):write(PROGRESS,{'stage':stage,**kw})

def main():
    began=time.monotonic();plan=DEST/'source-plan.sqlite';runtime=DEST/'runtime'
    proof=ROOT/'build/runtime-client-comparison-fresh-final-reference-r2/native-source-model-probes.json'
    rows=read(proof);assert len(rows)==93 and sum(p['count'] for p in rows)==11318
    drawable=[p for p in rows if p['expectedNativeMergeSemantics'].startswith('VANILLA_')]
    empty=[p for p in rows if p['expectedNativeMergeSemantics']=='SOURCE_MULTIPART_NO_MATCH_EMPTY_MODEL']
    assert len(drawable)==63 and sum(p['count'] for p in drawable)==10795
    assert len(empty)==30 and sum(p['count'] for p in empty)==523
    assert all(p['actualNativeQuadCountSeed42']>0 for p in drawable)
    assert all(p['actualNativeQuadCountSeed42']==0 and p['sourceOpaque'] for p in empty)
    assert all(len(p['modelCandidatesExpected'])==1 for p in drawable),'No weighted native fallback choices may be guessed'
    progress('native proof accepted',drawableCells=10795,emptyMultipartCells=523)
    con=sqlite3.connect(plan);decisions={i:json.loads(raw) for i,raw in con.execute('SELECT id,data FROM decisions')}
    previous=copy.deepcopy(decisions);manifest=read(runtime/'manifest.json');summary=json.loads(con.execute("SELECT data FROM metadata WHERE key='summary'").fetchone()[0])
    assert summary['counts']['REMOVE']==11318,'This delta requires the accepted previous preparation'
    changes={};visibleStates=[p['sourceState'] for p in drawable]
    # Standard blockstate rotation/UVlock is baked by Minecraft itself. Models
    # and textures are copied from the same v15-over-vanilla resource lookup.
    import import_remaining_assets as assets
    exporter=assets.Source(SOURCE.parent/'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16/bbmc_v15_resource.zip',assets.VANILLA)
    variants={};surfaceFiles=[]
    for n,p in enumerate(drawable):
        app=copy.deepcopy(p['modelCandidatesExpected'][0]);app['model']=exporter.export_model(app['model'])
        assert not any(k not in {'model','x','y','uvlock'} for k in app),app
        for profile in ('base','alt'):variants[f'profile={profile},source={n}']=app
        old=previous[p['decisionId']];assert old['sourceState']==p['sourceState'] and old['reason']=='SOURCE_BLOCKSTATE_NO_APPLICABLE_BRANCH'
        changes[p['decisionId']]={'action':'VANILLA','registryId':'bloodborne_dw:source_fallback_carrier' if p['sourceOpaque'] else 'bloodborne_dw:source_fallback_translucent_carrier','properties':{'source':str(n),'profile':'base'},'sourceState':p['sourceState'],'sourceApplications':p['modelCandidatesExpected'],'reason':'VERIFIED_NATIVE_PACK_MERGE_SOURCE_FALLBACK','previousPreparationReason':old['reason'],'nativeSourceReference':{k:p[k] for k in p if k.startswith('source') and k!='sourceState'},'nativeModelReferenceQuadCountSeed42':p['actualNativeQuadCountSeed42']}
    for path in ('source_fallback_carrier','source_fallback_translucent_carrier'):
        write(RES/'assets/bloodborne_dw/blockstates'/f'{path}.json',{'variants':variants})
    write(RES/'bloodborne_dw/source-fallback-carriers-v1271.json',{'schemaVersion':1,'sourceStates':visibleStates,'nativeReferenceSha256':sha(proof),'translucentSourceStates':[p['sourceState'] for p in drawable if not p['sourceOpaque']],'policy':'Native saved MODEL states without items, owners, block entities or source callbacks. BASE and ALT share exactly the same source model; no ALT artwork loaded.','sourceModelApplications':[p['modelCandidatesExpected'][0] for p in drawable]})
    invisible=read(RES/'bloodborne_dw/invisible-source-carriers-v1271.json');assert len(invisible['sourceStates']) in (36,66)
    if len(invisible['sourceStates'])==66:
        assert invisible['sourceStates'][36:]==[p['sourceState'] for p in empty]
        invisible['sourceStates']=invisible['sourceStates'][:36];invisible['solidSourceStates']=[s for s in invisible['solidSourceStates'] if s in invisible['sourceStates']]
    for p in empty:
        state=p['sourceState'];assert state not in invisible['sourceStates'];n=len(invisible['sourceStates'])
        invisible['sourceStates'].append(state);invisible['solidSourceStates'].append(state)
        old=previous[p['decisionId']];assert old['sourceState']==state and old['reason']=='SOURCE_BLOCKSTATE_NO_APPLICABLE_BRANCH'
        changes[p['decisionId']]={'action':'VANILLA','registryId':'bloodborne_dw:source_invisible_solid_carrier','properties':{'source':str(n),'profile':'base'},'sourceState':state,'sourceApplications':[],'reason':'VERIFIED_EMPTY_NATIVE_SOURCE_MULTIPART_PHYSICS','previousPreparationReason':old['reason'],'nativeSourceReference':{k:p[k] for k in p if k.startswith('source') and k!='sourceState'},'nativeModelReferenceQuadCountSeed42':0}
    invisible['originalSourceModelVisibility']='Native reference confirms all-alpha-zero / empty authored geometry prefix36 and zero-quad source multipart append30; never replaces a drawable source model'
    invisible['nativeMultipartReferenceSha256']=sha(proof);write(RES/'bloodborne_dw/invisible-source-carriers-v1271.json',invisible)
    debug=read(RES/'bloodborne_dw/debug_catalogue.json')
    for number,path,name in [('93237','source_fallback_carrier','Исходный резервный носитель'),('93238','source_fallback_translucent_carrier','Исходный прозрачный резервный носитель')]:
        registry='bloodborne_dw:'+path;existing=[e for e in debug['entries'] if e['temporaryId']==number or e['registryId']==registry]
        if existing:assert len(existing)==1 and existing[0]['temporaryId']==number and existing[0]['registryId']==registry
        else:debug['entries'].append({'temporaryId':number,'finalId':None,'kind':'compatibility','name':name,'registryId':registry,'aliases':[registry]})
    write(RES/'bloodborne_dw/debug_catalogue.json',debug)
    progress('native fallback resources exported',modelFiles=len(exporter.exported),sourceStates=63,invisibleStates=66)
    # The thirteen beehive-backed ladders are standalone. The old source_clone
    # field instead creates a paired construction, so retain the original mesh
    # in hidden single-root poses with its own beehive optics and inert NBT.
    protectedIds=(7081,7082);protected=[previous[i] for i in protectedIds]
    assert all(d.get('reason',d.get('resolution'))=='EXACT_PROTECTED_NATIVE_SOURCE_LADDER_BLOCKSTATE_MODEL' for d in protected)
    mappings=resources(SOURCE,include_catalogue_exclusions=True,additional_decisions=protected)
    for i in protectedIds:
        old=previous[i];d=copy.deepcopy(mappings[token([old['sourceState'],old['sourceApplications']])]);d['previousPreparationReason']=old.get('reason',old.get('resolution'));d['reason']='EXACT_HIDDEN_SINGLE_BEEHIVE_SOURCE_LADDER';changes[i]=d
    ladderPositions={}
    with PreparedCity(plan) as city:
        for meta in manifest['originalSourceBlockEntities']:
            dim=meta['dimension'];pos=meta['sourcePosition'];d=city.decision_at(dim,*pos)
            for i in protectedIds:
                if d==previous[i]:ladderPositions[(dim,*pos)]=i
    assert len(ladderPositions)==13,('Every protected original beehive ladder has typed metadata',len(ladderPositions))
    compact=read(ROOT/'docs/final-city-repair/geometry/compact-optical-pose-mapping.json');assert compact['occurrences']==2905
    byState={m['sourceState']:m for m in compact['mappings']};snowIds={}
    for i,old in previous.items():
        p=old.get('properties',{});pose=int(p.get('variant',0))+16*int(p.get('art_bank',0))
        if old.get('action')=='COMPACT' and old.get('registryId')==compact['registryId'] and pose==3 and old['sourceState'] in byState:
            m=byState[old['sourceState']];d=copy.deepcopy(old);d['properties'].update(m['properties']);d['reason']='EXACT_SOURCE_NATIVE_OPTICAL_CARRIER_POSE';d['previousPreparationReason']=old.get('reason');changes[i]=d;snowIds[i]=m
    assert {previous[i]['sourceState'] for i in snowIds}==set(byState),(len(snowIds),byState)
    # Coordinates of the former removed states already exist in V12.7's audit.
    # Read this small immutable stream, not the original world or all SQL arrays.
    bySource={p['sourceState']:p['decisionId'] for p in rows};sections=collections.defaultdict(dict);fallbackCells=0
    with gzip.open(ROOT/'docs/v12.7/conversion/source-removal-coordinates.jsonl.gz','rt',encoding='utf8') as original:
        for line in original:
            r=json.loads(line)
            if r['reason']!='SOURCE_BLOCKSTATE_NO_APPLICABLE_BRANCH':continue
            i=bySource.get(r['sourceState']);assert i is not None,r['sourceState']
            if 'position' in r:
                x,y,z=r['position'];at=((y&15)<<8)|((z&15)<<4)|(x&15);sections[(r['dimension'],x>>4,z>>4,y>>4)][at]=i;fallbackCells+=1
            else:
                cx,cz=r['chunk'];key=(r['dimension'],cx,cz,r['sectionY'])
                for start,length in r['linearRuns']:
                    for at in range(start,start+length):sections[key][at]=i;fallbackCells+=1
    assert fallbackCells==11318,fallbackCells
    for (dim,x,y,z),i in ladderPositions.items():sections[(dim,x>>4,z>>4,y>>4)][((y&15)<<8)|((z&15)<<4)|(x&15)]=i
    # Commit dictionary only. All original source, palette and decision index
    # arrays stay byte-identical; native target palette IDs are derived below.
    for i,d in changes.items():con.execute('UPDATE decisions SET data=? WHERE id=?',(token(d),i))
    con.commit();decisions.update(changes)
    target=runtime/'sections.native-verified.jsonl.gz';counts=collections.Counter();changedSections=0;seenSections=set()
    pattern=re.compile(r'^\{"chunk":\[(-?\d+),(-?\d+)\],"dimension":"([^"]+)"')
    with gzip.open(runtime/'sections.jsonl.gz','rt',encoding='utf8') as src,gzip.open(target,'wt',encoding='utf8') as out:
        for rowNumber,line in enumerate(src,1):
            found=pattern.match(line);assert found,'Unexpected canonical section header'
            # The section Y is after indices/palette in canonical token order;
            # extracting it from the tail avoids parsing 861,360 4096-cell rows.
            sy=int(line.rsplit('"sectionY":',1)[1].split('}',1)[0]);key=(found[3],int(found[1]),int(found[2]),sy)
            if key not in sections and 'bloodborne_dw:source_snow_height12' not in line:out.write(line);continue
            row=json.loads(line);raw=con.execute('SELECT decision_indices FROM sections WHERE dimension=? AND cx=? AND cz=? AND sy=?',key).fetchone();assert raw
            index=np.frombuffer(zlib.decompress(raw[0]),dtype='<u2');hits=set(sections.get(key,{}).values())|set(int(i) for i in np.unique(index) if int(i) in snowIds)
            if not hits:out.write(line);continue
            ids={token(s):n for n,s in enumerate(row['palette'])};changed=False
            for i in hits:
                d=decisions[i];positions=np.flatnonzero(index==i)
                if i in bySource.values() or i in protectedIds:
                    expected={at for at,di in sections[key].items() if di==i};assert set(map(int,positions))==expected,(key,i,len(positions),len(expected))
                state={'registryId':d['registryId'],'properties':d['properties']} if d['action']!='OWNER' else {'registryId':'minecraft:air','properties':{}}
                tag=token(state)
                if tag not in ids:ids[tag]=len(row['palette']);row['palette'].append(state)
                for at in positions:row['indices'][int(at)]=ids[tag]
                counts[i]+=len(positions);changed=True
            if key in sections:seenSections.add(key)
            changedSections+=changed;out.write(token(row)+'\n' if changed else line)
            if rowNumber%10000==0:progress('rewrite native target palettes',streamRows=rowNumber,changedSections=changedSections)
    assert seenSections==set(sections),(len(seenSections),len(sections))
    assert sum(counts[p['decisionId']] for p in rows)==11318
    assert sum(counts[i] for i in protectedIds)==13
    assert sum(counts[i] for i in snowIds)==2905
    target.replace(runtime/'sections.jsonl.gz');progress('native palettes rewritten',changedSections=changedSections)
    # Rewrite only thirteen old owner records; source UUID/XYZ remain stable.
    target=runtime/'owners.native-verified.jsonl.gz';ownerChanged=0
    with gzip.open(runtime/'owners.jsonl.gz','rt',encoding='utf8') as src,gzip.open(target,'wt',encoding='utf8') as out:
        for line in src:
            if 'bloodborne_dw:prototype_ladder' not in line:out.write(line);continue
            owner=json.loads(line);i=ladderPositions.get((owner['dimension'],*owner['position']))
            if i is None:out.write(line);continue
            d=changes[i];owner['registryId']=d['registryId'];owner['properties']=d['properties'];owner['payload']={**owner.get('payload',{}),**d['payload']};out.write(token(owner)+'\n');ownerChanged+=1
    assert ownerChanged==13,ownerChanged;target.replace(runtime/'owners.jsonl.gz')
    oldCounts=summary['counts'];oldCounts['REMOVE']=0;oldCounts['VANILLA']+=11318
    reasons=summary['reasonCounts'];reasons['SOURCE_BLOCKSTATE_NO_APPLICABLE_BRANCH']=0;reasons['VERIFIED_NATIVE_PACK_MERGE_SOURCE_FALLBACK']=10795;reasons['VERIFIED_EMPTY_NATIVE_SOURCE_MULTIPART_PHYSICS']=523
    for i in (*protectedIds,*snowIds):
        old=previous[i];reason=old.get('reason',old.get('resolution',''))
        if not counts[i]:continue
        reasons[reason]=reasons.get(reason,0)-counts[i];reasons[changes[i]['reason']]=reasons.get(changes[i]['reason'],0)+counts[i]
    summary.update(nativeSourceReferenceSha256=sha(proof),nativeSourceReferenceProtocol='VALID_R2: explicit file/bbmc_v15_resource.zip activation confirmed in reload log; R1 intentionally excluded',nativeSourceFallbackStates=63,nativeSourceFallbackCells=10795,nativeEmptyMultipartSourceStates=30,nativeEmptyMultipartSourceCells=523,hiddenInvisibleSourceStates=66,hiddenInvisibleNonOpaqueSourceStates=33,hiddenInvisibleOpaqueSourceStates=33,protectedSingleBeehiveLadderCells=13,compactSourceOpticalCarrierSplitCells=2905,runtimeExecution='READY_REUSED_NATIVE_REFERENCE_VERIFIED_PREPARATION',nativeValidation='PASS_NATIVE_SOURCE_REFERENCE_R2_AND_FINITE_DICTIONARY_DELTA; FULL_CITY_NOT_EXECUTED',knownVisibleGaps=0,knownVisibleGapsMeaning='No source decisions are discarded; native R2 distinguishes lower-pack drawable fallback from truly empty source multipart. Full-city visual equality requires final converted-world acceptance.',fullWorldConverted=False)
    summary['planBytes']=plan.stat().st_size
    con.execute('UPDATE metadata SET data=? WHERE key=?',(token(summary),'summary'));con.commit();con.close()
    manifest.update(summary);manifest['sourcePlanSha256']=sha(plan);manifest['runtimeFiles']={p.name:sha(p) for p in runtime.glob('*.jsonl.gz')};write(runtime/'manifest.json',manifest)
    report={'status':'PASS_REUSED_PREPARATION_NATIVE_SOURCE_REFERENCE_DELTA','sourcePlan':str(plan),'sourcePlanSha256':sha(plan),'runtimeDirectory':str(runtime),'runtimeManifestSha256':sha(runtime/'manifest.json'),'runtimeFiles':manifest['runtimeFiles'],'counts':summary['counts'],'nativeSourceReferenceSha256':sha(proof),'nativeFallbackDrawableCells':10795,'genuinelyEmptyMultipartPhysicsCells':523,'sourceNativeOpticalPoseSplitCells':2905,'singleRootOriginalBeehiveLadders':13,'changedRuntimeSections':changedSections,'changedOwnerRecords':13,'additionalBlockEntitiesFromNativeCompatibility':0,'sourceWorldRescans':0,'sourceIndexArraysChanged':0,'weightedChoicesRecomputed':False,'originalSourceArchivesChanged':False,'fullWorldConverted':False,'altArtworkLoaded':0,'elapsedSeconds':round(time.monotonic()-began,2),'exportedSourceFiles':exporter.exported}
    write(OUT/'final-prepared-bundle-verification.json',report)
    write(OUT/'remaining-source-edge-cases.json',{'status':'RESOLVED_BY_NATIVE_REFERENCE_R2','sourceBlockstateNoApplicableBranchCells':11318,'drawableNativeLowerPackFallbackCells':10795,'sourceMultipartOriginallyEmptyCells':523,'discardedSourceCells':0,'noArbitraryHoleFillerAdded':True,'fullWorldConverted':False})
    progress('complete',sourcePlanSha256=report['sourcePlanSha256'],runtimeManifestSha256=report['runtimeManifestSha256'],elapsedSeconds=report['elapsedSeconds'])
    return report

if __name__=='__main__':
    try:report=main();write(RESULT,{'status':'PASS','exitCode':0,'sourcePlanSha256':report['sourcePlanSha256'],'runtimeManifestSha256':report['runtimeManifestSha256']})
    except BaseException:write(RESULT,{'status':'FAIL','exitCode':1,'error':traceback.format_exc()});raise
