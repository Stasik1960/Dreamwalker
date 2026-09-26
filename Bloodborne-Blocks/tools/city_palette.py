"""Frozen, cell-preserving palette adapter for the existing logical converter."""
from __future__ import annotations
import hashlib
import json
from collections import Counter
from pathlib import Path
from world_io import compound, block_state_key, section_blocks, TAG_LIST, TAG_COMPOUND, TAG_LONG, TAG_STRING, Tag

DEFAULT_CITY = Path(__file__).resolve().parents[1]/'src/main/resources/bloodborne_blocks/city'

def audit_helpers(world, city):
    from convert_logical_world import PART, unpack_pos_long
    geometries={};root_carriers=set()
    for resource in (city,city.parent/'logical'):
        definitions=resource/'definitions.json'
        if definitions.exists():
            root_carriers.update('bloodborne_blocks:'+d['id'] for d in json.loads(definitions.read_bytes())['blocks']
                                 if d.get('logical') or d.get('whole_owner'))
        data=json.loads((resource/'geometry.json').read_bytes())
        for ident,block in data['blocks'].items():
            geometries['bloodborne_blocks:'+ident]={key:data.get('profiles',{}).get(value.get('ref'),value)
                                                    for key,value in block['states'].items()}
    count=0;errors=[];seen=set()
    for chunk in world.chunks.values():
        for entity in chunk.entities()[1]:
            data=compound(entity)
            if data.get('id') is None or data['id'].value!=PART:continue
            count+=1
            point=tuple(int(data[a].value) for a in ('x','y','z'))
            seen.add((chunk.dimension,*point))
            reason=None
            try:bindings=helper_bindings(data)
            except ValueError:reason='malformed_owner_bindings';bindings=[]
            for root,owner in bindings:
                state=world.get(chunk.dimension,root)
                key=','.join(k+'='+v for k,v in state[1]) if state else ''
                geometry=geometries.get(owner,{}).get(key)
                offset=','.join(str(point[i]-root[i]) for i in range(3))
                if not state or state[0]!=owner:reason='owner_block_missing'
                elif not geometry or offset not in geometry['cells'] or point==root:reason='outside_owner_geometry'
                if reason:break
            carrier=world.get(chunk.dimension,point)
            if not reason and carrier!=(PART,()):
                carrier_key=','.join(k+'='+v for k,v in carrier[1]) if carrier else ''
                if not carrier or carrier[0] not in root_carriers or carrier_key not in geometries.get(carrier[0],{}):
                    reason='entity_without_supported_carrier'
            if reason:errors.append({'dimension':chunk.dimension,'position':list(point),'reason':reason})
    for chunk in world.chunks.values():
        for section in chunk.root().get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
            bs=compound(section).get('block_states')
            if not bs:continue
            palette=compound(bs)['palette'].value
            ids={i for i,t in enumerate(palette) if compound(t)['Name'].value==PART}
            if not ids:continue
            sy=int(compound(section)['Y'].value)
            # Flush candidate reads/writes before comparing actual cell membership.
            cached=chunk.loaded.get(sy)
            if cached:
                palette=cached[1];indices=cached[2]
                ids={i for i,t in enumerate(palette) if compound(t)['Name'].value==PART}
            else:_,indices=section_blocks(section)
            for index,value in enumerate(indices):
                if value not in ids:continue
                point=(chunk.x*16+(index&15),sy*16+(index>>8),chunk.z*16+((index>>4)&15))
                if (chunk.dimension,*point) not in seen:
                    errors.append({'dimension':chunk.dimension,'position':list(point),'reason':'part_without_entity'})
    return {'checked':count,'orphans':errors,'ok':not errors}


def helper_bindings(data):
    """Strict disk-schema validation; metadata is never itself ownership proof."""
    from convert_logical_world import unpack_pos_long
    def pair(row):
        if row.get('Root',Tag(TAG_LIST,[])).type!=TAG_LONG or row.get('Owner',Tag(TAG_LIST,[])).type!=TAG_STRING:
            raise ValueError('invalid owner fields')
        return unpack_pos_long(row['Root'].value),row['Owner'].value
    first=pair(data)
    if 'Owners' not in data:return [first]
    raw=data['Owners']
    if raw.type!=TAG_LIST or not 2<=len(raw.value)<=16:raise ValueError('invalid owner count')
    result=[]
    for entry in raw.value:
        if entry.type!=TAG_COMPOUND or set(compound(entry))!={'Root','Owner'}:raise ValueError('invalid owner record')
        result.append(pair(compound(entry)))
    if result!=sorted(set(result)) or result[0]!=first:raise ValueError('noncanonical owner list')
    return result

def load(city=DEFAULT_CITY, declared=None):
    from convert_logical_world import state_tag
    hashes={name:hashlib.sha256((city/name).read_bytes()).hexdigest()
            for name in ('migration.json','definitions.json','geometry.json','meshes.json.gz')}
    if declared is not None and hashes != declared.get('hashes'):
        raise ValueError('City compatibility resources changed since conversion')
    data=json.loads((city/'migration.json').read_bytes())
    definitions=json.loads((city/'definitions.json').read_bytes())['blocks']
    by_id={'bloodborne_blocks:'+d['id']:d for d in definitions}
    mapping={}
    for old,new in data['states'].items():
        if not old.startswith('bloodborne_blocks:m_'):
            raise ValueError('City adapter may only replace historical module states')
        target=by_id[new['id']]
        key=','.join(k+'='+v for k,v in sorted(new['properties'].items()))
        if key not in target['states']:raise ValueError('City target state is not registered')
        mapping[old]=state_tag(new['id'],new['properties'])
    return mapping,by_id,hashes

def chunk_has_mapping(root, mapping):
    for section in root.get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
        bs=compound(section).get('block_states')
        if bs and any(block_state_key(t) in mapping for t in compound(bs)['palette'].value):return True
    return False

def apply(world, city, *, allow_unresolved=False):
    mapping, definitions, hashes=load(city)
    logical=json.loads((city.parent/'logical/definitions.json').read_bytes())['blocks']
    registered=set(definitions)|{'bloodborne_blocks:'+d['id'] for d in logical}|{'bloodborne_blocks:architecture_part'}
    changed=Counter();unknown=Counter();chunks=0
    for chunk in world.chunks.values():
        chunk.finish()
        chunk.loaded.clear()
        touched=False
        for section in chunk.root().get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
            bs=compound(section).get('block_states')
            if not bs:continue
            palette=compound(bs)['palette'].value
            keys=[block_state_key(t) for t in palette]
            relevant=any(k in mapping or (k.startswith('bloodborne_blocks:') and k.split('[')[0] not in registered) for k in keys)
            if not relevant:continue
            _,indices=section_blocks(section);counts=Counter(indices)
            for i,key in enumerate(keys):
                if not counts[i]:continue
                if key in mapping:
                    palette[i]=mapping[key];changed[key]+=counts[i];touched=True
                elif key.startswith('bloodborne_blocks:') and key.split('[')[0] not in registered:
                    unknown[key]+=counts[i]
        if touched:chunk.changed=True;chunks+=1
    if unknown and not allow_unresolved:
        raise ValueError('City adapter leaves unregistered states: '+str(dict(unknown.most_common(30))))
    return {'hashes':hashes,'changedCells':sum(changed.values()),'changedChunks':chunks,
            'states':dict(sorted(changed.items())),'coordinateChanges':0,'unknownStates':dict(sorted(unknown.items()))}
