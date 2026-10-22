"""Recover only the proved completed427 bind after its dimension-ledger typo.

No chunk/state/BE proof is fabricated. The actual failed completed-loop evidence,
the prior full audit/restoration chain and unchanged exact region hashes replace
the lost in-memory chunk digest dictionary. This writes metadata only.
"""
import argparse,collections,hashlib,json,sqlite3,traceback
from pathlib import Path
import migrate as m
import context_stage as c

OLD_CONTEXT_SHA='349793539b7e168af6c6568afa1e1f9a8b4ae2dfc09c00f492a3457cfce016b7'
MIGRATE_SHA='0e2534920f65b132fec6eb69fb8186c8a026c1405cde54affbd7f63ef7ed322e'
FAILURE_NAME='20261022T043105-migrate'

def expected_ledger(rel,source_hash,old_files,pilot,repair_world,repair_global):
    expected=old_files.get(rel,source_hash)
    if rel not in old_files and pilot and rel==pilot['globalLedger']['path']:expected=pilot['globalLedger']['beforeCleanupSha256']
    if repair_world:
        if rel in repair_world['existingShards']:expected=repair_world['existingShards'][rel].get('repairedAfterSha256',repair_world['existingShards'][rel]['beforeSha256'])
        elif rel=='data/bloodborne_dw_composite_owners.dat':expected=repair_global
    return expected

