"""No-JAR smoke test with injected source geometry/rasterizer."""
import sys, tempfile, types, unittest
from pathlib import Path
import numpy as np
from PIL import Image


class SourceAssemblyTest(unittest.TestCase):
    def test_catalog_writes_offline_evidence(self):
        geometry = types.ModuleType('source_assembly_visuals')
        geometry.source_polys = lambda apps: ([{'vertices': [[0,0,0,0,0],[1,0,0,1,0],[0,1,0,0,1]], 'texture':'x'}], [])
        geometry.source_texture = lambda _name: np.zeros((2,2,4),dtype=np.uint8)
        catalog = types.ModuleType('catalog_geometry'); catalog.rotation = lambda _a,_b: np.eye(3)
        raster = types.ModuleType('render_modular_preview'); raster.CAM=np.eye(3); raster.draw_polys=lambda _p,size: Image.new('RGBA',(size,size),(60,70,80,255))
        old_texture = raster.texture = lambda _name: None
        old = {n:sys.modules.get(n) for n in ('source_assembly_visuals','catalog_geometry','render_modular_preview')}; sys.modules.update({'source_assembly_visuals':geometry,'catalog_geometry':catalog,'render_modular_preview':raster})
        try:
            from source_assembly_render import render_catalog
            manifest={'source':{'sha256':'abc'},'scan_scope':'fixture','candidates':[{'review_id':'C001','hypothesis':'Тест','status':'UNREVIEWED','example':{'dimension':'overworld','anchor':[0,0,0]},'components':[{'number':1,'relative':[0,0,0],'source':{'id':'minecraft:stone','properties':{}},'apps':[{'model':'minecraft:block/stone','offset':[0,0,0]}]}],'context':[{'relative':[0,0,0],'source':{'id':'minecraft:stone','properties':{}}}],'similar_count':1,'rotations':[0],'count_scope':'bounded'}]}
            with tempfile.TemporaryDirectory() as temp:
                manifest['candidates'][0]['components'][0]['apps'][0]['preview_alternatives'] = 2
                index=render_catalog(manifest,Path(temp)); self.assertTrue(index.is_file()); self.assertTrue((Path(temp)/'batch-01-contact.png').is_file())
                page=index.read_text(encoding='utf8'); self.assertIn('C001 OBJECT',page); self.assertIn('relative XYZ',page); self.assertIn('minecraft:stone',page)
                self.assertTrue((Path(temp)/'images/C001-context.png').is_file()); self.assertTrue((Path(temp)/'batch-01-manifest.json').is_file())
                self.assertIn('не утверждение точной resolved-комбинации',page)
                self.assertIs(raster.texture,old_texture)
        finally:
            for n,v in old.items(): sys.modules.pop(n,None) if v is None else sys.modules.__setitem__(n,v)

    def test_v2_patterns_aliases_and_every_choice_are_in_one_card(self):
        geometry = types.ModuleType('source_assembly_visuals'); geometry.source_polys=lambda _apps: ([{'vertices':[[0,0,0,0,0],[1,0,0,1,0],[0,1,0,0,1]],'texture':'x'}],[]); geometry.source_texture=lambda _x: np.zeros((1,1,4),dtype=np.uint8)
        catalog=types.ModuleType('catalog_geometry'); catalog.rotation=lambda _a,_b:np.eye(3)
        raster=types.ModuleType('render_modular_preview'); raster.CAM=np.eye(3); raster.texture=lambda _x:None; raster.draw_polys=lambda _p,size:Image.new('RGBA',(size,size),(1,2,3,255))
        old={n:sys.modules.get(n) for n in ('source_assembly_visuals','catalog_geometry','render_modular_preview')};sys.modules.update({'source_assembly_visuals':geometry,'catalog_geometry':catalog,'render_modular_preview':raster})
        try:
            from source_assembly_render import render_catalog
            cell={'number':1,'relative':[0,0,0],'source':{'id':'minecraft:stone','properties':{}},'apps':[{'model':'minecraft:block/stone'}],'model_choices':[[[{'model':'minecraft:a','weight':2}],[{'model':'minecraft:b','weight':1}]]]}
            active={'review_id':'C010','hypothesis':'Canonical','status':'UNREVIEWED','components':[cell],'context':[],'source_patterns':[{'pattern_id':'A','exact_source_signature':'a','components':[cell],'example':{},'similar_count':2,'rotations':[0],'boundary_flags':['POSSIBLY_INCOMPLETE']},{'pattern_id':'B','exact_source_signature':'b','components':[cell],'example':{},'similar_count':1,'rotations':[90]}]}
            alias={'review_id':'C011','status':'VARIANT_OF','variant_of':'C010','hypothesis':'Alias','components':[cell],'context':[]}
            with tempfile.TemporaryDirectory() as temp:
                index=render_catalog({'batch_id':'batch-02','batch_review_ids':['C010'],'candidates':[active,alias],'coverage':{'x':1}},Path(temp)); page=index.read_text(encoding='utf8')
                self.assertIn('Exact pattern A',page); self.assertIn('POSSIBLY_INCOMPLETE',page); self.assertIn('source/properties',page); self.assertIn('C011',page); self.assertNotIn("id='C011'",page)
                self.assertTrue((Path(temp)/'batch-02-contact.png').is_file()); self.assertTrue((Path(temp)/'images/C010-A-cell1-g1-a2.png').is_file()); self.assertTrue((Path(temp)/'images/C010-B-cell1-g1-a2.png').is_file())
        finally:
            for n,v in old.items(): sys.modules.pop(n,None) if v is None else sys.modules.__setitem__(n,v)


if __name__ == '__main__': unittest.main()
