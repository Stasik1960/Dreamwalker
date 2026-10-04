"""Rebuild complete owners in a copied city using its aligned source palette.

Only Bloodborne cells are retired. Foreign blocks/entities are never carriers
for the new ownership NBT. Imported owners may share collision cells once;
this does not change the normal in-game placement rules.
"""
from __future__ import annotations
import argparse, copy, hashlib, json, os, shutil, zipfile
from collections import Counter
from pathlib import Path
import numpy as np
from convert_logical_world import (NS, PART, Chunk, as_tag_state, state_of,
    block_pos_long, list_region_files)
from convert_unified_city import atomic_json
from upgrade_gallery_city import StreamingWorld, parse_state
from convert_document_world import add_owner_binding
from city_palette import helper_bindings
from city_palette import audit_helpers
from world_io import (Tag, RegionFile, TAG_COMPOUND, TAG_LIST, TAG_STRING,
    TAG_LONG, compound, section_blocks, block_state_key)

AIR=('minecraft:air',())
DIM='minecraft:overworld'

def set_bindings(data, bindings):
    bindings=sorted(set(bindings))
    if not bindings:return False
    root,owner=bindings[0]
    data['Root']=Tag(TAG_LONG,block_pos_long(*root));data['Owner']=Tag(TAG_STRING,owner)
    if len(bindings)==1:data.pop('Owners',None)
    else:data['Owners']=Tag(TAG_LIST,[Tag(TAG_COMPOUND,{
        'Root':Tag(TAG_LONG,block_pos_long(*root)),'Owner':Tag(TAG_STRING,owner)
    })for root,owner in bindings],TAG_COMPOUND)
    return True

def in_gallery(p, bounds):
    return bounds is None or all(bounds[i]<=p[i]<=bounds[i+3]for i in range(3))

def clean_chunk(chunk, retained, gallery_y, gallery_bounds=None):
    """Remove retired art and the old gallery; keep native/manual guest owners."""
    bindings={};entities=[];removed=Counter()
    for tag in chunk.entities()[1]:
        data=compound(tag);y=int(data.get('y',Tag(3,-1000)).value)
        position=tuple(int(data.get(a,Tag(3,-1000)).value)for a in 'xyz')
        if y>=gallery_y and in_gallery(position,gallery_bounds):continue
        if data.get('id',Tag(TAG_STRING,'')).value==PART:
            owners=[(root,owner)for root,owner in helper_bindings(data)if owner in retained]
            if not set_bindings(data,owners):continue
            p=tuple(int(data[a].value)for a in 'xyz');bindings[p]=owners
        entities.append(tag)
    if len(entities)!=len(chunk.entities()[1]) or bindings:
        chunk.root()['block_entities']=Tag(TAG_LIST,entities,TAG_COMPOUND);chunk.changed=True
    for sec in list(chunk.root().get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value):
        sy=int(compound(sec)['Y'].value);bs=compound(sec).get('block_states')
        if not bs:continue
        names=[compound(t)['Name'].value for t in compound(bs)['palette'].value]
        retired=any(n.startswith(NS)and n not in retained for n in names)
        gallery_overlap=(sy*16+15>=gallery_y and (gallery_bounds is None or
            chunk.x*16<=gallery_bounds[3]and chunk.x*16+15>=gallery_bounds[0]and
            chunk.z*16<=gallery_bounds[5]and chunk.z*16+15>=gallery_bounds[2]))
        if not retired and not gallery_overlap:continue
        if not retired and all(n in {'minecraft:air','minecraft:cave_air','minecraft:void_air'}for n in names):continue
        chunk.changed=True
        _,palette,indices=chunk._loaded(sy)
        air_key=block_state_key(as_tag_state(AIR))
        lookup=chunk.loaded[sy][0]
        if air_key not in lookup:lookup[air_key]=len(palette);palette.append(as_tag_state(AIR))
        air_index=lookup[air_key]
        for i,tag in enumerate(list(palette)):
            name=compound(tag)['Name'].value
            if name.startswith(NS) and name not in retained:
                selected=np.flatnonzero(indices==i);removed[name]+=len(selected)
                indices[selected]=air_index
        if sy*16+15>=gallery_y:
            start=max(0,gallery_y-sy*16)*256
            if gallery_bounds is None:indices[start:]=air_index
            else:
                positions=np.arange(start,4096)
                mask=((chunk.x*16+(positions&15)>=gallery_bounds[0])&
                      (chunk.x*16+(positions&15)<=gallery_bounds[3])&
                      (chunk.z*16+((positions>>4)&15)>=gallery_bounds[2])&
                      (chunk.z*16+((positions>>4)&15)<=gallery_bounds[5]))
                indices[positions[mask]]=air_index
    for p in bindings:
        current=chunk.get(*p,{})
        if current==AIR:chunk.set(*p,(PART,()))
        elif current[0]!=PART and current[0]not in retained:
            raise ValueError('retained helper on foreign carrier')
    for name in ('block_ticks','fluid_ticks'):
        ticks=chunk.root().get(name)
        if ticks:
            ticks.value[:]=[t for t in ticks.value if
                not (int(compound(t).get('y',Tag(3,-1000)).value)>=gallery_y and
                     in_gallery(tuple(int(compound(t).get(a,Tag(3,-1000)).value)for a in 'xyz'),gallery_bounds)) and
                not (compound(t).get('i',Tag(TAG_STRING,'')).value.startswith(NS)
                and compound(t)['i'].value not in retained)]
    return removed

