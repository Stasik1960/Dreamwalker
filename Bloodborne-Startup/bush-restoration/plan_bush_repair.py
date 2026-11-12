"""Match native bush quads to historical source owners and choose stock V22 recipes."""
from collections import Counter, defaultdict
import json
import math
import struct
from zipfile import ZipFile

from probe_bush import BASE, JAR, NATIVE

STATIC = 'bloodborne_dw/static-architecture-v1274.json'
DATA = BASE/'grass-audit.json'
PLAN = BASE/'grass-repair-plan.json'
MIXED_PLAN = BASE/'grass-repair-mixed-plan.json'
EPSILON = 0.0001
AIR = {'minecraft:air','minecraft:cave_air','minecraft:void_air'}


def selected_meshes(jar, catalog, meshes):
    with jar.open(NATIVE+'meshes.bin') as stream:
        stream.read(8)
        for i, desc in enumerate(catalog['meshes']):
            count, = struct.unpack('>I',stream.read(4))
            assert count == desc['quadCount']
            raw = stream.read(count*139)
            if i in meshes:
                yield i, [raw[n:n+139] for n in range(0,len(raw),139)]


def transform(point, rotation, shift):
    x,y,z = point
    ox,oy,oz = rotation['origin']
    theta = math.radians(rotation['angle'])
    assert rotation['axis'] == 'y'
    cos,sin = math.cos(theta),math.sin(theta)
    return ((ox+(x-ox)*cos+(z-oz)*sin)/16+shift[0],
            y/16+shift[1], (oz-(x-ox)*sin+(z-oz)*cos)/16+shift[2])


def source_planes(jar, owner):
    name = owner['id'].rsplit(':',1)[1].removeprefix('source_addon_')
    model = json.loads(jar.read(f'assets/bloodborne_dw/models/remaining/source/minecraft/block/addon/{name}.json'))
    assert owner['properties'] == {'open':'false','profile':'base','rotation':'0','variant':'0'}
    shift = owner['payload'].get('SourceShift',[0,0,0])
    assert not owner['payload'].get('MountY')
    for element in model['elements']:
        low = transform(element['from'],element['rotation'],shift)
        high = transform(element['to'],element['rotation'],shift)
        dx,dz = high[0]-low[0],high[2]-low[2]
        if dx*dx+dz*dz <= EPSILON:
            continue
        assert element['from'][0] == element['to'][0] or element['from'][2] == element['to'][2]
        uv = tuple(tuple(face['uv']) for face in element['faces'].values())
        yield low,high,dx,dz,uv


def matches(vertices, plane, origin_delta):
    low,high,dx,dz,uv = plane
    denom = dx*dx+dz*dz
    for x,y,z,u,v in vertices:
        x,y,z = (x+origin_delta[0]-low[0],y+origin_delta[1],z+origin_delta[2]-low[2])
        along = (dx*x+dz*z)/denom
        if abs(dx*z-dz*x)>EPSILON or not -EPSILON<=along<=1+EPSILON:
            return False
        if not low[1]-EPSILON<=y<=high[1]+EPSILON:
            return False
    return any(all(min(u0,u1)-EPSILON<=u<=max(u0,u1)+EPSILON and
                   min(v0,v1)-EPSILON<=v<=max(v0,v1)+EPSILON for _,_,_,u,v in vertices)
               for u0,v0,u1,v1 in uv)


