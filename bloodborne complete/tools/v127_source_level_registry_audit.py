"""Read original level/worldgen and every used biome palette, without world writes."""
import collections,gzip,json,zipfile
from pathlib import PurePosixPath,Path
from v125_map_index import ROOT,MAP,PACK,sha
from v127_apply_full_map_poses import write
from v125_map_city import typed
from scan_original_map_states import dimension_for
from world_io import RegionFile,compound,decode_nbt,TAG_COMPOUND,TAG_LIST,TAG_STRING
def plain(t):
    if t.type==TAG_COMPOUND:return {k:plain(v) for k,v in t.value.items()}
    if t.type==TAG_LIST:return [plain(v) for v in t.value]
    if t.type==7:return {'byteArrayLength':len(t.value)}
    return t.value
def biomes(data):
    palette=data['palette'].value;longs=data.get('data');bits=max(1,(len(palette)-1).bit_length());per=64//bits
    if len(palette)==1:return [(palette[0].value,64)]
    assert longs is not None and len(longs.value)==(64+per-1)//per
    counts=collections.Counter((int(longs.value[i//per])&0xffffffffffffffff)>>(bits*(i%per))&((1<<bits)-1) for i in range(64));assert all(i<len(palette) for i in counts)
    return [(palette[i].value,n) for i,n in counts.items()]
def main():
    before=sha(MAP);counts=collections.Counter();dimensions=collections.Counter();samples={};chunks=sections=0;programs=[]
    with zipfile.ZipFile(MAP) as source:
        levels=[n for n in source.namelist() if PurePosixPath(n).name=='level.dat'];assert len(levels)==1;level=decode_nbt(gzip.decompress(source.read(levels[0])));data=compound(compound(level.root)['Data']);worldgen=plain(data['WorldGenSettings'])
        for name in source.namelist():
            pp=PurePosixPath(name)
            if 'datapacks' in pp.parts and pp.suffix.lower() in {'.json','.mcfunction'}:programs.append({'member':name,'bytes':len(source.read(name)),'decision':'OMIT_SOURCE_DATAPACK_FROM_DERIVED_WORLD'})
            if pp.suffix!='.mca' or pp.parent.name!='region':continue
            dimension=dimension_for(name,'');region=RegionFile(source.read(name))
            for stored in region.chunks():
                root=compound(stored.nbt().root);root=compound(root['Level']) if 'Level' in root else root;chunks+=1;dimensions[dimension]+=1
                for section in (root.get('sections') or root.get('Sections')).value:
                    fields=compound(section);biome=fields.get('biomes')
                    if biome is None:continue
                    sections+=1
                    for id,n in biomes(compound(biome)):
                        counts[id]+=n;samples.setdefault(id,{'dimension':dimension,'chunk':[int(root['xPos'].value),int(root['zPos'].value)],'sectionY':int(fields['Y'].value)})
    vanilla=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'
    with zipfile.ZipFile(vanilla) as z:vanillaBiomes={n[len('data/minecraft/worldgen/biome/'):-5] for n in z.namelist() if n.startswith('data/minecraft/worldgen/biome/') and n.endswith('.json')}
    missing=[id for id in counts if not id.startswith('minecraft:') or id.split(':')[1] not in vanillaBiomes]
    refs=[]
    def walk(value,path='WorldGenSettings'):
        if isinstance(value,dict):
            for k,v in value.items():walk(v,path+'.'+k)
        elif isinstance(value,list):
            for i,v in enumerate(value):walk(v,path+f'[{i}]')
        elif isinstance(value,str) and ':' in value:refs.append({'path':path,'registryReference':value})
    walk(worldgen);unknown=[r for r in refs if not r['registryReference'].startswith('minecraft:')]
    after=sha(MAP);assert before==after
    report={'status':'PASS_VANILLA_LEVEL_AND_USED_BIOME_REGISTRIES' if not missing and not unknown and set(dimensions)<={'minecraft:overworld','minecraft:the_nether','minecraft:the_end'} else 'FAIL_UNKNOWN_SOURCE_LEVEL_REGISTRY','sourceHashes':{'map':before,'pack':sha(PACK)},'levelDataVersion':data.get('DataVersion').value if data.get('DataVersion') else None,'worldGenSettings':worldgen,'worldGenRegistryReferences':refs,'unknownWorldGenReferences':unknown,'dimensions':dict(dimensions),'chunks':chunks,'biomeSections':sections,'usedBiomeCounts':dict(counts),'biomeSamples':samples,'unknownOrMissingVanillaBiomeReferences':missing,'vanillaBiomeRegistryDefinitionCount':len(vanillaBiomes),'datapackMembersToOmit':programs,'originalMapShaAfter':after,'sourceWorldOpened':False,'sourceWorldWritten':False,'nativeLevelCodecCheck':'NOT_RUN_FULL_SOURCE_BY_USER_REQUEST; separately prove codecs if any nonvanilla reference is found'}
    write(ROOT/'docs/v12.7/conversion/source-level-registry-audit.json',report);print(json.dumps({k:report[k] for k in ['status','levelDataVersion','dimensions','chunks','biomeSections','usedBiomeCounts','unknownOrMissingVanillaBiomeReferences','unknownWorldGenReferences']},ensure_ascii=False))
if __name__=='__main__':main()
