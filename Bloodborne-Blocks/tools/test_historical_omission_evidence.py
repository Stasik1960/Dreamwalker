"""A ledger is evidence only when source, fragment and current context agree."""
import gzip
import json
import unittest
from historical_omission_evidence import OmissionEvidence
from inspect_historical_wall_meshes import historical_art, carrier_cells, ROOT
from composite_world_oracle import EvidenceReader
from verify_composite_overlap_evidence import signature
from build_city_compat import rotated_mesh


class HistoricalOmissionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.evidence = OmissionEvidence()
        with historical_art() as jar:
            cls.meshes = json.loads(gzip.decompress(jar.read('bloodborne_blocks/v2/meshes.json.gz')))
            cls.expected = signature(carrier_cells(jar,'dead_horn_coral_fan',{'waterlogged':'false'})[(0,0,2)])
        cls.root = (-644,83,-25); cls.point = (-644,83,-23)
        cls.source = 'minecraft:dead_horn_coral_fan[waterlogged=false]'
        reader = EvidenceReader(ROOT/'reference-inputs/latest-modded-world.zip')
        try: cls.current = reader.state('minecraft:overworld',cls.point)[1]
        finally: reader.close()

    @classmethod
    def polygons(cls, ident, facing):
        if ident not in cls.meshes: return None
        return signature(rotated_mesh(cls.meshes[ident],('north','east','south','west').index(facing))['polygons'])

    def test_exact_historical_omission_is_identified_without_write_permission(self):
        witness = self.evidence.witness(self.root,self.point,self.source,self.current,self.expected,self.polygons)
        self.assertIsNotNone(witness)
        self.assertEqual('world-static-grid-v2.json',witness['ledger'])
        self.assertEqual('bloodborne_blocks:architecture_part',witness['preserveState'])
        self.assertIn('Not conversion authorization',witness['policy'])

    def test_changed_context_is_not_approved_by_an_old_ledger(self):
        self.assertIsNone(self.evidence.witness(self.root,self.point,self.source,'minecraft:air',self.expected,self.polygons))

    def test_wrong_source_state_is_not_approved(self):
        self.assertIsNone(self.evidence.witness(self.root,self.point,'minecraft:dead_horn_coral_fan[waterlogged=true]',self.current,self.expected,self.polygons))

    def test_incomplete_textured_fragment_is_not_approved(self):
        altered = set(self.expected); altered.pop()
        self.assertIsNone(self.evidence.witness(self.root,self.point,self.source,self.current,altered,self.polygons))


if __name__ == '__main__': unittest.main()