def recover(plan):
    db=m.connect(plan)
    try:
        extension=c.receipt(m,db);target=m.checked(m.meta(db,'target'));m.require(extension and not m.meta(db,'applyStarted'),'Recover only the same unapplied context binding')
        m.require(c.native_light_restart(m,db),'Conservative complete-domain light debt requires genuine fresh-light restart')
        old_source=plan/'context-extension/context_stage.before-dimension-fix.py';before=old_source.read_text(encoding='utf-8');after=Path(c.__file__).read_text(encoding='utf-8')
        m.require(m.sha(old_source)==OLD_CONTEXT_SHA and m.sha(m.__file__)==MIGRATE_SHA,'Exact frozen failed-run source binding changed')
        old_line="elif global_ledger:expected=repair_global";new_line="elif rel=='data/bloodborne_dw_composite_owners.dat':expected=repair_global"
        m.require(before.count(old_line)==1 and after==before.replace(old_line,new_line),'Recovery permits only the proved dimension condition change')
        failure=m.ROOT/'research/processes'/Path(FAILURE_NAME+'.json');run=m.read(failure);log=Path(run['log']);trace=log.read_text(encoding='utf-8')
        m.require(run['status']=='FAIL' and run['exitCode']==1 and run['args']==['bind-context-copy','--target','../research/pilot/candidate'] and Path(run['script']).resolve()==Path(m.__file__).resolve(),'Wrong failed actual bind invocation')
        m.require('context_stage.py", line 507, in bind_copy' in trace and trace.rstrip().endswith('RuntimeError: Prior-stage ledger differs from its exact committed after bytes: DIM-1/data/bloodborne_dw_composite_owners.dat'),'Actual invocation did not fail at the proved post-loop dimension ledger guard')
        archived_progress=plan/'context-extension/completed-bind-recovery/progress.json';progress=archived_progress if archived_progress.exists() else plan/'progress.json';progress_doc=m.read(progress);m.require(progress_doc['phase']=='BINDING_CONTEXT_CONTENT' and progress_doc['verifiedChunks']==427 and progress_doc['lastRegion']=='region/r.2.0.mca','The actual complete427 loop completion evidence is absent')
        repair=c.dependency_repair(m,db);m.require(repair and m.sha(repair['audit']['path'])==repair['audit']['sha256'],'Accepted427 restoration audit changed');audit=m.read(repair['audit']['path']);m.require(audit['status']=='PASS' and not audit['errors'] and audit['sourceBinding']==m.meta(db,'binding'),'Original427 proof chain changed')
        rich=m.read(repair['repairPlan']);m.require(rich['contextSha256']==OLD_CONTEXT_SHA and rich['migrationSha256']==MIGRATE_SHA,'Failed run lacks the preserved reviewed source basis')
        for operation in repair['operations']:
            actual=Path(repair['worlds'][operation['variant']]['target'])/operation['path'];m.require(m.sha(actual)==operation['afterSha256'] and m.sha(operation['staged'])==operation['afterSha256'],'A committed/restored repair input changed after the completed actual bind')
        candidate=repair['worlds']['candidate'];after_regions={v['path']:v['afterSha256'] for v in repair['operations'] if v['variant']=='candidate' and v['kind']=='region'}
        allowed=set(db.execute('SELECT cx,cz FROM context_allowed_chunks'));m.require(len(allowed)==427,'Original427 domain changed');groups=collections.defaultdict(list)
        for cx,cz in allowed:
            m.require(db.execute("SELECT 1 FROM old.chunk_cache WHERE dim='.' AND x=? AND z=?",(cx,cz)).fetchone(),'A completed actual chunk lost its immutable cache address')
            groups['region/r.%d.%d.mca'%(cx>>5,cz>>5)].append((cx,cz))
        regions={}
        for rel in sorted(groups):
            expected=after_regions.get(rel,candidate['originalRegions'].get(rel));m.require(expected and m.sha(target/rel)==expected,'A completed427 actual region changed after its admitted repair proof');regions[rel]=expected
        final=c._final_input_gate(m,db,plan);c.catalog_prefix(m,db,m.meta(db,'catalog')['path']);c.check_complete(m,db)
        fingerprint=c._content_fingerprint(m,db);m.require(fingerprint==rich['plannedContentFingerprint'],'Planned final content changed since the repaired actual bind')
        copy_path=target.parent/(target.name+'-copy.json');m.require(m.read(copy_path)['selectedChunkSetSha256']==extension['allowedChunkSetSha256'],'Original copied selection domain changed')
        manifest=m.read(m.meta(db,'binding')['copyManifest']);old_meta=m.read(Path(extension['history'])/'meta.json');old_files={path:after for path,after,phase in db.execute('SELECT path,after_hash,phase FROM stage1_files') if phase=='committed' and not path.startswith('relight-cache/')}
        repair_global=next(v['afterSha256'] for v in repair['operations'] if v['variant']=='candidate' and v['path']=='data/bloodborne_dw_composite_owners.dat');ledgers={}
        for rel,row in manifest['files'].items():
            path=target/rel;dictionary=path.parent.name=='bloodborne_dw_owner_shards' and path.suffix=='.nbt';global_ledger=path.name=='bloodborne_dw_composite_owners.dat' and path.parent.name=='data'
            if not path.is_file() or not(dictionary or global_ledger):continue
            expected=expected_ledger(rel,row['sha256'],old_files,old_meta.get('pilotMetadataBinding'),candidate,repair_global);actual=m.sha(path);m.require(actual==expected,'A dimension/source ledger differs from its exact independently scoped before bytes: '+rel);ledgers[rel]={'sha256':actual,'sourceSha256':row['sha256'],'proof':'exact_dimension_scoped_committed_or_repair_bytes'}
        m.require(sum(Path(v).parent.name=='bloodborne_dw_owner_shards' for v in ledgers)==repair['requiredSourceShardCount'],'Exact full source shard set changed')
        folder=plan/'context-extension/completed-bind-recovery';folder.mkdir(parents=True,exist_ok=True);evidence={}
        for name,path in (('failure',failure),('console',log),('progress',progress),('source',old_source)):
            dest=folder/(name+path.suffix)
            if dest.exists():m.require(m.sha(dest)==m.sha(path),'Frozen actual completion evidence changed')
            else:
                with path.open('rb') as source,dest.open('wb') as output:output.write(source.read());output.flush();m.os.fsync(output.fileno())
            evidence[name]={'path':str(dest),'sha256':m.sha(dest)}
        transition={'schemaVersion':1,'status':'PASS','kind':'ONE_LINE_DIMENSION_LEDGER_OVERRIDE_SCOPE_FIX','beforeSource':{'path':str(old_source),'sha256':OLD_CONTEXT_SHA},'afterSource':{'path':str(Path(c.__file__).resolve()),'sha256':m.sha(c.__file__)},'migrationSha256':MIGRATE_SHA,'onlyChange':{'before':old_line,'after':new_line},'actualCompleted427Evidence':evidence,'repair':m.meta(db,'contextDependencyRepair'),'audit':repair['audit'],'regions':regions,'priorMeasuredChunkDigestDictionaryLostWithProcess':True,'chunkAndBERechecksPerformed':False,'mapWrites':0}
        proof_path=folder/'receipt.json';m.atomic_json(proof_path,transition);proof_ref={'path':str(proof_path),'sha256':m.sha(proof_path)}
        chunks={str(cx)+','+str(cz):{'proof':'ACTUAL_COMPLETED427_LOOP_AND_UNCHANGED_REPAIRED_REGION_BYTES','recoveryReceipt':proof_ref} for cx,cz in sorted(allowed)}
        report={'schemaVersion':1,'status':'PASS','kind':'EXACT_PRIOR_STAGE_IN_PLACE_CONTEXT_BINDING','plan':str(plan),'target':str(target),'contextExtensionSha256':m.meta(db,'contextExtension')['sha256'],'allowedChunkSetSha256':extension['allowedChunkSetSha256'],'catalog':m.meta(db,'catalog'),'compositionGeneration':m.meta(db,'compositionGeneration'),'finalNativeInputGate':final,'plannedContentFingerprint':fingerprint,'predictedChangedChunks':[['.',cx,cz] for cx,cz in sorted(allowed)],'predictedChangedChunksPolicy':'CONSERVATIVE_ALL427_DEBT_NOT_GEOMETRY_SKIP','regions':regions,'chunks':chunks,'originalAbsentChunks':[],'ledgers':ledgers,'dependencyRepair':m.meta(db,'contextDependencyRepair'),'archivedPlacementProof':True,'unknownBlockEntityFieldsPreserved':True,'completedActualBindRecovery':proof_ref,'mapWrites':0}
        output=plan/'context-extension/in-place-binding.json';m.atomic_json(output,report)
        for rel, in db.execute('SELECT path FROM regions').fetchall():m.require(rel in regions,'Planned region exceeds exact completed427 region domain');db.execute('UPDATE regions SET before_hash=? WHERE path=?',(regions[rel],rel))
        m.meta(db,'contextRegionBinding',{'path':str(output),'sha256':m.sha(output),'target':str(target)});db.commit();m.progress(plan,db,'CONTEXT_IN_PLACE_CONTENT_BOUND',verifiedChunks=427,ledgerFiles=len(ledgers),recoveredCompletedActualBind=True);print(m.canonical({'status':'PASS','binding':str(output),'sha256':m.sha(output),'ledgers':len(ledgers),'regions':len(regions),'recovery':proof_ref,'mapWrites':0}))
    finally:db.close()
if __name__=='__main__':
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--plan',type=Path,default=m.ROOT/'research/native-grid-plan');args=ap.parse_args();m.control_check()
    try:recover(m.checked(args.plan))
    except BaseException:m.atomic_json(m.checked(args.plan)/'context-extension/completed-bind-recovery-error.json',{'status':'FAIL','error':traceback.format_exc(),'mapWrites':0});raise
