"""Read-only packet size audit mirroring CompositeSpec clipping and NBT lists.

Uses exact serialized sizes for an empty building payload, one owner per cell.
Reports a conservative worst 64-cell choice, plus sorted placement batches.
Does not change production resources, runtime limits, maps or test worlds.
"""
from collections import Counter
from functools import lru_cache
from pathlib import Path
import gzip
import json
import math
import argparse

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/architecture/resources'
OUT = ROOT / 'docs/remaining-transfer'


def cardinal_cells(x0, z0, x1, z1):
    return Counter((x, z) for x in range(math.floor(x0 + 1e-8), math.ceil(x1 - 1e-8))
                   for z in range(math.floor(z0 + 1e-8), math.ceil(z1 - 1e-8)))


@lru_cache(maxsize=100000)
def horizontal_counts(x0, z0, x1, z1, rotation):
    angle = math.radians(rotation * 45)
    c, s = math.cos(angle), math.sin(angle)
    polygon = [(0.5 + c * (x - 0.5) - s * (z - 0.5),
                0.5 + s * (x - 0.5) + c * (z - 0.5))
               for x, z in ((x0, z0), (x1, z0), (x1, z1), (x0, z1))]
    low_x, low_z = min(p[0] for p in polygon), min(p[1] for p in polygon)
    high_x, high_z = max(p[0] for p in polygon), max(p[1] for p in polygon)
    if rotation % 2 == 0:
        return cardinal_cells(low_x, low_z, high_x, high_z)
    axes = [(1, 0), (0, 1)]
    axes += [(-(polygon[(i + 1) % 4][1] - polygon[i][1]),
               polygon[(i + 1) % 4][0] - polygon[i][0]) for i in range(4)]
    projections = [(ax, az, min(x * ax + z * az for x, z in polygon),
                    max(x * ax + z * az for x, z in polygon)) for ax, az in axes]

    def occupied(x, z):
        x0, z0, x1, z1 = x / 4, z / 4, (x + 1) / 4, (z + 1) / 4
        for ax, az, p0, p1 in projections:
            q0 = ax * (x0 if ax >= 0 else x1) + az * (z0 if az >= 0 else z1)
            q1 = ax * (x1 if ax >= 0 else x0) + az * (z1 if az >= 0 else z0)
            if p1 <= q0 + 1e-9 or q1 <= p0 + 1e-9:
                return False
        return True

    counts = Counter()
    first_x, last_x = math.floor(low_x * 4), math.ceil(high_x * 4)
    for z in range(math.floor(low_z * 4), math.ceil(high_z * 4)):
        start = None
        for x in range(first_x, last_x + 1):
            solid = x < last_x and occupied(x, z)
            if solid and start is None:
                start = x
            if not solid and start is not None:
                counts.update(cardinal_cells(start / 4, z / 4, x / 4, (z + 1) / 4))
                start = None
    return counts


def footprint_counts(collision, selection, rotation):
    cells = Counter({(0, 0, 0): 0})
    for box in collision + selection:
        x0, y0, z0 = [v / 16 for v in box['from']]
        x1, y1, z1 = [v / 16 for v in box['to']]
        horizontal = horizontal_counts(x0, z0, x1, z1, rotation)
        for y in range(math.floor(y0 + 1e-8), math.ceil(y1 - 1e-8)):
            for (x, z), count in horizontal.items():
                cells[x, y, z] += count
    return cells


