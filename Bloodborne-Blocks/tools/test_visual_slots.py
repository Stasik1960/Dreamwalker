from __future__ import annotations

import gzip
import hashlib
import json
import tempfile
import unittest
from pathlib import Path

import build_visual_slots


class VisualSlotsTests(unittest.TestCase):
 def fixture(self, root: Path):
  logical=root/'bloodborne_blocks/logical'; logical.mkdir(parents=True)
  definitions={'blocks':[
   {'id':'o_visible','logical':True,'creative':True,'properties':{'facing':['north'],'embedded':['true','false']},'default':{'facing':'north','embedded':'true'},'placement_properties':{'embedded':'false'},'states':{'embedded=true,facing=north':[0,0,0],'embedded=false,facing=north':[0,0,0]},'models':{'embedded=true,facing=north':'mesh','embedded=false,facing=north':'mesh'}},
   {'id':'o_hidden','logical':True,'creative':True,'properties':{'facing':['north']},'default':{'facing':'north'},'states':{'facing=north':[0,0,0]},'models':{'facing=north':'mesh'}}]}
  geometry={'blocks':{
   'o_visible':{'states':{state:{'cells':{'0,0,0':{}}} for state in ('embedded=true,facing=north','embedded=false,facing=north')}},
   'o_hidden':{'states':{'facing=north':{'cells':{'0,0,0':{}}}}}}}
  contract={'families':[
   {'id':'o_visible','canonical_anchor':{'cell':[0,0,0],'pivot':[.5,0,.5]},'states':{state:{'migration_source_pattern':[{'components':[]}]} for state in ('embedded=true,facing=north','embedded=false,facing=north')}},
   {'id':'o_hidden','canonical_anchor':{'cell':[0,0,0],'pivot':[.5,0,.5]},'states':{'facing=north':{'migration_source_pattern':[{'components':[]}]}}}]}
  for name,value in (('definitions.json',definitions),('geometry.json',geometry),('contracts-v2.json',contract),('hidden-items.json',['o_hidden'])):
   (logical/name).write_text(json.dumps(value),encoding='utf8')
  with gzip.open(logical/'meshes.json.gz','wt',encoding='utf8') as stream: json.dump({'mesh':{'polygons':[{'texture':'minecraft:block/stone','vertices':[[0,0,0,0,0],[1,0,0,16,0],[0,1,0,0,16]]}]}},stream)
  (root/'bloodborne_blocks/aliases.json').write_text(json.dumps({'aliases':{},'removed':[]}),encoding='utf8')
  for ident in ('o_visible','o_hidden'):
   path=root/f'assets/bloodborne_blocks/models/item/{ident}.json'; path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps({'parent':'old','display':{'gui':{'scale':[2,2,2]}}}),encoding='utf8')
  for locale in ('en_us','ru_ru'):
   path=root/f'assets/bloodborne_blocks/lang/{locale}.json';path.parent.mkdir(parents=True,exist_ok=True);path.write_text('{}',encoding='utf8')
 def digest(self, root):
  return {p.relative_to(root).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*') if p.is_file()}
 def test_generator_is_idempotent_and_preserves_base_migration_hidden_and_item_display(self):
  with tempfile.TemporaryDirectory() as folder:
   root=Path(folder);self.fixture(root);build_visual_slots.build(root);first=self.digest(root);build_visual_slots.build(root);self.assertEqual(first,self.digest(root))
   definitions=json.loads((root/'bloodborne_blocks/logical/definitions.json').read_text());visible=next(x for x in definitions['blocks'] if x['id']=='o_visible');hidden=next(x for x in definitions['blocks'] if x['id']=='o_hidden')
   self.assertEqual(['base','alt'],visible['properties']['visual']);self.assertNotIn('visual',hidden['properties'])
   contract=json.loads((root/'bloodborne_blocks/logical/contracts-v2.json').read_text());family=next(x for x in contract['families'] if x['id']=='o_visible')
   self.assertEqual([{'components':[]}],family['states']['embedded=false,facing=north,visual=base']['migration_source_pattern']);self.assertEqual([],family['states']['embedded=false,facing=north,visual=alt']['migration_source_pattern'])
   item=json.loads((root/'assets/bloodborne_blocks/models/item/o_visible.json').read_text());self.assertEqual({'gui':{'scale':[2,2,2]}},item['display']);self.assertEqual('bloodborne_blocks:block/logical/o_visible/base/embedded_false__facing_north',item['parent'])

if __name__=='__main__': unittest.main()
