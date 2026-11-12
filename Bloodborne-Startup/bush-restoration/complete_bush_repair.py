"""Restore historical root blocks while keeping foreign cells and exact remaining quads."""
from collections import Counter, defaultdict
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import shutil
import struct
import sys
import uuid
from zipfile import ZIP_DEFLATED, ZipFile

SCRIPT_DIR=Path(__file__).resolve().parent
REPO=SCRIPT_DIR.parents[1] if SCRIPT_DIR.parent.name=='Bloodborne-Startup' else SCRIPT_DIR
sys.path.insert(0,str(REPO/'DW-City-Visuals/tools'))
from map_io import world_io
sys.modules['world_io']=world_io
from map_io.studio_save import (AIR, HELPER, EMPTY_SHAPE, OwnerShard, WorldEdit,
                               list_tag, name, owner_uuid, rewrite_be, state_tag, uuid_order)
from audit_grass import BASE, CURRENT
from bush_native_catalog import BushNativeCatalog
from probe_bush import visual_entries
from restore_legacy_grass import LEDGER, SHARD_DIR, cell_owners, chunk_key, old_entities
from world_io import (NbtFile, Tag, block_state_key, compound, decode_nbt, encode_nbt,
                      read_nbt, write_nbt)

OUT=BASE/'worlds/bush-restoration-release/bloodborne'
RELEASE_JAR=BASE/'DreamWalker-v22-Compact-Startup-Bush-Restoration.jar'
RELEASE_WORLD=BASE/'Bloodborne-v22-Bush-Restoration-world.zip'
REPORT=BASE/'Bloodborne-v22-Bush-Restoration-report.json'
PARTITION=uuid.UUID('8ade0487-04ca-4969-8af2-c366d83f24e8')


def xyz_add(pos,delta):
    return tuple(a+b for a,b in zip(pos,delta))


def xyz_sub(a,b):
    return tuple(i-j for i,j in zip(a,b))


def pos_code(pos):
    x,y,z=pos
    unsigned=((x&0x3ffffff)<<38)|((z&0x3ffffff)<<12)|(y&4095)
    return unsigned if unsigned<2**63 else unsigned-2**64


class SavedDimension:
    def __init__(self,root):
        self.root=root
        self.world=WorldEdit(root)
        self.shards={}
        self.rows={}
        self.changed_rows=set()

    def shard(self,pos):
        cx,_,cz=pos
        cx,cz=cx//16,cz//16
        key=cx,cz
        if key not in self.shards:
            self.shards[key]=OwnerShard(self.root/SHARD_DIR/f'owners-{cx}.{cz}.nbt',cx,cz)
        return self.shards[key]

    def visuals(self,chunk):
        key=chunk.fields['xPos'].value,chunk.fields['zPos'].value
        if key not in self.rows:
            self.rows[key]=dict(visual_entries(chunk.fields))
        return key,self.rows[key]

    def index(self,pos):
        chunk=self.world.at(pos)
        sy,idx=chunk.cell(pos)
        return self.visuals(chunk)[1].get(sy,{}).get(idx)

    def edit_native(self,pos,original,next_index):
        chunk=self.world.at(pos)
        key,sections=self.visuals(chunk)
        sy,idx=chunk.cell(pos)
        assert sections[sy][idx]==original
        if next_index is None:
            del sections[sy][idx]
        else:
            sections[sy][idx]=next_index
        self.changed_rows.add(key)

    def finish(self,new_catalog):
        for key in self.changed_rows:
            chunk=self.world.chunks[key]
            visuals=compound(chunk.fields['DreamwalkerVisuals'])
            old_rows={compound(row)['Y'].value:row for row in visuals['Sections'].value}
            for sy,entries in self.rows[key].items():
                field=compound(old_rows[sy])
                values=[(value<<12)|i for i,value in sorted(entries.items())]
                field['Entries']=Tag(11,[n if n<2**31 else n-2**32 for n in values])
            visuals['Sections']=list_tag(row for sy,row in old_rows.items() if self.rows[key][sy])
            visuals['Catalog']=Tag(8,new_catalog)
            chunk.entity_change=True
        self.world.flush()
        for chunk_key_pair,chunk in self.world.chunks.items():
            if chunk.changed or chunk.entity_change:
                cx,cz=chunk_key_pair
                saved=self.world.region(cx,cz).get_chunk(cx%32,cz%32)
                assert saved.nbt()==chunk.nbt
        if not any(shard.dirty for shard in self.shards.values()):
            return
        ledger=read_nbt(self.root/LEDGER)
        fields=compound(compound(ledger.root)['data'])
        assert fields['OwnerShardSchema'].value==1 and not fields['cells'].value
        current=set(fields['OwnerShardChunks'].value)
        for (cx,cz),shard in self.shards.items():
            if shard.dirty:
                current.add(chunk_key(cx,cz))
                shard.path.parent.mkdir(parents=True,exist_ok=True)
                shard.flush()
        fields['OwnerShardChunks']=Tag(12,sorted(current))
        write_nbt(self.root/LEDGER,ledger)
        assert read_nbt(self.root/LEDGER)==ledger