def extract_city(zip_path,output):
    """Use the gallery's exact input-file inventory, then strip its upper band."""
    output=Path(output).resolve()
    if output.exists():raise ValueError('output already exists')
    with zipfile.ZipFile(zip_path)as archive:
        name=next(n for n in archive.namelist()if n.endswith('/editable-gallery-manifest.json'))
        prefix=name[:-len('editable-gallery-manifest.json')]
        manifest=json.loads(archive.read(name))
        allowed=set(manifest['sourceHashes'])
        output.mkdir(parents=True)
        for rel in sorted(allowed):
            p=Path(rel)
            if p.is_absolute()or '..'in p.parts:raise ValueError('unsafe city path')
            if p.parts[0]in {'playerdata','advancements','stats'}:continue
            dest=output/p;dest.parent.mkdir(parents=True,exist_ok=True)
            raw=archive.read(prefix+rel)
            # Region files contain the added gallery. Foreign entity/POI files
            # were not edited by that builder and must match its input hashes.
            if p.parts[0]in {'entities','poi'} and hashlib.sha256(raw).hexdigest()!=manifest['sourceHashes'][rel]:
                raise ValueError('foreign source file differs from gallery baseline '+rel)
            dest.write_bytes(raw)
    return manifest

def prepare_source(zip_path, city, output):
    output=Path(output).resolve();output.mkdir(parents=True,exist_ok=True)
    required={p.name for p in (Path(city)/'region').glob('*.mca')}
    with zipfile.ZipFile(zip_path)as archive:
        found=[n for n in archive.namelist()if '/dimensions/eh_s2/yharnam/region/'in n and n.endswith('.mca')]
        names={Path(n).name:n for n in found}
        if len(names)!=len(found):raise ValueError('duplicate source region names')
        if required-names.keys():raise ValueError('source region missing '+str(required-names.keys()))
        for name in sorted(required):
            dest=output/'region'/name;dest.parent.mkdir(parents=True,exist_ok=True)
            raw=archive.read(names[name])
            if dest.exists() and dest.read_bytes()!=raw:raise ValueError('source extraction cache differs '+name)
            if not dest.exists():dest.write_bytes(raw)
    return output

def load_catalog(city):
    definitions={};geometry={};defaults={}
    for scope in (Path(city),Path(city).parent/'logical'):
        raw=json.loads((scope/'geometry.json').read_bytes())
        for d in json.loads((scope/'definitions.json').read_bytes())['blocks']:
            name=NS+d['id'];definitions[name]=d;defaults[name]=d['default']
            geometry[name]={s:raw.get('profiles',{}).get(g.get('ref'),g)
                for s,g in raw['blocks'][d['id']]['states'].items()}
    return definitions,geometry,defaults

