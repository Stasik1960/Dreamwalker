"""Clip actual exported baked quads offline and intern exact pose-cell fragments.

Runtime never loads SourcePose or performs this clipping. Boundary planes belong
to the inward cell: +normal at max=1, -normal at min=0. EPS only decides boundary
membership; intersections are projected exactly onto the same integer plane.
"""
from pathlib import Path
from functools import lru_cache
from collections import defaultdict
import argparse, hashlib, inspect, itertools, json, math, os, sqlite3, struct, zlib
from array import array

EPS = 1e-6
AREA_EPS = 1e-13
CLIPPING_VERSION = 1


class TextureOpacity:
    """Conclusive sampled-UV alpha proof using unchanged PNG bytes.

    The sampled rectangle expands to whole 16-pixel mip footprints, including
    neighbouring edge texels, so promotion remains safe through mip level four.
    Unsupported/animated images simply retain their authored cutout layer.
    """
    def __init__(self, resources):
        self.resources = Path(resources)
        self.counts = defaultdict(int)

    @lru_cache(maxsize=8)
    def image(self, sprite):
        namespace, name = sprite.split(':', 1)
        path = self.resources/'assets'/namespace/'textures'/(name+'.png')
        if not path.is_file():
            return None
        metadata = path.with_name(path.name+'.mcmeta')
        if metadata.is_file() and 'animation' in json.loads(metadata.read_text(encoding='utf-8-sig')):
            return None
        raw = path.read_bytes()
        if raw[:8] != b'\x89PNG\r\n\x1a\n':
            return None
        offset, payload, transparency = 8, bytearray(), None
        width = height = depth = color = interlace = None
        while offset+12 <= len(raw):
            length = struct.unpack('>I', raw[offset:offset+4])[0]
            kind, data = raw[offset+4:offset+8], raw[offset+8:offset+8+length]
            if kind == b'IHDR':
                width, height, depth, color, compression, filtering, interlace = struct.unpack('>IIBBBBB', data)
            elif kind == b'IDAT':
                payload.extend(data)
            elif kind == b'tRNS':
                transparency = data
            offset += length+12
        channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}.get(color)
        if depth != 8 or interlace or not channels or not width or not height:
            return None
        pixels = zlib.decompress(payload)
        stride = width*channels
        previous = bytearray(stride)
        prefix = array('I', [0])*((width+1)*(height+1))
        offset = 0
        all_opaque = True
        for y in range(height):
            filtering = pixels[offset]
            current = bytearray(pixels[offset+1:offset+1+stride])
            offset += stride+1
            for i in range(stride):
                left = current[i-channels] if i >= channels else 0
                up = previous[i]
                upper_left = previous[i-channels] if i >= channels else 0
                if filtering == 1:
                    prediction = left
                elif filtering == 2:
                    prediction = up
                elif filtering == 3:
                    prediction = (left+up)//2
                elif filtering == 4:
                    p = left+up-upper_left
                    distances = (abs(p-left), abs(p-up), abs(p-upper_left))
                    prediction = (left, up, upper_left)[distances.index(min(distances))]
                elif filtering == 0:
                    prediction = 0
                else:
                    return None
                current[i] = (current[i]+prediction) & 255
            row_transparent = 0
            for x in range(width):
                start = x*channels
                if color in (4, 6):
                    opaque = current[start+channels-1] == 255
                elif color == 3:
                    palette_index = current[start]
                    opaque = transparency is None or palette_index >= len(transparency) or transparency[palette_index] == 255
                elif transparency is None:
                    opaque = True
                elif color == 0:
                    opaque = current[start] != struct.unpack('>H', transparency)[0]
                else:
                    opaque = tuple(current[start:start+3]) != struct.unpack('>HHH', transparency)
                if not opaque:
                    row_transparent += 1
                    all_opaque = False
                prefix[(y+1)*(width+1)+x+1] = prefix[y*(width+1)+x+1]+row_transparent
            previous = current
        return width, height, None if all_opaque else prefix

    def opaque_quad(self, quad):
        if any(((int(v[5]) >> 24) & 255) != 255 for v in quad['vertices']):
            return False
        image = self.image(quad['material']['sprite'])
        if image is None:
            return False
        width, height, prefix = image
        low_u, high_u = min(v[3] for v in quad['vertices'])/16, max(v[3] for v in quad['vertices'])/16
        low_v, high_v = min(v[4] for v in quad['vertices'])/16, max(v[4] for v in quad['vertices'])/16
        if min(low_u, low_v) < 0 or max(high_u, high_v) > 1:
            return False
        if prefix is None:
            return True
        x0 = max(0, (math.floor(low_u*width)//16)*16)
        y0 = max(0, (math.floor(low_v*height)//16)*16)
        x1 = min(width, (math.floor(high_u*width)//16+1)*16)
        y1 = min(height, (math.floor(high_v*height)//16+1)*16)
        pitch = width+1
        return prefix[y1*pitch+x1]-prefix[y1*pitch+x0]-prefix[y0*pitch+x1]+prefix[y0*pitch+x0] == 0


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False, allow_nan=False)


def digest(value):
    return hashlib.sha256(canonical(value).encode('utf-8')).hexdigest()


def read_lines(path):
    with Path(path).open(encoding='utf-8-sig') as stream:
        for line in stream:
            if line.strip():
                yield json.loads(line)


def f32(value):
    value = struct.unpack('>f', struct.pack('>f', value))[0]
    return 0.0 if value == 0 else value


def canonical_boxes(boxes):
    # Double precision is retained. Only signed zero and exact duplicate boxes
    # are normalized; no voxelization, hull or approximate full-cube physics.
    return [list(row) for row in sorted({tuple(0.0 if float(v) == 0 else float(v) for v in b) for b in boxes if all(float(b[i+3]) > float(b[i]) for i in range(3))})]


def area(vertices):
    total = 0.0
    if len(vertices) < 3:
        return total
    a = vertices[0]
    for index in range(1, len(vertices)-1):
        b, c = vertices[index:index+2]
        u, v = [b[i]-a[i] for i in range(3)], [c[i]-a[i] for i in range(3)]
        cross = (u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0])
        total += math.sqrt(sum(n*n for n in cross))*.5
    return total


def mix_color(a, b, fraction):
    result = 0
    for shift in range(0, 32, 8):
        left, right = (int(a) >> shift) & 255, (int(b) >> shift) & 255
        result |= max(0, min(255, round(left+(right-left)*fraction))) << shift
    return result-(1 << 32) if result >= (1 << 31) else result


def interpolate(a, b, fraction):
    result = [a[i]+(b[i]-a[i])*fraction for i in range(5)]
    result += [mix_color(a[5], b[5], fraction), int(a[6]), int(a[7])]
    return result


def clip_plane(vertices, axis, plane, greater):
    if not vertices:
        return []
    output = []
    previous = vertices[-1]
    previous_in = previous[axis] >= plane-EPS if greater else previous[axis] <= plane+EPS
    for vertex in vertices:
        inside = vertex[axis] >= plane-EPS if greater else vertex[axis] <= plane+EPS
        if inside != previous_in:
            denominator = vertex[axis]-previous[axis]
            if denominator == 0:
                raise ValueError('Inconsistent coplanar classification')
            point = interpolate(previous, vertex, max(0.0, min(1.0, (plane-previous[axis])/denominator)))
            point[axis] = plane
            output.append(point)
        if inside:
            point = list(vertex)
            if abs(point[axis]-plane) <= EPS:
                point[axis] = plane
            output.append(point)
        previous, previous_in = vertex, inside
    compact = []
    for point in output:
        if not compact or any(abs(point[i]-compact[-1][i]) > 1e-12 for i in range(5)):
            compact.append(point)
    if len(compact) > 1 and all(abs(compact[0][i]-compact[-1][i]) <= 1e-12 for i in range(5)):
        compact.pop()
    return compact


def quad_cells(quad):
    vertices, normal = quad['vertices'], quad['normal']
    spans = []
    for axis in range(3):
        low, high = min(v[axis] for v in vertices), max(v[axis] for v in vertices)
        plane = round((low+high)*.5)
        if high-low < EPS and abs(low-plane) <= EPS and abs(high-plane) <= EPS:
            # The true geometric normal is exported, not BakedQuad.face's
            # nearest-axis lighting label. Back-to-back sprite planes survive.
            coordinate = plane-1 if normal[axis] > 0 else plane
            spans.append(range(coordinate, coordinate+1))
        else:
            start, end = math.floor(low+EPS), math.ceil(high-EPS)
            spans.append(range(start, max(start+1, end)))
    yield from itertools.product(*spans)


def clipped_quad(quad, cell):
    polygon = [[v[i]-cell[i] if i < 3 else v[i] for i in range(8)] for v in quad['vertices']]
    for axis in range(3):
        polygon = clip_plane(polygon, axis, 0, True)
        polygon = clip_plane(polygon, axis, 1, False)
    if len(polygon) < 3 or area(polygon) < AREA_EPS:
        return []
    polygons = [polygon] if len(polygon) == 4 else [[polygon[0], polygon[i], polygon[i+1], polygon[i+1]] for i in range(1, len(polygon)-1)]
    result = []
    for vertices in polygons:
        if area(vertices) < AREA_EPS:
            continue
        vertices = [[f32(v[i]) if i < 5 else int(v[i]) for i in range(8)] for v in vertices]
        # Cyclic vertex rotation normalizes equivalent quads without changing
        # winding or UV association. Reversing winding would destroy backfaces.
        rotations = [vertices[i:]+vertices[:i] for i in range(4)]
        vertices = min(rotations, key=canonical)
        row = {key: value for key, value in quad.items() if key not in ('vertices', 'normal')}
        row['material'] = dict(row['material'])
        if row['material'].get('tintSource'):
            original_offset = row['material'].get('tintOffset', [0, 0, 0])
            row['material']['tintOffset'] = [original_offset[i]-cell[i] for i in range(3)]
        row['vertices'] = vertices
        row['cull'] = boundary_face(vertices, quad['normal']) if quad.get('opaque') else -1
        result.append(row)
    return result


def boundary_face(vertices, normal):
    # Direction ordinal: DOWN, UP, NORTH, SOUTH, WEST, EAST.
    for axis, negative, positive in ((0, 4, 5), (1, 0, 1), (2, 2, 3)):
        if normal[axis] < -.9999 and all(abs(v[axis]) <= EPS for v in vertices):
            return negative
        if normal[axis] > .9999 and all(abs(v[axis]-1) <= EPS for v in vertices):
            return positive
    return -1


def unique_quads(quads):
    # Opaque/cutout duplicates do not change coverage. Translucent multiplicity
    # can change the authored appearance, so retain repeated alpha surfaces.
    opaque = set()
    transparent = []
    for quad in quads:
        key = canonical(quad)
        if quad.get('material', {}).get('layer') == 'translucent':
            transparent.append(key)
        else:
            opaque.add(key)
    return [json.loads(key) for key in sorted([*opaque, *transparent])]


def clip_pose(source, opacity=None):
    cells = {tuple(c['cell']): {**c, 'quads': []} for c in source['cells']}
    before = after = 0.0
    source_quads = fragment_quads = 0
    for original_quad in source['quads']:
        quad = {**original_quad, 'material': dict(original_quad['material'])}
        # Packed alpha belongs to the baked material as well as the PNG. A
        # SOLID assignment needs both proofs; preserve fractional alpha.
        alphas = {(int(v[5]) >> 24) & 255 for v in quad['vertices']}
        if quad['material']['layer'] == 'solid' and alphas != {255}:
            quad['material']['layer'] = 'translucent' if any(0 < a < 255 for a in alphas) else 'cutout'
            quad['opaque'] = False
        if opacity and quad['material']['layer'] in ('cutout', 'cutout_mipped') and opacity.opaque_quad(quad):
            quad['material']['layer'] = 'solid'
            quad['opaque'] = True
            opacity.counts['promotedOpaqueUvQuads'] += 1
        before += area(quad['vertices'])
        source_quads += 1
        for coordinate in quad_cells(quad):
            clipped = clipped_quad(quad, coordinate)
            if not clipped:
                continue
            cell = cells.setdefault(coordinate, {'cell': list(coordinate), 'collision': [], 'outline': [], 'climbing': [], 'optics': {}, 'fluid': None, 'quads': []})
            cell['quads'].extend(clipped)
            fragment_quads += len(clipped)
            after += sum(area(q['vertices']) for q in clipped)
    error = abs(before-after)/max(1e-12, before)
    # Float32 positions at large authored spans introduce tiny area error; no
    # lost face is accepted. This is stricter than the previous adapter gate.
    if error > 2e-5:
        raise ValueError(f"Clipping changed visible surface area for {source['key']}: {before} -> {after} ({error})")
    for coordinate, cell in sorted(cells.items()):
        cell['cell'] = list(coordinate)
        for field in ('collision', 'outline', 'climbing'):
            cell[field] = canonical_boxes(cell.get(field, []))
        cell['quads'] = unique_quads(cell['quads'])
    return list(cells.values()), {'key': source['key'], 'sourceArea': before, 'fragmentArea': after, 'relativeError': error, 'sourceQuads': source_quads, 'fragmentQuads': fragment_quads, 'cells': len(cells)}


def state_key(state):
    return canonical({'registryId': state.get('registryId', state.get('id')), 'properties': {k: str(v) for k, v in state.get('properties', {}).items()}})


def clipping_fingerprint():
    functions = (TextureOpacity, f32, canonical_boxes, area, mix_color, interpolate, clip_plane, quad_cells, clipped_quad, boundary_face, unique_quads, clip_pose)
    return hashlib.sha256(('version='+str(CLIPPING_VERSION)+'\n'+'\n'.join(inspect.getsource(function) for function in functions)).encode('utf-8')).hexdigest()


def merge_native_rows(database, native):
    counts = defaultdict(int)
    for row in read_lines(native):
        state = row.pop('state')
        for quad in row.get('quads', []):
            material = quad.get('material', {})
            if material.get('tintSource') and state.get('properties'):
                material['tintProperties'] = state['properties']
        row['quads'] = unique_quads(row.get('quads', []))
        for field in ('collision', 'outline', 'culling', 'climbing'):
            row[field] = canonical_boxes(row.get(field, []))
        key = state_key(state)
        previous = database.execute('SELECT data FROM native WHERE state=?', (key,)).fetchone()
        if previous:
            old = json.loads(previous[0])
            # Dedicated physics queries may enrich a previous full visual row.
            # They must never replace its mesh with the intentional empty list.
            for field in ('collision', 'outline', 'culling', 'climbing', 'optics', 'fluid'):
                if old.get(field) != row.get(field):
                    raise ValueError('Exact native '+field+' changed: '+key)
            if old.get('nativeVisualComplete', True) and not row.get('nativeVisualComplete', True):
                row = {**row, **old}
            elif not old.get('nativeVisualComplete', True) and row.get('nativeVisualComplete', True):
                row = {**old, **row}
            else:
                for field in set(old) & set(row):
                    if old[field] != row[field]:
                        raise ValueError('Exact native export changed '+field+': '+key)
                row = {**old, **row}
            counts['existingVerified'] += 1
        else:
            counts['newStates'] += 1
        database.execute('INSERT OR REPLACE INTO native VALUES (?,?)', (key, canonical(row)))
    return dict(counts)


def prepare_database(geometry, out, native=None, resources=None):
    out = Path(out)
    out.parent.mkdir(parents=True, exist_ok=True)
    database = sqlite3.connect(out)
    database.execute('PRAGMA journal_mode=WAL')
    database.execute('PRAGMA cache_size=-32768')
    database.executescript('CREATE TABLE IF NOT EXISTS metadata (name TEXT PRIMARY KEY,data TEXT NOT NULL);CREATE TABLE IF NOT EXISTS poses (key TEXT PRIMARY KEY,source TEXT NOT NULL,proof TEXT NOT NULL);CREATE TABLE IF NOT EXISTS fragments (pose TEXT NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,digest TEXT NOT NULL,data TEXT NOT NULL,PRIMARY KEY(pose,x,y,z));CREATE TABLE IF NOT EXISTS native (state TEXT PRIMARY KEY,data TEXT NOT NULL);')
    old = database.execute("SELECT data FROM metadata WHERE name='sourceGeometrySha256'").fetchone()
    if geometry is None:
        if not old:
            raise ValueError('Native-only enrichment requires an existing proven fragment database')
        geometry_hash = json.loads(old[0])
    else:
        source_digest = hashlib.sha256()
        with Path(geometry).open('rb') as stream:
            for chunk in iter(lambda: stream.read(1 << 20), b''):
                source_digest.update(chunk)
        geometry_hash = source_digest.hexdigest()
    if old and json.loads(old[0]) != geometry_hash:
        raise ValueError('Source geometry changed; create a versioned fragment database instead of silently remapping')
    database.execute('INSERT OR REPLACE INTO metadata VALUES (?,?)', ('sourceGeometrySha256', canonical(geometry_hash)))
    algorithm = clipping_fingerprint()
    prior_algorithm = database.execute("SELECT data FROM metadata WHERE name='clippingAlgorithmSha256'").fetchone()
    if prior_algorithm and json.loads(prior_algorithm[0]) != algorithm:
        raise ValueError('Clipping implementation changed; create a versioned fragment database rather than resume stale fragments')
    database.execute('INSERT OR REPLACE INTO metadata VALUES (?,?)', ('clippingAlgorithmSha256', canonical(algorithm)))
    counts = defaultdict(int)
    opacity = TextureOpacity(resources) if resources else None
    for source in read_lines(geometry) if geometry else ():
        if database.execute('SELECT 1 FROM poses WHERE key=?', (source['key'],)).fetchone():
            counts['resumedPoses'] += 1
            continue
        cells, proof = clip_pose(source, opacity)
        with database:
            for cell in cells:
                coordinate = cell.pop('cell')
                database.execute('INSERT INTO fragments VALUES (?,?,?,?,?,?)', (source['key'], *coordinate, digest(cell), canonical(cell)))
            provenance = {k: v for k, v in source.items() if k not in ('cells', 'quads')}
            database.execute('INSERT INTO poses VALUES (?,?,?)', (source['key'], canonical(provenance), canonical(proof)))
        counts['poses'] += 1
        counts['cells'] += len(cells)
        counts['sourceQuads'] += proof['sourceQuads']
        counts['fragmentQuads'] += proof['fragmentQuads']
    if native and Path(native).is_file():
        for key, count in merge_native_rows(database, native).items():
            counts['native.'+key] += count
    database.commit()
    totals = {table: database.execute('SELECT COUNT(*) FROM '+table).fetchone()[0] for table in ('poses', 'fragments', 'native')}
    database.execute('INSERT OR REPLACE INTO metadata VALUES (?,?)', ('complete', 'true'))
    database.commit()
    database.execute('PRAGMA wal_checkpoint(TRUNCATE)')
    database.close()
    return {'status': 'PASS', 'schemaVersion': 1, 'sourceGeometrySha256': geometry_hash, 'clippingAlgorithmSha256': algorithm, 'clippingVersion': CLIPPING_VERSION, 'thisInvocation': dict(counts), 'totals': totals, 'materialProofs': dict(opacity.counts) if opacity else {}, 'boundaryEpsilon': EPS, 'areaRelativeTolerance': 2e-5, 'noWorldReadOrWrite': True}


def merge_additional_sources(geometry, out, resources):
    """Append a verified finite source request without rebuilding prior poses.

    An already known key must reproduce the same exact fragment bank. The
    initial geometry binding remains intact; each supplement has its own SHA.
    """
    database = sqlite3.connect(out)
    database.execute('PRAGMA journal_mode=WAL')
    original = database.execute("SELECT data FROM metadata WHERE name='sourceGeometrySha256'").fetchone()
    previous_algorithm = database.execute("SELECT data FROM metadata WHERE name='clippingAlgorithmSha256'").fetchone()
    algorithm = clipping_fingerprint()
    if not original or not previous_algorithm or json.loads(previous_algorithm[0]) != algorithm:
        database.close()
        raise ValueError('Supplement requires the same proven initial geometry and clipping algorithm')
    geometry_hash = hashlib.sha256(Path(geometry).read_bytes()).hexdigest()
    opacity = TextureOpacity(resources)
    counts = defaultdict(int)
    proofs = []
    with database:
        for source in read_lines(geometry):
            cells, proof = clip_pose(source, opacity)
            proofs.append(proof)
            old = database.execute('SELECT proof FROM poses WHERE key=?', (source['key'],)).fetchone()
            encoded_cells = {tuple(cell['cell']): {k: v for k, v in cell.items() if k != 'cell'} for cell in cells}
            if old:
                saved = {(x, y, z): json.loads(data) for x, y, z, data in database.execute('SELECT x,y,z,data FROM fragments WHERE pose=?', (source['key'],))}
                if saved != encoded_cells or json.loads(old[0]) != proof:
                    raise ValueError('Supplement changed an already frozen source pose: '+source['key'])
                counts['existingPosesVerified'] += 1
                continue
            for coordinate, cell in encoded_cells.items():
                database.execute('INSERT INTO fragments VALUES (?,?,?,?,?,?)', (source['key'], *coordinate, digest(cell), canonical(cell)))
            provenance = {k: v for k, v in source.items() if k not in ('cells', 'quads')}
            database.execute('INSERT INTO poses VALUES (?,?,?)', (source['key'], canonical(provenance), canonical(proof)))
            counts['newPoses'] += 1
            counts['newFragments'] += len(cells)
        database.execute('INSERT OR REPLACE INTO metadata VALUES (?,?)', ('sourceGeometrySupplement.'+geometry_hash, canonical({'sha256': geometry_hash, 'path': str(geometry), 'poses': len(proofs), 'clippingAlgorithmSha256': algorithm})))
    totals = {table: database.execute('SELECT COUNT(*) FROM '+table).fetchone()[0] for table in ('poses', 'fragments', 'native')}
    database.execute('PRAGMA wal_checkpoint(TRUNCATE)')
    database.close()
    return {'status': 'PASS', 'originalSourceGeometrySha256': json.loads(original[0]), 'supplementSha256': geometry_hash, 'clippingAlgorithmSha256': algorithm, 'thisInvocation': dict(counts), 'totals': totals, 'materialProofs': dict(opacity.counts), 'proofs': proofs}


class FragmentStore:
    """Bounded authoring lookup. The shipped runtime uses no such cache."""
    def __init__(self, path):
        self.database = sqlite3.connect('file:'+str(Path(path).absolute()).replace('\\', '/')+'?mode=ro', uri=True)

    @lru_cache(maxsize=4096)
    def fragment(self, pose, cell):
        row = self.database.execute('SELECT digest,data FROM fragments WHERE pose=? AND x=? AND y=? AND z=?', (pose, *cell)).fetchone()
        if row is None:
            raise KeyError(f'Unexported pose-cell {pose} {cell}')
        return row[0], json.loads(row[1])

    @lru_cache(maxsize=512)
    def native(self, key):
        row = self.database.execute('SELECT data FROM native WHERE state=?', (key,)).fetchone()
        if row is None:
            raise KeyError('Unexported exact native base: '+key)
        return json.loads(row[0])

    def pose_cells(self, pose):
        for row in self.database.execute('SELECT x,y,z FROM fragments WHERE pose=? ORDER BY x,y,z', (pose,)):
            yield tuple(row)

    @lru_cache(maxsize=512)
    def native_fragment(self, key, cell):
        row = self.database.execute('SELECT digest,data FROM native_fragments WHERE state=? AND x=? AND y=? AND z=?', (key, *cell)).fetchone()
        if row is None:
            raise KeyError('Unclipped exact native visual fragment: '+key+' '+str(cell))
        return row[0], json.loads(row[1])

    def native_cells(self, key):
        for row in self.database.execute('SELECT x,y,z FROM native_fragments WHERE state=? ORDER BY x,y,z', (key,)):
            yield tuple(row)

    def close(self):
        self.database.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--geometry', type=Path)
    parser.add_argument('--native', type=Path)
    parser.add_argument('--resources', type=Path, default=Path(__file__).resolve().parents[2]/'src/architecture/resources')
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    if not args.geometry and not args.native:
        parser.error('Provide --geometry, --native, or both')
    proof = prepare_database(args.geometry, args.out, args.native, args.resources)
    receipt = args.out.with_suffix('.receipt.json')
    receipt.write_text(json.dumps(proof, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(proof, ensure_ascii=False))


if __name__ == '__main__':
    main()