def tagged_props(block_data):
    return {k:v.value for k,v in compound(block_data['properties']).items()}


def old_owner_data(roots,footprints,entities,placements,scope):
    owners={}
    for original in sorted(placements):
        anchor=placements[original]
        fields=compound(entities[original])
        old_owner=fields['resident']
        assert compound(old_owner)['id'].value==roots[original]['id']
        identity=uuid.uuid5(PARTITION,scope+':'+','.join(map(str,original)))
        tag=Tag(10,{'id':compound(old_owner)['id'],'root':Tag(11,list(anchor)),
                    'uuid':Tag(11,list(struct.unpack('>4i',identity.bytes)))})
        _,old_rows=cell_owners(entities[original])
        previous=next(compound(row)['rootData'] for row in old_rows.value if compound(row)['owner']==old_owner)
        source=compound(deepcopy(previous))
        payload=decode_nbt(source['payload'].value).root
        assert payload==fields['payload']
        props=tagged_props(source)
        assert props['rotation']=='0'
        if anchor!=original:
            payload=deepcopy(payload)
            shift=compound(payload).get('SourceShift',Tag(9,[Tag(6,0.0)]*3,6)).value
            vector=xyz_add(tuple(float(v.value) for v in shift),xyz_sub(original,anchor))
            compound(payload)['SourceShift']=list_tag((Tag(6,v) for v in vector),6)
            source['payload']=Tag(7,encode_nbt(NbtFile('',payload)))
        block=state_tag(source['id'].value,props)
        footprint={}
        for cell in footprints[original]:
            _,rows=cell_owners(entities[cell])
            parts=[compound(row) for row in rows.value if compound(row)['owner']==old_owner]
            assert len(parts)==1,(original,cell)
            footprint[cell]=Tag(10,{key:parts[0][key] for key in ('collision','selection')})
            assert not parts[0]['collision'].value
        footprint.setdefault(anchor,EMPTY_SHAPE)
        owners[original]=(tag,Tag(10,source),payload,block,footprint)
    return owners


