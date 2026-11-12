"""Rebuild only grass owners in composite block entities and sharded storage."""
from collections import defaultdict
from copy import deepcopy
from pathlib import Path

from audit_grass import OLD
from world_io import (NbtFile, RegionFile, Tag, compound, decode_nbt, encode_nbt, read_nbt,
                      write_nbt, TAG_BYTE, TAG_BYTE_ARRAY, TAG_COMPOUND, TAG_INT,
                      TAG_INT_ARRAY, TAG_LIST, TAG_LONG_ARRAY, TAG_STRING)

SHARD_DIR=Path('data/bloodborne_dw_owner_shards')
LEDGER=Path('data/bloodborne_dw_composite_owners.dat')


def coordinate(fields):
    return tuple(fields[k].value for k in 'xyz')


def cell_owners(entity):
    packed=decode_nbt(compound(entity)['OwnerDataV1'].value)
    return packed,compound(packed.root)['owners']


def old_entities(edits):
    by_region=defaultdict(lambda:defaultdict(set))
    for x,y,z in edits:
        by_region[(x//512,z//512)][(x//16%32,z//16%32)].add((x,y,z))
    for (rx,rz),chunks in by_region.items():
        region=RegionFile.open(OLD/'region'/f'r.{rx}.{rz}.mca')
        for (cx,cz),positions in chunks.items():
            root=compound(region.get_chunk(cx,cz).nbt().root)
            for entity in root['block_entities'].value:
                pos=coordinate(compound(entity))
                if pos in positions:
                    yield pos,entity


def restore_entity(pos, entity, options, variants):
    entity=deepcopy(entity)
    fields=compound(entity)
    assert fields['id'].value=='bloodborne_dw:composite_cell'
    assert 'NativeGridBase' not in fields
    desired={tuple(o) for o in options['legacy_roots']}
    packed, owners=cell_owners(entity)
    owners.value=[tag for tag in owners.value
                  if tuple(compound(compound(tag)['owner'])['root'].value) in desired
                  and compound(compound(tag)['owner'])['id'].value==variants[
                      tuple(compound(compound(tag)['owner'])['root'].value)]]
    assert {tuple(compound(compound(t)['owner'])['root'].value) for t in owners.value}==desired
    fields['OwnerDataV1']=Tag(TAG_BYTE_ARRAY,encode_nbt(packed))
    resident=fields.get('resident')
    if pos in desired:
        assert resident is not None and compound(resident)['id'].value==variants[pos]
        assert tuple(compound(resident)['root'].value)==pos
        own=next(compound(t) for t in owners.value if compound(t)['owner']==resident)
        snapshot=compound(own['rootData'])
        assert snapshot['id'].value==variants[pos]
        state=Tag(TAG_COMPOUND,{'Name':snapshot['id'],'Properties':snapshot['properties']})
    else:
        fields.pop('resident',None)
        fields['payload']=Tag(TAG_COMPOUND,{})
        state=Tag(TAG_COMPOUND,{'Name':Tag(TAG_STRING,'bloodborne_dw:composite_cell')})
    if options['native_base'] is not None:
        fields['NativeGridBase']=Tag(TAG_INT,options['native_base'])
    return state,entity


def chunk_key(cx,cz):
    value=(cx&0xffffffff)|((cz&0xffffffff)<<32)
    return value if value<2**63 else value-2**64


def write_shards(world, entities):
    by_chunk=defaultdict(dict)
    by_owner=defaultdict(dict)
    for pos,entity in entities.items():
        _,owners=cell_owners(entity)
        x,_,z=pos
        by_chunk[x//16,z//16][pos]=owners.value
        for member in owners.value:
            record=compound(member)
            origin=tuple(compound(record['owner'])['root'].value)
            by_owner[origin][pos]=record
    ledger=read_nbt(world/LEDGER)
    meta=compound(compound(ledger.root)['data'])
    assert meta['OwnerShardSchema'].value==1 and not meta['cells'].value
    known=set(meta['OwnerShardChunks'].value)
    members_by_chunk=defaultdict(dict)
    for origin,parts in by_owner.items():
        members_by_chunk[origin[0]//16,origin[2]//16][origin]=parts
    changed={LEDGER}
    for (cx,cz),positions in by_chunk.items():
        key=chunk_key(cx,cz)
        relative=SHARD_DIR/f'owners-{cx}.{cz}.nbt'
        path=world/relative
        if path.exists():
            nbt=read_nbt(path)
            assert key in known
        else:
            assert key not in known
            nbt=NbtFile('',Tag(TAG_COMPOUND,{
                'Schema':Tag(TAG_INT,1),'ChunkX':Tag(TAG_INT,cx),'ChunkZ':Tag(TAG_INT,cz),
                'Dictionary':Tag(TAG_BYTE,1),
                **{k:Tag(TAG_LIST,[],TAG_COMPOUND)
                   for k in ('cells','ownerMembership','ownerTable','shapeTable','dataTable')}
            }))
        data=compound(nbt.root)
        assert data['Dictionary'].value==1 and data['Schema'].value==1
        assert (data['ChunkX'].value,data['ChunkZ'].value)==(cx,cz)
        assert not ({tuple(compound(c)['pos'].value) for c in data['cells'].value}&positions.keys())
        for table in ('cells','ownerMembership','ownerTable','shapeTable','dataTable'):
            assert data[table].type==TAG_LIST
            if not data[table].value:
                data[table].list_type=TAG_COMPOUND
        tables={k:data[k].value for k in ('ownerTable','shapeTable','dataTable')}
        lookups={k:{encode_nbt(NbtFile('',tag)):i for i,tag in enumerate(values)}
                 for k,values in tables.items()}

        def ref(table,tag):
            key=encode_nbt(NbtFile('',tag))
            found=lookups[table].get(key)
            if found is not None:
                return found
            number=len(tables[table])
            tables[table].append(tag)
            lookups[table][key]=number
            return number

        def shape(record):
            return ref('shapeTable',Tag(TAG_COMPOUND,{k:record[k] for k in ('collision','selection')}))

        for pos,entries in sorted(positions.items()):
            values=[]
            for entry in entries:
                row=compound(entry)
                values.append(Tag(TAG_INT_ARRAY,[ref('ownerTable',row['owner']),
                                               ref('dataTable',row['rootData']),shape(row)]))
            data['cells'].value.append(Tag(TAG_COMPOUND,{'pos':Tag(TAG_INT_ARRAY,list(pos)),
                                                       'refs':Tag(TAG_LIST,values,TAG_INT_ARRAY)}))
        existing={compound(tag)['ownerRef'].value for tag in data['ownerMembership'].value}
        for origin,parts in sorted(members_by_chunk[cx,cz].items()):
            owner_id=ref('ownerTable',parts[origin]['owner'])
            assert owner_id not in existing
            values=[Tag(TAG_COMPOUND,{'offset':Tag(TAG_INT_ARRAY,[a-b for a,b in zip(pos,origin)]),
                                      'shapeRef':Tag(TAG_INT,shape(record))})
                    for pos,record in sorted(parts.items())]
            data['ownerMembership'].value.append(Tag(TAG_COMPOUND,{'ownerRef':Tag(TAG_INT,owner_id),
                                                                  'members':Tag(TAG_LIST,values,TAG_COMPOUND)}))
        write_nbt(path,nbt)
        assert read_nbt(path)==nbt
        known.add(key)
        changed.add(relative)
    meta['OwnerShardChunks']=Tag(TAG_LONG_ARRAY,sorted(known))
    write_nbt(world/LEDGER,ledger)
    assert read_nbt(world/LEDGER)==ledger
    return changed
