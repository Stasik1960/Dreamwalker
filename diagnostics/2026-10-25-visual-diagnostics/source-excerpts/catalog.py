"""Intern only observed final cell contents into the native-grid v1 resource.

Python standard library only. Shared banks live in a disk authoring database;
runtime resources contain geometry/shape references and no owners or positions.
The same authoring database is append-only across pilot and full-city passes.
"""
from pathlib import Path
from collections import Counter
from functools import lru_cache
import argparse, hashlib, inspect, io, json, math, os, sqlite3, struct, sys

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
from export_cells import canonical, canonical_boxes, digest, FragmentStore, read_lines, state_key, TextureOpacity, unique_quads

EMPTY_OPTICS = {'opacity': 0, 'luminance': 0, 'ambientOcclusion': 1.0, 'transparent': True, 'sidedTransparency': True, 'opaque': False, 'opaqueFullCube': False, 'solidBlock': False, 'suffocates': False, 'blockVision': False}
SHAPE_FIELDS = ('collision', 'outline', 'culling', 'climbing')


def signed32(value):
    value = int(value) & 0xffffffff
    return value if value < 0x80000000 else value-0x100000000


def full_rectangles(rectangles):
    """Exact proof that axis-aligned face rectangles cover a unit square."""
    xs = sorted({0.0, 1.0, *[max(0.0, min(1.0, r[i])) for r in rectangles for i in (0, 2)]})
    for left, right in zip(xs, xs[1:]):
        if right-left < 1e-12:
            continue
        midpoint = (left+right)*.5
        intervals = sorted((max(0.0, r[1]), min(1.0, r[3])) for r in rectangles if r[0] <= midpoint <= r[2])
        end = 0.0
        for low, high in intervals:
            if low > end+1e-8:
                return False
            end = max(end, high)
        if end < 1-1e-8:
            return False
    return bool(rectangles)


def opaque_cube(quads):
    sides = [[] for _ in range(6)]
    for quad in quads:
        face = quad.get('cull', -1)
        if face < 0 or not quad.get('opaque', quad.get('material', {}).get('layer') == 'solid'):
            continue
        vertices = quad['vertices']
        # A rectangle is recognized from its actual four corners; triangles
        # and diagonal polygons are deliberately insufficient occlusion proof.
        axis = 1 if face < 2 else 2 if face < 4 else 0
        u, v = [i for i in range(3) if i != axis]
        xs, ys = sorted({p[u] for p in vertices}), sorted({p[v] for p in vertices})
        if len(xs) == len(ys) == 2 and {(p[u], p[v]) for p in vertices} == {(x, y) for x in xs for y in ys}:
            sides[face].append((xs[0], ys[0], xs[-1], ys[-1]))
    return all(full_rectangles(rectangles) for rectangles in sides)


def normalize_optics(row):
    result = {**EMPTY_OPTICS, **(row or {})}
    for field in ('opacity', 'luminance'):
        result[field] = int(result[field])
        if not 0 <= result[field] <= 15:
            raise ValueError('Invalid optics '+field)
    result['ambientOcclusion'] = float(result['ambientOcclusion'])
    if not 0 <= result['ambientOcclusion'] <= 1:
        raise ValueError('Invalid AO scalar')
    for field in EMPTY_OPTICS.keys()-{'opacity', 'luminance', 'ambientOcclusion'}:
        result[field] = bool(result[field])
    return result


def normalize_fluid(value):
    if value in (None, 'empty', 'minecraft:empty'):
        return None
    if isinstance(value, str):
        value = {'id': 'minecraft:'+value if ':' not in value else value, 'level': 8, 'falling': False}
    value = {'id': value['id'], 'level': int(value.get('level', 8)), 'falling': bool(value.get('falling', False))}
    if value['id'] not in ('minecraft:water', 'minecraft:lava') or not 1 <= value['level'] <= 8:
        raise ValueError('Unreviewed exact fluid: '+canonical(value))
    return value


def binary_mesh(quads, material_intern):
    out = io.BytesIO()
    out.write(struct.pack('>i', len(quads)))
    for quad in quads:
        material = dict(quad['material'])
        if material['layer'] not in ('solid', 'cutout', 'cutout_mipped', 'translucent'):
            raise ValueError('Invalid render layer')
        material_index = material_intern(material)
        flags = int(bool(quad.get('shade', True))) | (int(bool(quad.get('ao', True))) << 1) | (int(bool(quad.get('emissive', False))) << 2)
        out.write(struct.pack('>ibbiB', material_index, int(quad.get('cull', -1)), int(quad['face']), int(quad.get('tint', -1)), flags))
        if len(quad['vertices']) != 4:
            raise ValueError('A baked polygon must have four vertices (a triangle repeats its last vertex)')
        for vertex in quad['vertices']:
            if len(vertex) != 8 or not all(math.isfinite(float(n)) for n in vertex[:5]):
                raise ValueError('Invalid vertex')
            out.write(struct.pack('>fffffiii', *map(float, vertex[:5]), *[signed32(n) for n in vertex[5:]]))
    return out.getvalue()


