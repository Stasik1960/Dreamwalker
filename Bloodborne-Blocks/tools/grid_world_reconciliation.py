"""Remove only historically verified compatibility helpers outside one-cell masks."""
import gzip,hashlib,json
from pathlib import Path
from world_io import compound,TAG_STRING,TAG_LONG
ROOT=Path(__file__).resolve().parents[1]

def plan(view, entities):
    from convert_logical_world import PART,unpack_pos_long
    path=ROOT/'docs/grid-physics/legacy-city-geometry.json.gz'
    expected=json.loads((ROOT/'docs/grid-physics/input-integrity.json').read_bytes())['legacy_city_geometry_sha256']
    if hashlib.sha256(path.read_bytes()).hexdigest()!=expected:raise ValueError('GRID_LEGACY_GEOMETRY_HASH_MISMATCH')
    legacy=json.loads(gzip.decompress(path.read_bytes()))
    entries=[]
    for location,tag in sorted(entities.items()):
        dim,*xyz=location;p=tuple(xyz);d=compound(tag)
        if d.get('id') is None or d['id'].value!=PART:continue
        if d.get('Owner') is None or d['Owner'].type!=TAG_STRING or d.get('Root') is None or d['Root'].type!=TAG_LONG:continue
        owner=d['Owner'].value;ident=owner.removeprefix('bloodborne_blocks:')
        if ident not in legacy['blocks']:continue
        root=unpack_pos_long(d['Root'].value);state=view.get(dim,root)
        if not state or state[0]!=owner:raise ValueError('GRID_ORPHAN_OWNER')
        if view.get(dim,p)!=(PART,()):raise ValueError('GRID_HELPER_STATE_MISMATCH')
        key=','.join(k+'='+v for k,v in state[1]);old=legacy['blocks'][ident]['states'].get(key)
        if old is None:raise ValueError('GRID_UNKNOWN_OWNER_STATE')
        old=legacy.get('profiles',{}).get(old.get('ref'),old)
        offset=','.join(str(p[i]-root[i]) for i in range(3))
        if p==root or offset not in old['cells']:raise ValueError('GRID_UNPROVEN_HELPER')
        # Never delete inventory/custom payload attached to a nominal helper.
        if set(d)-{'id','x','y','z','Owner','Root','keepPacked'}:raise ValueError('GRID_HELPER_CUSTOM_NBT')
        entries.append({'dimension':dim,'changes':[{'position':list(p),'before':PART,'after':'minecraft:air'}],
                        'helpers':[],'owner':owner,'root':list(root),'reason':'verified_compatibility_helper_outside_physical_footprint'})
    return entries

def apply(world):
    entries=plan(world,world.block_entities())
    for e in entries:
        p=tuple(e['changes'][0]['position'])
        if world.ticks_at(e['dimension'],{p}):raise ValueError('GRID_HELPER_HAS_TICKS')
    for e in entries:
        p=tuple(e['changes'][0]['position']);world.set(e['dimension'],p,('minecraft:air',()));world.remove_entities(e['dimension'],{p})
    return {'physicalFootprintsSha256':hashlib.sha256((ROOT/'src/main/resources/bloodborne_blocks/logical/physical-footprints.json').read_bytes()).hexdigest(),'entries':entries,'helperChanges':len(entries),'physicsReconciliationChanges':len(entries)}
