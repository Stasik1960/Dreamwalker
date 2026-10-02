import gzip
import json
import struct
import tempfile
import unittest
from pathlib import Path
from export_assembled_artist_kit import export, gltf, select_states

TRIANGLE = [[0,0,0,0,0],[1,0,0,16,0],[0,1,0,0,16]]
MESH = {'polygons':[{'texture':'demo:a','vertices':TRIANGLE},
                    {'texture':'demo:b','vertices':[[p[0],p[1],p[2]+1,p[3],p[4]] for p in TRIANGLE]}]}


def decode(g, accessor, data):
    a = g['accessors'][accessor]
    v = g['bufferViews'][a['bufferView']]
    count = a['count'] * {'SCALAR':1,'VEC2':2,'VEC3':3}[a['type']]
    fmt = 'f' if a['componentType'] == 5126 else 'I'
    return struct.unpack_from('<'+fmt*count, data, v.get('byteOffset',0)+a.get('byteOffset',0))


class ExportTests(unittest.TestCase):
    def test_gltf_has_separate_material_indices_and_top_origin_uv(self):
        g = gltf(MESH,['demo:a','demo:b'],['a.png','b.png'],[2,3,4])
        data = g.pop('buffer_data')
        primitives = g['meshes'][0]['primitives']
        self.assertEqual(2,len(primitives))
        self.assertEqual((0,1,2),decode(g,primitives[0]['indices'],data))
        self.assertEqual((3,4,5),decode(g,primitives[1]['indices'],data))
        self.assertEqual((0.,0.,1.,0.,0.,1.)*2,decode(g,primitives[0]['attributes']['TEXCOORD_0'],data))
        self.assertEqual((2.,3.,4.),decode(g,0,data)[:3])
        for view in g['bufferViews']:
            self.assertEqual(0,view['byteOffset']%4)
            self.assertLessEqual(view['byteOffset']+view['byteLength'],len(data))
        self.assertEqual(len(data),g['buffers'][0]['byteLength'])
        self.assertEqual('MASK',g['materials'][0]['alphaMode'])
        self.assertEqual(0,g['materials'][0]['pbrMetallicRoughness']['metallicFactor'])

    def definition(self):
        states = {'facing=north,open=false,root_anchor=canonical,visual=base':'mesh',
                  'facing=north,open=false,root_anchor=canonical,visual=alt':'mesh',
                  'facing=north,open=true,root_anchor=canonical,visual=base':'mesh',
                  'facing=east,open=false,root_anchor=canonical,visual=base':'mesh',
                  'facing=north,open=false,root_anchor=upper_1,visual=base':'mesh'}
        return {'id':'demo','default':{'facing':'north','open':'false','root_anchor':'canonical','visual':'base'},'models':states,
                'states':{k:[99,99,99] for k in states}}

    def test_canonical_selection_retains_open_and_art_and_discards_storage_yaw(self):
        states = [k for k,v in select_states(self.definition())]
        self.assertEqual(3,len(states))
        self.assertTrue(any('open=true' in k for k in states))
        self.assertTrue(any('visual=alt' in k for k in states))
        self.assertFalse(any('upper_' in k or 'facing=east' in k for k in states))

    def resources(self, root):
        logical=root/'bloodborne_blocks/logical';logical.mkdir(parents=True)
        definition=self.definition()
        (logical/'definitions.json').write_text(json.dumps({'blocks':[definition]}))
        (logical/'contracts-v2.json').write_text(json.dumps({'families':[{'id':'demo','states':{
            k:{'render_mesh':{'id':'mesh','offset':[2,3,4]}} for k in definition['models']}}]}))
        (logical/'meshes.json.gz').write_bytes(gzip.compress(json.dumps({'mesh':MESH}).encode()))
        (root/'bloodborne_blocks/debug-ids.json').write_text('{"ids":{"demo":"00001"}}')
        texture=root/'assets/demo/textures';texture.mkdir(parents=True)
        (texture/'a.png').write_bytes(b'png-a');(texture/'b.png').write_bytes(b'png-b')

    def test_export_uses_contract_offset_obj_bottom_uv_and_real_mtl(self):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);resources=root/'resources';self.resources(resources)
            out=root/'artist';m=export(resources,out)
            self.assertEqual(3,len(m['objects']))
            for row in m['objects']:
                self.assertEqual([2,3,4],row['render_offset'])
                self.assertEqual('00001',row['debug_id'])
                folder=out/row['attachment_root'];stem=row['attachment_root']
                text=(folder/(stem+'.obj')).read_text()
                self.assertIn('v 2.00000000 3.00000000 4.00000000',text)
                self.assertIn('vt 0.00000000 1.00000000',text)
                self.assertIn('map_Kd demo__b.png',(folder/(stem+'.mtl')).read_text())
                self.assertEqual(b'png-b',(folder/'demo__b.png').read_bytes())
            with self.assertRaises(FileExistsError):export(resources,out)
            (resources/'assets/demo/textures/b.png').unlink()
            with self.assertRaises(FileNotFoundError):export(resources,root/'failed',jar=root/'missing.jar')
            self.assertFalse((root/'failed').exists())
            self.assertFalse(list(root.glob('artist-*')))


if __name__ == '__main__':unittest.main()
