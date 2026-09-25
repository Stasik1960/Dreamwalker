"""Cell-preserving migration and fail-closed city resource checks."""
import json
import tempfile
import unittest
from pathlib import Path
from build_city_compat import rotated_mesh, coarse_boxes
from city_palette import apply, load
from convert_logical_world import World
from test_logical_world import assembly_source

class CityPaletteTests(unittest.TestCase):
    def resources(self, root, real=False):
        city=root/'city';city.mkdir()
        if real:
            from test_logical_world import resources
            resources(root/'logical')
        else:
            (root/'logical').mkdir()
            (root/'logical/definitions.json').write_text('{"blocks": []}')
        target={'id':'bloodborne_blocks:city_test','properties':{'variant':'0'}}
        (city/'migration.json').write_text(json.dumps({'states':{'bloodborne_blocks:m_old[facing=east]':target}}))
        (city/'definitions.json').write_text(json.dumps({'blocks':[{'id':'city_test','states':{'variant=0':{}}}]}))
        (city/'geometry.json').write_text('{"blocks":{}}');(city/'meshes.json.gz').write_bytes(b'frozen test artifact')
        return city

    def test_same_cell_palette_and_second_pass(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);city=self.resources(root);source=root/'world'
            assembly_source(source,{(3,64,8):('bloodborne_blocks:m_old',{'facing':'east'}),(9,64,8):('minecraft:stone',{})})
            world=World(source,{})
            result=apply(world,city);self.assertEqual(1,result['changedCells'])
            self.assertEqual(0,result['coordinateChanges']);world.save()
            reread=World(source,{})
            self.assertEqual(('bloodborne_blocks:city_test',(('variant','0'),)),reread.get('minecraft:overworld',(3,64,8)))
            self.assertEqual(('minecraft:stone',()),reread.get('minecraft:overworld',(9,64,8)))
            self.assertEqual(0,apply(reread,city)['changedCells'])

    def test_independent_checker_rejects_unrelated_cell_mutation(self):
        from convert_logical_world import convert
        from check_logical_world import check
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);city=self.resources(root,True);source=root/'world';output=root/'output'
            assembly_source(source,{(3,64,8):('bloodborne_blocks:m_old',{'facing':'east'}),(9,64,8):('minecraft:stone',{})})
            report_path=root/'reports/result.json'
            report=convert(source,output,resources=root/'logical',report_path=report_path,report_root=root/'reports')
            world=World(output,{})
            report['cityPaletteMigration']=apply(world,city);world.save()
            report_path.write_text(json.dumps(report))
            self.assertTrue(check(source,output,report_path,root/'logical')['ok'])
            world=World(output,{})
            world.set('minecraft:overworld',(9,64,8),('minecraft:diamond_block',()));world.save()
            with self.assertRaisesRegex(AssertionError,'outside ledger'):check(source,output,report_path,root/'logical')

    def test_unknown_fails_and_resource_tampering_fails(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);city=self.resources(root);source=root/'world'
            assembly_source(source,{(3,64,8):('bloodborne_blocks:m_missing',{})})
            with self.assertRaisesRegex(ValueError,'unregistered'):apply(World(source,{}),city)
            _,_,hashes=load(city);(city/'geometry.json').write_text('{"changed":true}')
            with self.assertRaisesRegex(ValueError,'changed since'):load(city,{'hashes':hashes})

    def test_four_rotations_restore_mesh_and_coarse_boxes_cover_inputs(self):
        mesh={'polygons':[{'texture':'test:block','vertices':[[0.25,0.5,0.75,0,1]]}]}
        value=mesh
        for _ in range(4):value=rotated_mesh(value,1)
        self.assertEqual(mesh,value)
        boxes=[[i/10,0,0,(i+1)/10,0.5,0.1] for i in range(8)]
        result=coarse_boxes(boxes)
        self.assertLessEqual(len(result),4)
        for b in boxes:self.assertTrue(any(all(r[i]<=b[i] and r[i+3]>=b[i+3] for i in range(3)) for r in result))

if __name__=='__main__':unittest.main()
