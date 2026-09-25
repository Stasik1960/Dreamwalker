"""Coordinate-scoped restoration from the archived pre-positionfix MODDED map."""
from pathlib import Path
import hashlib
import json
import zipfile
from world_io import RegionFile, compound, section_blocks, block_state_key, Tag, TAG_COMPOUND, TAG_INT, TAG_LONG, TAG_STRING

ROOT=Path(__file__).resolve().parents[1]
PLAN=ROOT/'docs/city-compat/recovery-plan.json'
REFERENCE=ROOT/'reference-inputs/city-recovery-reference.zip'

def parse_state(text):
    name,_,props=text.partition('[')
    return name,tuple(sorted(tuple(p.split('=',1)) for p in props.rstrip(']').split(','))) if props else ()

class ReferenceReader:
    def __init__(self, archive):
        self.archive=archive;self.regions={};self.chunks={}
    def chunk(self, point):
        x,y,z=point;key=(x//16,z//16)
        if key not in self.chunks:
            region=(x//512,z//512);name=f'city-recovery-reference/region/r.{region[0]}.{region[1]}.mca'
            if region not in self.regions:self.regions[region]=RegionFile(self.archive.read(name))
            chunk=self.regions[region].get_chunk(key[0]%32,key[1]%32)
            if chunk is None:raise ValueError('Missing reference chunk')
            self.chunks[key]=compound(chunk.nbt().root)
        return self.chunks[key]
    def state(self,point):
        x,y,z=point
        section=next((s for s in self.chunk(point)['sections'].value if compound(s)['Y'].value==y//16),None)
        data=section_blocks(section) if section else None
        return block_state_key(data[0][data[1][(y&15)*256+(z&15)*16+(x&15)]]) if data else 'minecraft:air'
    def entity(self,point):
        for tag in self.chunk(point).get('block_entities',Tag(9,[])).value:
            data=compound(tag)
            if tuple(data[a].value for a in ('x','y','z'))==tuple(point):return {k:v.value for k,v in data.items()}
        return None

def load_plan(path=PLAN, reference=REFERENCE):
    from convert_logical_world import unpack_pos_long, PART
    raw=path.read_bytes();plan=json.loads(raw)
    if hashlib.sha256(reference.read_bytes()).hexdigest()!=plan['referenceSha256']:
        raise ValueError('Recovery reference checksum mismatch')
    seen=set()
    with zipfile.ZipFile(reference) as archive:
        reader=ReferenceReader(archive)
        for row in plan['cells']:
            p=tuple(row['position']);key=(row['dimension'],p)
            if key in seen or row['dimension']!='minecraft:overworld':raise ValueError('Duplicate/unsupported recovery position')
            seen.add(key)
            if not row['before'].startswith('bloodborne_blocks:m_'):raise ValueError('Recovery may only replace named missing modules')
            if reader.state(p)!=row['after']:raise ValueError('Recovery differs from old map')
            entity=reader.entity(p)
            if row['after']==PART:
                root=tuple(row['ownerPosition'])
                if not entity or entity['id']!=PART or unpack_pos_long(entity['Root'])!=root or entity['Owner']!=parse_state(row['ownerState'])[0]:
                    raise ValueError('Reference helper ownership mismatch')
                if reader.state(root)!=row['ownerState']:raise ValueError('Reference owner state mismatch')
            elif row['after']!='minecraft:air' or entity is not None:raise ValueError('Unsupported recovery replacement')
    return plan,hashlib.sha256(raw).hexdigest()

def helper_tag(helper):
    p=helper['position']
    return Tag(TAG_COMPOUND,{'id':Tag(TAG_STRING,helper['id']),'x':Tag(TAG_INT,p[0]),'y':Tag(TAG_INT,p[1]),'z':Tag(TAG_INT,p[2]),
                             'Owner':Tag(TAG_STRING,helper['Owner']),'Root':Tag(TAG_LONG,helper['Root'])})

def prepare(world, resources, declared=None, *, plan_path=PLAN, reference=REFERENCE):
    from convert_logical_world import PART, block_pos_long
    plan,sha=load_plan(plan_path,reference)
    geometry=json.loads((resources.parent/'city/geometry.json').read_bytes())
    entities=world.block_entities();entries=[]
    for row in plan['cells']:
        dim=row['dimension'];p=tuple(row['position']);state=world.get(dim,p)
        if state!=parse_state(row['before']):
            # Already converted/restored cells never trigger a second repair.
            continue
        if (dim,*p) in entities or world.ticks_at(dim,{p}):raise ValueError('Recovery cell has foreign entity or scheduled tick')
        helpers=[]
        if row['after']==PART:
            owner=parse_state(row['ownerState']);root=tuple(row['ownerPosition'])
            if world.get(dim,root)!=owner:raise ValueError('Recovery owner changed; refusing a detached helper')
            states=geometry['blocks'][owner[0].split(':')[1]]['states'];key=','.join(k+'='+v for k,v in owner[1]);value=states[key]
            shape=geometry.get('profiles',{}).get(value.get('ref'),value)
            offset=','.join(str(p[i]-root[i]) for i in range(3))
            if p==root or offset not in shape['cells']:raise ValueError('Recovery cell outside current owner geometry')
            helpers=[{'position':list(p),'id':PART,'Owner':owner[0],'Root':block_pos_long(*root)}]
        entries.append({'dimension':dim,'changes':[{'position':list(p),'before':row['before'],'after':row['after']}],
                        'helpers':helpers,'reason':'restore_historical_owned_part' if helpers else 'restore_historical_empty_cell'})
    result={'planSha256':sha,'referenceSha256':plan['referenceSha256'],'entries':entries,'restoredHelpers':sum(bool(e['helpers']) for e in entries),
            'restoredEmptyCells':sum(not e['helpers'] for e in entries)}
    if declared is not None and result!=declared:raise ValueError('Recovery report does not match independently checked source/reference')
    return result

def apply(world, recovery):
    from convert_logical_world import unpack_pos_long
    for entry in recovery['entries']:
        for change in entry['changes']:world.set(entry['dimension'],tuple(change['position']),parse_state(change['after']))
        for helper in entry['helpers']:world.add_helper(entry['dimension'],tuple(helper['position']),unpack_pos_long(helper['Root']),helper['Owner'])


def prepare_directory(source, resources, declared):
    """Bounded source read for the streaming preservation verifier."""
    from convert_logical_world import World, Chunk, load_defaults
    plan, _ = load_plan()
    world = World.__new__(World)
    world.root = source
    world.defaults = load_defaults(resources)
    world.chunks = {}
    world.by_region = {}
    world._dimension_heights = {}
    points = {tuple(row['position']) for row in plan['cells']}
    points.update(tuple(row['ownerPosition']) for row in plan['cells'] if 'ownerPosition' in row)
    for x, z in {(p[0] // 16, p[2] // 16) for p in points}:
        path = source / 'region' / f'r.{x//32}.{z//32}.mca'
        region = world.by_region.setdefault(path, RegionFile.open(path))
        stored = region.get_chunk(x % 32, z % 32)
        if stored is None: raise ValueError('Missing recovery source chunk')
        world.chunks[('minecraft:overworld',x,z)] = Chunk('minecraft:overworld',x,z,path,region,stored.x,stored.z,stored.timestamp,stored.nbt())
    return prepare(world, resources, declared=declared)