class CatalogCompiler:
    """Streaming API: intern_cell(row)->int; intern_whole(template); finish()."""
    def __init__(self, fragments, database, output, textures=None):
        self.store = FragmentStore(fragments)
        quarantine = self.store.database.execute("SELECT data FROM metadata WHERE name='nativePhysicsQuarantine'").fetchone()
        if quarantine:
            self.store.close()
            raise ValueError('Native physics authoring data is quarantined after an incomplete/OOM driver; wait for a clean completed export confirming the same states')
        self.output = Path(output)
        self.opacity = TextureOpacity(textures or self.output.parents[2])
        self.database_path = Path(database)
        self.database_path.parent.mkdir(parents=True, exist_ok=True)
        self.db = sqlite3.connect(self.database_path)
        self.db.execute('PRAGMA journal_mode=WAL')
        self.db.execute('PRAGMA cache_size=-32768')
        self.db.executescript('CREATE TABLE IF NOT EXISTS banks(kind TEXT NOT NULL,idx INTEGER NOT NULL,digest TEXT NOT NULL,data BLOB NOT NULL,PRIMARY KEY(kind,idx),UNIQUE(kind,digest));CREATE TABLE IF NOT EXISTS ingredients(signature TEXT PRIMARY KEY,cell INTEGER NOT NULL);CREATE TABLE IF NOT EXISTS mapping(key TEXT PRIMARY KEY,cell INTEGER NOT NULL);CREATE TABLE IF NOT EXISTS metadata(name TEXT PRIMARY KEY,data TEXT NOT NULL);')
        algorithm = compiler_fingerprint()
        previous = self.db.execute("SELECT data FROM metadata WHERE name='compilerAlgorithmSha256'").fetchone()
        if (previous and json.loads(previous[0]) != algorithm) or (not previous and self.db.execute('SELECT 1 FROM banks LIMIT 1').fetchone()):
            self.store.close()
            self.db.close()
            raise ValueError('Compiled authoring rules changed after indices were frozen; migrate the persistent catalog explicitly instead of silently reusing stale signatures')
        self.db.execute('INSERT OR IGNORE INTO metadata VALUES (?,?)', ('compilerAlgorithmSha256', canonical(algorithm)))
        self.next = {kind: self.db.execute('SELECT COALESCE(MAX(idx)+1,0) FROM banks WHERE kind=?', (kind,)).fetchone()[0] for kind in ('materials', 'shapes', 'optics', 'meshes', 'cells', 'wholeObjects')}
        self.pending = 0
        self.counts = Counter()
        # An empty mesh and shape are shared, ordinary content entries.
        self.empty_shape = self.intern('shapes', [])

    def intern(self, kind, value, raw=None):
        encoded = canonical(value).encode('utf-8') if raw is None else raw
        key = hashlib.sha256(encoded).hexdigest()
        previous = self.db.execute('SELECT idx FROM banks WHERE kind=? AND digest=?', (kind, key)).fetchone()
        if previous:
            return previous[0]
        index = self.next[kind]
        self.next[kind] += 1
        self.db.execute('INSERT INTO banks VALUES (?,?,?,?)', (kind, index, key, encoded))
        return index

    def _native(self, base):
        if base is None or base.get('registryId', base.get('id')) in ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'):
            return {'quads': [], 'collision': [], 'outline': [], 'culling': [], 'climbing': [], 'optics': dict(EMPTY_OPTICS), 'fluid': None}
        return self.store.native(state_key(base))

    @lru_cache(maxsize=512)
    def _native_quads(self, key):
        result = []
        for original in self.store.native(key).get('quads', []):
            quad = {**original, 'material': dict(original['material'])}
            alphas = {(int(v[5]) >> 24) & 255 for v in quad['vertices']}
            if quad['material']['layer'] == 'solid' and alphas != {255}:
                quad['material']['layer'] = 'translucent' if any(0 < a < 255 for a in alphas) else 'cutout'
                quad['opaque'] = False
            if quad['material']['layer'] in ('cutout', 'cutout_mipped') and self.opacity.opaque_quad(quad):
                quad['material']['layer'] = 'solid'
                quad['opaque'] = True
                self.opacity.counts['promotedNativeOpaqueUvQuads'] += 1
            result.append(quad)
        return result

    def intern_cell(self, composition, salt=None):
        fragments = []
        digests = []
        for item in composition.get('fragments', []):
            key, value = self.store.fragment(item['pose'], tuple(item['cell']))
            digests.append(key)
            fragments.append(value)
        substrate_fragments = []
        substrate_digests = []
        for item in composition.get('substrateFragments', []):
            key, value = self.store.fragment(item['pose'], tuple(item['cell']))
            substrate_digests.append(key)
            substrate_fragments.append(value)
        for item in composition.get('substrateNativeFragments', []):
            key, value = self.store.native_fragment(state_key(item['state']), tuple(item['cell']))
            substrate_digests.append(key)
            substrate_fragments.append(value)
        # Only final semantic values are part of the persistent signature.
        # These read-side ingredients merely accelerate repeated input lookup.
        base = composition.get('baseState')
        visual_mode = composition.get('baseVisualMode', 'native')
        if visual_mode not in ('native', 'substrateFragments', 'nativePlusSubstrateFragments'):
            raise ValueError('Unknown base visual mode: '+str(visual_mode))
        if visual_mode == 'native' and substrate_fragments:
            raise ValueError('Substrate fragments require an explicit source visual route')
        if visual_mode in ('substrateFragments', 'nativePlusSubstrateFragments') and (base is None or not any(field in composition for field in ('substrateFragments', 'substrateNativeFragments'))):
            raise ValueError('Substrate visual transfer requires exact actual native state and explicit fragments')
        ingredient_key = digest({'fragments': sorted(digests), 'substrateFragments': sorted(substrate_digests), 'baseVisualMode': visual_mode, 'base': state_key(base) if base else None, 'optics': composition.get('optics'), 'fluid': composition.get('fluid'), 'fluidExplicit': 'fluid' in composition, 'localFunction': composition.get('localFunction'), 'salt': salt})
        previous = self.db.execute('SELECT cell FROM ingredients WHERE signature=?', (ingredient_key,)).fetchone()
        if previous:
            index = previous[0]
            self._map(composition, index)
            self.counts['cachedCompositions'] += 1
            return index
        native = self._native(base)
        if visual_mode in ('native', 'nativePlusSubstrateFragments'):
            if not native.get('nativeVisualComplete', True):
                raise ValueError('A physics-only native substrate needs explicit actual source visual fragments: '+state_key(base))
            native_quads = self._native_quads(state_key(base)) if base and base.get('registryId', base.get('id')) not in ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air') else []
            if any(vertex[axis] < -1e-6 or vertex[axis] > 1.000001 for quad in native_quads for vertex in quad['vertices'] for axis in range(3)):
                raise ValueError('Complete native visual extends outside this cell; transfer its actual full visual footprint through substrateNativeFragments: '+state_key(base))
            if visual_mode == 'nativePlusSubstrateFragments':
                native_quads = [*native_quads, *[q for f in substrate_fragments for q in f.get('quads', [])]]
        else:
            native_quads = [q for f in substrate_fragments for q in f.get('quads', [])]
            if not native_quads and str((base or {}).get('properties', {}).get('compact', '')).lower() == 'true' and native.get('renderType') == 'MODEL':
                raise ValueError('Explicit empty substrate visual transfer discards an actual compact MODEL: '+state_key(base))
        quads = unique_quads([*native_quads, *[q for f in fragments for q in f.get('quads', [])]])
        shapes = {field: canonical_boxes([*native.get(field, []), *[box for f in fragments for box in f.get(field, [])]]) for field in SHAPE_FIELDS}
        full_cube = opaque_cube(quads)
        if full_cube:
            shapes['culling'] = [[0.0, 0.0, 0.0, 1.0, 1.0, 1.0]]
        optics = normalize_optics(native.get('optics'))
        if composition.get('optics') is not None:
            optics = normalize_optics(composition['optics'])
        else:
            # Ordinary source guests still emit and occlude in a mixed cell.
            # Only substrate visual transfers deliberately omit source optics:
            # their exact installed native state was queried separately above.
            for field in ('opacity', 'luminance'):
                optics[field] = max([optics[field], *[f.get('optics', {}).get(field, 0) for f in fragments]])
        # Culling describes proven opaque visual surfaces, never collision.
        # A source carrier's exact emission/opacity remains separate above.
        if full_cube and base is None:
            optics.update(opaque=True, opaqueFullCube=True, solidBlock=True, blockVision=True, ambientOcclusion=.2)
        fluid = normalize_fluid(composition['fluid']) if 'fluid' in composition else normalize_fluid(native.get('fluid'))
        if 'fluid' not in composition and base is None:
            fluids = {canonical(normalize_fluid(f.get('fluid'))) for f in fragments if f.get('fluid') is not None}
            if len(fluids) > 1:
                raise ValueError('Conflicting exact fluids in observed composition')
            if fluids:
                fluid = json.loads(next(iter(fluids)))
        function = composition.get('localFunction')
        if function in (None, 'none'):
            function = {'kind': 'climb'} if shapes['climbing'] else None
        elif function == 'climb':
            function = {'kind': 'climb'}
        if function is not None and function != {'kind': 'climb'}:
            raise ValueError('Unsupported local function: '+canonical(function))
        # Binary mesh bytes use only stable shared material indices.
        raw_mesh = binary_mesh(quads, lambda row: self.intern('materials', row))
        mesh_index = self.intern('meshes', None, raw_mesh)
        shape_indices = {field: self.intern('shapes', shapes[field]) for field in SHAPE_FIELDS}
        optics_index = self.intern('optics', optics)
        final = {'mesh': mesh_index, **shape_indices, 'optics': optics_index, 'fluid': fluid, 'localFunction': function}
        # The signature addresses final content, not source pose/model names,
        # UUID, coordinates or owner identity. Whole-object roles are the sole
        # deliberate salt, so their removal cannot consume architecture cells.
        content_key = digest({'mesh': hashlib.sha256(raw_mesh).hexdigest(), 'shapes': shapes, 'optics': optics, 'fluid': fluid, 'localFunction': function, 'wholeRole': salt})
        final['key'] = content_key
        final['baseState'] = base
        if composition.get('displayName'):
            final['displayName'] = composition['displayName']
        existing = self.db.execute('SELECT idx FROM banks WHERE kind=? AND digest=?', ('cells', content_key)).fetchone()
        if existing:
            index = existing[0]
        else:
            index = self.next['cells']
            self.next['cells'] += 1
            self.db.execute('INSERT INTO banks VALUES (?,?,?,?)', ('cells', index, content_key, canonical(final).encode('utf-8')))
        self.db.execute('INSERT INTO ingredients VALUES (?,?)', (ingredient_key, index))
        self._map(composition, index)
        self.pending += 1
        self.counts['compiledCompositions'] += 1
        if self.pending >= 2000:
            self.db.commit()
            self.pending = 0
        return index

    def _map(self, row, index):
        if 'key' not in row:
            return
        key = str(row['key'])
        previous = self.db.execute('SELECT cell FROM mapping WHERE key=?', (key,)).fetchone()
        if previous and previous[0] != index:
            raise ValueError('A migration composition key changed its meaning: '+key)
        self.db.execute('INSERT OR IGNORE INTO mapping VALUES (?,?)', (key, index))

    def intern_whole(self, template):
        """A whole source pose has finite relative roles, never graph ownership."""
        key = template['key']
        members = []
        rows = template.get('cells')
        if rows is None:
            pose = template['pose']
            rows = [{'offset': list(cell), 'fragments': [{'pose': pose, 'cell': list(cell)}]} for cell in self.store.pose_cells(pose)]
        if not any(tuple(row['offset']) == (0, 0, 0) for row in rows):
            raise ValueError('Whole object lacks its zero-offset base')
        for row in sorted(rows, key=lambda value: value['offset']):
            offset = row['offset']
            if len(offset) != 3 or any(abs(int(n)) > 127 for n in offset):
                raise ValueError('Whole object role offset exceeds finite local budget')
            cell = self.intern_cell({**row, 'displayName': template.get('displayName', 'Цельная статуя')}, salt={'template': key, 'offset': offset})
            members.append({'offset': list(offset), 'cell': cell})
        result = {'key': key, 'displayName': template.get('displayName', 'Цельная статуя'), 'baseCell': next(r['cell'] for r in members if r['offset'] == [0, 0, 0]), 'cells': members}
        self.intern('wholeObjects', result)
        return result

    def _rows(self, kind):
        for index, data in self.db.execute('SELECT idx,data FROM banks WHERE kind=? ORDER BY idx', (kind,)):
            yield index, data

    def finish(self, mapping=None):
        self.db.commit()
        self.output.mkdir(parents=True, exist_ok=True)
        temporary = self.output/'meshes.bin.writing'
        mesh_metadata = []
        with temporary.open('wb') as output:
            output.write(b'NGQ1')
            output.write(struct.pack('>i', self.next['meshes']))
            for index, data in self._rows('meshes'):
                count = struct.unpack('>i', data[:4])[0]
                mesh_metadata.append({'quadCount': count})
                output.write(data)
        os.replace(temporary, self.output/'meshes.bin')
        document = {'schemaVersion': 1, 'bankSize': 128, 'materials': [], 'shapes': [], 'optics': [], 'meshes': mesh_metadata, 'cells': [], 'wholeObjects': []}
        for kind in ('materials', 'shapes', 'optics', 'cells', 'wholeObjects'):
            document[kind] = [json.loads(data) for index, data in self._rows(kind)]
        temporary = self.output/'catalog.json.writing'
        temporary.write_text(canonical(document)+'\n', encoding='utf-8')
        os.replace(temporary, self.output/'catalog.json')
        self._markers(document)
        if mapping:
            path = Path(mapping)
            path.parent.mkdir(parents=True, exist_ok=True)
            with path.open('w', encoding='utf-8') as stream:
                for key, index in self.db.execute('SELECT key,cell FROM mapping ORDER BY key'):
                    stream.write(canonical({'key': key, 'typeIndex': index})+'\n')
        proof = {'status': 'PASS', 'schemaVersion': 1, 'compilerAlgorithmSha256': compiler_fingerprint(), 'banks': dict(self.next), 'thisInvocation': dict(self.counts), 'materialProofs': dict(self.opacity.counts), 'catalogSha256': hashlib.sha256((self.output/'catalog.json').read_bytes()).hexdigest(), 'meshesSha256': hashlib.sha256((self.output/'meshes.bin').read_bytes()).hexdigest(), 'noUuidOrWorldCoordinatesInRuntime': True, 'observedCompositionsOnly': True}
        (self.output/'compiler-receipt.json').write_text(json.dumps(proof, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
        self.db.execute('PRAGMA wal_checkpoint(TRUNCATE)')
        return proof

    def _markers(self, document):
        # output is assets/bloodborne_dw/native_grid; marker resources sit in
        # the same namespace's blockstates/models. Every part has one model ID.
        namespace = self.output.parent
        for folder in ('blockstates', 'models/block', 'models/item'):
            (namespace/folder).mkdir(parents=True, exist_ok=True)
        for bank in range((len(document['cells'])+127)//128):
            variants = {}
            for part in range(128):
                index = bank*128+part
                name = f'native_grid_{bank}_{part}'
                variants['part='+str(part)] = {'model': 'bloodborne_dw:block/'+name}
                if index < len(document['cells']):
                    mesh = document['cells'][index]['mesh']
                    binary = self.db.execute("SELECT data FROM banks WHERE kind='meshes' AND idx=?", (mesh,)).fetchone()[0]
                    material_index = struct.unpack('>i', binary[4:8])[0] if len(binary) > 4 else None
                    sprite = document['materials'][material_index]['sprite'] if material_index is not None else 'minecraft:block/stone'
                else:
                    sprite = 'minecraft:block/stone'
                (namespace/'models/block'/(name+'.json')).write_text(canonical({'textures': {'particle': sprite}})+'\n', encoding='utf-8')
            (namespace/'blockstates'/f'native_grid_{bank}.json').write_text(canonical({'variants': variants})+'\n', encoding='utf-8')
            (namespace/'models/item'/f'native_grid_{bank}.json').write_text(canonical({'parent': f'bloodborne_dw:block/native_grid_{bank}_0'})+'\n', encoding='utf-8')

    def close(self):
        self.db.commit()
        self.store.close()
        self.db.close()


def compiler_fingerprint():
    functions = (signed32, full_rectangles, opaque_cube, normalize_optics, normalize_fluid, binary_mesh, CatalogCompiler)
    return hashlib.sha256('\n'.join(inspect.getsource(function) for function in functions).encode('utf-8')).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fragments', type=Path, required=True)
    parser.add_argument('--compositions', type=Path, required=True)
    parser.add_argument('--whole-objects', type=Path)
    parser.add_argument('--database', type=Path, required=True)
    parser.add_argument('--resources', type=Path, required=True, help='Output assets/bloodborne_dw/native_grid directory')
    parser.add_argument('--textures', type=Path, help='Unchanged texture resource directory; inferred from output assets location by default')
    parser.add_argument('--out', type=Path, required=True, help='JSONL composition key -> typeIndex mapping')
    args = parser.parse_args()
    compiler = CatalogCompiler(args.fragments, args.database, args.resources, args.textures)
    try:
        for row in read_lines(args.compositions):
            compiler.intern_cell(row)
        if args.whole_objects:
            for row in read_lines(args.whole_objects):
                compiler.intern_whole(row)
        print(json.dumps(compiler.finish(args.out), ensure_ascii=False))
    finally:
        compiler.close()


if __name__ == '__main__':
    main()
