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
        if ident not in ('iron_bars', 'cut_copper_stairs') and any(len(group) != 1 for group in groups):
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
                match = {'axis': 'z' if turn == 0 else 'x'}
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
    clean_shutters = {}
    for stem in ('spruce_trapdoor_bottom', 'spruce_trapdoor_open'):
        clean = copy.deepcopy(model('bloodborne_blocks:block/' + stem))
        clean['elements'] = clean['elements'][:4]
        # The open source's back face is masonry because it was embedded in a
        # wall. A standalone frame must show the same opening on its reverse.
        # Correct only that proven UV mismatch; do not repaint the atlas.
        if stem == 'spruce_trapdoor_open':
            reverse = copy.deepcopy(clean['elements'][3]['faces']['north'])
            u0, v0, u1, v1 = reverse['uv']
            reverse['uv'] = [u1, v0, u0, v1]
            clean['elements'][3]['faces']['south'] = reverse
        path = ASSETS / 'models/block/logical_source' / (stem + '_standalone.json')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(clean, separators=(',', ':')) + '\n', encoding='utf-8')
        clean_shutters[stem] = 'bloodborne_blocks:block/logical_source/' + path.stem
    states = []
    for facing, opened, embedded in itertools.product(FACING, ('false', 'true'), ('true', 'false')):
        source = {'assembled': 'false', 'half': 'bottom', 'facing': facing, 'open': opened}
        models = apps('spruce_trapdoor', source)
        if any(a['model'].split('/')[-1] not in ('spruce_trapdoor_bottom', 'spruce_trapdoor_open') for a in models):
            continue
        # Elements 4/5 are the rectangular masonry carrier, not the shutters.
        # Preserve that artwork in the compatibility state of converted maps;
        # newly placed items contain the shutters, frame and sill (0..3) only.
        if embedded == 'false':
            models = [{**a, 'model': clean_shutters[a['model'].split('/')[-1]]} for a in models]
        states.append({'properties': {'facing': facing, 'open': opened, 'embedded': embedded}, 'apps': models,
                       'sources': [{'id': 'spruce_trapdoor', 'match': source}] if embedded == 'true' else []})
    if len(states) == 16:
        objects.append({'id': 'o_shuttered_window', 'name': 'Арочное окно со ставнями',
                        'semantic': 'window', 'behavior': 'shutter', 'states': states,
                        'default': {'facing': 'north', 'open': 'false', 'embedded': 'true'},
                        'placement_properties': {'embedded': 'false'}})

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

    # The actual two-storey balustrade carrier is warped_trapdoor/open, not the
    # deepslate/smooth-red stair carriers (those contain arched window artwork).
    # Every arm is a UV-preserving half of the authored straight section. The
    # footprint stays within one horizontal cell, so adjacent roots never need
    # to compete for a helper cell. Vertical helpers belong to this one item.
    states = []
    for values, pose in itertools.product(itertools.product(('false', 'true'), repeat=4), FACING):
        target = {**dict(zip(FACING, values)), 'facing': pose}
        active = [direction for direction in FACING if target[direction] == 'true']
        pose_yaw = FACING.index(pose) * 90
        pose_axis = {'east', 'west'} if pose_yaw % 180 == 0 else {'north', 'south'}
        # A lone element displays its full original straight section.
        if not active or set(active) == pose_axis:
            models = [{'model': 'bloodborne_blocks:block/warped_trapdoor_open', 'y': pose_yaw}]
        else:
            models = [{'model': 'bloodborne_blocks:block/warped_trapdoor_open', 'clip_x': [.25, .75], 'y': pose_yaw}]
            models += [{'model': 'bloodborne_blocks:block/warped_trapdoor_open',
                        'clip_x': [.75, 1.0], 'y': {'east': 0, 'south': 90, 'west': 180, 'north': 270}[direction]}
                       for direction in active]
        sources = []
        # Only straight masks are exact reconstructions of old artwork.
        if set(active) in ({'east', 'west'}, {'north', 'south'}):
            for facing in FACING:
                for half in ('bottom', 'top'):
                    source = {'assembled': 'false', 'facing': facing, 'half': half, 'open': 'true'}
                    old = apps('warped_trapdoor', source)
                    if len(old) != 1 or old[0]['model'] != 'bloodborne_blocks:block/warped_trapdoor_open':
                        continue
                    old_axis = {'east', 'west'} if old[0].get('y', 0) % 180 == 0 else {'north', 'south'}
                    if set(active) == old_axis and old[0].get('y', 0) % 360 == pose_yaw:
                        sources.append({'id': 'warped_trapdoor', 'match': source})
        states.append({'properties': target, 'apps': models, 'sources': sources})
    objects.append({'id': 'o_ornate_balustrade', 'name': 'Каменная балюстрада с основанием',
                    'semantic': 'fence', 'behavior': 'connected', 'connection_family': 'ornate_balustrade',
                    'states': states, 'evidence': 'Whole authored warped_trapdoor_open, horizontal [0,16] footprint; corners derive from half sections.'})

    # The two sculpted post/rail families already encode directional arms in
    # authored multipart models. Their middle post is repeated per arm: keep it
    # once, then select only the arm from each connected direction.
    for carrier, ident, label in (
            ('red_nether_brick_wall', 'o_carved_balustrade', 'Резное каменное ограждение'),
            ('sandstone_wall', 'o_stepped_balustrade', 'Ступенчатое каменное ограждение')):
        states = []
        for values in itertools.product(('false', 'true'), repeat=4):
            target = dict(zip(FACING, values))
            models = [{'model': 'bloodborne_blocks:block/' + carrier + '_side_tall_north', 'elements': [0, 1, 2, 3]}]
            for direction in FACING:
                if target[direction] == 'true':
                    models.append({'model': 'bloodborne_blocks:block/' + carrier + '_side_tall_' + direction, 'elements': [4]})
            sources = []
            if any(value == 'true' for value in values):
                for turn, facing in enumerate(FACING):
                    old = {FACING[i]: 'tall' if target[FACING[(i + turn) % 4]] == 'true' else 'none' for i in range(4)}
                    for up in ('false', 'true'):
                        sources.append({'id': carrier, 'match': {**old, 'up': up, 'facing': facing}})
            states.append({'properties': target, 'apps': models, 'sources': sources})
        objects.append({'id': ident, 'name': label, 'semantic': 'fence', 'behavior': 'connected',
                        'connection_family': ident[2:], 'states': states})

    # This vanilla trapdoor carrier is a complete crowned post, not a door.
    states = []
    for turn, facing in enumerate(FACING):
        sources = []
        for raw in definitions['dark_oak_trapdoor']['states']:
            source = dict(piece.split('=', 1) for piece in raw.split(','))
            if source.get('assembled') != 'false' or source.get('waterlogged') == 'true':
                continue
            old = apps('dark_oak_trapdoor', source)
            if len(old) == 1 and old[0]['model'] == 'bloodborne_blocks:block/dark_oak_trapdoor_open' and old[0].get('y', 0) % 360 == turn * 90:
                sources.append({'id': 'dark_oak_trapdoor', 'match': source})
        states.append({'properties': {'facing': facing},
                       'apps': [{'model': 'bloodborne_blocks:block/dark_oak_trapdoor_open', 'y': turn * 90}],
                       'sources': sources})
    objects.append({'id': 'o_crowned_stone_post', 'name': 'Каменный столб с резным навершием',
                    'semantic': 'ornament', 'behavior': 'static', 'states': states,
                    'ownership_bounds': [[0, -1, 0], [1, 2, 1]],
                    'evidence': 'Complete authored dark_oak_trapdoor_open; narrow crown planes overhang the solid post without occupying neighboring cells.'})

    # A second balustrade is carried by the *top* cut-copper stair variants.
    # Their six weighted alternatives only vary surface artwork. The existing
    # v2 conversion selected alternative zero; preserve that canonical choice.
    straight = 'bloodborne_blocks:block/stairs/cut_copper_stairs_top_0'
    corner = 'bloodborne_blocks:block/stairs/cut_copper_stairs_inner_outer'
    states = []
    for values, pose in itertools.product(itertools.product(('false', 'true'), repeat=4), FACING):
        target_state = {**dict(zip(FACING, values)), 'facing': pose}
        active = {direction for direction in FACING if target_state[direction] == 'true'}
        yaw = FACING.index(pose) * 90
        axis = {'east', 'west'} if yaw % 180 == 0 else {'north', 'south'}
        corner_axis = {FACING[(FACING.index(d) + yaw // 90) % 4] for d in ('south', 'east')}
        if not active or active == axis:
            models = [{'model': straight, 'y': yaw}]
        elif active == corner_axis:
            models = [{'model': corner, 'y': yaw}]
        else:
            models = [{'model': straight, 'clip_x': [.25, .75], 'y': yaw}]
            models += [{'model': straight, 'clip_x': [.75, 1],
                        'y': {'east': 0, 'south': 90, 'west': 180, 'north': 270}[d]} for d in sorted(active)]
        sources = []
        for raw in definitions['cut_copper_stairs']['states']:
            source = dict(piece.split('=', 1) for piece in raw.split(','))
            if source.get('assembled') != 'false' or source.get('half') != 'top' or source.get('waterlogged') == 'true':
                continue
            old = apps('cut_copper_stairs', source)
            if len(old) != 1 or old[0].get('y', 0) % 360 != yaw:
                continue
            if (old[0]['model'] == straight and active == axis) or (old[0]['model'] == corner and active == corner_axis):
                sources.append({'id': 'cut_copper_stairs', 'match': source})
        states.append({'properties': target_state, 'apps': models, 'sources': sources})
    objects.append({'id': 'o_high_balustrade', 'name': 'Высокая каменная балюстрада',
                    'semantic': 'fence', 'behavior': 'connected', 'connection_family': 'high_balustrade', 'states': states})

    # Rail and rail_corner are the stone walkway/curb artwork in screenshot 9.
    # Direction masks choose original straight/corner art, with no corner item.
    # T/cross/end states reuse the same stone face and perimeter strips.
    base = copy.deepcopy(model('bloodborne_blocks:block/rail'))
    base.pop('parent', None)
    base['elements'] = [copy.deepcopy(base['elements'][0])]
    base['elements'][0]['from'][0] = 0
    base['elements'][0]['to'][0] = 16
    base['elements'][0]['faces']['up']['uv'] = [10.75, 12, 12.75, 14]
    base['elements'][0]['faces']['down']['uv'] = [10.75, 12, 12.75, 14]
    base_path = ASSETS / 'models/block/logical_source/curb_base.json'
    base_path.parent.mkdir(parents=True, exist_ok=True)
    base_path.write_text(json.dumps(base, separators=(',', ':')) + '\n', encoding='utf-8')
    states = []
    for values in itertools.product(('false', 'true'), repeat=4):
        target_state = dict(zip(FACING, values))
        active = {direction for direction in FACING if target_state[direction] == 'true'}
        models = None
        if not active or active == {'north', 'south'}:
            models = [{'model': 'bloodborne_blocks:block/rail'}]
        elif active == {'east', 'west'}:
            models = [{'model': 'bloodborne_blocks:block/rail', 'y': 90}]
        elif len(active) == 2:
            for turn in range(4):
                if active == {FACING[(FACING.index(d) + turn) % 4] for d in ('south', 'east')}:
                    models = [{'model': 'bloodborne_blocks:block/rail_corner', 'y': turn * 90}]
                    break
        if models is None:
            models = [{'model': 'bloodborne_blocks:block/logical_source/curb_base'}]
            models += [{'model': 'bloodborne_blocks:block/rail', 'elements': [2],
                        'y': {'east': 0, 'south': 90, 'west': 180, 'north': 270}[d]} for d in FACING if d not in active]
        sources = []
        for raw in definitions['rail']['states']:
            source = dict(piece.split('=', 1) for piece in raw.split(','))
            if source.get('assembled') != 'false':
                continue
            old = apps('rail', source)
            if len(old) != 1:
                continue
            stem = old[0]['model'].split('/')[-1]
            if stem not in ('rail', 'rail_corner'):
                continue
            old_dirs = ('north', 'south') if stem == 'rail' else ('south', 'east')
            turn = old[0].get('y', 0) // 90
            if active == {FACING[(FACING.index(d) + turn) % 4] for d in old_dirs}:
                sources.append({'id': 'rail', 'match': source})
        states.append({'properties': target_state, 'apps': models, 'sources': sources})
    objects.append({'id': 'o_stone_curb', 'name': 'Соединяемая каменная дорожка с бордюром',
                    'semantic': 'floor', 'behavior': 'connected', 'connection_family': 'stone_curb',
                    'ownership_bounds': [[0, 0, 0], [1, 1, 1]], 'states': states,
                    'evidence': 'Authored rail/rail_corner stone geometry; one-pixel decorative overhangs are not neighboring blocks.'})

    # These carriers are windows, not balustrades. Each lower sill ends where
    # the upper three-block panel begins at the proven authored +2 offset.
    # Exact-member rules cannot consume a nearby unrelated window section.
    for carrier, ident, label in (
            ('deepslate_tile_stairs', 'o_gothic_sash_window', 'Высокое готическое окно с подоконником'),
            ('smooth_red_sandstone_stairs', 'o_gothic_framed_window', 'Высокое готическое окно в каменной раме')):
        states = []
        for facing in FACING:
            low = {'facing': facing, 'half': 'bottom', 'shape': 'straight'}
            if 'assembled' in definitions[carrier]['properties']:
                low['assembled'] = 'false'
            high = {**low, 'half': 'top'}
            states.append({'properties': {'facing': facing},
                           'apps': apps(carrier, low) + [{**a, 'offset': [0, 2, 0]} for a in apps(carrier, high)],
                           'sources': [{'id': carrier, 'match': low,
                                        'members': [{'id': carrier, 'match': high, 'offset': [0, 2, 0]}]}]})
        objects.append({'id': ident, 'name': label, 'semantic': 'window', 'behavior': 'static', 'states': states,
                        'evidence': 'Sill y=0..16 and upper panel y=-16..32 join exactly at member offset +2. Whole source artwork, not native stair corners.'})

    # 459 exact source-world coal/raw-iron/raw-copper triples. Their authored
    # heights are each [-1,2], so +3/+6 gives a continuous nine-block spire.
    # Optional lower ancient_debris pedestals are deliberately not consumed.
    states = []
    for facing in FACING:
        source = {'assembled': 'false', 'facing': facing}
        states.append({'properties': {'facing': facing},
                       'apps': apps('coal_block', source) +
                               [{**a, 'offset': [0, 3, 0]} for a in apps('raw_iron_block', source)] +
                               [{**a, 'offset': [0, 6, 0]} for a in apps('raw_copper_block', source)],
                       'sources': [{'id': 'coal_block', 'match': source, 'members': [
                           {'id': 'raw_iron_block', 'match': source, 'offset': [0, 3, 0]},
                           {'id': 'raw_copper_block', 'match': source, 'offset': [0, 6, 0]}]}],
                       'supersedes_targets': ['o_toothed_stone_rib', 'o_raw_iron_block', 'o_raw_copper_block']})
    objects.append({'id': 'o_toothed_spire', 'name': 'Цельный каменный шпиль с зубцами',
                    'semantic': 'ornament', 'behavior': 'static', 'states': states,
                    'evidence': '459 exact source-world triples +3/+6; optional bottom pedestals excluded.'})

    # Keep the existing statue registry and all its unlit artwork. A separate
    # inventory lantern attaches in its authored hand socket. Sharing the
    # object's owned cells avoids two overlapping masters fighting for cells.
    states = []
    for turn, facing in enumerate(FACING):
        for mounted in ('false', 'true'):
            models = [{'model': 'bloodborne_blocks:block/hold/statue', 'y': turn * 90}]
            if mounted == 'true':
                models.append({'model': 'bloodborne_blocks:block/hold/lanterns', 'y': turn * 90, 'offset': [0, 2, 0]})
            sources = []
            for old_facing in FACING:
                source = {'assembled': 'false', 'facing': old_facing, 'half': 'bottom', 'shape': 'straight'}
                old = apps('weathered_cut_copper_stairs', source)
                if len(old) == 1 and old[0]['model'] == 'bloodborne_blocks:block/hold/statue' and old[0].get('y', 0) % 360 == turn * 90:
                    entry = {'id': 'weathered_cut_copper_stairs', 'match': source}
                    if mounted == 'true':
                        entry['members'] = [{'id': 'weathered_cut_copper_stairs', 'match': {**source, 'half': 'top'}, 'offset': [0, 2, 0]}]
                    sources.append(entry)
            states.append({'properties': {'facing': facing, 'lantern': mounted}, 'apps': models, 'sources': sources,
                           **({'supersedes_targets': ['o_statue', 'o_lanterns']} if mounted == 'true' else {})})
    objects.append({'id': 'o_statue', 'name': 'Каменная статуя с креплением для фонаря',
                    'semantic': 'statue', 'behavior': 'static', 'states': states,
                    'default': {'facing': 'north', 'lantern': 'false'}, 'placement_properties': {'lantern': 'false'},
                    'attachment_item': 'o_lanterns',
                    'evidence': '50 exact same-facing weathered-stair bottom/top pairs at +2; one mixed-facing pair is not guessed.'})

    target = ROOT / 'docs/logical-families-v3.json'
    target.write_text(json.dumps({'schemaVersion': 1, 'objects': objects}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('Curated logical families:', len(objects), 'states:', sum(len(o['states']) for o in objects))


if __name__ == '__main__':
    main()
