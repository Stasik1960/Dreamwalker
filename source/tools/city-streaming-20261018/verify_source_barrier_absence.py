#!/usr/bin/env python3
"""Verify proven source barriers and their shared-cell owners without world writes."""
import argparse,gzip,hashlib,json,traceback
from collections import Counter,defaultdict
from pathlib import Path
from audit_source_barriers import sha,file_sha,dim_dir,safe_tree,sections
from world_io import RegionFile,Tag,NbtFile,compound,section_blocks,block_state_key,decode_nbt,encode_nbt,read_nbt

PHASE_SHA='665989a9b97740dbc360daac6c8e857e537de9d12a15e0c1beff12edf11bad86'
MAP_SHA='39e83f2941b768fd2ae9f84d8f57dc76aec78bd926dc5e38eab3059251d2bb08'
SOURCE_PLAN_SHA='384b1dd6548e356dc4b078848443f9df779d9c7e2338c89e183155949d7bfffb'

def owners(be):
    if 'OwnerDataV1' in be:
        assert 'owners' not in be
        root=compound(decode_nbt(be['OwnerDataV1'].value).root)
        assert set(root)=={'owners'}
        return root['owners'].value
    return be.get('owners',Tag(9,[],10)).value

def summary(row):
    r=compound(row); o=compound(r['owner']);d=compound(r['rootData'])
    return {'ownerId':o['id'].value,'ownerRoot':o['root'].value,'uuid':o['uuid'].value,'dataId':d['id'].value,
            'rootPayloadSha256':sha(d['payload'].value),'collisionBoxes':len(r['collision'].value),'selectionBoxes':len(r['selection'].value)}

def canonical(tag):
    if tag.type==10: return (10,tuple((k,canonical(v)) for k,v in sorted(tag.value.items())))
    if tag.type==9:return (9,tag.list_type,tuple(canonical(v) for v in tag.value))
    if isinstance(tag.value,list):return (tag.type,tuple(tag.value))
    return (tag.type,tag.value)

