"""Preserve water in occupied architectural cells using ordinary waterlogging."""
from repair_modular_overlaps import *
from build_modular_palette import dump,OUT
import hashlib

def hydrated(palette,result):
    old=result['id'];ident='m_'+hashlib.sha256((old+':waterlogged').encode()).hexdigest()[:16]
    if ident not in palette.blocks:
        d=copy.deepcopy(palette.blocks[old]);d['id']=ident;d['creative']=False
        d['properties']['waterlogged']=['false','true'];d['default']['waterlogged']='false'
        d['states']={k+',waterlogged='+wet:v for k,v in d['states'].items() for wet in ('false','true')}
        palette.blocks[ident]=d;palette.meshes[ident]=palette.meshes[old]
        palette.geometry[ident]={'states':{k+',waterlogged='+wet:v for k,v in palette.geometry[old]['states'].items() for wet in ('false','true')}}
        palette.names[ident]=palette.names[old];palette.sources[ident]=['world_waterlogged',old]
    return {'id':ident,'properties':{**result['properties'],'waterlogged':'true'}}

def repair_water(target, input_report, output_report):
    conflicts=json.loads(input_report.read_text(encoding='utf-8'))['unresolved']
    conflicts=[c for c in conflicts if c['protectedBlock'].startswith('minecraft:water[')]
    p=load_palette();by_region=collections.defaultdict(list);ledger=[]
    for c in conflicts:
        pos=c['position'];by_region[(pos[0]//512,pos[2]//512)].append(c)
    for (rx,rz),rows in by_region.items():
        path=target/'region'/f'r.{rx}.{rz}.mca';region=RegionFile.open(path);chunks={}
        for c in rows:
            x,y,z=c['position'];key=(x//16,z//16)
            if key not in chunks:
                stored=region.get_chunk(key[0]%32,key[1]%32);chunks[key]=(stored,stored.nbt(),{})
            stored,nbt,sections=chunks[key];root=compound(nbt.root)
            if y//16 not in sections:
                sd=next(compound(s) for s in root['sections'].value if compound(s)['Y'].value==y//16)
                bs=compound(sd['block_states']);sections[y//16]=(sd,bs,list(bs['palette'].value),unpack_fast(bs))
            sd,bs,pal,array=sections[y//16];idx=y%16*256+z%16*16+x%16;old=block_state_key(pal[int(array[idx])])
            assert old==c['protectedBlock'],(c['position'],old)
            result,_=compose(p,c['incomingPieces'],source='world_water');result=hydrated(p,result)
            array[idx]=len(pal);pal.append(tag_state(result['id'],result['properties']))
            ledger.append({'position':c['position'],'oldState':old,'newState':state_text(result)})
        for stored,nbt,sections in chunks.values():
            for sd,bs,pal,array in sections.values():set_section(bs,pal,array)
            root=compound(nbt.root);invalidate_chunk_lighting(root);root.pop('Heightmaps',None)
            region.set_chunk(stored.x,stored.z,nbt,timestamp=stored.timestamp)
        region.save(path)
    p.save();dump(output_report,{'applied':True,'changes':ledger,'count':len(ledger)})
    print('Waterlogged architecture cells:',len(ledger))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('target',type=Path)
    parser.add_argument('--conflicts',type=Path,default=ROOT/'docs/world-ornaments-v2.json')
    parser.add_argument('--output',type=Path,default=ROOT/'docs/world-water-v2.json')
    a=parser.parse_args();repair_water(a.target.resolve(),a.conflicts.resolve(),a.output.resolve())
