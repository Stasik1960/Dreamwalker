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
                self.assertIn('детерминированное приближение',page)
                self.assertIs(raster.texture,old_texture)
        finally:
            for n,v in old.items(): sys.modules.pop(n,None) if v is None else sys.modules.__setitem__(n,v)


if __name__ == '__main__': unittest.main()
