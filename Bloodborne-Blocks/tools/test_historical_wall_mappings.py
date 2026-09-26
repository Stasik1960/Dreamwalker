"""Exact historical artwork, safe conversion and second-pass regression."""
import json
import tempfile
import unittest
from collections import defaultdict
from pathlib import Path
from inspect_historical_wall_meshes import mappings, MANIFEST
from convert_logical_world import DEFAULT_RESOURCES, World, convert
from modded_world_adapter import compile_modded_rules
from test_logical_world import assembly_source, tree_hash
from check_logical_world import check


class HistoricalWallMappingsTests(unittest.TestCase):
    def test_reconstruct_every_mapping_from_frozen_artifact(self):
        self.assertEqual(json.loads(MANIFEST.read_bytes()), mappings())

    def test_no_ambiguous_target_for_an_exact_module(self):
        rules, _, _ = compile_modded_rules(DEFAULT_RESOURCES)
        targets = defaultdict(set)
        selected = {r.source.state for r in rules if
                    (r.source_reference or '').startswith('proven complete historical wall')}
        for rule in rules:
            if rule.source.state in selected and not rule.members:
                targets[rule.source.state].add(rule.target)
        self.assertTrue(targets)
        self.assertTrue(all(len(v) == 1 for v in targets.values()), targets)

    def test_complete_walls_preserve_neighbors_and_second_pass(self):
        rules, defaults, _ = compile_modded_rules(DEFAULT_RESOURCES)
        ids = ('m_0730cfd72a0dbe29', 'm_5992264774a8b7bf')
        picked = [next(r for r in rules if r.source.state[0] == 'bloodborne_blocks:'+ident
                       and not r.members) for ident in ids]
        positions = ((3, 64, 3), (10, 64, 10))
        blocks = {p: (r.source.state[0], dict(r.source.state[1])) for p, r in zip(positions, picked)}
        neighbor = (4, 64, 3); blocks[neighbor] = ('minecraft:diamond_block', {})
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp); source = base/'source'; first = base/'first'; second = base/'second'
            assembly_source(source, blocks)
            report = base/'first.json'
            convert(source, first, source_mode='modded', report_root=base, report_path=report)
            world = World(first, defaults)
            for pos, rule in zip(positions, picked): self.assertEqual(rule.target, world.get('minecraft:overworld', pos))
            self.assertEqual(('minecraft:diamond_block', ()), world.get('minecraft:overworld', neighbor))
            self.assertTrue(check(source, first, report, DEFAULT_RESOURCES)['ok'])
            again = convert(first, second, source_mode='modded', report_root=base, report_path=base/'second.json')
            self.assertEqual(0, again['counts']['converted'])
            self.assertEqual(tree_hash(first), tree_hash(second))


if __name__ == '__main__': unittest.main()