def restore(dimension,catalog,audit,provenance,historical_loader,scope):
    roots={tuple(p):owner for p,owner,_ in audit['old']}
    footprints={tuple(p):{tuple(cell) for cell,_ in members} for p,_,members in audit['old']}
    groups={tuple(pos):(index,{i:{tuple(o) for o in rows} for i,rows in memberships})
            for pos,index,memberships in provenance}
    active=set.union(set(),*(owners for _,rows in groups.values() for owners in rows.values()))
    initial=len(active)
    snapshots={}
    for pos in {cell for origin in active for cell in (footprints[origin]|{origin})}:
        try:
            chunk=dimension.world.at(pos)
        except (FileNotFoundError,ValueError):
            continue
        snapshots[pos]=(chunk.state(pos),chunk.entities.get(pos),dimension.index(pos))
    reserved=set(active)
    placements={}
    rejected={}

    def anchor_ok(pos):
        if pos not in snapshots:
            return False
        block,entity,index=snapshots[pos]
        key=name(block)
        if key.startswith('bloodborne_dw:native_physics_'):
            _, quads = groups.get(pos, (None, {}))
            return (entity is None and index is not None and catalog.can_split(index)
                    and catalog.empty_after(index, quads))
        if key==HELPER and entity is not None:
            fields=compound(entity)
            return ('resident' not in fields and not compound(fields['payload']) and
                    'NativeGridBase' not in fields)
        return key in AIR and entity is None

    def add_snapshot(pos):
        if pos not in snapshots:
            try:
                chunk=dimension.world.at(pos)
                snapshots[pos]=(chunk.state(pos),chunk.entities.get(pos),dimension.index(pos))
            except (FileNotFoundError,ValueError):
                pass

    for pos in sorted(active):
        if anchor_ok(pos):
            placements[pos]=pos
            continue
        candidates=sorted(footprints[pos]-reserved,
                          key=lambda c:(sum(v*v for v in xyz_sub(c,pos)),c))
        for candidate in candidates:
            if anchor_ok(candidate):
                placements[pos]=candidate
                reserved.add(candidate)
                break
        else:
            nearby=sorted({xyz_add(pos,(dx,dy,dz)) for dx in range(-5,6)
                           for dz in range(-5,6) for dy in range(-3,4)}-reserved,
                          key=lambda c:(sum(v*v for v in xyz_sub(c,pos)),c[1]!=pos[1],c))
            for candidate in nearby:
                add_snapshot(candidate)
                if anchor_ok(candidate):
                    placements[pos]=candidate
                    reserved.add(candidate)
                    break
            else:
                rejected[pos]='no_compatible_anchor'
    active=set(placements)
    for pos,(visual,rows) in groups.items():
        if pos not in snapshots:
            for origins in rows.values():
                rejected.update((o,'missing_saved_cell') for o in origins)
            continue
        state,entity,observed=snapshots[pos]
        if observed!=visual or not name(state).startswith('bloodborne_dw:native_physics_') or not catalog.can_split(visual):
            for origins in rows.values():
                rejected.update((o,'unsupported_native_fragment') for o in origins)
    for pos in list(placements):
        if pos in rejected:
            del placements[pos]
    active=set(placements)
    # A single coincident quad may be attributable to several old roots.
    # Restore all its owners together or keep that face and all their roots.
    while True:
        blocked=set()
        for _,rows in groups.values():
            for origins in rows.values():
                if origins-active:
                    blocked.update(origins&active)
        if not blocked:
            break
        active-=blocked
        for origin in blocked:
            placements.pop(origin)
            rejected[origin]='shared_with_unrestored_root'

    old_positions={p for o in active for p in footprints[o]|{o}}
    historic=dict(historical_loader(old_positions))
    assert old_positions<=historic.keys()
    owners=old_owner_data(roots,footprints,historic,placements,scope)
    changes={}
    members=defaultdict(list)
    for o,(identity,root_data,payload,state,parts) in owners.items():
        anchor=placements[o]
        assert anchor not in changes
        changes[anchor]=(identity,payload,state)
        dimension.shard(anchor).set_members(identity,{xyz_sub(c,anchor):shape for c,shape in parts.items()})
        for cell,shape in parts.items():
            members[cell].append((identity,root_data,shape))

    count=Counter()
    edited=[]
    for pos in sorted(members):
        chunk=dimension.world.at(pos)
        state,be,index=snapshots[pos]
        shard=dimension.shard(pos)
        added=members[pos]
        current_rows=shard.cells.get(pos,())
        # Older saves can store an identical owner set in the BE and in the
        # shard. Preserve both and promote a BE-only record if necessary.
        if be is not None and compound(be)['id'].value==HELPER:
            if 'OwnerDataV1' in compound(be):
                _,be_rows=cell_owners(be)
            else:
                be_rows=compound(be).get('owners',list_tag())
            current={owner_uuid(compound(tag)['owner']) for tag in shard.expand(current_rows)}
            for entry in be_rows.value:
                values=compound(entry)
                identity=owner_uuid(values['owner'])
                if identity not in current:
                    shard.add(pos,values['owner'],values['rootData'],
                              Tag(10,{k:values[k] for k in ('collision','selection')}))
                    current.add(identity)
        for identity,root_data,shape in added:
            shard.add(pos,identity,root_data,shape)
        shard.cells[pos].sort(key=lambda ref:uuid_order(owner_uuid(shard.owners[ref.value[0]])))
        entries=shard.expand(shard.cells[pos])
        after=index
        remove={i for i,origins in groups.get(pos,(None,{}))[1].items() if origins<=active}
        anchor=changes.get(pos)
        if index is not None and (anchor is not None or remove):
            assert name(state).startswith('bloodborne_dw:native_physics_')
            after=None if catalog.empty_after(index,remove) else catalog.split(index,remove)
            dimension.edit_native(pos,index,None if anchor else after)
            count['removed_baked_quads']+=len(remove)
        if anchor is not None:
            owner,payload,block=anchor
            # Source grass and the native transparent carrier disagree on a
            # worldless light predicate. Keep mixed native cells independent.
            assert after is None
            chunk.set_state(pos,block)
            new_be=rewrite_be(pos,be,owner,payload,entries)
            chunk.set_entity(pos,new_be)
            count['normal_roots']+=1
        elif be is not None and compound(be)['id'].value==HELPER:
            fields=compound(be)
            chunk.set_entity(pos,rewrite_be(pos,be,fields.get('resident'),fields['payload'],entries))
            count['existing_helpers_updated']+=1
        elif name(state) in AIR or (index is not None and after is None):
            chunk.set_state(pos,state_tag(HELPER))
            chunk.set_entity(pos,rewrite_be(pos,None,None,Tag(10,{}),entries))
            count['new_helpers']+=1
        else:
            count['foreign_carriers_preserved']+=1
        if remove or anchor:
            edited.append({'pos':pos,'before':block_state_key(state),'visual':index,'after':after,
                           'quads_removed':sorted(remove),'root':anchor is not None})
    assert len(active)+len(rejected)==initial
    return {'matched_roots':initial,'counts':dict(count),'edits':edited,
            'restored':[[p,placements[p],roots[p]['id']] for p in sorted(active)],
            'unresolved':[[p,rejected[p]] for p in sorted(rejected)]}