def retained_ids(old_city):
    result=set()
    for scope in (Path(old_city),Path(old_city).parent/'logical'):
        for d in json.loads((scope/'definitions.json').read_bytes())['blocks']:
            if not d.get('unified'):result.add(NS+d['id'])
    return result

def place_owner(world, entities, root, state, cells, counts, cases):
    current=world.get(DIM,root)
    if current is None:counts['rootOutsideCity']+=1;return False
    if current[0]not in {PART,'minecraft:air','minecraft:cave_air','minecraft:void_air'}:
        # Existing approved roots are already represented; foreign data wins.
        counts['retainedRoot'if current[0].startswith(NS)else'foreignRoot']+=1
        if len(cases)<256:cases.append({'kind':'occupiedRoot','position':list(root),'state':current[0],'new':state[0]})
        return False
    world.set(DIM,root,state);counts['wholeOwners']+=1
    for off in cells:
        if off==(0,0,0):continue
        p=tuple(root[i]+off[i]for i in range(3));old=world.get(DIM,p)
        if old is None or not -64<=p[1]<320:counts['helperOutsideCity']+=1;continue
        if not old[0].startswith(NS) and old[0]not in {'minecraft:air','minecraft:cave_air','minecraft:void_air'}:
            counts['foreignCollisionCells']+=1
            if len(cases)<256:cases.append({'kind':'foreignCollisionCell','position':list(p),'state':old[0],'owner':state[0]})
            continue
        if not old[0].startswith(NS):world.set(DIM,p,(PART,()))
        existing=entities.get((DIM,*p))
        owners=[]if existing is None else helper_bindings(compound(existing))
        if len(set(owners)|{(root,state[0])})>16:
            raise ValueError('owner binding capacity exceeded at '+str(p))
        add_owner_binding(world,entities,DIM,p,root,state[0]);counts['collisionBindings']+=1
        if old[0].startswith(NS):counts['forcedSharedCollisionCells']+=1
    return True

def place_single_cell_batch(chunk, sy, selected, state, protected_indices, counts):
    """Palette rewrite for simple objects, avoiding millions of Python edits."""
    lookup,palette,indices=chunk._loaded(sy)
    acceptable=[i for i,t in enumerate(palette)if compound(t)['Name'].value in
        {PART,'minecraft:air','minecraft:cave_air','minecraft:void_air'}]
    available=selected[np.isin(indices[selected],acceptable)]
    if protected_indices:
        protected_mask=np.isin(selected,list(protected_indices))
        counts['approvedCompositeSourceCells']+=int(protected_mask.sum())
        selected=selected[~protected_mask]
        available=available[~np.isin(available,list(protected_indices))]
    counts['retainedOrForeignRoots']+=len(selected)-len(available)
    if not len(available):return
    target_key=block_state_key(as_tag_state(state))
    if target_key not in lookup:lookup[target_key]=len(palette);palette.append(as_tag_state(state))
    indices[available]=lookup[target_key];chunk.changed=True
    counts['wholeOwners']+=len(available)

def require_chunk_coverage(source, target):
    missing=set(source.chunks.rows)-set(target.chunks.rows)
    extra=set(target.chunks.rows)-set(source.chunks.rows)
    if missing or extra:raise ValueError('source/target chunk coverage mismatch missing='+str(sorted(missing)[:5])+' extra='+str(sorted(extra)[:5]))

def audit_saved_world(output, defaults, catalog):
    audit=audit_helpers(StreamingWorld(output,defaults,cache_limit=32),catalog)
    result={'checked':audit['checked'],'orphans':len(audit['orphans']),'ok':audit['ok']}
    if not audit['ok']:raise ValueError('helper audit failed '+str(audit['orphans'][:20]))
    return result