def varint_size(value):
    return max(1, (value.bit_length() + 6) // 7)


def utf(value):
    # All NBT IDs/property values audited here are ASCII: Java modified UTF-8
    # and ordinary UTF-8 have the same byte length for these values.
    return 2 + len(value.encode('utf-8'))


def named_size(name, payload_size):
    return 1 + utf(name) + payload_size


def contribution_base(identifier, variant, rotation, opened):
    owner = 1 + named_size('uuid', 4 + 16) + named_size('id', utf(identifier)) + named_size('root', 4 + 12)
    props = {'rotation': str(rotation), 'variant': str(variant), 'open': str(opened).lower(), 'profile': 'base'}
    properties = 1 + sum(named_size(k, utf(v)) for k, v in props.items())
    root_data = 1 + named_size('id', utf(identifier)) + named_size('properties', properties) + named_size('payload', 4 + 4)
    return (1 + named_size('owner', owner) + named_size('rootData', root_data)
            + named_size('collision', 5) + named_size('selection', 5))


def budget(identifier, variant, pose, collision, selection, rotation):
    counts = footprint_counts(collision, selection, rotation)
    base = contribution_base(identifier, variant, rotation, pose == 'open')
    # PacketByteBuf identifier then varint count. Per cell: blockpos8,
    # root compound4, named owners list14, contribution plus box lists.
    sizes = {cell: 8 + 4 + named_size('owners', 5 + base) + 53 * count for cell, count in counts.items()}
    header = varint_size(len('minecraft:overworld')) + len('minecraft:overworld')
    ordered = [sizes[cell] for cell in sorted(sizes)]
    sorted_batches = [header + varint_size(len(ordered[i:i + 64])) + sum(ordered[i:i + 64])
                      for i in range(0, len(ordered), 64)]
    worst = sorted(sizes.values(), reverse=True)[:64]
    return {'cells': len(counts), 'serializedClippedBoxes': sum(counts.values()),
            'maxCellBoxes': max(counts.values()), 'maxCellBytes': max(sizes.values()),
            'sortedPlacementBatchMaxBytes': max(sorted_batches),
            'any64CellsMaxBytes': header + varint_size(len(worst)) + sum(worst),
            'wholeObjectSerializedBytes': sum(sizes.values())}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--filter-active-only', action='store_true')
    args = parser.parse_args()
    catalogue = json.loads((RES / 'bloodborne_dw/remaining-catalogue.json').read_text(encoding='utf-8'))
    active = {row['path'] for row in catalogue['objects']}
    if args.filter_active_only:
        target = OUT / 'packet-budget.json'
        result = json.loads(target.read_text(encoding='utf-8'))
        result['rows'] = [row for row in result['rows'] if row['id'].split(':')[1] in active]
        result.update(activeObjects=len(active), poses=len(result['rows']),
                      beforeWorst=max(result['rows'], key=lambda row: row['before']['any64CellsMaxBytes']),
                      afterWorst=max(result['rows'], key=lambda row: row['after']['any64CellsMaxBytes']),
                      beforeExceedingCount=sum(row['before']['any64CellsMaxBytes'] > 1048576 for row in result['rows']),
                      afterExceedingCount=sum(row['after']['any64CellsMaxBytes'] > 1048576 for row in result['rows']))
        target.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        (OUT / 'packet-budget-progress.json').write_text(json.dumps({'done': len(active), 'total': len(active), 'status': 'COMPLETE'}), encoding='utf-8')
        return
    old = {}
    with gzip.open(OUT / 'box-coalescing-before-after.jsonl.gz', 'rt', encoding='utf-8') as source:
        for line in source:
            row = json.loads(line)
            path = row['id'].split(':')[1]
            if path in active:
                old.setdefault((path, row['variant'], row['pose']), {})[row['field']] = row['before']
    rows = []
    for index, path in enumerate(sorted(active)):
        descriptor = json.loads((RES / 'bloodborne_dw/composite' / (path + '.json')).read_text(encoding='utf-8'))
        for variant, value in enumerate(descriptor['variants']):
            for pose, current in value['poses'].items():
                previous = old.get((path, variant, pose), {})
                collision, selection = previous.get('collision', current['collision']), previous.get('selection', current['selection'])
                # All eight yaw states: translated world cell raster phase can
                # differ after a cardinal rotation of a noninteger geometry.
                before, after = [], []
                for rotation in range(8):
                    after.append({'rotation': rotation, **budget(descriptor['id'], variant, pose, current['collision'], current['selection'], rotation)})
                    before.append({'rotation': rotation, **budget(descriptor['id'], variant, pose, collision, selection, rotation)})
                rows.append({'id': descriptor['id'], 'variant': variant, 'pose': pose,
                             'descriptorBoxesBefore': len(collision) + len(selection),
                             'descriptorBoxesAfter': len(current['collision']) + len(current['selection']),
                             'before': max(before, key=lambda v: v['any64CellsMaxBytes']),
                             'after': max(after, key=lambda v: v['any64CellsMaxBytes'])})
        if index % 25 == 0:
            (OUT / 'packet-budget-progress.json').write_text(json.dumps({'done': index + 1, 'total': len(active)}), encoding='utf-8')
    result = {'status': 'STATIC_RUNTIME_EQUIVALENT_AUDIT', 'activeObjects': len(active), 'poses': len(rows),
              'limitBytes': 1048576, 'allEightYawStates': True,
              'assumptions': 'One owner per cell, overworld, empty canonical building payload (4 NBT bytes), profile=base, no arbitrary later user NBT. Box list row = 53 bytes. Exact native per-box raster/clipping and NBT size; sorted placement batches plus conservative any64-cell bound.',
              'beforeWorst': max(rows, key=lambda r: r['before']['any64CellsMaxBytes']),
              'afterWorst': max(rows, key=lambda r: r['after']['any64CellsMaxBytes']),
              'beforeExceedingCount': sum(r['before']['any64CellsMaxBytes'] > 1048576 for r in rows),
              'afterExceedingCount': sum(r['after']['any64CellsMaxBytes'] > 1048576 for r in rows),
              'rows': sorted(rows, key=lambda r: r['after']['any64CellsMaxBytes'], reverse=True)}
    (OUT / 'packet-budget.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (OUT / 'packet-budget-progress.json').write_text(json.dumps({'done': len(active), 'total': len(active), 'status': 'COMPLETE'}), encoding='utf-8')


if __name__ == '__main__':
    main()