def integrate_stencils(audit,provenance,extra,scope):
    """Translate verified complete stencils from a historical example."""
    template={tuple(pos):(owner,parts) for pos,owner,parts in audit['old']}
    samples={tuple(sample) for _,_,sample,_ in extra}
    original=dict(old_entities({tuple(p) for root in samples
                               for p,_ in template[root][1]}|samples)) if samples else {}
    owners=defaultdict(list)
    residents={}
    inherited=dict((tuple(p),(i,{k:{tuple(o) for o in os} for k,os in groups}))
                   for p,i,groups in provenance)
    records=list(audit['old']) if scope=='overworld' else []
    synthetic={}
    for start,_,sample,parts in extra:
        root=tuple(start)
        source=tuple(sample)
        owner,footprint=template[source]
        delta=xyz_sub(root,source)
        orig=compound(original[source])['resident']
        transformed=deepcopy(orig)
        compound(transformed)['root']=Tag(11,list(root))
        _,tags=cell_owners(original[source])
        root_data=next(compound(t)['rootData'] for t in tags.value if compound(t)['owner']==orig)
        root_payload=decode_nbt(compound(root_data)['payload'].value).root
        residents[root]=(transformed,root_payload)
        positions=[]
        for pos,_ in footprint:
            p=tuple(pos)
            target=xyz_add(p,delta)
            _,tags=cell_owners(original[p])
            contribution=next(compound(t) for t in tags.value if compound(t)['owner']==orig)
            owners[target].append(Tag(10,{'owner':transformed,**{k:contribution[k] for k in
                                                               ('collision','selection','rootData')}}))
            positions.append([target,1])
        records.append([root,owner,positions])
        for pos,idx,faces in parts:
            current,groups=inherited.setdefault(tuple(pos),(idx,{}))
            assert current==idx
            for face in faces:
                assert face not in groups
                groups[face]={root}
    for pos,rows in owners.items():
        owner,payload=residents.get(pos,(None,Tag(10,{})))
        synthetic[pos]=rewrite_be(pos,None,owner,payload,rows)

    def history(requested):
        # In the main dimension a stencil's footprint may overlap a historic
        # bush. Include both owners in that cell's source data.
        baseline=dict(old_entities(requested)) if scope=='overworld' else {}
        for pos in requested:
            if pos not in synthetic:
                if pos in baseline:
                    yield pos,baseline[pos]
                continue
            fallback=baseline.get(pos)
            if fallback is None:
                yield pos,synthetic[pos]
                continue
            native=compound(fallback)
            _,historic=cell_owners(fallback) if 'OwnerDataV1' in native else (None,list_tag())
            synthetic_fields=compound(synthetic[pos])
            _,extra_rows=cell_owners(synthetic[pos])
            chosen=synthetic_fields if pos in residents else native
            yield pos,rewrite_be(pos,None,chosen.get('resident'),chosen['payload'],
                                 historic.value+extra_rows.value)

    return {'old':records},[[p,i,sorted(groups.items())] for p,(i,groups) in inherited.items()],history


