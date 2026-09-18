"""Offline invariants for a converted world; does not start Minecraft."""
from convert_modular_world import *

def verify(source,target):
    definitions={b['id']:b for b in json.loads((RES/'bloodborne_blocks/definitions.json').read_text())['blocks']}
    definitions.update({b['id']:b for b in json.loads((OUT/'definitions.json').read_text())['blocks']})
    counts=collections.Counter();states_seen=set();helpers=[];legacy_positions={};errors=[]
    expected_changes={};entity_move_batches=[];seen_changes=set()
    for filename,key in [('world-overlap-repair-v2.json','resolved'),('world-ornaments-v2.json','changes'),('world-water-v2.json','changes'),('world-relocation-v2.json','changes'),('world-relocation-extra-v2.json','changes'),('world-relocation-final-v2.json','changes'),('world-static-grid-v2.json','changes'),('world-static-technical-v2.json','resolved'),('world-static-safe-v2.json','resolved'),('world-static-water-v2.json','changes'),('world-static-relocation-v2.json','changes'),('world-functional-repair-v2.json','changes')]:
        path=ROOT/'docs'/filename
        if not path.exists():continue
        ledger=json.loads(path.read_text(encoding='utf-8'))
        if not ledger.get('applied'):continue
        for entry in ledger.get(key,[]):
            pos=tuple(entry['position']);previous=expected_changes.get(pos)
            if previous and previous['newState']!=entry['oldState']:raise AssertionError(('discontinuous_change_ledger',pos))
            expected_changes[pos]={'oldState':previous['oldState'] if previous else entry['oldState'],'newState':entry['newState']}
        # Apply whole batches in chronological order: an object can move again in
        # a later repair, while positions may be reused by another object.
        entity_move_batches.append({tuple(e['from']):tuple(e['to']) for e in ledger.get('entityMoves',[])})
    changes_by_section=collections.defaultdict(list)
    for pos,entry in expected_changes.items():changes_by_section[(pos[0]//16,pos[1]//16,pos[2]//16)].append((pos,entry))
    old_entities={};new_entities={}
    for path in sorted(source.glob('region/*.mca')):
        before=RegionFile.open(path);after=RegionFile.open(target/path.relative_to(source))
        old_chunks={(c.x,c.z):c for c in before.chunks()};new_chunks={(c.x,c.z):c for c in after.chunks()}
        assert old_chunks.keys()==new_chunks.keys(), 'Chunk set changed'
        for key,c in old_chunks.items():
            a=compound(c.nbt().root);b=compound(new_chunks[key].nbt().root);cx=a['xPos'].value;cz=a['zPos'].value
            assert cx==b['xPos'].value and cz==b['zPos'].value
            errors.extend(('invalid_lighting_cache',cx,cz,*issue) for issue in lighting_cache_errors(b))
            old={compound(s)['Y'].value:compound(s) for s in a['sections'].value}
            new={compound(s)['Y'].value:compound(s) for s in b['sections'].value}
            assert old.keys()==new.keys(), 'Section set changed'
            for y,section in old.items():
                dest=new[y]
                assert section.get('biomes')==dest.get('biomes'), 'Biomes changed'
                if 'block_states' not in section:continue
                oldbs=compound(section['block_states']);newbs=compound(dest['block_states'])
                op=oldbs['palette'].value;npal=newbs['palette'].value
                oka=[block_state_key(t) for t in op];nka=[block_state_key(t) for t in npal]
                ia=unpack_fast(oldbs);ib=unpack_fast(newbs)
                # Compare foreign non-air blocks exactly, including all properties.
                foreign={i:k for i,k in enumerate(oka) if not k.startswith(NS) and k not in ('minecraft:air','minecraft:cave_air','minecraft:void_air')}
                for i,k in foreign.items():
                    at=np.flatnonzero(ia==i)
                    for idx in at:
                        actual=nka[int(ib[idx])]
                        if actual==k:continue
                        pos=(cx*16+(int(idx)&15),y*16+(int(idx)>>8),cz*16+((int(idx)>>4)&15));allowed=expected_changes.get(pos)
                        if not allowed or allowed['oldState']!=k or allowed['newState']!=actual:errors.append(('unrecorded_foreign_change',pos,k,actual))
                        else:counts['recorded_foreign_changes']+=1
                    counts['foreign_blocks_verified']+=len(at)
                for pos,entry in changes_by_section.get((cx,y,cz),[]):
                    idx=pos[1]%16*256+pos[2]%16*16+pos[0]%16
                    if nka[int(ib[idx])]!=entry['newState']:errors.append(('recorded_change_not_applied',pos))
                    seen_changes.add(pos)
                values,freq=np.unique(ib,return_counts=True)
                for i,n in zip(values,freq):
                    entry=npal[int(i)];name=compound(entry)['Name'].value;state=nka[int(i)]
                    counts['blocks:'+name.split(':')[0]]+=int(n);states_seen.add(state)
                    if not name.startswith(NS):continue
                    ident=name[len(NS):]
                    if ident=='architecture_part':counts['remaining_helpers']+=int(n);continue
                    assert ident in definitions,'Unregistered ID '+ident
                    d=definitions[ident];ps=props(entry)
                    assert set(ps)==set(d['properties']), 'Properties mismatch '+state
                    assert statekey(ps) in d['states'],'Unknown state '+state
                    if d.get('modular'):counts['modular_blocks']+=int(n)
                    else:
                        counts['retained_legacy_blocks']+=int(n)
                        for idx in np.flatnonzero(ib==i):
                            pos=(cx*16+(int(idx)&15),y*16+(int(idx)>>8),cz*16+((int(idx)>>4)&15));legacy_positions[pos]=name
            def nonhelper(root):return [t for t in root.get('block_entities',Tag(TAG_LIST,[],TAG_COMPOUND)).value if compound(t).get('id',Tag(TAG_STRING,'')).value!=NS+'architecture_part']
            for root,destination in [(a,old_entities),(b,new_entities)]:
                for t in nonhelper(root):
                    bd=compound(t);pos=tuple(bd[axis].value for axis in ('x','y','z'));destination[pos]=t
            counts['foreign_block_entities_verified']+=len(nonhelper(a))
            for be in b.get('block_entities',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
                bd=compound(be)
                if bd.get('id',Tag(TAG_STRING,'')).value==NS+'architecture_part':helpers.append(bd)
            counts['chunks_verified']+=1
        print('Verified',path.name,counts['chunks_verified'],'chunks',flush=True)
    def signed(v,bits):return v-(1<<bits) if v&(1<<(bits-1)) else v
    for be in helpers:
        packed=be['Root'].value&((1<<64)-1);root=(signed(packed>>38,26),signed(packed&4095,12),signed((packed>>12)&((1<<26)-1),26))
        if legacy_positions.get(root)!=be['Owner'].value:errors.append(('orphan_helper',be['x'].value,be['y'].value,be['z'].value,root,be['Owner'].value))
    assert counts['remaining_helpers']==len(helpers), 'Helper block/entity count differs'
    expected_entities={}
    for pos,entity in old_entities.items():
        newpos=pos
        for batch in entity_move_batches:newpos=batch.get(newpos,newpos)
        moved=copy.deepcopy(entity)
        for axis,value in zip(('x','y','z'),newpos):compound(moved)[axis].value=value
        expected_entities[newpos]=moved
    assert expected_entities==new_entities, 'Foreign block entity contents changed outside recorded position moves'
    assert seen_changes==set(expected_changes), 'Recorded changed cells missing from the world'
    # Player data, entities, mod storage and datapacks are not conversion targets.
    # Verify them again after every repair, not only after initial conversion.
    for path in source.rglob('*'):
        if not path.is_file():continue
        relative=path.relative_to(source)
        if relative.parts[0]=='region' or relative.as_posix() in {'level.dat','level.dat_old','session.lock'}:continue
        destination=target/relative
        assert destination.is_file(), 'Missing preserved file '+str(relative)
        assert hashlib.sha256(path.read_bytes()).digest()==hashlib.sha256(destination.read_bytes()).digest(), 'Preserved file changed '+str(relative)
        counts['external_files_verified']+=1
    report={'counts':dict(counts),'unique_states':len(states_seen),'errors':errors}
    dump(ROOT/'docs/world-verification-v2.json',report)
    if errors:raise AssertionError(str(len(errors))+' world verification errors; see report')
    print('VERIFIED',dict(counts),flush=True)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('source',type=Path);parser.add_argument('target',type=Path);a=parser.parse_args();verify(a.source,a.target)