def main(a):
    world=safe_tree(a.world);proof_path=safe_tree(a.original_audit);out=safe_tree(a.out);out.mkdir(parents=True,exist_ok=True)
    original=json.loads(proof_path.read_text(encoding='utf8'));phase=proof_path.parent/'source-barriers-from-original.jsonl.gz'
    assert original['originalZipSha256']==MAP_SHA and original['historical14855PhaseMatches'] and original['sourceBarrierCount']==14855
    assert file_sha(phase)==PHASE_SHA
    coords=[(r['dimension'],*r['position']) for r in map(json.loads,gzip.decompress(phase.read_bytes()).decode().splitlines())]
    coordset=set(coords);assert len(coords)==len(coordset)==14855
    lineage=json.loads((world/'dw-derived-copy.json').read_text(encoding='utf8'))
    assert lineage['sourceHashes']['map']==MAP_SHA and lineage['sourcePlanSha256']==SOURCE_PLAN_SHA
    prior=json.loads((world/'dw-source-barrier-cleanup-report.json').read_text(encoding='utf8'))
    assert prior['status']=='PASS_COMPLETE' and prior['removed']==prior['expectedCount']==14855 and prior['sourcePlanSha256']==SOURCE_PLAN_SHA and prior['barrierPhaseSha256']==PHASE_SHA and not prior['skipped']
    by_chunk=defaultdict(set)
    for dim,x,y,z in coords:by_chunk[(dim,x>>4,z>>4)].add((x,y,z))
    region_groups=defaultdict(list)
    for dim,cx,cz in by_chunk:region_groups[(dim,cx>>5,cz>>5)].append((cx,cz))
    states=Counter();ownerids=Counter();dataids=Counter();residentids=Counter();be_keys=Counter();root_at_source=Counter();candidateowners=[];details=[];files=[];source_guests={};mismatch=[]
    empty_payload=0;helpers=0;guest_records=0;shared_examples=[]
    for (dim,rx,rz),chunks in sorted(region_groups.items()):
        path=world/dim_dir(dim)/'region'/f'r.{rx}.{rz}.mca';before=file_sha(path);region=RegionFile.open(path)
        for cx,cz in chunks:
            stored=region.get_chunk(cx&31,cz&31);assert stored is not None
            root,secs=sections(compound(stored.nbt().root));sec={compound(s)['Y'].value:section_blocks(s) for s in secs}
            bes={(compound(b)['x'].value,compound(b)['y'].value,compound(b)['z'].value):compound(b) for b in root.get('block_entities',Tag(9,[],10)).value}
            for x,y,z in sorted(by_chunk[dim,cx,cz]):
                bs=sec.get(y>>4);state='minecraft:air' if bs is None else block_state_key(bs[0][bs[1][((y&15)<<8)|((z&15)<<4)|(x&15)]])
                states[state]+=1;row={'dimension':dim,'position':[x,y,z],'state':state}
                be=bes.get((x,y,z));entries=[]
                if be is not None:
                    helpers+=1;be_keys.update(be.keys());assert state=='bloodborne_dw:composite_cell'
                    if 'resident' in be: residentids[compound(be['resident'])['id'].value]+=1
                    if not compound(be.get('payload',Tag(10,{}))):empty_payload+=1
                    entries=owners(be);guest_records+=len(entries);row['ownerCount']=len(entries);row['blockEntitySha256']=sha(encode_nbt(NbtFile('',Tag(10,be))))
                    row['guests']=[summary(e) for e in entries]
                    for e,rec in zip(entries,row['guests']):
                        ownerids[rec['ownerId']]+=1;dataids[rec['dataId']]+=1
                        if (dim,*rec['ownerRoot']) in coordset:root_at_source[rec['ownerId']]+=1
                        raw=encode_nbt(NbtFile('',e))
                        if b'minecraft:barrier' in raw or 'barrier' in rec['ownerId'].lower() or 'barrier' in rec['dataId'].lower():candidateowners.append({'position':[x,y,z],**rec})
                    if len(shared_examples)<12:shared_examples.append(row)
                source_guests[(dim,x,y,z)]=entries;details.append(row)
        assert file_sha(path)==before;files.append({'file':path.relative_to(world).as_posix(),'sha256Before':before,'sha256After':before})
    shard_root_ids=Counter();shard_candidate_roots=[];shard_root_count=0;shard_contributions=0;shard_source_cell_count=0;shard_dictionary=Counter()
    for (dim,cx,cz),positions in sorted(by_chunk.items()):
        path=world/dim_dir(dim)/'data/bloodborne_dw_owner_shards'/f'owners-{cx}.{cz}.nbt'
        if not path.exists():
            if any(source_guests[(dim,*p)] for p in positions):mismatch.append({'chunk':[cx,cz],'reason':'guest-shard-missing'})
            continue
        before=file_sha(path);r=compound(read_nbt(path).root);dictionary=bool(r.get('Dictionary',Tag(1,0)).value);shard_dictionary[str(dictionary)]+=1
        ot=r.get('ownerTable',Tag(9,[],10)).value;dt=r.get('dataTable',Tag(9,[],10)).value;st=r.get('shapeTable',Tag(9,[],10)).value;found=set()
        for cell in r.get('cells',Tag(9,[],10)).value:
            c=compound(cell);p=tuple(c['pos'].value)
            if p not in positions:continue
            found.add(p);shard_source_cell_count+=1;entries=[]
            if dictionary:
                for ref in c['refs'].value:
                    oi,di,si=ref.value;s=compound(st[si]);entries.append(Tag(10,{'owner':ot[oi],'rootData':dt[di],'collision':s['collision'],'selection':s['selection']}))
            else:entries=c.get('owners',Tag(9,[],10)).value
            shard_contributions+=len(entries)
            be_entries=source_guests[(dim,*p)]
            if sorted(map(canonical,entries))!=sorted(map(canonical,be_entries)):mismatch.append({'position':list(p),'reason':'be-shard-contributions-differ','beCount':len(be_entries),'shardCount':len(entries)})
        for p in positions-found:
            if source_guests[(dim,*p)]:mismatch.append({'position':list(p),'reason':'be-guests-missing-from-shard'})
        for member in r.get('ownerMembership',Tag(9,[],10)).value:
            m=compound(member);owner=compound(ot[m['ownerRef'].value] if dictionary else m['owner']);p=tuple(owner['root'].value)
            if p not in positions:continue
            reg=owner['id'].value;shard_root_count+=1;shard_root_ids[reg]+=1
            shard_candidate_roots.append({'dimension':dim,'root':list(p),'ownerId':reg,'uuid':owner['uuid'].value})
        assert file_sha(path)==before;files.append({'file':path.relative_to(world).as_posix(),'sha256Before':before,'sha256After':before})
    details_path=out/'source-barrier-shared-cell-objects.json.gz'
    details_path.write_bytes(gzip.compress((json.dumps(details,separators=(',',':'),sort_keys=True)+'\n').encode(),mtime=0))
    report={'schema':1,'status':'PASS_SOURCE_BARRIERS_ALREADY_ABSENT' if states==Counter({'minecraft:air':6265,'bloodborne_dw:composite_cell':8590}) and not residentids and not root_at_source and not candidateowners and not shard_candidate_roots and not mismatch else 'REVIEW_REQUIRED',
        'world':str(world),'sourceOriginalZipSha256':MAP_SHA,'sourcePlanSha256':SOURCE_PLAN_SHA,'sourceCoordinateGzipSha256':PHASE_SHA,'sourceBarrierCount':14855,'previousCleanupRemoved':14855,'removedThisRun':0,'worldWritesThisRun':0,
        'statesAtAllOriginalBarrierCoordinates':dict(states),'helperBlockEntities':helpers,'helperResidentOwnerIds':dict(residentids),'helperEmptyPayloadCount':empty_payload,'guestContributionRecords':guest_records,'guestOwnerRegistryIds':dict(sorted(ownerids.items())),'guestDataRegistryIds':dict(sorted(dataids.items())),
        'guestRootsAtAnyOriginalBarrierCoordinate':dict(root_at_source),'barrierLikeGuestCandidates':candidateowners,'shardOwnerMembershipRootsAtOriginalBarrierCoordinates':shard_root_count,'shardOwnerMembershipRegistryIds':dict(shard_root_ids),'shardOwnerMembershipCandidates':shard_candidate_roots,'shardDictionaryModes':dict(shard_dictionary),'shardSourceCells':shard_source_cell_count,'shardSourceContributions':shard_contributions,'beShardMismatches':mismatch,
        'preservation':'No map files written. All 8590 helpers and their nonbarrier guest ownership, geometry, mutable payload bytes, UUIDs and root offsets retained. Source KEEP decision 223 generated no barrier owner or shifted fragments. No class-wide invisible-carrier removal.',
        'sourceDecision':{'id':223,'action':'KEEP','sourceState':'minecraft:barrier','reason':'PRESERVE_ORIGINAL_INVISIBLE_NATIVE_BARRIER_OR_LIGHT'},'sourceCoordinateProof':str(proof_path),'sourceCoordinateProofSha256':file_sha(proof_path),'detailsFile':details_path.name,'detailsSha256':file_sha(details_path),'sharedCellSamples':shared_examples,'readOnlyFileProof':files}
    (out/'source-barrier-verification.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
    assert report['status']=='PASS_SOURCE_BARRIERS_ALREADY_ABSENT',report['status']

if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('--world',type=Path,required=True);ap.add_argument('--original-audit',type=Path,required=True);ap.add_argument('--out',type=Path,required=True);a=ap.parse_args()
    try:main(a)
    except Exception:
        a.out.mkdir(parents=True,exist_ok=True);(a.out/'source-barrier-verification-error.txt').write_text(traceback.format_exc(),encoding='utf8');raise
