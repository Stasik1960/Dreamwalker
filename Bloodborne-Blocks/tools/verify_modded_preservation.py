"""Verify offline conversion preserves every cell and NBT field outside its ledger."""
from __future__ import annotations
import argparse,hashlib,json
from pathlib import Path
from world_io import RegionFile,compound,section_blocks,block_state_key,invalidate_chunk_lighting,TAG_LIST,TAG_COMPOUND,Tag
from convert_logical_world import list_region_files,region_dimension

def verify(source:Path,output:Path,report:dict):
    allowed={};errors=[]
    if report.get('dryRun') or not isinstance(report.get('ledger'),list):
        return {'result':'FAIL','changed_cells':0,'ledger_cells':0,'changed_chunks':0,'unchanged_chunks':0,
                'preserved_nonterrain_files':0,'errors':['completed conversion ledger required']}
    for item in report['ledger']:
        dim=item.get('dimension')
        for c in item.get('changes',[]):
            p=tuple(c.get('position',()))
            key=(dim,p)
            if len(p)!=3 or any(type(value) is not int for value in p):errors.append('invalid ledger position: '+str(key));continue
            if key in allowed:errors.append('duplicate ledger position: '+str(key));continue
            if not isinstance(c.get('before'),str) or not isinstance(c.get('after'),str):errors.append('invalid ledger state: '+str(key));continue
            allowed[key]=c
    allowed_chunks={(dim,p[0]//16,p[2]//16) for dim,p in allowed}
    seen_allowed=set()
    changed_cells=0;changed_chunks=0;unchanged_chunks=0
    source_files={p.relative_to(source).as_posix():p for p in source.rglob('*') if p.is_file()}
    out_files={p.relative_to(output).as_posix():p for p in output.rglob('*') if p.is_file()}
    if set(source_files)!=set(out_files):errors.append('world file membership changed')
    regions={p.relative_to(source).as_posix() for p in list_region_files(source)}
    preserved_files=0
    for name,p in source_files.items():
        if name not in regions and name in out_files:
            if hashlib.sha256(p.read_bytes()).digest()!=hashlib.sha256(out_files[name].read_bytes()).digest():errors.append('non-terrain file changed: '+name)
            else:preserved_files+=1
    for name in sorted(regions & set(out_files)):
        before=RegionFile.open(source_files[name]);after=RegionFile.open(out_files[name]);dim=region_dimension(source,source_files[name])
        old={(c.x,c.z):c for c in before.chunks()};new={(c.x,c.z):c for c in after.chunks()}
        if set(old)!=set(new):errors.append('chunk membership changed: '+name);continue
        for key,a in old.items():
            b=new[key]
            if a.timestamp!=b.timestamp:errors.append('chunk timestamp changed: '+str((name,key)))
            if a.compression==b.compression and a.compressed_payload==b.compressed_payload:
                # Decode unchanged chunks as well, to validate NBT integrity.
                b.nbt();unchanged_chunks+=1;continue
            changed_chunks+=1
            ar=compound(a.nbt().root);br=compound(b.nbt().root);cx=int(ar['xPos'].value);cz=int(ar['zPos'].value)
            if (dim,cx,cz) not in allowed_chunks:errors.append('chunk changed without ledger cells: '+str((name,cx,cz)))
            asec={int(compound(s)['Y'].value):s for s in ar.get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value}
            bsec={int(compound(s)['Y'].value):s for s in br.get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value}
            for sy in asec.keys()|bsec.keys():
                def states(s):
                    data=section_blocks(s) if s else None
                    if data is None:return ['minecraft:air'],[0]*4096
                    pal,indices=data;return [block_state_key(t) for t in pal],indices
                ap,ai=states(asec.get(sy));bp,bi=states(bsec.get(sy))
                for i in range(4096):
                    av,bv=ap[ai[i]],bp[bi[i]]
                    pos=(cx*16+(i&15),sy*16+(i>>8),cz*16+((i>>4)&15));key=(dim,pos);c=allowed.get(key)
                    if c is not None:
                        seen_allowed.add(key)
                        if c['before']!=av or c['after']!=bv:errors.append('cell does not match ledger: '+str(key))
                    elif av!=bv:errors.append('cell changed outside ledger: '+str(key))
                    if av!=bv:changed_cells+=1
            # Only palette data, invalidated light caches/heightmaps and touched
            # block entities may differ. Everything else compares as typed NBT.
            for root,sections in ((ar,asec),(br,bsec)):
                invalidate_chunk_lighting(root);root.pop('Heightmaps',None);root.pop('sections',None)
                for bekey in ('block_entities','TileEntities'):
                    bes=root.get(bekey)
                    if bes:
                        kept=[]
                        for e in bes.value:
                            v=compound(e);p=tuple(int(v[t].value) for t in ('x','y','z'))
                            if (dim,p) not in allowed:kept.append(e)
                        if kept:root[bekey]=Tag(TAG_LIST,kept,TAG_COMPOUND)
                        else:root.pop(bekey,None)
                for section in sections.values():compound(section).pop('block_states',None)
            if ar!=br:errors.append('unrelated chunk NBT changed: '+str((name,cx,cz)))
            for sy in asec.keys()|bsec.keys():
                left={k:v for k,v in compound(asec[sy]).items() if k not in ('Y','block_states')} if sy in asec else {}
                right={k:v for k,v in compound(bsec[sy]).items() if k not in ('Y','block_states')} if sy in bsec else {}
                if left!=right:errors.append('section metadata changed: '+str((name,cx,sy,cz)))
    for key in sorted(set(allowed)-seen_allowed,key=str):errors.append('ledger cell missing from compared output: '+str(key))
    return {'result':'PASS' if not errors else 'FAIL','changed_cells':changed_cells,'ledger_cells':len(allowed),'changed_chunks':changed_chunks,'unchanged_chunks':unchanged_chunks,'preserved_nonterrain_files':preserved_files,'errors':errors}

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('source',type=Path);p.add_argument('output',type=Path);p.add_argument('report',type=Path);p.add_argument('--result',type=Path,required=True);a=p.parse_args();r=verify(a.source,a.output,json.loads(a.report.read_text(encoding='utf8')));a.result.write_text(json.dumps(r,indent=2)+'\n',encoding='utf8');print(json.dumps({k:v for k,v in r.items() if k!='errors'}));raise SystemExit(bool(r['errors']))
