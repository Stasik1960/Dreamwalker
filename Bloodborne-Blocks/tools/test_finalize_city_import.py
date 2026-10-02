import json, tempfile, unittest
from pathlib import Path

from finalize_city_import import finalize
from test_city_palette import CityPaletteTests
from test_logical_world import write_chunk, entity
from convert_logical_world import World, state_tag


class FinalizeCityImportTests(unittest.TestCase):
    def city(self, root):
        return CityPaletteTests().resources(root)

    def run_case(self, blocks, entities=()):
        root = Path(tempfile.mkdtemp()); city = self.city(root); source = root / 'source'; out = root / 'out'; report = root / 'report.json'
        source.mkdir(parents=True, exist_ok=True); (source / 'level.dat').write_bytes(b'test level')
        write_chunk(source / 'region/r.0.0.mca', 0, 0, blocks, entities)
        return root, city, source, out, report

    def test_mapped_cell_changes_and_every_other_cell_and_entity_survives(self):
        root, city, source, out, report = self.run_case({(1,64,1): ('bloodborne_blocks:m_old', {'facing':'east'}), (2,64,1): ('minecraft:stone', {})}, (entity((2,64,1), 'minecraft:chest'),))
        result = finalize(source, out, city, report)
        self.assertEqual('complete', result['status']); self.assertEqual(1, result['changedCells'])
        world = World(out, {})
        self.assertEqual('bloodborne_blocks:city_test', world.get('minecraft:overworld', (1,64,1))[0])
        self.assertEqual('minecraft:stone', world.get('minecraft:overworld', (2,64,1))[0])
        self.assertEqual(1, len(world.block_entities()))
        self.assertEqual('bloodborne_blocks:m_old', World(source, {}).get('minecraft:overworld', (1,64,1))[0])

    def test_unknown_writes_exact_coordinate_ledger_and_no_output(self):
        root, city, source, out, report = self.run_case({(3,64,2): ('bloodborne_blocks:m_missing', {})})
        result = finalize(source, out, city, report)
        self.assertEqual('blocked', result['status']); self.assertFalse(out.exists())
        data = json.loads(report.read_text())
        self.assertEqual([[3,64,2]], data['unknownStates']['bloodborne_blocks:m_missing']['cells'])
        self.assertEqual('pending_review', data['unknownStates']['bloodborne_blocks:m_missing']['status'])

    def test_foreign_block_entity_refuses_and_cleans_output(self):
        root, city, source, out, report = self.run_case({(1,64,1): ('bloodborne_blocks:m_old', {'facing':'east'})}, (entity((1,64,1), 'minecraft:chest'),))
        with self.assertRaisesRegex(ValueError, 'foreign block entity'):
            finalize(source, out, city, report)
        self.assertFalse(out.exists())

    def test_report_never_writes_inside_source_or_output(self):
        root, city, source, out, report = self.run_case({(1,64,1): ('bloodborne_blocks:m_old', {'facing':'east'})})
        original = (source / 'region/r.0.0.mca').read_bytes()
        resources_before = (city / 'definitions.json').read_bytes()
        for unsafe in (source / 'report.json', out / 'report.json', source, out, city / 'definitions.json'):
            with self.assertRaisesRegex(ValueError, 'report must be outside'):
                finalize(source, out, city, unsafe)
        self.assertFalse(out.exists())
        self.assertFalse((source / 'report.json').exists())
        self.assertEqual(original, (source / 'region/r.0.0.mca').read_bytes())
        self.assertEqual(resources_before, (city / 'definitions.json').read_bytes())


if __name__ == '__main__': unittest.main()
