import json, tempfile, unittest
from pathlib import Path
from reviewed_migration_fixture import write_fixture
from inspect_editable_city_gallery import inspect
from convert_logical_world import World

class InspectTests(unittest.TestCase):
    def test_minecraft_resave_omits_constant_properties_without_an_edit(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);world=root/'world'
            write_fixture(world,{(0,80,0):('bloodborne_blocks:a',{'facing':'east'})},floor=False)
            manifest={'schemaVersion':2,'dimension':'minecraft:overworld','registryConstants':{'bloodborne_blocks:a':{'root_anchor':'canonical','variant':'0'}},
                      'specimens':[{'stableKey':'a','numericId':'1','sourceBlockId':'a','editable':True,'inspectionBounds':[0,80,0,0,319,0],'marker':[9,80,9],
                                    'expectedCells':[{'absolute':[0,80,0],'state':'bloodborne_blocks:a','properties':{'facing':'east','root_anchor':'canonical','variant':'0'}}]}]}
            path=root/'manifest.json';path.write_text(json.dumps(manifest))
            self.assertEqual('unchanged',inspect(world,path)['specimens'][0]['classification'])

    def test_adjacent_and_high_constructions_helpers_and_service(self):
        from convert_logical_world import PART
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);world=root/'world'
            blocks={(0,80,0):('bloodborne_blocks:a',{}),(1,80,0):(PART,{}),
                    (2,90,0):('minecraft:stone',{}),(6,80,0):(PART,{}),
                    (10,80,0):('bloodborne_blocks:host',{}),(11,80,0):(PART,{})}
            write_fixture(world,blocks,floor=False)
            def row(ident,x,editable=True):
                return {'stableKey':ident,'numericId':str(x),'sourceBlockId':ident,'editable':editable,
                        'inspectionBounds':[x,80,0,x+2,319,2],'marker':[x,81,2],
                        'expectedCells':[{'absolute':[x,80,0],'state':'bloodborne_blocks:'+ident,'properties':{}}]}
            rows=[row('a',0),row('deleted',5),row('host',10,False)]
            manifest=root/'manifest.json';manifest.write_text(json.dumps({'schemaVersion':2,'dimension':'minecraft:overworld','specimens':rows}))
            out=inspect(world,manifest)['specimens']
            self.assertEqual(['multi-block-construction','deleted','service-unchanged'],[s['classification'] for s in out])
            self.assertEqual([[0,80,0],[2,90,0]],[c['position'] for c in out[0]['actualConstruction']])

    def test_classifies_actual_delete_and_replacement(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); world=root/'world'
            write_fixture(world,{(0,80,0):('bloodborne_blocks:a',{}),(2,80,0):('bloodborne_blocks:b',{}), (3,80,0):('minecraft:stone',{})},floor=False)
            manifest={'schemaVersion':2,'dimension':'minecraft:overworld','specimens':[{'stableKey':'a','numericId':'1','sourceBlockId':'a','editable':True,'inspectionBounds':[0,80,0,0,80,0],'marker':[9,80,9],'expectedCells':[{'absolute':[0,80,0],'state':'bloodborne_blocks:a','properties':{}}]}, {'stableKey':'b','numericId':'2','sourceBlockId':'b','editable':True,'inspectionBounds':[2,80,0,2,80,0],'marker':[9,80,9],'expectedCells':[{'absolute':[2,80,0],'state':'bloodborne_blocks:b','properties':{}}]}]}
            w=World(world,{}); w.set('minecraft:overworld',(0,80,0),('minecraft:air',())); w.set('minecraft:overworld',(2,80,0),('bloodborne_blocks:c',())); w.save()
            (root/'manifest.json').write_text(json.dumps(manifest))
            out=inspect(world,root/'manifest.json')
            self.assertEqual(['deleted','replaced'],[r['classification'] for r in out['specimens']])

if __name__=='__main__': unittest.main()
