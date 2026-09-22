import sys, types, unittest
from unittest.mock import patch
import numpy as np
import json
from pathlib import Path


class SignatureTests(unittest.TestCase):
    def setUp(self):
        visuals=types.ModuleType('source_assembly_visuals')
        def polys(apps):
            out=[]
            for app in apps:
                tag=app['model']; size=2 if tag.endswith('wide') else 1
                vertices=np.array([[0,0,0,0,0],[size,0,0,1,0],[.25,1,.37,0,1]],float)
                if app.get('y',0) == 90: vertices[:,:3]=vertices[:,:3] @ np.array([[0,0,-1],[0,1,0],[1,0,0]]).T
                if tag.endswith('mirror'): vertices[:,0] *= -1
                out.append({'texture':tag.replace('_mirror',''),'vertices':vertices.tolist()})
            return out,[]
        visuals.source_polys=polys; geometry=types.ModuleType('catalog_geometry')
        def rotation(axis,angle):
            a=np.radians(angle); c,s=np.cos(a),np.sin(a); return np.array([[c,0,s],[0,1,0],[-s,0,c]]) if axis==1 else np.eye(3)
        geometry.rotation=rotation
        self.modules = patch.dict(sys.modules,{'source_assembly_visuals':visuals,'catalog_geometry':geometry})
        self.modules.start(); self.addCleanup(self.modules.stop)
        from source_assembly_signatures import descriptor, equivalent
        import source_assembly_signatures as signatures
        signatures._local_polys.cache_clear(); self.addCleanup(signatures._local_polys.cache_clear)
        type(self).descriptor,type(self).equivalent=descriptor,equivalent
    def cell(self,n,x,model='minecraft:a', choices=None): return {'number':n,'relative':[x,0,0],'apps':[{'model':model,'offset':[x,0,0]}],'model_choices':choices or []}
    def test_translation_rotation_and_mirror_boundary(self):
        a=type(self).descriptor([self.cell(1,0),self.cell(2,1)]); b=type(self).descriptor([self.cell(1,4),self.cell(2,5)])
        self.assertIsNotNone(type(self).equivalent(a,b)); self.assertEqual(type(self).equivalent(a,b)['method'],'exact_geometry')
        mirrored=type(self).descriptor([self.cell(1,0),self.cell(2,-1)]); self.assertIsNotNone(type(self).equivalent(a,mirrored))  # yaw 180 is allowed
    def test_fuzzy_and_all_alternatives_bucket(self):
        choices=[[[{'model':'minecraft:a','offset':[0,0,0]}],[{'model':'minecraft:wide','offset':[0,0,0]}]]]
        a=type(self).descriptor([self.cell(1,0,choices=choices),self.cell(2,1)])
        shifted=[[[{**app,'offset':[.5,0,0]} for app in option] for option in choices[0]]]
        b=type(self).descriptor([self.cell(1,.5,choices=shifted),self.cell(2,1.5)])
        self.assertIsNotNone(type(self).equivalent(a,b))
        changed=type(self).descriptor([self.cell(1,.5,choices=[]),self.cell(2,1.5)])
        self.assertIsNone(type(self).equivalent(a,changed))

    def test_fuzzy_rejects_spread_and_incompatible_individual_orientation(self):
        a=type(self).descriptor([self.cell(1,0),self.cell(2,1)])
        spread=type(self).descriptor([self.cell(1,0),self.cell(2,2)])
        self.assertIsNone(type(self).equivalent(a,spread))
        rotated=type(self).descriptor([self.cell(1,.5),self.cell(2,1.5)])
        rotated['exact_geometry_signature'] = 'orientation-test'
        for entry in rotated['components']:
            entry['orientation_signatures'] = ['mirror-only'] * 4
        self.assertIsNone(type(self).equivalent(a,rotated))

    def test_exact_reports_yaw90_and_rejects_asymmetric_reflection(self):
        a=type(self).descriptor([{'number':1,'apps':[{'model':'minecraft:asym'}]}])
        turned=type(self).descriptor([{'number':1,'apps':[{'model':'minecraft:asym','y':90}]}])
        self.assertEqual(type(self).equivalent(a,turned)['yaw'], 90)
        mirror=type(self).descriptor([{'number':1,'apps':[{'model':'minecraft:asym_mirror'}]}])
        self.assertIsNone(type(self).equivalent(a,mirror))

    def test_fuzzy_threshold_is_symmetric(self):
        a=type(self).descriptor([self.cell(1,0),self.cell(2,5)])
        b=type(self).descriptor([self.cell(1,0),self.cell(2,6)])
        self.assertEqual(type(self).equivalent(a,b) is None,type(self).equivalent(b,a) is None)

    def test_repeated_art_pairing_is_symmetric_and_permutation_invariant(self):
        left=[self.cell(i,x) for i,x in enumerate((0,1,3,7),1)]
        right=[self.cell(i,x) for i,x in enumerate((1,2,3,7),1)]
        a,b=map(type(self).descriptor,(left,right))
        forward,backward=type(self).equivalent(a,b),type(self).equivalent(b,a)
        self.assertIsNotNone(forward); self.assertIsNotNone(backward)
        self.assertEqual(forward['max_displacement'],backward['max_displacement'])
        self.assertEqual(forward,type(self).equivalent(type(self).descriptor(left[::-1]),type(self).descriptor(right[::-1])))

    def test_alternative_relative_offset_is_not_erased(self):
        a=self.cell(1,0,choices=[[[{'model':'minecraft:a','offset':[0,0,0]}],[{'model':'minecraft:wide','offset':[0,0,0]}]]])
        import copy
        b=copy.deepcopy(a); b['model_choices'][0][1][0]['offset']=[2,0,0]
        self.assertIsNone(type(self).equivalent(type(self).descriptor([a]),type(self).descriptor([b])))

    def test_z_real_c001_c005_full_choice_groups_review_equivalent(self):
        # This deliberately exercises the checked-in original-pack resolver,
        # including each candidate's complete model_choices payload.
        self.modules.stop()
        import source_assembly_signatures as real
        real._local_polys.cache_clear()
        rows = {row['review_id']: row for row in json.loads((Path(__file__).parents[1] / 'docs/manual-review/source-assemblies/batch-01/batch-01-manifest.json').read_text(encoding='utf8'))['candidates']}
        from source_assembly_pipeline import component
        from manual_review_world import _load_blockstates, DEFAULT_SOURCEPACK, DEFAULT_VANILLA
        blockstates = _load_blockstates(DEFAULT_SOURCEPACK,DEFAULT_VANILLA)
        def complete(row):
            return [component((c['source']['id'],tuple(sorted(c['source']['properties'].items()))),c['relative'],c['number'],blockstates) for c in row['components']]
        result = real.equivalent(real.descriptor(complete(rows['C001'])),real.descriptor(complete(rows['C005'])))
        self.assertIsNotNone(result)
        self.assertEqual(result['yaw'],0)
        self.assertEqual(result['max_displacement'],.5)
        windows=[component(('minecraft:warped_stairs',tuple(sorted({'facing':f,'half':'top','shape':'straight','waterlogged':'false'}.items()))),[0,0,0],1,blockstates) for f in ('south','west')]
        a,b=[real.descriptor([c]) for c in windows]
        self.assertEqual(a['canonical_visual_family_signature'],b['canonical_visual_family_signature'])
        self.assertIsNotNone(real.equivalent(a,b))


if __name__ == '__main__': unittest.main()
