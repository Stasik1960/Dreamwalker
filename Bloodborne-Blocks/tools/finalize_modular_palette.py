"""Prune unused conversion intermediates, retaining every placed/public/mapped ID."""
from convert_modular_world import *


def finalize(target):
    used=set();counts=collections.Counter()
    for path in sorted(target.glob('region/*.mca')):
        for chunk in RegionFile.open(path).chunks():
            for section in compound(chunk.nbt().root).get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
                sd=compound(section)
                if 'block_states' not in sd:continue
                bs=compound(sd['block_states']);array=unpack_fast(bs)
                for index,n in zip(*np.unique(array,return_counts=True)):
                    name=compound(bs['palette'].value[int(index)])['Name'].value
                    if name.startswith(NS+'m_'):used.add(name.removeprefix(NS));counts['placed_modules']+=int(n)
    palette=load_palette();before=len(palette.blocks)
    mapping=json.loads((OUT/'migration.json').read_text(encoding='utf-8'))
    mapped={p['id'] for spec in mapping.values() for value in spec['states'].values() if isinstance(value,list) for p in value}
    required=used|mapped|palette.public
    assert required<=palette.blocks.keys(), 'Referenced module absent from registry'
    for field in ('blocks','meshes','geometry','names','sources'):
        setattr(palette,field,{i:v for i,v in getattr(palette,field).items() if i in required})
    renamed=0
    for ident,name in palette.names.items():
        if 'Пустая' not in name:continue
        texture=collections.Counter(p['texture'] for p in palette.meshes[ident]['polygons']).most_common(1)[0][0]
        palette.names[ident]=('Секция кровли' if 'roof' in texture else
                              'Оконная секция' if 'window' in texture else 'Секция каменной отделки')
        renamed+=1
    palette.save()
    profiles={json.dumps(v,sort_keys=True,separators=(',',':')) for b in palette.geometry.values() for v in b['states'].values()}
    polygons=[len(mesh['polygons']) for mesh in palette.meshes.values()]
    report={'registered_modules':len(palette.blocks),'removed_unused_intermediates':before-len(palette.blocks),
            'unique_placed_modules':len(used),'mapped_modules':len(mapped),'public_modules':len(palette.public),
            'renamed_placeholder_items':renamed,'geometry_profiles':len(profiles),'polygons':sum(polygons),
            'max_polygons_per_module':max(polygons),'counts':dict(counts)}
    dump(ROOT/'docs/palette-finalization-v2.json',report);print(json.dumps(report),flush=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('target',type=Path);args=parser.parse_args();finalize(args.target.resolve())