def convert(source_zip, original_zip, output, catalog, old_city, oracle, *, resume=False):
    output=Path(output).resolve();catalog=Path(catalog).resolve()
    checkpoint=output.parent/(output.name+'-checkpoint.json')
    def sha(path):
        h=hashlib.sha256()
        with Path(path).open('rb')as stream:
            for block in iter(lambda:stream.read(1048576),b''):h.update(block)
        return h.hexdigest()
    def current_inputs():
        result={str(Path(p).resolve()):sha(p)for p in (source_zip,original_zip,oracle)}
        for base in (catalog,Path(old_city)):
            for scope in (base,base.parent/'logical'):
                for name in ('definitions.json','geometry.json','raw-source-mapping.json'):
                    if (scope/name).exists():result[str((scope/name).resolve())]=sha(scope/name)
        return result
    inputs=current_inputs()
    def outputs():return {p.relative_to(output).as_posix():sha(p)for p in output.rglob('*')
                          if p.is_file()and p.name!='compact-city-conversion.json'}
    if resume:
        proof=json.loads(checkpoint.read_bytes())
        if proof['inputs']!=[str(Path(p).resolve())for p in (source_zip,original_zip,catalog,old_city,oracle)]:
            raise ValueError('resume input mismatch')
        if proof.get('inputHashes')!=inputs:raise ValueError('resume input contents changed')
        if proof.get('outputHashes')!=outputs():raise ValueError('resume output contents changed')
    else:
        if checkpoint.exists():raise ValueError('existing checkpoint; use resume')
        manifest=extract_city(source_zip,output)
        proof={'inputs':[str(Path(p).resolve())for p in (source_zip,original_zip,catalog,old_city,oracle)],
            'galleryY':manifest['galleryBand']['minY'],'cityBounds':manifest['cityBounds'],
            'galleryBounds':manifest['galleryBand']['bounds'],'inputHashes':inputs,
            'cleanedRegions':[],'convertedRegions':[],'counts':{},'cases':[]}
        proof['outputHashes']=outputs()
        atomic_json(checkpoint,proof)
    retained=retained_ids(old_city);world=StreamingWorld(output,{},cache_limit=32)
    counts=Counter(proof['counts']);cases=proof['cases']
    for path in sorted(world.by_region):
        relative=path.relative_to(output).as_posix()
        if relative in proof['cleanedRegions']:continue
        keys=[k for k,v in world.chunks.rows.items()if v[0]==path]
        for k in keys:counts.update(clean_chunk(world.chunks[k],retained,proof['galleryY'],proof['galleryBounds']))
        world.save();proof['cleanedRegions'].append(relative);proof['counts']=dict(counts);proof['outputHashes']=outputs();atomic_json(checkpoint,proof)
        print('cleaned',relative,len(keys),flush=True)
    original=prepare_source(original_zip,output,output.parent/(output.name+'-original-source'))
    source=StreamingWorld(original,{},cache_limit=8)
    defs,geo,defaults=load_catalog(catalog);world.defaults=defaults
    require_chunk_coverage(source,world)
    raw=json.loads((catalog/'raw-source-mapping.json').read_bytes())['states']
    mappings={parse_state(k):(v['id'],tuple(sorted(v['properties'].items())))for k,v in raw.items()}
    protected=set()
    for row in json.loads(Path(oracle).read_bytes())['occurrences']:
        # A historical match only consumes source cells if its construction
        # actually survived in this corrected city. Missing roots are restored
        # by the complete-source path rather than leaving invisible holes.
        if all((world.get(DIM,tuple(v['canonical_root']))or ('',()))[0]==NS+v['family']
               for v in row['outputs']):
            protected.update(tuple(c['position'])for c in row['source_cells'])
    protected_sections={}
    for x,y,z in protected:protected_sections.setdefault((x//16,y//16,z//16),set()).add((y&15)*256+(z&15)*16+(x&15))
    entities=world.block_entities();processed=0
    for path in sorted(source.by_region):
        relative=path.relative_to(original).as_posix()
        if relative in proof['convertedRegions']:continue
        for k,v in sorted(source.chunks.rows.items()):
            if v[0]!=path:continue
            chunk=source.chunks[k]
            for sec in chunk.root().get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
                bs=compound(sec).get('block_states')
                if not bs:continue
                if not any(state_of(t,{})in mappings for t in compound(bs)['palette'].value):continue
                values=section_blocks(sec)
                if values is None:continue
                palette,indices=values;sy=int(compound(sec)['Y'].value)*16;array=np.asarray(indices)
                for i,entry in enumerate(palette):
                    source_state=state_of(entry,{})
                    target=mappings.get(source_state)
                    if target is None:continue
                    selected=np.flatnonzero(array==i);counts['sourcePaletteRoots']+=len(selected)
                    if target==AIR:
                        counts['emptySourceModelsOmitted']+=len(selected);continue
                    statekey=','.join(a+'='+b for a,b in target[1])
                    cells=[tuple(map(int,p.split(',')))for p in geo[target[0]][statekey]['cells']]
                    if cells==[(0,0,0)]:
                        target_chunk=world.chunks[k]
                        exclusions=protected_sections.get((chunk.x,sy//16,chunk.z),set())
                        place_single_cell_batch(target_chunk,sy//16,selected,target,exclusions,counts)
                        processed+=len(selected)
                        continue
                    for index in selected:
                        index=int(index);root=(chunk.x*16+(index&15),sy+(index>>8),chunk.z*16+((index>>4)&15))
                        if root in protected:counts['approvedCompositeSourceCells']+=1;continue
                        place_owner(world,entities,root,target,cells,counts,cases);processed+=1
                        if processed%10000==0:print('whole source owners considered',processed,flush=True)
        world.save();proof['convertedRegions'].append(relative);proof['counts']=dict(counts);proof['outputHashes']=outputs();atomic_json(checkpoint,proof)
        print('converted',relative,'owners',counts['wholeOwners'],flush=True)
    # Every retired registry ID must disappear from stored palettes, including
    # unused entries (Chunk.finish compacts them) and helper owner references.
    invalid=set()
    for chunk in world.chunks.values():
        for sec in chunk.root().get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
            bs=compound(sec).get('block_states')
            if bs:
                for tag in compound(bs)['palette'].value:
                    name=compound(tag)['Name'].value
                    if name.startswith(NS)and name!=PART and name not in defs:invalid.add(name)
        for tag in chunk.entities()[1]:
            data=compound(tag)
            if data.get('id',Tag(TAG_STRING,'')).value==PART:
                for root,owner in helper_bindings(data):
                    if owner not in defs:invalid.add(owner)
    if invalid:raise ValueError('retired IDs remain '+str(sorted(invalid)[:20]))
    outcomes=sum(counts[k]for k in ('wholeOwners','rootOutsideCity','retainedRoot','foreignRoot',
        'retainedOrForeignRoots','approvedCompositeSourceCells','emptySourceModelsOmitted'))
    if outcomes!=counts['sourcePaletteRoots']:raise ValueError('unreconciled source roots')
    if current_inputs()!=inputs:raise ValueError('input contents changed before completion')
    world.save()
    proof['helperAudit']=audit_saved_world(output,defaults,catalog)
    if current_inputs()!=inputs:raise ValueError('input contents changed after helper audit')
    proof['exceptions']={k:counts[k]for k in ('foreignRoot','rootOutsideCity','foreignCollisionCells','helperOutsideCity')if counts[k]}
    proof['complete']=True;proof['counts']=dict(counts);proof['outputHashes']=outputs();atomic_json(checkpoint,proof)
    atomic_json(output/'compact-city-conversion.json',proof)
    return proof

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--source',type=Path,required=True);p.add_argument('--original',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True);p.add_argument('--catalog',type=Path,required=True);p.add_argument('--old-city',type=Path,required=True)
    p.add_argument('--oracle',type=Path,required=True);p.add_argument('--resume',action='store_true');a=p.parse_args()
    result=convert(a.source,a.original,a.output,a.catalog,a.old_city,a.oracle,resume=a.resume)
    print(json.dumps({k:v for k,v in result['counts'].items()if not str(k).startswith(NS)},ensure_ascii=False))
