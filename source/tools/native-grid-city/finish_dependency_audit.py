"""Finish only 3 blocked slots/5 shards using exact finite native provenance."""
import collections,copy,shutil
import pilot_dependencies as p
from probe_dependency_shards import semantic

def main():
    m=p.m;path=p.REPAIR/'audit.json';report=m.read(path);m.require(report['status']=='BLOCKED_UNEXPECTED_INPUTS','Expected the preserved eight finite blockers')
    saved=p.REPAIR/'audit-blocked-native-prune.json'
    if saved.exists():m.require(m.sha(saved)==m.sha(path),'Original blocked audit changed')
    else:shutil.copyfile(path,saved)
    dependency=p.m.ROOT/'research/native-functional-bases/static-component-dependency-proof-v2.json';dep=m.read(dependency)
    m.require(dep['status']=='PASS' and dep['depsComplete'] is True and not dep['additionalChunksRequired'] and dep['sourceBinding']==report['sourceBinding'] and dep['inventory']['sha256']==p.INVENTORY_SHA,'Exact old component dependency proof incomplete')
    m.require(m.sha(dep['inputProof']['path'])==dep['inputProof']['sha256'],'Native finite removal provenance changed');native=m.read(dep['inputProof']['path'])
    for ref in native['inputDiffs']:m.require(m.sha(ref['path'])==ref['sha256'],'Addressed native state-diff evidence changed')
    m.require(all(row['rootChunkCoveredByOriginalOrAux'] and row['rootResidentMatchesPose'] and row['completeFootprintCovered'] and row['exactRemovalClosureProven'] for row in dep['states']),'Native state lacks complete exact source component')
    states=native['stateChanges'];m.require(len(states)==22 and len({tuple(row['position']) for row in dep['states']})==22,'Unexpected finite old component state set')
    for row in states:m.require(row['exactFiniteRemovalClosureMatches'],'Native recipe change does not match finite source removal')
    db=p.c.attached_readonly(m,p.PLAN)
    try:
        inventory=m.read(p.INVENTORY);outside={r['uuid']:r for r in inventory['owners'] if r['rootOutsideCopy']};target=p.Path(report['worlds']['candidate']['target']);bychunk=collections.defaultdict(list)
        for row in states:at=row['position'];bychunk[(at[0]>>4,at[2]>>4)].append(row)
        m.require(set(bychunk)=={(18,-57),(18,-56),(18,-55)},'Native restoration affected another chunk')
        for (cx,cz),rows in sorted(bychunk.items()):
            rel='region/r.%d.%d.mca'%(cx>>5,cz>>5);m.require(m.sha(target/rel)==report['worlds']['candidate']['regions'][rel],'Target changed since original427 audit');record=p.read_slot(target/rel,cx,cz);actual=m.compound(p.chunk_decode(record[0],record[1]).root);source=p.c._original_chunk(m,db,'.',cx,cz);proof=p.audit_chunk(db,'candidate',cx,cz,source,actual,outside,rows)
            m.require(not any(item['chunk']==[cx,cz] for item in report['worlds']['candidate']['chunks']),'A blocked chunk was already accepted');report['worlds']['candidate']['chunks'].append(proof)
        shard_path=p.REPAIR/'shard-semantic-difference.json';shard_proof=m.read(shard_path);approved=set();restored=0
        for row in shard_proof['shards']:
            m.require(row['otherTypedFieldsEqual'] and row['allLostOwnersExactOutside960'] and not row['addedOrChangedCells'] and not row['lostMembers'] and not row['addedOrChangedMembers'],'Shard changes exceed exact missing outside-root contributions')
            rel=row['path'];m.require(m.sha(target/rel)==row['actualSha256'],'Pruned shard changed after semantic evidence');expected=m.read_nbt(p.Path(report['sourceBinding']['source'])/rel);m.cleanup_dictionary(p.PriorCleanup(db),'.',expected);actual=m.read_nbt(target/rel);proof=p.restore_contributions(copy.deepcopy(actual),expected,set(outside));m.require(proof['restoredContributions']==len(row['lostCells']),'Lost contribution count changed');restored+=proof['restoredContributions'];approved.add(rel)
            item=report['worlds']['candidate']['ledgers']['shards'][rel];item['restoreNativePrunedContributions']=True;item['restorationProof']=proof;item['preserveBytes']=False
        m.require(len(approved)==5 and restored==47,'Expected only five known pruned shards/47 exact contributions')
        errors=[row for row in report['errors'] if not(row.get('variant')=='candidate' and (tuple(row.get('chunk',[])) in bychunk or row.get('detail',{}).get('path') in approved))];m.require(not errors,'Another unexplained original427 change remains')
        world=report['worlds']['candidate'];world['chunks'].sort(key=lambda row:row['chunk']);world['losses']=[dict(row,chunk=item['chunk']) for item in world['chunks'] for row in item['losses']];world['ledgers']['unexpectedShardChanges']=[]
        report.update(status='PASS',errors=[],geometryEquivalentBeforeRepair=False,exactRestorationRequired=True,restoredShardContributionsRequired=47,baseInventory=report['inventory'],inventory={'path':str(p.INVENTORY),'sha256':p.INVENTORY_SHA},blockedAudit={'path':str(saved),'sha256':m.sha(saved)},nativeComponentDependencyProof={'path':str(dependency),'sha256':m.sha(dependency)},nativePrunedShardProof={'path':str(shard_path),'sha256':m.sha(shard_path)},targetedRechecks={'chunks':[[18,-57],[18,-56],[18,-55]],'shards':sorted(approved),'whole427Rescanned':False})
        m.atomic_json(path,report);print(m.canonical({'status':'PASS','candidateRestorationCells':len(world['losses']),'nativeStateRestorations':22,'shardContributionsToRestore':47,'allOtherOriginalProofsPreserved':True}))
    finally:db.close()

if __name__=='__main__':
    p.m.control_check()
    try:main()
    except BaseException:
        p.m.atomic_json(p.REPAIR/'targeted-audit-error.json',{'status':'FAIL','error':p.traceback.format_exc(),'mapWrites':0,'databaseWrites':0});raise