def main():
    data = json.loads(DATA.read_bytes())
    owners = {tuple(pos): owner for pos,owner,_ in data['old']}
    footprints = {tuple(pos): {tuple(p) for p,_ in cells} for pos,_,cells in data['old']}
    in_cell = defaultdict(set)
    for pos,footprint in footprints.items():
        for cell in footprint:
            in_cell[cell].add(pos)
    visuals = {tuple(pos):index for pos,index in data['visuals']}
    states = {tuple(pos): (state,index,be) for pos,state,index,be in data['found']}
    shapes = {int(i):v for i,v in data['grass_cells'].items()}
    with ZipFile(JAR) as jar:
        catalog = json.loads(jar.read(NATIVE+'catalog.json'))
        static = json.loads(jar.read(STATIC))
        quads = dict(selected_meshes(jar,catalog,{v['mesh'] for v in shapes.values()}))
        planes = {}
        pose_map = {}
        for pos,owner in owners.items():
            shift = tuple(owner['payload'].get('SourceShift',[0,0,0]))
            lookup = owner['id'],shift
            if lookup not in planes:
                planes[lookup] = list(source_planes(jar,owner))
            poses = [p['key'] for p in static['poses'] if p['registryId']==owner['id']
                     and p['properties']==owner['properties'] and tuple(p['shift'])==shift and p['mountY']==0]
            assert len(poses)==1, (owner,poses)
            pose_map[pos] = poses[0]
    quad_owners = {}
    grass_sprites = {i for i,m in enumerate(catalog['materials']) if m['sprite'].endswith('/spirelamp_0046')}
    for pos,index in visuals.items():
        memberships = {}
        for qi,quad in enumerate(quads[shapes[index]['mesh']]):
            if struct.unpack_from('>I',quad)[0] not in grass_sprites:
                continue
            vertices = [struct.unpack_from('>5f',quad,11+32*i) for i in range(4)]
            for origin in in_cell.get(pos,()):
                owner=owners[origin]
                key=(owner['id'],tuple(owner['payload'].get('SourceShift',[0,0,0])))
                delta=tuple(a-b for a,b in zip(pos,origin))
                if any(matches(vertices,plane,delta) for plane in planes[key]):
                    memberships.setdefault(qi,[]).append(origin)
        if memberships:
            quad_owners[pos]=memberships
    print('Cells with at least one source-matched quad',len(quad_owners),flush=True)
    print('Unambiguous matches at screenshot',
          [(p,quad_owners.get(p)) for p in sorted(footprints[(101,118,-748)])],flush=True)
    residuals = {}
    for pos, membership in quad_owners.items():
        cell = catalog['cells'][visuals[pos]]
        remaining = tuple(sorted(q for qi,q in enumerate(quads[cell['mesh']]) if qi not in membership))
        if remaining:
            residuals[pos] = remaining
    keys = set(residuals.values())
    mesh_matches = defaultdict(list)
    with ZipFile(JAR) as jar:
        sizes = {len(key) for key in keys}
        for i, mesh in selected_meshes(jar,catalog,{i for i,m in enumerate(catalog['meshes']) if m['quadCount'] in sizes}):
            key=tuple(sorted(mesh))
            if key in keys:
                mesh_matches[key].append(i)
    by_mesh = defaultdict(list)
    for i, cell in enumerate(catalog['cells']):
        by_mesh[cell['mesh']].append(i)
    print('Mixed residual matching existing meshes',sum(key in mesh_matches for key in residuals.values()),
          'of',len(residuals))
    for pos in sorted(footprints[(101,118,-748)] & residuals.keys()):
        old = catalog['cells'][visuals[pos]]
        fields = ('collision','culling','fluid','optics','localFunction','climbing')
        after = [i for mesh in mesh_matches[residuals[pos]] for i in by_mesh[mesh]
                 if all(catalog['cells'][i][f]==old[f] for f in fields)]
        print('Screenshot residual',pos,after[:25], 'mesh',mesh_matches[residuals[pos]][:25])
    proposed = set(p for m in quad_owners.values() for group in m.values() for p in group)
    with (BASE/'bush-quad-provenance.json').open('w',encoding='utf-8') as out:
        json.dump([[pos,visuals[pos],sorted(groups.items())] for pos,groups in sorted(quad_owners.items())],out)
    recipes = {}
    for i,r in enumerate(static['recipes']):
        if r['opacity'] or r['luminance'] or r['collision']:
            continue
        if all(f['member'] for f in r['fragments']):
            key = frozenset((f['pose'],tuple(f['origin'])) for f in r['fragments'])
            recipes.setdefault(key,i)

    def classify(pos, candidate):
        state,visual,be = states.get(pos,(None,None,None))
        if be:
            return 'block_entity'
        if state is None:
            return 'missing_chunk'
        if state in AIR and visual is None:
            return 'clear'
        if not state.startswith('bloodborne_dw:native_physics_') or visual not in shapes:
            return 'non_grass_block'
        shape = shapes[visual]
        if shape['base'] is not None or shape['collision'] or shape['fluid'] is not None:
            return 'native_substrate'
        quad_map=quad_owners.get(pos,{})
        if len(quad_map)!=len(quads[shape['mesh']]):
            return 'mixed_geometry'
        if any(not any(owner in candidate for owner in group) for group in quad_map.values()):
            return 'shared_unrestored_grass'
        return 'clear'

    blocked={}
    active=set(proposed)
    while True:
        drop={}
        for pos in sorted({cell for origin in active for cell in footprints[origin]}):
            roots=in_cell[pos]&active
            reason=classify(pos,active)
            if reason=='clear':
                parts=frozenset((pose_map[o],tuple(a-b for a,b in zip(o,pos))) for o in roots)
                if parts not in recipes:
                    reason='recipe_missing'
            if reason!='clear':
                for origin in roots:
                    drop.setdefault(origin,(reason,pos))
        if not drop:
            break
        blocked.update(drop)
        active.difference_update(drop)
    edits={}
    for pos in sorted({cell for origin in active for cell in footprints[origin]}):
        roots=in_cell[pos]&active
        parts=frozenset((pose_map[o],tuple(a-b for a,b in zip(o,pos))) for o in roots)
        edits[pos]=recipes[parts]
    print('Historically matched',len(proposed),'restorable',len(active),'cells',len(edits),
          'blocked',Counter(r for r,_ in blocked.values()),flush=True)
    with PLAN.open('w',encoding='utf-8') as out:
        json.dump({'catalog':data['catalog_hash'],
                   'restored':sorted(active),
                   'blocked':[[p,*blocked[p]] for p in sorted(blocked)],
                   'edits':[[p,states[p][0],states[p][1],r] for p,r in edits.items()]},
                  out,indent=1)
    bridge_plan(catalog,proposed,footprints,in_cell,states,visuals,quad_owners,shapes,
                residuals,mesh_matches,by_mesh,pose_map,recipes,edits,data['catalog_hash'])


