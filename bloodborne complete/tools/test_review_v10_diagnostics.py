"""Pure bounded snapshot-gate regressions; not a fabricated runtime PASS."""
import copy
import hashlib
import unittest
import uuid
from verify_review_v10_diagnostics import persisted_sections, sorted_json, gson_packet_text, native_registry


def uid(name): return str(uuid.uuid5(uuid.NAMESPACE_DNS, name))


def envelope():
    def ref(name, kind='RP', registry='lever_1'):
        return {'uuid': uid(name), 'kind': kind, 'dimension': 'minecraft:overworld', 'root': [0, 64, 0], 'registry': registry}
    refs = {'architecture_door': ref('door', 'ARCHITECTURE', 'bloodborne_dw:prototype_double_door'),
            'rp_door': ref('rpdoor', registry='door_1'), 'wood_gate': ref('gate', registry='wood_gate')}
    command = 'scoreboard players add event_counter dw_v10_gui 1'
    state = {'rule': {'uuid': uid('rule'), 'name': 'QA All', 'order': 1, 'condition': 'ALL', 'effect': 'TOGGLE',
                     'incomplete': True, 'satisfied': False,
                     'sources': [dict(ref(key), savedActive=False) for key in ('lever_1', 'lever_2')], 'targets': list(refs.values())},
             'sourceFlags': {key: {'uuid': uid(key), 'ruleMember': key != 'lever_3', 'savedActive': False}
                             for key in ('lever_1', 'lever_2', 'lever_3')},
             'targets': {key: {'identity': value, 'open': False, 'pendingEffect': False, 'leversOnly': key == 'architecture_door',
                               'afterOpen': [command] if key == 'architecture_door' else [],
                               'afterClose': [command] if key == 'architecture_door' else [], 'actualPulseIdle': True}
                         for key, value in refs.items()},
             'harmlessCommandCounter': 2,
             'removedFixtures': {'lamp_C_uuid': uid('lamp_C'), 'lever_3_uuid': uid('lever_3'),
                                 'lamp_C_still_registered': False, 'lever_3_still_rule_member': False}}
    a, b, d = (uid(key) for key in ('nodeA', 'nodeB', 'nodeD'))
    state['lampGraph'] = {'schema': 2,
                         'nodes': [{'nodeUuid': node, 'entityUuid': uid(name), 'name': name,
                                    'position': [60.0, 64.125 if name == 'QA B' else 64.0, -16.0], 'routes': routes}
                                   for name, node, routes in [('QA A renamed', a, [b, d]), ('QA B', b, []), ('QA D', d, [a])]],
                         'lines': [{'lineUuid': uid('line'), 'name': 'QA Main', 'connections': [
                             {'connectionUuid': uid('pairAB'), 'a': a, 'b': b, 'aToB': True, 'bToA': False},
                             {'connectionUuid': uid('pairAD'), 'a': a, 'b': d, 'aToB': True, 'bToA': True}]}]}
    return rehash({'schema': 'dw-v10-menus-persisted-state-v1', 'state': state})


def rehash(value):
    value['canonicalJson'] = gson_packet_text(sorted_json(value['state']))
    value['sha256'] = hashlib.sha256(value['canonicalJson'].encode('utf8')).hexdigest()
    return value


class SnapshotTests(unittest.TestCase):
    def test_actual_native_state_string_uses_block_registry_wrapper(self):
        self.assertEqual(native_registry('Block{bloodborne_dw:prototype_double_door}[open=false,rotation=0]'), 'bloodborne_dw:prototype_double_door')
        with self.assertRaises(ValueError): native_registry('unparsed alleged state')

    def test_actual_contract_valid_semantic_sections(self):
        value = envelope()
        self.assertEqual(persisted_sections(value)['sha256'], value['sha256'])

    def test_changed_bytes_with_stale_digest_are_rejected(self):
        value = envelope(); value['state']['harmlessCommandCounter'] = 3
        with self.assertRaises(ValueError): persisted_sections(value)

    def test_rehashed_extra_event_is_still_rejected(self):
        value = envelope(); value['state']['harmlessCommandCounter'] = 3; rehash(value)
        with self.assertRaises(ValueError): persisted_sections(value)

    def test_one_way_connection_cannot_silently_become_bidirectional(self):
        value = envelope(); value['state']['lampGraph']['lines'][0]['connections'][0]['bToA'] = True; rehash(value)
        with self.assertRaises(ValueError): persisted_sections(value)

    def test_replaced_target_uuid_is_not_same_persisted_instance(self):
        value = envelope(); value['state']['targets']['rp_door']['identity'] = copy.deepcopy(value['state']['targets']['rp_door']['identity'])
        value['state']['targets']['rp_door']['identity']['uuid'] = uid('replacement'); rehash(value)
        with self.assertRaises(ValueError): persisted_sections(value)

    def test_removed_required_source_cannot_reappear(self):
        value = envelope(); value['state']['sourceFlags']['lever_3']['ruleMember'] = True; rehash(value)
        with self.assertRaises(ValueError): persisted_sections(value)


if __name__ == '__main__': unittest.main()