def main():
    assert not OUT.exists() and not RELEASE_JAR.exists() and not RELEASE_WORLD.exists()
    audit=json.loads((BASE/'grass-audit.json').read_bytes())
    provenance=json.loads((BASE/'bush-quad-provenance.json').read_bytes())
    stencils=BASE/'additional-bush-stencils.json'
    additional=json.loads(stencils.read_bytes()) if stencils.exists() else {}
    dimensions={}
    dimensions['overworld']=integrate_stencils(audit,provenance,
        additional.get('overworld',{}).get('instances',[]),'overworld')
    if additional.get('bloodborne_old',{}).get('instances'):
        dimensions['bloodborne_old']=integrate_stencils(audit,[],
            additional['bloodborne_old']['instances'],'bloodborne_old')
    catalogue=BushNativeCatalog()
    # Include native visuals at possible anchors even where the old object's
    # polygons were already absent.
    catalogue.load_meshes({i for _,provenance,_ in dimensions.values() for _,i,_ in provenance})
    shutil.copytree(CURRENT,OUT,ignore=shutil.ignore_patterns('session.lock'))
    results={}
    modified={}
    for scope,(owners,provenance,history) in dimensions.items():
        target=OUT if scope=='overworld' else OUT/'dimensions/bloodborne_dw/bloodborne_old'
        dimension=SavedDimension(target)
        result=restore(dimension,catalogue,owners,provenance,history,scope)
        results[scope]=result
        modified[scope]=dimension
        print(scope,result['matched_roots'],result['counts'],
              Counter(r for _,r in result['unresolved']),flush=True)
    next_hash=catalogue.package(RELEASE_JAR)
    for dimension in modified.values():
        dimension.finish(next_hash)
    with ZipFile(RELEASE_WORLD,'x',ZIP_DEFLATED,compresslevel=4) as archive:
        for path in sorted(OUT.rglob('*')):
            if path.is_file():
                archive.write(path,'bloodborne/'+path.relative_to(OUT).as_posix())
    with ZipFile(RELEASE_WORLD) as world:
        assert world.testzip() is None
    files={}
    for path in (RELEASE_JAR,RELEASE_WORLD):
        with path.open('rb') as stream:
            files[path.name]={'bytes':path.stat().st_size,'sha256':hashlib.file_digest(stream,'sha256').hexdigest()}
    with REPORT.open('x',encoding='utf-8') as stream:
        json.dump({'catalog':next_hash,'input_catalog':catalogue.original_hash,'files':files,
                   **results,'residual_catalog':catalogue.retained,
                   'stencils':{dim:{k:v for k,v in data.items() if k!='instances'}
                               for dim,data in additional.items()}},stream,indent=1)


if __name__=='__main__':
    main()
