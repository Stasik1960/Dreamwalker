import sys, unittest, tempfile, hashlib, zipfile, copy
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
from world_io import Tag, NbtFile, RegionFile, TAG_COMPOUND, TAG_LIST, TAG_STRING, TAG_INT, TAG_LONG_ARRAY, pack_palette_indices, write_nbt, compound
from restore_yuushya import _entries, _ns, restore, scan, _world

def fixture(path,dimension,blocks,records=None,version=3465):
    path.mkdir()
    write_nbt(path/'level.dat',NbtFile('',Tag(TAG_COMPOUND,{'Data':Tag(TAG_COMPOUND,{'DataVersion':Tag(TAG_INT,version)})})),compressed='gzip')
    region=path/'region' if dimension=='minecraft:overworld' else path/'dimensions'/dimension.replace(':','/')/'region'
    region.mkdir(parents=True)
    pal=[state('minecraft:air')]; idx=[0]*4096
    for (x,y,z),entry in blocks.items():
        if entry not in pal: pal.append(entry)
        idx[(y%16)*256+(z%16)*16+x%16]=pal.index(entry)
    section=Tag(TAG_COMPOUND,{'Y':Tag(1,4),'block_states':Tag(TAG_COMPOUND,{'palette':Tag(TAG_LIST,pal,TAG_COMPOUND),'data':Tag(TAG_LONG_ARRAY,pack_palette_indices(idx,len(pal)))})})
    root={'DataVersion':Tag(TAG_INT,version),'xPos':Tag(TAG_INT,-24),'zPos':Tag(TAG_INT,-13),'sections':Tag(TAG_LIST,[section],TAG_COMPOUND)}
    for key,entries in (records or {}).items(): root[key]=Tag(TAG_LIST,entries,TAG_COMPOUND)
    rf=RegionFile(); rf.set_chunk(8,19,NbtFile('',Tag(TAG_COMPOUND,root)),timestamp=123); rf.save(region/'r.-1.-1.mca')
    return region/'r.-1.-1.mca'

def record(position,ident):
    return Tag(TAG_COMPOUND,{'id':Tag(TAG_STRING,ident),**{k:Tag(TAG_INT,v) for k,v in zip(('x','y','z'),position)}})

def state(name, props=None):
    d={'Name':Tag(TAG_STRING,name)}
    if props: d['Properties']=Tag(TAG_COMPOUND,{k:Tag(TAG_STRING,v) for k,v in props.items()})
    return Tag(TAG_COMPOUND,d)

class RestoreYuushyaTests(unittest.TestCase):
    def test_namespace_and_properties_are_scanned_at_negative_section(self):
        pal=[state('minecraft:stone'),state('yuushya:chair',{'facing':'north'})]
        idx=[0]*4096; idx[2*256+3*16+4]=1
        sec=Tag(TAG_COMPOUND,{'Y':Tag(1,-2),'block_states':Tag(TAG_COMPOUND,{
            'palette':Tag(TAG_LIST,pal,TAG_COMPOUND),
            'data':Tag(TAG_LONG_ARRAY,pack_palette_indices(idx,2))})})
        found=list(_entries({'sections':Tag(TAG_LIST,[sec],TAG_COMPOUND)}))
        self.assertEqual(found[0][0:3],(4,-30,3)); self.assertEqual(found[0][3],'yuushya:chair[facing=north]')

    def test_existing_yuushya_namespace_is_recognized(self):
        self.assertTrue(_ns('yuushya_extra:block').startswith('yuushya'))
        self.assertFalse(_ns('minecraft:stone').startswith('yuushya'))

    def test_saved_negative_coordinates_mapping_metadata_and_idempotence(self):
        with tempfile.TemporaryDirectory() as directory:
            base=Path(directory); donor=base/'donor'; target=base/'target'; output=base/'output'
            p=(-374,67,-197); vending=(-382,68,-199); other=(-370,67,-194)
            curtain=state('yuushya:red_curtain',{'facing':'east','form':'0'})
            fields=('block_entities','block_ticks','fluid_ticks')
            source_records={key:[record(p,'yuushya:restored'),record(other,'minecraft:unused')] for key in fields}
            target_records={key:[record(p,'minecraft:stale'),record(other,'minecraft:preserve')] for key in fields}
            sf=fixture(donor,'eh_s2:yharnam',{p:curtain},source_records)
            tf=fixture(target,'minecraft:overworld',{p:state('minecraft:stone'),vending:state('yuushya:vending_machine')},target_records)
            original=(sf.read_bytes(),tf.read_bytes())
            result=restore(donor,target,output,{'eh_s2:yharnam':'minecraft:overworld'})
            self.assertEqual(result['positions'],[('minecraft:overworld',*p,'yuushya:red_curtain[facing=east,form=0]')])
            actual={(r['x'],r['y'],r['z']):r['state'] for r in scan(output)}
            self.assertEqual(actual[p],'yuushya:red_curtain[facing=east,form=0]')
            self.assertEqual(actual[vending],'yuushya:vending_machine')
            saved=compound(RegionFile.open(output/'region/r.-1.-1.mca').get_chunk(8,19).nbt().root)
            for key in fields:
                self.assertEqual([compound(r)['id'].value for r in saved[key].value],['minecraft:preserve','yuushya:restored'])
            self.assertEqual(original,(sf.read_bytes(),tf.read_bytes()))
            again=base/'again'; self.assertEqual(restore(donor,output,again,{'eh_s2:yharnam':'minecraft:overworld'})['changed'],0)
            self.assertEqual((output/'region/r.-1.-1.mca').read_bytes(),(again/'region/r.-1.-1.mca').read_bytes())

    def test_rejections_leave_no_output(self):
        with tempfile.TemporaryDirectory() as directory:
            base=Path(directory); donor=base/'donor'; target=base/'target'
            fixture(donor,'eh_s2:yharnam',{(-374,67,-197):state('yuushya:chair')})
            fixture(target,'minecraft:overworld',{},version=3464)
            for mapping in ({'eh_s2:yharnam':'absent:dimension'},{'eh_s2:yharnam':'minecraft:overworld'}):
                with self.assertRaises(ValueError): restore(donor,target,base/'output',mapping)
                self.assertFalse((base/'output').exists())
            with self.assertRaises(ValueError): restore(donor,target,target/'nested',{})
            archive=base/'bad.zip'
            with zipfile.ZipFile(archive,'w') as z: z.writestr('../escape','bad')
            with self.assertRaises(ValueError): _world(archive)
            self.assertFalse((base/'escape').exists())

if __name__=='__main__': unittest.main()
