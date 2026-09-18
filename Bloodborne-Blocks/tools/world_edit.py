"""Small batched cell editor for a generated world copy; never creates chunks."""
from convert_modular_world import *

class WorldEditor:
    def __init__(self,target):
        self.target=Path(target).resolve();self.regions={};self.chunks={};self.sections={};self.dirty=set();self.changes={}
        level=compound(compound(read_nbt(self.target/'level.dat').root)['Data'])
        if not level.get('LevelName',Tag(TAG_STRING,'')).value.startswith('Ether - Bloodborne 2.0'):
            raise ValueError('Only the generated Ether copy can be edited')
    def chunk(self,cx,cz):
        key=(cx,cz)
        if key not in self.chunks:
            rk=(cx//32,cz//32)
            if rk not in self.regions:
                path=self.target/'region'/f'r.{rk[0]}.{rk[1]}.mca'
                if not path.exists():return None
                self.regions[rk]=RegionFile.open(path)
            stored=self.regions[rk].get_chunk(cx%32,cz%32)
            if stored is None:return None
            self.chunks[key]=(stored,stored.nbt())
        return compound(self.chunks[key][1].root)
    def section(self,pos):
        x,y,z=pos;key=(x//16,y//16,z//16)
        if key not in self.sections:
            root=self.chunk(x//16,z//16)
            if root is None:return None
            sd=next((compound(s) for s in root['sections'].value if compound(s)['Y'].value==y//16),None)
            if sd is None:return None
            if 'block_states' not in sd:sd['block_states']=Tag(TAG_COMPOUND,{'palette':Tag(TAG_LIST,[AIR],TAG_COMPOUND)})
            bs=compound(sd['block_states']);pal=list(bs['palette'].value)
            self.sections[key]=(sd,bs,pal,unpack_fast(bs),{block_state_key(t):i for i,t in enumerate(pal)})
        return self.sections[key]
    def get(self,pos):
        section=self.section(pos)
        if section is None:return None
        x,y,z=pos;return section[2][int(section[3][y%16*256+z%16*16+x%16])]
    def put(self,pos,entry):
        section=self.section(pos)
        if section is None:raise ValueError(('Missing target section',pos))
        pos=tuple(pos);old=block_state_key(self.get(pos));new=block_state_key(entry)
        if old==new:return
        record=self.changes.setdefault(pos,{'position':list(pos),'oldState':old});record['newState']=new
        sd,bs,pal,array,indices=section
        if new not in indices:indices[new]=len(pal);pal.append(entry)
        x,y,z=pos;array[y%16*256+z%16*16+x%16]=indices[new];self.dirty.add((x//16,z//16))
    def remove_entity(self,pos):
        x,y,z=pos;root=self.chunk(x//16,z//16);entities=root.get('block_entities',Tag(TAG_LIST,[],TAG_COMPOUND))
        before=len(entities.value);entities.value=[be for be in entities.value if tuple(compound(be)[a].value for a in ('x','y','z'))!=tuple(pos)]
        if len(entities.value)!=before:self.dirty.add((x//16,z//16))
    def save(self):
        regions=set()
        for (cx,sy,cz),(sd,bs,pal,array,_) in self.sections.items():
            if (cx,cz) in self.dirty:set_section(bs,pal,array)
        for cx,cz in self.dirty:
            stored,nbt=self.chunks[(cx,cz)];root=compound(nbt.root)
            invalidate_chunk_lighting(root);root.pop('Heightmaps',None)
            rk=(cx//32,cz//32);self.regions[rk].set_chunk(stored.x,stored.z,nbt,timestamp=stored.timestamp);regions.add(rk)
        for rx,rz in regions:self.regions[(rx,rz)].save(self.target/'region'/f'r.{rx}.{rz}.mca')

def unpack_position(packed):
    p=packed&((1<<64)-1)
    def signed(v,b):return v-(1<<b) if v&(1<<(b-1)) else v
    return signed(p>>38,26),signed(p&4095,12),signed((p>>12)&((1<<26)-1),26)
