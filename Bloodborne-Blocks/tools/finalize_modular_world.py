"""Remove proven orphan helpers and invalidate lighting in a converted COPY only."""
from convert_modular_world import *

def finalize(source,target):
    source=source.resolve();target=target.resolve()
    if source==target or source in target.parents:raise ValueError('Separate copied world required')
    checked=json.loads((ROOT/'docs/world-verification-v2.json').read_text())
    errors=checked['errors']
    if any(e[0]!='orphan_helper' or e[-1]!=NS+'spruce_button' for e in errors):
        raise ValueError('Only verified removed spruce-button helpers can be cleaned')
    orphan_positions={tuple(e[1:4]) for e in errors};removed=[];relit=0
    for path in sorted(target.glob('region/*.mca')):
        region=RegionFile.open(path);dirty=False
        for c in list(region.chunks()):
            nbt=c.nbt();root=compound(nbt.root);cx=root['xPos'].value;cz=root['zPos'].value;changed=False;has_architecture=False
            positions={p for p in orphan_positions if p[0]//16==cx and p[2]//16==cz}
            for section in root.get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
                sd=compound(section)
                if 'block_states' not in sd:continue
                bs=compound(sd['block_states']);pal=list(bs['palette'].value)
                if any(compound(p)['Name'].value.startswith(NS) for p in pal):has_architecture=True
                here={p for p in positions if p[1]//16==sd['Y'].value}
                if here:
                    arr=unpack_fast(bs);air_index=len(pal);pal.append(AIR)
                    for p in here:
                        idx=(p[1]%16)*256+(p[2]%16)*16+p[0]%16
                        assert compound(pal[int(arr[idx])])['Name'].value==NS+'architecture_part',p
                        arr[idx]=air_index;removed.append(p)
                    set_section(bs,pal,arr);changed=True
            if positions:
                entities=root.get('block_entities',Tag(TAG_LIST,[],TAG_COMPOUND))
                entities.value=[be for be in entities.value if not (
                    compound(be).get('id',Tag(TAG_STRING,'')).value==NS+'architecture_part' and
                    tuple(compound(be)[a].value for a in ('x','y','z')) in positions)]
            if has_architecture:
                invalidate_chunk_lighting(root)
                root.pop('Heightmaps',None);changed=True;relit+=1
            if changed:region.set_chunk(c.x,c.z,nbt,timestamp=c.timestamp);dirty=True
        if dirty:region.save(path)
    assert set(removed)==orphan_positions
    report={'removed_orphans':len(removed),'removed_positions':sorted(removed),'chunks_marked_for_relighting':relit,'source_modified':False}
    dump(ROOT/'docs/world-finalization-v2.json',report);print('Finalized',len(removed),'orphan cells,',relit,'chunks for lighting')

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('target',type=Path);a=p.parse_args();finalize(a.source,a.target)
