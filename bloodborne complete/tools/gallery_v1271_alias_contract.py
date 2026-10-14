"""Exact compatibility expectations from the packaged imported alias manifests.

This only models the documented finite alias migration. It does not rewrite
worlds, UUIDs, geometry, original source rows or the authored manifest.
"""
import copy, json, math
from collections import Counter


class AliasContract:
    def __init__(self, jar):
        self.canonical = {}; self.mappings = {}; self.checked = []
        original = json.loads(jar.read('bloodborne_dw/imported-type-aliases.json'))
        newer = json.loads(jar.read('bloodborne_dw/imported-type-aliases-v127.json'))
        assert original['schemaVersion'] == newer['schemaVersion'] == 1
        for group in original['groups']:
            primary = group['primaryRegistryId']; assert primary in group['members']
            for member in group['members']:
                assert member.startswith('bloodborne_dw:source_') and member not in self.canonical
                self.canonical[member] = primary
        for row in original['mappings']:
            key = row['oldRegistryId'], row['oldVariant']; assert key not in self.mappings
            self.mappings[key] = copy.deepcopy(row)
        additions = {}
        for group in newer['groups']:
            primary = group['primaryRegistryId']; assert primary in group['members']
            for member in group['members']:
                assert member.startswith('bloodborne_dw:source_') and self.canonical.get(member, member) == member
                self.canonical[member] = primary
        for row in newer['mappings']:
            assert row['rotationDelta'] == 0 and row['sourceShift'] == [0, 0, 0] and not row['properties']
            key = row['oldRegistryId'], row['oldVariant']; assert key not in additions
            additions[key] = copy.deepcopy(row)
        # Java composes the final target/pose, retaining the historical yaw,
        # shift and properties. Then current V127 primary mappings supersede
        # their older self mappings.
        for row in self.mappings.values():
            follow = additions.get((row['newRegistryId'], row['newVariant']))
            if follow:
                row.update(newRegistryId=follow['newRegistryId'], newVariant=follow['newVariant'], newId=follow['newId'])
        self.mappings.update(additions)
        for member in self.canonical:
            target = self.canonical[member]; seen = set()
            while target != self.canonical.get(target, target):
                assert target not in seen; seen.add(target); target = self.canonical[target]
            self.canonical[member] = target
        for row in self.mappings.values():
            assert row['newRegistryId'] == self.canonical[row['oldRegistryId']]
        stairs = json.loads(jar.read('bloodborne_dw/imported-stair-types.json'))
        self.stairs = {'bloodborne_dw:' + row['path'] for row in stairs['stairTypes']}
        self.ladders = {'bloodborne_dw:' + row['path'] for row in stairs['ladderTypes']}
        self.target_pose_counts = {}
        for registry in {row['newRegistryId'] for row in self.mappings.values()}:
            descriptor = json.loads(jar.read('bloodborne_dw/composite/' + registry.split(':')[1] + '.json'))
            self.target_pose_counts[registry] = len(descriptor['variants'])

    @staticmethod
    def rotate(shift, yaw):
        angle = math.pi * (yaw & 7) / 4; c, s = math.cos(angle), math.sin(angle)
        x, y, z = shift; return [x*c-z*s, y, x*s+z*c]

    def expected_registry(self, row):
        registry = row['registry']; state = row.get('properties', row.get('blockStateTag', {}))
        # Nonzero banks are current canonical art and are never interpreted
        # as one of the historical low-four-bit mappings.
        if int(state.get('art_bank', '0')) != 0: return registry
        mapping = self.mappings.get((registry, int(state.get('variant', '0'))))
        return mapping['newRegistryId'] if mapping and mapping['oldRegistryId'] != mapping['newRegistryId'] else registry

    def verify(self, row, actual, translation):
        expected = self.expected_registry(row)
        if expected == row['registry']: return expected
        assert row['key'].startswith('city/'), 'Unexpected compatibility alias in clean main/review catalogue'
        assert all(float(value).is_integer() for value in translation), 'City alias proof requires original integer translation'
        old = row['properties']; mapping = self.mappings[row['registry'], int(old.get('variant', '0'))]
        state = row['placedState']; selectors = dict(mapping.get('properties', {}))
        variant = mapping['newVariant']
        if expected in self.stairs: variant = variant if variant < 10 else 0
        if expected in self.ladders: variant = 1 if old.get('height', 'one') == 'two' else 0
        selectors.update(variant=str(variant), rotation=str((int(old.get('rotation', '0')) + mapping['rotationDelta']) & 7))
        for key, default in [('art_bank', '0'), ('profile', 'base'), ('open', 'false')]:
            selectors.setdefault(key, old.get(key, default))
        for key, value in selectors.items():
            if key == 'art_bank' and key not in state:
                assert str(value) == '0' and self.target_pose_counts[expected] <= 16, ('Banked canonical alias lost art_bank property', row['key'])
                continue
            assert state.get(key) == str(value), ('Canonical state selector differs from packaged mapping', row['key'], key, value, state)
        payload = actual['nbt']['payload']
        assert payload['ImportedTypeVersion'] == 1
        supplied = row.get('sourceShift', [0, 0, 0])
        rotated = self.rotate(supplied, -mapping['rotationDelta'])
        shift = [rotated[i] + mapping['sourceShift'][i] for i in range(3)]
        stored_shift = payload.get('SourceShift', [0, 0, 0])
        # ImportedTypeAliases.writeShift removes the field for exactly zero.
        if 'SourceShift' not in payload: assert shift == [0, 0, 0]
        assert len(stored_shift) == 3 and all(abs(a-b) < 1e-10 for a,b in zip(stored_shift, shift)), ('Canonical SourceShift differs from packaged mapping', row['key'], shift, stored_shift)
        # Source provenance is inert and must survive compatibility placement.
        for key, value in row.get('payload', {}).items():
            if key.startswith(('Original', 'SourceAlias')):
                assert payload.get(key) == value, ('Original source archive changed', row['key'], key)
        self.checked.append({'key': row['key'], 'sourceRegistry': row['registry'], 'canonicalRegistry': expected,
                             'sourceVariant': int(old.get('variant', '0')), 'canonicalVariant': variant,
                             'artBank': state.get('art_bank', '0'), 'rotationDelta': mapping['rotationDelta'], 'sourceShift': shift,
                             'reason': 'PACKAGED_EXACT_COMPATIBILITY_ALIAS_STATE_AND_PAYLOAD'})
        return expected

    def report(self):
        pairs = Counter(row['sourceRegistry'] + ' -> ' + row['canonicalRegistry'] for row in self.checked)
        return {'status': 'PASS_EXACT_PACKAGED_ALIAS_STATE_SHIFT_AND_SOURCE_ARCHIVE',
                'normalizedOwners': len(self.checked), 'registryPairs': dict(sorted(pairs.items())),
                'reason': 'Original source aliases migrate through packaged finite compatibility mappings; UUID/root and geometry remain strict',
                'rows': self.checked}