def bridge_plan(catalog,proposed,footprints,in_cell,states,visuals,quad_owners,shapes,
                residuals,mesh_matches,by_mesh,pose_map,recipes,pure_edits,catalog_hash):
    whole_ids = {cell['cell'] for obj in catalog['wholeObjects'] for cell in obj['cells']}
    fields = ('baseState','collision','culling','fluid','optics','localFunction','climbing')
    # This is the exact dry, nonemitting, transparent carrier profile. Existing
    # solid/fluid/functional cells remain outside this bridge repair.
    dry = catalog['optics'][0]
    assert not dry['opaque'] and dry['sidedTransparency'] and not dry['opacity'] and not dry['luminance']

    def bridge_ready(index):
        cell=catalog['cells'][index]
        return (index not in whole_ids and cell['optics']==0 and cell['fluid'] is None
                and cell['localFunction'] is None and not cell['climbing'])

    def classify(pos,roots,candidates):
        state,index,be=states.get(pos,(None,None,None))
        if be or state is None:
            return 'block_entity_or_missing_chunk',None
        if state in AIR and index is None:
            return 'clear',None
        if not state.startswith('bloodborne_dw:native_physics_') or index is None or not bridge_ready(index):
            return 'protected_state',None
        old=catalog['cells'][index]
        groups=quad_owners.get(pos,{})
        if any(not all(o in candidates for o in group) for group in groups.values()):
            return 'shared_unrestored_grass',None
        if not groups:
            return 'bridge',index
        if pos not in residuals:
            if old['collision'] or old['baseState'] is not None or old['culling']:
                return 'native_substrate',None
            return 'clear',None
        for mesh in mesh_matches.get(residuals[pos],()):
            for ident in by_mesh[mesh]:
                other=catalog['cells'][ident]
                if bridge_ready(ident) and all(other[k]==old[k] for k in fields):
                    return 'bridge',ident
        return 'residual_geometry_missing',None

    active=set(proposed)
    blocked={}
    while True:
        drop={}
        for pos in sorted({c for owner in active for c in footprints[owner]}):
            roots=active&in_cell[pos]
            reason,_=classify(pos,roots,active)
            if reason not in ('clear','bridge'):
                for root in roots:
                    drop.setdefault(root,(reason,pos))
        if not drop:
            break
        blocked.update(drop)
        active.difference_update(drop)
    edits={}
    legacy_roots=set()
    roots_by_cell={}
    for pos in sorted({c for owner in active for c in footprints[owner]}):
        roots=in_cell[pos]&active
        roots_by_cell[pos]=roots
        reason,base=classify(pos,roots,active)
        assert reason in ('bridge','clear')
        parts=frozenset((pose_map[o],tuple(a-b for a,b in zip(o,pos))) for o in roots)
        recipe=recipes.get(parts)
        if base is not None or recipe is None:
            legacy_roots.update(roots)
        edits[pos]=(recipe,base)
    todo=list(legacy_roots)
    while todo:
        for pos in footprints[todo.pop()]:
            extra=roots_by_cell[pos]-legacy_roots
            legacy_roots.update(extra)
            todo.extend(extra)
    with MIXED_PLAN.open('w',encoding='utf-8') as out:
        json.dump({'catalog':catalog_hash,'restored':sorted(active),
                   'legacy':sorted(legacy_roots),'blocked':[[p,*blocked[p]] for p in sorted(blocked)],
                   'edits':[[p,states[p][0],states[p][1],
                             {'legacy_roots':sorted(roots_by_cell[p]),'native_base':base}
                             if roots_by_cell[p]&legacy_roots else recipe]
                            for p,(recipe,base) in edits.items()]},out,indent=1)
    print('Bridge plan',len(active),'objects including',len(legacy_roots),'composite owners;',
          len(edits),'cells;',sum(base is not None for _,base in edits.values()),'mixed cells;',
          'screenshot',(101,118,-748) in active,'blocked',Counter(r for r,_ in blocked.values()),flush=True)


if __name__=='__main__':
    main()
