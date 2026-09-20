"""Write reviewed logical families; this never reads or edits a world.

Unlike the old palette migration, this describes complete objects and states.
Finite assemblies require all listed legacy members during conversion.
"""
from catalog_geometry import *

FACING = ('north', 'east', 'south', 'west')


def main():
    definitions = {b['id']: b for b in DATA['blocks']}
    blockstates = {}

    def apps(ident, properties):
        bs = blockstates.setdefault(ident, json.loads((ASSETS / 'blockstates' / (ident + '.json')).read_text()))
        state = {**definitions[ident]['default'], **properties}
        groups = applications(bs, state)
        if ident != 'iron_bars' and any(len(group) != 1 for group in groups):
            raise ValueError('Random model not eligible for a deterministic curated family: ' + ident)
        return [copy.deepcopy(group[0]) for group in groups]

    objects = []
    # Authored bounds meet every three blocks. The original map has 130 exact
    # white -> orange(+3) -> magenta(+6) stacks, with no horizontal offset.
    states = []
    for facing in FACING:
        source = {'assembled': 'false', 'facing': facing}
        states.append({'properties': {'facing': facing},
                       'apps': apps('white_wool', source) +
                               [{**a, 'offset': [0, 3, 0]} for a in apps('orange_wool', source)] +
                               [{**a, 'offset': [0, 6, 0]} for a in apps('magenta_wool', source)],
                       'sources': [{'id': 'white_wool', 'match': source, 'members': [
                           {'id': 'orange_wool', 'match': source, 'offset': [0, 3, 0]},
                           {'id': 'magenta_wool', 'match': source, 'offset': [0, 6, 0]}]}]})
    objects.append({'id': 'o_dead_tree_planter', 'name': 'Сухое дерево в каменном основании',
                    'semantic': 'tree', 'behavior': 'static', 'states': states,
                    'evidence': '130 exact vertical source-world triples; 3-block authored panel heights.'})

    states = []
    for turn, facing in enumerate(FACING):
        for opened in ('false', 'true'):
            models = [{'model': 'bloodborne_blocks:block/addon/gate_bottom', 'y': turn * 90},
                      {'model': 'bloodborne_blocks:block/addon/gate_top', 'y': turn * 90, 'offset': [0, 3, 0]}]
            if opened == 'true':
                models = [{**a, 'open_double': True} for a in models]
            sources = []
            if opened == 'false' and turn in (0, 1):
                match = {'assembled': 'false', 'axis': 'z' if turn == 0 else 'x'}
                sources.append({'id': 'stripped_jungle_log', 'match': match,
                                'members': [{'id': 'stripped_acacia_log', 'match': match, 'offset': [0, 3, 0]}]})
            states.append({'properties': {'facing': facing, 'open': opened}, 'apps': models, 'sources': sources})
    objects.append({'id': 'o_iron_gate', 'name': 'Большие железные ворота',
                    'semantic': 'fence', 'behavior': 'gate', 'states': states,
                    'evidence': 'Nine exact source-world bottom/top pairs at +3; +6 repeated gates not consumed.'})
    # Each old lower panel occupies y=-1..2. Its upper panel occupies y=0..1,
    # thus the matching header is two cells above the lower carrier origin.
    # Existing functional meshes retain jambs and rotate the two door leaves.
    for carrier, label in [('acacia_stairs', 'Большая решётчатая дверь'),
                           ('birch_stairs', 'Большая резная дверь'),
                           ('dark_oak_stairs', 'Большая филёнчатая дверь')]:
        states = []
        # Birch's header starts at -1 rather than 0. The source-map pair
        # frequency confirms +3 (22 instances), versus +2 for the other doors.
        header_y = 3 if carrier == 'birch_stairs' else 2
        for facing, opened in itertools.product(FACING, ('false', 'true')):
            low = {'assembled': 'false', 'facing': facing, 'half': 'bottom', 'shape': 'straight', 'open': opened}
            high = {**low, 'half': 'top'}
            models = apps(carrier, low) + [{**a, 'offset': [0, header_y, 0]} for a in apps(carrier, {**high, 'open': 'false'})]
            states.append({'properties': {'facing': facing, 'open': opened}, 'apps': models,
                           'sources': [{'id': carrier, 'match': low, 'members': [{'id': carrier, 'match': high, 'offset': [0, header_y, 0]}]}]})
        objects.append({'id': 'o_' + carrier.replace('_stairs', '_door'), 'name': label,
                        'semantic': 'door', 'behavior': 'door', 'states': states,
                        'evidence': 'Authored bounds and repeated source-world co-occurrence; exact-member migration only.'})

    states = []
    for facing in FACING:
        low = {'assembled': 'false', 'facing': facing, 'half': 'bottom', 'shape': 'straight'}
        high = {**low, 'half': 'top'}
        states.append({'properties': {'facing': facing},
                       'apps': apps('warped_stairs', low) + [{**a, 'offset': [0, 2, 0]} for a in apps('warped_stairs', high)],
                       'sources': [{'id': 'warped_stairs', 'match': low,
                                    'members': [{'id': 'warped_stairs', 'match': high, 'offset': [0, 2, 0]}]}]})
    objects.append({'id': 'o_tall_arched_window', 'name': 'Высокое арочное окно',
                    'semantic': 'window', 'behavior': 'static', 'states': states,
                    'evidence': '45 exact bottom/top pairs at vertical offset +2; authored bounds join at y=1.'})

    # These two authored meshes are the closed/open version of the same arched
    # shuttered window, not vanilla horizontal trapdoors. Top-half variants
    # contain unrelated sprite artwork and are deliberately not captured.
    states = []
    for facing, opened in itertools.product(FACING, ('false', 'true')):
        source = {'assembled': 'false', 'half': 'bottom', 'facing': facing, 'open': opened}
        models = apps('spruce_trapdoor', source)
        if any(a['model'].split('/')[-1] not in ('spruce_trapdoor_bottom', 'spruce_trapdoor_open') for a in models):
            continue
        states.append({'properties': {'facing': facing, 'open': opened}, 'apps': models,
                       'sources': [{'id': 'spruce_trapdoor', 'match': source}]})
    if len(states) == 8:
        objects.append({'id': 'o_shuttered_window', 'name': 'Арочное окно со ставнями',
                        'semantic': 'window', 'behavior': 'shutter', 'states': states})

    # A complete authored toothed rib: the v2 section cuts were never separate
    # player-facing objects. Repeated vertical ribs are not guessed as one spire.
    states = []
    for facing in FACING:
        source = {'assembled': 'false', 'facing': facing}
        states.append({'properties': {'facing': facing}, 'apps': apps('coal_block', source),
                       'sources': [{'id': 'coal_block', 'match': source}]})
    objects.append({'id': 'o_toothed_stone_rib', 'name': 'Каменное ребро с зубцами',
                    'semantic': 'ornament', 'behavior': 'static', 'states': states})

    # Preserve the source multipart geometry but use genuine neighbour-driven
    # properties. Every corner is a state, not an independently registered item.
    # The v2 generator selected the first weighted iron-bars alternative. Use
    # that same canonical artwork; no random branch becomes a separate item.
    states = []
    for values in itertools.product(('false', 'true'), repeat=4):
        target = dict(zip(FACING, values))
        models = apps('iron_bars', {**target, 'facing': 'north'})
        sources = []
        for turn, facing in enumerate(FACING):
            # Source extra-facing rotates its entire multipart after choosing
            # the directions. Undo that rotation in the matched source mask.
            old = {FACING[i]: target[FACING[(i + turn) % 4]] for i in range(4)}
            sources.append({'id': 'iron_bars', 'match': {**old, 'facing': facing}})
        states.append({'properties': target, 'apps': models, 'sources': sources})
    objects.append({'id': 'o_iron_railing', 'name': 'Соединяемая железная ограда',
                    'semantic': 'fence', 'behavior': 'connected', 'connection_family': 'iron_railing', 'states': states})

    # The low wall arm ends at 14/16. When stacked, use the authored tall arm
    # reaching 16/16 instead, closing the reported two-pixel horizontal gap.
    states = []
    for values in itertools.product(('false', 'true'), repeat=5):
        target = dict(zip((*FACING, 'up'), values))
        wall_shape = 'tall' if target['up'] == 'true' else 'low'
        mask = {direction: wall_shape if target[direction] == 'true' else 'none' for direction in FACING}
        models = apps('mossy_cobblestone_wall', {**mask, 'up': 'true', 'facing': 'north'})
        sources = []
        for turn, facing in enumerate(FACING):
            old = {FACING[i]: mask[FACING[(i + turn) % 4]] for i in range(4)}
            sources.append({'id': 'mossy_cobblestone_wall', 'match': {**old, 'up': 'true', 'facing': facing}})
        # The isolated post has identical low/tall artwork. Do not create two
        # contradictory inverse rules for that same source state.
        if all(target[d] == 'false' for d in FACING) and target['up'] == 'true':
            sources = []
        states.append({'properties': target, 'apps': models, 'sources': sources})
    objects.append({'id': 'o_stone_railing', 'name': 'Соединяемое каменное ограждение',
                    'semantic': 'fence', 'behavior': 'connected', 'connection_family': 'stone_railing', 'states': states})

    target = ROOT / 'docs/logical-families-v3.json'
    target.write_text(json.dumps({'schemaVersion': 1, 'objects': objects}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('Curated logical families:', len(objects), 'states:', sum(len(o['states']) for o in objects))


if __name__ == '__main__':
    main()
