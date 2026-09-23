"""Minecraft 1.20.1 positional model selection; no world I/O or RNG guessing.

Verified against cached Yarn build.10 bytecode: MathHelper.hashCode,
CheckedRandom/BaseRandom, WeightedBakedModel and MultipartBakedModel.
The x product is int32 before widening; both nextLong halves are signed.
"""
from __future__ import annotations

import copy
import functools
import json


def signed(value, bits):
    value &= (1 << bits) - 1
    return value - (1 << bits) if value & (1 << (bits - 1)) else value


def position_seed(position):
    x, y, z = position
    value = signed(x * 3129871, 32) ^ signed(z * 116129781, 64) ^ y
    return signed(value * value * 42317861 + value * 11, 64) >> 16


class JavaRandom:
    def __init__(self, seed):
        self.seed = (seed ^ 25214903917) & ((1 << 48) - 1)

    def next(self, bits):
        self.seed = (self.seed * 25214903917 + 11) & ((1 << 48) - 1)
        return signed(self.seed >> (48 - bits), 32)

    def next_long(self):
        return signed((self.next(32) << 32) + self.next(32), 64)


def weighted_index(weights, position, multipart=False):
    if not weights or any(type(n) is not int or n <= 0 for n in weights):
        raise ValueError('Invalid positive model weights')
    if len(weights) == 1:
        return 0
    random = JavaRandom(position_seed(position))
    if multipart:
        random = JavaRandom(random.next_long())
    value = signed(random.next_long(), 32)
    # Math.abs(Integer.MIN_VALUE) remains negative; Java's remainder is negative
    # and Weighting.getAt selects its first entry (verified bytecode).
    if value == -(1 << 31):
        return 0
    point = abs(value) % sum(weights)
    for index, weight in enumerate(weights):
        point -= weight
        if point < 0:
            return index
    raise AssertionError('Unreachable weight selection')


@functools.lru_cache(None)
def is_multipart(source_id):
    from source_assembly_visuals import resource
    value = json.loads(resource(source_id, 'blockstates', 'json'))
    return 'multipart' in value


def group_weights(group):
    if any(len(option) != 1 for option in group):
        raise ValueError('Each vanilla weighted choice must contain exactly one application')
    return [option[0].get('weight', 1) for option in group]


def resolve_choices(component, worldpos):
    """Resolve every independent group at the ORIGINAL source block position."""
    groups = component.get('model_choices', [])
    if not groups:
        return copy.deepcopy(component.get('apps', component.get('source_apps', [])))
    source_id = component.get('id') or component['source']['id']
    multipart = is_multipart(source_id)
    result = []
    for group in groups:
        selected = weighted_index(group_weights(group), worldpos, multipart)
        if selected < 0:
            raise ValueError('Minecraft weighted model selected no model at ' + str(worldpos))
        result.extend(copy.deepcopy(group[selected]))
    return result


def guards_for(component, expected_apps, offset):
    """Portable pure-numeric RNG preconditions for a compiled migration rule."""
    groups = component.get('model_choices', [])
    if not any(len(group) > 1 for group in groups):
        return []
    multipart = is_multipart(component.get('id') or component['source']['id'])
    def token(app):
        return json.dumps({k: v for k, v in app.items() if k not in ('offset', 'weight')}, sort_keys=True)
    chosen = {token(app) for app in expected_apps}
    result = []
    for group in groups:
        if len(group) < 2:
            continue
        matches = [i for i, option in enumerate(group) if all(token(app) in chosen for app in option)]
        if not matches:
            raise ValueError('Expected visual is not a source alternative')
        result.append({'offset': list(offset), 'weights': group_weights(group),
                       'indices': matches, 'multipart': multipart})
    return result


def guards_match(guards, origin):
    for guard in guards:
        position = tuple(origin[i] + guard['offset'][i] for i in range(3))
        if weighted_index(guard['weights'], position, guard.get('multipart', False)) not in guard['indices']:
            return False
    return True
